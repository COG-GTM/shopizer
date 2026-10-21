package com.salesmanager.test.shop.uat;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.salesmanager.test.shop.common.ServicesTestSupport;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

class PlatformUatTest extends ServicesTestSupport {

	@Test
	void serviceSecurityHealthAndApiDocumentationAreAvailable() {
		String servicePath = "/services/private/system/optin";
		HttpHeaders noCredentialsHeaders = new HttpHeaders();
		noCredentialsHeaders.setContentType(MediaType.APPLICATION_JSON);
		HttpEntity<String> unauthenticatedRequest = new HttpEntity<>("{}", noCredentialsHeaders);
		ResponseEntity<String> noCredentials = testRestTemplate.exchange(
				servicePath, HttpMethod.POST, unauthenticatedRequest, String.class);

		HttpHeaders badCredentials = new HttpHeaders();
		badCredentials.setBasicAuth("admin@shopizer.com", "incorrect-password");
		badCredentials.setContentType(MediaType.APPLICATION_JSON);
		ResponseEntity<String> badAuth = testRestTemplate.exchange(
				servicePath, HttpMethod.POST, new HttpEntity<>("{}", badCredentials), String.class);

		HttpHeaders adminCredentials = new HttpHeaders();
		adminCredentials.setBasicAuth("admin@shopizer.com", "password");
		adminCredentials.setContentType(MediaType.APPLICATION_JSON);
		ResponseEntity<String> validAuth = testRestTemplate.exchange(
				servicePath, HttpMethod.POST, new HttpEntity<>("{}", adminCredentials), String.class);
		ResponseEntity<String> health = testRestTemplate.getForEntity(
				"/actuator/health", String.class);
		ResponseEntity<String> apiDocs = testRestTemplate.getForEntity(
				"/v2/api-docs", String.class);

		assertEquals(HttpStatus.FOUND, noCredentials.getStatusCode());
		assertEquals(HttpStatus.UNAUTHORIZED, badAuth.getStatusCode());
		assertNotEquals(HttpStatus.UNAUTHORIZED, validAuth.getStatusCode());
		assertEquals(HttpStatus.OK, health.getStatusCode());
		assertTrue(health.getBody().contains("\"status\":\"UP\""));
		assertEquals(HttpStatus.OK, apiDocs.getStatusCode());
		assertTrue(apiDocs.getBody().contains("\"swagger\":\"2.0\""));
	}
}
