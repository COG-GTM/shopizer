package com.salesmanager.test.shop.unit.facade;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.fail;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.Date;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnitRunner;
import org.springframework.security.crypto.password.PasswordEncoder;

import com.salesmanager.core.business.services.user.UserService;
import com.salesmanager.core.model.common.CredentialsReset;
import com.salesmanager.core.model.user.User;
import com.salesmanager.shop.store.api.exception.GenericRuntimeException;
import com.salesmanager.shop.store.api.exception.ServiceRuntimeException;
import com.salesmanager.shop.store.facade.user.UserFacadeImpl;
import com.salesmanager.shop.utils.DateUtil;

@RunWith(MockitoJUnitRunner.class)
public class UserPasswordResetTokenTest {

	private static final String STORE = "DEFAULT";
	private static final String TOKEN = "3f1c2b7e-1111-2222-3333-444455556666";

	@Mock
	private UserService userService;

	@Mock
	private PasswordEncoder passwordEncoder;

	@InjectMocks
	private UserFacadeImpl userFacade;

	private User user;

	@Before
	public void setUp() throws Exception {
		user = new User();
		user.setId(1L);
		CredentialsReset reset = new CredentialsReset();
		reset.setCredentialsRequest(TOKEN);
		reset.setCredentialsRequestExpiry(DateUtil.addDaysToCurrentDate(2));
		user.setCredentialsResetRequest(reset);

		when(passwordEncoder.encode(anyString())).thenAnswer(i -> "encoded:" + i.getArgument(0));
		// mirrors UserRepository.findByResetPasswordToken: match on the stored token
		when(userService.getByPasswordResetToken(anyString(), anyString())).thenAnswer(i -> {
			CredentialsReset stored = user.getCredentialsResetRequest();
			if (stored != null && i.getArgument(1).equals(stored.getCredentialsRequest())) {
				return user;
			}
			return null;
		});
	}

	@Test
	public void resetPasswordClearsToken() throws Exception {
		userFacade.resetPassword("NewPassword1", TOKEN, STORE);

		assertEquals("encoded:NewPassword1", user.getAdminPassword());
		assertNull(user.getCredentialsResetRequest());
		verify(userService).save(user);
	}

	@Test
	public void resetTokenCannotBeReplayed() throws Exception {
		userFacade.resetPassword("NewPassword1", TOKEN, STORE);

		try {
			userFacade.resetPassword("AttackerPassword1", TOKEN, STORE);
			fail("Replayed reset token must be rejected");
		} catch (ServiceRuntimeException expected) {
			// token no longer resolves to a user
		}

		assertEquals("encoded:NewPassword1", user.getAdminPassword());
		verify(userService, times(1)).save(any(User.class));
	}

	@Test
	public void usedTokenFailsVerification() throws Exception {
		userFacade.resetPassword("NewPassword1", TOKEN, STORE);

		try {
			userFacade.verifyPasswordRequestToken(TOKEN, STORE);
			fail("Used reset token must not verify");
		} catch (ServiceRuntimeException expected) {
		}
	}

	@Test
	public void expiredTokenIsRejected() throws Exception {
		user.getCredentialsResetRequest().setCredentialsRequestExpiry(new Date(System.currentTimeMillis() - 1000L));

		try {
			userFacade.resetPassword("NewPassword1", TOKEN, STORE);
			fail("Expired reset token must be rejected");
		} catch (GenericRuntimeException expected) {
		}

		verify(userService, never()).save(any(User.class));
	}
}
