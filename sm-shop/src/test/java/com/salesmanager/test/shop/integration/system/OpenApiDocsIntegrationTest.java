package com.salesmanager.test.shop.integration.system;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.SpringBootTest.WebEnvironment;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.test.context.junit4.SpringRunner;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.salesmanager.shop.application.ShopApplication;
import com.salesmanager.test.shop.common.ServicesTestSupport;

@SpringBootTest(classes = ShopApplication.class, webEnvironment = WebEnvironment.RANDOM_PORT)
@RunWith(SpringRunner.class)
public class OpenApiDocsIntegrationTest extends ServicesTestSupport {

	@Autowired
	private TestRestTemplate testRestTemplate;

	@Autowired
	private ObjectMapper objectMapper;

	@Test
	public void anonymousOpenApiDocumentsExposeJwtSecurity() throws Exception {
		ResponseEntity<String> response = testRestTemplate.getForEntity("/v3/api-docs", String.class);
		assertEquals(HttpStatus.OK, response.getStatusCode());
		JsonNode docs = objectMapper.readTree(response.getBody());

		assertTrue(docs.path("openapi").asText().startsWith("3."));
		assertEquals("Shopizer REST API", docs.path("info").path("title").asText());
		assertEquals("http", docs.path("components").path("securitySchemes").path("JWT").path("type").asText());
		assertEquals("bearer", docs.path("components").path("securitySchemes").path("JWT").path("scheme").asText());
		assertTrue(containsSecurityRequirement(docs, "JWT"));

		ResponseEntity<String> groupedResponse = testRestTemplate.getForEntity("/v3/api-docs/default", String.class);
		assertEquals(HttpStatus.OK, groupedResponse.getStatusCode());
		JsonNode groupedDocs = objectMapper.readTree(groupedResponse.getBody());
		assertTrue(groupedDocs.path("openapi").asText().startsWith("3."));
	}

	@Test
	public void groupedDocsContainStorefrontAndAuthenticationMappingsOnly() throws Exception {
		JsonNode docs = getDocs("/v3/api-docs/default");
		JsonNode paths = docs.path("paths");
		for (String path : new String[] {
				"/api/v1/private/login",
				"/api/v1/customer/login",
				"/api/v1/cart",
				"/api/v1/products",
				"/api/v1/category",
				"/api/v1/store/{code}" }) {
			assertTrue("Missing OpenAPI path " + path, paths.has(path));
		}
		for (Iterator<String> iterator = paths.fieldNames(); iterator.hasNext();) {
			String path = iterator.next();
			assertFalse("Unexpected path " + path, path.startsWith("/actuator") || path.startsWith("/services"));
		}
	}

	@Test
	public void getOperationsHaveGlobalErrorResponses() throws Exception {
		JsonNode docs = getDocs("/v3/api-docs");
		JsonNode paths = docs.path("paths");
		for (Iterator<Map.Entry<String, JsonNode>> pathIterator = paths.fields(); pathIterator.hasNext();) {
			Map.Entry<String, JsonNode> path = pathIterator.next();
			for (Iterator<Map.Entry<String, JsonNode>> operationIterator = path.getValue().fields();
					operationIterator.hasNext();) {
				Map.Entry<String, JsonNode> operation = operationIterator.next();
				if ("get".equals(operation.getKey())) {
					JsonNode responses = operation.getValue().path("responses");
					assertTrue(path.getKey() + " GET missing 401", responses.has("401"));
					assertTrue(path.getKey() + " GET missing 403", responses.has("403"));
					assertTrue(path.getKey() + " GET missing 500", responses.has("500"));
				}
			}
		}
	}

