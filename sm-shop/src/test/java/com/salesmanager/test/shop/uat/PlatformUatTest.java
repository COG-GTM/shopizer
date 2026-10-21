package com.salesmanager.test.shop.uat;

import static org.junit.Assert.assertEquals;

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
public class PlatformUatTest extends ServicesTestSupport {

	@Test
	public void healthAndDocumentationEndpointsMatchPlatformConfiguration() throws Exception {
		ResponseEntity<String> health = testRestTemplate.getForEntity("/actuator/health", String.class);
		assertEquals(HttpStatus.OK, health.getStatusCode());
		JsonNode healthJson = new ObjectMapper().readTree(health.getBody());
		assertEquals("UP", healthJson.path("status").asText());

		ResponseEntity<String> swagger2 = testRestTemplate.getForEntity("/v2/api-docs", String.class);
		assertEquals(HttpStatus.OK, swagger2.getStatusCode());

		ResponseEntity<String> swagger3 = testRestTemplate.getForEntity("/v3/api-docs", String.class);
		assertEquals(HttpStatus.NOT_FOUND, swagger3.getStatusCode());
	}
}
