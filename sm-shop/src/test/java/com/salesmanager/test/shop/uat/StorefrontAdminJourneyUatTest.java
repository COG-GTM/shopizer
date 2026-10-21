package com.salesmanager.test.shop.uat;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;
import static org.springframework.http.HttpMethod.GET;
import static org.springframework.http.HttpMethod.POST;
import static org.springframework.http.HttpMethod.PUT;
import static org.springframework.http.HttpStatus.CREATED;
import static org.springframework.http.HttpStatus.OK;
import static org.springframework.http.HttpStatus.UNAUTHORIZED;

import java.nio.charset.StandardCharsets;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.SpringBootTest.WebEnvironment;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.test.context.junit4.SpringRunner;

import com.salesmanager.core.business.constants.Constants;
import com.salesmanager.core.model.customer.CustomerGender;
import com.salesmanager.shop.application.ShopApplication;
import com.salesmanager.shop.model.catalog.product.ReadableProduct;
import com.salesmanager.shop.model.catalog.product.ReadableProductList;
import com.salesmanager.shop.model.customer.PersistableCustomer;
import com.salesmanager.shop.model.customer.address.Address;
import com.salesmanager.shop.model.shoppingcart.PersistableShoppingCartItem;
import com.salesmanager.shop.model.shoppingcart.ReadableShoppingCart;
import com.salesmanager.shop.model.shoppingcart.ReadableShoppingCartItem;
import com.salesmanager.shop.model.store.ReadableMerchantStore;
import com.salesmanager.shop.store.security.AuthenticationRequest;
import com.salesmanager.shop.store.security.AuthenticationResponse;
import com.salesmanager.test.shop.common.ServicesTestSupport;

@RunWith(SpringRunner.class)
@SpringBootTest(classes = ShopApplication.class, webEnvironment = WebEnvironment.RANDOM_PORT)
public class StorefrontAdminJourneyUatTest extends ServicesTestSupport {

    @Test
    public void adminCanAuthenticateAndReachPrivateApi() {
        String token = login("admin@shopizer.com", "password");

        assertEquals(OK, testRestTemplate.exchange("/api/v1/private/users/1", GET, bearer(token),
                String.class).getStatusCode());
        assertEquals(UNAUTHORIZED, testRestTemplate.getForEntity("/api/v1/private/users/1", String.class)
                .getStatusCode());
    }

    @Test
    public void customerCanRegisterLoginAndReadProfile() {
        String email = uniqueEmail();
        registerCustomer(email);
        String token = login(email, "clear123");

        assertEquals(OK, testRestTemplate.exchange("/api/v1/auth/customer/profile", GET, bearer(token),
                String.class).getStatusCode());
    }

    @Test
    public void storefrontCanBrowseStoreCategoriesProductsAndProductDetail() {
        ResponseEntity<ReadableMerchantStore> store = testRestTemplate.getForEntity("/api/v1/store/DEFAULT",
                ReadableMerchantStore.class);
        assertEquals(OK, store.getStatusCode());
        assertNotNull(store.getBody());

        assertEquals(OK, testRestTemplate.getForEntity("/api/v1/category?store=DEFAULT", String.class)
                .getStatusCode());
        ResponseEntity<ReadableProductList> products = testRestTemplate.getForEntity(
                "/api/v1/products?store=DEFAULT", ReadableProductList.class);
        assertEquals(OK, products.getStatusCode());

        if (products.getBody().getProducts().isEmpty()) {
            sampleProduct("uat-browse-" + System.nanoTime());
            products = testRestTemplate.getForEntity("/api/v1/products?store=DEFAULT", ReadableProductList.class);
        }
        ReadableProduct product = products.getBody().getProducts().get(0);
        assertEquals(OK, testRestTemplate.getForEntity("/api/v2/product/" + product.getSku(),
                ReadableProduct.class).getStatusCode());
    }

