package com.salesmanager.test.shop.integration.contract;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.salesmanager.shop.application.ShopApplication;
import java.lang.reflect.Method;
import java.util.HashSet;
import java.util.Set;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.http.ResponseEntity;
import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest(classes = ShopApplication.class, webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class ApiDocsContractIntegrationTest {
    @Autowired private TestRestTemplate http;
    private final ObjectMapper json = new ObjectMapper();

    @Test
    void liveSwaggerExactlyMatchesCommittedInventory() throws Exception {
        ResponseEntity<String> response = http.getForEntity("/v2/api-docs", String.class);
        assertEquals(200, response.getStatusCodeValue(), response.getBody());
        JsonNode spec = json.readTree(response.getBody());
        assertEquals("2.0", spec.path("swagger").asText());
        Method normalize = Class.forName("com.salesmanager.test.shop.contract.OperationInventory")
                .getDeclaredMethod("normalize", JsonNode.class);
        normalize.setAccessible(true);
        JsonNode actual = (JsonNode) normalize.invoke(null, spec);
        JsonNode expected = json.readTree(getClass().getResourceAsStream("/contract/operations.json"));
        assertEquals(expected, actual, "Live inventory must have no additions, removals or metadata changes");
        Set<String> keys = new HashSet<>();
        actual.forEach(op -> keys.add(op.path("method").asText() + " " + op.path("path").asText()));
        for (String key : new String[] {"POST /api/v1/private/login", "POST /api/v1/customer/login",
                "POST /api/v1/customer/register", "POST /api/v1/cart", "GET /api/v1/cart/{code}",
                "POST /api/v1/cart/{code}/checkout"}) {
            assertTrue(keys.contains(key), key);
        }
    }
}
