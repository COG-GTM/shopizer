package com.salesmanager.test.shop.integration.system;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Iterator;
import java.util.List;
import java.util.Locale;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.SpringBootTest.WebEnvironment;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.test.context.junit4.SpringRunner;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.salesmanager.shop.application.ShopApplication;
import com.salesmanager.shop.model.user.ReadableUser;
import com.salesmanager.test.shop.common.ServicesTestSupport;

@SpringBootTest(classes = ShopApplication.class, webEnvironment = WebEnvironment.RANDOM_PORT)
@RunWith(SpringRunner.class)
public class OpenApiDocumentationIntegrationTest extends ServicesTestSupport {

	private final ObjectMapper objectMapper = new ObjectMapper();

	@Test
	public void apiDocsUseOpenApiThreeAndDocumentJwtSecurity() throws Exception {
		JsonNode document = getDocument("/v3/api-docs");

		assertTrue(document.path("openapi").asText().startsWith("3."));
		assertEquals("Shopizer REST API", document.path("info").path("title").asText());
		JsonNode jwt = document.path("components").path("securitySchemes").path("JWT");
		assertEquals("http", jwt.path("type").asText());
		assertEquals("bearer", jwt.path("scheme").asText());
		assertEquals("JWT", jwt.path("bearerFormat").asText());
		assertTrue(document.path("security").toString().contains("\"JWT\""));
	}

	@Test
	public void invalidBearerTokenDoesNotBlockDocumentation() {
		HttpHeaders headers = new HttpHeaders();
		headers.setBearerAuth("not-a-jwt");

		ResponseEntity<String> response = testRestTemplate.exchange("/v3/api-docs", HttpMethod.GET,
				new HttpEntity<>(headers), String.class);

		assertEquals(HttpStatus.OK, response.getStatusCode());
	}

	@Test
	public void defaultGroupDocumentsMainEndpointsAndFlattensProductCriteria() throws Exception {
		JsonNode document = getDocument("/v3/api-docs/default");
		JsonNode paths = document.path("paths");

		assertTrue(paths.path("/api/v1/private/login").has("post"));
		assertTrue(paths.path("/api/v1/products").has("get"));
		assertTrue(paths.path("/api/v2/products").has("get"));

		JsonNode productParameters = paths.path("/api/v2/products").path("get").path("parameters");
		List<String> parameterNames = new ArrayList<>();
		for (JsonNode parameter : productParameters) {
			parameterNames.add(parameter.path("name").asText());
			assertFalse("Unexpected nested ProductCriteria parameter: " + parameter.path("name").asText(),
					Arrays.asList("searchCriterias", "criteria").contains(parameter.path("name").asText()));
			if (Arrays.asList("productName", "pageSize").contains(parameter.path("name").asText())) {
				assertEquals("query", parameter.path("in").asText());
			}
		}
		assertTrue("Expected flattened ProductCriteria query fields, got " + parameterNames,
				parameterNames.contains("productName") && parameterNames.contains("pageSize"));

		List<String> languageParameterOperations = new ArrayList<>();
		Iterator<String> pathNames = paths.fieldNames();
		while (pathNames.hasNext()) {
			String pathName = pathNames.next();
			JsonNode path = paths.path(pathName);
			for (String method : Arrays.asList("get", "post", "put", "patch", "delete", "options", "head")) {
				for (JsonNode parameter : path.path(method).path("parameters")) {
					String name = parameter.path("name").asText();
					assertFalse("MerchantStore leaked into API docs as a parameter", name.equals("merchantStore"));
					if (name.equals("language")) {
						languageParameterOperations.add(method + " " + pathName);
					}
				}
			}
		}
		assertEquals("The only language parameter should be ProductCriteria's inherited filter",
				Arrays.asList("get /api/v2/products"), languageParameterOperations);
	}

	@Test
	public void getOperationsReceiveGlobalResponsesInBothDocuments() throws Exception {
		for (String documentPath : Arrays.asList("/v3/api-docs", "/v3/api-docs/default")) {
			JsonNode responses = getDocument(documentPath).path("paths")
					.path("/api/v1/products").path("get").path("responses");
			assertTrue(documentPath + " lacks response 401", responses.has("401"));
			assertTrue(documentPath + " lacks response 403", responses.has("403"));
			assertTrue(documentPath + " lacks response 500", responses.has("500"));
		}
	}

