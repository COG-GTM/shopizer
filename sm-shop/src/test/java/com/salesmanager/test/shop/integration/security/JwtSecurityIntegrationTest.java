package com.salesmanager.test.shop.integration.security;

import static org.junit.jupiter.api.Assertions.*;
import java.nio.charset.StandardCharsets;
import org.junit.jupiter.api.Test;
import org.springframework.http.*;
import com.salesmanager.test.shop.common.SecurityJourneySupport;
import com.salesmanager.test.shop.common.TokenContractSupport;
import com.salesmanager.shop.store.security.AuthenticationResponse;
import com.fasterxml.jackson.databind.JsonNode;

class JwtSecurityIntegrationTest extends SecurityJourneySupport {
    @Test
    void adminLoginIssuesHs512ApiTokenAndAuthorizesProfile() throws Exception {
        String token = adminToken();
        assertEquals(3, token.split("\\.").length);
        assertEquals("HS512", TokenContractSupport.part(token, 0).get("alg").asText());
        JsonNode claims = TokenContractSupport.part(token, 1);
        JsonNode audience = claims.get("aud");
        assertEquals("api", audience.isArray() ? audience.get(0).asText() : audience.asText());
        assertEquals("admin@shopizer.com", claims.get("sub").asText());
        assertEquals(HttpStatus.OK, get(ADMIN_PROFILE, token).getStatusCode());
    }

    @Test
    void legacyTokenAuthorizesProfile() throws Exception {
        try (java.io.InputStream in = getClass().getResourceAsStream("/legacy-jjwt-0.8.token")) {
            assertNotNull(in);
            assertEquals(HttpStatus.OK, get(ADMIN_PROFILE, new String(in.readAllBytes(), StandardCharsets.UTF_8).trim()).getStatusCode());
        }
    }

    @Test
    void expiredTokenIsDenied() throws Exception {
        assertDenied(get(ADMIN_PROFILE, TokenContractSupport.expiredAdminToken()));
    }

    @Test
    void missingTokenIsDenied() {
        assertDenied(get(ADMIN_PROFILE, null));
    }

    @Test
    void refreshIssuesUsableAdminToken() {
        ResponseEntity<AuthenticationResponse> response = testRestTemplate.exchange("/api/v1/auth/refresh", HttpMethod.GET,
                new HttpEntity<>(bearer(adminToken())), AuthenticationResponse.class);
        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertNotNull(response.getBody());
        assertEquals(HttpStatus.OK, get(ADMIN_PROFILE, response.getBody().getToken()).getStatusCode());
    }

    @Test
    void customerProfileWorksAndAdminProfileIsDenied() {
        String token = registerAndLoginCustomer();
        assertEquals(HttpStatus.OK, get("/api/v1/auth/customer/profile", token).getStatusCode());
        assertDenied(get(ADMIN_PROFILE, token));
    }
}
