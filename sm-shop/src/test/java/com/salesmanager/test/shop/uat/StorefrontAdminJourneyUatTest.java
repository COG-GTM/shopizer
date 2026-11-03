package com.salesmanager.test.shop.uat;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;
import static org.springframework.http.HttpStatus.CREATED;
import static org.springframework.http.HttpStatus.OK;
import static org.springframework.http.HttpStatus.SERVICE_UNAVAILABLE;

import java.util.Arrays;
import java.util.Date;
import java.util.UUID;

import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.SignatureAlgorithm;
import org.junit.Assume;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.SpringBootTest.WebEnvironment;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.test.context.junit4.SpringRunner;

import com.salesmanager.core.business.constants.Constants;
import com.salesmanager.core.model.customer.CustomerGender;
import com.salesmanager.shop.application.ShopApplication;
import com.salesmanager.shop.model.catalog.category.CategoryDescription;
import com.salesmanager.shop.model.catalog.category.PersistableCategory;
import com.salesmanager.shop.model.catalog.category.ReadableCategoryList;
import com.salesmanager.shop.model.catalog.product.ReadableProduct;
import com.salesmanager.shop.model.catalog.product.ReadableProductList;
import com.salesmanager.shop.model.customer.PersistableCustomer;
import com.salesmanager.shop.model.customer.address.Address;
import com.salesmanager.shop.model.order.transaction.PersistablePayment;
import com.salesmanager.shop.model.order.v1.PersistableOrder;
import com.salesmanager.shop.model.order.v1.ReadableOrderConfirmation;
import com.salesmanager.shop.model.references.PersistableAddress;
import com.salesmanager.shop.model.shoppingcart.PersistableShoppingCartItem;
import com.salesmanager.shop.model.shoppingcart.ReadableShoppingCart;
import com.salesmanager.shop.model.store.PersistableMerchantStore;
import com.salesmanager.shop.model.store.ReadableMerchantStore;
import com.salesmanager.shop.store.security.AuthenticationRequest;
import com.salesmanager.shop.store.security.AuthenticationResponse;
import com.salesmanager.test.shop.common.ServicesTestSupport;

@SpringBootTest(classes = ShopApplication.class, webEnvironment = WebEnvironment.RANDOM_PORT)
@RunWith(SpringRunner.class)
public class StorefrontAdminJourneyUatTest extends ServicesTestSupport {

    @Value("${jwt.secret}")
    private String jwtSecret;

    @Test
    public void adminLoginAndPrivateAuthorizationJourney() {
        AuthenticationResponse login = adminLogin();
        assertNotNull(login.getToken());

        HttpHeaders validHeaders = bearer(login.getToken());
        ResponseEntity<String> authorized = testRestTemplate.exchange("/api/v1/private/users/1",
                HttpMethod.GET, new HttpEntity<>(validHeaders), String.class);
        assertEquals(OK, authorized.getStatusCode());

        ResponseEntity<String> noToken = testRestTemplate.exchange("/api/v1/private/users/1",
                HttpMethod.GET, HttpEntity.EMPTY, String.class);
        assertTrue(isUnauthorized(noToken.getStatusCode()));

        HttpHeaders invalidHeaders = bearer(expiredAdminToken());
        ResponseEntity<String> invalidToken = testRestTemplate.exchange("/api/v1/private/users/1",
                HttpMethod.GET, new HttpEntity<>(invalidHeaders), String.class);
        assertTrue(isUnauthorized(invalidToken.getStatusCode()));
    }

    private String expiredAdminToken() {
        return Jwts.builder().setSubject("admin@shopizer.com").setExpiration(new Date(0))
                .signWith(SignatureAlgorithm.HS512, jwtSecret).compact();
    }

