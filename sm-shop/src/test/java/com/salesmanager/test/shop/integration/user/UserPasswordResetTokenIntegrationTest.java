package com.salesmanager.test.shop.integration.user;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;
import static org.springframework.http.HttpStatus.NOT_FOUND;
import static org.springframework.http.HttpStatus.OK;
import static org.springframework.http.HttpStatus.UNAUTHORIZED;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.SpringBootTest.WebEnvironment;
import org.springframework.http.ResponseEntity;
import org.springframework.test.context.junit4.SpringRunner;

import com.salesmanager.core.business.services.user.UserService;
import com.salesmanager.core.model.common.CredentialsReset;
import com.salesmanager.core.model.user.User;
import com.salesmanager.shop.application.ShopApplication;
import com.salesmanager.shop.store.security.AuthenticationRequest;
import com.salesmanager.shop.store.security.AuthenticationResponse;
import com.salesmanager.test.shop.common.ServicesTestSupport;

@SpringBootTest(classes = ShopApplication.class, webEnvironment = WebEnvironment.RANDOM_PORT)
@RunWith(SpringRunner.class)
public class UserPasswordResetTokenIntegrationTest extends ServicesTestSupport {

	private static final String STORE = "DEFAULT";
	private static final String USERNAME = "admin@shopizer.com";

	@Autowired
	private UserService userService;

	@Test
	public void resetClearsTokenAndRejectsReuse() throws Exception {
		String token = UUID.randomUUID().toString();
		User user = userService.getByUserName(USERNAME, STORE);
		CredentialsReset reset = new CredentialsReset();
		reset.setCredentialsRequest(token);
		reset.setCredentialsRequestExpiry(com.salesmanager.shop.utils.DateUtil.addDaysToCurrentDate(2));
		user.setCredentialsResetRequest(reset);
		userService.saveOrUpdate(user);

		assertEquals(OK, testRestTemplate.getForEntity(resetVerifyUrl(token), Void.class).getStatusCode());
		assertEquals(OK, resetPassword(token, "password").getStatusCode());
		assertNull(userService.getByPasswordResetToken(STORE, token));
		CredentialsReset persistedReset = userService.getByUserName(USERNAME, STORE).getCredentialsResetRequest();
		assertTrue(persistedReset == null || persistedReset.getCredentialsRequest() == null);

		assertEquals(NOT_FOUND, testRestTemplate.getForEntity(resetVerifyUrl(token), Void.class).getStatusCode());
		assertEquals(NOT_FOUND, resetPassword(token, "hacked123").getStatusCode());

		ResponseEntity<AuthenticationResponse> validLogin = testRestTemplate.postForEntity("/api/v1/private/login",
				new AuthenticationRequest(USERNAME, "password"), AuthenticationResponse.class);
		assertEquals(OK, validLogin.getStatusCode());
		assertNotNull(validLogin.getBody().getToken());

		ResponseEntity<String> invalidLogin = testRestTemplate.postForEntity("/api/v1/private/login",
				new AuthenticationRequest(USERNAME, "hacked123"), String.class);
		assertEquals(UNAUTHORIZED, invalidLogin.getStatusCode());
	}

	@Test
	public void unknownTokenIsNotFoundForVerifyAndReset() {
		String token = UUID.randomUUID().toString();

		assertEquals(NOT_FOUND, testRestTemplate.getForEntity(resetVerifyUrl(token), Void.class).getStatusCode());
		assertEquals(NOT_FOUND, resetPassword(token, "password").getStatusCode());
	}

	private String resetVerifyUrl(String token) {
		return "/api/v1/user/" + STORE + "/reset/" + token;
	}

	private ResponseEntity<Void> resetPassword(String token, String password) {
		Map<String, String> body = new HashMap<>();
		body.put("password", password);
		body.put("repeatPassword", password);
		return testRestTemplate.postForEntity("/api/v1/user/" + STORE + "/password/" + token,
				body, Void.class);
	}
}
