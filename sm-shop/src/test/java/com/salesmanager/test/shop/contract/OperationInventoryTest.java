package com.salesmanager.test.shop.contract;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class OperationInventoryTest {
    private final ObjectMapper json = new ObjectMapper();

    @Test
    void everyIndividualOperationRemovalFailsIncludingSimilarTemplatePaths() throws Exception {
        JsonNode baseline = json.readTree(getClass().getResourceAsStream("/contract/operations.json"));
        assertTrue(baseline.size() > 0);
        for (int i = 0; i < baseline.size(); i++) {
            ArrayNode changed = baseline.deepCopy();
            JsonNode removed = changed.remove(i);
            AssertionError error = assertThrows(AssertionError.class,
                    () -> OperationInventory.assertPreserved(baseline, changed));
            assertTrue(error.getMessage().contains(OperationInventory.key(removed)));
        }
    }

    @Test
    void swaggerAndOpenApiNormalizeToTheSameWireMetadata() throws Exception {
        JsonNode swagger = json.readTree("{\"swagger\":\"2.0\",\"consumes\":[\"application/json\"],\"produces\":[\"application/json\"],\"security\":[{\"jwt\":[]}],\"paths\":{\"/items/{id}\":{\"parameters\":[{\"in\":\"path\",\"name\":\"id\",\"required\":true}],\"post\":{\"parameters\":[{\"in\":\"body\",\"name\":\"item\",\"required\":true}]}}}}");
        JsonNode openapi = json.readTree("{\"openapi\":\"3.0.1\",\"paths\":{\"/items/{id}\":{\"post\":{\"security\":[{\"jwt\":[]}],\"parameters\":[{\"in\":\"path\",\"name\":\"id\",\"required\":true}],\"requestBody\":{\"required\":true,\"content\":{\"application/json\":{}}},\"responses\":{\"200\":{\"content\":{\"application/json\":{}}}}}}}}");
        assertEquals(OperationInventory.normalize(swagger), OperationInventory.normalize(openapi));
    }

    @Test
    void renamedPathOrMethodFailsAndAdditionsAreAllowed() throws Exception {
        JsonNode baseline = json.readTree("[{\"method\":\"GET\",\"path\":\"/category/{id}\"}]");
        assertThrows(AssertionError.class, () -> OperationInventory.assertPreserved(baseline,
                json.readTree("[{\"method\":\"GET\",\"path\":\"/category/{friendlyUrl}\"}]")));
        assertThrows(AssertionError.class, () -> OperationInventory.assertPreserved(baseline,
                json.readTree("[{\"method\":\"POST\",\"path\":\"/category/{id}\"}]")));
        OperationInventory.assertPreserved(baseline, json.readTree(
                "[{\"method\":\"GET\",\"path\":\"/category/{id}\"},{\"method\":\"GET\",\"path\":\"/new\"}]"));
    }
}
