package com.salesmanager.test.shop.smoke;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

import org.junit.jupiter.api.Test;
import org.springframework.http.HttpEntity;
import org.springframework.http.ResponseEntity;

import com.salesmanager.shop.store.security.AuthenticationRequest;
import com.salesmanager.shop.store.security.AuthenticationResponse;

class AdminAuthSmokeTest extends SmokeTestSupport {

	@Test
	void adminCanLoginAndReadPrivateCustomersButBadPasswordIsRejected() {
		String token = adminToken();
		ResponseEntity<String> customers = testRestTemplate.exchange("/api/v1/private/customers",
				org.springframework.http.HttpMethod.GET, new HttpEntity<>(bearerHeaders(token)), String.class);
		assertEquals(200, customers.getStatusCodeValue());
		assertNotNull(customers.getBody());

		ResponseEntity<String> badLogin = testRestTemplate.postForEntity("/api/v1/private/login",
				new HttpEntity<>(new AuthenticationRequest("admin@shopizer.com", "incorrect"), jsonHeaders()),
				String.class);
		assertEquals(401, badLogin.getStatusCodeValue());
	}
}
