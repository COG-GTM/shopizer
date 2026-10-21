package com.salesmanager.test.shop.integration.user;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import javax.inject.Inject;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.salesmanager.core.business.services.user.UserService;
import com.salesmanager.core.model.common.CredentialsReset;
import com.salesmanager.core.model.user.User;
import com.salesmanager.shop.application.ShopApplication;
import com.salesmanager.shop.store.controller.user.facade.UserFacade;
import com.salesmanager.shop.store.security.AuthenticationRequest;
import com.salesmanager.shop.store.security.AuthenticationResponse;
import com.salesmanager.shop.store.security.PasswordRequest;
import com.salesmanager.shop.store.security.ResetPasswordRequest;
import com.salesmanager.test.shop.common.ServicesTestSupport;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.SpringBootTest.WebEnvironment;
import org.springframework.http.ResponseEntity;
import org.springframework.test.context.junit4.SpringRunner;
import org.springframework.test.util.AopTestUtils;
import org.springframework.test.util.ReflectionTestUtils;

@SpringBootTest(classes = ShopApplication.class, webEnvironment = WebEnvironment.RANDOM_PORT)
@RunWith(SpringRunner.class)
public class UserPasswordResetFlowIntegrationTest extends ServicesTestSupport {

	private static final String ADMIN_USERNAME = "admin@shopizer.com";
	private static final String RESET_REQUEST_PATH = "/api/v1/user/password/reset/request?store=DEFAULT";
	private static final String STORE_RETURN_URL = "http://localhost:8080/admin";

	@Inject
	private UserService userService;

	@Autowired
	private UserFacade userFacade;

	@Before
	public void clearAdminResetTokenBeforeTest() throws Exception {
		clearAdminResetToken();
	}

	@After
	public void clearAdminResetTokenAfterTest() throws Exception {
		clearAdminResetToken();
	}

	@Test
	public void rejectedReturnUrlsReturnErrorWithoutToken() throws Exception {
		String[] returnUrls = {
				"https://evil.example",
				"http://localhost:8080@evil.example",
				"http://localhost:8080\\@evil.example",
				"http://evil.example#@localhost:8080",
				"javascript://localhost:8080/x"
		};

		for (String returnUrl : returnUrls) {
			ResponseEntity<String> response = requestReset(returnUrl);
			assertEquals("Expected 400 for " + returnUrl, 400, response.getStatusCodeValue());

			JsonNode error = new ObjectMapper().readTree(response.getBody());
			assertEquals("Expected errorCode 400 for " + returnUrl, "400", error.get("errorCode").asText());
			assertTrue("Expected a rejection message for " + returnUrl,
					error.path("message").asText().contains("is not allowed"));
			assertNoStoredResetToken();
		}
	}

	@Test
	public void acceptsStoreHostWithMixedCaseAndDifferentPorts() throws Exception {
		assertAllowedUrlCreatesToken("http://LOCALHOST:8080/admin");
		assertAllowedUrlCreatesToken("http://localhost:4200");
	}

	@Test
	public void acceptsConfiguredHostAndRejectsUnconfiguredHost() throws Exception {
		Object target = AopTestUtils.getUltimateTargetObject(userFacade);
		String originalAllowedHosts = (String) ReflectionTestUtils.getField(target, "passwordResetAllowedHosts");
		try {
			ReflectionTestUtils.setField(target, "passwordResetAllowedHosts", "admin.example.com");
			assertAllowedUrlCreatesToken("https://admin.example.com/admin");
			clearAdminResetToken();

			ResponseEntity<String> rejected = requestReset("https://evil.example");
			assertEquals(400, rejected.getStatusCodeValue());
			JsonNode error = new ObjectMapper().readTree(rejected.getBody());
			assertEquals("400", error.path("errorCode").asText());
			assertTrue(error.path("message").asText().contains("is not allowed"));
			assertNoStoredResetToken();
		} finally {
			ReflectionTestUtils.setField(target, "passwordResetAllowedHosts", originalAllowedHosts);
		}
	}

