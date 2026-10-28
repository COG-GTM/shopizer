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

import com.salesmanager.core.business.constants.Constants;
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
	public void customerCanRegisterLogInAndReadProfile() {
		String email = "customer-uat-" + System.nanoTime() + "@example.com";
		PersistableCustomer customer = new PersistableCustomer();
		customer.setEmailAddress(email);
		customer.setUserName(email);
		customer.setPassword("clear123");
		customer.setRepeatPassword("clear123");
		customer.setGender("M");
		customer.setLanguage("en");
		customer.setStoreCode(Constants.DEFAULT_STORE);

		Address billing = new Address();
		billing.setFirstName("Customer");
		billing.setLastName("UAT");
		billing.setCountry("US");
		customer.setBilling(billing);

		ResponseEntity<PersistableCustomer> registrationResponse = testRestTemplate.postForEntity(
				"/api/v1/customer/register", new HttpEntity<>(customer, getHeader()), PersistableCustomer.class);
		assertEquals(HttpStatus.OK, registrationResponse.getStatusCode());

		ResponseEntity<AuthenticationResponse> loginResponse = testRestTemplate.postForEntity(
				"/api/v1/customer/login",
				new HttpEntity<>(new AuthenticationRequest(email, "clear123")), AuthenticationResponse.class);
		assertEquals(HttpStatus.OK, loginResponse.getStatusCode());
		assertNotNull(loginResponse.getBody());

		HttpHeaders headers = new HttpHeaders();
		headers.setBearerAuth(loginResponse.getBody().getToken());
		ResponseEntity<ReadableCustomer> profileResponse = testRestTemplate.exchange(
				"/api/v1/auth/customer/profile", HttpMethod.GET, new HttpEntity<>(headers), ReadableCustomer.class);
		assertEquals(HttpStatus.OK, profileResponse.getStatusCode());
		assertNotNull(profileResponse.getBody());
		assertEquals(email, profileResponse.getBody().getEmailAddress());
	}
}
