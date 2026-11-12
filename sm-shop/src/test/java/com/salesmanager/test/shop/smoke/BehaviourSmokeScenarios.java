package com.salesmanager.test.shop.smoke;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Disabled;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.http.*;
import static org.junit.jupiter.api.Assertions.*;

/** AB-533 scenarios shared by the embedded suite and the packaged H2/MySQL runner. */
public abstract class BehaviourSmokeScenarios {
    @Autowired protected TestRestTemplate http;
    private final ObjectMapper json = new ObjectMapper();
    private static final String PASSWORD = "clear123";

    @Test
    void adminLoginJwtCanAccessPrivateEndpoint() throws Exception {
        String token = admin();
        JsonNode customers = request(HttpMethod.GET, "/api/v1/private/customers", null, token, 200);
        assertTrue(customers.has("customers"));
    }

    @Test
    void customerRegistersLogsInAndReadsAuthenticatedProfile() throws Exception {
        String email = unique() + "@example.test";
        JsonNode registered = request(HttpMethod.POST, "/api/v1/customer/register", customer(email, true), null, 200);
        assertTrue(registered.path("id").asLong() > 0);
        assertJwt(registered.path("token").asText());
        String token = login("/api/v1/customer/login", email, PASSWORD);
        JsonNode profile = request(HttpMethod.GET, "/api/v1/auth/customer/profile", null, token, 200);
        assertEquals(email, profile.path("emailAddress").asText());
        assertEquals(registered.path("id").asLong(), profile.path("id").asLong());
    }

    @Test
    void missingTokenAndCustomerTokenCannotAccessAdminApi() throws Exception {
        assertDenied(exchange(HttpMethod.GET, "/api/v1/private/customers", null, null));
        String email = unique() + "@example.test";
        request(HttpMethod.POST, "/api/v1/customer/register", customer(email, true), null, 200);
        String token = login("/api/v1/customer/login", email, PASSWORD);
        assertDenied(exchange(HttpMethod.GET, "/api/v1/private/customers", null, token));
    }

    @Test
    void privateProductCreateReadUpdateDeletePersistsChanges() throws Exception {
        String token = admin();
        ObjectNode product = product(unique());
        long id = request(HttpMethod.POST, "/api/v1/private/product", product, token, 201).path("id").asLong();
        assertTrue(id > 0);
        try {
            assertTrue(request(HttpMethod.GET, "/api/v1/private/product/unique?code=" + product.path("sku").asText(),
                    null, token, 200).path("exists").asBoolean());
            JsonNode read = request(HttpMethod.GET, "/api/v2/private/product/" + id, null, token, 200);
            assertEquals(product.path("sku").asText(), read.path("sku").asText());
            product.put("id", id);
            ((ObjectNode) product.path("descriptions").get(0)).put("name", "Updated smoke product");
            request(HttpMethod.PUT, "/api/v1/private/product/" + id, product, token, 200);
            JsonNode updated = request(HttpMethod.GET, "/api/v2/private/product/" + id, null, token, 200);
            assertTrue(updated.toString().contains("Updated smoke product"), updated.toString());
        } finally {
            request(HttpMethod.DELETE, "/api/v1/private/product/" + id, null, token, 200);
        }
        assertFalse(request(HttpMethod.GET, "/api/v1/private/product/unique?code=" + product.path("sku").asText(),
                null, token, 200).path("exists").asBoolean());
    }

    @Test
    void cartCreateAddUpdateRemovePersistsQuantities() throws Exception {
        String token = admin();
        ObjectNode first = product(unique());
        ObjectNode second = product(unique());
        long firstId = request(HttpMethod.POST, "/api/v1/private/product", first, token, 201).path("id").asLong();
        long secondId = request(HttpMethod.POST, "/api/v1/private/product", second, token, 201).path("id").asLong();
        try {
            JsonNode cart = request(HttpMethod.POST, "/api/v1/cart", item(first, 1), null, 201);
            assertEquals(1, cart.path("quantity").asInt());
            String path = "/api/v1/cart/" + cart.path("code").asText();
            assertFalse(cart.path("code").asText().isEmpty());
            assertEquals(2, request(HttpMethod.PUT, path, item(second, 1), null, 201).path("quantity").asInt());
            assertEquals(4, request(HttpMethod.PUT, path, item(first, 3), null, 201).path("quantity").asInt());
            assertEquals(4, request(HttpMethod.GET, path, null, null, 200).path("quantity").asInt());
            request(HttpMethod.DELETE, path + "/product/" + first.path("sku").asText(), null, null, 204);
            assertEquals(1, request(HttpMethod.GET, path, null, null, 200).path("quantity").asInt());
            request(HttpMethod.DELETE, path + "/product/" + second.path("sku").asText(), null, null, 204);
        } finally {
            request(HttpMethod.DELETE, "/api/v1/private/product/" + firstId, null, token, 200);
            request(HttpMethod.DELETE, "/api/v1/private/product/" + secondId, null, token, 200);
        }
    }

    @Test
    void checkoutCreatesOrderThatAdminCanRead() throws Exception {
        checkout();
    }

