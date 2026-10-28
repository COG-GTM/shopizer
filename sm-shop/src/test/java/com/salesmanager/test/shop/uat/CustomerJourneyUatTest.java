package com.salesmanager.test.shop.uat;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.SpringBootTest.WebEnvironment;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.test.context.junit4.SpringRunner;

import com.salesmanager.shop.application.ShopApplication;
import com.salesmanager.shop.model.customer.PersistableCustomer;
import com.salesmanager.shop.model.customer.ReadableCustomer;
import com.salesmanager.shop.model.customer.address.Address;
import com.salesmanager.shop.store.security.AuthenticationRequest;
import com.salesmanager.shop.store.security.AuthenticationResponse;
import com.salesmanager.test.shop.common.ServicesTestSupport;

@SpringBootTest(classes = ShopApplication.class, webEnvironment = WebEnvironment.RANDOM_PORT)
@RunWith(SpringRunner.class)
public class CustomerJourneyUatTest extends ServicesTestSupport {

	@Test
	public void customerCanRegisterLogInAndReadAuthenticatedProfile() {
		String email = "uat-customer-" + System.nanoTime() + "@example.test";
		String password = "UatPassword123!";
		PersistableCustomer customer = customer(email, password);

		ResponseEntity<AuthenticationResponse> registration = testRestTemplate.postForEntity(
				"/api/v1/customer/register?store=DEFAULT", new HttpEntity<>(customer), AuthenticationResponse.class);
		assertEquals(HttpStatus.OK, registration.getStatusCode());
		assertNotNull(registration.getBody());
		assertNotNull(registration.getBody().getToken());

		ResponseEntity<AuthenticationResponse> login = testRestTemplate.postForEntity("/api/v1/customer/login",
				new HttpEntity<>(new AuthenticationRequest(email, password)), AuthenticationResponse.class);
		assertEquals(HttpStatus.OK, login.getStatusCode());
		assertNotNull(login.getBody());
		assertNotNull(login.getBody().getToken());

		HttpHeaders headers = new HttpHeaders();
		headers.setBearerAuth(login.getBody().getToken());
		ResponseEntity<ReadableCustomer> profile = testRestTemplate.exchange(
				"/api/v1/auth/customer/profile?store=DEFAULT", HttpMethod.GET, new HttpEntity<>(headers),
				ReadableCustomer.class);
		assertEquals(HttpStatus.OK, profile.getStatusCode());
		assertEquals(email, profile.getBody().getEmailAddress());
	}

	private static PersistableCustomer customer(String email, String password) {
		PersistableCustomer customer = new PersistableCustomer();
		customer.setEmailAddress(email);
		customer.setUserName(email);
		customer.setPassword(password);
		customer.setRepeatPassword(password);
		customer.setFirstName("UAT");
		customer.setLastName("Customer");
		customer.setLanguage("en");

		Address billing = new Address();
		billing.setFirstName("UAT");
		billing.setLastName("Customer");
		billing.setAddress("1 Test Street");
		billing.setCity("Test City");
		billing.setCountry("US");
		billing.setStateProvince("CA");
		billing.setZone("CA");
		customer.setBilling(billing);
		return customer;
	}
}
