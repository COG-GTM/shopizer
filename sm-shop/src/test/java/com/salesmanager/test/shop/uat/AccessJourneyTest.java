package com.salesmanager.test.shop.uat;

import static org.junit.Assert.*;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.springframework.http.*;
import org.springframework.test.context.junit4.SpringRunner;
import com.fasterxml.jackson.databind.JsonNode;
import com.salesmanager.test.shop.common.ServicesTestSupport;

@RunWith(SpringRunner.class)
public class AccessJourneyTest extends ServicesTestSupport {
    @Test
    public void adminLoginAndProtectedResource() {
        HttpHeaders admin = getHeader();
        assertTrue(admin.getFirst("Authorization").startsWith("Bearer "));
        assertTrue(testRestTemplate.exchange("/api/v1/private/customers", HttpMethod.GET,
                new HttpEntity<>(admin), String.class).getStatusCode().is2xxSuccessful());
        denied(testRestTemplate.getForEntity("/api/v1/private/customers", String.class));
    }

    @Test
    public void legacyServicesBasicAuthentication() {
        HttpHeaders valid = new HttpHeaders();
        valid.setBasicAuth("admin@shopizer.com", "password");
        ResponseEntity<String> response = testRestTemplate.postForEntity("/services/private/system/optin",
                new HttpEntity<>("{}", valid), String.class);
        assertEquals(response.toString(), 200, response.getStatusCodeValue());
        HttpHeaders invalid = new HttpHeaders();
        invalid.setBasicAuth("admin@shopizer.com", "wrong-password");
        denied(testRestTemplate.postForEntity("/services/private/system/optin", new HttpEntity<>("{}", invalid), String.class));
    }

    @Test
    public void healthAndApiDocumentation() {
        ResponseEntity<JsonNode> health = testRestTemplate.getForEntity("/actuator/health", JsonNode.class);
        assertEquals(health.toString(), 200, health.getStatusCodeValue());
        assertEquals("UP", health.getBody().path("status").asText());
        ResponseEntity<JsonNode> docs = testRestTemplate.getForEntity("/v2/api-docs", JsonNode.class);
        assertEquals(docs.toString(), 200, docs.getStatusCodeValue());
        assertTrue(docs.getBody().has("paths"));
    }

    private void denied(ResponseEntity<?> response) {
        assertTrue(response.toString(), response.getStatusCodeValue() == 401 || response.getStatusCodeValue() == 403);
    }
}
