package com.salesmanager.test.shop.contract;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.util.Arrays;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;

/** Operation identities deliberately retain path-variable names: never merge two routes. */
final class OperationInventory {
    private static final ObjectMapper JSON = new ObjectMapper();
    private static final Set<String> METHODS = new TreeSet<>(Arrays.asList(
            "get", "put", "post", "delete", "patch", "head", "options", "trace"));

    static ArrayNode normalize(JsonNode spec) {
        Map<String, ObjectNode> operations = new TreeMap<>();
        spec.path("paths").fields().forEachRemaining(path -> {
            JsonNode item = resolve(spec, path.getValue());
            item.fields().forEachRemaining(method -> {
                if (!METHODS.contains(method.getKey())) return;
                JsonNode op = method.getValue();
                ObjectNode entry = JSON.createObjectNode();
                entry.put("method", method.getKey().toUpperCase(java.util.Locale.ROOT));
                entry.put("path", path.getKey());
                Map<String, JsonNode> parameters = new TreeMap<>();
                for (JsonNode source : Arrays.asList(item.path("parameters"), op.path("parameters"))) {
                    for (JsonNode raw : source) {
                        JsonNode p = resolve(spec, raw);
                        ObjectNode parameter = JSON.createObjectNode();
                        parameter.put("in", p.path("in").asText());
                        // Swagger body argument names are not part of the wire contract.
                        parameter.put("name", "body".equals(p.path("in").asText()) ? "body" : p.path("name").asText());
                        parameter.put("required", p.path("required").asBoolean());
                        parameters.put(parameter.path("in").asText() + ":" + parameter.path("name").asText(), parameter);
                    }
                }
                if (op.has("requestBody")) {
                    JsonNode body = resolve(spec, op.get("requestBody"));
                    ObjectNode p = JSON.createObjectNode().put("in", "body").put("name", "body")
                            .put("required", body.path("required").asBoolean());
                    parameters.put("body:body", p);
                }
                ArrayNode params = entry.putArray("parameters");
                parameters.values().forEach(params::add);
                entry.set("requestMediaTypes", media(spec, op, true));
                entry.set("responseMediaTypes", media(spec, op, false));
                // Empty means the published spec declares no security requirement. It is
                // not a claim that the Spring Security filter chain permits anonymous access.
                entry.set("security", sorted(op.has("security") ? op.get("security") : spec.path("security")));
                operations.put(key(entry), entry);
            });
        });
        ArrayNode result = JSON.createArrayNode();
        operations.values().forEach(result::add);
        return result;
    }

    private static JsonNode resolve(JsonNode root, JsonNode node) {
        String ref = node.path("$ref").asText();
        return ref.startsWith("#/") ? root.at(ref.substring(1)) : node;
    }

    private static ArrayNode media(JsonNode spec, JsonNode op, boolean request) {
        Set<String> types = new TreeSet<>();
        String field = request ? "consumes" : "produces";
        for (JsonNode type : op.has(field) ? op.get(field) : spec.path(field)) types.add(type.asText());
        if (request) {
            resolve(spec, op.path("requestBody")).path("content").fieldNames().forEachRemaining(types::add);
        } else {
            op.path("responses").forEach(response -> resolve(spec, response).path("content")
                    .fieldNames().forEachRemaining(types::add));
        }
        ArrayNode result = JSON.createArrayNode();
        types.forEach(result::add);
        return result;
    }

    private static ArrayNode sorted(JsonNode values) {
        Map<String, JsonNode> sorted = new TreeMap<>();
        values.forEach(v -> sorted.put(v.toString(), v));
        ArrayNode result = JSON.createArrayNode();
        sorted.values().forEach(result::add);
        return result;
    }

    static String key(JsonNode operation) {
        return operation.path("method").asText() + " " + operation.path("path").asText();
    }

    static void assertPreserved(JsonNode baseline, JsonNode current) {
        Set<String> actual = new TreeSet<>();
        current.forEach(op -> actual.add(key(op)));
        Set<String> missing = new TreeSet<>();
        baseline.forEach(op -> { if (!actual.contains(key(op))) missing.add(key(op)); });
        if (!missing.isEmpty()) throw new AssertionError("Removed or renamed API operations: " + missing);
    }
}
