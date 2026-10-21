package com.salesmanager.test.shop.contract;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.SpringBootTest.WebEnvironment;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.test.context.junit.jupiter.SpringExtension;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.salesmanager.shop.application.ShopApplication;
import com.salesmanager.test.shop.contract.OperationInventory.Diff;
import com.salesmanager.test.shop.contract.OperationInventory.Operation;

@SpringBootTest(classes = ShopApplication.class, webEnvironment = WebEnvironment.RANDOM_PORT)
@ExtendWith(SpringExtension.class)
class ContractSnapshotTest {

	private static final ObjectMapper MAPPER = new ObjectMapper()
			.enable(SerializationFeature.INDENT_OUTPUT)
			.enable(SerializationFeature.ORDER_MAP_ENTRIES_BY_KEYS);

	@Autowired
	private TestRestTemplate testRestTemplate;

	@Test
	void liveApiMatchesCommittedOperationInventory() throws IOException {
		String specificationPath = System.getProperty("contract.spec.path", "/v2/api-docs");
		ResponseEntity<String> response = testRestTemplate.getForEntity(specificationPath, String.class);
		assertEquals(HttpStatus.OK, response.getStatusCode(), "Could not retrieve " + specificationPath);
		assertNotNull(response.getBody());

		JsonNode specification = MAPPER.readTree(response.getBody());
		List<Operation> live = OperationInventory.normalize(specification);
		if (Boolean.getBoolean("contract.update")) {
			writeSnapshot(specificationPath, specification, live);
			return;
		}

		JsonNode baselineDocument = MAPPER.readTree(getClass().getResourceAsStream("/contract/operations.json"));
		List<Operation> baseline = MAPPER.convertValue(baselineDocument.path("operations"),
				MAPPER.getTypeFactory().constructCollectionType(List.class, Operation.class));
		Diff diff = OperationInventory.diff(baseline, live);
		System.out.println("Contract inventory: " + live.size() + " operations; added=" + diff.added.size()
				+ ", changed=" + diff.changed.size() + ", missing=" + diff.missing.size());
		if (!diff.added.isEmpty()) {
			System.out.println("Added operations: " + diff.added);
		}
		if (!diff.changed.isEmpty()) {
			System.out.println("Changed operations: " + diff.changed);
		}
		assertTrue(diff.missing.isEmpty(), missingMessage(diff.missing));
	}

	private void writeSnapshot(String specificationPath, JsonNode specification, List<Operation> operations)
			throws IOException {
		Path contractDirectory = Paths.get("src/test/resources/contract");
		Files.createDirectories(contractDirectory);
		MAPPER.writeValue(contractDirectory.resolve("api-docs-v2.json").toFile(), sortObjectKeys(specification));

		ObjectNode inventory = MAPPER.createObjectNode();
		inventory.put("source", specificationPath);
		String format = specification.has("swagger")
				? specification.path("swagger").asText()
				: specification.path("openapi").asText();
		inventory.put("format", format);
		inventory.put("operationCount", operations.size());
		ArrayNode operationNodes = inventory.putArray("operations");
		for (Operation operation : operations) {
			operationNodes.add(MAPPER.valueToTree(operation));
		}
		MAPPER.writeValue(contractDirectory.resolve("operations.json").toFile(), inventory);
	}

	private JsonNode sortObjectKeys(JsonNode value) {
		if (value.isObject()) {
			ObjectNode sorted = MAPPER.createObjectNode();
			Map<String, JsonNode> fields = new TreeMap<>();
			value.fields().forEachRemaining(entry -> fields.put(entry.getKey(), entry.getValue()));
			fields.forEach((key, child) -> sorted.set(key, sortObjectKeys(child)));
			return sorted;
		}
		if (value.isArray()) {
			ArrayNode sorted = MAPPER.createArrayNode();
			value.forEach(child -> sorted.add(sortObjectKeys(child)));
			return sorted;
		}
		return value;
	}

	private String missingMessage(List<String> missing) {
		return "Removed or renamed operation(s):\n" + String.join("\n", missing)
				+ "\nremoved or renamed operation; if intentional regenerate with -Dcontract.update=true and justify in the PR";
	}
}
