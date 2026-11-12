package com.salesmanager.test.shop.uat;

import static org.junit.jupiter.api.Assertions.*;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.salesmanager.core.business.modules.email.HtmlEmailSender;
import com.salesmanager.core.business.services.system.EmailService;
import com.salesmanager.test.shop.common.ServicesTestSupport;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.*;
import org.springframework.test.util.ReflectionTestUtils;

class Java21UpgradeUatTest extends ServicesTestSupport {
    private final ObjectMapper json = new ObjectMapper();
    @Autowired private EmailService emailService;

    @Test
    void concurrentAdminAndCustomerRequestsKeepTheirIdentitiesAndAuthorizationBoundaries() throws Exception {
        String admin = login("private", "admin@shopizer.com");
        String email = unique() + "@example.test";
        JsonNode registered = request(HttpMethod.POST, "/api/v1/customer/register", customer(email), null, 200);
        String customer = login("customer", email);
        List<CompletableFuture<Void>> requests = new ArrayList<>();
        for (int i = 0; i < 12; i++) {
            requests.add(CompletableFuture.runAsync(() -> {
                try {
                    assertTrue(request(HttpMethod.GET, "/api/v1/private/customers", null, admin, 200).has("customers"));
                    JsonNode profile = request(HttpMethod.GET, "/api/v1/auth/customer/profile", null, customer, 200);
                    assertEquals(email, profile.path("emailAddress").asText());
                    assertEquals(registered.path("id"), profile.path("id"));
                    request(HttpMethod.GET, "/api/v1/private/customers", null, customer, 401);
                    request(HttpMethod.GET, "/api/v1/auth/customer/profile", null, admin, 401);
                } catch (Exception e) {
                    throw new java.util.concurrent.CompletionException(e);
                }
            }));
        }
        CompletableFuture.allOf(requests.toArray(CompletableFuture[]::new)).get(30, TimeUnit.SECONDS);
        request(HttpMethod.GET, "/api/v1/private/customers", null, null, 401);
        request(HttpMethod.GET, "/api/v1/auth/customer/profile", null, null, 401);
    }

    @Test
    void customerCanBrowseAndCreateUpdateAndReadCart() throws Exception {
        assertEquals("DEFAULT", request(HttpMethod.GET, "/api/v1/store/DEFAULT", null, null, 200).path("code").asText());
        assertTrue(request(HttpMethod.GET, "/api/v1/category?store=DEFAULT", null, null, 200).has("categories"));
        assertTrue(request(HttpMethod.GET, "/api/v1/products?store=DEFAULT", null, null, 200).has("products"));
        String admin = login("private", "admin@shopizer.com");
        String sku = unique();
        long id = request(HttpMethod.POST, "/api/v1/private/product", productBody(sku), admin, 201).path("id").asLong();
        try {
            assertEquals(sku, request(HttpMethod.GET, "/api/v2/product/" + sku, null, null, 200).path("sku").asText());
            String path = "/api/v1/cart/" + request(HttpMethod.POST, "/api/v1/cart", item(sku, 1), null, 201).path("code").asText();
            assertEquals(3, request(HttpMethod.PUT, path, item(sku, 3), null, 201).path("quantity").asInt());
            assertEquals(3, request(HttpMethod.GET, path, null, null, 200).path("quantity").asInt());
            request(HttpMethod.DELETE, path + "/product/" + sku, null, null, 204);
        } finally {
            request(HttpMethod.DELETE, "/api/v1/private/product/" + id, null, admin, 200);
        }
    }

