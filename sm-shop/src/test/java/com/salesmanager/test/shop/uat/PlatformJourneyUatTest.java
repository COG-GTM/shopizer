package com.salesmanager.test.shop.uat;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.http.HttpMethod.GET;
import static org.springframework.http.HttpStatus.OK;

import org.junit.jupiter.api.Test;
import org.springframework.http.HttpEntity;
import org.springframework.http.ResponseEntity;

import com.salesmanager.test.shop.common.ServicesTestSupport;

class PlatformJourneyUatTest extends ServicesTestSupport {

	@Test
	void healthAndApiDocumentationAreAvailable() {
		ResponseEntity<String> health = testRestTemplate.exchange(
				"/actuator/health", GET, HttpEntity.EMPTY, String.class);
		assertTrue(health.getStatusCode().is2xxSuccessful());
		assertNotNull(health.getBody());
		assertTrue(health.getBody().contains("\"status\":\"UP\""));

		ResponseEntity<String> docs = testRestTemplate.exchange(
				"/v2/api-docs", GET, HttpEntity.EMPTY, String.class);
		assertEquals(OK, docs.getStatusCode());
		assertNotNull(docs.getBody());
		String body = docs.getBody().toLowerCase();
		assertTrue(body.contains("swagger") || body.contains("paths"));
	}
}
