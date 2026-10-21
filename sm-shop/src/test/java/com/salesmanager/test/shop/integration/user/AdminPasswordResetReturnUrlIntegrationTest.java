package com.salesmanager.test.shop.integration.user;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.SpringBootTest.WebEnvironment;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.test.context.junit4.SpringRunner;

import com.salesmanager.core.business.constants.Constants;
import com.salesmanager.core.business.services.user.UserService;
import com.salesmanager.core.model.common.CredentialsReset;
import com.salesmanager.core.model.user.User;
import com.salesmanager.shop.application.ShopApplication;
import com.salesmanager.shop.store.security.ResetPasswordRequest;
import com.salesmanager.test.shop.common.ServicesTestSupport;

@RunWith(SpringRunner.class)
@SpringBootTest(classes = ShopApplication.class, webEnvironment = WebEnvironment.RANDOM_PORT)
public class AdminPasswordResetReturnUrlIntegrationTest extends ServicesTestSupport {

	private static final String ADMIN_USERNAME = "admin@shopizer.com";
	private static final String RESET_REQUEST_URL = "/api/v1/user/password/reset/request?store=DEFAULT";

	@Autowired
	private UserService userService;

	@Test
	public void rejectsEvilReturnUrlWithoutChangingTheToken() throws Exception {
		String tokenBefore = adminResetToken();

		ResponseEntity<Void> response = requestReset("https://evil.example");

		assertEquals(HttpStatus.INTERNAL_SERVER_ERROR, response.getStatusCode());
		assertEquals(tokenBefore, adminResetToken());
	}

	@Test
	public void rejectsUserInfoReturnUrlWithoutChangingTheToken() throws Exception {
		String tokenBefore = adminResetToken();

		ResponseEntity<Void> response = requestReset("https://admin@evil.example");

		assertEquals(HttpStatus.INTERNAL_SERVER_ERROR, response.getStatusCode());
		assertEquals(tokenBefore, adminResetToken());
	}

	@Test
	public void acceptsLocalhostOriginAndPersistsANewToken() throws Exception {
		String tokenBefore = adminResetToken();

		ResponseEntity<Void> response = requestReset("http://localhost:4200");

		assertAcceptedResetRequestStatus(response.getStatusCode());
		assertNotNull(adminResetToken());
		assertNotEquals(tokenBefore, adminResetToken());
	}

	@Test
	public void rejectsLoopbackAddressWithoutChangingTheToken() throws Exception {
		String tokenBefore = adminResetToken();

		ResponseEntity<Void> response = requestReset("http://127.0.0.1:4200");

		assertEquals(HttpStatus.INTERNAL_SERVER_ERROR, response.getStatusCode());
		assertEquals(tokenBefore, adminResetToken());
	}

	private ResponseEntity<Void> requestReset(String returnUrl) {
		ResetPasswordRequest request = new ResetPasswordRequest(ADMIN_USERNAME);
		request.setReturnUrl(returnUrl);
		return testRestTemplate.postForEntity(RESET_REQUEST_URL, request, Void.class);
	}

	private String adminResetToken() throws Exception {
		User admin = userService.getByUserName(ADMIN_USERNAME, Constants.DEFAULT_STORE);
		assertNotNull(admin);
		CredentialsReset credentialsReset = admin.getCredentialsResetRequest();
		if (credentialsReset == null) {
			return null;
		}
		return credentialsReset.getCredentialsRequest();
	}

	private void assertAcceptedResetRequestStatus(HttpStatus status) {
		assertEquals(HttpStatus.BAD_REQUEST, status);
	}
}
