package com.salesmanager.test.shop.integration.security;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import java.util.Arrays;
import java.util.List;
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

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.salesmanager.core.model.customer.CustomerGender;
import com.salesmanager.shop.application.ShopApplication;
import com.salesmanager.shop.model.customer.PersistableCustomer;
import com.salesmanager.shop.model.customer.address.Address;
import com.salesmanager.shop.store.security.AuthenticationRequest;
import com.salesmanager.shop.store.security.AuthenticationResponse;
import com.salesmanager.test.shop.common.ServicesTestSupport;

@SpringBootTest(classes = ShopApplication.class, webEnvironment = WebEnvironment.RANDOM_PORT)
@RunWith(SpringRunner.class)
public class SecurityApiAccessIntegrationTest extends ServicesTestSupport {

	private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();
	private static final String[] SECURITY_PATHS = {
			"/api/v1/private/security/groups",
			"/api/v1/private/security/permissions",
			"/api/v1/private/security/ADMIN/permissions",
			"/api/v1/sec/private/groups",
			"/api/v1/sec/private/permissions",
			"/api/v1/sec/private/ADMIN/permissions" };
	private static final String ADMIN_PATH = "/api/v1/private/store/DEFAULT";

	@Test
	public void anonymousRequestsToAllSecurityPathsAreUnauthorized() {
		for (String path : SECURITY_PATHS) {
			ResponseEntity<String> response = testRestTemplate.getForEntity(path, String.class);
			assertEquals(path, HttpStatus.UNAUTHORIZED, response.getStatusCode());
		}
	}

	@Test
	public void badBearerResponsesMatchExistingAdminEndpoint() {
		HttpHeaders headers = new HttpHeaders();
		headers.setBearerAuth("garbage");
		HttpEntity<Void> request = new HttpEntity<>(headers);
		HttpStatus existingAdminStatus = testRestTemplate
				.exchange("/api/v1/private/customers", HttpMethod.GET, request, String.class).getStatusCode();

		for (String path : SECURITY_PATHS) {
			HttpStatus status = testRestTemplate.exchange(path, HttpMethod.GET, request, String.class).getStatusCode();
			System.out.println("Invalid bearer " + path + " -> " + status
					+ "; existing admin endpoint -> " + existingAdminStatus);
			assertEquals("Bad bearer handling differs from the existing admin endpoint for " + path,
					existingAdminStatus, status);
		}
	}

	@Test
	public void adminCanListAllSecurityResourcesWithStableJsonFields() throws Exception {
		HttpEntity<Void> request = new HttpEntity<>(getHeader());
		String[] responseBodies = new String[SECURITY_PATHS.length];

		for (int i = 0; i < SECURITY_PATHS.length; i++) {
			ResponseEntity<String> response = testRestTemplate.exchange(
					SECURITY_PATHS[i], HttpMethod.GET, request, String.class);
			assertEquals(SECURITY_PATHS[i], HttpStatus.OK, response.getStatusCode());
			assertNotNull(response.getBody());
			responseBodies[i] = response.getBody();
			JsonNode firstResult = OBJECT_MAPPER.readTree(response.getBody()).get(0);
			assertNotNull(firstResult);
			if (SECURITY_PATHS[i].endsWith("/groups")) {
				assertEquals(Arrays.asList("id", "name", "type"), fieldNames(firstResult));
			} else {
				assertEquals(Arrays.asList("id", "name"), fieldNames(firstResult));
			}
		}

		assertFalse(OBJECT_MAPPER.readTree(responseBodies[0]).isEmpty());
		assertEquals(responseBodies[0], responseBodies[3]);
		assertEquals(responseBodies[1], responseBodies[4]);
		assertEquals(responseBodies[2], responseBodies[5]);
	}

	@Test
	public void optionsPreflightIsNotBlockedByAdminSecurity() {
		for (String path : Arrays.asList(
				"/api/v1/sec/private/groups", "/api/v1/private/security/groups")) {
			HttpHeaders headers = new HttpHeaders();
			headers.add(HttpHeaders.ORIGIN, "https://shop.example.test");
			headers.add(HttpHeaders.ACCESS_CONTROL_REQUEST_METHOD, "GET");
			ResponseEntity<String> response = testRestTemplate.exchange(
					path, HttpMethod.OPTIONS, new HttpEntity<>(headers), String.class);
			assertFalse(path + " returned " + response.getStatusCode(),
					response.getStatusCode() == HttpStatus.UNAUTHORIZED
							|| response.getStatusCode() == HttpStatus.FORBIDDEN);
		}
	}

