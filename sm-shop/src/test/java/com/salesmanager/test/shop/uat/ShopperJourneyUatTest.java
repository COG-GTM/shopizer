package com.salesmanager.test.shop.uat;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Collections;
import java.util.Map;
import java.util.UUID;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;

import com.salesmanager.test.shop.common.ServicesTestSupport;

public class ShopperJourneyUatTest extends ServicesTestSupport {
    private final ObjectMapper json = new ObjectMapper();

    @Test
    void adminJwtProtectsPrivateCustomerListing() throws Exception {
        String token = login("/api/v1/private/login", "admin@shopizer.com", "password");
        request(HttpMethod.GET, "/api/v1/private/customers", null, token, 200);
        assertDenied("/api/v1/private/customers", null);
    }

    @Test
    void zeroIdCustomerRegistersLogsInAndReadsOwnProfile() throws Exception {
        String email = unique() + "@example.com";
        JsonNode registered = register(email);
        assertTrue(registered.path("id").asLong() > 0);
        String token = login("/api/v1/customer/login", email, "clear123");
        JsonNode profile = request(HttpMethod.GET, "/api/v1/auth/customer/profile", null, token, 200);
        assertEquals(registered.path("id").asLong(), profile.path("id").asLong());
        assertEquals(email, profile.path("emailAddress").asText());
    }

    @Test
    void browseStoreCategoriesAndCreatedZeroIdProduct() throws Exception {
        String admin = login("/api/v1/private/login", "admin@shopizer.com", "password");
        String sku = unique();
        long category = createCategory(admin, sku);
        long product = createProduct(admin, sku, category);
        JsonNode store = request(HttpMethod.GET, "/api/v1/store/DEFAULT", null, null, 200);
        assertEquals("DEFAULT", store.path("code").asText());
        JsonNode categories = request(HttpMethod.GET, "/api/v1/category", null, null, 200);
        assertTrue(categories.path("categories").isArray());
        JsonNode listing = request(HttpMethod.GET, "/api/v1/products?category=" + category, null, null, 200);
        assertTrue(listing.path("products").isArray());
        assertTrue(listing.path("products").findValues("id").stream().anyMatch(id -> id.asLong() == product));
        JsonNode detail = request(HttpMethod.GET, "/api/v2/product/" + sku, null, null, 200);
        assertEquals(product, detail.path("id").asLong());
        assertEquals(sku, detail.path("sku").asText());
    }

    @Test
    void customerUpdatesCartChecksOutAndReadsOrder() throws Exception {
        String admin = login("/api/v1/private/login", "admin@shopizer.com", "password");
        String sku = unique();
        createProduct(admin, sku, createCategory(admin, sku));
        String email = unique() + "@example.com";
        JsonNode customer = register(email);
        String token = login("/api/v1/customer/login", email, "clear123");
        JsonNode cart = request(HttpMethod.POST, "/api/v1/cart", Map.of("product", sku, "quantity", 1), null, 201);
        String code = cart.path("code").asText();
        assertFalse(code.isEmpty());
        JsonNode updated = request(HttpMethod.PUT, "/api/v1/cart/" + code,
                Map.of("product", sku, "quantity", 2), null, 201);
        assertEquals(2, updated.path("quantity").asInt());
        JsonNode loaded = request(HttpMethod.GET, "/api/v1/cart/" + code, null, null, 200);
        assertEquals(2, loaded.path("quantity").asInt());
        JsonNode linked = request(HttpMethod.GET, "/api/v1/auth/customer/cart?cart=" + code, null, token, 200);
        assertEquals(customer.path("id").asLong(), linked.path("customer").asLong());
        request(HttpMethod.POST, "/api/v1/private/modules/payment",
                Map.of("code", "moneyorder", "active", true, "defaultSelected", true,
                        "integrationKeys", Map.of("address", "UAT offline payment",
                                "transaction", "AUTHORIZECAPTURE"), "integrationOptions", Collections.emptyMap()),
                admin, 200);
        String amount = loaded.path("total").asText();
        JsonNode confirmation = request(HttpMethod.POST, "/api/v1/auth/cart/" + code + "/checkout",
                Map.of("customerAgreement", true, "currency", "CAD", "payment",
                        Map.of("paymentModule", "moneyorder", "paymentType", "MONEYORDER",
                                "transactionType", "AUTHORIZECAPTURE", "amount", amount)), token, 200);
        long order = confirmation.path("id").asLong();
        assertTrue(order > 0);
        JsonNode read = request(HttpMethod.GET, "/api/v1/auth/orders/" + order, null, token, 200);
        assertEquals(order, read.path("id").asLong());
        assertEquals(customer.path("id").asLong(), read.path("customer").path("id").asLong());
    }

