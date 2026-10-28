package com.salesmanager.test.shop.uat;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.test.context.junit.jupiter.SpringExtension;

import com.salesmanager.core.business.constants.Constants;
import com.salesmanager.core.model.customer.CustomerGender;
import com.salesmanager.shop.application.ShopApplication;
import com.salesmanager.shop.model.customer.PersistableCustomer;
import com.salesmanager.shop.model.customer.ReadableCustomer;
import com.salesmanager.shop.model.customer.address.Address;
import com.salesmanager.shop.store.security.AuthenticationRequest;
import com.salesmanager.shop.store.security.AuthenticationResponse;
import com.salesmanager.test.shop.common.ServicesTestSupport;

@SpringBootTest(classes = ShopApplication.class, webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ExtendWith(SpringExtension.class)
public class AuthJourneyUatTest extends ServicesTestSupport {

	@Test
	public void adminAndCustomerAuthenticationJourneysWork() {
		ResponseEntity<AuthenticationResponse> adminLogin = testRestTemplate.postForEntity(
				"/api/v1/private/login", new HttpEntity<>(new AuthenticationRequest("admin@shopizer.com", "password")),
				AuthenticationResponse.class);
		assertEquals(HttpStatus.OK, adminLogin.getStatusCode());
		assertNotNull(adminLogin.getBody().getToken());

		HttpHeaders adminHeaders = bearerHeaders(adminLogin.getBody().getToken());
		ResponseEntity<String> privateCustomers = testRestTemplate.exchange(
				"/api/v1/private/customers?store=DEFAULT", HttpMethod.GET,
				new HttpEntity<>(adminHeaders), String.class);
		assertEquals(HttpStatus.OK, privateCustomers.getStatusCode());

		ResponseEntity<String> noToken = testRestTemplate.exchange(
				"/api/v1/private/customers?store=DEFAULT", HttpMethod.GET,
				HttpEntity.EMPTY, String.class);
		assertForbiddenOrUnauthorized(noToken.getStatusCode());

		String email = "uat-" + UUID.randomUUID().toString().replace("-", "") + "@example.test";
		PersistableCustomer customer = new PersistableCustomer();
		customer.setEmailAddress(email);
		customer.setPassword("clear123");
		customer.setGender(CustomerGender.M.name());
		customer.setLanguage("en");
		customer.setStoreCode(Constants.DEFAULT_STORE);
		Address billing = new Address();
		billing.setFirstName("UAT");
		billing.setLastName("Customer");
		billing.setCountry("US");
		customer.setBilling(billing);
		ResponseEntity<AuthenticationResponse> registration = testRestTemplate.postForEntity(
				"/api/v1/customer/register?store=DEFAULT",
				new HttpEntity<>(customer, adminHeaders), AuthenticationResponse.class);
		assertEquals(HttpStatus.OK, registration.getStatusCode());
		assertNotNull(registration.getBody().getToken());

		ResponseEntity<AuthenticationResponse> customerLogin = testRestTemplate.postForEntity(
				"/api/v1/customer/login", new HttpEntity<>(new AuthenticationRequest(email, "clear123")),
				AuthenticationResponse.class);
		assertEquals(HttpStatus.OK, customerLogin.getStatusCode());
		assertNotNull(customerLogin.getBody().getToken());

		ResponseEntity<ReadableCustomer> profile = testRestTemplate.exchange(
				"/api/v1/auth/customer/profile?store=DEFAULT", HttpMethod.GET,
				new HttpEntity<>(bearerHeaders(customerLogin.getBody().getToken())), ReadableCustomer.class);
		assertEquals(HttpStatus.OK, profile.getStatusCode());
	}

	private static HttpHeaders bearerHeaders(String token) {
		HttpHeaders headers = new HttpHeaders();
		headers.setBearerAuth(token);
		return headers;
	}

	private static void assertForbiddenOrUnauthorized(HttpStatus status) {
		assertTrue(status == HttpStatus.UNAUTHORIZED || status == HttpStatus.FORBIDDEN);
	}
}
