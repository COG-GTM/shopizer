package com.salesmanager.test.shop.contract;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import java.util.TreeMap;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.boot.web.client.RestTemplateBuilder;
import static org.junit.jupiter.api.Assertions.*;

@EnabledIfSystemProperty(named = "parity.url", matches = "http://127\\.0\\.0\\.1:[0-9]+")
class PackagedContractTest {
    private final TestRestTemplate http = new TestRestTemplate(
            new RestTemplateBuilder().rootUri(System.getProperty("parity.url")));
    private final ObjectMapper json = new ObjectMapper();

    @Test
    void openApiInventoryAndOperationalEndpointsPreserveContract() throws Exception {
        var response = http.getForEntity("/v3/api-docs", String.class);
        assertEquals(200, response.getStatusCode().value());
        JsonNode spec = json.readTree(response.getBody());
        JsonNode current = OperationInventory.normalize(spec);
        assertFalse(current.isEmpty());
        JsonNode baseline;
        try (var stream = getClass().getResourceAsStream("/contract/operations.json")) {
            assertNotNull(stream);
            baseline = json.readTree(stream);
        }
        Map<String, JsonNode> before = new TreeMap<>(), after = new TreeMap<>();
        baseline.forEach(op -> before.put(OperationInventory.key(op), op));
        current.forEach(op -> after.put(OperationInventory.key(op), op));
        ObjectNode diff = json.createObjectNode();
        var removed = diff.putArray("removed");
        var added = diff.putArray("added");
        var changed = diff.putArray("metadataChanged");
        var fieldCounts = diff.putObject("metadataFieldCounts");
        for (String field : java.util.List.of("parameters", "requestMediaTypes", "responseMediaTypes", "security"))
            fieldCounts.put(field, 0);
        before.forEach((key, value) -> {
            if (!after.containsKey(key)) removed.add(key);
            else if (!value.equals(after.get(key))) {
                changed.add(key);
                for (String field : java.util.List.of("parameters", "requestMediaTypes", "responseMediaTypes", "security")) {
                    if (!value.path(field).equals(after.get(key).path(field)))
                        fieldCounts.put(field, fieldCounts.path(field).asInt() + 1);
                }
            }
        });
        after.keySet().stream().filter(key -> !before.containsKey(key)).forEach(added::add);
        diff.put("baselineCount", before.size()).put("currentCount", after.size());
        Path output = Path.of(System.getProperty("parity.output"));
        Files.createDirectories(output);
        json.writerWithDefaultPrettyPrinter().writeValue(output.resolve("api-docs.json").toFile(), spec);
        json.writerWithDefaultPrettyPrinter().writeValue(output.resolve("operations.json").toFile(), current);
        json.writerWithDefaultPrettyPrinter().writeValue(output.resolve("contract-diff.json").toFile(), diff);
        OperationInventory.assertPreserved(baseline, current);
        var swagger = http.getForEntity("/swagger-ui/index.html", String.class);
        assertEquals(200, swagger.getStatusCode().value());
        assertTrue(swagger.getBody().contains("Swagger UI"));
        var health = http.getForEntity("/actuator/health", String.class);
        assertEquals(200, health.getStatusCode().value());
        JsonNode healthDocument = json.readTree(health.getBody());
        assertEquals("UP", healthDocument.path("status").asText());
        assertEquals(System.getProperty("parity.database"), healthDocument.path("components")
                .path("db").path("details").path("database").asText(), "Must exercise the intended database engine");
        var info = http.getForEntity("/actuator/info", String.class);
        assertEquals(200, info.getStatusCode().value());
        assertTrue(json.readTree(info.getBody()).isObject());
        System.out.println("PACKAGED-PARITY baseline=" + before.size() + " current=" + after.size()
                + " removed=" + removed.size() + " added=" + added.size()
                + " metadataChanged=" + changed.size() + " health=UP swagger=200 info=200");
    }
}
