package com.salesmanager.test.shop.common;

import static org.junit.jupiter.api.Assertions.*;

import java.util.Map;
import java.util.UUID;
import com.fasterxml.jackson.databind.JsonNode;
import org.springframework.http.*;

public class SecurityHttpTestSupport extends ServicesTestSupport {
    protected ResponseEntity<JsonNode> request(HttpMethod method, String path, Object body, HttpHeaders headers) {
        return testRestTemplate.exchange(path, method, new HttpEntity<>(body, headers), JsonNode.class);
    }

    protected HttpHeaders bearer(String token) {
        HttpHeaders headers = new HttpHeaders();
        headers.setBearerAuth(token);
        return headers;
    }

    protected String adminToken() {
        return login("/api/v1/private/login", "admin@shopizer.com", "password");
    }

    protected String login(String path, String username, String password) {
        ResponseEntity<JsonNode> response = request(HttpMethod.POST, path,
                Map.of("username", username, "password", password), new HttpHeaders());
        assertEquals(200, response.getStatusCode().value(), response.toString());
        assertFalse(response.getBody().path("token").asText().isBlank());
        return response.getBody().path("token").asText();
    }

    protected String customerToken() {
        String email = "security-" + UUID.randomUUID() + "@example.com";
        ResponseEntity<JsonNode> response = request(HttpMethod.POST, "/api/v1/customer/register",
                Map.of("emailAddress", email, "password", "clear123", "gender", "M", "language", "en",
                        "storeCode", "DEFAULT", "billing", Map.of("firstName", "Security", "lastName", "Test",
                                "country", "BE", "address", "1 Test Street", "city", "Brussels", "postalCode", "1000")),
                new HttpHeaders());
        assertEquals(200, response.getStatusCode().value(), response.toString());
        return login("/api/v1/customer/login", email, "clear123");
    }

    protected void assertUnauthorized(ResponseEntity<JsonNode> response, String realm) {
        assertEquals(401, response.getStatusCode().value(), response.toString());
        assertTrue(response.getHeaders().getFirst(HttpHeaders.WWW_AUTHENTICATE)
                .startsWith("Bearer realm=\"" + realm + "\""), response.toString());
        assertTrue(MediaType.APPLICATION_JSON.isCompatibleWith(response.getHeaders().getContentType()));
        assertEquals("401", response.getBody().path("errorCode").asText());
    }

    protected void assertStateless(ResponseEntity<?> response) {
        assertTrue(response.getHeaders().getOrEmpty(HttpHeaders.SET_COOKIE).stream()
                .noneMatch(cookie -> cookie.contains("JSESSIONID")), response.toString());
    }

    protected ResponseEntity<String> preflight(String path, String origin) {
        HttpHeaders headers = new HttpHeaders();
        headers.setOrigin(origin);
        headers.setAccessControlRequestMethod(HttpMethod.GET);
        headers.setAccessControlRequestHeaders(java.util.List.of("Authorization"));
        return testRestTemplate.exchange(path, HttpMethod.OPTIONS, new HttpEntity<>(headers), String.class);
    }
}
