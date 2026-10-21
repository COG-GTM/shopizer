package com.salesmanager.test.shop.integration.security;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Arrays;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.ProviderManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.authentication.dao.DaoAuthenticationProvider;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.web.DefaultSecurityFilterChain;
import org.springframework.security.web.FilterChainProxy;
import org.springframework.security.web.util.matcher.AntPathRequestMatcher;

import com.salesmanager.core.business.constants.Constants;
import com.salesmanager.core.model.customer.CustomerGender;
import com.salesmanager.shop.model.customer.PersistableCustomer;
import com.salesmanager.shop.model.customer.address.Address;
import com.salesmanager.shop.store.security.AuthenticationRequest;
import com.salesmanager.shop.store.security.AuthenticationResponse;
import com.salesmanager.shop.store.security.admin.JWTAdminAuthenticationProvider;
import com.salesmanager.test.shop.common.ServicesTestSupport;

@TestInstance(TestInstance.Lifecycle.PER_CLASS)
public class SecurityFilterChainIntegrationTest extends ServicesTestSupport {

	private static final String ADMIN_USERNAME = "admin@shopizer.com";
	private static final String ADMIN_PASSWORD = "password";
	private static final String CUSTOMER_PASSWORD = "clear123";

	@Autowired
	private FilterChainProxy filterChainProxy;

	@Autowired
	@Qualifier("customerAuthenticationManager")
	private AuthenticationManager customerAuthenticationManager;

	@Autowired
	@Qualifier("servicesAuthenticationManager")
	private AuthenticationManager servicesAuthenticationManager;

	@Autowired
	@Qualifier("jwtAdminAuthenticationManager")
	private AuthenticationManager jwtAdminAuthenticationManager;

	@Autowired
	@Qualifier("jwtCustomerAuthenticationManager")
	private AuthenticationManager jwtCustomerAuthenticationManager;

	private String customerUsername;
	private String customerToken;
	private String adminToken;

	@BeforeAll
	void registerCustomerAndGetTokens() {
		customerUsername = "security-" + UUID.randomUUID() + "@test.com";
		PersistableCustomer customer = new PersistableCustomer();
		customer.setEmailAddress(customerUsername);
		customer.setPassword(CUSTOMER_PASSWORD);
		customer.setGender(CustomerGender.M.name());
		customer.setLanguage("en");
		customer.setStoreCode(Constants.DEFAULT_STORE);
		Address billing = new Address();
		billing.setFirstName("Security");
		billing.setLastName("Test");
		billing.setCountry("BE");
		customer.setBilling(billing);

		ResponseEntity<String> registration = testRestTemplate.exchange("/api/v1/customer/register", HttpMethod.POST,
				new HttpEntity<>(customer, getHeader()), String.class);
		assertTrue(registration.getStatusCode().is2xxSuccessful(), registration.toString());

		ResponseEntity<AuthenticationResponse> login = testRestTemplate.postForEntity("/api/v1/customer/login",
				new HttpEntity<>(new AuthenticationRequest(customerUsername, CUSTOMER_PASSWORD)),
				AuthenticationResponse.class);
		assertEquals(HttpStatus.OK, login.getStatusCode());
		assertNotNull(login.getBody());
		customerToken = login.getBody().getToken();

		adminToken = getHeader().getFirst(HttpHeaders.AUTHORIZATION).substring("Bearer ".length());
	}

	@Test
	void exposesTheExpectedOrderedChainsAndAuthenticationManagers() {
		List<String> matcherPatterns = filterChainProxy.getFilterChains().stream()
				.map(chain -> ((DefaultSecurityFilterChain) chain).getRequestMatcher())
				.map(matcher -> matcher instanceof AntPathRequestMatcher
						? ((AntPathRequestMatcher) matcher).getPattern()
						: matcher.toString())
				.collect(Collectors.toList());

		assertEquals(Arrays.asList("/", "/error", "/resources/**", "/static/**", "/services/public/**",
				"/swagger-ui.html", "/shop/**", "/services/**", "/api/v*/private/**", "/api/v*/auth/**"),
				matcherPatterns);

		customerAuthenticationManager.authenticate(new UsernamePasswordAuthenticationToken(customerUsername,
				CUSTOMER_PASSWORD));
		jwtAdminAuthenticationManager.authenticate(new UsernamePasswordAuthenticationToken(ADMIN_USERNAME,
				ADMIN_PASSWORD));
		jwtCustomerAuthenticationManager.authenticate(new UsernamePasswordAuthenticationToken(customerUsername,
				CUSTOMER_PASSWORD));
		assertProviders(jwtAdminAuthenticationManager, JWTAdminAuthenticationProvider.class,
				DaoAuthenticationProvider.class);
		assertProviders(customerAuthenticationManager, DaoAuthenticationProvider.class);
		assertProviders(servicesAuthenticationManager, DaoAuthenticationProvider.class);
		assertProviders(jwtCustomerAuthenticationManager, DaoAuthenticationProvider.class);
	}

