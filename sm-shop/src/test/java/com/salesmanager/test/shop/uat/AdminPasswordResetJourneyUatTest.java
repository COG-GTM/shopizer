package com.salesmanager.test.shop.uat;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import java.util.HashMap;
import java.util.Map;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.SpringBootTest.WebEnvironment;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.test.context.junit4.SpringRunner;

import com.salesmanager.core.business.constants.Constants;
import com.salesmanager.core.business.services.user.UserService;
import com.salesmanager.core.model.common.CredentialsReset;
import com.salesmanager.core.model.user.User;
import com.salesmanager.shop.application.ShopApplication;
import com.salesmanager.shop.model.security.PersistableGroup;
import com.salesmanager.shop.model.user.PersistableUser;
import com.salesmanager.shop.model.user.ReadableUser;
import com.salesmanager.shop.store.security.AuthenticationRequest;
import com.salesmanager.shop.store.security.AuthenticationResponse;
import com.salesmanager.shop.store.security.ResetPasswordRequest;
import com.salesmanager.test.shop.common.ServicesTestSupport;

@RunWith(SpringRunner.class)
@SpringBootTest(classes = ShopApplication.class, webEnvironment = WebEnvironment.RANDOM_PORT)
public class AdminPasswordResetJourneyUatTest extends ServicesTestSupport {

	@Autowired
	private UserService userService;

	@Test
	public void resetsPasswordForDedicatedAdminThroughAnonymousFlow() throws Exception {
		String username = "uat-reset-" + System.currentTimeMillis() + "@shopizer.com";
		String originalPassword = "Password1!";
		String updatedPassword = "Password2!";
		createAdminUser(username, originalPassword);
		String tokenBefore = resetToken(username);
		assertNull(tokenBefore);

		ResponseEntity<Void> rejected = requestReset(username, "https://evil.example");
		assertEquals(HttpStatus.INTERNAL_SERVER_ERROR, rejected.getStatusCode());
		assertEquals(tokenBefore, resetToken(username));

		ResponseEntity<Void> accepted = requestReset(username, "http://localhost:4200");
		assertTrue("Unexpected accepted reset status: " + accepted.getStatusCode(),
				accepted.getStatusCode() == HttpStatus.OK || accepted.getStatusCode() == HttpStatus.BAD_REQUEST);
		String resetToken = resetToken(username);
		assertNotNull(resetToken);
		assertNotEquals(tokenBefore, resetToken);

		ResponseEntity<Void> verified = testRestTemplate.getForEntity(
				"/api/v1/user/DEFAULT/reset/" + resetToken + "?store=DEFAULT", Void.class);
		assertEquals(HttpStatus.OK, verified.getStatusCode());

		Map<String, String> passwordChange = new HashMap<>();
		passwordChange.put("current", originalPassword);
		passwordChange.put("password", updatedPassword);
		passwordChange.put("repeatPassword", updatedPassword);
		ResponseEntity<Void> changed = testRestTemplate.postForEntity(
				"/api/v1/user/DEFAULT/password/" + resetToken + "?store=DEFAULT",
				new HttpEntity<Map<String, String>>(passwordChange, jsonHeaders()), Void.class);
		assertEquals(HttpStatus.OK, changed.getStatusCode());

		ResponseEntity<AuthenticationResponse> login = testRestTemplate.postForEntity(
				"/api/v1/private/login", new AuthenticationRequest(username, updatedPassword),
				AuthenticationResponse.class);
		assertEquals(HttpStatus.OK, login.getStatusCode());
		assertNotNull(login.getBody());
		assertNotNull(login.getBody().getToken());
	}

	private void createAdminUser(String username, String password) {
		PersistableUser user = new PersistableUser();
		user.setDefaultLanguage("en");
		user.setEmailAddress(username);
		user.setFirstName("Reset");
		user.setLastName("UAT");
		user.setUserName(username);
		user.setPassword(password);
		user.setRepeatPassword(password);

		PersistableGroup adminGroup = new PersistableGroup();
		adminGroup.setName("ADMIN");
		user.getGroups().add(adminGroup);

		ResponseEntity<ReadableUser> response = testRestTemplate.postForEntity(
				"/api/v1/private/user/?store=DEFAULT",
				new HttpEntity<PersistableUser>(user, getHeader()), ReadableUser.class);
		assertTrue("User creation returned " + response.getStatusCode(),
				response.getStatusCode().is2xxSuccessful());
		assertNotNull(response.getBody());
	}

	private ResponseEntity<Void> requestReset(String username, String returnUrl) {
		ResetPasswordRequest request = new ResetPasswordRequest(username);
		request.setReturnUrl(returnUrl);
		return testRestTemplate.postForEntity("/api/v1/user/password/reset/request?store=DEFAULT", request, Void.class);
	}

	private String resetToken(String username) throws Exception {
		User user = userService.getByUserName(username, Constants.DEFAULT_STORE);
		assertNotNull(user);
		CredentialsReset credentialsReset = user.getCredentialsResetRequest();
		return credentialsReset == null ? null : credentialsReset.getCredentialsRequest();
	}

	private HttpHeaders jsonHeaders() {
		HttpHeaders headers = new HttpHeaders();
		headers.setContentType(org.springframework.http.MediaType.APPLICATION_JSON);
		return headers;
	}
}
