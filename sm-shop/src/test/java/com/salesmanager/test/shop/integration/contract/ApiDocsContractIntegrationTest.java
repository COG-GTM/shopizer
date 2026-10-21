package com.salesmanager.test.shop.integration.contract;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.SpringBootTest.WebEnvironment;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.test.context.junit.jupiter.SpringExtension;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.salesmanager.shop.application.ShopApplication;
import com.salesmanager.test.shop.contract.OperationInventory;
import com.salesmanager.test.shop.contract.OperationInventory.Diff;
import com.salesmanager.test.shop.contract.OperationInventory.Operation;

@SpringBootTest(classes = ShopApplication.class, webEnvironment = WebEnvironment.RANDOM_PORT)
@ExtendWith(SpringExtension.class)
class ApiDocsContractIntegrationTest {

	private static final ObjectMapper MAPPER = new ObjectMapper();
	private static final Pattern PATH_VARIABLE = Pattern.compile("\\{[^/{}]+\\}");
	private static final TypeReference<List<Operation>> OPERATION_LIST_TYPE = new TypeReference<List<Operation>>() { };

	@Autowired
	private TestRestTemplate testRestTemplate;

	@Test
	void liveApiHasNoMissingOrAddedOperations() throws IOException {
		List<Operation> live = liveOperations();
		List<Operation> committed = committedOperations();
		Diff diff = OperationInventory.diff(committed, live);

		if (!diff.added.isEmpty()) {
			System.out.println("Added live contract operations: " + diff.added);
		}
		for (String key : diff.changed) {
			Operation committedOperation = operationWithKey(committed, key);
			Operation liveOperation = operationWithKey(live, key);
			System.out.println("Changed contract operation: key=" + key
					+ ", committed={" + describeOperation(committedOperation)
					+ "}, live={" + describeOperation(liveOperation) + "}");
		}
		assertTrue(diff.missing.isEmpty(), "Missing live operations: " + diff.missing);
		assertTrue(diff.added.isEmpty(), "Added live operations: " + diff.added);
	}

	@Test
	void jwtClassifiedOperationsRejectAnonymousRequests() throws IOException {
		List<String> violations = new ArrayList<>();
		for (Operation operation : liveOperations()) {
			if (!Arrays.asList("admin-jwt", "customer-jwt").contains(operation.security)) {
				continue;
			}

			String path = PATH_VARIABLE.matcher(operation.path).replaceAll("1");
			HttpHeaders headers = new HttpHeaders();
			boolean hasBody = Arrays.asList("POST", "PUT", "PATCH").contains(operation.method);
			HttpEntity<?> request;
			if (hasBody) {
				headers.setContentType(MediaType.APPLICATION_JSON);
				request = new HttpEntity<>("{}", headers);
			} else {
				request = new HttpEntity<>(headers);
			}

			ResponseEntity<String> response = testRestTemplate.exchange(path, HttpMethod.valueOf(operation.method),
					request, String.class);
			if (response.getStatusCode() != HttpStatus.UNAUTHORIZED
					&& response.getStatusCode() != HttpStatus.FORBIDDEN) {
				violations.add(operation.method + " " + path + " -> " + response.getStatusCodeValue());
			}
		}

		violations.forEach(violation -> System.out.println("Anonymous JWT endpoint violation: " + violation));
		assertTrue(violations.isEmpty(), "Anonymous JWT endpoint violations: " + violations);
	}

	@Test
	void removingOneUniqueAdminOperationIsReportedAsMissing() throws IOException {
		List<Operation> live = liveOperations();
		Map<String, Long> keyCounts = live.stream()
				.collect(Collectors.groupingBy(operation -> operation.key, Collectors.counting()));
		Operation adminOperation = live.stream()
				.filter(operation -> "admin-jwt".equals(operation.security))
				.filter(operation -> keyCounts.get(operation.key) == 1L)
				.findFirst().orElse(null);
		assertNotNull(adminOperation, "No uniquely keyed admin-jwt operation was found");
		List<Operation> mutated = new ArrayList<>(live);
		assertTrue(mutated.remove(adminOperation));

		Diff diff = OperationInventory.diff(live, mutated);

		assertEquals(Collections.singletonList(adminOperation.key), diff.missing);
		assertTrue(diff.added.isEmpty());
		assertTrue(diff.changed.isEmpty());
	}

	private Operation operationWithKey(List<Operation> operations, String key) {
		Operation match = null;
		for (Operation operation : operations) {
			if (key.equals(operation.key)) {
				match = operation;
			}
		}
		assertNotNull(match, "Missing operation for changed key " + key);
		return match;
	}

	private String describeOperation(Operation operation) {
		String parameters = operation.parameters.stream()
				.map(parameter -> parameter.name + "/" + parameter.in + "/" + parameter.required)
				.collect(Collectors.joining(", ", "[", "]"));
		return "method=" + operation.method
				+ ", path=" + operation.path
				+ ", parameters=" + parameters
				+ ", consumes=" + operation.consumes
				+ ", produces=" + operation.produces;
	}

	private List<Operation> liveOperations() throws IOException {
		ResponseEntity<String> response = testRestTemplate.getForEntity("/v2/api-docs", String.class);
		assertEquals(HttpStatus.OK, response.getStatusCode());
		assertNotNull(response.getBody());
		JsonNode specification = MAPPER.readTree(response.getBody());
		assertEquals("2.0", specification.path("swagger").asText());
		return OperationInventory.normalize(specification);
	}

	private List<Operation> committedOperations() throws IOException {
		try (InputStream input = getClass().getResourceAsStream("/contract/operations.json")) {
			assertNotNull(input, "Missing committed operations inventory");
			JsonNode document = MAPPER.readTree(input);
			return MAPPER.convertValue(document.path("operations"), OPERATION_LIST_TYPE);
		}
	}
}