    @Test
    void authenticatedCustomerChecksOutAndReadsOwnOrder() throws Exception {
        String admin = login("private", "admin@shopizer.com");
        String email = unique() + "@example.test";
        request(HttpMethod.POST, "/api/v1/customer/register", customer(email), null, 200);
        String customer = login("customer", email);
        ObjectNode config = json.createObjectNode().put("code", "moneyorder").put("active", true);
        config.putObject("integrationKeys").put("transaction", "AUTHORIZECAPTURE").put("address", "1 Test Street");
        config.putObject("integrationOptions");
        request(HttpMethod.POST, "/api/v1/private/modules/payment", config, admin, 200);
        String sku = unique();
        request(HttpMethod.POST, "/api/v1/private/product", productBody(sku), admin, 201);
        String cart = request(HttpMethod.POST, "/api/v1/cart", item(sku, 1), null, 201).path("code").asText();
        ObjectNode order = json.createObjectNode().put("currency", "CAD").put("customerAgreement", true);
        order.putObject("payment").put("paymentModule", "moneyorder").put("paymentType", "MONEYORDER")
                .put("transactionType", "AUTHORIZECAPTURE").put("amount", "10.00");
        Object sender = ReflectionTestUtils.getField(emailService, "sender");
        HtmlEmailSender transport = org.mockito.Mockito.mock(HtmlEmailSender.class);
        ReflectionTestUtils.setField(emailService, "sender", transport);
        try {
            JsonNode confirmation = request(HttpMethod.POST, "/api/v1/auth/cart/" + cart + "/checkout", order, customer, 200);
            long id = confirmation.path("id").asLong();
            assertTrue(id > 0);
            assertEquals(1, confirmation.path("products").size());
            assertEquals(id, request(HttpMethod.GET, "/api/v1/auth/orders/" + id, null, customer, 200).path("id").asLong());
            assertEquals(id, request(HttpMethod.GET, "/api/v1/private/orders/" + id, null, admin, 200).path("id").asLong());
            org.mockito.Mockito.verify(transport, org.mockito.Mockito.timeout(5000).times(2))
                    .send(org.mockito.ArgumentMatchers.any());
        } finally {
            ReflectionTestUtils.setField(emailService, "sender", sender);
        }
    }

    @Test
    void basicAuthHealthAndApiDocsRemainAvailable() throws Exception {
        String path = "/services/private/system/optin";
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.setAccept(List.of(MediaType.APPLICATION_JSON));
        headers.setBasicAuth("admin@shopizer.com", "incorrect");
        assertEquals(401, testRestTemplate.postForEntity(path, new HttpEntity<>("{}", headers), String.class).getStatusCode().value());
        headers.setBasicAuth("admin@shopizer.com", "password");
        assertEquals(200, testRestTemplate.postForEntity(path, new HttpEntity<>("{}", headers), String.class).getStatusCode().value());
        assertEquals("UP", request(HttpMethod.GET, "/actuator/health", null, null, 200).path("status").asText());
        assertTrue(request(HttpMethod.GET, "/v3/api-docs", null, null, 200).has("paths"));
    }

    private String login(String realm, String email) throws Exception {
        String password = realm.equals("private") ? "password" : "clear123";
        String token = request(HttpMethod.POST, "/api/v1/" + realm + "/login",
                json.createObjectNode().put("username", email).put("password", password), null, 200).path("token").asText();
        JsonNode claims = json.readTree(java.util.Base64.getUrlDecoder().decode(token.split("\\.")[1]));
        assertEquals(email, claims.path("sub").asText());
        assertTrue(claims.path("aud").isTextual());
        assertEquals("api", claims.path("aud").asText());
        return token;
    }

    private JsonNode request(HttpMethod method, String path, JsonNode body, String token, int status) throws Exception {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.setAccept(List.of(MediaType.APPLICATION_JSON));
        if (token != null) headers.setBearerAuth(token);
        ResponseEntity<String> response = testRestTemplate.exchange(path, method,
                new HttpEntity<>(body == null ? null : body.toString(), headers), String.class);
        assertEquals(status, response.getStatusCode().value(), method + " " + path + ": " + response.getBody());
        return response.getBody() == null || response.getBody().isEmpty() ? json.nullNode() : json.readTree(response.getBody());
    }

    private ObjectNode customer(String email) {
        ObjectNode customer = json.createObjectNode().put("emailAddress", email).put("gender", "M")
                .put("language", "en").put("storeCode", "DEFAULT").put("password", "clear123").put("repeatPassword", "clear123");
        customer.putObject("billing").put("firstName", "Upgrade").put("lastName", "Customer").put("country", "BE")
                .put("city", "Brussels").put("address", "1 Test Street").put("postalCode", "1000").put("phone", "123456789");
        return customer;
    }

    private ObjectNode productBody(String sku) {
        ObjectNode product = json.createObjectNode().put("sku", sku).put("available", true).put("price", 10).put("quantity", 100);
        product.putObject("productSpecifications").put("manufacturer", "DEFAULT");
        product.putArray("descriptions").addObject().put("name", sku).put("language", "en");
        product.putObject("inventory").put("quantity", 100).put("sku", sku)
                .putObject("price").put("defaultPrice", true).put("price", 10);
        return product;
    }

    private ObjectNode item(String sku, int quantity) {
        return json.createObjectNode().put("product", sku).put("quantity", quantity);
    }

    private String unique() { return "upgrade" + UUID.randomUUID().toString().replace("-", "").substring(0, 12); }
}
