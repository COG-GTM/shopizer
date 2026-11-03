package com.salesmanager.test.shop.integration.user;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import javax.inject.Inject;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.SpringBootTest.WebEnvironment;
import org.springframework.test.context.junit4.SpringRunner;

import com.salesmanager.core.business.services.user.UserService;
import com.salesmanager.core.model.common.CredentialsReset;
import com.salesmanager.core.model.user.User;
import com.salesmanager.shop.application.ShopApplication;
import com.salesmanager.test.shop.common.ServicesTestSupport;

@SpringBootTest(classes = ShopApplication.class, webEnvironment = WebEnvironment.RANDOM_PORT)
@RunWith(SpringRunner.class)
public class PasswordResetTokenIntegrationTest extends ServicesTestSupport {

	private static final String DEFAULT_USER = "admin@shopizer.com";

	@Inject
	private UserService userService;

	@Test
	public void resetTokenCanOnlyBeUsedOnce() throws Exception {
		User user = userService.getByUserName(DEFAULT_USER);
		Long userId = user.getId();
		String storeCode = user.getMerchantStore().getCode();
		String currentPassword = user.getAdminPassword();

		CredentialsReset reset = new CredentialsReset();
		reset.setCredentialsRequest("single-use-test-token");
		user.setCredentialsResetRequest(reset);
		userService.save(user);

		assertEquals(userId, userService.getByPasswordResetToken(storeCode, "single-use-test-token").getId());
		assertTrue(userService.resetPasswordWithToken(userId, "single-use-test-token", currentPassword));
		assertFalse(userService.resetPasswordWithToken(userId, "single-use-test-token", currentPassword));
		assertNull(userService.getByPasswordResetToken(storeCode, "single-use-test-token"));
	}
}