    @Test
    public void customerCanCreateUpdateAndRetrieveCartQuantities() {
        String code = "uat-cart-" + System.nanoTime();
        ReadableProduct first = sampleProduct(code + "-one");
        ReadableProduct second = sampleProduct(code + "-two");

        ResponseEntity<ReadableShoppingCart> created = testRestTemplate.postForEntity("/api/v1/cart",
                new HttpEntity<>(cartItem(first.getSku(), 1), headers()), ReadableShoppingCart.class);
        assertEquals(CREATED, created.getStatusCode());
        String cartCode = created.getBody().getCode();

        ResponseEntity<ReadableShoppingCart> added = testRestTemplate.exchange("/api/v1/cart/" + cartCode, PUT,
                new HttpEntity<>(cartItem(second.getSku(), 2), headers()), ReadableShoppingCart.class);
        assertEquals(CREATED, added.getStatusCode());

        ResponseEntity<ReadableShoppingCart> retrieved = testRestTemplate.getForEntity(
                "/api/v1/cart/" + cartCode, ReadableShoppingCart.class);
        assertEquals(OK, retrieved.getStatusCode());
        assertEquals(3, retrieved.getBody().getQuantity());
        assertEquals(2, quantityForSku(retrieved.getBody(), second.getSku()));
    }

    @Test
    public void servicesPrivateEndpointAcceptsValidAndRejectsInvalidBasicAuth() {
        HttpHeaders validHeaders = new HttpHeaders();
        validHeaders.setBasicAuth("admin@shopizer.com", "password", StandardCharsets.UTF_8);
        validHeaders.setContentType(MediaType.TEXT_PLAIN);
        assertTrue(testRestTemplate.exchange("/services/private/system/optin", POST,
                new HttpEntity<>("test", validHeaders), String.class).getStatusCode().is2xxSuccessful());

        HttpHeaders invalidHeaders = new HttpHeaders();
        invalidHeaders.setBasicAuth("admin@shopizer.com", "wrong-password", StandardCharsets.UTF_8);
        invalidHeaders.setContentType(MediaType.TEXT_PLAIN);
        assertEquals(UNAUTHORIZED, testRestTemplate.exchange("/services/private/system/optin", POST,
                new HttpEntity<>("test", invalidHeaders), String.class).getStatusCode());
    }

    @Test
    public void actuatorHealthAndSwaggerDocsAreAvailable() {
        ResponseEntity<String> health = testRestTemplate.getForEntity("/actuator/health", String.class);
        assertEquals(OK, health.getStatusCode());
        assertTrue(health.getBody().contains("\"status\":\"UP\""));

        ResponseEntity<String> docs = testRestTemplate.getForEntity("/v2/api-docs", String.class);
        assertEquals(OK, docs.getStatusCode());
        assertTrue(docs.getBody().contains("swagger"));
    }

    private String login(String username, String password) {
        ResponseEntity<AuthenticationResponse> response = testRestTemplate.postForEntity("/api/v1/"
                + (username.equals("admin@shopizer.com") ? "private" : "customer") + "/login",
                new HttpEntity<>(new AuthenticationRequest(username, password)), AuthenticationResponse.class);
        assertEquals(OK, response.getStatusCode());
        assertNotNull(response.getBody());
        return response.getBody().getToken();
    }

    private void registerCustomer(String email) {
        PersistableCustomer customer = new PersistableCustomer();
        customer.setEmailAddress(email);
        customer.setPassword("clear123");
        customer.setGender(CustomerGender.M.name());
        customer.setLanguage("en");
        customer.setStoreCode(Constants.DEFAULT_STORE);
        Address billing = new Address();
        billing.setFirstName("Storefront");
        billing.setLastName("Customer");
        billing.setCountry("BE");
        customer.setBilling(billing);
        ResponseEntity<String> response = testRestTemplate.postForEntity("/api/v1/customer/register",
                new HttpEntity<>(customer, getHeader()), String.class);
        assertTrue("customer registration status: " + response.getStatusCode(),
                response.getStatusCode().is2xxSuccessful());
    }

    private String uniqueEmail() {
        return "uat-" + System.nanoTime() + "@test.com";
    }

    private PersistableShoppingCartItem cartItem(String sku, int quantity) {
        PersistableShoppingCartItem item = new PersistableShoppingCartItem();
        item.setProduct(sku);
        item.setQuantity(quantity);
        return item;
    }

    private int quantityForSku(ReadableShoppingCart cart, String sku) {
        for (ReadableShoppingCartItem item : cart.getProducts()) {
            if (sku.equals(item.getSku())) {
                return item.getQuantity();
            }
        }
        return 0;
    }

    private HttpEntity<String> bearer(String token) {
        HttpHeaders requestHeaders = headers();
        requestHeaders.setBearerAuth(token);
        return new HttpEntity<>(requestHeaders);
    }

    private HttpHeaders headers() {
        HttpHeaders requestHeaders = new HttpHeaders();
        requestHeaders.setContentType(MediaType.APPLICATION_JSON);
        return requestHeaders;
    }
}