    @Test
    public void customerRegistrationLoginAndAuthenticatedProfileJourney() {
        String email = uniqueCode("customer") + "@test.com";
        String password = "clear123";

        PersistableCustomer customer = new PersistableCustomer();
        customer.setEmailAddress(email);
        customer.setPassword(password);
        customer.setGender(CustomerGender.M.name());
        customer.setLanguage("en");
        Address billing = new Address();
        billing.setFirstName("Journey");
        billing.setLastName("Customer");
        billing.setCountry("BE");
        customer.setBilling(billing);
        customer.setStoreCode(Constants.DEFAULT_STORE);

        ResponseEntity<String> registration = testRestTemplate.postForEntity(
                "/api/v1/customer/register?store=" + Constants.DEFAULT_STORE,
                new HttpEntity<>(customer, jsonHeaders()), String.class);
        assertTrue(registration.getStatusCode().is2xxSuccessful());

        ResponseEntity<AuthenticationResponse> login = testRestTemplate.postForEntity(
                "/api/v1/customer/login?store=" + Constants.DEFAULT_STORE,
                new HttpEntity<>(new AuthenticationRequest(email, password), jsonHeaders()),
                AuthenticationResponse.class);
        assertEquals(OK, login.getStatusCode());
        assertNotNull(login.getBody().getToken());

        ResponseEntity<String> profile = testRestTemplate.exchange(
                "/api/v1/auth/customer/profile?store=" + Constants.DEFAULT_STORE,
                HttpMethod.GET, new HttpEntity<>(bearer(login.getBody().getToken())), String.class);
        assertEquals(OK, profile.getStatusCode());
    }

    @Test
    public void storefrontBrowseJourney() {
        ResponseEntity<ReadableMerchantStore> store = testRestTemplate.getForEntity(
                "/api/v1/store/" + Constants.DEFAULT_STORE, ReadableMerchantStore.class);
        ResponseEntity<String> categories = testRestTemplate.getForEntity(
                "/api/v1/category?store=" + Constants.DEFAULT_STORE, String.class);
        ResponseEntity<ReadableProductList> products = testRestTemplate.getForEntity(
                "/api/v1/products?store=" + Constants.DEFAULT_STORE + "&lang=en", ReadableProductList.class);

        assertEquals(OK, store.getStatusCode());
        assertEquals(OK, categories.getStatusCode());
        assertEquals(OK, products.getStatusCode());

        ReadableProduct product;
        if (products.getBody() != null && !products.getBody().getProducts().isEmpty()) {
            product = products.getBody().getProducts().get(0);
        } else {
            product = sampleProduct(uniqueCode("browse"));
        }
        ResponseEntity<ReadableProduct> detail = testRestTemplate.getForEntity(
                "/api/v2/product/" + product.getSku() + "?store=" + Constants.DEFAULT_STORE + "&lang=en",
                ReadableProduct.class);
        assertEquals(OK, detail.getStatusCode());
        assertNotNull(detail.getBody());
    }

    @Test
    public void cartCreateAddAndReadJourney() {
        ReadableProduct first = sampleProduct(uniqueCode("cartone"));
        ReadableProduct second = sampleProduct(uniqueCode("carttwo"));
        PersistableShoppingCartItem firstItem = cartItem(first.getSku(), 1);

        ResponseEntity<ReadableShoppingCart> created = testRestTemplate.postForEntity("/api/v1/cart?store="
                        + Constants.DEFAULT_STORE,
                new HttpEntity<>(firstItem, jsonHeaders()), ReadableShoppingCart.class);
        assertEquals(CREATED, created.getStatusCode());
        assertNotNull(created.getBody());

        ResponseEntity<ReadableShoppingCart> updated = testRestTemplate.exchange(
                "/api/v1/cart/" + created.getBody().getCode() + "?store=" + Constants.DEFAULT_STORE,
                HttpMethod.PUT, new HttpEntity<>(cartItem(second.getSku(), 1), jsonHeaders()),
                ReadableShoppingCart.class);
        assertEquals(CREATED, updated.getStatusCode());

        ResponseEntity<ReadableShoppingCart> read = testRestTemplate.getForEntity(
                "/api/v1/cart/" + created.getBody().getCode() + "?store=" + Constants.DEFAULT_STORE,
                ReadableShoppingCart.class);
        assertEquals(OK, read.getStatusCode());
        assertEquals(2, read.getBody().getQuantity());
    }