	@Test
	void authenticatesWithEachLocalManager() {
		assertAuthenticated(customerAuthenticationManager, customerUsername, CUSTOMER_PASSWORD);
		assertAuthenticated(jwtCustomerAuthenticationManager, customerUsername, CUSTOMER_PASSWORD);
		assertRejected(customerAuthenticationManager, ADMIN_USERNAME, ADMIN_PASSWORD);
		assertRejected(jwtCustomerAuthenticationManager, ADMIN_USERNAME, ADMIN_PASSWORD);

		assertAuthenticated(jwtAdminAuthenticationManager, ADMIN_USERNAME, ADMIN_PASSWORD);
		assertAuthenticated(servicesAuthenticationManager, ADMIN_USERNAME, ADMIN_PASSWORD);
		assertRejected(jwtAdminAuthenticationManager, customerUsername, CUSTOMER_PASSWORD);
		assertRejected(servicesAuthenticationManager, customerUsername, CUSTOMER_PASSWORD);
	}

	@Test
	void preservesShopChainResponses() {
		assertStatus(HttpMethod.GET, "/shop/x", null, HttpStatus.NOT_FOUND);
		ResponseEntity<String> badBasic = exchange(HttpMethod.GET, "/shop/customer/account",
				basicHeaders("wrong", "credentials"));
		assertEquals(HttpStatus.UNAUTHORIZED, badBasic.getStatusCode());
		assertEquals("Basic realm=\"shop-realm\"", badBasic.getHeaders().getFirst(HttpHeaders.WWW_AUTHENTICATE));
	}

	@Test
	void preservesServicesChainResponses() {
		assertUnauthorizedWithRealm("/services/private/x", null, "rest-customer-realm");
		assertStatus(HttpMethod.GET, "/services/private/x", basicHeaders(ADMIN_USERNAME, ADMIN_PASSWORD),
				HttpStatus.NOT_FOUND);
		assertUnauthorized(HttpMethod.GET, "/services/private/x", basicHeaders(customerUsername, CUSTOMER_PASSWORD));
		HttpHeaders bearer = new HttpHeaders();
		bearer.setBearerAuth(adminToken);
		assertUnauthorized(HttpMethod.GET, "/services/private/x", bearer);
	}

	@Test
	void preservesPrivateApiChainResponses() {
		assertUnauthorizedWithRealm("/api/v1/private/customers", null, "api-admin-realm");
		assertStatus(HttpMethod.GET, "/api/v1/private/customers", bearerHeaders(adminToken), HttpStatus.OK);
		assertUnauthorized(HttpMethod.GET, "/api/v1/private/customers", bearerHeaders(customerToken));
		assertStatus(HttpMethod.GET, "/api/v1/private/customers", basicHeaders(ADMIN_USERNAME, ADMIN_PASSWORD),
				HttpStatus.OK);
		assertUnauthorized(HttpMethod.GET, "/api/v1/private/customers", basicHeaders("wrong", "credentials"));

		HttpHeaders cors = new HttpHeaders();
		cors.add(HttpHeaders.ORIGIN, "http://localhost");
		cors.add(HttpHeaders.ACCESS_CONTROL_REQUEST_METHOD, "GET");
		assertStatus(HttpMethod.OPTIONS, "/api/v1/private/customers", cors, HttpStatus.OK);

		AuthenticationRequest badPassword = new AuthenticationRequest(ADMIN_USERNAME, "wrong");
		ResponseEntity<String> badLogin = testRestTemplate.exchange("/api/v1/private/login", HttpMethod.POST,
				new HttpEntity<>(badPassword), String.class);
		assertEquals(HttpStatus.UNAUTHORIZED, badLogin.getStatusCode());

		AuthenticationRequest goodPassword = new AuthenticationRequest(ADMIN_USERNAME, ADMIN_PASSWORD);
		ResponseEntity<String> goodLogin = testRestTemplate.exchange("/api/v1/private/login", HttpMethod.POST,
				new HttpEntity<>(goodPassword), String.class);
		assertEquals(HttpStatus.OK, goodLogin.getStatusCode());
		assertTrue(goodLogin.getBody().contains("token"));

		assertUnauthorizedWithRealm("/api/v2/private/products", null, "api-admin-realm");
	}

