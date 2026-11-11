package com.salesmanager.test.shop.contract;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.salesmanager.shop.application.ShopApplication;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.http.ResponseEntity;
import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest(classes = ShopApplication.class, webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class ApiContractTest {
    @Autowired private TestRestTemplate http;
    private final ObjectMapper json = new ObjectMapper();

    @Test
    void runningApplicationPreservesPublishedOperations() throws Exception {
        ResponseEntity<String> response = http.getForEntity("/v2/api-docs", String.class);
        if (response.getStatusCodeValue() == 404) response = http.getForEntity("/v3/api-docs", String.class);
        assertEquals(200, response.getStatusCodeValue(), response.getBody());
        JsonNode spec = json.readTree(response.getBody());
        JsonNode inventory = OperationInventory.normalize(spec);
        assertTrue(inventory.size() > 0, "Documentation must contain operations");
        Path output = Path.of("target", "contract");
        Files.createDirectories(output);
        json.writerWithDefaultPrettyPrinter().writeValue(output.resolve("api-docs.json").toFile(), spec);
        json.writerWithDefaultPrettyPrinter().writeValue(output.resolve("operations.json").toFile(), inventory);
        if (Boolean.getBoolean("contract.capture")) return; // explicit baseline capture, never used in CI
        try (java.io.InputStream stream = getClass().getResourceAsStream("/contract/operations.json")) {
            assertNotNull(stream, "Committed operation inventory is required");
            JsonNode baseline = json.readTree(stream);
            OperationInventory.assertPreserved(baseline, inventory);
            // Metadata differences are visible for Swagger -> OpenAPI migration review.
            if (!baseline.equals(inventory)) System.out.println(
                    "API metadata changed; compare src/test/resources/contract/operations.json with target/contract/operations.json");
        }
    }
}
