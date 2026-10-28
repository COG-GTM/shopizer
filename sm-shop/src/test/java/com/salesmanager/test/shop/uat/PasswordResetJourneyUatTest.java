package com.salesmanager.test.shop.uat;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;
import static org.springframework.http.HttpStatus.BAD_REQUEST;
import static org.springframework.http.HttpStatus.NOT_FOUND;
import static org.springframework.http.HttpStatus.OK;

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
import com.salesmanager.core.business.services.user.UserService;
import com.salesmanager.core.model.customer.Customer;
import com.salesmanager.core.model.customer.CustomerGender;
import com.salesmanager.core.model.user.User;
import com.salesmanager.shop.application.ShopApplication;
import com.salesmanager.shop.model.customer.PersistableCustomer;
import com.salesmanager.shop.model.customer.address.Address;
import com.salesmanager.shop.store.security.AuthenticationRequest;
import com.salesmanager.shop.store.security.AuthenticationResponse;
import com.salesmanager.test.shop.common.ServicesTestSupport;

@SpringBootTest(classes = ShopApplication.class, webEnvironment = WebEnvironment.RANDOM_PORT)
@RunWith(SpringRunner.class)
public class PasswordResetJourneyUatTest extends ServicesTestSupport {

	private static final String STORE = Constants.DEFAULT_STORE;

	@Autowired
	private UserService userService;

	@Autowired
	private CustomerService customerService;

	@Test
	public void adminResetRequestJourney() throws Exception {
		Map<String, String> request = new HashMap<>();
		request.put("username", "admin@shopizer.com");
		request.put("returnUrl", "http://localhost/admin");
		ResponseEntity<String> requestResponse = testRestTemplate.postForEntity(
				"/api/v1/user/password/reset/request?store=" + STORE, request, String.class);
		assertTrue(requestResponse.getStatusCode() == OK || requestResponse.getStatusCode() == BAD_REQUEST);

		User user = userService.getByUserName("admin@shopizer.com", STORE);
		assertNotNull("Reset request must persist a token for the test inbox", user.getCredentialsResetRequest());
		// The database token stands in for the email inbox in this UAT flow.
		String token = user.getCredentialsResetRequest().getCredentialsRequest();
		assertNotNull(token);
		assertEquals(OK, testRestTemplate.getForEntity("/api/v1/user/" + STORE + "/reset/" + token, Void.class)
				.getStatusCode());
		assertEquals(OK, changePassword("/api/v1/user/" + STORE + "/password/" + token, "password"));
		assertEquals(NOT_FOUND,
				testRestTemplate.getForEntity("/api/v1/user/" + STORE + "/reset/" + token, Void.class).getStatusCode());
		assertEquals(NOT_FOUND, changePassword("/api/v1/user/" + STORE + "/password/" + token, "hacked123"));

		ResponseEntity<AuthenticationResponse> login = testRestTemplate.postForEntity("/api/v1/private/login",
				new AuthenticationRequest("admin@shopizer.com", "password"), AuthenticationResponse.class);
		assertEquals(OK, login.getStatusCode());
		assertNotNull(login.getBody().getToken());
	}

	@Test
	public void customerResetRequestJourney() throws Exception {
		String email = "reset-uat-" + UUID.randomUUID() + "@test.com";
		registerCustomer(email);
		Map<String, String> request = new HashMap<>();
		request.put("username", email);
		request.put("returnUrl", "http://localhost/admin");
		ResponseEntity<String> requestResponse = testRestTemplate.postForEntity(
				"/api/v1/customer/password/reset/request?store=" + STORE, request, String.class);
		assertTrue(requestResponse.getStatusCode() == OK || requestResponse.getStatusCode() == BAD_REQUEST);

		Customer customer = customerService.getByNick(email, STORE);
		assertNotNull("Reset request must persist a token for the test inbox", customer.getCredentialsResetRequest());
		// The database token stands in for the email inbox in this UAT flow.
		String token = customer.getCredentialsResetRequest().getCredentialsRequest();
		assertNotNull(token);
		assertEquals(OK,
				testRestTemplate.getForEntity("/api/v1/customer/" + STORE + "/reset/" + token, Void.class)
						.getStatusCode());
		assertEquals(OK,
				changePassword("/api/v1/customer/" + STORE + "/password/" + token, "newSecret1"));
		assertEquals(NOT_FOUND,
				testRestTemplate.getForEntity("/api/v1/customer/" + STORE + "/reset/" + token, Void.class)
						.getStatusCode());
		assertEquals(NOT_FOUND,
				changePassword("/api/v1/customer/" + STORE + "/password/" + token, "hacked123"));

		ResponseEntity<AuthenticationResponse> login = testRestTemplate.postForEntity("/api/v1/customer/login",
				new AuthenticationRequest(email, "newSecret1"), AuthenticationResponse.class);
		assertEquals(OK, login.getStatusCode());
		assertNotNull(login.getBody().getToken());
	}

	private void registerCustomer(String email) {
		PersistableCustomer customer = new PersistableCustomer();
		customer.setEmailAddress(email);
		customer.setPassword("clear123");
		customer.setRepeatPassword("clear123");
		customer.setGender(CustomerGender.M.name());
		customer.setLanguage("en");
		customer.setStoreCode(STORE);
		Address billing = new Address();
		billing.setFirstName("Reset");
		billing.setLastName("UAT");
		billing.setCountry("BE");
		customer.setBilling(billing);

		assertEquals(OK, testRestTemplate.postForEntity("/api/v1/customer/register", customer, String.class)
				.getStatusCode());
	}

	private org.springframework.http.HttpStatus changePassword(String path, String password) {
		Map<String, String> body = new HashMap<>();
		body.put("password", password);
		body.put("repeatPassword", password);
		return testRestTemplate.postForEntity(path, body, Void.class).getStatusCode();
	}
}
