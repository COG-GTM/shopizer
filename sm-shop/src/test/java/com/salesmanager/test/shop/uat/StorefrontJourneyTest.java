package com.salesmanager.test.shop.uat;

import static org.junit.jupiter.api.Assertions.*;

import java.util.Map;
import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.SpringBootTest.WebEnvironment;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.ResponseEntity;

import com.fasterxml.jackson.databind.JsonNode;
import com.salesmanager.shop.application.ShopApplication;
import com.salesmanager.test.shop.common.ServicesTestSupport;

@SpringBootTest(classes = ShopApplication.class, webEnvironment = WebEnvironment.RANDOM_PORT)
class StorefrontJourneyTest extends ServicesTestSupport {
    @Test
    void adminLoginAndProtectedResourceRejectMissingToken() {
        HttpHeaders admin = login("/api/v1/private/login", "admin@shopizer.com", "password");
        assertEquals(200, request(HttpMethod.GET, "/api/v1/private/user/profile", null, admin).getStatusCode().value());
        assertDenied(request(HttpMethod.GET, "/api/v1/private/user/profile", null, new HttpHeaders()));
    }

    @Test
    void customerRegistersLogsInAndReadsOwnProfile() {
        HttpHeaders customer = customer();
        ResponseEntity<JsonNode> profile = request(HttpMethod.GET, "/api/v1/auth/customer/profile", null, customer);
        assertEquals(200, profile.getStatusCode().value());
        assertTrue(profile.getBody().path("emailAddress").asText().endsWith("@example.com"));
    }

    @Test
    void browseStoreCategoryProductsAndDetail() {
        var product = sampleProduct("uat-browse");
        for (String path : new String[]{"/api/v1/store/DEFAULT", "/api/v1/category?store=DEFAULT&lang=en",
                "/api/v1/products?store=DEFAULT&lang=en", "/api/v2/product/" + product.getSku() + "?store=DEFAULT&lang=en"}) {
            ResponseEntity<JsonNode> response = request(HttpMethod.GET, path, null, new HttpHeaders());
            assertEquals(200, response.getStatusCode().value(), path);
            assertNotNull(response.getBody());
        }
    }

    @Test
    void cartCreatesAddsUpdatesAndReadsItems() {
        var product = sampleProduct("uat-cart");
        ResponseEntity<JsonNode> created = request(HttpMethod.POST, "/api/v1/cart", Map.of("product", product.getSku(), "quantity", 1), new HttpHeaders());
        assertEquals(201, created.getStatusCode().value());
        String path = "/api/v1/cart/" + created.getBody().path("code").asText();
        assertEquals(201, request(HttpMethod.PUT, path, Map.of("product", product.getSku(), "quantity", 1), new HttpHeaders()).getStatusCode().value());
        ResponseEntity<JsonNode> updated = request(HttpMethod.POST, path + "/multi", new Object[]{Map.of("product", product.getSku(), "quantity", 3)}, new HttpHeaders());
        assertEquals(201, updated.getStatusCode().value());
        ResponseEntity<JsonNode> fetched = request(HttpMethod.GET, path, null, new HttpHeaders());
        assertEquals(200, fetched.getStatusCode().value());
        assertEquals(3, fetched.getBody().path("quantity").asInt());
    }

    @Test
    void authenticatedCustomerChecksOutAndReadsOrder() {
        HttpHeaders customer = customer();
        assertEquals(200, request(HttpMethod.POST, "/api/v1/private/modules/payment", Map.of("code", "moneyorder", "active", true, "defaultSelected", true,
                "integrationKeys", Map.of("address", "123 UAT Street"), "integrationOptions", Map.of()), getHeader()).getStatusCode().value());
        var product = sampleProduct("uat-checkout");
        ResponseEntity<JsonNode> cart = request(HttpMethod.POST, "/api/v1/cart", Map.of("product", product.getSku(), "quantity", 1), customer);
        assertEquals(201, cart.getStatusCode().value());
        String code = cart.getBody().path("code").asText();
        ResponseEntity<JsonNode> checkout = request(HttpMethod.POST, "/api/v1/auth/cart/" + code + "/checkout",
                Map.of("customerAgreement", true, "currency", "CAD", "payment", Map.of("paymentModule", "moneyorder", "paymentType", "MONEYORDER", "transactionType", "AUTHORIZECAPTURE", "amount", "125.00")), customer);
        assertEquals(200, checkout.getStatusCode().value(), String.valueOf(checkout.getBody()));
        long id = checkout.getBody().path("id").asLong();
        assertTrue(id > 0, String.valueOf(checkout.getBody()));
        assertEquals(200, request(HttpMethod.GET, "/api/v1/auth/orders/" + id, null, customer).getStatusCode().value());
    }

    @Test
    void servicesBasicAuthAcceptsAdminAndRejectsInvalidCredentials() {
        String path = "/services/private/system/optin";
        HttpHeaders valid = new HttpHeaders();
        valid.setBasicAuth("admin@shopizer.com", "password");
        assertEquals(200, request(HttpMethod.POST, path, "{}", valid).getStatusCode().value());
        HttpHeaders invalid = new HttpHeaders();
        invalid.setBasicAuth("admin@shopizer.com", "wrong-password");
        assertDenied(request(HttpMethod.POST, path, "{}", invalid));
    }

    @Test
    void healthAndApiDocumentationAreAvailable() {
        ResponseEntity<JsonNode> health = request(HttpMethod.GET, "/actuator/health", null, new HttpHeaders());
        assertEquals(200, health.getStatusCode().value());
        assertEquals("UP", health.getBody().path("status").asText());
        assertEquals(200, request(HttpMethod.GET, "/v3/api-docs", null, new HttpHeaders()).getStatusCode().value());
    }

    private HttpHeaders customer() {
        String email = "uat-" + UUID.randomUUID().toString().substring(0, 16) + "@example.com";
        var payload = Map.of("emailAddress", email, "password", "clear123", "gender", "M", "language", "en", "storeCode", "DEFAULT",
                "billing", Map.of("firstName", "Uat", "lastName", "Buyer", "country", "BE"));
        assertEquals(200, request(HttpMethod.POST, "/api/v1/customer/register", payload, new HttpHeaders()).getStatusCode().value());
        return login("/api/v1/customer/login", email, "clear123");
    }

    private HttpHeaders login(String path, String username, String password) {
        ResponseEntity<JsonNode> response = request(HttpMethod.POST, path, Map.of("username", username, "password", password), new HttpHeaders());
        assertEquals(200, response.getStatusCode().value());
        String token = response.getBody().path("token").asText();
        assertFalse(token.isBlank());
        HttpHeaders headers = new HttpHeaders();
        headers.setBearerAuth(token);
        return headers;
    }

    private ResponseEntity<JsonNode> request(HttpMethod method, String path, Object body, HttpHeaders headers) {
        headers.setContentType(org.springframework.http.MediaType.APPLICATION_JSON);
        return testRestTemplate.exchange(path, method, new HttpEntity<>(body, headers), JsonNode.class);
    }

    private static void assertDenied(ResponseEntity<?> response) {
        assertTrue(response.getStatusCode().value() == 401 || response.getStatusCode().value() == 403, response.getStatusCode().toString());
    }
}
