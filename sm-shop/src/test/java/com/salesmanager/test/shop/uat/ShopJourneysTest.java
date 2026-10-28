package com.salesmanager.test.shop.uat;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import java.util.UUID;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.test.context.junit4.SpringRunner;

import com.fasterxml.jackson.databind.JsonNode;
import com.salesmanager.core.business.constants.Constants;
import com.salesmanager.core.model.customer.CustomerGender;
import com.salesmanager.shop.model.catalog.product.ReadableProduct;
import com.salesmanager.shop.model.customer.PersistableCustomer;
import com.salesmanager.shop.model.customer.address.Address;
import com.salesmanager.shop.model.shoppingcart.PersistableShoppingCartItem;
import com.salesmanager.shop.model.shoppingcart.ReadableShoppingCart;
import com.salesmanager.shop.store.security.AuthenticationRequest;
import com.salesmanager.shop.store.security.AuthenticationResponse;
import com.salesmanager.test.shop.common.ServicesTestSupport;

@RunWith(SpringRunner.class)
public class ShopJourneysTest extends ServicesTestSupport {
    private static final String PRIVATE = "/api/v1/private/customers";

    @Test
    public void adminLoginAndMissingTokenBoundary() {
        String admin = login("/api/v1/private/login", "admin@shopizer.com", "password");
        status("admin private API", get(PRIVATE, admin), 200);
        denied("missing token private API", get(PRIVATE, null));
    }

    @Test
    public void customerWithAdminUsernameCannotAccessPrivateApi() {
        register("admin@shopizer.com");
        String customer = login("/api/v1/customer/login", "admin@shopizer.com", "clear123");
        try {
            denied("same-username customer private API", get(PRIVATE, customer));
        } finally {
            status("remove same-username customer", testRestTemplate.exchange("/api/v1/auth/customer/",
                    HttpMethod.DELETE, new HttpEntity<>(bearer(customer)), String.class), 200);
        }
    }

    @Test
    public void customerRegistersLogsInAndReadsProfile() {
        status("customer profile", get("/api/v1/auth/customer/profile", customer()), 200);
    }

    @Test
    public void visitorBrowsesStoreCatalogAndProduct() {
        ReadableProduct product = sampleProduct("uat-browse-" + UUID.randomUUID());
        ResponseEntity<JsonNode> store = json("/api/v1/store/DEFAULT");
        status("browse store", store, 200);
        assertEquals("DEFAULT", store.getBody().path("code").asText());
        ResponseEntity<JsonNode> categories = json("/api/v1/category?store=DEFAULT");
        status("browse categories", categories, 200);
        assertTrue(categories.getBody().path("categories").size() > 0);
        ResponseEntity<JsonNode> products = json("/api/v1/products?store=DEFAULT");
        status("browse product list", products, 200);
        assertTrue(products.getBody().path("products").size() > 0);
        ResponseEntity<JsonNode> detail = json("/api/v2/product/" + product.getSku());
        status("browse product detail", detail, 200);
        assertEquals(product.getSku(), detail.getBody().path("sku").asText());
    }

    @Test
    public void visitorCreatesUpdatesAndReadsCart() {
        ReadableProduct product = sampleProduct("uat-cart-" + UUID.randomUUID());
        ReadableShoppingCart cart = createCart(product);
        assertEquals(1, cart.getQuantity());
        PersistableShoppingCartItem item = new PersistableShoppingCartItem();
        item.setProduct(product.getSku());
        item.setQuantity(3);
        ResponseEntity<ReadableShoppingCart> updated = testRestTemplate.exchange(
                "/api/v1/cart/" + cart.getCode(), HttpMethod.PUT, new HttpEntity<>(item),
                ReadableShoppingCart.class);
        status("cart update", updated, 201);
        assertEquals(3, updated.getBody().getQuantity());
        ResponseEntity<ReadableShoppingCart> read = testRestTemplate.getForEntity(
                "/api/v1/cart/" + cart.getCode(), ReadableShoppingCart.class);
        status("cart read", read, 200);
        assertEquals(cart.getCode(), read.getBody().getCode());
        assertEquals(3, read.getBody().getQuantity());
        assertEquals(1, read.getBody().getProducts().size());
    }

