package com.salesmanager.test.shop.uat;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.http.HttpMethod.GET;
import static org.springframework.http.HttpStatus.OK;

import org.junit.jupiter.api.Test;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseEntity;

import com.salesmanager.shop.store.security.AuthenticationRequest;
import com.salesmanager.shop.store.security.AuthenticationResponse;
import com.salesmanager.test.shop.common.ServicesTestSupport;

class AdminLoginJourneyUatTest extends ServicesTestSupport {

	@Test
	void adminCanLoginAndUsePrivateApiWhileAnonymousRequestsAreRejected() {
		ResponseEntity<AuthenticationResponse> login = testRestTemplate.postForEntity("/api/v1/private/login",
				new HttpEntity<>(new AuthenticationRequest("admin@shopizer.com", "password")),
				AuthenticationResponse.class);
		assertEquals(OK, login.getStatusCode());
		assertNotNull(login.getBody());

		HttpHeaders authorized = new HttpHeaders();
		authorized.setBearerAuth(login.getBody().getToken());
		ResponseEntity<String> users = testRestTemplate.exchange("/api/v1/private/users", GET,
				new HttpEntity<>(authorized), String.class);
		assertEquals(OK, users.getStatusCode());

		ResponseEntity<String> anonymous = testRestTemplate.exchange("/api/v1/private/users", GET,
				HttpEntity.EMPTY, String.class);
		assertUnauthorized(anonymous);
	}

	private static void assertUnauthorized(ResponseEntity<?> response) {
		assertTrue(response.getStatusCode().value() == 401 || response.getStatusCode().value() == 403,
				"Expected 401 or 403, got " + response.getStatusCode().value());
	}
}
