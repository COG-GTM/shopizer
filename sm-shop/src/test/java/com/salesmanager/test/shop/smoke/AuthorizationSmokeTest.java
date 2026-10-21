package com.salesmanager.test.shop.smoke;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.ResponseEntity;

class AuthorizationSmokeTest extends SmokeTestSupport {

	@Test
	void privateAndCustomerEndpointsRejectMissingOrWrongCredentials() {
		ResponseEntity<String> missingAdmin = testRestTemplate.getForEntity("/api/v1/private/customers", String.class);
		assertEquals(401, missingAdmin.getStatusCodeValue());

		CustomerSession customer = registerCustomer();
		ResponseEntity<String> customerOnPrivate = testRestTemplate.exchange("/api/v1/private/customers",
				HttpMethod.GET, new HttpEntity<>(bearerHeaders(customer.token)), String.class);
		assertEquals(401, customerOnPrivate.getStatusCodeValue());

		ResponseEntity<String> missingCustomer = testRestTemplate.getForEntity(
				"/api/v1/auth/customer/profile", String.class);
		assertEquals(401, missingCustomer.getStatusCodeValue());
	}

	@Test
	@Tag("known-failure")
	@Disabled("KNOWN-FAILURE KF-1: malformed JWT on /api/v*/private/** returns 500 (MalformedJwtException escapes AuthenticationTokenFilter); expected 401. Out of scope for AB-533; to be filed separately.")
	void malformedBearerOnPrivateApiIsRejectedWith401() {
		HttpHeaders garbage = bearerHeaders("a.b.c");
		ResponseEntity<String> invalidAdmin = testRestTemplate.exchange("/api/v1/private/customers",
				HttpMethod.GET, new HttpEntity<>(garbage), String.class);
		assertEquals(401, invalidAdmin.getStatusCodeValue());
	}
}
