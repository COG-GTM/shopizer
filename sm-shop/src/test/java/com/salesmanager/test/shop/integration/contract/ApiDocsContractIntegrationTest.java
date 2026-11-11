package com.salesmanager.test.shop.integration.contract;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.salesmanager.shop.application.ShopApplication;
import java.lang.reflect.Method;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
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
        Map<String, JsonNode> baseline = operationsByKey(expected);
        Map<String, JsonNode> live = operationsByKey(actual);
        assertEquals(baseline.keySet(), live.keySet(), "Live operation keys must match exactly");
        baseline.forEach((key, operation) -> assertEquals(operation,
                withoutCollectionAccessorParameters(operation, live.get(key)), key));
        Set<String> keys = new HashSet<>();
        actual.forEach(op -> keys.add(op.path("method").asText() + " " + op.path("path").asText()));
        for (String key : new String[] {"POST /api/v1/private/login", "POST /api/v1/customer/login",
                "POST /api/v1/customer/register", "POST /api/v1/cart", "GET /api/v1/cart/{code}",
                "POST /api/v1/cart/{code}/checkout"}) {
            assertTrue(keys.contains(key), key);
        }
    }

    private Map<String, JsonNode> operationsByKey(JsonNode inventory) {
        Map<String, JsonNode> operations = new TreeMap<>();
        inventory.forEach(op -> {
            String key = op.path("method").asText() + " " + op.path("path").asText();
            assertNull(operations.put(key, op), "Duplicate operation: " + key);
        });
        return operations;
    }

    private JsonNode withoutCollectionAccessorParameters(JsonNode baseline, JsonNode live) {
        Set<String> baselineQueries = new HashSet<>();
        baseline.path("parameters").forEach(parameter -> {
            if ("query".equals(parameter.path("in").asText())) {
                baselineQueries.add(parameter.path("name").asText());
            }
        });
        ObjectNode result = live.deepCopy();
        ArrayNode parameters = result.putArray("parameters");
        live.path("parameters").forEach(parameter -> {
            String name = parameter.path("name").asText();
            // Springfox exposes JDK 21 SequencedCollection getFirst/getLast as query properties.
            boolean accessor = name.endsWith(".first") || name.endsWith(".last")
                    || name.contains(".first.") || name.contains(".last.");
            if (!"query".equals(parameter.path("in").asText()) || !accessor || baselineQueries.contains(name)) {
                parameters.add(parameter);
            }
        });
        return result;
    }

    @Test
    void collectionAccessorFilterPreservesBaselineAndNonQueryParameters() throws Exception {
        JsonNode baseline = json.readTree("{\"parameters\":[{\"in\":\"query\",\"name\":\"items.first\",\"required\":true}]}");
        JsonNode live = json.readTree("{\"parameters\":[{\"in\":\"query\",\"name\":\"items.first\",\"required\":false},"
                + "{\"in\":\"header\",\"name\":\"items.last\"},{\"in\":\"query\",\"name\":\"first\"}]}");
        assertEquals(live, withoutCollectionAccessorParameters(baseline, live));
        assertNotEquals(baseline, withoutCollectionAccessorParameters(baseline, live));
    }

    @Test
    void collectionAccessorFilterDropsOnlyUncommittedQueryAccessors() throws Exception {
        JsonNode baseline = json.readTree("{\"parameters\":[]}");
        JsonNode live = json.readTree("{\"parameters\":[{\"in\":\"query\",\"name\":\"items.first\"},"
                + "{\"in\":\"query\",\"name\":\"items.last\"},{\"in\":\"query\",\"name\":\"items.first.code\"},"
                + "{\"in\":\"query\",\"name\":\"items.last.code\"}]}");
        assertEquals(baseline, withoutCollectionAccessorParameters(baseline, live));
        assertEquals(4, live.path("parameters").size());
        assertEquals(baseline, withoutCollectionAccessorParameters(baseline, baseline));
    }
}
