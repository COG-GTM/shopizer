package com.salesmanager.test.shop.contract;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

import org.junit.jupiter.api.Test;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.salesmanager.test.shop.contract.OperationInventory.Diff;
import com.salesmanager.test.shop.contract.OperationInventory.Operation;

class OperationInventoryNormalizationTest {

	private static final ObjectMapper MAPPER = new ObjectMapper();

	@Test
	void rejectsUnsupportedDocumentVersions() throws IOException {
		assertThrows(IllegalArgumentException.class,
				() -> OperationInventory.normalize(MAPPER.readTree("{\"swagger\":\"1.2\",\"paths\":{}}")));
		assertThrows(IllegalArgumentException.class,
				() -> OperationInventory.normalize(MAPPER.readTree("{}")));
	}

	@Test
	void inheritsSwaggerMediaTypesAndAllowsOperationOverrides() throws IOException {
		JsonNode specification = MAPPER.readTree("{\"swagger\":\"2.0\","
				+ "\"consumes\":[\"application/json\"],\"produces\":[\"application/json\"],"
				+ "\"paths\":{\"/inherited\":{\"get\":{}},"
				+ "\"/overridden\":{\"get\":{\"consumes\":[\"application/xml\"],"
				+ "\"produces\":[\"application/xml\"]}}}}");

		List<Operation> operations = OperationInventory.normalize(specification);
		Operation inherited = operation(operations, "GET /inherited");
		Operation overridden = operation(operations, "GET /overridden");

		assertEquals(Collections.singletonList("application/json"), inherited.consumes);
		assertEquals(Collections.singletonList("application/json"), inherited.produces);
		assertEquals(Collections.singletonList("application/xml"), overridden.consumes);
		assertEquals(Collections.singletonList("application/xml"), overridden.produces);
	}

	@Test
	void mergesPathAndOperationParametersAndOmitsBodyParameters() throws IOException {
		JsonNode specification = MAPPER.readTree("{\"swagger\":\"2.0\",\"paths\":{\"/items/{id}\":{"
				+ "\"parameters\":[{\"name\":\"id\",\"in\":\"query\",\"required\":false},"
				+ "{\"name\":\"payload\",\"in\":\"body\",\"required\":true}],"
				+ "\"get\":{\"parameters\":[{\"name\":\"id\",\"in\":\"query\",\"required\":true},"
				+ "{\"name\":\"sort\",\"in\":\"query\",\"required\":false}]}}}}");

		Operation operation = OperationInventory.normalize(specification).get(0);

		assertEquals(2, operation.parameters.size());
		assertTrue(operation.parameters.stream().noneMatch(parameter -> "payload".equals(parameter.name)));
		assertTrue(operation.parameters.stream()
				.anyMatch(parameter -> "id".equals(parameter.name) && parameter.required));
		assertTrue(operation.parameters.stream()
				.anyMatch(parameter -> "sort".equals(parameter.name) && !parameter.required));
	}

	@Test
	void ignoresNonHttpPathItemKeysUppercasesMethodsAndSortsOperations() throws IOException {
		JsonNode specification = MAPPER.readTree("{\"swagger\":\"2.0\",\"paths\":{"
				+ "\"/z\":{\"parameters\":[],\"x-foo\":{},\"$ref\":\"#/definitions/ignored\","
				+ "\"post\":{},\"get\":{}},\"/a\":{\"get\":{}}}}");

		List<Operation> operations = OperationInventory.normalize(specification);

		assertEquals(Arrays.asList("GET /a", "GET /z", "POST /z"),
				operations.stream().map(operation -> operation.method + " " + operation.path)
						.collect(Collectors.toList()));
	}

