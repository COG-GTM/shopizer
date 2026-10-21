package com.salesmanager.test.shop.integration.user;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotEquals;
import static org.junit.Assert.assertNotNull;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.SpringBootTest.WebEnvironment;
import org.springframework.context.ApplicationContext;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.test.context.junit4.SpringRunner;
import org.springframework.test.util.AopTestUtils;
import org.springframework.test.util.ReflectionTestUtils;

import com.salesmanager.core.business.constants.Constants;
import com.salesmanager.core.business.services.user.UserService;
import com.salesmanager.core.model.common.CredentialsReset;
import com.salesmanager.core.model.user.User;
import com.salesmanager.shop.application.ShopApplication;
import com.salesmanager.shop.store.security.ResetPasswordRequest;
import com.salesmanager.test.shop.common.ServicesTestSupport;

/**
 * Overrides the facade field directly so this test reuses the cached context.
 */
@RunWith(SpringRunner.class)
@SpringBootTest(classes = ShopApplication.class, webEnvironment = WebEnvironment.RANDOM_PORT)
public class AdminPasswordResetCustomAllowlistIntegrationTest extends ServicesTestSupport {

	private static final String ADMIN_USERNAME = "admin@shopizer.com";
	private static final String RESET_REQUEST_URL = "/api/v1/user/password/reset/request?store=DEFAULT";

	@Autowired
	private UserService userService;

	@Autowired
	private ApplicationContext applicationContext;

	private String savedAllowedPasswordResetReturnUrls;

	@Before
	public void applyTestAllowlistProperty() {
		Object facade = AopTestUtils.getUltimateTargetObject(applicationContext.getBean("userFacade"));
		savedAllowedPasswordResetReturnUrls =
				(String) ReflectionTestUtils.getField(facade, "allowedPasswordResetReturnUrls");
		ReflectionTestUtils.setField(facade, "allowedPasswordResetReturnUrls",
				"https://admin.example.com, https://ops.example.com:8443");
	}

	@After
	public void restorePasswordResetAllowlist() {
		Object facade = AopTestUtils.getUltimateTargetObject(applicationContext.getBean("userFacade"));
		ReflectionTestUtils.setField(facade, "allowedPasswordResetReturnUrls",
				savedAllowedPasswordResetReturnUrls);
	}

	@Test
	public void acceptsBothConfiguredOriginsAndStoreLocalhost() throws Exception {
		assertAcceptedAndTokenPersisted("https://admin.example.com/admin");
		assertAcceptedAndTokenPersisted("https://ops.example.com:8443");
		assertAcceptedAndTokenPersisted("http://localhost:4200");
	}

	@Test
	public void rejectsConfiguredOriginWithDifferentPortWithoutChangingTheToken() throws Exception {
		assertRejectedWithoutTokenChange("https://ops.example.com");
	}

	@Test
	public void rejectsConfiguredOriginWithDifferentSchemeWithoutChangingTheToken() throws Exception {
		assertRejectedWithoutTokenChange("http://admin.example.com");
	}

	private void assertAcceptedAndTokenPersisted(String returnUrl) throws Exception {
		String tokenBefore = adminResetToken();
		ResponseEntity<Void> response = requestReset(returnUrl);

		assertEquals(HttpStatus.BAD_REQUEST, response.getStatusCode());
		String tokenAfter = adminResetToken();
		assertNotNull(tokenAfter);
		assertNotEquals(tokenBefore, tokenAfter);
	}

	private void assertRejectedWithoutTokenChange(String returnUrl) throws Exception {
		String tokenBefore = adminResetToken();
		ResponseEntity<Void> response = requestReset(returnUrl);

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
		return credentialsReset == null ? null : credentialsReset.getCredentialsRequest();
	}
}
