package com.salesmanager.test.shop.integration.system;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

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
import com.salesmanager.shop.application.ShopApplication;

@SpringBootTest(classes = ShopApplication.class, webEnvironment = WebEnvironment.RANDOM_PORT)
@ExtendWith(SpringExtension.class)
class OpenApiDocumentationTest {

	@Autowired
	private TestRestTemplate testRestTemplate;

	private JsonNode apiDocs(String url) throws Exception {
		ResponseEntity<String> response = testRestTemplate.getForEntity(url, String.class);
		assertEquals(HttpStatus.OK, response.getStatusCode());
		return new ObjectMapper().readTree(response.getBody());
	}

	@Test
	void apiDocsDescribeJwtBearerSecurity() throws Exception {
		JsonNode docs = apiDocs("/v3/api-docs/default");

		assertTrue(docs.path("openapi").asText().startsWith("3."));
		assertEquals("Shopizer REST API", docs.path("info").path("title").asText());

		JsonNode jwt = docs.path("components").path("securitySchemes").path("JWT");
		assertEquals("http", jwt.path("type").asText());
		assertEquals("bearer", jwt.path("scheme").asText());
		assertEquals("Authorization", jwt.path("name").asText());
		assertTrue(docs.path("security").findValues("JWT").size() > 0);
	}

	@Test
	void apiDocsKeepOperationsSummariesAndTags() throws Exception {
		JsonNode paths = apiDocs("/v3/api-docs/default").path("paths");

		JsonNode login = paths.path("/api/v1/private/login").path("post");
		assertEquals("User authentication Api", login.path("tags").path(0).asText());
		assertEquals("authenticate", login.path("summary").asText());

		JsonNode productBySku = paths.path("/api/v2/product/{sku}").path("get");
		assertEquals("Get a product by sku", productBySku.path("summary").asText());

		assertTrue(paths.path("/api/v1/private/product/{id}").has("delete"));
		assertTrue(paths.path("/api/v2/private/product/{id}").has("put"));

		JsonNode products = paths.path("/api/v1/products").path("get");
		for (String code : new String[] { "401", "403", "500" }) {
			assertTrue(products.path("responses").has(code), "GET response " + code);
		}
	}

	@Test
	void ungroupedApiDocsMatchDefaultGroup() throws Exception {
		assertEquals(apiDocs("/v3/api-docs/default").path("paths").size(),
				apiDocs("/v3/api-docs").path("paths").size());
	}

	@Test
	void swaggerUiIsPublic() {
		ResponseEntity<String> ui = testRestTemplate.getForEntity("/swagger-ui/index.html", String.class);
		assertEquals(HttpStatus.OK, ui.getStatusCode());

		ResponseEntity<String> config = testRestTemplate.getForEntity("/v3/api-docs/swagger-config", String.class);
		assertEquals(HttpStatus.OK, config.getStatusCode());
		assertTrue(config.getBody().contains("/v3/api-docs/default"));
	}

}
