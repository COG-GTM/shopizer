package com.salesmanager.test.shop.uat;

import static org.junit.Assert.*;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.SpringBootTest.WebEnvironment;
import org.springframework.http.*;
import org.springframework.test.context.junit4.SpringRunner;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.salesmanager.shop.application.ShopApplication;
import com.salesmanager.test.shop.common.ServicesTestSupport;

@RunWith(SpringRunner.class)
@SpringBootTest(classes = ShopApplication.class, webEnvironment = WebEnvironment.RANDOM_PORT)
public class AdminJourneyUatTest extends ServicesTestSupport {
    @Test
    public void adminLoginProtectsPrivateProfile() throws Exception {
        ResponseEntity<String> login = testRestTemplate.postForEntity("/api/v1/private/login",
                new HttpEntity<>("{\"username\":\"admin@shopizer.com\",\"password\":\"password\"}", jsonHeaders()), String.class);
        assertEquals(HttpStatus.OK, login.getStatusCode());
        String token = new ObjectMapper().readTree(login.getBody()).path("token").asText();
        assertFalse(token.isEmpty());
        HttpHeaders headers = jsonHeaders();
        headers.setBearerAuth(token);
        ResponseEntity<String> profile = testRestTemplate.exchange("/api/v1/private/user/profile", HttpMethod.GET,
                new HttpEntity<>(headers), String.class);
        assertEquals(HttpStatus.OK, profile.getStatusCode());
        assertTrue(new ObjectMapper().readTree(profile.getBody()).path("id").asLong() > 0);
        assertEquals(HttpStatus.UNAUTHORIZED, testRestTemplate.getForEntity("/api/v1/private/user/profile", String.class).getStatusCode());
    }

    @Test
    public void servicesBasicAuthenticationDistinguishesValidAndInvalidCredentials() {
        assertEquals(HttpStatus.NOT_FOUND, testRestTemplate.withBasicAuth("admin@shopizer.com", "password")
                .getForEntity("/services/private/uat-probe", String.class).getStatusCode());
        assertEquals(HttpStatus.UNAUTHORIZED, testRestTemplate.withBasicAuth("admin@shopizer.com", "incorrect")
                .getForEntity("/services/private/uat-probe", String.class).getStatusCode());
    }

    @Test
    public void actuatorHealthIsUp() throws Exception {
        ResponseEntity<String> response = testRestTemplate.getForEntity("/actuator/health", String.class);
        assertEquals(HttpStatus.OK, response.getStatusCode());
        JsonNode health = new ObjectMapper().readTree(response.getBody());
        assertEquals("UP", health.path("status").asText());
    }

    private HttpHeaders jsonHeaders() {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        return headers;
    }
}
