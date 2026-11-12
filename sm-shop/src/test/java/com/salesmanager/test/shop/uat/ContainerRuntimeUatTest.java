package com.salesmanager.test.shop.uat;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.salesmanager.core.business.modules.email.HtmlEmailSender;
import com.salesmanager.core.business.services.system.EmailService;
import com.salesmanager.shop.application.ShopApplication;
import java.math.BigDecimal;
import java.util.Collections;
import java.util.UUID;
import jakarta.inject.Inject;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.test.util.ReflectionTestUtils;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.mock;

@SpringBootTest(classes = ShopApplication.class, webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class ContainerRuntimeUatTest {
    @Inject private TestRestTemplate http;
    @Inject private EmailService emailService;
    private final ObjectMapper json = new ObjectMapper();
    private static final String PASSWORD = "Password123!";

    @Test
    void adminJwtReadsProfileAndMissingTokenIsRejected() throws Exception {
        String token = admin();
        JsonNode profile = request(HttpMethod.GET, "/api/v1/private/user/profile", null, token, 200);
        assertTrue(profile.toString().contains("admin@shopizer.com"));
        request(HttpMethod.GET, "/api/v1/private/user/profile", null, null, 401);
    }

    @Test
    void customerRegistersLogsInAndReadsOwnProfile() throws Exception {
        String email = email();
        JsonNode registered = request(HttpMethod.POST, "/api/v1/customer/register", customer(email), null, 200);
        assertJwt(registered.path("token").asText());
        String token = login("/api/v1/customer/login", email, PASSWORD);
        JsonNode profile = request(HttpMethod.GET, "/api/v1/auth/customer/profile", null, token, 200);
        assertEquals(email, profile.path("emailAddress").asText());
        assertEquals(registered.path("id").asLong(), profile.path("id").asLong());
    }

    @Test
    void publicCatalogAndCartPersistSkuQuantityAndPrice() throws Exception {
        JsonNode product = table();
        assertEquals("DEFAULT", request(HttpMethod.GET, "/api/v1/store/DEFAULT", null, null, 200).path("code").asText());
        assertTrue(request(HttpMethod.GET, "/api/v1/category?store=DEFAULT", null, null, 200).path("categories").isArray());
        JsonNode products = request(HttpMethod.GET, "/api/v1/products?store=DEFAULT", null, null, 200).path("products");
        assertTrue(products.isArray());
        boolean found = false;
        for (JsonNode candidate : products) if ("table1".equals(candidate.path("sku").asText())) found = true;
        assertTrue(found, "table1 must be publicly browsable");
        JsonNode created = request(HttpMethod.POST, "/api/v1/cart", item(1), null, 201);
        String code = created.path("code").asText();
        assertFalse(code.isEmpty());
        assertEquals(1, created.path("quantity").asInt());
        JsonNode updated = request(HttpMethod.PUT, "/api/v1/cart/" + code, item(2), null, 201);
        assertEquals(2, updated.path("quantity").asInt());
        JsonNode cart = request(HttpMethod.GET, "/api/v1/cart/" + code, null, null, 200);
        assertEquals(code, cart.path("code").asText());
        assertEquals(2, cart.path("quantity").asInt());
        assertEquals("table1", cart.path("products").get(0).path("sku").asText());
        BigDecimal expected = product.path("price").decimalValue().multiply(BigDecimal.valueOf(2));
        assertEquals(0, expected.compareTo(cart.path("total").decimalValue()));
    }

    @Test
    void customerChecksOutWithConfiguredOfflinePaymentAndReadsOrder() throws Exception {
        table();
        String admin = admin();
        ObjectNode configuration = json.createObjectNode().put("code", "moneyorder").put("active", true);
        configuration.putObject("integrationKeys").put("transaction", "AUTHORIZECAPTURE").put("address", "1 Test Street");
        configuration.putObject("integrationOptions");
        request(HttpMethod.POST, "/api/v1/private/modules/payment", configuration, admin, 200);
        String email = email();
        request(HttpMethod.POST, "/api/v1/customer/register", customer(email), null, 200);
        String token = login("/api/v1/customer/login", email, PASSWORD);
        JsonNode cart = request(HttpMethod.POST, "/api/v1/cart", item(1), null, 201);
        ObjectNode order = json.createObjectNode().put("currency", "CAD").put("customerAgreement", true);
        order.putObject("payment").put("paymentModule", "moneyorder").put("paymentType", "MONEYORDER")
                .put("transactionType", "AUTHORIZECAPTURE").put("amount", cart.path("total").decimalValue().toPlainString());
        Object sender = ReflectionTestUtils.getField(emailService, "sender");
        ReflectionTestUtils.setField(emailService, "sender", mock(HtmlEmailSender.class));
        try {
            JsonNode confirmation = request(HttpMethod.POST, "/api/v1/auth/cart/" + cart.path("code").asText() + "/checkout",
                    order, token, 200);
            long id = confirmation.path("id").asLong();
            assertTrue(id > 0);
            JsonNode persisted = request(HttpMethod.GET, "/api/v1/auth/orders/" + id, null, token, 200);
            assertEquals(id, persisted.path("id").asLong());
            assertEquals(1, persisted.path("products").size());
        } finally {
            ReflectionTestUtils.setField(emailService, "sender", sender);
        }
    }

    @Test
    void servicesAcceptsValidBasicCredentialsAndRejectsInvalidCredentials() throws Exception {
        HttpHeaders headers = headers(null);
        headers.setBasicAuth("admin@shopizer.com", "password");
        ResponseEntity<String> valid = http.exchange("/services/private/system/optin", HttpMethod.POST,
                new HttpEntity<>("{}", headers), String.class);
        assertEquals(200, valid.getStatusCode().value());
        assertEquals(200, json.readTree(valid.getBody()).path("status").asInt());
        headers.setBasicAuth("admin@shopizer.com", "invalid");
        assertEquals(401, http.exchange("/services/private/system/optin", HttpMethod.POST,
                new HttpEntity<>("{}", headers), String.class).getStatusCode().value());
    }

    @Test
    void healthAndOpenApiArePublic() throws Exception {
        JsonNode health = request(HttpMethod.GET, "/actuator/health", null, null, 200);
        assertEquals("UP", health.path("status").asText());
        assertEquals("UP", health.path("components").path("db").path("status").asText());
        JsonNode docs = request(HttpMethod.GET, "/v3/api-docs", null, null, 200);
        assertTrue(docs.path("openapi").asText().startsWith("3."));
    }

    private JsonNode table() throws Exception {
        String token = admin();
        JsonNode exists = request(HttpMethod.GET, "/api/v1/private/product/unique?code=table1", null, token, 200);
        if (!exists.path("exists").asBoolean()) {
            // The test H2 database initializes reference data, not the packaged demo catalog.
            ObjectNode product = json.createObjectNode().put("sku", "table1").put("available", true)
                    .put("price", 199).put("quantity", 1000);
            product.putObject("productSpecifications").put("manufacturer", "DEFAULT");
            product.putArray("descriptions").addObject().put("name", "Olive Table").put("language", "en");
            ObjectNode inventory = product.putObject("inventory").put("quantity", 1000).put("sku", "table1");
            inventory.putObject("price").put("defaultPrice", true).put("price", 199);
            request(HttpMethod.POST, "/api/v1/private/product", product, token, 201);
        }
        JsonNode product = request(HttpMethod.GET, "/api/v2/product/table1?store=DEFAULT", null, null, 200);
        assertEquals("table1", product.path("sku").asText());
        assertTrue(product.path("price").decimalValue().signum() > 0);
        return product;
    }

    private String admin() throws Exception {
        return login("/api/v1/private/login", "admin@shopizer.com", "password");
    }

    private String login(String path, String username, String password) throws Exception {
        String token = request(HttpMethod.POST, path,
                json.createObjectNode().put("username", username).put("password", password), null, 200).path("token").asText();
        assertJwt(token);
        return token;
    }

    private void assertJwt(String token) {
        assertEquals(3, token.split("\\.").length);
    }

    private HttpHeaders headers(String token) {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.setAccept(Collections.singletonList(MediaType.APPLICATION_JSON));
        if (token != null) headers.setBearerAuth(token);
        return headers;
    }

    private JsonNode request(HttpMethod method, String path, JsonNode body, String token, int status) throws Exception {
        ResponseEntity<String> response = http.exchange(path, method,
                new HttpEntity<>(body == null ? null : body.toString(), headers(token)), String.class);
        assertEquals(status, response.getStatusCode().value(), method + " " + path + ": " + response.getBody());
        return response.getBody() == null || response.getBody().isEmpty() ? json.nullNode() : json.readTree(response.getBody());
    }

    private ObjectNode item(int quantity) {
        return json.createObjectNode().put("product", "table1").put("quantity", quantity);
    }

    private String email() {
        return "uat" + UUID.randomUUID().toString().replace("-", "") + "@example.test";
    }

    private ObjectNode customer(String email) {
        ObjectNode customer = json.createObjectNode().put("emailAddress", email).put("password", PASSWORD)
                .put("repeatPassword", PASSWORD).put("gender", "M").put("language", "en").put("storeCode", "DEFAULT");
        customer.putObject("billing").put("firstName", "Uat").put("lastName", "Customer").put("country", "BE")
                .put("city", "Brussels").put("address", "1 Test Street").put("postalCode", "1000").put("phone", "123456789");
        return customer;
    }
}