	@Test
	public void resetJourneyChangesPasswordRejectsReuseAndRestoresSeededPassword() throws Exception {
		boolean passwordChanged = false;
		try {
			String token = assertAllowedUrlCreatesToken(STORE_RETURN_URL);

			ResponseEntity<Void> verifyResponse = testRestTemplate.getForEntity(
					"/api/v1/user/DEFAULT/reset/" + token, Void.class);
			assertEquals(200, verifyResponse.getStatusCodeValue());

			ResponseEntity<Void> changeResponse = testRestTemplate.postForEntity(
					"/api/v1/user/DEFAULT/password/" + token, passwordRequest("NewPassw0rd!"), Void.class);
			passwordChanged = changeResponse.getStatusCode().is2xxSuccessful();
			assertEquals(200, changeResponse.getStatusCodeValue());

			assertTrue(login("NewPassw0rd!").getStatusCode().is2xxSuccessful());
			assertFalse(login("password").getStatusCode().is2xxSuccessful());

			ResponseEntity<Void> reusedTokenResponse = testRestTemplate.getForEntity(
					"/api/v1/user/DEFAULT/reset/" + token, Void.class);
			assertFalse(reusedTokenResponse.getStatusCode().is2xxSuccessful());
			ResponseEntity<Void> reusedPasswordResponse = testRestTemplate.postForEntity(
					"/api/v1/user/DEFAULT/password/" + token, passwordRequest("AnotherPassw0rd!"), Void.class);
			assertFalse(reusedPasswordResponse.getStatusCode().is2xxSuccessful());
		} finally {
			if (passwordChanged) {
				String restoreToken = assertAllowedUrlCreatesToken(STORE_RETURN_URL);
				ResponseEntity<Void> restoreResponse = testRestTemplate.postForEntity(
						"/api/v1/user/DEFAULT/password/" + restoreToken, passwordRequest("password"), Void.class);
				assertTrue(restoreResponse.getStatusCode().is2xxSuccessful());
			}
		}

		assertTrue(login("password").getStatusCode().is2xxSuccessful());
	}

	private String assertAllowedUrlCreatesToken(String returnUrl) throws Exception {
		String previousToken = storedResetToken();
		ResponseEntity<String> response = requestReset(returnUrl);
		String token = storedResetToken();

		assertNotNull("Expected reset token for " + returnUrl, token);
		assertFalse("Expected a new reset token for " + returnUrl, previousToken != null
				&& previousToken.equals(token));
		if (!response.getStatusCode().is2xxSuccessful()) {
			JsonNode error = new ObjectMapper().readTree(response.getBody());
			assertFalse("Expected URL validation to pass for " + returnUrl,
					error.path("message").asText().contains("is not allowed"));
		}
		return token;
	}

	private ResponseEntity<String> requestReset(String returnUrl) {
		return testRestTemplate.postForEntity(RESET_REQUEST_PATH, resetPasswordRequest(returnUrl), String.class);
	}

	private ResponseEntity<AuthenticationResponse> login(String password) {
		return testRestTemplate.postForEntity("/api/v1/private/login",
				new AuthenticationRequest(ADMIN_USERNAME, password), AuthenticationResponse.class);
	}

	private static ResetPasswordRequest resetPasswordRequest(String returnUrl) {
		ResetPasswordRequest request = new ResetPasswordRequest();
		request.setUsername(ADMIN_USERNAME);
		request.setReturnUrl(returnUrl);
		return request;
	}

	private static PasswordRequest passwordRequest(String password) {
		PasswordRequest request = new PasswordRequest();
		request.setUsername(ADMIN_USERNAME);
		request.setPassword(password);
		request.setRepeatPassword(password);
		return request;
	}

	private void clearAdminResetToken() throws Exception {
		User admin = userService.getByUserName(ADMIN_USERNAME, "DEFAULT");
		assertNotNull(admin);
		CredentialsReset credentialsReset = admin.getCredentialsResetRequest();
		if (credentialsReset != null) {
			admin.setCredentialsResetRequest(null);
			userService.save(admin);
		}
		assertNoStoredResetToken();
	}

	private String storedResetToken() throws Exception {
		User admin = userService.getByUserName(ADMIN_USERNAME, "DEFAULT");
		assertNotNull(admin);
		CredentialsReset credentialsReset = admin.getCredentialsResetRequest();
		return credentialsReset == null ? null : credentialsReset.getCredentialsRequest();
	}

	private void assertNoStoredResetToken() throws Exception {
		assertTrue(storedResetToken() == null);
	}
}
