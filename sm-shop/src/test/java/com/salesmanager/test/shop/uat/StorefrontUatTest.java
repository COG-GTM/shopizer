package com.salesmanager.test.shop.uat;

import static org.junit.jupiter.api.Assertions.*;

import java.util.Map;
import java.util.UUID;
import com.fasterxml.jackson.databind.JsonNode;
import com.salesmanager.shop.model.catalog.product.ReadableProduct;
import com.salesmanager.test.shop.common.SecurityHttpTestSupport;
import org.junit.jupiter.api.Test;
import org.springframework.http.*;

class StorefrontUatTest extends SecurityHttpTestSupport {
    @Test
    void adminSignsInAndCallsProtectedProfile() {
        assertEquals(200, request(HttpMethod.GET, "/api/v1/private/user/profile", null, bearer(adminToken())).getStatusCode().value());
        assertUnauthorized(request(HttpMethod.GET, "/api/v1/private/user/profile", null, new HttpHeaders()), "api-admin-realm");
        assertUnauthorized(request(HttpMethod.GET, "/api/v1/private/user/profile", null, bearer("bad-token")), "api-admin-realm");
    }

    @Test
    void customerRegistersSignsInAndReadsProfile() {
        assertEquals(200, request(HttpMethod.GET, "/api/v1/auth/customer/profile", null, bearer(customerToken())).getStatusCode().value());
    }

    @Test
    void browseCatalogCreateAndUpdateCart() {
        ReadableProduct product = sampleProduct("uat-" + UUID.randomUUID().toString().substring(0, 8));
        for (String path : new String[] { "/api/v1/store/DEFAULT", "/api/v1/category", "/api/v1/products",
                "/api/v2/product/" + product.getSku() }) {
            ResponseEntity<JsonNode> response = request(HttpMethod.GET, path, null, new HttpHeaders());
            assertEquals(200, response.getStatusCode().value(), path + ": " + response);
            assertNotNull(response.getBody());
        }
        ResponseEntity<JsonNode> created = request(HttpMethod.POST, "/api/v1/cart",
                Map.of("product", product.getSku(), "quantity", 1), new HttpHeaders());
        assertEquals(201, created.getStatusCode().value(), created.toString());
        String code = created.getBody().path("code").asText();
        assertFalse(code.isBlank(), created.toString());
        ResponseEntity<JsonNode> updated = request(HttpMethod.PUT, "/api/v1/cart/" + code,
                Map.of("product", product.getSku(), "quantity", 2), new HttpHeaders());
        assertEquals(201, updated.getStatusCode().value(), updated.toString());
        ResponseEntity<JsonNode> read = request(HttpMethod.GET, "/api/v1/cart/" + code, null, new HttpHeaders());
        assertEquals(200, read.getStatusCode().value(), read.toString());
        assertEquals(2, read.getBody().path("quantity").asInt());
    }

    @Test
    void legacyBasicAuthenticationAcceptsValidAndRejectsInvalidCredentials() {
        HttpHeaders headers = new HttpHeaders();
        headers.setBasicAuth("admin@shopizer.com", "password");
        assertEquals(200, request(HttpMethod.POST, "/services/private/system/optin", "{}", headers).getStatusCode().value());
        headers.setBasicAuth("admin@shopizer.com", "wrong");
        assertEquals(401, request(HttpMethod.POST, "/services/private/system/optin", "{}", headers).getStatusCode().value());
    }

    @Test
    void healthAndOpenApiArePublic() {
        ResponseEntity<JsonNode> health = request(HttpMethod.GET, "/actuator/health", null, new HttpHeaders());
        assertEquals(200, health.getStatusCode().value());
        assertEquals("UP", health.getBody().path("status").asText());
        ResponseEntity<JsonNode> docs = request(HttpMethod.GET, "/v3/api-docs", null, new HttpHeaders());
        assertEquals(200, docs.getStatusCode().value(), docs.toString());
        assertTrue(docs.getBody().path("openapi").asText().startsWith("3."));
    }
}
