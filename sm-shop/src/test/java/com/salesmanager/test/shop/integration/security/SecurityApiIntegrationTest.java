package com.salesmanager.test.shop.integration.security;

import static org.junit.jupiter.api.Assertions.*;

import java.nio.charset.StandardCharsets;
import java.util.Date;
import com.fasterxml.jackson.databind.JsonNode;
import com.salesmanager.test.shop.common.SecurityHttpTestSupport;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.SignatureAlgorithm;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.*;

class SecurityApiIntegrationTest extends SecurityHttpTestSupport {
    @Value("${jwt.secret}")
    private String secret;

    @Test
    void adminProfileRequiresValidJwtAndIsStateless() {
        String token = adminToken();
        ResponseEntity<JsonNode> profile = request(HttpMethod.GET, "/api/v1/private/user/profile", null, bearer(token));
        assertEquals(200, profile.getStatusCode().value(), profile.toString());
        assertStateless(profile);
        String[] parts = token.split("\\.");
        String tampered = parts[0] + "." + parts[1] + "." + (parts[2].startsWith("A") ? "B" : "A") + parts[2].substring(1);
        for (String invalid : new String[] { "", tampered, signed("z".repeat(64), false), signed(secret, true) }) {
            HttpHeaders headers = invalid.isEmpty() ? new HttpHeaders() : bearer(invalid);
            assertUnauthorized(request(HttpMethod.GET, "/api/v1/private/user/profile", null, headers), "api-admin-realm");
        }
    }

    @SuppressWarnings("deprecation")
    private String signed(String key, boolean expired) {
        long now = System.currentTimeMillis();
        return Jwts.builder().setSubject("admin@shopizer.com").setAudience("api")
                .setIssuedAt(new Date(now - 60_000)).setExpiration(new Date(now + (expired ? -10_000 : 60_000)))
                .signWith(SignatureAlgorithm.HS512, key.getBytes(StandardCharsets.UTF_8)).compact();
    }

    @Test
    void customerLoginProfileRefreshAndMissingToken() throws Exception {
        String token = customerToken();
        ResponseEntity<JsonNode> profile = request(HttpMethod.GET, "/api/v1/auth/customer/profile", null, bearer(token));
        assertEquals(200, profile.getStatusCode().value(), profile.toString());
        assertStateless(profile);
        assertUnauthorized(request(HttpMethod.GET, "/api/v1/auth/customer/profile", null, new HttpHeaders()), "api-customer-realm");
        Thread.sleep(1100);
        ResponseEntity<JsonNode> refresh = request(HttpMethod.GET, "/api/v1/auth/customer/refresh", null, bearer(token));
        assertEquals(200, refresh.getStatusCode().value(), refresh.toString());
        String refreshed = refresh.getBody().path("token").asText();
        assertFalse(refreshed.isBlank());
        assertNotEquals(token, refreshed);
        assertEquals(200, request(HttpMethod.GET, "/api/v1/auth/customer/profile", null, bearer(refreshed)).getStatusCode().value());
        assertStateless(refresh);
    }

    @Test
    void adminRefreshUsesActualControllerPathAndRejectsGarbage() {
        ResponseEntity<JsonNode> refresh = request(HttpMethod.GET, "/api/v1/auth/refresh", null, bearer(adminToken()));
        assertEquals(200, refresh.getStatusCode().value(), refresh.toString());
        assertFalse(refresh.getBody().path("token").asText().isBlank());
        ResponseEntity<JsonNode> invalid = request(HttpMethod.GET, "/api/v1/auth/refresh", null, bearer("garbage"));
        assertEquals(401, invalid.getStatusCode().value(), invalid.toString());
    }

    @Test
    void defaultCorsAllowsCredentialedPreflightsAndPublicGet() {
        String origin = "http://localhost:3000";
        for (String path : new String[] { "/api/v1/private/user/profile", "/api/v1/auth/customer/profile" }) {
            ResponseEntity<String> response = preflight(path, origin);
            assertEquals(200, response.getStatusCode().value(), response.toString());
            assertEquals(origin, response.getHeaders().getAccessControlAllowOrigin());
            assertTrue(response.getHeaders().getAccessControlAllowCredentials());
            assertEquals(3600L, response.getHeaders().getAccessControlMaxAge());
            assertTrue(response.getHeaders().getAccessControlAllowHeaders().stream().anyMatch("Authorization"::equalsIgnoreCase));
        }
        HttpHeaders headers = new HttpHeaders();
        headers.setOrigin(origin);
        ResponseEntity<JsonNode> response = request(HttpMethod.GET, "/api/v1/store/DEFAULT", null, headers);
        assertEquals(200, response.getStatusCode().value());
        assertEquals(origin, response.getHeaders().getAccessControlAllowOrigin());
    }

    @Test
    void actuatorExposesHealthWithoutDetailsButNotEnvOrBeans() {
        ResponseEntity<JsonNode> health = request(HttpMethod.GET, "/actuator/health", null, new HttpHeaders());
        assertEquals(200, health.getStatusCode().value());
        assertEquals("UP", health.getBody().path("status").asText());
        assertFalse(health.getBody().toString().contains("\"details\""));
        for (String endpoint : new String[] { "env", "beans" }) {
            assertEquals(404, request(HttpMethod.GET, "/actuator/" + endpoint, null, new HttpHeaders()).getStatusCode().value());
        }
    }

    @Test
    void legacyPrivateEndpointRejectsBadBasicCredentials() {
        HttpHeaders headers = new HttpHeaders();
        headers.setBasicAuth("admin@shopizer.com", "wrong");
        assertEquals(401, request(HttpMethod.POST, "/services/private/system/optin", "{}", headers).getStatusCode().value());
    }
}
