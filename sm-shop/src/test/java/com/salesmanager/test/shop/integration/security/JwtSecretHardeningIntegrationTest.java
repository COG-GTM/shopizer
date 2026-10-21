package com.salesmanager.test.shop.integration.security;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.Date;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.SpringBootTest.WebEnvironment;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.test.context.junit4.SpringRunner;
import org.springframework.test.util.ReflectionTestUtils;

import com.salesmanager.core.business.constants.Constants;
import com.salesmanager.core.model.customer.CustomerGender;
import com.salesmanager.shop.application.ShopApplication;
import com.salesmanager.shop.model.customer.PersistableCustomer;
import com.salesmanager.shop.model.customer.address.Address;
import com.salesmanager.shop.store.security.AuthenticationRequest;
import com.salesmanager.shop.store.security.AuthenticationResponse;
import com.salesmanager.shop.store.security.JWTTokenUtil;
import com.salesmanager.test.shop.common.ServicesTestSupport;

import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.SignatureAlgorithm;

@RunWith(SpringRunner.class)
@SpringBootTest(classes = ShopApplication.class, webEnvironment = WebEnvironment.RANDOM_PORT)
public class JwtSecretHardeningIntegrationTest extends ServicesTestSupport {

	private static final String ADMIN_PROFILE = "/api/v1/private/user/profile";
	private static final String CUSTOMER_PROFILE = "/api/v1/auth/customer/profile";

	@Autowired
	private JWTTokenUtil jwtTokenUtil;

	@Test
	public void rejectsForgedTokensAndAllowsAuthenticatedProfiles() {
		ResponseEntity<AuthenticationResponse> adminLogin = testRestTemplate.postForEntity(
				"/api/v1/private/login",
				new HttpEntity<>(new AuthenticationRequest("admin@shopizer.com", "password")),
				AuthenticationResponse.class);
		assertEquals(HttpStatus.OK, adminLogin.getStatusCode());
		assertNotNull(adminLogin.getBody());

		String adminToken = adminLogin.getBody().getToken();
		assertEquals(HttpStatus.OK, profile(ADMIN_PROFILE, adminToken).getStatusCode());
		assertUnauthorized(profile(ADMIN_PROFILE, null));
		assertUnauthorized(profile(ADMIN_PROFILE, "abc.def.ghi"));
		assertUnauthorized(profile(ADMIN_PROFILE, forgedToken("aSecret", "admin@shopizer.com")));
		String otherKey = Base64.getEncoder().encodeToString(
				"x".repeat(64).getBytes(StandardCharsets.UTF_8));
		assertUnauthorized(profile(ADMIN_PROFILE, forgedToken(otherKey, "admin@shopizer.com")));

		String customerEmail = "jwt-hardening-" + System.currentTimeMillis() + "@example.com";
		PersistableCustomer customer = new PersistableCustomer();
		customer.setEmailAddress(customerEmail);
		customer.setPassword("clear123");
		customer.setGender(CustomerGender.M.name());
		customer.setLanguage("en");
		customer.setStoreCode(Constants.DEFAULT_STORE);
		Address billing = new Address();
		billing.setFirstName("JWT");
		billing.setLastName("Customer");
		billing.setCountry("BE");
		customer.setBilling(billing);
		ResponseEntity<PersistableCustomer> registration = testRestTemplate.postForEntity(
				"/api/v1/customer/register", new HttpEntity<>(customer, getHeader()),
				PersistableCustomer.class);
		assertEquals(HttpStatus.OK, registration.getStatusCode());

		ResponseEntity<AuthenticationResponse> customerLogin = testRestTemplate.postForEntity(
				"/api/v1/customer/login",
				new HttpEntity<>(new AuthenticationRequest(customerEmail, "clear123")),
				AuthenticationResponse.class);
		assertEquals(HttpStatus.OK, customerLogin.getStatusCode());
		assertNotNull(customerLogin.getBody());
		assertEquals(HttpStatus.OK, profile(CUSTOMER_PROFILE, customerLogin.getBody().getToken()).getStatusCode());
		assertUnauthorized(profile(CUSTOMER_PROFILE, forgedToken("aSecret", customerEmail)));

		String configuredSecret = (String) ReflectionTestUtils.getField(jwtTokenUtil, "secret");
		assertNotNull(configuredSecret);
		assertTrue(configuredSecret.getBytes(StandardCharsets.UTF_8).length >= 64);
		assertNotEquals("aSecret", configuredSecret);
	}

	private ResponseEntity<String> profile(String endpoint, String token) {
		HttpHeaders headers = new HttpHeaders();
		if (token != null) {
			headers.set(HttpHeaders.AUTHORIZATION, "Bearer " + token);
		}
		return testRestTemplate.exchange(endpoint, HttpMethod.GET, new HttpEntity<>(headers), String.class);
	}

	private void assertUnauthorized(ResponseEntity<String> response) {
		assertTrue(response.getStatusCode() == HttpStatus.UNAUTHORIZED
				|| response.getStatusCode() == HttpStatus.FORBIDDEN);
		assertTrue("Unexpected server error: " + response.getStatusCode(),
				!response.getStatusCode().is5xxServerError());
	}

	private String forgedToken(String signingSecret, String username) {
		Date now = new Date();
		return Jwts.builder()
				.setSubject(username)
				.setAudience("api")
				.setIssuedAt(now)
				.setExpiration(new Date(now.getTime() + 86400000L))
				.signWith(SignatureAlgorithm.HS512, signingSecret)
				.compact();
	}
}
