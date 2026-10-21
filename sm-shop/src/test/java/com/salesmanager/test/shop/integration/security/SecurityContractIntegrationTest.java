package com.salesmanager.test.shop.integration.security;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import java.util.UUID;

import org.junit.Before;
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
import com.salesmanager.shop.store.security.AuthenticationRequest;
import com.salesmanager.shop.store.security.AuthenticationResponse;
import com.salesmanager.test.shop.common.ServicesTestSupport;

@RunWith(SpringRunner.class)
@SpringBootTest(classes = ShopApplication.class, webEnvironment = WebEnvironment.RANDOM_PORT)
public class SecurityContractIntegrationTest extends ServicesTestSupport {

	private String adminToken;
	private String customerToken;

	@Before
	public void registerCustomerAndGetTokens() {
		adminToken = getHeader().getFirst(HttpHeaders.AUTHORIZATION).substring("Bearer ".length());

		String email = "security-contract-" + UUID.randomUUID() + "@test.com";
		PersistableCustomer customer = new PersistableCustomer();
		customer.setEmailAddress(email);
		customer.setPassword("clear123");
		customer.setGender(CustomerGender.M.name());
		customer.setLanguage("en");
		customer.setStoreCode(Constants.DEFAULT_STORE);
		Address billing = new Address();
		billing.setFirstName("Security");
		billing.setLastName("Contract");
		billing.setCountry("BE");
		customer.setBilling(billing);

		ResponseEntity<String> registration = testRestTemplate.postForEntity("/api/v1/customer/register",
				new HttpEntity<>(customer), String.class);
		assertTrue(registration.toString(), registration.getStatusCode().is2xxSuccessful());

		ResponseEntity<AuthenticationResponse> login = testRestTemplate.postForEntity("/api/v1/customer/login",
				new HttpEntity<>(new AuthenticationRequest(email, "clear123")), AuthenticationResponse.class);
		assertEquals(HttpStatus.OK, login.getStatusCode());
		assertNotNull(login.getBody());
		customerToken = login.getBody().getToken();
	}

	@Test
	public void reportsBaseStatusForTamperedBearerTokens() {
		ResponseEntity<String> adminResponse = exchange(HttpMethod.GET, "/api/v1/private/user/profile",
				bearerHeaders("garbage.token.value"));
		ResponseEntity<String> customerResponse = exchange(HttpMethod.GET, "/api/v1/auth/customer/profile",
				bearerHeaders("garbage.token.value"));

		assertEquals(HttpStatus.INTERNAL_SERVER_ERROR, adminResponse.getStatusCode());
		assertEquals(HttpStatus.INTERNAL_SERVER_ERROR, customerResponse.getStatusCode());
	}

	@Test
	public void adminJwtCanUseVersionTwoPrivateApi() {
		ResponseEntity<String> response = exchange(HttpMethod.GET, "/api/v2/private/x", bearerHeaders(adminToken));

		assertFalse(HttpStatus.UNAUTHORIZED.equals(response.getStatusCode()));
	}

	@Test
	public void servicesPostAndShopLogoutAreNotBlockedByCsrf() {
		ResponseEntity<String> servicesPost = exchange(HttpMethod.POST, "/services/private/system/x",
				basicHeaders("admin@shopizer.com", "password"));
		assertFalse(HttpStatus.FORBIDDEN.equals(servicesPost.getStatusCode()));

		ResponseEntity<String> shopLogout = exchange(HttpMethod.POST, "/shop/customer/logout", new HttpHeaders());
		assertFalse(HttpStatus.UNAUTHORIZED.equals(shopLogout.getStatusCode()));
		assertFalse(HttpStatus.FORBIDDEN.equals(shopLogout.getStatusCode()));
	}

	@Test
	public void adminAndCustomerJwtsCanReadTheirOrders() {
		ResponseEntity<String> adminOrders = exchange(HttpMethod.GET, "/api/v1/private/orders",
				bearerHeaders(adminToken));
		ResponseEntity<String> customerOrders = exchange(HttpMethod.GET, "/api/v1/auth/orders",
				bearerHeaders(customerToken));

		assertEquals(HttpStatus.OK, adminOrders.getStatusCode());
		assertEquals(HttpStatus.OK, customerOrders.getStatusCode());
	}

	@Test
	public void securityHeadersArePresentOnlyOnASecuredChainResponse() {
		ResponseEntity<String> secured = exchange(HttpMethod.GET, "/api/v1/private/orders",
				bearerHeaders(adminToken));
		ResponseEntity<String> publicResponse = testRestTemplate.getForEntity(
				"/api/v1/products?store=DEFAULT", String.class);

		assertTrue(secured.getStatusCode().is2xxSuccessful());
		assertNotNull(secured.getHeaders().getFirst("X-Frame-Options"));
		assertNotNull(secured.getHeaders().getFirst(HttpHeaders.CACHE_CONTROL));
		assertEquals(HttpStatus.OK, publicResponse.getStatusCode());
		assertTrue(publicResponse.getHeaders().getFirst("X-Frame-Options") == null);
		assertTrue(publicResponse.getHeaders().getFirst(HttpHeaders.CACHE_CONTROL) == null);
	}

	private ResponseEntity<String> exchange(HttpMethod method, String path, HttpHeaders headers) {
		return testRestTemplate.exchange(path, method, new HttpEntity<>(headers), String.class);
	}

	private HttpHeaders bearerHeaders(String token) {
		HttpHeaders headers = new HttpHeaders();
		headers.setBearerAuth(token);
		return headers;
	}

	private HttpHeaders basicHeaders(String username, String password) {
		HttpHeaders headers = new HttpHeaders();
		headers.setBasicAuth(username, password);
		return headers;
	}
}
