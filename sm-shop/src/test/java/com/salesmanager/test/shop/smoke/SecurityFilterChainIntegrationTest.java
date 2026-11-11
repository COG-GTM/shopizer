package com.salesmanager.test.shop.smoke;

import com.salesmanager.shop.application.ShopApplication;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.http.*;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest(classes = ShopApplication.class, webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class SecurityFilterChainIntegrationTest {
    @Autowired private TestRestTemplate http;

    @Test
    void servicesUsesItsBasicEntryPointAndRejectsBadCredentials() {
        ResponseEntity<String> response = http.getForEntity("/services/private/probe", String.class);
        assertEquals(302, response.getStatusCodeValue());
        assertTrue(response.getHeaders().getLocation().getPath().endsWith("/login"));
        challenge("/services/private/probe", "Basic bm9ib2R5Ondyb25n", "rest-customer-realm");
    }

    @Test
    void jwtChainsHaveDistinctEntryPointsAndRejectBadCredentials() {
        challenge("/api/v1/private/customers", null, "api-admin-realm");
        challenge("/api/v1/auth/customer/profile", null, "api-customer-realm");
        challenge("/api/v1/private/customers", "Basic bm9ib2R5Ondyb25n", "api-admin-realm");
        challenge("/api/v1/auth/customer/profile", "Basic bm9ib2R5Ondyb25n", "api-customer-realm");
        challenge("/shop/customer/probe", "Basic bm9ib2R5Ondyb25n", "shop-realm");
    }

    @Test
    void adminJwtCannotAuthenticateAsCustomer() throws Exception {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        ResponseEntity<String> login = http.postForEntity("/api/v1/private/login",
                new HttpEntity<>("{\"username\":\"admin@shopizer.com\",\"password\":\"password\"}", headers), String.class);
        assertEquals(200, login.getStatusCodeValue());
        String token = new com.fasterxml.jackson.databind.ObjectMapper().readTree(login.getBody()).path("token").asText();
        challenge("/api/v1/auth/customer/profile", "Bearer " + token, "api-customer-realm");
    }

    @Test
    void servicesAcceptsAdminBasicCredentials() {
        ResponseEntity<String> response = http.withBasicAuth("admin@shopizer.com", "password")
                .getForEntity("/services/private/probe", String.class);
        assertEquals(404, response.getStatusCodeValue());
        assertNull(response.getHeaders().getFirst(HttpHeaders.WWW_AUTHENTICATE));
    }

    @Test
    void publicCatalogAndUnsecuredLegacyPathsStayPublic() {
        assertEquals(200, http.getForEntity("/api/v1/products?store=DEFAULT", String.class).getStatusCodeValue());
        for (String path : new String[]{"/shop/customer/probe", "/admin/probe", "/services/public/probe"}) {
            ResponseEntity<String> response = http.getForEntity(path, String.class);
            assertNull(response.getHeaders().getFirst(HttpHeaders.WWW_AUTHENTICATE), path);
            assertNotEquals(401, response.getStatusCodeValue(), path);
            assertNotEquals(403, response.getStatusCodeValue(), path);
        }
    }

    private void challenge(String path, String authorization, String realm) {
        HttpHeaders headers = new HttpHeaders();
        if (authorization != null) headers.set(HttpHeaders.AUTHORIZATION, authorization);
        ResponseEntity<String> response = http.exchange(path, HttpMethod.GET, new HttpEntity<>(headers), String.class);
        assertEquals(401, response.getStatusCodeValue(), path);
        assertEquals("Basic realm=\"" + realm + "\"", response.getHeaders().getFirst(HttpHeaders.WWW_AUTHENTICATE), path);
    }
}
