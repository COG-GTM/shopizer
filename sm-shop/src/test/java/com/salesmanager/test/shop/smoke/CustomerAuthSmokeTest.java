package com.salesmanager.test.shop.smoke;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

import org.junit.jupiter.api.Test;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpMethod;
import org.springframework.http.ResponseEntity;

import com.salesmanager.shop.model.customer.ReadableCustomer;

class CustomerAuthSmokeTest extends SmokeTestSupport {

	@Test
	void customerCanRegisterLoginAndReadTheirProfile() {
		CustomerSession customer = registerCustomer();
		ResponseEntity<ReadableCustomer> profile = testRestTemplate.exchange("/api/v1/auth/customer/profile",
				HttpMethod.GET, new HttpEntity<>(bearerHeaders(customer.token)), ReadableCustomer.class);

		assertEquals(200, profile.getStatusCodeValue());
		assertNotNull(profile.getBody());
		assertEquals(customer.email, profile.getBody().getEmailAddress());
	}
}
