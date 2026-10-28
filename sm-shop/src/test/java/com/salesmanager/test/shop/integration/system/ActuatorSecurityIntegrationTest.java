package com.salesmanager.test.shop.integration.system;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import java.util.Iterator;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.SpringBootTest.WebEnvironment;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.test.context.junit4.SpringRunner;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.salesmanager.shop.application.ShopApplication;
import com.salesmanager.test.shop.common.ServicesTestSupport;

/**
 * Actuator surface with the shipped configuration (exposure = health,info).
 */
@SpringBootTest(classes = ShopApplication.class, webEnvironment = WebEnvironment.RANDOM_PORT)
@RunWith(SpringRunner.class)
public class ActuatorSecurityIntegrationTest extends ServicesTestSupport {

	static final String[] SENSITIVE_ENDPOINTS = { "/actuator/heapdump", "/actuator/env", "/actuator/env/jwt.secret",
			"/actuator/beans", "/actuator/mappings", "/actuator/threaddump", "/actuator/loggers",
			"/actuator/configprops", "/actuator/metrics", "/actuator/conditions", "/actuator/scheduledtasks",
			"/actuator/caches" };

	private final ObjectMapper mapper = new ObjectMapper();

	@Test
	public void healthIsPublicAndUp() throws Exception {
		ResponseEntity<String> response = testRestTemplate.getForEntity("/actuator/health", String.class);
		assertEquals(HttpStatus.OK, response.getStatusCode());
		JsonNode body = mapper.readTree(response.getBody());
		assertEquals("UP", body.path("status").asText());
	}

	@Test
	public void healthShowsComponentStatusWithoutDetails() throws Exception {
		ResponseEntity<String> response = testRestTemplate.getForEntity("/actuator/health", String.class);
		JsonNode body = mapper.readTree(response.getBody());
		JsonNode components = body.get("components");
		assertNotNull("components should still be shown: " + response.getBody(), components);
		assertTrue(components.size() > 0);
		Iterator<JsonNode> it = components.elements();
		while (it.hasNext()) {
			JsonNode component = it.next();
			assertNotNull(component.get("status"));
			assertFalse("health component details leaked: " + response.getBody(), component.has("details"));
		}
	}

	@Test
	public void healthSubPathsArePublic() {
		assertEquals(HttpStatus.OK, testRestTemplate.getForEntity("/actuator/health/ping", String.class).getStatusCode());
		assertEquals(HttpStatus.OK, testRestTemplate.getForEntity("/actuator/health/db", String.class).getStatusCode());
	}

	@Test
	public void infoIsPublic() {
		assertEquals(HttpStatus.OK, testRestTemplate.getForEntity("/actuator/info", String.class).getStatusCode());
	}

	@Test
	public void sensitiveEndpointsAreNotReachableAnonymously() {
		for (String endpoint : SENSITIVE_ENDPOINTS) {
			ResponseEntity<String> response = testRestTemplate.getForEntity(endpoint, String.class);
			assertFalse(endpoint + " returned " + response.getStatusCode(), response.getStatusCode().is2xxSuccessful());
		}
	}

	@Test
	public void sensitiveEndpointsAreNotReachableWithAdminJwt() {
		HttpEntity<String> entity = new HttpEntity<>(getHeader());
		for (String endpoint : SENSITIVE_ENDPOINTS) {
			ResponseEntity<String> response = testRestTemplate.exchange(endpoint, HttpMethod.GET, entity, String.class);
			assertFalse(endpoint + " returned " + response.getStatusCode() + " for admin",
					response.getStatusCode().is2xxSuccessful());
		}
	}

	@Test
	public void actuatorLinksIndexIsDenied() {
		ResponseEntity<String> response = testRestTemplate.getForEntity("/actuator", String.class);
		assertEquals(HttpStatus.FORBIDDEN, response.getStatusCode());
	}

	@Test
	public void loggerLevelCannotBeChangedAnonymously() {
		HttpHeaders headers = new HttpHeaders();
		headers.setContentType(MediaType.APPLICATION_JSON);
		ResponseEntity<String> response = testRestTemplate.postForEntity("/actuator/loggers/ROOT",
				new HttpEntity<>("{\"configuredLevel\":\"OFF\"}", headers), String.class);
		assertFalse("POST /actuator/loggers/ROOT returned " + response.getStatusCode(),
				response.getStatusCode().is2xxSuccessful());
	}

}