	@Test
	public void controllerTagsAndOperationIdsAreStable() throws Exception {
		JsonNode paths = getDocs("/v3/api-docs").path("paths");
		assertHasTag(paths, "/api/v1/products", "get",
				"Product definition resource (Create udtate and delete product definition. Serves api v1 and v2 with backward compatibility)");
		assertHasTag(paths, "/api/v1/category", "get",
				"Category management resource (Category Management Api)");
		assertHasTag(paths, "/api/v1/store/{code}", "get",
				"Merchant and store management resource (Merchant - Store Management Api)");

		Map<String, String> operationLocations = new HashMap<>();
		List<String> duplicateOperationIds = new ArrayList<>();
		for (Iterator<Map.Entry<String, JsonNode>> pathIterator = paths.fields(); pathIterator.hasNext();) {
			Map.Entry<String, JsonNode> path = pathIterator.next();
			for (Iterator<Map.Entry<String, JsonNode>> operationIterator = path.getValue().fields();
					operationIterator.hasNext();) {
				Map.Entry<String, JsonNode> operation = operationIterator.next();
				String location = operation.getKey().toUpperCase() + " " + path.getKey();
				String operationId = operation.getValue().path("operationId").asText();
				if (operationId.isEmpty()) {
					duplicateOperationIds.add("missing operationId at " + location);
				} else {
					String previousLocation = operationLocations.putIfAbsent(operationId, location);
					if (previousLocation != null) {
						duplicateOperationIds.add(operationId + ": " + previousLocation + " and " + location);
					}
				}
			}
		}
		assertTrue("Duplicate or missing operationIds: " + duplicateOperationIds, duplicateOperationIds.isEmpty());
	}

	@Test
	public void swaggerUiAndDocsIgnoreAuthorizationHeaders() {
		ResponseEntity<String> config = testRestTemplate.getForEntity("/v3/api-docs/swagger-config", String.class);
		assertEquals(HttpStatus.OK, config.getStatusCode());

		ResponseEntity<String> index = testRestTemplate.getForEntity("/swagger-ui/index.html", String.class);
		assertEquals(HttpStatus.OK, index.getStatusCode());
		assertNotNull(index.getHeaders().getContentType());
		assertEquals(MediaType.TEXT_HTML, index.getHeaders().getContentType());

		ResponseEntity<String> legacyUi = testRestTemplate.getForEntity("/swagger-ui.html", String.class);
		assertTrue(legacyUi.getStatusCode().is2xxSuccessful() || legacyUi.getStatusCode().is3xxRedirection());
		if (legacyUi.getStatusCode().is3xxRedirection()) {
			assertNotNull(legacyUi.getHeaders().getLocation());
			assertTrue(legacyUi.getHeaders().getLocation().getPath().endsWith("/swagger-ui/index.html"));
		}

		HttpHeaders headers = new HttpHeaders();
		headers.set("Authorization", "Bearer garbage");
		ResponseEntity<String> withInvalidToken = testRestTemplate.exchange(
				"/v3/api-docs", org.springframework.http.HttpMethod.GET, new HttpEntity<>(headers), String.class);
		assertEquals(HttpStatus.OK, withInvalidToken.getStatusCode());
	}

	@Test
	public void springfoxDocsEndpointIsNotAvailable() {
		ResponseEntity<String> legacyDocs = testRestTemplate.getForEntity("/v2/api-docs", String.class);
		assertFalse(legacyDocs.getStatusCode().is2xxSuccessful());
	}

	private JsonNode getDocs(String path) throws Exception {
		ResponseEntity<String> response = testRestTemplate.getForEntity(path, String.class);
		assertEquals(path, HttpStatus.OK, response.getStatusCode());
		return objectMapper.readTree(response.getBody());
	}

	private boolean containsSecurityRequirement(JsonNode docs, String scheme) {
		for (JsonNode requirement : docs.path("security")) {
			if (requirement.has(scheme)) {
				return true;
			}
		}
		return false;
	}

	private void assertHasTag(JsonNode paths, String path, String method, String expectedTag) {
		boolean found = false;
		for (JsonNode tag : paths.path(path).path(method).path("tags")) {
			found |= expectedTag.equals(tag.asText());
		}
		assertTrue(path + " " + method + " missing tag " + expectedTag, found);
	}
}
