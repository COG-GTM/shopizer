package com.salesmanager.test.shop.integration.user;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import java.util.Collections;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.SpringBootTest.WebEnvironment;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.test.context.junit4.SpringRunner;

import com.salesmanager.shop.application.ShopApplication;
import com.salesmanager.shop.model.security.PersistableGroup;
import com.salesmanager.shop.model.user.PersistableUser;
import com.salesmanager.shop.model.user.ReadableUser;
import com.salesmanager.shop.store.security.AuthenticationRequest;
import com.salesmanager.shop.store.security.AuthenticationResponse;
import com.salesmanager.test.shop.common.ServicesTestSupport;

/**
 * Disabling an admin through PATCH /api/v1/private/user/{id}/enabled must block login,
 * invalidate already issued JWTs and block refresh, through the real security filter chain.
 */
@SpringBootTest(classes = ShopApplication.class, webEnvironment = WebEnvironment.RANDOM_PORT)
@RunWith(SpringRunner.class)
public class DisabledAdminUserIntegrationTest extends ServicesTestSupport {

	private static final String PASSWORD = "Password123";

	@Test
	public void disablingAdminRevokesLoginTokenAndRefreshAndEnablingRestoresIt() throws Exception {
		String username = "disabled.it." + System.nanoTime() + "@shopizer.com";
		Long id = createAdmin(username);

		String issuedToken = login(username);
		assertEquals(HttpStatus.OK, profile(issuedToken).getStatusCode());

		setEnabled(id, false);

		assertEquals(HttpStatus.UNAUTHORIZED, profile(issuedToken).getStatusCode());
		assertEquals(HttpStatus.UNAUTHORIZED, refresh(issuedToken).getStatusCode());

		ResponseEntity<String> loginWhileDisabled = loginResponse(username);
		assertEquals(HttpStatus.UNAUTHORIZED, loginWhileDisabled.getStatusCode());
		assertTrue(loginWhileDisabled.getBody(), loginWhileDisabled.getBody().contains("Bad credentials"));

		setEnabled(id, true);

		assertEquals(HttpStatus.OK, profile(issuedToken).getStatusCode());
		assertEquals(HttpStatus.OK, profile(login(username)).getStatusCode());
	}

	@Test
	public void disablingAnotherAdminDoesNotAffectSeededAdmin() throws Exception {
		Long id = createAdmin("other.disabled." + System.nanoTime() + "@shopizer.com");
		setEnabled(id, false);

		ResponseEntity<ReadableUser> response = testRestTemplate.exchange("/api/v1/private/user/profile", HttpMethod.GET,
				new HttpEntity<>(getHeader()), ReadableUser.class);

		assertEquals(HttpStatus.OK, response.getStatusCode());
		assertEquals("admin@shopizer.com", response.getBody().getUserName());
	}

	@Test
	public void wrongPasswordForDisabledAdminIsIndistinguishableFromDisabled() throws Exception {
		String username = "disabled.pwd." + System.nanoTime() + "@shopizer.com";
		setEnabled(createAdmin(username), false);

		ResponseEntity<String> wrongPassword = testRestTemplate.postForEntity("/api/v1/private/login",
				new HttpEntity<>(new AuthenticationRequest(username, "wrong-password")), String.class);
		ResponseEntity<String> rightPassword = loginResponse(username);

		assertEquals(HttpStatus.UNAUTHORIZED, wrongPassword.getStatusCode());
		assertEquals(wrongPassword.getStatusCode(), rightPassword.getStatusCode());
		assertEquals(wrongPassword.getBody(), rightPassword.getBody());
	}

	private Long createAdmin(String username) {
		PersistableUser user = new PersistableUser();
		user.setDefaultLanguage("en");
		user.setEmailAddress(username);
		user.setUserName(username);
		user.setFirstName("Disabled");
		user.setLastName("Admin");
		user.setPassword(PASSWORD);
		user.setRepeatPassword(PASSWORD);
		user.setStore("DEFAULT");
		user.setActive(true);
		PersistableGroup group = new PersistableGroup();
		group.setName("ADMIN");
		user.setGroups(Collections.singletonList(group));

		ResponseEntity<ReadableUser> response = testRestTemplate.postForEntity("/api/v1/private/user/",
				new HttpEntity<>(user, getHeader()), ReadableUser.class);
		assertEquals(HttpStatus.OK, response.getStatusCode());
		assertNotNull(response.getBody().getId());
		return response.getBody().getId();
	}

	private void setEnabled(Long id, boolean active) {
		PersistableUser user = new PersistableUser();
		user.setActive(active);
		ResponseEntity<Void> response = testRestTemplate.exchange("/api/v1/private/user/" + id + "/enabled",
				HttpMethod.PATCH, new HttpEntity<>(user, getHeader()), Void.class);
		assertEquals(HttpStatus.OK, response.getStatusCode());
	}

	private ResponseEntity<String> loginResponse(String username) {
		return testRestTemplate.postForEntity("/api/v1/private/login",
				new HttpEntity<>(new AuthenticationRequest(username, PASSWORD)), String.class);
	}

	private String login(String username) {
		ResponseEntity<AuthenticationResponse> response = testRestTemplate.postForEntity("/api/v1/private/login",
				new HttpEntity<>(new AuthenticationRequest(username, PASSWORD)), AuthenticationResponse.class);
		assertEquals(HttpStatus.OK, response.getStatusCode());
		return response.getBody().getToken();
	}

	private ResponseEntity<String> profile(String token) {
		return testRestTemplate.exchange("/api/v1/private/user/profile", HttpMethod.GET,
				new HttpEntity<>(bearer(token)), String.class);
	}

	private ResponseEntity<String> refresh(String token) {
		return testRestTemplate.exchange("/api/v1/auth/refresh", HttpMethod.GET,
				new HttpEntity<>(bearer(token)), String.class);
	}

	private static HttpHeaders bearer(String token) {
		HttpHeaders headers = new HttpHeaders();
		headers.setContentType(MediaType.APPLICATION_JSON);
		headers.add("Authorization", "Bearer " + token);
		return headers;
	}
}