	@Test
	public void customerTokenCannotAccessSecurityApiButCanUseCustomerApi() {
		String customerToken = registerAndLoginCustomer();
		HttpHeaders customerHeaders = new HttpHeaders();
		customerHeaders.setBearerAuth(customerToken);
		HttpEntity<Void> customerRequest = new HttpEntity<>(customerHeaders);

		ResponseEntity<String> profile = testRestTemplate.exchange(
				"/api/v1/auth/customer/profile", HttpMethod.GET, customerRequest, String.class);
		assertEquals(HttpStatus.OK, profile.getStatusCode());

		HttpStatus existingAdminStatus = testRestTemplate.exchange(
				"/api/v1/private/customers", HttpMethod.GET, customerRequest, String.class).getStatusCode();
		HttpStatus securityStatus = testRestTemplate.exchange(
				"/api/v1/sec/private/groups", HttpMethod.GET, customerRequest, String.class).getStatusCode();
		System.out.println("Customer bearer /api/v1/sec/private/groups -> " + securityStatus
				+ "; existing admin endpoint -> " + existingAdminStatus);
		assertEquals(existingAdminStatus, securityStatus);
		assertTrue(securityStatus == HttpStatus.UNAUTHORIZED || securityStatus == HttpStatus.FORBIDDEN);
	}

	@Test
	public void authenticationMatchersAndPublicEndpointsRemainReachable() {
		ResponseEntity<AuthenticationResponse> login = testRestTemplate.postForEntity(
				"/api/v1/private/login",
				new HttpEntity<>(new AuthenticationRequest("admin@shopizer.com", "password")),
				AuthenticationResponse.class);
		assertEquals(HttpStatus.OK, login.getStatusCode());
		assertNotNull(login.getBody());
		assertNotNull(login.getBody().getToken());

		ResponseEntity<String> refresh = testRestTemplate.getForEntity("/api/v1/private/refresh", String.class);
		assertFalse("Refresh returned " + refresh.getStatusCode(),
				refresh.getStatusCode() == HttpStatus.UNAUTHORIZED);

		ResponseEntity<String> anonymousAdmin = testRestTemplate.getForEntity(ADMIN_PATH, String.class);
		assertEquals(HttpStatus.UNAUTHORIZED, anonymousAdmin.getStatusCode());
		HttpHeaders adminHeaders = new HttpHeaders();
		adminHeaders.setBearerAuth(login.getBody().getToken());
		ResponseEntity<String> authorizedAdmin = testRestTemplate.exchange(
				ADMIN_PATH, HttpMethod.GET, new HttpEntity<>(adminHeaders), String.class);
		assertEquals(HttpStatus.OK, authorizedAdmin.getStatusCode());

		ResponseEntity<String> anonymousCustomer = testRestTemplate.getForEntity(
				"/api/v1/auth/customer/profile", String.class);
		assertEquals(HttpStatus.UNAUTHORIZED, anonymousCustomer.getStatusCode());
		ResponseEntity<String> publicStore = testRestTemplate.getForEntity(
				"/api/v1/store/DEFAULT", String.class);
		assertEquals(HttpStatus.OK, publicStore.getStatusCode());
	}

	private String registerAndLoginCustomer() {
		String email = "security-" + UUID.randomUUID() + "@example.test";
		PersistableCustomer customer = new PersistableCustomer();
		customer.setEmailAddress(email);
		customer.setPassword("clear123");
		customer.setGender(CustomerGender.M.name());
		customer.setLanguage("en");
		customer.setStoreCode("DEFAULT");
		Address billing = new Address();
		billing.setFirstName("Security");
		billing.setLastName("Customer");
		billing.setCountry("BE");
		customer.setBilling(billing);

		ResponseEntity<PersistableCustomer> registration = testRestTemplate.postForEntity(
				"/api/v1/customer/register", new HttpEntity<>(customer, getHeader()), PersistableCustomer.class);
		assertEquals(HttpStatus.OK, registration.getStatusCode());

		ResponseEntity<AuthenticationResponse> login = testRestTemplate.postForEntity(
				"/api/v1/customer/login",
				new HttpEntity<>(new AuthenticationRequest(email, "clear123")),
				AuthenticationResponse.class);
		assertEquals(HttpStatus.OK, login.getStatusCode());
		assertNotNull(login.getBody());
		return login.getBody().getToken();
	}

	private List<String> fieldNames(JsonNode node) {
		java.util.ArrayList<String> names = new java.util.ArrayList<>();
		node.fieldNames().forEachRemaining(names::add);
		java.util.Collections.sort(names);
		return names;
	}
}
