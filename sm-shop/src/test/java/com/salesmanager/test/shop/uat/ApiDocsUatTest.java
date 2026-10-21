package com.salesmanager.test.shop.uat;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import java.util.Locale;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.SpringBootTest.WebEnvironment;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.test.context.junit4.SpringRunner;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.salesmanager.shop.application.ShopApplication;
import com.salesmanager.test.shop.common.ServicesTestSupport;

@SpringBootTest(classes = ShopApplication.class, webEnvironment = WebEnvironment.RANDOM_PORT)
@RunWith(SpringRunner.class)
public class ApiDocsUatTest extends ServicesTestSupport {

	private final ObjectMapper objectMapper = new ObjectMapper();

	@Test
	public void openApiDocumentationAndSwaggerUiAreAvailable() throws Exception {
		ResponseEntity<String> docs = testRestTemplate.getForEntity("/v3/api-docs", String.class);
		assertEquals(HttpStatus.OK, docs.getStatusCode());
		JsonNode document = objectMapper.readTree(docs.getBody());
		assertTrue(document.path("openapi").asText().startsWith("3."));

		ResponseEntity<String> swaggerUi = testRestTemplate.getForEntity("/swagger-ui/index.html", String.class);
		assertEquals(HttpStatus.OK, swaggerUi.getStatusCode());
		assertTrue(swaggerUi.getBody().toLowerCase(Locale.ROOT).contains("swagger-ui"));
	}
}
