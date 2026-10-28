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
import java.util.concurrent.atomic.AtomicReference;

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

@RunWith(MockitoJUnitRunner.Silent.class)
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

	// token column as stored in the database
	private final AtomicReference<String> storedToken = new AtomicReference<>(TOKEN);

	@Before
	public void setUp() throws Exception {
		user = new User();
		user.setId(1L);
		CredentialsReset reset = new CredentialsReset();
		reset.setCredentialsRequest(TOKEN);
		reset.setCredentialsRequestExpiry(DateUtil.addDaysToCurrentDate(2));
		user.setCredentialsResetRequest(reset);

		when(passwordEncoder.encode(anyString())).thenAnswer(i -> "encoded:" + i.getArgument(0));
		// mirrors UserRepository.findByResetPasswordToken
		when(userService.getByPasswordResetToken(anyString(), anyString()))
				.thenAnswer(i -> i.getArgument(1).equals(storedToken.get()) ? user : null);
		// mirrors UserServiceImpl.resetPassword: conditional UPDATE, then password save
		when(userService.resetPassword(any(User.class), anyString(), anyString())).thenAnswer(i -> {
			if (!storedToken.compareAndSet(i.getArgument(1), null)) {
				return false;
			}
			User target = i.getArgument(0);
			target.setAdminPassword(i.getArgument(2));
			target.setCredentialsResetRequest(null);
			return true;
		});
	}

	@Test
	public void resetPasswordClearsToken() throws Exception {
		userFacade.resetPassword("NewPassword1", TOKEN, STORE);

		assertEquals("encoded:NewPassword1", user.getAdminPassword());
		assertNull(user.getCredentialsResetRequest());
		assertNull(storedToken.get());
		verify(userService).resetPassword(user, TOKEN, "encoded:NewPassword1");
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
		verify(userService, times(1)).resetPassword(any(User.class), anyString(), anyString());
	}

	@Test
	public void concurrentResetWithSameTokenOnlySucceedsOnce() throws Exception {
		// both requests pass verification before either one consumes the token
		when(userService.getByPasswordResetToken(anyString(), anyString())).thenReturn(user);

		userFacade.resetPassword("NewPassword1", TOKEN, STORE);

		try {
			userFacade.resetPassword("AttackerPassword1", TOKEN, STORE);
			fail("Second concurrent reset must be rejected");
		} catch (GenericRuntimeException expected) {
		}

		assertEquals("encoded:NewPassword1", user.getAdminPassword());
		verify(userService, times(2)).resetPassword(any(User.class), anyString(), anyString());
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

		verify(userService, never()).resetPassword(any(User.class), anyString(), anyString());
	}
}
