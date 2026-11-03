package com.salesmanager.shop.store.api.v1.store;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.security.Principal;
import java.util.Arrays;
import java.util.List;

import javax.servlet.http.HttpServletRequest;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnitRunner;
import org.springframework.test.util.ReflectionTestUtils;

import com.salesmanager.shop.constants.Constants;
import com.salesmanager.shop.model.store.PersistableMerchantStore;
import com.salesmanager.shop.store.api.exception.UnauthorizedException;
import com.salesmanager.shop.store.controller.store.facade.StoreFacade;
import com.salesmanager.shop.store.controller.user.facade.UserFacade;
import com.salesmanager.shop.utils.AuthorizationUtils;

import static org.junit.Assert.assertEquals;

@RunWith(MockitoJUnitRunner.class)
public class MerchantStoreApiAuthorizationTest {

	private static final String STORE = "ACME";
	private static final String USER = "bob@acme.com";

	@Mock
	private UserFacade userFacade;

	@Mock
	private StoreFacade storeFacade;

	@Mock
	private HttpServletRequest request;

	private MerchantStoreApi api;

	@Before
	public void setUp() {
		AuthorizationUtils authorizationUtils = new AuthorizationUtils();
		ReflectionTestUtils.setField(authorizationUtils, "userFacade", userFacade);

		api = new MerchantStoreApi();
		ReflectionTestUtils.setField(api, "userFacade", userFacade);
		ReflectionTestUtils.setField(api, "storeFacade", storeFacade);
		ReflectionTestUtils.setField(api, "authorizationUtils", authorizationUtils);

		Principal principal = mock(Principal.class);
		when(principal.getName()).thenReturn(USER);
		when(request.getUserPrincipal()).thenReturn(principal);
	}

	private void nonAdminAttachedToStore() {
		doThrow(new UnauthorizedException("User " + USER + " not authorized")).when(userFacade)
				.authorizedGroup(eq(USER), anyList());
	}

	@Test(expected = UnauthorizedException.class)
	public void deleteRejectsNonAdminStoreMember() {
		nonAdminAttachedToStore();
		try {
			api.delete(STORE, request);
		} finally {
			verify(storeFacade, never()).delete(any());
		}
	}

	@Test(expected = UnauthorizedException.class)
	public void updateRejectsNonAdminStoreMember() {
		nonAdminAttachedToStore();
		try {
			api.update(STORE, new PersistableMerchantStore(), request);
		} finally {
			verify(storeFacade, never()).update(any());
		}
	}

	@Test(expected = UnauthorizedException.class)
	public void deleteLogoRejectsNonAdminStoreMember() {
		nonAdminAttachedToStore();
		try {
			api.deleteStoreLogo(STORE, request);
		} finally {
			verify(storeFacade, never()).deleteLogo(any());
		}
	}

	@Test(expected = UnauthorizedException.class)
	public void deleteRejectsAdminOfAnotherStore() {
		when(userFacade.userInRoles(eq(USER), anyList())).thenReturn(false);
		when(userFacade.authorizedStore(USER, STORE)).thenReturn(false);
		try {
			api.delete(STORE, request);
		} finally {
			verify(storeFacade, never()).delete(any());
		}
	}

	@SuppressWarnings("unchecked")
	@Test
	public void deleteAllowedForStoreAdmin() {
		when(userFacade.userInRoles(eq(USER), anyList())).thenReturn(false);
		when(userFacade.authorizedStore(USER, STORE)).thenReturn(true);

		api.delete(STORE, request);

		ArgumentCaptor<List<String>> groups = ArgumentCaptor.forClass(List.class);
		verify(userFacade).authorizedGroup(eq(USER), groups.capture());
		assertEquals(Arrays.asList(Constants.GROUP_SUPERADMIN, Constants.GROUP_ADMIN, Constants.GROUP_ADMIN_RETAIL),
				groups.getValue());
		verify(storeFacade).delete(STORE);
	}
}
