package com.salesmanager.test.shop.integration.customer;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;
import static org.springframework.http.HttpStatus.NOT_FOUND;
import static org.springframework.http.HttpStatus.OK;
import static org.springframework.http.HttpStatus.UNAUTHORIZED;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.SpringBootTest.WebEnvironment;
import org.springframework.http.ResponseEntity;
import org.springframework.test.context.junit4.SpringRunner;

import com.salesmanager.core.business.constants.Constants;
import com.salesmanager.core.business.services.customer.CustomerService;
import com.salesmanager.core.model.common.CredentialsReset;
import com.salesmanager.core.model.customer.Customer;
import com.salesmanager.core.model.customer.CustomerGender;
import com.salesmanager.shop.application.ShopApplication;
import com.salesmanager.shop.model.customer.PersistableCustomer;
import com.salesmanager.shop.model.customer.address.Address;
import com.salesmanager.shop.store.security.AuthenticationRequest;
import com.salesmanager.shop.store.security.AuthenticationResponse;
import com.salesmanager.shop.utils.DateUtil;
import com.salesmanager.test.shop.common.ServicesTestSupport;

@SpringBootTest(classes = ShopApplication.class, webEnvironment = WebEnvironment.RANDOM_PORT)
@RunWith(SpringRunner.class)
public class CustomerPasswordResetTokenIntegrationTest extends ServicesTestSupport {

	private static final String STORE = Constants.DEFAULT_STORE;
	private static final String ORIGINAL_PASSWORD = "clear123";
	private static final String RESET_PASSWORD = "newSecret1";

	@Autowired
	private CustomerService customerService;

	@Test
	public void resetClearsTokenAndRejectsReuse() throws Exception {
		String email = "reset-it-" + UUID.randomUUID() + "@test.com";
		registerCustomer(email);
		Customer customer = customerService.getByNick(email, STORE);
		String token = UUID.randomUUID().toString();
		CredentialsReset reset = new CredentialsReset();
		reset.setCredentialsRequest(token);
		reset.setCredentialsRequestExpiry(DateUtil.addDaysToCurrentDate(2));
		customer.setCredentialsResetRequest(reset);
		customerService.saveOrUpdate(customer);

		assertEquals(OK, testRestTemplate.getForEntity(resetVerifyUrl(token), Void.class).getStatusCode());
		assertEquals(OK, resetPassword(token, RESET_PASSWORD).getStatusCode());
		assertNull(customerService.getByPasswordResetToken(STORE, token));
		CredentialsReset persistedReset = customerService.getByNick(email, STORE).getCredentialsResetRequest();
		assertTrue(persistedReset == null || persistedReset.getCredentialsRequest() == null);

		assertEquals(NOT_FOUND, testRestTemplate.getForEntity(resetVerifyUrl(token), Void.class).getStatusCode());
		assertEquals(NOT_FOUND, resetPassword(token, "hacked123").getStatusCode());

		ResponseEntity<AuthenticationResponse> validLogin = testRestTemplate.postForEntity("/api/v1/customer/login",
				new AuthenticationRequest(email, RESET_PASSWORD), AuthenticationResponse.class);
		assertEquals(OK, validLogin.getStatusCode());
		assertNotNull(validLogin.getBody().getToken());

		ResponseEntity<String> oldLogin = testRestTemplate.postForEntity("/api/v1/customer/login",
				new AuthenticationRequest(email, ORIGINAL_PASSWORD), String.class);
		assertEquals(UNAUTHORIZED, oldLogin.getStatusCode());
		ResponseEntity<String> invalidLogin = testRestTemplate.postForEntity("/api/v1/customer/login",
				new AuthenticationRequest(email, "hacked123"), String.class);
		assertEquals(UNAUTHORIZED, invalidLogin.getStatusCode());
	}

	@Test
	public void unknownTokenIsNotFoundForVerifyAndReset() {
		String token = UUID.randomUUID().toString();

		assertEquals(NOT_FOUND, testRestTemplate.getForEntity(resetVerifyUrl(token), Void.class).getStatusCode());
		assertEquals(NOT_FOUND, resetPassword(token, "newSecret1").getStatusCode());
	}

	private void registerCustomer(String email) {
		PersistableCustomer customer = new PersistableCustomer();
		customer.setEmailAddress(email);
		customer.setPassword(ORIGINAL_PASSWORD);
		customer.setRepeatPassword(ORIGINAL_PASSWORD);
		customer.setGender(CustomerGender.M.name());
		customer.setLanguage("en");
		customer.setStoreCode(STORE);
		Address billing = new Address();
		billing.setFirstName("Reset");
		billing.setLastName("Test");
		billing.setCountry("BE");
		customer.setBilling(billing);

		ResponseEntity<String> response = testRestTemplate.postForEntity("/api/v1/customer/register", customer,
				String.class);
		assertEquals(OK, response.getStatusCode());
	}

	private String resetVerifyUrl(String token) {
		return "/api/v1/customer/" + STORE + "/reset/" + token;
	}

	private ResponseEntity<Void> resetPassword(String token, String password) {
		Map<String, String> body = new HashMap<>();
		body.put("password", password);
		body.put("repeatPassword", password);
		return testRestTemplate.postForEntity("/api/v1/customer/" + STORE + "/password/" + token,
				body, Void.class);
	}
}