    protected void checkout() throws Exception {
        String token = admin();
        // Configure the built-in offline payment method just as a merchant would.
        ObjectNode paymentConfig = json.createObjectNode().put("code", "moneyorder").put("active", true);
        paymentConfig.putObject("integrationKeys").put("transaction", "AUTHORIZECAPTURE").put("address", "1 Test Street");
        paymentConfig.putObject("integrationOptions");
        request(HttpMethod.POST, "/api/v1/private/modules/payment", paymentConfig, token, 200);
        ObjectNode product = product(unique());
        request(HttpMethod.POST, "/api/v1/private/product", product, token, 201);
        JsonNode cart = request(HttpMethod.POST, "/api/v1/cart", item(product, 1), null, 201);
        ObjectNode order = json.createObjectNode().put("currency", "CAD").put("customerAgreement", true);
        order.set("customer", customer(unique() + "@example.test", false));
        order.putObject("payment").put("paymentModule", "moneyorder").put("paymentType", "MONEYORDER")
                .put("transactionType", "AUTHORIZECAPTURE").put("amount", "10.00");
        JsonNode confirmation = request(HttpMethod.POST,
                "/api/v1/cart/" + cart.path("code").asText() + "/checkout", order, null, 200);
        long id = confirmation.path("id").asLong();
        assertTrue(id > 0, confirmation.toString());
        assertEquals(1, confirmation.path("products").size());
        JsonNode persisted = request(HttpMethod.GET, "/api/v1/private/orders/" + id, null, token, 200);
        assertEquals(id, persisted.path("id").asLong());
    }

    @Test
    @Disabled("KF-1: deleted-product private GET returns 500 instead of 404; see docs/upgrade/known-failures.md")
    void deletedPrivateProductShouldReturnNotFound() throws Exception {
        String token = admin();
        long id = request(HttpMethod.POST, "/api/v1/private/product", product(unique()), token, 201).path("id").asLong();
        request(HttpMethod.DELETE, "/api/v1/private/product/" + id, null, token, 200);
        request(HttpMethod.GET, "/api/v2/private/product/" + id, null, token, 404);
    }

    @Test
    void servicesBasicAuthAcceptsAdminAndRejectsMissingOrWrongCredentials() throws Exception {
        String path = "/services/private/system/optin";
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.setAccept(java.util.Collections.singletonList(MediaType.APPLICATION_JSON));
        assertEquals(401, http.exchange(path, HttpMethod.POST, new HttpEntity<>("{}", headers), String.class).getStatusCodeValue());
        headers.setBasicAuth("admin@shopizer.com", "incorrect");
        assertEquals(401, http.exchange(path, HttpMethod.POST, new HttpEntity<>("{}", headers), String.class).getStatusCodeValue());
        headers.setBasicAuth("admin@shopizer.com", "password");
        ResponseEntity<String> response = http.exchange(path, HttpMethod.POST, new HttpEntity<>("{}", headers), String.class);
        assertEquals(200, response.getStatusCodeValue(), response.getBody());
        assertEquals(200, json.readTree(response.getBody()).path("status").asInt());
    }

    @Test
    void actuatorHealthIsUp() throws Exception {
        assertEquals("UP", request(HttpMethod.GET, "/actuator/health", null, null, 200).path("status").asText());
    }

    private String admin() throws Exception {
        return login("/api/v1/private/login", "admin@shopizer.com", "password");
    }

    private String login(String path, String username, String password) throws Exception {
        JsonNode response = request(HttpMethod.POST, path,
                json.createObjectNode().put("username", username).put("password", password), null, 200);
        String token = response.path("token").asText();
        assertJwt(token);
        return token;
    }

    private void assertJwt(String token) {
        assertEquals(3, token.split("\\.").length, "Expected a signed JWT");
    }

    private void assertDenied(ResponseEntity<String> response) {
        assertTrue(response.getStatusCodeValue() == 401 || response.getStatusCodeValue() == 403,
                "Expected 401/403, got " + response.getStatusCodeValue());
    }

    private ResponseEntity<String> exchange(HttpMethod method, String path, JsonNode body, String token) {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.setAccept(java.util.Collections.singletonList(MediaType.APPLICATION_JSON));
        if (token != null) headers.setBearerAuth(token);
        return http.exchange(path, method, new HttpEntity<>(body == null ? null : body.toString(), headers), String.class);
    }

    private JsonNode request(HttpMethod method, String path, JsonNode body, String token, int status) throws Exception {
        ResponseEntity<String> response = exchange(method, path, body, token);
        assertEquals(status, response.getStatusCodeValue(), method + " " + path + ": " + response.getBody());
        return response.getBody() == null || response.getBody().isEmpty() ? json.nullNode() : json.readTree(response.getBody());
    }

    private ObjectNode customer(String email, boolean withPassword) {
        ObjectNode customer = json.createObjectNode().put("emailAddress", email).put("gender", "M")
                .put("language", "en").put("storeCode", "DEFAULT");
        if (withPassword) customer.put("password", PASSWORD).put("repeatPassword", PASSWORD);
        customer.putObject("billing").put("firstName", "Smoke").put("lastName", "Customer")
                .put("country", "BE").put("city", "Brussels").put("address", "1 Test Street")
                .put("postalCode", "1000").put("phone", "123456789");
        return customer;
    }

    private ObjectNode product(String sku) {
        ObjectNode product = json.createObjectNode().put("sku", sku).put("available", true)
                .put("price", 10).put("quantity", 100);
        product.putObject("productSpecifications").put("manufacturer", "DEFAULT");
        product.putArray("descriptions").addObject().put("name", sku).put("language", "en");
        ObjectNode inventory = product.putObject("inventory").put("quantity", 100).put("sku", sku);
        inventory.putObject("price").put("defaultPrice", true).put("price", 10);
        return product;
    }

    private ObjectNode item(JsonNode product, int quantity) {
        return json.createObjectNode().put("product", product.path("sku").asText()).put("quantity", quantity);
    }

    private String unique() {
        return "smoke" + UUID.randomUUID().toString().replace("-", "").substring(0, 12);
    }
}