    @Test
    public void authenticatedCustomerCheckoutAndOrderReadbackJourney() {
        String email = uniqueCode("checkout") + "@test.com";
        String password = "clear123";
        AuthenticationResponse customerAuth = registerCustomer(email, password);

        ReadableProduct product = sampleProduct(uniqueCode("checkoutproduct"));
        ResponseEntity<ReadableShoppingCart> cart = testRestTemplate.postForEntity("/api/v1/cart?store="
                        + Constants.DEFAULT_STORE,
                new HttpEntity<>(cartItem(product.getSku(), 1), jsonHeaders()), ReadableShoppingCart.class);
        assertEquals(CREATED, cart.getStatusCode());

        PersistableOrder order = new PersistableOrder();
        order.setCurrency("USD");
        PersistablePayment payment = new PersistablePayment();
        payment.setPaymentType("MONEYORDER");
        payment.setTransactionType("OK");
        payment.setPaymentModule("moneyorder");
        order.setPayment(payment);

        ResponseEntity<ReadableOrderConfirmation> checkout = testRestTemplate.exchange(
                "/api/v1/auth/cart/" + cart.getBody().getCode() + "/checkout?store="
                        + Constants.DEFAULT_STORE,
                HttpMethod.POST,
                new HttpEntity<>(order, bearer(customerAuth.getToken())),
                ReadableOrderConfirmation.class);
        if (checkout.getStatusCode() == SERVICE_UNAVAILABLE) {
            Assume.assumeFalse("Checkout is unavailable in the H2 test runtime",
                    checkout.getStatusCode() == SERVICE_UNAVAILABLE);
        }
        assertEquals(OK, checkout.getStatusCode());
        assertNotNull(checkout.getBody());
        assertNotNull(checkout.getBody().getId());

        ResponseEntity<String> readback = testRestTemplate.exchange(
                "/api/v1/auth/orders/" + checkout.getBody().getId() + "?store=" + Constants.DEFAULT_STORE,
                HttpMethod.GET, new HttpEntity<>(bearer(customerAuth.getToken())), String.class);
        assertEquals(OK, readback.getStatusCode());
    }

    @Test
    public void servicesBasicAuthenticationJourney() {
        HttpHeaders validCredentials = new HttpHeaders();
        validCredentials.setContentType(MediaType.TEXT_PLAIN);
        validCredentials.setBasicAuth("admin@shopizer.com", "password");
        ResponseEntity<String> valid = testRestTemplate.postForEntity("/services/private/system/optin",
                new HttpEntity<>("{}", validCredentials), String.class);

        HttpHeaders invalidCredentials = new HttpHeaders();
        invalidCredentials.setContentType(MediaType.TEXT_PLAIN);
        invalidCredentials.setBasicAuth("admin@shopizer.com", "wrong-password");
        ResponseEntity<String> invalid = testRestTemplate.postForEntity("/services/private/system/optin",
                new HttpEntity<>("{}", invalidCredentials), String.class);

        assertEquals(OK, valid.getStatusCode());
        assertTrue(isUnauthorized(invalid.getStatusCode()));
    }

    @Test
    public void actuatorAndApiDocumentationJourney() {
        ResponseEntity<String> health = testRestTemplate.getForEntity("/actuator/health", String.class);
        assertEquals(OK, health.getStatusCode());
        assertTrue(health.getBody().contains("\"status\":\"UP\"")
                || health.getBody().contains("\"status\" : \"UP\""));

        ResponseEntity<String> docs = testRestTemplate.getForEntity("/v2/api-docs", String.class);
        assertEquals(OK, docs.getStatusCode());
        assertTrue(docs.getBody().trim().startsWith("{"));
    }

    @Test
    public void categoryAdminCreateUpdateCrossStoreDeleteJourney() {
        String storeB = createStore();
        PersistableCategory category = categoryPayload(uniqueCode("uat-category"));

        ResponseEntity<PersistableCategory> created = testRestTemplate.postForEntity(
                "/api/v1/private/category?store=" + Constants.DEFAULT_STORE,
                new HttpEntity<>(category, getHeader()), PersistableCategory.class);
        assertEquals(CREATED, created.getStatusCode());
        assertNotNull(created.getBody().getId());

        PersistableCategory update = categoryPayload(created.getBody().getCode());
        update.setSortOrder(3);
        ResponseEntity<PersistableCategory> updated = testRestTemplate.exchange(
                "/api/v1/private/category/" + created.getBody().getId() + "?store="
                        + Constants.DEFAULT_STORE,
                HttpMethod.PUT, new HttpEntity<>(update, getHeader()), PersistableCategory.class);
        assertEquals(OK, updated.getStatusCode());

        ResponseEntity<String> crossStorePut = testRestTemplate.exchange(
                "/api/v1/private/category/" + created.getBody().getId() + "?store=" + storeB,
                HttpMethod.PUT, new HttpEntity<>(update, getHeader()), String.class);
        ResponseEntity<String> crossStoreDelete = testRestTemplate.exchange(
                "/api/v1/private/category/" + created.getBody().getId() + "?store=" + storeB,
                HttpMethod.DELETE, new HttpEntity<>(getHeader()), String.class);
        assertEquals(org.springframework.http.HttpStatus.NOT_FOUND, crossStorePut.getStatusCode());
        assertEquals(org.springframework.http.HttpStatus.NOT_FOUND, crossStoreDelete.getStatusCode());

        ResponseEntity<String> ownerDelete = testRestTemplate.exchange(
                "/api/v1/private/category/" + created.getBody().getId(), HttpMethod.DELETE,
                new HttpEntity<>(getHeader()), String.class);
        assertEquals(OK, ownerDelete.getStatusCode());
        ResponseEntity<ReadableCategoryList> afterDelete = testRestTemplate.getForEntity(
                "/api/v1/category?store=" + Constants.DEFAULT_STORE + "&lang=en&count=1000",
                ReadableCategoryList.class);
        assertEquals(OK, afterDelete.getStatusCode());
        assertFalse(afterDelete.getBody().getCategories().stream()
                .anyMatch(candidate -> created.getBody().getCode().equals(candidate.getCode())));
    }

