package com.salesmanager.test.shop.uat;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import java.nio.charset.StandardCharsets;

import com.salesmanager.core.business.constants.Constants;
import com.salesmanager.core.model.customer.CustomerGender;
import com.salesmanager.shop.application.ShopApplication;
import com.salesmanager.shop.model.catalog.product.ReadableProduct;
import com.salesmanager.shop.model.catalog.product.ReadableProductList;
import com.salesmanager.shop.model.customer.PersistableCustomer;
import com.salesmanager.shop.model.customer.address.Address;
import com.salesmanager.shop.model.shoppingcart.PersistableShoppingCartItem;
import com.salesmanager.shop.model.shoppingcart.ReadableShoppingCart;
import com.salesmanager.shop.model.store.ReadableMerchantStore;
import com.salesmanager.shop.store.security.AuthenticationRequest;
import com.salesmanager.shop.store.security.AuthenticationResponse;
import com.salesmanager.test.shop.common.ServicesTestSupport;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.SpringBootTest.WebEnvironment;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.test.context.junit4.SpringRunner;

@SpringBootTest(classes = ShopApplication.class, webEnvironment = WebEnvironment.RANDOM_PORT)
@RunWith(SpringRunner.class)
public class CoreUserJourneysTest extends ServicesTestSupport {

    private static final String SUFFIX = String.valueOf(System.nanoTime() % 100000L);
    private static final String CUSTOMER_PASSWORD = "clear123";

    @Autowired
    private TestRestTemplate testRestTemplate;

    @Test
    public void adminLoginAndBearerAuthorization() {
        HttpHeaders adminHeaders = getHeader();
        ResponseEntity<String> user = testRestTemplate.exchange(
                "/api/v1/private/users/1", HttpMethod.GET,
                new HttpEntity<>(adminHeaders), String.class);
        assertEquals(user.toString(), HttpStatus.OK, user.getStatusCode());

        ResponseEntity<String> anonymous = testRestTemplate.exchange(
                "/api/v1/private/users/1", HttpMethod.GET,
                new HttpEntity<>(new HttpHeaders()), String.class);
        assertEquals(anonymous.toString(), HttpStatus.UNAUTHORIZED, anonymous.getStatusCode());

        HttpHeaders invalidHeaders = new HttpHeaders();
        invalidHeaders.set("Authorization", "Bearer ");
        ResponseEntity<String> invalid = testRestTemplate.exchange(
                "/api/v1/private/users/1", HttpMethod.GET,
                new HttpEntity<>(invalidHeaders), String.class);
        assertEquals(invalid.toString(), HttpStatus.UNAUTHORIZED, invalid.getStatusCode());
    }

    @Test
    public void customerCanRegisterLoginAndReadProfile() {
        String email = "core-user-" + SUFFIX + "@example.com";
        registerCustomer(email);
        HttpHeaders customerHeaders = customerHeaders(email);

        ResponseEntity<String> profile = testRestTemplate.exchange(
                "/api/v1/auth/customer/profile", HttpMethod.GET,
                new HttpEntity<>(customerHeaders), String.class);
        assertEquals(profile.toString(), HttpStatus.OK, profile.getStatusCode());
    }

    @Test
    public void storefrontCanBrowseProductsAndReadProductDetails() {
        ResponseEntity<ReadableMerchantStore> store = testRestTemplate.getForEntity(
                "/api/v1/store/DEFAULT", ReadableMerchantStore.class);
        assertEquals(store.toString(), HttpStatus.OK, store.getStatusCode());

        ResponseEntity<String> categories = testRestTemplate.getForEntity(
                "/api/v1/category", String.class);
        assertEquals(categories.toString(), HttpStatus.OK, categories.getStatusCode());

        ResponseEntity<ReadableProductList> response = testRestTemplate.getForEntity(
                "/api/v1/products", ReadableProductList.class);
        assertEquals(response.toString(), HttpStatus.OK, response.getStatusCode());
        assertNotNull(response.getBody());

        ReadableProduct product = response.getBody().getProducts().isEmpty()
                ? sampleProduct("core-user-browse-" + SUFFIX)
                : response.getBody().getProducts().get(0);
        assertNotNull(product);
        assertNotNull(product.getSku());

        ResponseEntity<ReadableProduct> detail = testRestTemplate.getForEntity(
                "/api/v2/product/" + product.getSku(), ReadableProduct.class);
        assertEquals(detail.toString(), HttpStatus.OK, detail.getStatusCode());
        assertNotNull(detail.getBody());
    }

