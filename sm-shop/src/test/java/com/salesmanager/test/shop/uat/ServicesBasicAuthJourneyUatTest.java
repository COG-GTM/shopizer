package com.salesmanager.test.shop.uat;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import java.util.UUID;

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
import com.salesmanager.core.model.customer.CustomerGender;
import com.salesmanager.shop.application.ShopApplication;
import com.salesmanager.shop.model.customer.PersistableCustomer;
import com.salesmanager.shop.model.customer.address.Address;
import com.salesmanager.test.shop.common.ServicesTestSupport;

@RunWith(SpringRunner.class)
@SpringBootTest(classes = ShopApplication.class, webEnvironment = WebEnvironment.RANDOM_PORT)
public class ServicesBasicAuthJourneyUatTest extends ServicesTestSupport {

	@Test
	public void servicesBasicAuthAcceptsAdminAndRejectsBadOrCustomerCredentials() {
		String customerEmail = "uat-services-" + UUID.randomUUID() + "@test.com";
		registerCustomer(customerEmail);

		ResponseEntity<String> admin = request("admin@shopizer.com", "password");
		assertFalse(HttpStatus.UNAUTHORIZED.equals(admin.getStatusCode()));

		ResponseEntity<String> badPassword = request("admin@shopizer.com", "wrong");
		assertEquals(HttpStatus.UNAUTHORIZED, badPassword.getStatusCode());
		assertEquals("Basic realm=\"rest-customer-realm\"",
				badPassword.getHeaders().getFirst(HttpHeaders.WWW_AUTHENTICATE));

		ResponseEntity<String> customer = request(customerEmail, "clear123");
		assertEquals(HttpStatus.UNAUTHORIZED, customer.getStatusCode());
	}

	private void registerCustomer(String email) {
		PersistableCustomer customer = new PersistableCustomer();
		customer.setEmailAddress(email);
		customer.setPassword("clear123");
		customer.setGender(CustomerGender.M.name());
		customer.setLanguage("en");
		customer.setStoreCode(Constants.DEFAULT_STORE);
		Address billing = new Address();
		billing.setFirstName("Uat");
		billing.setLastName("Services");
		billing.setCountry("BE");
		customer.setBilling(billing);

		ResponseEntity<String> registration = testRestTemplate.postForEntity("/api/v1/customer/register",
				new HttpEntity<>(customer), String.class);
		assertTrue(registration.toString(), registration.getStatusCode().is2xxSuccessful());
	}

	private ResponseEntity<String> request(String username, String password) {
		HttpHeaders headers = new HttpHeaders();
		headers.setBasicAuth(username, password);
		return testRestTemplate.exchange("/services/private/system/x", HttpMethod.GET,
				new HttpEntity<>(headers), String.class);
	}
}
