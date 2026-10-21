package com.salesmanager.test.shop.uat;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;
import static org.springframework.http.HttpStatus.OK;

import java.util.UUID;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.ResponseEntity;
import org.springframework.test.context.junit4.SpringRunner;

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
	public void customerCanRegisterLoginAndListOrders() {
		String email = "uat-" + UUID.randomUUID() + "@example.com";
		String password = "clear123";
		PersistableCustomer customer = new PersistableCustomer();
		customer.setEmailAddress(email);
		customer.setPassword(password);
		customer.setGender(CustomerGender.M.name());
		customer.setLanguage("en");
		customer.setStoreCode(Constants.DEFAULT_STORE);
		Address billing = new Address();
		billing.setFirstName("UAT");
		billing.setLastName("Customer");
		billing.setCountry("BE");
		customer.setBilling(billing);

		HttpHeaders customerHeaders = null;
		try {
			ResponseEntity<String> registration = testRestTemplate.postForEntity(
					"/api/v1/customer/register", new HttpEntity<>(customer), String.class);
			assertEquals(OK, registration.getStatusCode());

			ResponseEntity<AuthenticationResponse> login = testRestTemplate.postForEntity(
					"/api/v1/customer/login",
					new HttpEntity<>(new AuthenticationRequest(email, password)),
					AuthenticationResponse.class);
			assertEquals(OK, login.getStatusCode());
			assertNotNull(login.getBody());
			assertNotNull(login.getBody().getToken());

			customerHeaders = new HttpHeaders();
			customerHeaders.setBearerAuth(login.getBody().getToken());
			ResponseEntity<String> orders = testRestTemplate.exchange(
					"/api/v1/auth/orders?store=DEFAULT", HttpMethod.GET,
					new HttpEntity<>(customerHeaders), String.class);
			assertEquals(OK, orders.getStatusCode());

			ResponseEntity<String> anonymousOrders = testRestTemplate.exchange(
					"/api/v1/auth/orders?store=DEFAULT", HttpMethod.GET,
					new HttpEntity<>(new HttpHeaders()), String.class);
			assertTrue(anonymousOrders.getStatusCode().value() == 401
					|| anonymousOrders.getStatusCode().value() == 403);
		} finally {
			if (customerHeaders != null) {
				testRestTemplate.exchange("/api/v1/auth/customer/?store=DEFAULT",
						HttpMethod.DELETE, new HttpEntity<>(customerHeaders), String.class);
			}
		}
	}
}
