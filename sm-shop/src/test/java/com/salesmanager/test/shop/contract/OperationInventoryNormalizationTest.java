package com.salesmanager.test.shop.contract;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class OperationInventoryNormalizationTest {
    private final ObjectMapper json = new ObjectMapper();

    private JsonNode normalize(String spec) throws Exception {
        return OperationInventory.normalize(json.readTree(spec));
    }

    @Test
    void resolvesReferencesAndMergesParametersWithOperationPrecedence() throws Exception {
        JsonNode result = normalize("{\"parameters\":{\"id\":{\"in\":\"query\",\"name\":\"id\",\"required\":true}},"
                + "\"paths\":{\"/items\":{\"parameters\":[{\"$ref\":\"#/parameters/id\"},"
                + "{\"in\":\"header\",\"name\":\"X-Test\",\"required\":true}],"
                + "\"get\":{\"parameters\":[{\"in\":\"query\",\"name\":\"id\",\"required\":false}]},\"post\":{}}}}");
        assertEquals(json.readTree("[{\"in\":\"header\",\"name\":\"X-Test\",\"required\":true},"
                + "{\"in\":\"query\",\"name\":\"id\",\"required\":false}]"), result.get(0).path("parameters"));
        assertTrue(result.get(1).path("parameters").get(1).path("required").asBoolean());
    }

    @Test
    void ignoresNonHttpKeysAndSortsStablyByMethodThenPath() throws Exception {
        String spec = "{\"paths\":{\"/z\":{\"post\":{},\"parameters\":[],\"x-foo\":{},\"get\":{}},\"/a\":{\"get\":{}}}}";
        JsonNode first = normalize(spec);
        assertEquals(first, normalize(spec));
        assertEquals(3, first.size());
        assertEquals("GET /a", OperationInventory.key(first.get(0)));
        assertEquals("GET /z", OperationInventory.key(first.get(1)));
        assertEquals("POST /z", OperationInventory.key(first.get(2)));
    }

    @Test
    void securityUsesOperationOverrideThenGlobalThenEmpty() throws Exception {
        JsonNode result = normalize("{\"security\":[{\"global\":[]}],\"paths\":{\"/a\":{\"get\":{},"
                + "\"post\":{\"security\":[{\"local\":[]}]},\"put\":{\"security\":[]}}}}");
        assertEquals(json.readTree("[{\"global\":[]}]"), result.get(0).path("security"));
        assertEquals(json.readTree("[{\"local\":[]}]"), result.get(1).path("security"));
        assertEquals(json.readTree("[]"), result.get(2).path("security"));
        assertEquals(json.readTree("[]"), normalize("{\"paths\":{\"/a\":{\"get\":{}}}}").get(0).path("security"));
    }

    @Test
    void mediaTypesUseOperationOverridesInsteadOfGlobals() throws Exception {
        JsonNode result = normalize("{\"consumes\":[\"global/request\"],\"produces\":[\"global/response\"],"
                + "\"paths\":{\"/a\":{\"get\":{},\"post\":{\"consumes\":[\"local/request\"],"
                + "\"produces\":[\"local/response\"]},\"put\":{\"consumes\":[],\"produces\":[]}}}}");
        assertEquals(json.readTree("[\"global/request\"]"), result.get(0).path("requestMediaTypes"));
        assertEquals(json.readTree("[\"global/response\"]"), result.get(0).path("responseMediaTypes"));
        assertEquals(json.readTree("[\"local/request\"]"), result.get(1).path("requestMediaTypes"));
        assertEquals(json.readTree("[\"local/response\"]"), result.get(1).path("responseMediaTypes"));
        assertTrue(result.get(2).path("requestMediaTypes").isEmpty());
        assertTrue(result.get(2).path("responseMediaTypes").isEmpty());
    }

    @Test
    void resolvesOpenApiResponseReferencesAndCollectsSortedContentTypes() throws Exception {
        JsonNode result = normalize("{\"openapi\":\"3.0.0\",\"components\":{\"responses\":{\"ok\":{\"content\":{"
                + "\"text/plain\":{},\"application/json\":{}}}}},\"paths\":{\"/a\":{\"get\":{\"responses\":{"
                + "\"200\":{\"$ref\":\"#/components/responses/ok\"},\"400\":{\"content\":{\"application/json\":{}}}}}}}}");
        assertEquals(json.readTree("[\"application/json\",\"text/plain\"]"), result.get(0).path("responseMediaTypes"));
    }

    @Test
    void emptyDocumentHasNoOperations() throws Exception {
        assertTrue(normalize("{}").isEmpty());
    }

    @Test
    void committedFixturesMatchExactlyWith309UniqueSortedOperations() throws Exception {
        JsonNode spec = json.readTree(getClass().getResourceAsStream("/contract/swagger-v2.json"));
        JsonNode expected = json.readTree(getClass().getResourceAsStream("/contract/operations.json"));
        JsonNode actual = OperationInventory.normalize(spec);
        assertEquals(expected, actual);
        assertEquals(actual, OperationInventory.normalize(spec));
        assertEquals(309, actual.size());
        List<String> keys = new ArrayList<>();
        actual.forEach(op -> keys.add(OperationInventory.key(op)));
        assertEquals(actual.size(), new HashSet<>(keys).size());
        List<String> sorted = new ArrayList<>(keys);
        Collections.sort(sorted);
        assertEquals(sorted, keys);
    }
}
