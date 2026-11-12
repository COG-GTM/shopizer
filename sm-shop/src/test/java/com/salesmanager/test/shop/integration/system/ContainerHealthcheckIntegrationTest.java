package com.salesmanager.test.shop.integration.system;

import com.fasterxml.jackson.databind.JsonNode;
import com.salesmanager.shop.application.ShopApplication;
import jakarta.inject.Inject;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.http.ResponseEntity;
import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest(classes = ShopApplication.class, webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class ContainerHealthcheckIntegrationTest {
    @Inject private TestRestTemplate http;

    @Test
    void unauthenticatedHealthcheckReportsHealthyDatabase() {
        ResponseEntity<JsonNode> response = http.getForEntity("/actuator/health", JsonNode.class);
        assertEquals(200, response.getStatusCode().value());
        assertNotNull(response.getBody());
        assertEquals("UP", response.getBody().path("status").asText());
        assertEquals("UP", response.getBody().path("components").path("db").path("status").asText());
    }
}
