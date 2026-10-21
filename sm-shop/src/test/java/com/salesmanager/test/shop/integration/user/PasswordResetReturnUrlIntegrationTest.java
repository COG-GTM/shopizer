package com.salesmanager.test.shop.integration.user;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Date;
import java.util.UUID;

import javax.inject.Inject;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.SpringBootTest.WebEnvironment;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.test.context.junit.jupiter.SpringExtension;

import com.salesmanager.core.business.services.user.UserService;
import com.salesmanager.core.model.common.CredentialsReset;
import com.salesmanager.core.model.user.User;
import com.salesmanager.shop.application.ShopApplication;
import com.salesmanager.shop.model.security.PersistableGroup;
import com.salesmanager.shop.model.user.PersistableUser;
import com.salesmanager.shop.model.user.ReadableUser;
import com.salesmanager.shop.store.security.AuthenticationRequest;
import com.salesmanager.shop.store.security.PasswordRequest;
import com.salesmanager.shop.store.security.ResetPasswordRequest;
import com.salesmanager.test.shop.common.ServicesTestSupport;

/**
 * Admin password reset through the real Spring context (no mocked beans, so the
 * default test context is shared). Each test uses its own admin user.
 */
@SpringBootTest(classes = ShopApplication.class, webEnvironment = WebEnvironment.RANDOM_PORT)
@ExtendWith(SpringExtension.class)
public class PasswordResetReturnUrlIntegrationTest extends ServicesTestSupport {

	private static final String STORE = "DEFAULT";
	private static final String RESET_REQUEST = "/api/v1/user/password/reset/request?store=" + STORE;
	private static final String INITIAL_PASSWORD = "Password1";

	@Inject
	private UserService userService;

	private String userName;

	@BeforeEach
	public void createAdminUser() {
		userName = "reset-" + UUID.randomUUID().toString().substring(0, 8) + "@test.com";
		PersistableUser newUser = new PersistableUser();
		newUser.setDefaultLanguage("en");
		newUser.setEmailAddress(userName);
		newUser.setFirstName("Reset");
		newUser.setLastName("User");
		newUser.setUserName(userName);
		newUser.setPassword(INITIAL_PASSWORD);
		newUser.setRepeatPassword(INITIAL_PASSWORD);
		PersistableGroup group = new PersistableGroup();
		group.setName("ADMIN");
		newUser.getGroups().add(group);

		ResponseEntity<ReadableUser> created = testRestTemplate.exchange("/api/v1/private/user/", HttpMethod.POST,
				new HttpEntity<>(newUser, getHeader()), ReadableUser.class);
		assertEquals(HttpStatus.OK, created.getStatusCode());
		assertNull(storedReset());
	}

	@Test
	public void untrustedReturnUrlsAreRejectedBeforeAnyTokenIsIssued() {
		String[] rejected = { "https://evil.example", "http://evil.example:8080/admin",
				"http://localhost:8080@evil.example", "http://admin:secret@localhost:8080/admin",
				"javascript://localhost:8080/x", "ftp://localhost:8080/admin", "file://localhost/etc/passwd",
				"localhost:8080/admin", "/admin", "not a url", "http://localhost.evil.example/admin",
				"http://evil.example/?next=http://localhost:8080", "http://evil.example#localhost:8080" };

		for (String returnUrl : rejected) {
			ResponseEntity<String> response = requestReset(userName, returnUrl);

			assertEquals(HttpStatus.BAD_REQUEST, response.getStatusCode(), returnUrl);
			assertTrue(response.getBody().contains("is not allowed"), returnUrl + " -> " + response.getBody());
			assertNull(storedReset(), "token must not be issued for " + returnUrl);
		}
	}

	@Test
	public void rejectionHappensBeforeUserLookup() {
		ResponseEntity<String> response = requestReset("unknown-" + UUID.randomUUID() + "@test.com",
				"https://evil.example");

		assertEquals(HttpStatus.BAD_REQUEST, response.getStatusCode());
		assertTrue(response.getBody().contains("is not allowed"), response.getBody());
	}

	@Test
	public void storeDomainOnAnotherPortAndSchemeIsAccepted() {
		ResponseEntity<String> response = requestReset(userName, "https://LOCALHOST:4200/admin");

		assertFalse(String.valueOf(response.getBody()).contains("is not allowed"), response.getBody());
		assertNotNull(storedReset());
	}

	@Test
	public void storeDomainTokenResetsPasswordOnlyOnce() {
		ResponseEntity<String> response = requestReset(userName, "http://localhost:8080/admin");
		assertFalse(String.valueOf(response.getBody()).contains("is not allowed"), response.getBody());

		CredentialsReset reset = storedReset();
		assertNotNull(reset);
		String token = reset.getCredentialsRequest();
		assertNotNull(token);
		assertTrue(reset.getCredentialsRequestExpiry().after(new Date()));

		String verifyPath = "/api/v1/user/" + STORE + "/reset/" + token;
		String changePath = "/api/v1/user/" + STORE + "/password/" + token;
		assertEquals(HttpStatus.OK, testRestTemplate.getForEntity(verifyPath, String.class).getStatusCode());

		PasswordRequest newPassword = new PasswordRequest();
		newPassword.setUsername(userName);
		newPassword.setPassword("Password9");
		newPassword.setRepeatPassword("Password9");
		assertEquals(HttpStatus.OK,
				testRestTemplate.postForEntity(changePath, newPassword, String.class).getStatusCode());

		assertNull(storedReset());
		assertEquals(HttpStatus.OK, login(userName, "Password9"));
		assertEquals(HttpStatus.UNAUTHORIZED, login(userName, INITIAL_PASSWORD));

		assertTrue(testRestTemplate.getForEntity(verifyPath, String.class).getStatusCode().is4xxClientError());
		newPassword.setPassword("Password8");
		newPassword.setRepeatPassword("Password8");
		assertTrue(testRestTemplate.postForEntity(changePath, newPassword, String.class).getStatusCode()
				.is4xxClientError());
		assertEquals(HttpStatus.OK, login(userName, "Password9"));
	}

	private ResponseEntity<String> requestReset(String user, String returnUrl) {
		ResetPasswordRequest request = new ResetPasswordRequest();
		request.setUsername(user);
		request.setReturnUrl(returnUrl);
		return testRestTemplate.postForEntity(RESET_REQUEST, request, String.class);
	}

	private HttpStatus login(String user, String password) {
		return testRestTemplate.postForEntity("/api/v1/private/login", new AuthenticationRequest(user, password),
				String.class).getStatusCode();
	}

	private CredentialsReset storedReset() {
		try {
			User user = userService.getByUserName(userName, STORE);
			assertNotNull(user);
			CredentialsReset reset = user.getCredentialsResetRequest();
			return reset == null || reset.getCredentialsRequest() == null ? null : reset;
		} catch (Exception e) {
			throw new IllegalStateException(e);
		}
	}
}
