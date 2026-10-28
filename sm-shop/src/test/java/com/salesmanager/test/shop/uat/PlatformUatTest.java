package com.salesmanager.test.shop.uat;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

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
public class PlatformUatTest extends ServicesTestSupport {

	private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

	@Test
	public void platformEndpointsEnforceAuthReportHealthAndExposePasswordApiDocs() throws Exception {
		ResponseEntity<String> noAuth = testRestTemplate.getForEntity("/services/private/ping", String.class);
		assertEquals(HttpStatus.FOUND, noAuth.getStatusCode());

		ResponseEntity<String> wrongBasicAuth = testRestTemplate.withBasicAuth("admin@shopizer.com", "wrong-password")
				.getForEntity("/services/private/ping", String.class);
		assertEquals(HttpStatus.UNAUTHORIZED, wrongBasicAuth.getStatusCode());

		ResponseEntity<String> validBasicAuth = testRestTemplate.withBasicAuth("admin@shopizer.com", "password")
				.getForEntity("/services/private/ping", String.class);
		assertTrue(validBasicAuth.getStatusCode() != HttpStatus.UNAUTHORIZED);

		ResponseEntity<String> healthResponse = testRestTemplate.getForEntity("/actuator/health", String.class);
		assertEquals(HttpStatus.OK, healthResponse.getStatusCode());
		JsonNode health = OBJECT_MAPPER.readTree(healthResponse.getBody());
		assertEquals("UP", health.path("status").asText());

		ResponseEntity<String> apiDocs = testRestTemplate.getForEntity("/v2/api-docs", String.class);
		assertEquals(HttpStatus.OK, apiDocs.getStatusCode());
		assertNotNull(apiDocs.getBody());
		assertTrue(apiDocs.getBody().contains("/api/v1/private/user/{id}/password"));
	}
}
