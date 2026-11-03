package com.salesmanager.test.shop.integration.payment;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

import javax.inject.Inject;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.SpringBootTest.WebEnvironment;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.test.context.junit4.SpringRunner;

import com.fasterxml.jackson.databind.JsonNode;
import com.salesmanager.core.business.constants.Constants;
import com.salesmanager.core.model.customer.CustomerGender;
import com.salesmanager.shop.application.ShopApplication;
import com.salesmanager.shop.model.customer.PersistableCustomer;
import com.salesmanager.shop.model.customer.address.Address;
import com.salesmanager.shop.model.security.PersistableGroup;
import com.salesmanager.shop.model.system.IntegrationModuleConfiguration;
import com.salesmanager.shop.model.user.PersistableUser;
import com.salesmanager.shop.model.user.ReadableUser;
import com.salesmanager.shop.store.security.AuthenticationRequest;
import com.salesmanager.shop.store.security.AuthenticationResponse;
import com.salesmanager.test.shop.common.ServicesTestSupport;

@SpringBootTest(classes = ShopApplication.class, webEnvironment = WebEnvironment.RANDOM_PORT)
@RunWith(SpringRunner.class)
public class PaymentApiGroupAuthorizationIntegrationTest extends ServicesTestSupport {

	private static final String PAYMENT_PATH = "/api/v1/private/modules/payment";
	private static final String MODULE_PATH = PAYMENT_PATH + "/moneyorder";
	private static final String STAFF_PASSWORD = "Password1";

	@Inject
	private TestRestTemplate restTemplate;

	@Test
	public void adminOrderCannotReadOrConfigurePayments() throws Exception {
		assertDeniedForAllPaymentEndpoints(createStaff("ADMIN_ORDER"), HttpStatus.UNAUTHORIZED);
	}

	@Test
	public void missingAndInvalidTokensCannotReadOrConfigurePayments() {
		HttpHeaders noToken = new HttpHeaders();
		assertDeniedForAllPaymentEndpoints(noToken, HttpStatus.UNAUTHORIZED);

		HttpHeaders invalidToken = new HttpHeaders();
		invalidToken.setBearerAuth("not-a-valid-jwt");
		assertPaymentEndpointsAreNotSuccessful(invalidToken);
	}

	@Test
	public void customerTokenCannotReadPaymentModules() {
		String email = uniqueEmail("customer");
		PersistableCustomer customer = new PersistableCustomer();
		customer.setEmailAddress(email);
		customer.setPassword(STAFF_PASSWORD);
		customer.setGender(CustomerGender.M.name());
		customer.setLanguage("en");
		customer.setStoreCode(Constants.DEFAULT_STORE);
		Address billing = new Address();
		billing.setFirstName("Payment");
		billing.setLastName("Customer");
		billing.setCountry("BE");
		customer.setBilling(billing);

		ResponseEntity<String> registration = restTemplate.postForEntity("/api/v1/customer/register",
				new HttpEntity<>(customer, getHeader()), String.class);
		assertTrue(registration.getStatusCode().is2xxSuccessful());

		ResponseEntity<AuthenticationResponse> login = restTemplate.postForEntity("/api/v1/customer/login",
				new HttpEntity<>(new AuthenticationRequest(email, STAFF_PASSWORD)), AuthenticationResponse.class);
		assertEquals(HttpStatus.OK, login.getStatusCode());
		assertNotNull(login.getBody());
		HttpHeaders customerHeaders = new HttpHeaders();
		customerHeaders.setBearerAuth(login.getBody().getToken());

		ResponseEntity<String> response = restTemplate.exchange(PAYMENT_PATH, HttpMethod.GET,
				new HttpEntity<>(customerHeaders), String.class);
		System.out.println("Customer JWT payment-list status: " + response.getStatusCodeValue());
		assertTrue(response.getStatusCode() == HttpStatus.UNAUTHORIZED
				|| response.getStatusCode() == HttpStatus.FORBIDDEN);
	}

	@Test
	public void deniedCatalogueWriteDoesNotChangeMoneyorderConfiguration() throws Exception {
		HttpHeaders adminHeaders = getHeader();
		IntegrationModuleConfiguration before = restTemplate.exchange(MODULE_PATH, HttpMethod.GET,
				new HttpEntity<>(adminHeaders), IntegrationModuleConfiguration.class).getBody();
		assertNotNull(before);

		HttpHeaders catalogueHeaders = createStaff("ADMIN_CATALOGUE");
		IntegrationModuleConfiguration maliciousConfiguration = new IntegrationModuleConfiguration();
		maliciousConfiguration.setCode("moneyorder");
		maliciousConfiguration.setActive(false);
		Map<String, String> keys = new HashMap<>();
		keys.put("attacker", "x");
		maliciousConfiguration.setIntegrationKeys(keys);
		ResponseEntity<String> denied = restTemplate.exchange(PAYMENT_PATH, HttpMethod.POST,
				new HttpEntity<>(maliciousConfiguration, catalogueHeaders), String.class);
		assertEquals(HttpStatus.UNAUTHORIZED, denied.getStatusCode());

		IntegrationModuleConfiguration after = restTemplate.exchange(MODULE_PATH, HttpMethod.GET,
				new HttpEntity<>(adminHeaders), IntegrationModuleConfiguration.class).getBody();
		assertNotNull(after);
		assertEquals(before.isActive(), after.isActive());
		assertEquals(before.getIntegrationKeys(), after.getIntegrationKeys());
	}

