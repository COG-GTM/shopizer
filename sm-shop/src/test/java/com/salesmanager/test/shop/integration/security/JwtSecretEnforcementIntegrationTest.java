package com.salesmanager.test.shop.integration.security;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertThrows;

import java.nio.charset.StandardCharsets;
import java.util.Base64;

import javax.crypto.spec.SecretKeySpec;

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
import com.salesmanager.shop.model.customer.ReadableCustomer;
import com.salesmanager.shop.model.customer.address.Address;
import com.salesmanager.shop.store.security.AuthenticationRequest;
import com.salesmanager.shop.store.security.AuthenticationResponse;
import com.salesmanager.test.shop.common.ServicesTestSupport;

import io.jsonwebtoken.Jwts;

@SpringBootTest(classes = ShopApplication.class, webEnvironment = WebEnvironment.RANDOM_PORT)
@RunWith(SpringRunner.class)
public class JwtSecretEnforcementIntegrationTest extends ServicesTestSupport {

	private static final String ADMIN = "admin@shopizer.com";
	private static final String ADMIN_PASSWORD = "password";

	@Test
	public void adminLoginTokenUsesConfiguredJwtSecret() {
		AuthenticationResponse login = adminLogin();
		String configuredSecret = System.getProperty("JWT_SECRET");

		assertNotNull(configuredSecret);
		assertEquals(ADMIN, Jwts.parser().setSigningKey(
				new SecretKeySpec(configuredSecret.getBytes(StandardCharsets.UTF_8), "HmacSHA512"))
				.parseClaimsJws(login.getToken()).getBody().getSubject());
		assertThrows(Exception.class,
				() -> Jwts.parser().setSigningKey("aSecret").parseClaimsJws(login.getToken()));
	}

	@Test
	public void invalidAdminTokensAreUnauthorized() {
		AuthenticationResponse login = adminLogin();
		HttpHeaders malformed = bearer("not-a-jwt");
		HttpHeaders tampered = bearer(withTamperedSignature(login.getToken()));
		HttpHeaders unsigned = bearer(unsignedAdminToken());

		assertEquals(HttpStatus.UNAUTHORIZED, get("/api/v1/private/users/1", malformed).getStatusCode());
		assertEquals(HttpStatus.UNAUTHORIZED, get("/api/v1/private/users/1", tampered).getStatusCode());
		assertEquals(HttpStatus.UNAUTHORIZED, get("/api/v1/private/users/1", unsigned).getStatusCode());
	}

	@Test
	public void registeredCustomerTokenAuthenticatesAndTamperingIsUnauthorized() throws Exception {
		String email = uniqueEmail();
		registerCustomer(email);
		AuthenticationResponse login = customerLogin(email);

		ResponseEntity<ReadableCustomer> profile = testRestTemplate.exchange(
				"/api/v1/auth/customer/profile", HttpMethod.GET,
				new HttpEntity<>(bearer(login.getToken())), ReadableCustomer.class);

		assertEquals(HttpStatus.OK, profile.getStatusCode());
		assertEquals(email, profile.getBody().getEmailAddress());
		assertEquals(HttpStatus.UNAUTHORIZED, get("/api/v1/auth/customer/profile",
				bearer(withTamperedSignature(login.getToken()))).getStatusCode());
	}

	@Test
	public void refreshedAdminTokenWorksOnProtectedEndpoint() {
		AuthenticationResponse adminLogin = adminLogin();
		ResponseEntity<AuthenticationResponse> refreshedAdmin = testRestTemplate.exchange(
				"/api/v1/auth/refresh", HttpMethod.GET,
				new HttpEntity<>(bearer(adminLogin.getToken())), AuthenticationResponse.class);
		assertEquals(HttpStatus.OK, refreshedAdmin.getStatusCode());
		assertEquals(HttpStatus.OK, get("/api/v1/private/users/1", bearer(refreshedAdmin.getBody().getToken()))
				.getStatusCode());
	}

	private AuthenticationResponse adminLogin() {
		ResponseEntity<AuthenticationResponse> response = testRestTemplate.postForEntity(
				"/api/v1/private/login",
				new HttpEntity<>(new AuthenticationRequest(ADMIN, ADMIN_PASSWORD)), AuthenticationResponse.class);
		assertEquals(HttpStatus.OK, response.getStatusCode());
		assertNotNull(response.getBody());
		return response.getBody();
	}

	private AuthenticationResponse customerLogin(String email) {
		ResponseEntity<AuthenticationResponse> response = testRestTemplate.postForEntity(
				"/api/v1/customer/login", new HttpEntity<>(new AuthenticationRequest(email, "clear123")),
				AuthenticationResponse.class);
		assertEquals(HttpStatus.OK, response.getStatusCode());
		assertNotNull(response.getBody());
		return response.getBody();
	}

	private void registerCustomer(String email) {
		PersistableCustomer customer = new PersistableCustomer();
		customer.setEmailAddress(email);
		customer.setPassword("clear123");
		customer.setGender(CustomerGender.M.name());
		customer.setLanguage("en");
		customer.setStoreCode(Constants.DEFAULT_STORE);
		Address billing = new Address();
		billing.setFirstName("JWT");
		billing.setLastName("Integration");
		billing.setCountry("BE");
		customer.setBilling(billing);

		ResponseEntity<String> response = testRestTemplate.postForEntity("/api/v1/customer/register",
				new HttpEntity<>(customer), String.class);
		assertEquals(HttpStatus.OK, response.getStatusCode());
	}

	private ResponseEntity<String> get(String path, HttpHeaders headers) {
		return testRestTemplate.exchange(path, HttpMethod.GET, new HttpEntity<>(headers), String.class);
	}

	private static HttpHeaders bearer(String token) {
		HttpHeaders headers = new HttpHeaders();
		headers.add("Authorization", "Bearer " + token);
		return headers;
	}

	private static String withTamperedSignature(String token) {
		String[] parts = token.split("\\.");
		String alphabet = "ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789-_";
		int lastCharacterIndex = alphabet.indexOf(parts[2].charAt(parts[2].length() - 1));
		char replacement = alphabet.charAt((lastCharacterIndex + 16) % alphabet.length());
		return parts[0] + "." + parts[1] + "." + parts[2].substring(0, parts[2].length() - 1) + replacement;
	}

	private static String unsignedAdminToken() {
		String header = Base64.getUrlEncoder().withoutPadding()
				.encodeToString("{\"alg\":\"none\",\"typ\":\"JWT\"}".getBytes(StandardCharsets.UTF_8));
		String payload = Base64.getUrlEncoder().withoutPadding()
				.encodeToString(("{\"sub\":\"" + ADMIN + "\",\"aud\":\"api\",\"iat\":"
						+ (System.currentTimeMillis() / 1000) + ",\"exp\":"
						+ (System.currentTimeMillis() / 1000 + 3600) + "}")
						.getBytes(StandardCharsets.UTF_8));
		return header + "." + payload + ".";
	}

	private static String uniqueEmail() {
		return "jwt-integration-" + System.nanoTime() + "@test.com";
	}
}
