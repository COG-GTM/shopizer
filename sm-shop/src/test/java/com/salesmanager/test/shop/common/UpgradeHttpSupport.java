package com.salesmanager.test.shop.common;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.salesmanager.shop.application.ShopApplication;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.http.*;
import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest(classes = ShopApplication.class, webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
public abstract class UpgradeHttpSupport {
    @Autowired protected TestRestTemplate http;
    protected final ObjectMapper json = new ObjectMapper();
    protected static final String PASSWORD = "clear123";

    protected ResponseEntity<String> exchange(HttpMethod method, String path, JsonNode body, String token) {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.setAccept(java.util.List.of(MediaType.APPLICATION_JSON));
        if (token != null) headers.setBearerAuth(token);
        return http.exchange(path, method, new HttpEntity<>(body == null ? null : body.toString(), headers), String.class);
    }

    protected JsonNode request(HttpMethod method, String path, JsonNode body, String token, int status) throws Exception {
        ResponseEntity<String> response = exchange(method, path, body, token);
        assertEquals(status, response.getStatusCode().value(), method + " " + path + ": " + response.getBody());
        return response.getBody() == null || response.getBody().isEmpty() ? json.nullNode() : json.readTree(response.getBody());
    }

    protected String login(String path, String username, String password) throws Exception {
        String token = request(HttpMethod.POST, path,
                json.createObjectNode().put("username", username).put("password", password), null, 200).path("token").asText();
        assertEquals(3, token.split("\\.").length);
        return token;
    }

    protected String admin() throws Exception {
        return login("/api/v1/private/login", "admin@shopizer.com", "password");
    }

    protected ObjectNode customer(String email) {
        ObjectNode customer = json.createObjectNode().put("emailAddress", email).put("gender", "M")
                .put("language", "en").put("storeCode", "DEFAULT").put("password", PASSWORD).put("repeatPassword", PASSWORD);
        customer.putObject("billing").put("firstName", "Upgrade").put("lastName", "Customer")
                .put("country", "BE").put("city", "Brussels").put("address", "1 Test Street")
                .put("postalCode", "1000").put("phone", "123456789");
        return customer;
    }

    protected ObjectNode product(String sku) {
        ObjectNode product = json.createObjectNode().put("sku", sku).put("available", true)
                .put("price", 10).put("quantity", 100);
        product.putObject("productSpecifications").put("manufacturer", "DEFAULT");
        product.putArray("descriptions").addObject().put("name", sku).put("language", "en").put("friendlyUrl", sku);
        product.putObject("inventory").put("quantity", 100).put("sku", sku)
                .putObject("price").put("defaultPrice", true).put("price", 10);
        return product;
    }

    protected ObjectNode item(String sku, int quantity) {
        return json.createObjectNode().put("product", sku).put("quantity", quantity);
    }

    protected String unique() { return "ab544" + UUID.randomUUID().toString().replace("-", "").substring(0, 12); }

    protected void denied(ResponseEntity<String> response) {
        assertTrue(response.getStatusCode().value() == 401 || response.getStatusCode().value() == 403,
                response.getStatusCode() + ": " + response.getBody());
    }

    protected void basicAuthentication() throws Exception {
        String path = "/services/private/system/optin";
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.setAccept(java.util.List.of(MediaType.APPLICATION_JSON));
        headers.setBasicAuth("admin@shopizer.com", "wrong");
        assertEquals(401, http.exchange(path, HttpMethod.POST, new HttpEntity<>("{}", headers), String.class).getStatusCode().value());
        headers.setBasicAuth("admin@shopizer.com", "password");
        ResponseEntity<String> response = http.exchange(path, HttpMethod.POST, new HttpEntity<>("{}", headers), String.class);
        assertEquals(200, response.getStatusCode().value(), response.getBody());
        assertEquals(200, json.readTree(response.getBody()).path("status").asInt());
    }
}