	@Test
	public void adminPaymentContractContainsModuleFields() {
		HttpHeaders headers = getHeader();
		ResponseEntity<JsonNode> list = restTemplate.exchange(PAYMENT_PATH, HttpMethod.GET,
				new HttpEntity<>(headers), JsonNode.class);
		assertEquals(HttpStatus.OK, list.getStatusCode());
		assertTrue(list.getBody().isArray());
		assertTrue(list.getBody().size() > 0);
		assertTrue(list.getBody().get(0).has("code"));

		ResponseEntity<JsonNode> module = restTemplate.exchange(MODULE_PATH, HttpMethod.GET,
				new HttpEntity<>(headers), JsonNode.class);
		assertEquals(HttpStatus.OK, module.getStatusCode());
		assertEquals("moneyorder", module.getBody().path("code").asText());
		assertTrue(module.getBody().has("integrationKeys"));
	}

	private void assertDeniedForAllPaymentEndpoints(HttpHeaders headers, HttpStatus expectedStatus) {
		HttpEntity<String> request = new HttpEntity<>(headers);
		ResponseEntity<String> list = restTemplate.exchange(PAYMENT_PATH, HttpMethod.GET, request, String.class);
		ResponseEntity<String> module = restTemplate.exchange(MODULE_PATH, HttpMethod.GET, request, String.class);
		IntegrationModuleConfiguration configuration = new IntegrationModuleConfiguration();
		configuration.setCode("moneyorder");
		configuration.setActive(true);
		ResponseEntity<String> post = restTemplate.exchange(PAYMENT_PATH, HttpMethod.POST,
				new HttpEntity<>(configuration, headers), String.class);
		System.out.println("Payment denial statuses list/single/post: " + list.getStatusCodeValue() + "/"
				+ module.getStatusCodeValue() + "/" + post.getStatusCodeValue());
		assertEquals(expectedStatus, list.getStatusCode());
		assertEquals(expectedStatus, module.getStatusCode());
		assertEquals(expectedStatus, post.getStatusCode());
	}

	private void assertPaymentEndpointsAreNotSuccessful(HttpHeaders headers) {
		HttpEntity<String> request = new HttpEntity<>(headers);
		ResponseEntity<String> list = restTemplate.exchange(PAYMENT_PATH, HttpMethod.GET, request, String.class);
		ResponseEntity<String> module = restTemplate.exchange(MODULE_PATH, HttpMethod.GET, request, String.class);
		IntegrationModuleConfiguration configuration = new IntegrationModuleConfiguration();
		configuration.setCode("moneyorder");
		configuration.setActive(true);
		ResponseEntity<String> post = restTemplate.exchange(PAYMENT_PATH, HttpMethod.POST,
				new HttpEntity<>(configuration, headers), String.class);
		System.out.println("Invalid-token payment statuses list/single/post: " + list.getStatusCodeValue() + "/"
				+ module.getStatusCodeValue() + "/" + post.getStatusCodeValue());
		assertTrue(!list.getStatusCode().is2xxSuccessful());
		assertTrue(!module.getStatusCode().is2xxSuccessful());
		assertTrue(!post.getStatusCode().is2xxSuccessful());
	}

	private HttpHeaders createStaff(String groupName) throws Exception {
		String email = uniqueEmail(groupName.toLowerCase());
		PersistableUser user = new PersistableUser();
		user.setDefaultLanguage("en");
		user.setEmailAddress(email);
		user.setFirstName("Payment");
		user.setLastName("Staff");
		user.setUserName(email);
		user.setPassword(STAFF_PASSWORD);
		user.setRepeatPassword(STAFF_PASSWORD);
		PersistableGroup group = new PersistableGroup();
		group.setName(groupName);
		user.getGroups().add(group);

		ResponseEntity<ReadableUser> response = restTemplate.exchange("/api/v1/private/user/", HttpMethod.POST,
				new HttpEntity<>(user, getHeader()), ReadableUser.class);
		assertEquals(HttpStatus.OK, response.getStatusCode());
		return getHeader(email, STAFF_PASSWORD);
	}

	private String uniqueEmail(String prefix) {
		return prefix + "." + UUID.randomUUID() + "@payment-test.example";
	}
}
