package com.salesmanager.test.shop.uat;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.charset.StandardCharsets;
import java.util.Collections;
import java.util.Map;

import org.junit.jupiter.api.Test;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;

import com.fasterxml.jackson.databind.JsonNode;

class PlatformEndpointsUatTest extends UatTestSupport {

	@Test
	void platformEndpointsExposeHealthDocsUiAndProtectBasicAuthOptIn() {
		ResponseEntity<Map> health = testRestTemplate.getForEntity("/actuator/health", Map.class);
		assertEquals(200, health.getStatusCodeValue());
		assertNotNull(health.getBody());
		assertEquals("UP", health.getBody().get("status"));

		ResponseEntity<JsonNode> docs = testRestTemplate.getForEntity("/v2/api-docs", JsonNode.class);
		assertEquals(200, docs.getStatusCodeValue());
		assertNotNull(docs.getBody());
		assertEquals("2.0", docs.getBody().path("swagger").asText());

		ResponseEntity<String> swaggerUi = testRestTemplate.getForEntity("/swagger-ui.html", String.class);
		assertEquals(200, swaggerUi.getStatusCodeValue());

		HttpHeaders validHeaders = new HttpHeaders();
		validHeaders.setBasicAuth("admin@shopizer.com", "password");
		validHeaders.setContentType(new MediaType("text", "plain", StandardCharsets.UTF_8));
		ResponseEntity<String> authorized = testRestTemplate.postForEntity(
				"/services/private/system/optin", new HttpEntity<>("{}", validHeaders), String.class);
		assertTrue(authorized.getStatusCode().is2xxSuccessful(), authorized.toString());

		HttpHeaders invalidHeaders = new HttpHeaders();
		invalidHeaders.setBasicAuth("admin@shopizer.com", "wrong-password");
		invalidHeaders.setContentType(new MediaType("text", "plain", StandardCharsets.UTF_8));
		invalidHeaders.setAccept(Collections.singletonList(MediaType.APPLICATION_JSON));
		ResponseEntity<String> invalid = testRestTemplate.postForEntity(
				"/services/private/system/optin", new HttpEntity<>("{}", invalidHeaders), String.class);
		assertEquals(401, invalid.getStatusCodeValue());

		HttpHeaders anonymousHeaders = new HttpHeaders();
		anonymousHeaders.setContentType(new MediaType("text", "plain", StandardCharsets.UTF_8));
		anonymousHeaders.setAccept(Collections.singletonList(MediaType.APPLICATION_JSON));
		ResponseEntity<String> anonymous = testRestTemplate.postForEntity(
				"/services/private/system/optin", new HttpEntity<>("{}", anonymousHeaders), String.class);
		assertEquals(401, anonymous.getStatusCodeValue());
	}
}
