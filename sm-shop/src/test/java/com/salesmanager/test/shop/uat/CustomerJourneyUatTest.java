package com.salesmanager.test.shop.uat;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;

import java.util.UUID;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.test.context.junit4.SpringRunner;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.salesmanager.core.business.constants.Constants;
import com.salesmanager.core.model.customer.CustomerGender;
import com.salesmanager.shop.application.ShopApplication;
import com.salesmanager.shop.model.customer.PersistableCustomer;
import com.salesmanager.shop.model.customer.address.Address;
import com.salesmanager.shop.store.security.AuthenticationRequest;
import com.salesmanager.shop.store.security.AuthenticationResponse;
import com.salesmanager.test.shop.common.ServicesTestSupport;

@RunWith(SpringRunner.class)
@SpringBootTest(classes = ShopApplication.class, webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
public class CustomerJourneyUatTest extends ServicesTestSupport {

	@Test
	public void customerCanRegisterAuthenticateAndListOrders() throws Exception {
		String email = "ab534-" + UUID.randomUUID() + "@example.com";
		String password = "clear123";
		PersistableCustomer customer = new PersistableCustomer();
		customer.setEmailAddress(email);
		customer.setPassword(password);
		customer.setGender(CustomerGender.M.name());
		customer.setLanguage("en");
		customer.setStoreCode(Constants.DEFAULT_STORE);
		Address billing = new Address();
		billing.setFirstName("AB534");
		billing.setLastName("Customer");
		billing.setCountry("CA");
		customer.setBilling(billing);

		ResponseEntity<String> registration = testRestTemplate.postForEntity(
				"/api/v1/customer/register?store=DEFAULT",
				new HttpEntity<>(customer, jsonHeaders()), String.class);
		assertEquals(HttpStatus.OK, registration.getStatusCode());

		ResponseEntity<AuthenticationResponse> login = testRestTemplate.postForEntity("/api/v1/customer/login",
				new HttpEntity<>(new AuthenticationRequest(email, password), jsonHeaders()),
				AuthenticationResponse.class);
		assertEquals(HttpStatus.OK, login.getStatusCode());
		assertNotNull(login.getBody());
		assertNotNull(login.getBody().getToken());

		HttpEntity<Void> customerAuthorization = new HttpEntity<>(bearerHeaders(login.getBody().getToken()));
		ResponseEntity<String> profile = testRestTemplate.exchange(
				"/api/v1/auth/customer/profile?store=DEFAULT", HttpMethod.GET, customerAuthorization, String.class);
		assertEquals(HttpStatus.OK, profile.getStatusCode());
		JsonNode profileJson = new ObjectMapper().readTree(profile.getBody());
		assertEquals(email, profileJson.path("emailAddress").asText());

		ResponseEntity<String> anonymousProfile = testRestTemplate
				.getForEntity("/api/v1/auth/customer/profile?store=DEFAULT", String.class);
		assertEquals(HttpStatus.UNAUTHORIZED, anonymousProfile.getStatusCode());

		HttpEntity<Void> adminAuthorization = new HttpEntity<>(getHeader());
		ResponseEntity<String> adminProfile = testRestTemplate.exchange(
				"/api/v1/auth/customer/profile?store=DEFAULT", HttpMethod.GET, adminAuthorization, String.class);
		assertEquals(HttpStatus.UNAUTHORIZED, adminProfile.getStatusCode());

		ResponseEntity<String> orders = testRestTemplate.exchange("/api/v1/auth/orders?store=DEFAULT",
				HttpMethod.GET, customerAuthorization, String.class);
		assertEquals(HttpStatus.OK, orders.getStatusCode());
	}

	private static HttpHeaders jsonHeaders() {
		HttpHeaders headers = new HttpHeaders();
		headers.setContentType(MediaType.APPLICATION_JSON);
		return headers;
	}

	private static HttpHeaders bearerHeaders(String token) {
		HttpHeaders headers = jsonHeaders();
		headers.setBearerAuth(token);
		return headers;
	}
}
