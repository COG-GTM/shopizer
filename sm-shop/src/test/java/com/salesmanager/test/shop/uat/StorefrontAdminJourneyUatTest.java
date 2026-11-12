package com.salesmanager.test.shop.uat;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.salesmanager.core.business.modules.email.HtmlEmailSender;
import com.salesmanager.core.business.services.system.EmailService;
import com.salesmanager.shop.application.ShopApplication;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.http.*;
import org.springframework.test.util.ReflectionTestUtils;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@SpringBootTest(classes = ShopApplication.class, webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class StorefrontAdminJourneyUatTest {
    @Autowired private TestRestTemplate http;
    @Autowired private EmailService emailService;
    private final ObjectMapper json = new ObjectMapper();

    @Test
    void adminJwtEnforcesPrivateApiBoundary() throws Exception {
        assertTrue(request(HttpMethod.GET, "/api/v1/private/customers", null, admin(), 200).has("customers"));
        int status = exchange(HttpMethod.GET, "/api/v1/private/customers", null, null).getStatusCode().value();
        assertTrue(status == 401 || status == 403, "Expected denial, got " + status);
    }

    @Test
    void authenticatedCustomerBrowsesUpdatesCartChecksOutAndReadsOrder() throws Exception {
        String admin = admin();
        String email = unique() + "@example.test";
        ObjectNode customer = customer(email);
        long customerId = request(HttpMethod.POST, "/api/v1/customer/register", customer, null, 200).path("id").asLong();
        assertTrue(customerId > 0);
        String token = login("/api/v1/customer/login", email, "clear123");
        JsonNode profile = request(HttpMethod.GET, "/api/v1/auth/customer/profile", null, token, 200);
        assertEquals(customerId, profile.path("id").asLong());
        assertEquals(email, profile.path("emailAddress").asText());
        assertEquals("DEFAULT", request(HttpMethod.GET, "/api/v1/store/DEFAULT", null, null, 200).path("code").asText());
        assertTrue(request(HttpMethod.GET, "/api/v1/category?store=DEFAULT", null, null, 200).has("categories"));
        ObjectNode product = product(unique());
        long productId = request(HttpMethod.POST, "/api/v1/private/product", product, admin, 201).path("id").asLong();
        assertTrue(productId > 0);
        Object sender = ReflectionTestUtils.getField(emailService, "sender");
        HtmlEmailSender transport = mock(HtmlEmailSender.class);
        ReflectionTestUtils.setField(emailService, "sender", transport);
        try {
            assertTrue(request(HttpMethod.GET, "/api/v1/products?store=DEFAULT", null, null, 200).has("products"));
            assertEquals(product.path("sku").asText(), request(HttpMethod.GET, "/api/v2/product/" + product.path("sku").asText(),
                    null, null, 200).path("sku").asText());
            ObjectNode item = json.createObjectNode().put("product", product.path("sku").asText()).put("quantity", 1);
            JsonNode cart = request(HttpMethod.POST, "/api/v1/cart", item, token, 201);
            String path = "/api/v1/cart/" + cart.path("code").asText();
            assertFalse(cart.path("code").asText().isEmpty());
            item.put("quantity", 2);
            assertEquals(2, request(HttpMethod.PUT, path, item, token, 201).path("quantity").asInt());
            assertEquals(2, request(HttpMethod.GET, path, null, token, 200).path("quantity").asInt());
            ObjectNode paymentConfig = json.createObjectNode().put("code", "moneyorder").put("active", true);
            paymentConfig.putObject("integrationKeys").put("transaction", "AUTHORIZECAPTURE").put("address", "1 Test Street");
            paymentConfig.putObject("integrationOptions");
            request(HttpMethod.POST, "/api/v1/private/modules/payment", paymentConfig, admin, 200);
            ObjectNode order = json.createObjectNode().put("currency", "CAD").put("customerAgreement", true);
            order.set("customer", customer);
            order.putObject("payment").put("paymentModule", "moneyorder").put("paymentType", "MONEYORDER")
                    .put("transactionType", "AUTHORIZECAPTURE").put("amount", "20.00");
            JsonNode confirmation = request(HttpMethod.POST, "/api/v1/auth/cart/" + cart.path("code").asText()
                    + "/checkout", order, token, 200);
            long orderId = confirmation.path("id").asLong();
            assertTrue(orderId > 0, confirmation.toString());
            assertEquals(1, confirmation.path("products").size());
            assertEquals(orderId, request(HttpMethod.GET, "/api/v1/auth/orders/" + orderId, null, token, 200).path("id").asLong());
            assertEquals(orderId, request(HttpMethod.GET, "/api/v1/private/orders/" + orderId, null, admin, 200).path("id").asLong());
            verify(transport, timeout(5000).times(2)).send(any());
        } finally {
            ReflectionTestUtils.setField(emailService, "sender", sender);
            request(HttpMethod.DELETE, "/api/v1/private/product/" + productId, null, admin, 200);
        }
    }

    @Test
    void servicesBasicAuthAndPublicOperationalEndpointsWork() throws Exception {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.setAccept(java.util.List.of(MediaType.APPLICATION_JSON));
        String path = "/services/private/system/optin";
        headers.setBasicAuth("admin@shopizer.com", "wrong");
        assertEquals(401, http.exchange(path, HttpMethod.POST, new HttpEntity<>("{}", headers), String.class).getStatusCode().value());
        headers.setBasicAuth("admin@shopizer.com", "password");
        ResponseEntity<String> response = http.exchange(path, HttpMethod.POST, new HttpEntity<>("{}", headers), String.class);
        assertEquals(200, response.getStatusCode().value());
        assertEquals(200, json.readTree(response.getBody()).path("status").asInt());
        assertEquals("UP", request(HttpMethod.GET, "/actuator/health", null, null, 200).path("status").asText());
        assertFalse(request(HttpMethod.GET, "/v3/api-docs", null, null, 200).path("openapi").asText().isEmpty());
    }

    private String admin() throws Exception {
        return login("/api/v1/private/login", "admin@shopizer.com", "password");
    }

    private String login(String path, String username, String password) throws Exception {
        String token = request(HttpMethod.POST, path, json.createObjectNode().put("username", username)
                .put("password", password), null, 200).path("token").asText();
        assertEquals(3, token.split("\\.").length);
        return token;
    }

    private ResponseEntity<String> exchange(HttpMethod method, String path, JsonNode body, String token) {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.setAccept(java.util.List.of(MediaType.APPLICATION_JSON));
        if (token != null) headers.setBearerAuth(token);
        return http.exchange(path, method, new HttpEntity<>(body == null ? null : body.toString(), headers), String.class);
    }

    private JsonNode request(HttpMethod method, String path, JsonNode body, String token, int status) throws Exception {
        ResponseEntity<String> response = exchange(method, path, body, token);
        assertEquals(status, response.getStatusCode().value(), method + " " + path + ": " + response.getBody());
        return response.getBody() == null || response.getBody().isEmpty() ? json.nullNode() : json.readTree(response.getBody());
    }

    private ObjectNode customer(String email) {
        ObjectNode customer = json.createObjectNode().put("emailAddress", email).put("gender", "M")
                .put("language", "en").put("storeCode", "DEFAULT").put("password", "clear123").put("repeatPassword", "clear123");
        customer.putObject("billing").put("firstName", "Journey").put("lastName", "Customer")
                .put("country", "BE").put("city", "Brussels").put("address", "1 Test Street")
                .put("postalCode", "1000").put("phone", "123456789");
        return customer;
    }

    private ObjectNode product(String sku) {
        ObjectNode product = json.createObjectNode().put("sku", sku).put("available", true).put("price", 10).put("quantity", 100);
        product.putObject("productSpecifications").put("manufacturer", "DEFAULT");
        product.putArray("descriptions").addObject().put("name", sku).put("language", "en");
        ObjectNode inventory = product.putObject("inventory").put("quantity", 100).put("sku", sku);
        inventory.putObject("price").put("defaultPrice", true).put("price", 10);
        return product;
    }

    private String unique() {
        return "journey" + UUID.randomUUID().toString().replace("-", "").substring(0, 12);
    }
}