    @Test
    public void legacyServicesRequireValidBasicCredentials() {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        HttpEntity<String> request = new HttpEntity<>("{}", headers);
        String path = "/services/private/system/optin";
        status("services valid basic credentials", testRestTemplate.withBasicAuth("admin@shopizer.com", "password")
                .postForEntity(path, request, String.class), 200);
        denied("services invalid basic credentials", testRestTemplate.withBasicAuth("admin@shopizer.com", "incorrect")
                .postForEntity(path, request, String.class));
    }

    @Test
    public void healthAndApiDocumentationAreAvailable() {
        ResponseEntity<JsonNode> health = json("/actuator/health");
        status("actuator health", health, 200);
        assertEquals("UP", health.getBody().path("status").asText());
        ResponseEntity<JsonNode> docs = json("/v2/api-docs");
        status("springfox documentation", docs, 200);
        assertEquals("2.0", docs.getBody().path("swagger").asText());
        assertTrue(docs.getBody().path("paths").size() > 0);
    }

    private ReadableShoppingCart createCart(ReadableProduct product) {
        PersistableShoppingCartItem item = new PersistableShoppingCartItem();
        item.setProduct(product.getSku());
        item.setQuantity(1);
        ResponseEntity<ReadableShoppingCart> response = testRestTemplate.postForEntity(
                "/api/v1/cart", new HttpEntity<>(item), ReadableShoppingCart.class);
        status("cart create", response, 201);
        assertNotNull(response.getBody().getCode());
        return response.getBody();
    }

    private String customer() {
        String email = "uat-" + UUID.randomUUID() + "@test.com";
        register(email);
        return login("/api/v1/customer/login", email, "clear123");
    }

    private void register(String email) {
        PersistableCustomer customer = new PersistableCustomer();
        customer.setEmailAddress(email);
        customer.setPassword("clear123");
        customer.setGender(CustomerGender.M.name());
        customer.setLanguage("en");
        customer.setStoreCode(Constants.DEFAULT_STORE);
        Address billing = new Address();
        billing.setFirstName("UAT");
        billing.setLastName("Customer");
        billing.setCountry("BE");
        billing.setAddress("1 Test Street");
        billing.setCity("Brussels");
        billing.setPostalCode("1000");
        customer.setBilling(billing);
        status("customer register", testRestTemplate.postForEntity("/api/v1/customer/register",
                new HttpEntity<>(customer), String.class), 200);
    }

    private String login(String path, String username, String password) {
        ResponseEntity<AuthenticationResponse> response = testRestTemplate.postForEntity(path,
                new HttpEntity<>(new AuthenticationRequest(username, password)), AuthenticationResponse.class);
        status(path, response, 200);
        assertNotNull(response.getBody().getToken());
        return response.getBody().getToken();
    }

    private HttpHeaders bearer(String token) {
        HttpHeaders headers = new HttpHeaders();
        if (token != null) {
            headers.setBearerAuth(token);
        }
        return headers;
    }

    private ResponseEntity<String> get(String path, String token) {
        return testRestTemplate.exchange(path, HttpMethod.GET, new HttpEntity<>(bearer(token)), String.class);
    }

    private ResponseEntity<JsonNode> json(String path) {
        return testRestTemplate.getForEntity(path, JsonNode.class);
    }

    private void status(String scenario, ResponseEntity<?> response, int expected) {
        System.out.println("UAT: " + scenario + " -> " + response.getStatusCodeValue());
        assertEquals(scenario, expected, response.getStatusCodeValue());
    }

    private void denied(String scenario, ResponseEntity<?> response) {
        int status = response.getStatusCodeValue();
        System.out.println("UAT: " + scenario + " -> " + status);
        assertTrue(scenario + " returned " + status, status == 401 || status == 403);
    }
}
