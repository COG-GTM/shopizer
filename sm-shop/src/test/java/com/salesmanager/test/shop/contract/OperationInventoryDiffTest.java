package com.salesmanager.test.shop.contract;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import org.junit.jupiter.api.Test;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.salesmanager.test.shop.contract.OperationInventory.Diff;
import com.salesmanager.test.shop.contract.OperationInventory.Operation;

class OperationInventoryDiffTest {

	private final ObjectMapper mapper = new ObjectMapper();

	@Test
	void reportsExactlyTheRemovedOperation() throws IOException {
		List<Operation> baseline = baseline();
		List<Operation> live = new ArrayList<>(baseline);
		String removedKey = live.remove(0).key;

		Diff diff = OperationInventory.diff(baseline, live);

		assertEquals(List.of(removedKey), diff.missing);
		assertTrue(diff.added.isEmpty());
	}

	@Test
	void reportsAPathRenameAsRemovalAndAddition() throws IOException {
		List<Operation> baseline = baseline();
		List<Operation> live = new ArrayList<>(baseline);
		int index = indexWithPath(live, "/api/v1/cart/");
		Operation original = live.get(index);
		Operation renamed = original.withPath(original.path.replace("/cart/", "/basket/"));
		live.set(index, renamed);

		Diff diff = OperationInventory.diff(baseline, live);

		assertTrue(diff.missing.contains(original.key));
		assertTrue(diff.added.contains(renamed.key));
	}

	@Test
	void pathVariableRenamesKeepTheSameKey() throws IOException {
		List<Operation> baseline = baseline();
		List<Operation> live = new ArrayList<>(baseline);
		int index = indexWithPath(live, "{");
		Operation original = live.get(index);
		live.set(index, original.withPath(original.path.replaceAll("\\{[^}]+\\}", "{renamed}")));

		Diff diff = OperationInventory.diff(baseline, live);

		assertTrue(diff.missing.isEmpty());
		assertTrue(diff.added.isEmpty());
	}

	@Test
	void addedOperationDoesNotReportMissingOperations() throws IOException {
		List<Operation> baseline = baseline();
		List<Operation> live = new ArrayList<>(baseline);
		live.add(OperationInventory.normalize(mapper.readTree("{\"swagger\":\"2.0\",\"paths\":{\"/new\":{\"get\":{}}}}")).get(0));

		Diff diff = OperationInventory.diff(baseline, live);

		assertTrue(diff.missing.isEmpty());
		assertEquals(List.of("GET /new"), diff.added);
	}

	@Test
	void normalizesOpenApi3RequestAndResponseContentAndParameters() throws IOException {
		JsonNode specification = mapper.readTree("{\"openapi\":\"3.0.3\",\"security\":[{\"oauth\":[]}],"
				+ "\"paths\":{\"/api/v1/auth/things/{thingId}\":{"
				+ "\"parameters\":[{\"name\":\"thingId\",\"in\":\"path\",\"required\":true}],"
				+ "\"get\":{\"operationId\":\"getThing\",\"parameters\":["
				+ "{\"name\":\"include\",\"in\":\"query\",\"required\":true}],"
				+ "\"responses\":{\"200\":{\"content\":{\"text/plain\":{},\"application/json\":{}}}}},"
				+ "\"post\":{\"requestBody\":{\"content\":{\"application/json\":{}}},"
				+ "\"responses\":{\"201\":{\"content\":{\"application/xml\":{}}}}}}}}");

		List<Operation> operations = OperationInventory.normalize(specification);

		assertEquals(List.of("GET /api/v1/auth/things/{}", "POST /api/v1/auth/things/{}"),
				List.of(operations.get(0).key, operations.get(1).key));
		assertEquals(List.of("application/json"), operations.get(1).consumes);
		assertEquals(List.of("application/xml"), operations.get(1).produces);
		assertEquals(List.of("application/json", "text/plain"), operations.get(0).produces);
		assertEquals("customer-jwt", operations.get(0).security);
		assertEquals(List.of("oauth"), operations.get(1).declaredSecurity);
		assertEquals("include", operations.get(0).parameters.get(0).name);
		assertTrue(operations.get(0).parameters.get(0).required);
		assertEquals("thingId", operations.get(0).parameters.get(1).name);
		assertTrue(operations.get(0).parameters.get(1).required);
	}

	@Test
	void derivesSecurityClassFromApiPath() throws IOException {
		JsonNode specification = mapper.readTree("{\"swagger\":\"2.0\",\"paths\":{"
				+ "\"/api/v1/private/login\":{\"post\":{}},"
				+ "\"/api/v1/private/login/sso\":{\"get\":{}},"
				+ "\"/api/v1/private/refresh\":{\"post\":{}},"
				+ "\"/api/v1/private/customers\":{\"get\":{}},"
				+ "\"/api/v1/auth/login\":{\"post\":{}},"
				+ "\"/api/v1/auth/refresh\":{\"post\":{}},"
				+ "\"/api/v1/auth/customer/profile\":{\"get\":{}},"
				+ "\"/services/public/DEFAULT\":{\"get\":{}},"
				+ "\"/services/private/system/optin\":{\"post\":{}},"
				+ "\"/other\":{\"get\":{}}}}");

		Map<String, String> securityByPath = OperationInventory.normalize(specification).stream()
				.collect(Collectors.toMap(operation -> operation.path, operation -> operation.security));

		assertEquals(Map.of(
				"/api/v1/private/login", "public",
				"/api/v1/private/login/sso", "public",
				"/api/v1/private/refresh", "public",
				"/api/v1/private/customers", "admin-jwt",
				"/api/v1/auth/login", "public",
				"/api/v1/auth/refresh", "public",
				"/api/v1/auth/customer/profile", "customer-jwt",
				"/services/public/DEFAULT", "public",
				"/services/private/system/optin", "basic",
				"/other", "public"), securityByPath);
	}

	private List<Operation> baseline() throws IOException {
		JsonNode inventory = mapper.readTree(getClass().getResourceAsStream("/contract/operations.json"));
		return mapper.convertValue(inventory.path("operations"),
				mapper.getTypeFactory().constructCollectionType(List.class, Operation.class));
	}

	private int indexWithPath(List<Operation> operations, String fragment) {
		for (int index = 0; index < operations.size(); index++) {
			if (operations.get(index).path.contains(fragment)) {
				return index;
			}
		}
		throw new AssertionError("No operation path contains " + fragment);
	}
}
