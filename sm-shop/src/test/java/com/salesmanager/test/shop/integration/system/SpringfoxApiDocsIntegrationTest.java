package com.salesmanager.test.shop.integration.system;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import java.util.Iterator;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.test.context.junit4.SpringRunner;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.salesmanager.shop.application.ShopApplication;
import com.salesmanager.test.shop.common.ServicesTestSupport;

@RunWith(SpringRunner.class)
@SpringBootTest(classes = ShopApplication.class, webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
public class SpringfoxApiDocsIntegrationTest extends ServicesTestSupport {

	private final ObjectMapper objectMapper = new ObjectMapper();

	@Test
	public void springfoxApiDocsExposeExpectedPathsWithoutActuator() throws Exception {
		ResponseEntity<String> response = testRestTemplate.getForEntity("/v2/api-docs", String.class);

		assertEquals(HttpStatus.OK, response.getStatusCode());
		assertNotNull(response.getBody());
		JsonNode apiDocs = objectMapper.readTree(response.getBody());
		assertEquals("2.0", apiDocs.path("swagger").asText());
		JsonNode paths = apiDocs.path("paths");
		assertTrue("Missing /api/v1/private/login in " + paths, paths.has("/api/v1/private/login"));
		assertTrue("Missing /api/v1/products in " + paths, paths.has("/api/v1/products"));
		assertTrue("Missing /api/v1/cart in " + paths, paths.has("/api/v1/cart"));

		for (Iterator<String> names = paths.fieldNames(); names.hasNext();) {
			assertFalse(names.next().startsWith("/actuator"));
		}
	}

	@Test
	public void springfoxAndActuatorEndpointsAreAvailable() throws Exception {
		ResponseEntity<String> swaggerUi = testRestTemplate.getForEntity("/swagger-ui.html", String.class);
		assertEquals(HttpStatus.OK, swaggerUi.getStatusCode());

		ResponseEntity<String> resources = testRestTemplate.getForEntity("/swagger-resources", String.class);
		assertEquals(HttpStatus.OK, resources.getStatusCode());

		ResponseEntity<String> health = testRestTemplate.getForEntity("/actuator/health", String.class);
		assertEquals(HttpStatus.OK, health.getStatusCode());
		assertNotNull(health.getBody());
		assertEquals("UP", objectMapper.readTree(health.getBody()).path("status").asText());
	}
}
