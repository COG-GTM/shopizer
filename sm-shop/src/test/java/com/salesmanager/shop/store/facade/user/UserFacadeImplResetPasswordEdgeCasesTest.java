package com.salesmanager.shop.store.facade.user;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.Date;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnitRunner;
import org.springframework.security.crypto.password.PasswordEncoder;

import com.salesmanager.core.business.exception.ServiceException;
import com.salesmanager.core.business.services.user.UserService;
import com.salesmanager.core.model.common.CredentialsReset;
import com.salesmanager.core.model.user.User;
import com.salesmanager.shop.store.api.exception.GenericRuntimeException;
import com.salesmanager.shop.store.api.exception.ResourceNotFoundException;
import com.salesmanager.shop.store.api.exception.ServiceRuntimeException;
import com.salesmanager.shop.utils.DateUtil;

@RunWith(MockitoJUnitRunner.class)
public class UserFacadeImplResetPasswordEdgeCasesTest {

	private static final String STORE = "DEFAULT";
	private static final String TOKEN = "reset-token";

	@Mock
	private UserService userService;

	@Mock
	private PasswordEncoder passwordEncoder;

	@InjectMocks
	private UserFacadeImpl userFacade;

	@Test
	public void secondResetWithSameTokenIsRejected() throws Exception {
		User user = userWithToken(DateUtil.addDaysToCurrentDate(2));
		when(userService.getByPasswordResetToken(STORE, TOKEN)).thenReturn(user);
		when(passwordEncoder.encode("firstPassword")).thenReturn("first-encoded");

		userFacade.resetPassword("firstPassword", TOKEN, STORE);

		try {
			userFacade.resetPassword("secondPassword", TOKEN, STORE);
			fail("Expected a reused token to be rejected");
		} catch (ResourceNotFoundException expected) {
			assertTrue(expected.getErrorMessage().startsWith("User not found"));
		}

		assertEquals("first-encoded", user.getAdminPassword());
		verify(userService, times(1)).save(user);
	}

	@Test
	public void verifyAfterResetIsRejected() throws Exception {
		User user = userWithToken(DateUtil.addDaysToCurrentDate(2));
		when(userService.getByPasswordResetToken(STORE, TOKEN)).thenReturn(user);
		when(passwordEncoder.encode("firstPassword")).thenReturn("first-encoded");

		userFacade.resetPassword("firstPassword", TOKEN, STORE);

		try {
			userFacade.verifyPasswordRequestToken(TOKEN, STORE);
			fail("Expected a cleared token to be rejected");
		} catch (ResourceNotFoundException expected) {
			assertTrue(expected.getErrorMessage().startsWith("User not found"));
		}
	}

	@Test
	public void unknownTokenMessageNamesUser() throws Exception {
		when(userService.getByPasswordResetToken(STORE, TOKEN)).thenReturn(null);

		try {
			userFacade.verifyPasswordRequestToken(TOKEN, STORE);
			fail("Expected an unknown token to be rejected");
		} catch (ResourceNotFoundException expected) {
			assertTrue(expected.getErrorMessage().startsWith("User not found"));
		}
	}

	@Test
	public void expiredTokenIsRejectedWithoutSave() throws Exception {
		User user = userWithToken(DateUtil.addDaysToCurrentDate(-2));
		CredentialsReset reset = user.getCredentialsResetRequest();
		when(userService.getByPasswordResetToken(STORE, TOKEN)).thenReturn(user);

		try {
			userFacade.resetPassword("newPassword", TOKEN, STORE);
			fail("Expected an expired token to be rejected");
		} catch (GenericRuntimeException expected) {
			assertSame(reset, user.getCredentialsResetRequest());
		}

		verify(userService, never()).save(any(User.class));
	}

	@Test
	public void missingExpiryIsRejected() throws Exception {
		User user = userWithToken(null);
		CredentialsReset reset = user.getCredentialsResetRequest();
		when(userService.getByPasswordResetToken(STORE, TOKEN)).thenReturn(user);

		try {
			userFacade.verifyPasswordRequestToken(TOKEN, STORE);
			fail("Expected a token without expiry to be rejected");
		} catch (GenericRuntimeException expected) {
			assertSame(reset, user.getCredentialsResetRequest());
			assertNull(reset.getCredentialsRequestExpiry());
		}

		verify(userService, never()).save(any(User.class));
	}

	@Test(expected = ServiceRuntimeException.class)
	public void lookupFailureIsWrappedAsServiceRuntimeException() throws Exception {
		when(userService.getByPasswordResetToken(STORE, TOKEN)).thenThrow(new RuntimeException("lookup failed"));

		userFacade.verifyPasswordRequestToken(TOKEN, STORE);
	}

	@Test(expected = ServiceRuntimeException.class)
	public void saveFailureIsWrapped() throws Exception {
		User user = userWithToken(DateUtil.addDaysToCurrentDate(2));
		when(userService.getByPasswordResetToken(STORE, TOKEN)).thenReturn(user);
		when(passwordEncoder.encode("newPassword")).thenReturn("encoded");
		doThrow(new ServiceException("x")).when(userService).save(user);

		userFacade.resetPassword("newPassword", TOKEN, STORE);
	}

	private User userWithToken(Date expiry) {
		User user = new User();
		CredentialsReset reset = new CredentialsReset();
		reset.setCredentialsRequest(TOKEN);
		reset.setCredentialsRequestExpiry(expiry);
		user.setCredentialsResetRequest(reset);
		return user;
	}
}
