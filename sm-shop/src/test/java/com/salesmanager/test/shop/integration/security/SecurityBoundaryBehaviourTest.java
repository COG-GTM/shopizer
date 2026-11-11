package com.salesmanager.test.shop.integration.security;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.salesmanager.shop.application.ShopApplication;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.http.*;
import java.util.Collections;
import java.util.UUID;
import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest(classes = ShopApplication.class, webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class SecurityBoundaryBehaviourTest {
    @Autowired private TestRestTemplate http;
    private final ObjectMapper json = new ObjectMapper();

    @Test
    void ignoredPathsHaveNoSecurityHeadersOrChallenge() throws Exception {
        for (String path : new String[]{"/", "/error", "/v3/api-docs", "/swagger-ui.html"}) {
            ResponseEntity<String> response = request(HttpMethod.GET, path, null, null);
            assertNotEquals(401, response.getStatusCodeValue(), path);
            assertNotEquals(403, response.getStatusCodeValue(), path);
            assertNull(response.getHeaders().getFirst(HttpHeaders.WWW_AUTHENTICATE), path);
            for (String header : new String[]{"X-Content-Type-Options", "X-Frame-Options", "X-XSS-Protection"})
                assertNull(response.getHeaders().getFirst(header), path + " " + header);
            if (path.equals("/v3/api-docs")) {
                assertEquals(200, response.getStatusCodeValue());
                assertTrue(response.getHeaders().getContentType().isCompatibleWith(MediaType.APPLICATION_JSON));
                assertTrue(json.readTree(response.getBody()).has("openapi"));
            }
        }
    }

    @Test
    void privateLoginAndInvalidBearerKeepTheirContracts() throws Exception {
        ResponseEntity<String> good = request(HttpMethod.POST, "/api/v1/private/login", credentials("admin@shopizer.com", "password"), null);
        assertEquals(200, good.getStatusCodeValue());
        assertEquals(3, json.readTree(good.getBody()).path("token").asText().split("\\.").length);
        ResponseEntity<String> bad = request(HttpMethod.POST, "/api/v1/private/login", credentials("admin@shopizer.com", "incorrect"), null);
        assertEquals(401, bad.getStatusCodeValue());
        assertEquals("Bad credentials", json.readTree(bad.getBody()).path("message").asText());
        assertEquals(500, request(HttpMethod.GET, "/api/v1/private/customers", null, "Bearer garbage").getStatusCodeValue());
    }

    @Test
    void permitAllPathsAndPreflightsAreNotChallenged() {
        for (String path : new String[]{"/api/v1/private/refresh", "/api/v1/auth/register", "/api/v1/auth/refresh", "/api/v1/auth/login"}) {
            ResponseEntity<String> response = request(HttpMethod.GET, path, null, null);
            assertNotEquals(401, response.getStatusCodeValue(), path);
            assertNotEquals(403, response.getStatusCodeValue(), path);
            assertNull(response.getHeaders().getFirst(HttpHeaders.WWW_AUTHENTICATE), path);
        }
        for (String path : new String[]{"/api/v1/private/customers", "/api/v1/auth/customer/profile"}) {
            HttpHeaders headers = headers();
            headers.setOrigin("http://localhost:4200");
            headers.setAccessControlRequestMethod(HttpMethod.GET);
            ResponseEntity<String> response = http.exchange(path, HttpMethod.OPTIONS, new HttpEntity<>(headers), String.class);
            assertNotEquals(401, response.getStatusCodeValue(), path);
            assertNull(response.getHeaders().getFirst(HttpHeaders.WWW_AUTHENTICATE), path);
        }
    }

    @Test
    void customerIdentityWorksOnlyInItsOwnStore() throws Exception {
        String email = "boundary" + UUID.randomUUID().toString().replace("-", "") + "@example.test";
        ObjectNode customer = json.createObjectNode().put("emailAddress", email).put("gender", "M")
                .put("language", "en").put("storeCode", "DEFAULT").put("password", "clear123").put("repeatPassword", "clear123");
        customer.putObject("billing").put("firstName", "Boundary").put("lastName", "Customer")
                .put("country", "BE").put("city", "Brussels").put("address", "1 Test Street")
                .put("postalCode", "1000").put("phone", "123456789");
        assertEquals(200, request(HttpMethod.POST, "/api/v1/customer/register", customer.toString(), null).getStatusCodeValue());
        ResponseEntity<String> login = request(HttpMethod.POST, "/api/v1/customer/login", credentials(email, "clear123"), null);
        assertEquals(200, login.getStatusCodeValue());
        String token = json.readTree(login.getBody()).path("token").asText();
        assertFalse(token.isEmpty());
        ResponseEntity<String> profile = request(HttpMethod.GET, "/api/v1/auth/customer/profile", null, "Bearer " + token);
        assertEquals(200, profile.getStatusCodeValue());
        assertEquals(email, json.readTree(profile.getBody()).path("emailAddress").asText());
        int status = request(HttpMethod.GET, "/api/v1/private/customers", null, "Bearer " + token).getStatusCodeValue();
        assertTrue(status == 401 || status == 403);
        HttpHeaders headers = headers();
        headers.setBasicAuth(email, "clear123");
        assertEquals(401, http.exchange("/services/private/system/optin", HttpMethod.POST, new HttpEntity<>("{}", headers), String.class).getStatusCodeValue());
    }

    @Test
    void servicesJsonRequestsRequireAdminBasicCredentials() {
        String path = "/services/private/system/optin";
        assertEquals(401, request(HttpMethod.POST, path, "{}", null).getStatusCodeValue());
        HttpHeaders headers = headers();
        headers.setBasicAuth("admin@shopizer.com", "password");
        assertEquals(200, http.exchange(path, HttpMethod.POST, new HttpEntity<>("{}", headers), String.class).getStatusCodeValue());
    }

    @Test
    void shopLogoutRedirectsAndClearsSessionCookie() {
        HttpHeaders headers = headers();
        headers.setAccept(Collections.singletonList(MediaType.TEXT_HTML));
        headers.add(HttpHeaders.COOKIE, "JSESSIONID=boundary-session");
        ResponseEntity<String> response = http.exchange("/shop/customer/logout", HttpMethod.POST, new HttpEntity<>(headers), String.class);
        assertEquals(302, response.getStatusCodeValue());
        assertEquals("/shop/", response.getHeaders().getLocation().getPath());
        assertTrue(response.getHeaders().getOrEmpty(HttpHeaders.SET_COOKIE).stream()
                .anyMatch(cookie -> cookie.contains("JSESSIONID=") && cookie.contains("Max-Age=0")), response.getHeaders().toString());
    }

    private String credentials(String username, String password) {
        return json.createObjectNode().put("username", username).put("password", password).toString();
    }

    private HttpHeaders headers() {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.setAccept(Collections.singletonList(MediaType.APPLICATION_JSON));
        return headers;
    }

    private ResponseEntity<String> request(HttpMethod method, String path, String body, String authorization) {
        HttpHeaders headers = headers();
        if (authorization != null) headers.set(HttpHeaders.AUTHORIZATION, authorization);
        return http.exchange(path, method, new HttpEntity<>(body, headers), String.class);
    }
}
