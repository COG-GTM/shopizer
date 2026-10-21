package com.salesmanager.test.shop.smoke;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;

import org.junit.jupiter.api.Test;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpMethod;
import org.springframework.http.ResponseEntity;

class AuthorizationSmokeTest extends SmokeTestSupport {

	@Test
	void privateAndCustomerEndpointsRejectMissingOrWrongCredentials() {
		ResponseEntity<String> missingAdmin = testRestTemplate.getForEntity("/api/v1/private/customers", String.class);
		assertEquals(401, missingAdmin.getStatusCodeValue());

		org.springframework.http.HttpHeaders garbage = bearerHeaders("not-a-jwt");
		ResponseEntity<String> invalidAdmin = testRestTemplate.exchange("/api/v1/private/customers",
				HttpMethod.GET, new HttpEntity<>(garbage), String.class);
		assertEquals(401, invalidAdmin.getStatusCodeValue());

		CustomerSession customer = registerCustomer();
		ResponseEntity<String> customerOnPrivate = testRestTemplate.exchange("/api/v1/private/customers",
				HttpMethod.GET, new HttpEntity<>(bearerHeaders(customer.token)), String.class);
		Set<Integer> rejectedStatuses = new HashSet<>(Arrays.asList(401, 403));
		assertTrue(rejectedStatuses.contains(customerOnPrivate.getStatusCodeValue()),
				"Expected 401 or 403, got " + customerOnPrivate.getStatusCodeValue());

		ResponseEntity<String> missingCustomer = testRestTemplate.getForEntity(
				"/api/v1/auth/customer/profile", String.class);
		assertEquals(401, missingCustomer.getStatusCodeValue());
	}
}
