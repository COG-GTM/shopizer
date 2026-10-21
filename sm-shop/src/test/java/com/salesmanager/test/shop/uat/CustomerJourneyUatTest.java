package com.salesmanager.test.shop.uat;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpMethod;
import org.springframework.http.ResponseEntity;

import com.salesmanager.shop.model.customer.ReadableCustomer;
import com.salesmanager.shop.store.security.AuthenticationRequest;

class CustomerJourneyUatTest extends UatTestSupport {

	@Test
	void customerCanRegisterLoginAndReadProfileButCannotReadAdminCustomers() {
		UatCustomer customer = registerAndLoginCustomer();
		ResponseEntity<ReadableCustomer> profile = testRestTemplate.exchange("/api/v1/auth/customer/profile",
				HttpMethod.GET, new HttpEntity<>(bearerHeaders(customer.token)), ReadableCustomer.class);
		assertEquals(200, profile.getStatusCodeValue(), profile.toString());
		assertNotNull(profile.getBody());
		assertEquals(customer.email, profile.getBody().getEmailAddress());

		ResponseEntity<String> wrongPassword = testRestTemplate.postForEntity("/api/v1/customer/login",
				new HttpEntity<>(new AuthenticationRequest(customer.email, "incorrect-password"), jsonHeaders()),
				String.class);
		assertEquals(401, wrongPassword.getStatusCodeValue(), wrongPassword.getBody());

		ResponseEntity<String> adminCustomers = testRestTemplate.exchange("/api/v1/private/customers",
				HttpMethod.GET, new HttpEntity<>(bearerHeaders(customer.token)), String.class);
		assertTrue(adminCustomers.getStatusCodeValue() == 401 || adminCustomers.getStatusCodeValue() == 403,
				"Customer token status was " + adminCustomers.getStatusCodeValue());
	}
}