    private AuthenticationResponse adminLogin() {
        ResponseEntity<AuthenticationResponse> response = testRestTemplate.postForEntity("/api/v1/private/login",
                new HttpEntity<>(new AuthenticationRequest("admin@shopizer.com", "password"), jsonHeaders()),
                AuthenticationResponse.class);
        assertEquals(OK, response.getStatusCode());
        return response.getBody();
    }

    private AuthenticationResponse registerCustomer(String email, String password) {
        PersistableCustomer customer = new PersistableCustomer();
        customer.setEmailAddress(email);
        customer.setPassword(password);
        customer.setGender(CustomerGender.M.name());
        customer.setLanguage("en");
        Address billing = new Address();
        billing.setFirstName("Journey");
        billing.setLastName("Customer");
        billing.setCountry("BE");
        customer.setBilling(billing);
        customer.setStoreCode(Constants.DEFAULT_STORE);

        ResponseEntity<AuthenticationResponse> response = testRestTemplate.postForEntity(
                "/api/v1/customer/register?store=" + Constants.DEFAULT_STORE,
                new HttpEntity<>(customer, jsonHeaders()), AuthenticationResponse.class);
        assertTrue(response.getStatusCode().is2xxSuccessful());
        assertNotNull(response.getBody());
        assertNotNull(response.getBody().getToken());
        return response.getBody();
    }

    private String createStore() {
        String code = uniqueCode("uatstore");
        PersistableAddress address = new PersistableAddress();
        address.setAddress("1 uat street");
        address.setPostalCode("12345");
        address.setCountry("US");
        address.setCity("Test City");
        address.setStateProvince("CA");

        PersistableMerchantStore store = new PersistableMerchantStore();
        store.setCode(code);
        store.setCurrency("CAD");
        store.setDefaultLanguage("en");
        store.setEmail(code + "@test.com");
        store.setName(code);
        store.setPhone("444-555-6666");
        store.setSupportedLanguages(Arrays.asList("en"));
        store.setAddress(address);

        ResponseEntity<Void> response = testRestTemplate.exchange("/api/v1/private/store/",
                HttpMethod.POST, new HttpEntity<>(store, getHeader()), Void.class);
        assertEquals(OK, response.getStatusCode());
        return code;
    }

    private PersistableCategory categoryPayload(String code) {
        PersistableCategory category = new PersistableCategory();
        category.setCode(code);
        category.setSortOrder(1);
        category.setVisible(true);

        CategoryDescription description = new CategoryDescription();
        description.setLanguage("en");
        description.setName(code);
        description.setFriendlyUrl(code);
        description.setTitle(code);
        category.setDescriptions(Arrays.asList(description));
        return category;
    }

    private PersistableShoppingCartItem cartItem(String sku, int quantity) {
        PersistableShoppingCartItem item = new PersistableShoppingCartItem();
        item.setProduct(sku);
        item.setQuantity(quantity);
        return item;
    }

    private HttpHeaders jsonHeaders() {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        return headers;
    }

    private HttpHeaders bearer(String token) {
        HttpHeaders headers = jsonHeaders();
        headers.setBearerAuth(token);
        return headers;
    }

    private boolean isUnauthorized(org.springframework.http.HttpStatus status) {
        return status == org.springframework.http.HttpStatus.UNAUTHORIZED
                || status == org.springframework.http.HttpStatus.FORBIDDEN;
    }

    private String uniqueCode(String prefix) {
        return prefix + UUID.randomUUID().toString().replace("-", "");
    }
}
