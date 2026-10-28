package com.salesmanager.test.shop.unit.user;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import javax.servlet.http.HttpServletRequest;

import com.salesmanager.core.model.merchant.MerchantStore;
import com.salesmanager.core.model.reference.language.Language;
import com.salesmanager.shop.constants.Constants;
import com.salesmanager.shop.model.user.PersistableUser;
import com.salesmanager.shop.store.api.exception.UnauthorizedException;
import com.salesmanager.shop.store.api.v1.user.UserApi;
import com.salesmanager.shop.store.controller.user.facade.UserFacade;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnitRunner;

@RunWith(MockitoJUnitRunner.class)
public class UserApiStoreAuthorizationTest {

	private static final String STORE_CODE = "STORE_A";
	private static final String ADMIN_USER = "admin@store-a.com";

	@Mock
	private UserFacade userFacade;

	@InjectMocks
	private UserApi userApi;

	private MerchantStore store;

	@Before
	public void setUp() {
		store = new MerchantStore();
		store.setCode(STORE_CODE);
		when(userFacade.authenticatedUser()).thenReturn(ADMIN_USER);
		when(userFacade.userInRoles(eq(ADMIN_USER), any())).thenReturn(false);
	}

	@Test
	public void deleteRejectsNonSuperadminOutsideStore() {
		when(userFacade.authorizedStore(ADMIN_USER, STORE_CODE)).thenReturn(false);

		assertThrows(UnauthorizedException.class,
				() -> userApi.delete(store, new Language(), 12L, (HttpServletRequest) null));

		verify(userFacade, never()).delete(12L, STORE_CODE);
	}

	@Test
	public void deleteAllowsAuthorizedNonSuperadmin() {
		when(userFacade.authorizedStore(ADMIN_USER, STORE_CODE)).thenReturn(true);

		userApi.delete(store, new Language(), 12L, (HttpServletRequest) null);

		verify(userFacade).delete(12L, STORE_CODE);
	}

	@Test
	public void deleteSkipsStoreCheckForSuperadmin() {
		when(userFacade.userInRoles(eq(ADMIN_USER), any())).thenReturn(true);

		userApi.delete(store, new Language(), 12L, (HttpServletRequest) null);

		verify(userFacade, never()).authorizedStore(any(), any());
		verify(userFacade).delete(12L, STORE_CODE);
	}

	@Test
	public void deleteRejectsMissingAuthenticatedUser() {
		when(userFacade.authenticatedUser()).thenReturn(null);

		assertThrows(UnauthorizedException.class,
				() -> userApi.delete(store, new Language(), 12L, (HttpServletRequest) null));

		verify(userFacade, never()).delete(12L, STORE_CODE);
	}

	@Test
	public void updateEnabledRejectsNonSuperadminOutsideStore() {
		when(userFacade.authorizedStore(ADMIN_USER, STORE_CODE)).thenReturn(false);

		assertThrows(UnauthorizedException.class,
				() -> userApi.updateEnabled(12L, new PersistableUser(), store));

		verify(userFacade, never()).updateEnabled(any(), any());
	}

	@Test
	public void updateEnabledAllowsAuthorizedNonSuperadminAndSetsUserId() {
		PersistableUser user = new PersistableUser();
		when(userFacade.authorizedStore(ADMIN_USER, STORE_CODE)).thenReturn(true);

		userApi.updateEnabled(12L, user, store);

		assertEquals(Long.valueOf(12L), user.getId());
		verify(userFacade).updateEnabled(store, user);
	}

	@Test
	public void updateEnabledSkipsStoreCheckForSuperadmin() {
		when(userFacade.userInRoles(eq(ADMIN_USER), any())).thenReturn(true);
		PersistableUser user = new PersistableUser();

		userApi.updateEnabled(12L, user, store);

		verify(userFacade, never()).authorizedStore(any(), any());
		verify(userFacade).updateEnabled(store, user);
	}

	@Test
	public void updateEnabledRejectsMissingAuthenticatedUser() {
		when(userFacade.authenticatedUser()).thenReturn(null);

		assertThrows(UnauthorizedException.class,
				() -> userApi.updateEnabled(12L, new PersistableUser(), store));

		verify(userFacade, never()).updateEnabled(any(), any());
	}
}