	@Test
	public void loginOperationHasItsControllerTag() throws Exception {
		JsonNode tags = getDocument("/v3/api-docs/default").path("paths")
				.path("/api/v1/private/login").path("post").path("tags");

		assertTrue(tags.toString(), tags.isArray() && tags.size() > 0);
		assertTrue(tags.toString(), tags.toString().contains("User authentication Api"));
	}

	@Test
	public void swaggerConfigListsTheDefaultGroup() throws Exception {
		JsonNode swaggerConfig = getDocument("/v3/api-docs/swagger-config");

		boolean hasDefaultGroup = false;
		for (JsonNode url : swaggerConfig.path("urls")) {
			hasDefaultGroup |= "/v3/api-docs/default".equals(url.path("url").asText());
		}
		assertTrue(swaggerConfig.toString(), hasDefaultGroup);
	}

	@Test
	public void swaggerUiIsAvailableAndTheSwaggerfoxEndpointIsGone() {
		ResponseEntity<String> ui = testRestTemplate.getForEntity("/swagger-ui/index.html", String.class);
		assertEquals(HttpStatus.OK, ui.getStatusCode());
		assertTrue(ui.getBody().toLowerCase(Locale.ROOT).contains("swagger-ui"));

		ResponseEntity<String> legacyUi = testRestTemplate.getForEntity("/swagger-ui.html", String.class);
		if (legacyUi.getStatusCode() == HttpStatus.FOUND
				|| legacyUi.getStatusCode() == HttpStatus.MOVED_PERMANENTLY) {
			assertTrue(legacyUi.getHeaders().getLocation().toString().endsWith("/swagger-ui/index.html"));
		} else {
			assertEquals(HttpStatus.OK, legacyUi.getStatusCode());
			assertTrue(legacyUi.getBody().toLowerCase(Locale.ROOT).contains("swagger-ui"));
		}

		assertEquals(HttpStatus.NOT_FOUND,
				testRestTemplate.getForEntity("/v2/api-docs", String.class).getStatusCode());
	}

	@Test
	public void adminProfileStillRequiresAValidJwt() {
		ResponseEntity<String> anonymous = testRestTemplate.getForEntity("/api/v1/private/user/profile", String.class);
		assertEquals(HttpStatus.UNAUTHORIZED, anonymous.getStatusCode());

		ResponseEntity<ReadableUser> authenticated = testRestTemplate.exchange("/api/v1/private/user/profile",
				HttpMethod.GET, new HttpEntity<>(getHeader()), ReadableUser.class);
		assertEquals(HttpStatus.OK, authenticated.getStatusCode());
		assertNotNull(authenticated.getBody());
		assertEquals("admin@shopizer.com", authenticated.getBody().getUserName());
	}

	@Test
	public void defaultGroupPreservesTheSpringfoxOperationInventory() throws Exception {
		JsonNode document = getDocument("/v3/api-docs/default");
		JsonNode paths = document.path("paths");
		InputStream baseline = getClass().getClassLoader()
				.getResourceAsStream("openapi/springfox-baseline-operations.txt");
		assertNotNull("Springfox baseline operation inventory is missing", baseline);

		List<String> missing = new ArrayList<>();
		try (BufferedReader reader = new BufferedReader(
				new InputStreamReader(baseline, StandardCharsets.UTF_8))) {
			String line;
			while ((line = reader.readLine()) != null) {
				if (line.trim().isEmpty()) {
					continue;
				}
				String[] operation = line.split(" ", 2);
				String expectedMethod = operation[0];
				String path = operation[1];
				if (expectedMethod.equals("POST")
						&& (path.equals("/api/v1/private/product/{id}")
								|| path.equals("/api/v1/auth/product/{id}"))) {
					expectedMethod = "PATCH";
				}
				if (!paths.path(path).has(expectedMethod.toLowerCase(Locale.ROOT))) {
					missing.add(expectedMethod + " " + path);
				}
			}
		}

		assertTrue("Missing operations from the Springfox baseline:\n" + String.join("\n", missing),
				missing.isEmpty());
	}

	private JsonNode getDocument(String path) throws Exception {
		ResponseEntity<String> response = testRestTemplate.getForEntity(path, String.class);
		assertEquals(path, HttpStatus.OK, response.getStatusCode());
		assertNotNull(response.getBody());
		return objectMapper.readTree(response.getBody());
	}
}