    @Test
    void legacyServicesAcceptValidBasicAuthAndRejectInvalidPassword() {
        HttpHeaders valid = new HttpHeaders();
        valid.setBasicAuth("admin@shopizer.com", "password");
        valid.setContentType(MediaType.APPLICATION_JSON);
        ResponseEntity<String> accepted = testRestTemplate.exchange("/services/private/system/optin",
                HttpMethod.POST, new HttpEntity<>("{}", valid), String.class);
        assertEquals(200, accepted.getStatusCodeValue(), accepted.getBody());
        HttpHeaders invalid = new HttpHeaders();
        invalid.setBasicAuth("admin@shopizer.com", "incorrect-password");
        invalid.setContentType(MediaType.APPLICATION_JSON);
        ResponseEntity<String> rejected = testRestTemplate.exchange("/services/private/system/optin",
                HttpMethod.POST, new HttpEntity<>("{}", invalid), String.class);
        assertTrue(rejected.getStatusCodeValue() == 401 || rejected.getStatusCodeValue() == 403);
    }

    @Test
    void healthAndSpringfoxDocumentationAreAvailable() throws Exception {
        assertEquals("UP", request(HttpMethod.GET, "/actuator/health", null, null, 200).path("status").asText());
        assertEquals("2.0", request(HttpMethod.GET, "/v2/api-docs", null, null, 200).path("swagger").asText());
    }

    private JsonNode register(String email) throws Exception {
        return request(HttpMethod.POST, "/api/v1/customer/register",
                Map.of("id", 0, "emailAddress", email, "password", "clear123", "repeatPassword", "clear123",
                        "gender", "M", "language", "en", "storeCode", "DEFAULT", "billing",
                        Map.of("firstName", "Uat", "lastName", "Shopper", "country", "CA", "zone", "QC",
                                "address", "123 Test Street", "city", "Montreal", "postalCode", "H1H1H1")),
                null, 200);
    }

    private long createCategory(String admin, String code) throws Exception {
        JsonNode created = request(HttpMethod.POST, "/api/v1/private/category",
                Map.of("id", 0, "code", code, "sortOrder", 1, "visible", true, "depth", 1,
                        "descriptions", Collections.singletonList(Map.of("language", "en", "name", code,
                                "friendlyUrl", code, "title", code))), admin, 201);
        long id = created.path("id").asLong();
        assertTrue(id > 0);
        return id;
    }

    private long createProduct(String admin, String sku, long category) throws Exception {
        JsonNode created = request(HttpMethod.POST, "/api/v1/private/product",
                Map.of("id", 0, "sku", sku, "available", true, "price", 10, "quantity", 100,
                        "productSpecifications", Map.of("manufacturer", "DEFAULT"),
                        "descriptions", Collections.singletonList(Map.of("language", "en", "name", sku)),
                        "categories", Collections.singletonList(Map.of("id", category)),
                        "inventory", Map.of("sku", sku, "quantity", 100,
                                "price", Map.of("price", 10, "defaultPrice", true))), admin, 201);
        long id = created.path("id").asLong();
        assertTrue(id > 0);
        return id;
    }

    private String login(String path, String username, String password) throws Exception {
        String token = request(HttpMethod.POST, path, Map.of("username", username, "password", password),
                null, 200).path("token").asText();
        assertFalse(token.isEmpty());
        return token;
    }

    private JsonNode request(HttpMethod method, String path, Object body, String token, int expected) throws Exception {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        if (token != null) headers.setBearerAuth(token);
        ResponseEntity<String> response = testRestTemplate.exchange(path, method,
                new HttpEntity<>(body, headers), String.class);
        assertEquals(expected, response.getStatusCodeValue(), method + " " + path + ": " + response.getBody());
        return json.readTree(response.getBody() == null || response.getBody().isEmpty() ? "{}" : response.getBody());
    }

    private void assertDenied(String path, String token) {
        HttpHeaders headers = new HttpHeaders();
        if (token != null) headers.setBearerAuth(token);
        int status = testRestTemplate.exchange(path, HttpMethod.GET,
                new HttpEntity<>(headers), String.class).getStatusCodeValue();
        assertTrue(status == 401 || status == 403, "Expected 401/403, got " + status);
    }

    private String unique() {
        return "uat_" + UUID.randomUUID().toString().replace("-", "");
    }
}
