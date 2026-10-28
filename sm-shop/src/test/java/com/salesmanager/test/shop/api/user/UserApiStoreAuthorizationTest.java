package com.salesmanager.test.shop.api.user;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertThrows;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnitRunner;

import com.salesmanager.core.model.merchant.MerchantStore;
import com.salesmanager.shop.model.user.PersistableUser;
import com.salesmanager.shop.store.api.exception.UnauthorizedException;
import com.salesmanager.shop.store.api.v1.user.UserApi;
import com.salesmanager.shop.store.controller.user.facade.UserFacade;

@RunWith(MockitoJUnitRunner.class)
public class UserApiStoreAuthorizationTest {

	@Mock
	private UserFacade userFacade;

	@InjectMocks
	private UserApi userApi;

	private MerchantStore store;

	@Before
	public void setUp() {
		store = new MerchantStore();
		store.setCode("DEFAULT");
	}

	@Test
	public void missingAuthenticatedUserCannotDeleteOrUpdate() {
		when(userFacade.authenticatedUser()).thenReturn(null);

		assertThrows(UnauthorizedException.class, () -> userApi.delete(store, null, 7L, null));
		assertThrows(UnauthorizedException.class, () -> userApi.updateEnabled(7L, new PersistableUser(), store));

		verify(userFacade, never()).delete(7L, "DEFAULT");
		verify(userFacade, never()).updateEnabled(org.mockito.ArgumentMatchers.eq(store),
				org.mockito.ArgumentMatchers.any(PersistableUser.class));
	}

	@Test
	public void unauthorizedStoreCannotDeleteOrUpdate() {
		when(userFacade.authenticatedUser()).thenReturn("bob");
		when(userFacade.userInRoles(eq("bob"), anyList())).thenReturn(false);
		when(userFacade.authorizedStore("bob", "DEFAULT")).thenReturn(false);

		assertThrows(UnauthorizedException.class, () -> userApi.delete(store, null, 7L, null));
		assertThrows(UnauthorizedException.class, () -> userApi.updateEnabled(7L, new PersistableUser(), store));

		verify(userFacade, never()).delete(7L, "DEFAULT");
		verify(userFacade, never()).updateEnabled(org.mockito.ArgumentMatchers.eq(store),
				org.mockito.ArgumentMatchers.any(PersistableUser.class));
	}

	@Test
	public void authorizedStoreAllowsDeleteAndUpdate() {
		when(userFacade.authenticatedUser()).thenReturn("bob");
		when(userFacade.userInRoles(eq("bob"), anyList())).thenReturn(false);
		when(userFacade.authorizedStore("bob", "DEFAULT")).thenReturn(true);

		userApi.delete(store, null, 7L, null);
		PersistableUser user = new PersistableUser();
		userApi.updateEnabled(7L, user, store);

		assertEquals(Long.valueOf(7L), user.getId());
		verify(userFacade).delete(7L, "DEFAULT");
		verify(userFacade).updateEnabled(store, user);
	}

	@Test
	public void superadminCanDeleteWithoutStoreAuthorization() {
		when(userFacade.authenticatedUser()).thenReturn("root");
		when(userFacade.userInRoles(eq("root"), anyList())).thenReturn(true);

		userApi.delete(store, null, 7L, null);

		verify(userFacade, never()).authorizedStore("root", "DEFAULT");
		verify(userFacade).delete(7L, "DEFAULT");
	}

	@Test
	public void unauthorizedGroupFailurePropagates() {
		when(userFacade.authenticatedUser()).thenReturn("bob");
		doThrow(new UnauthorizedException()).when(userFacade).authorizedGroup(eq("bob"), anyList());

		assertThrows(UnauthorizedException.class, () -> userApi.delete(store, null, 7L, null));

		verify(userFacade, never()).delete(7L, "DEFAULT");
	}
}
