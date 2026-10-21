package com.salesmanager.test.shop.smoke;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.charset.StandardCharsets;
import java.util.Collections;

import org.junit.jupiter.api.Test;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;

class ServicesBasicAuthSmokeTest extends SmokeTestSupport {

	@Test
	void servicesPrivateRequiresAndAcceptsBasicAuthentication() {
		HttpHeaders correct = new HttpHeaders();
		correct.setBasicAuth("admin@shopizer.com", "password");
		correct.setContentType(new MediaType("text", "plain", StandardCharsets.UTF_8));
		ResponseEntity<String> authorized = testRestTemplate.postForEntity(
				"/services/private/system/optin", new HttpEntity<>("{}", correct), String.class);
		assertTrue(authorized.getStatusCode().is2xxSuccessful(), authorized.toString());

		HttpHeaders anonymousHeaders = new HttpHeaders();
		anonymousHeaders.setAccept(Collections.singletonList(MediaType.APPLICATION_JSON));
		ResponseEntity<String> anonymous = testRestTemplate.postForEntity(
				"/services/private/system/optin", new HttpEntity<>("{}", anonymousHeaders), String.class);
		assertEquals(401, anonymous.getStatusCodeValue());

		HttpHeaders incorrect = new HttpHeaders();
		incorrect.setBasicAuth("admin@shopizer.com", "wrong-password");
		incorrect.setContentType(new MediaType("text", "plain", StandardCharsets.UTF_8));
		incorrect.setAccept(Collections.singletonList(MediaType.APPLICATION_JSON));
		ResponseEntity<String> rejected = testRestTemplate.postForEntity(
				"/services/private/system/optin", new HttpEntity<>("{}", incorrect), String.class);
		assertEquals(401, rejected.getStatusCodeValue());

		ResponseEntity<String> publicStore = testRestTemplate.getForEntity("/services/public/DEFAULT", String.class);
		// Observed legacy status.
		assertEquals(202, publicStore.getStatusCodeValue());
	}
}