	@Test
	void preservesCustomerApiChainResponses() {
		assertUnauthorizedWithRealm("/api/v1/auth/customer/profile", null, "api-customer-realm");
		assertStatus(HttpMethod.GET, "/api/v1/auth/customer/profile", bearerHeaders(customerToken), HttpStatus.OK);
		assertUnauthorized(HttpMethod.GET, "/api/v1/auth/customer/profile", bearerHeaders(adminToken));
		assertStatus(HttpMethod.GET, "/api/v1/auth/customer/profile",
				basicHeaders(customerUsername, CUSTOMER_PASSWORD), HttpStatus.OK);

		HttpHeaders cors = new HttpHeaders();
		cors.add(HttpHeaders.ORIGIN, "http://localhost");
		cors.add(HttpHeaders.ACCESS_CONTROL_REQUEST_METHOD, "GET");
		assertStatus(HttpMethod.OPTIONS, "/api/v1/auth/customer/profile", cors, HttpStatus.OK);
	}

	@Test
	void preservesIgnoredAndUnmatchedEndpoints() {
		assertStatus(HttpMethod.GET, "/swagger-ui.html", null, HttpStatus.OK);
		assertStatus(HttpMethod.GET, "/api/v1/products?store=DEFAULT", null, HttpStatus.OK);
	}

	private void assertProviders(AuthenticationManager manager, Class<?>... providerTypes) {
		assertTrue(manager instanceof ProviderManager, manager.getClass().getName());
		List<org.springframework.security.authentication.AuthenticationProvider> providers =
				((ProviderManager) manager).getProviders();
		assertEquals(Arrays.asList(providerTypes),
				providers.stream().map(Object::getClass).collect(Collectors.toList()));
	}

	private void assertAuthenticated(AuthenticationManager manager, String username, String password) {
		Authentication result = manager.authenticate(new UsernamePasswordAuthenticationToken(username, password));
		assertTrue(result.isAuthenticated());
	}

	private void assertRejected(AuthenticationManager manager, String username, String password) {
		assertThrows(AuthenticationException.class,
				() -> manager.authenticate(new UsernamePasswordAuthenticationToken(username, password)));
	}

	private void assertUnauthorizedWithRealm(String path, HttpHeaders headers, String realm) {
		HttpHeaders requestHeaders = headers == null ? new HttpHeaders() : headers;
		requestHeaders.setAccept(Arrays.asList(MediaType.APPLICATION_JSON));
		ResponseEntity<String> response = exchange(HttpMethod.GET, path, requestHeaders);
		assertEquals(HttpStatus.UNAUTHORIZED, response.getStatusCode(), response.getHeaders().toString());
		assertEquals("Basic realm=\"" + realm + "\"", response.getHeaders().getFirst(HttpHeaders.WWW_AUTHENTICATE));
	}

	private void assertUnauthorized(HttpMethod method, String path, HttpHeaders headers) {
		HttpHeaders requestHeaders = headers == null ? new HttpHeaders() : headers;
		if (requestHeaders.getAccept().isEmpty()) {
			requestHeaders.setAccept(Arrays.asList(MediaType.APPLICATION_JSON));
		}
		assertEquals(HttpStatus.UNAUTHORIZED, exchange(method, path, requestHeaders).getStatusCode());
	}

	private void assertStatus(HttpMethod method, String path, HttpHeaders headers, HttpStatus expected) {
		ResponseEntity<String> response = exchange(method, path, headers);
		assertEquals(expected, response.getStatusCode(), response.toString());
	}

	private ResponseEntity<String> exchange(HttpMethod method, String path, HttpHeaders headers) {
		return testRestTemplate.exchange(path, method, new HttpEntity<>(headers == null ? new HttpHeaders() : headers),
				String.class);
	}

	private HttpHeaders basicHeaders(String username, String password) {
		HttpHeaders headers = new HttpHeaders();
		headers.setBasicAuth(username, password);
		return headers;
	}

	private HttpHeaders bearerHeaders(String token) {
		HttpHeaders headers = new HttpHeaders();
		headers.setBearerAuth(token);
		return headers;
	}
}
