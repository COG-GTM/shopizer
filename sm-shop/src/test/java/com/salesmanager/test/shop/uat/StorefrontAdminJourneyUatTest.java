package com.salesmanager.test.shop.uat;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.salesmanager.shop.application.ShopApplication;
import com.salesmanager.core.business.modules.email.HtmlEmailSender;
import com.salesmanager.core.business.services.system.EmailService;
import java.util.Collections;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.http.*;
import org.springframework.test.util.ReflectionTestUtils;
import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest(classes = ShopApplication.class, webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class StorefrontAdminJourneyUatTest {
    @Autowired private TestRestTemplate http;
    @Autowired private EmailService emailService;
    private final ObjectMapper json = new ObjectMapper();
    private static final String PASSWORD = "clear123";

    @Test
    void adminJwtAllowsPrivateAccessAndRejectsMissingToken() throws Exception {
        String token = login("/api/v1/private/login", "admin@shopizer.com", "password");
        String path = "/api/v1/private/customers";
        assertTrue(request(HttpMethod.GET, path, null, token, 200).path("customers").isArray());
        denied(exchange(HttpMethod.GET, path, null, null));
    }

    @Test
    void customerBrowsesBuildsCartChecksOutAndBothRolesReadOrder() throws Exception {
        String admin = login("/api/v1/private/login", "admin@shopizer.com", "password");
        String email = unique() + "@example.test";
        ObjectNode customer = customer(email);
        JsonNode registered = request(HttpMethod.POST, "/api/v1/customer/register", customer, null, 200);
        long customerId = registered.path("id").asLong();
        assertTrue(customerId > 0);
        jwt(registered.path("token").asText());
        String token = login("/api/v1/customer/login", email, PASSWORD);
        JsonNode profile = request(HttpMethod.GET, "/api/v1/auth/customer/profile", null, token, 200);
        assertEquals(email, profile.path("emailAddress").asText());
        assertEquals(customerId, profile.path("id").asLong());
        assertEquals("DEFAULT", request(HttpMethod.GET, "/api/v1/store/DEFAULT", null, null, 200).path("code").asText());
        assertTrue(request(HttpMethod.GET, "/api/v1/category", null, null, 200).path("categories").isArray());

        ObjectNode product = product(unique());
        long productId = request(HttpMethod.POST, "/api/v1/private/product", product, admin, 201).path("id").asLong();
        assertTrue(productId > 0);
        JsonNode detail = request(HttpMethod.GET, "/api/v2/product/" + product.path("sku").asText(), null, null, 200);
        assertTrue(detail.path("id").isIntegralNumber());
        assertEquals(productId, detail.path("id").asLong());
        assertEquals(product.path("sku").asText(), detail.path("sku").asText());
        assertTrue(detail.path("description").isObject());
        assertEquals(product.path("sku").asText(), detail.path("description").path("name").asText());
        assertTrue(detail.path("finalPrice").isTextual());
        JsonNode listed = request(HttpMethod.GET, "/api/v1/products?store=DEFAULT&count=100", null, null, 200);
        assertTrue(listed.path("products").isArray());
        boolean found = false;
        for (JsonNode entry : listed.path("products")) {
            if (entry.path("id").asLong() == productId) found = true;
        }
        assertTrue(found, "Created product must be in the storefront list");
        JsonNode cart = request(HttpMethod.POST, "/api/v1/cart", item(product, 1), null, 201);
        String cartPath = "/api/v1/cart/" + cart.path("code").asText();
        assertFalse(cart.path("code").asText().isEmpty());
        assertEquals(1, cart.path("quantity").asInt());
        assertEquals(2, request(HttpMethod.PUT, cartPath, item(product, 2), null, 201).path("quantity").asInt());
        assertEquals(3, request(HttpMethod.PUT, cartPath, item(product, 3), null, 201).path("quantity").asInt());
        assertEquals(3, request(HttpMethod.GET, cartPath, null, null, 200).path("quantity").asInt());

        ObjectNode paymentConfig = json.createObjectNode().put("code", "moneyorder").put("active", true);
        paymentConfig.putObject("integrationKeys").put("transaction", "AUTHORIZECAPTURE").put("address", "1 Test Street");
        paymentConfig.putObject("integrationOptions");
        request(HttpMethod.POST, "/api/v1/private/modules/payment", paymentConfig, admin, 200);
        ObjectNode order = json.createObjectNode().put("currency", "CAD").put("customerAgreement", true);
        order.set("customer", customer);
        order.putObject("payment").put("paymentModule", "moneyorder").put("paymentType", "MONEYORDER")
                .put("transactionType", "AUTHORIZECAPTURE").put("amount", "30.00");
        Object sender = ReflectionTestUtils.getField(emailService, "sender");
        ReflectionTestUtils.setField(emailService, "sender", org.mockito.Mockito.mock(HtmlEmailSender.class));
        try {
            JsonNode confirmation = request(HttpMethod.POST, "/api/v1/auth/cart/" + cart.path("code").asText()
                    + "/checkout", order, token, 200);
            long orderId = confirmation.path("id").asLong();
            assertTrue(orderId > 0, confirmation.toString());
            assertEquals(orderId, request(HttpMethod.GET, "/api/v1/auth/orders/" + orderId, null, token, 200).path("id").asLong());
            assertEquals(orderId, request(HttpMethod.GET, "/api/v1/private/orders/" + orderId, null, admin, 200).path("id").asLong());
        } finally {
            ReflectionTestUtils.setField(emailService, "sender", sender);
        }
    }

    @Test
    void servicesBasicAuthenticationAndPublicOperationalEndpoints() throws Exception {
        String path = "/services/private/system/optin";
        HttpHeaders headers = headers();
        assertEquals(401, http.exchange(path, HttpMethod.POST, new HttpEntity<>("{}", headers), String.class).getStatusCodeValue());
        headers.setBasicAuth("admin@shopizer.com", "incorrect");
        assertEquals(401, http.exchange(path, HttpMethod.POST, new HttpEntity<>("{}", headers), String.class).getStatusCodeValue());
        headers.setBasicAuth("admin@shopizer.com", "password");
        ResponseEntity<String> response = http.exchange(path, HttpMethod.POST, new HttpEntity<>("{}", headers), String.class);
        assertEquals(200, response.getStatusCodeValue(), response.getBody());
        assertEquals(200, json.readTree(response.getBody()).path("status").asInt());
        assertEquals("UP", request(HttpMethod.GET, "/actuator/health", null, null, 200).path("status").asText());
        assertEquals("2.0", request(HttpMethod.GET, "/v2/api-docs", null, null, 200).path("swagger").asText());
    }

    private String login(String path, String username, String password) throws Exception {
        String token = request(HttpMethod.POST, path, json.createObjectNode().put("username", username)
                .put("password", password), null, 200).path("token").asText();
        jwt(token);
        return token;
    }

    private void jwt(String token) {
        assertEquals(3, token.split("\\.").length);
    }

    private void denied(ResponseEntity<String> response) {
        assertTrue(response.getStatusCodeValue() == 401 || response.getStatusCodeValue() == 403,
                "Expected 401/403, got " + response.getStatusCodeValue() + ": " + response.getBody());
    }

    private HttpHeaders headers() {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.setAccept(Collections.singletonList(MediaType.APPLICATION_JSON));
        return headers;
    }

    private ResponseEntity<String> exchange(HttpMethod method, String path, JsonNode body, String token) {
        HttpHeaders headers = headers();
        if (token != null) headers.setBearerAuth(token);
        return http.exchange(path, method, new HttpEntity<>(body == null ? null : body.toString(), headers), String.class);
    }

    private JsonNode request(HttpMethod method, String path, JsonNode body, String token, int status) throws Exception {
        ResponseEntity<String> response = exchange(method, path, body, token);
        assertEquals(status, response.getStatusCodeValue(), method + " " + path + ": " + response.getBody());
        return response.getBody() == null || response.getBody().isEmpty() ? json.nullNode() : json.readTree(response.getBody());
    }

    private ObjectNode customer(String email) {
        ObjectNode customer = json.createObjectNode().put("emailAddress", email).put("gender", "M")
                .put("language", "en").put("storeCode", "DEFAULT").put("password", PASSWORD).put("repeatPassword", PASSWORD);
        customer.putObject("billing").put("firstName", "Journey").put("lastName", "Customer")
                .put("country", "BE").put("city", "Brussels").put("address", "1 Test Street")
                .put("postalCode", "1000").put("phone", "123456789");
        return customer;
    }

    private ObjectNode product(String sku) {
        ObjectNode product = json.createObjectNode().put("sku", sku).put("available", true).put("price", 10).put("quantity", 100);
        product.putObject("productSpecifications").put("manufacturer", "DEFAULT");
        product.putArray("descriptions").addObject().put("name", sku).put("language", "en").put("friendlyUrl", sku);
        ObjectNode inventory = product.putObject("inventory").put("quantity", 100).put("sku", sku);
        inventory.putObject("price").put("defaultPrice", true).put("price", 10);
        return product;
    }

    private ObjectNode item(JsonNode product, int quantity) {
        return json.createObjectNode().put("product", product.path("sku").asText()).put("quantity", quantity);
    }

    private String unique() {
        return "uat" + UUID.randomUUID().toString().replace("-", "").substring(0, 12);
    }
}
