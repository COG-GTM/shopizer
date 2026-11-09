package com.salesmanager.test.shop.uat;

import static org.junit.jupiter.api.Assertions.*;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.http.*;
import com.fasterxml.jackson.databind.JsonNode;
import com.salesmanager.shop.model.catalog.product.ReadableProduct;
import com.salesmanager.test.shop.common.Pr350IntegrationSupport;

class ShopHttpUatTest extends Pr350IntegrationSupport {
    private ResponseEntity<JsonNode> post(String path, Object body, HttpHeaders headers) {
        return testRestTemplate.postForEntity(path, new HttpEntity<>(body, headers), JsonNode.class);
    }

    private HttpHeaders jsonHeaders() {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        return headers;
    }

    @Test
    void adminJwtProtectsPrivateEndpoints() {
        ResponseEntity<JsonNode> login = post("/api/v1/private/login",
                Map.of("username", "admin@shopizer.com", "password", "password"), jsonHeaders());
        assertEquals(200, login.getStatusCode().value(), String.valueOf(login.getBody()));
        assertFalse(login.getBody().path("token").asText().isBlank());
        HttpHeaders authenticated = jsonHeaders();
        authenticated.setBearerAuth(login.getBody().path("token").asText());
        ResponseEntity<JsonNode> privateResponse = testRestTemplate.exchange("/api/v1/private/customers",
                HttpMethod.GET, new HttpEntity<>(authenticated), JsonNode.class);
        assertEquals(200, privateResponse.getStatusCode().value(), String.valueOf(privateResponse.getBody()));
        assertTrue(privateResponse.getBody().has("customers"));
        assertEquals(401, testRestTemplate.getForEntity("/api/v1/private/customers", String.class).getStatusCode().value());
    }

    @Test
    void registersCustomerLogsInAndReadsProfile() {
        String email = "pr350-" + UUID.randomUUID() + "@example.com";
        ResponseEntity<JsonNode> registered = post("/api/v1/customer/register", Map.of(
                "emailAddress", email, "password", "clear123", "gender", "M", "language", "en",
                "storeCode", "DEFAULT", "billing", Map.of("firstName", "Test", "lastName", "Customer",
                        "country", "CA", "address", "1 Test Street", "city", "Toronto", "postalCode", "M5V1A1")),
                jsonHeaders());
        assertEquals(200, registered.getStatusCode().value(), String.valueOf(registered.getBody()));
        ResponseEntity<JsonNode> login = post("/api/v1/customer/login",
                Map.of("username", email, "password", "clear123"), jsonHeaders());
        assertEquals(200, login.getStatusCode().value(), String.valueOf(login.getBody()));
        assertFalse(login.getBody().path("token").asText().isBlank());
        HttpHeaders headers = jsonHeaders();
        headers.setBearerAuth(login.getBody().path("token").asText());
        ResponseEntity<JsonNode> profile = testRestTemplate.exchange("/api/v1/auth/customer/profile",
                HttpMethod.GET, new HttpEntity<>(headers), JsonNode.class);
        assertEquals(200, profile.getStatusCode().value(), String.valueOf(profile.getBody()));
        assertEquals(email, profile.getBody().path("emailAddress").asText());
    }

    @Test
    void browsesStoreCategoriesProductsAndUpdatesCart() {
        ReadableProduct product = sampleProduct("uat-browse-cart");
        ResponseEntity<JsonNode> store = testRestTemplate.getForEntity("/api/v1/store/DEFAULT", JsonNode.class);
        assertEquals(200, store.getStatusCode().value());
        assertEquals("DEFAULT", store.getBody().path("code").asText());
        ResponseEntity<JsonNode> categories = testRestTemplate.getForEntity("/api/v1/category", JsonNode.class);
        assertEquals(200, categories.getStatusCode().value());
        assertNotNull(categories.getBody());
        ResponseEntity<JsonNode> products = testRestTemplate.getForEntity("/api/v1/products", JsonNode.class);
        assertEquals(200, products.getStatusCode().value());
        assertTrue(products.getBody().has("products"));
        ResponseEntity<JsonNode> detail = testRestTemplate.getForEntity("/api/v2/product/" + product.getSku(), JsonNode.class);
        assertEquals(200, detail.getStatusCode().value());
        assertEquals(product.getSku(), detail.getBody().path("sku").asText());
        ResponseEntity<JsonNode> created = post("/api/v1/cart",
                Map.of("product", product.getSku(), "quantity", 1), jsonHeaders());
        assertEquals(201, created.getStatusCode().value(), String.valueOf(created.getBody()));
        String code = created.getBody().path("code").asText();
        assertFalse(code.isBlank());
        assertEquals(1, created.getBody().path("quantity").asInt());
        ResponseEntity<JsonNode> updated = testRestTemplate.exchange("/api/v1/cart/" + code,
                HttpMethod.PUT, new HttpEntity<>(Map.of("product", product.getSku(), "quantity", 2), jsonHeaders()), JsonNode.class);
        assertEquals(201, updated.getStatusCode().value(), String.valueOf(updated.getBody()));
        assertEquals(2, updated.getBody().path("quantity").asInt());
        ResponseEntity<JsonNode> read = testRestTemplate.getForEntity("/api/v1/cart/" + code, JsonNode.class);
        assertEquals(200, read.getStatusCode().value());
        assertEquals(code, read.getBody().path("code").asText());
        assertEquals(2, read.getBody().path("quantity").asInt());
    }

    @Test
    void legacyServicesBasicAuthenticationAcceptsOnlyValidCredentials() {
        HttpHeaders headers = jsonHeaders();
        headers.setBasicAuth("admin@shopizer.com", "password");
        ResponseEntity<JsonNode> valid = post("/services/private/system/optin", Map.of("code", "pr350"), headers);
        assertEquals(200, valid.getStatusCode().value(), String.valueOf(valid.getBody()));
        assertEquals(200, valid.getBody().path("status").asInt());
        headers.setBasicAuth("admin@shopizer.com", "wrong-password");
        assertEquals(401, post("/services/private/system/optin", Map.of("code", "pr350"), headers).getStatusCode().value());
    }

    @Test
    void healthAndOpenapiAreAvailable() {
        ResponseEntity<JsonNode> health = testRestTemplate.getForEntity("/actuator/health", JsonNode.class);
        assertEquals(200, health.getStatusCode().value(), String.valueOf(health.getBody()));
        assertEquals("UP", health.getBody().path("status").asText());
        ResponseEntity<JsonNode> docs = testRestTemplate.getForEntity("/v3/api-docs", JsonNode.class);
        assertEquals(200, docs.getStatusCode().value(), String.valueOf(docs.getBody()));
        assertTrue(docs.getBody().path("openapi").asText().startsWith("3."));
        assertTrue(docs.getBody().path("paths").has("/api/v1/products"));
    }
}