    @Test
    public void customerCanCreateUpdateAndReadCart() {
        ReadableProduct product = sampleProduct("core-user-cart-" + SUFFIX);
        PersistableShoppingCartItem item = new PersistableShoppingCartItem();
        item.setProduct(product.getSku());
        item.setQuantity(1);

        ResponseEntity<ReadableShoppingCart> created = testRestTemplate.postForEntity(
                "/api/v1/cart/", new HttpEntity<>(item, getHeader()), ReadableShoppingCart.class);
        assertEquals(created.toString(), HttpStatus.CREATED, created.getStatusCode());
        assertNotNull(created.getBody());
        assertEquals(1, created.getBody().getQuantity());

        item.setQuantity(3);
        ResponseEntity<ReadableShoppingCart> updated = testRestTemplate.exchange(
                "/api/v1/cart/" + created.getBody().getCode(), HttpMethod.PUT,
                new HttpEntity<>(item, getHeader()), ReadableShoppingCart.class);
        assertEquals(updated.toString(), HttpStatus.CREATED, updated.getStatusCode());
        assertEquals(3, updated.getBody().getQuantity());

        ResponseEntity<ReadableShoppingCart> readBack = testRestTemplate.getForEntity(
                "/api/v1/cart/" + created.getBody().getCode(), ReadableShoppingCart.class);
        assertEquals(readBack.toString(), HttpStatus.OK, readBack.getStatusCode());
        assertEquals(3, readBack.getBody().getQuantity());
    }

    @Test
    public void servicesPrivateEndpointRequiresValidBasicAuthentication() {
        HttpHeaders validHeaders = new HttpHeaders();
        validHeaders.setBasicAuth("admin@shopizer.com", "password");
        validHeaders.setContentType(MediaType.TEXT_PLAIN);
        ResponseEntity<String> valid = testRestTemplate.exchange(
                "/services/private/system/optin", HttpMethod.POST,
                new HttpEntity<>("{}", validHeaders), String.class);
        assertEquals(valid.toString(), HttpStatus.OK, valid.getStatusCode());

        HttpHeaders invalidHeaders = new HttpHeaders();
        invalidHeaders.setBasicAuth("admin@shopizer.com", "invalid-password");
        invalidHeaders.setContentType(MediaType.TEXT_PLAIN);
        ResponseEntity<String> invalid = testRestTemplate.exchange(
                "/services/private/system/optin", HttpMethod.POST,
                new HttpEntity<>("{}", invalidHeaders), String.class);
        assertEquals(invalid.toString(), HttpStatus.UNAUTHORIZED, invalid.getStatusCode());
    }

    @Test
    public void healthAndSwaggerEndpointsAreAvailable() {
        ResponseEntity<String> health = testRestTemplate.getForEntity("/actuator/health", String.class);
        assertEquals(health.toString(), HttpStatus.OK, health.getStatusCode());
        assertTrue(health.getBody().contains("\"status\":\"UP\""));

        ResponseEntity<String> apiDocs = testRestTemplate.getForEntity("/v2/api-docs", String.class);
        assertEquals(apiDocs.toString(), HttpStatus.OK, apiDocs.getStatusCode());
    }

    private void registerCustomer(String email) {
        PersistableCustomer customer = new PersistableCustomer();
        customer.setEmailAddress(email);
        customer.setPassword(CUSTOMER_PASSWORD);
        customer.setGender(CustomerGender.M.name());
        customer.setLanguage("en");
        customer.setStoreCode(Constants.DEFAULT_STORE);
        Address billing = new Address();
        billing.setFirstName("Core");
        billing.setLastName("User");
        billing.setCountry("BE");
        customer.setBilling(billing);

        ResponseEntity<PersistableCustomer> registration = testRestTemplate.postForEntity(
                "/api/v1/customer/register", new HttpEntity<>(customer, getHeader()),
                PersistableCustomer.class);
        assertEquals(registration.toString(), HttpStatus.OK, registration.getStatusCode());
    }

    private HttpHeaders customerHeaders(String email) {
        ResponseEntity<AuthenticationResponse> response = testRestTemplate.postForEntity(
                "/api/v1/customer/login",
                new HttpEntity<>(new AuthenticationRequest(email, CUSTOMER_PASSWORD)),
                AuthenticationResponse.class);
        assertEquals(response.toString(), HttpStatus.OK, response.getStatusCode());
        assertNotNull(response.getBody());
        HttpHeaders headers = new HttpHeaders();
        headers.set("Authorization", "Bearer " + response.getBody().getToken());
        headers.setAcceptCharset(java.util.Collections.singletonList(StandardCharsets.UTF_8));
        return headers;
    }
}
