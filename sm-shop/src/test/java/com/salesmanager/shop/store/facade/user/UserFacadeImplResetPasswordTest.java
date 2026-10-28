package com.salesmanager.shop.store.facade.user;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnitRunner;
import org.springframework.security.crypto.password.PasswordEncoder;

import com.salesmanager.core.business.services.user.UserService;
import com.salesmanager.core.model.common.CredentialsReset;
import com.salesmanager.core.model.user.User;
import com.salesmanager.shop.store.api.exception.ResourceNotFoundException;
import com.salesmanager.shop.utils.DateUtil;

@RunWith(MockitoJUnitRunner.class)
public class UserFacadeImplResetPasswordTest {

	private static final String STORE = "DEFAULT";
	private static final String TOKEN = "reset-token";

	@Mock
	private UserService userService;

	@Mock
	private PasswordEncoder passwordEncoder;

	@InjectMocks
	private UserFacadeImpl userFacade;

	@Test
	public void resetPasswordInvalidatesToken() throws Exception {
		User user = new User();
		CredentialsReset reset = new CredentialsReset();
		reset.setCredentialsRequest(TOKEN);
		reset.setCredentialsRequestExpiry(DateUtil.addDaysToCurrentDate(2));
		user.setCredentialsResetRequest(reset);

		when(userService.getByPasswordResetToken(STORE, TOKEN)).thenReturn(user);
		when(passwordEncoder.encode("newPassword")).thenReturn("encoded");

		userFacade.resetPassword("newPassword", TOKEN, STORE);

		assertEquals("encoded", user.getAdminPassword());
		assertNull(user.getCredentialsResetRequest());
		verify(userService).save(user);
	}

	@Test(expected = ResourceNotFoundException.class)
	public void reusedTokenIsNotFound() throws Exception {
		when(userService.getByPasswordResetToken(STORE, TOKEN)).thenReturn(null);

		try {
			userFacade.resetPassword("anotherPassword", TOKEN, STORE);
		} finally {
			verify(userService, never()).save(any(User.class));
		}
	}

	@Test(expected = ResourceNotFoundException.class)
	public void verifyTokenWithoutResetRequestIsNotFound() throws Exception {
		when(userService.getByPasswordResetToken(STORE, TOKEN)).thenReturn(new User());

		userFacade.verifyPasswordRequestToken(TOKEN, STORE);
	}
}
