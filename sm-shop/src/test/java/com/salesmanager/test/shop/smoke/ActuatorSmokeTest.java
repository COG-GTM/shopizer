package com.salesmanager.test.shop.smoke;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

import java.util.Map;

import org.junit.jupiter.api.Test;
import org.springframework.http.ResponseEntity;

class ActuatorSmokeTest extends SmokeTestSupport {

	@Test
	void healthEndpointIsUp() {
		ResponseEntity<Map> health = testRestTemplate.getForEntity("/actuator/health", Map.class);
		assertEquals(200, health.getStatusCodeValue());
		assertNotNull(health.getBody());
		assertEquals("UP", health.getBody().get("status"));
	}
}