	@Test
	void diffReportsRequiredAndProducesChangesAsContractChanges() throws IOException {
		List<Operation> baseline = normalizeOperation("{\"parameters\":["
				+ "{\"name\":\"page\",\"in\":\"query\",\"required\":false}]}");
		List<Operation> changedParameter = normalizeOperation("{\"parameters\":["
				+ "{\"name\":\"page\",\"in\":\"query\",\"required\":true}]}");
		Diff parameterDiff = OperationInventory.diff(baseline, changedParameter);

		assertEquals(Collections.singletonList("GET /items"), parameterDiff.changed);
		assertTrue(parameterDiff.missing.isEmpty());
		assertTrue(parameterDiff.added.isEmpty());

		List<Operation> baselineProduces = normalizeOperation("{\"produces\":[\"application/json\"]}");
		List<Operation> changedProduces = normalizeOperation("{\"produces\":[\"application/xml\"]}");
		Diff producesDiff = OperationInventory.diff(baselineProduces, changedProduces);

		assertEquals(Collections.singletonList("GET /items"), producesDiff.changed);
		assertTrue(producesDiff.missing.isEmpty());
		assertTrue(producesDiff.added.isEmpty());
	}

	@Test
	void diffIsEmptyForIdenticalListsAndIgnoresMetadataOnlyChanges() throws IOException {
		List<Operation> baseline = normalizeOperation(
				"{\"operationId\":\"before\",\"tags\":[\"before\"],\"produces\":[\"application/json\"]}");
		List<Operation> identical = new ArrayList<>(baseline);
		List<Operation> metadataOnly = normalizeOperation(
				"{\"operationId\":\"after\",\"tags\":[\"after\"],\"produces\":[\"application/json\"]}");

		assertTrue(OperationInventory.diff(baseline, identical).isEmpty());
		assertTrue(OperationInventory.diff(baseline, metadataOnly).isEmpty());
	}

	@Test
	void normalizedOperationsSurviveJacksonRoundTrip() throws IOException {
		List<Operation> original = normalizeOperation("{\"consumes\":[\"application/json\"],"
				+ "\"parameters\":[{\"name\":\"id\",\"in\":\"path\",\"required\":true}],"
				+ "\"produces\":[\"application/json\"]}");
		List<Operation> roundTripped = MAPPER.readValue(MAPPER.writeValueAsBytes(original),
				new TypeReference<List<Operation>>() { });

		assertTrue(OperationInventory.diff(original, roundTripped).isEmpty());
	}

	@Test
	void committedContractResourcesMatchWithExpectedCounts() throws IOException {
		JsonNode specification = readResource("/contract/api-docs-v2.json");
		JsonNode inventory = readResource("/contract/operations.json");
		JsonNode operationNodes = inventory.path("operations");
		List<Operation> committed = MAPPER.convertValue(operationNodes, new TypeReference<List<Operation>>() { });
		List<Operation> normalized = OperationInventory.normalize(specification);
		Set<String> keys = committed.stream().map(operation -> operation.key).collect(Collectors.toSet());
		Map<String, Long> securityCounts = committed.stream()
				.collect(Collectors.groupingBy(operation -> operation.security, Collectors.counting()));
		Diff diff = OperationInventory.diff(committed, normalized);

		assertTrue(diff.isEmpty(), "missing=" + diff.missing + ", added=" + diff.added + ", changed=" + diff.changed);
		assertEquals(309, inventory.path("operationCount").asInt());
		assertEquals(309, operationNodes.size());
		assertEquals(308, keys.size());
		assertEquals(210L, securityCounts.get("admin-jwt").longValue());
		assertEquals(25L, securityCounts.get("customer-jwt").longValue());
		assertEquals(74L, securityCounts.get("public").longValue());
	}

	private List<Operation> normalizeOperation(String operation) throws IOException {
		JsonNode specification = MAPPER.readTree(
				"{\"swagger\":\"2.0\",\"paths\":{\"/items\":{\"get\":" + operation + "}}}");
		return OperationInventory.normalize(specification);
	}

	private Operation operation(List<Operation> operations, String key) {
		Operation matching = operations.stream().filter(operation -> key.equals(operation.key)).findFirst().orElse(null);
		assertNotNull(matching, "Missing operation " + key);
		return matching;
	}

	private JsonNode readResource(String path) throws IOException {
		try (InputStream input = getClass().getResourceAsStream(path)) {
			assertNotNull(input, "Missing resource " + path);
			return MAPPER.readTree(input);
		}
	}
}
