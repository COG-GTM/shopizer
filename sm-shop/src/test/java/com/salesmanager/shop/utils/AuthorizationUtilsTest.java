package com.salesmanager.shop.utils;

import static org.junit.Assert.assertTrue;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.Arrays;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnitRunner;
import org.springframework.test.util.ReflectionTestUtils;

import com.salesmanager.core.model.merchant.MerchantStore;
import com.salesmanager.shop.constants.Constants;
import com.salesmanager.shop.store.api.exception.UnauthorizedException;
import com.salesmanager.shop.store.controller.user.facade.UserFacade;

@RunWith(MockitoJUnitRunner.class)
public class AuthorizationUtilsTest {

	private static final String USER = "admin@acme.com";
	private static final String STORE = "ACME";

	@Mock
	private UserFacade userFacade;

	private AuthorizationUtils authorizationUtils;

	@Before
	public void setUp() {
		authorizationUtils = new AuthorizationUtils();
		ReflectionTestUtils.setField(authorizationUtils, "userFacade", userFacade);
	}

	@Test(expected = UnauthorizedException.class)
	public void groupFailurePropagatesWithoutCheckingStore() {
		doThrow(new UnauthorizedException()).when(userFacade).authorizedGroup(eq(USER), anyList());

		try {
			authorizationUtils.authorizeUser(USER, Arrays.asList(Constants.GROUP_ADMIN), STORE);
		} finally {
			verify(userFacade, never()).authorizedStore(USER, STORE);
		}
	}

	@Test
	public void superadminDoesNotNeedStoreMembership() {
		when(userFacade.userInRoles(USER, Arrays.asList(Constants.GROUP_SUPERADMIN))).thenReturn(true);

		authorizationUtils.authorizeUser(USER, Arrays.asList(Constants.GROUP_ADMIN), STORE);

		verify(userFacade).authorizedGroup(eq(USER), anyList());
		verify(userFacade, never()).authorizedStore(USER, STORE);
	}

	@Test
	public void rejectsUserNotAttachedToStore() {
		when(userFacade.userInRoles(USER, Arrays.asList(Constants.GROUP_SUPERADMIN))).thenReturn(false);
		when(userFacade.authorizedStore(USER, STORE)).thenReturn(false);

		try {
			authorizationUtils.authorizeUser(USER, Arrays.asList(Constants.GROUP_ADMIN), STORE);
		} catch (UnauthorizedException exception) {
			assertTrue(exception.getErrorMessage().contains("store [ACME]"));
			return;
		}
		throw new AssertionError("Expected UnauthorizedException");
	}

	@Test
	public void allowsUserAttachedToStore() {
		when(userFacade.userInRoles(USER, Arrays.asList(Constants.GROUP_SUPERADMIN))).thenReturn(false);
		when(userFacade.authorizedStore(USER, STORE)).thenReturn(true);

		authorizationUtils.authorizeUser(USER, Arrays.asList(Constants.GROUP_ADMIN), STORE);

		verify(userFacade).authorizedStore(USER, STORE);
	}

	@Test
	public void merchantStoreOverloadDelegatesUsingStoreCode() {
		MerchantStore store = new MerchantStore();
		store.setCode(STORE);
		when(userFacade.userInRoles(USER, Arrays.asList(Constants.GROUP_SUPERADMIN))).thenReturn(false);
		when(userFacade.authorizedStore(USER, STORE)).thenReturn(true);

		authorizationUtils.authorizeUser(USER, Arrays.asList(Constants.GROUP_ADMIN), store);

		verify(userFacade).authorizedStore(USER, STORE);
	}
}
