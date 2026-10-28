package com.salesmanager.test.user;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.lang.reflect.Method;
import java.util.Date;

import javax.persistence.Temporal;
import javax.persistence.TemporalType;

import org.junit.Before;
import org.junit.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.InOrder;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.transaction.annotation.Transactional;

import com.salesmanager.core.business.repositories.user.UserRepository;
import com.salesmanager.core.business.services.user.UserServiceImpl;
import com.salesmanager.core.model.common.CredentialsReset;

/**
 * Unit tests (repository mocked) for the single-use password reset added to
 * UserService / UserRepository.
 */
public class UserServiceImplResetPasswordTest {

	private static final Long USER_ID = 42L;
	private static final String TOKEN = "3f1c1b4e-token";
	private static final String ENCODED = "{bcrypt}encoded";

	private UserRepository userRepository;
	private UserServiceImpl userService;

	@Before
	public void setUp() {
		userRepository = mock(UserRepository.class);
		userService = new UserServiceImpl(userRepository);
	}

	@Test
	public void clearsTokenThenUpdatesPasswordWhenExactlyOneRowCleared() {
		when(userRepository.clearResetPasswordToken(USER_ID, TOKEN)).thenReturn(1);

		long before = System.currentTimeMillis();
		assertTrue(userService.resetPasswordWithToken(USER_ID, TOKEN, ENCODED));

		InOrder order = inOrder(userRepository);
		order.verify(userRepository).clearResetPasswordToken(USER_ID, TOKEN);
		ArgumentCaptor<Date> modified = ArgumentCaptor.forClass(Date.class);
		order.verify(userRepository).updateAdminPassword(eq(USER_ID), eq(ENCODED), modified.capture());
		assertNotNull(modified.getValue());
		assertTrue(modified.getValue().getTime() >= before);
	}

	@Test
	public void doesNotTouchPasswordWhenTokenAlreadyConsumed() {
		when(userRepository.clearResetPasswordToken(USER_ID, TOKEN)).thenReturn(0);

		assertFalse(userService.resetPasswordWithToken(USER_ID, TOKEN, ENCODED));
		verify(userRepository, never()).updateAdminPassword(anyLong(), anyString(), any(Date.class));
	}

	@Test
	public void doesNotTouchPasswordWhenMoreThanOneRowAffected() {
		when(userRepository.clearResetPasswordToken(USER_ID, TOKEN)).thenReturn(2);

		assertFalse(userService.resetPasswordWithToken(USER_ID, TOKEN, ENCODED));
		verify(userRepository, never()).updateAdminPassword(anyLong(), anyString(), any(Date.class));
	}

	@Test
	public void resetPasswordWithTokenIsTransactional() throws Exception {
		Method method = UserServiceImpl.class.getMethod("resetPasswordWithToken", Long.class, String.class,
				String.class);
		assertNotNull(method.getAnnotation(Transactional.class));
	}

	@Test
	public void repositoryUpdatesAreModifyingQueriesGuardedByToken() throws Exception {
		Method clear = UserRepository.class.getMethod("clearResetPasswordToken", Long.class, String.class);
		assertNotNull(clear.getAnnotation(Modifying.class));
		assertTrue(clear.getAnnotation(Modifying.class).clearAutomatically());
		String clearJpql = clear.getAnnotation(Query.class).value();
		assertTrue(clearJpql, clearJpql.contains("u.id = ?1"));
		assertTrue(clearJpql, clearJpql.contains("credentialsRequest = ?2"));

		Method update = UserRepository.class.getMethod("updateAdminPassword", Long.class, String.class, Date.class);
		assertNotNull(update.getAnnotation(Modifying.class));
		assertTrue(update.getAnnotation(Query.class).value().contains("u.adminPassword = ?2"));
	}

	@Test
	public void resetExpiryIsMappedAsTimestamp() throws Exception {
		Temporal temporal = CredentialsReset.class.getDeclaredField("credentialsRequestExpiry")
				.getAnnotation(Temporal.class);
		assertNotNull(temporal);
		assertEquals(TemporalType.TIMESTAMP, temporal.value());
	}
}
