package com.salesmanager.shop.store.api.v1.category;

import static org.junit.Assert.assertEquals;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.util.Arrays;
import java.util.Collections;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnitRunner;

import com.salesmanager.core.model.merchant.MerchantStore;
import com.salesmanager.core.model.reference.language.Language;
import com.salesmanager.shop.constants.Constants;
import com.salesmanager.shop.store.api.exception.UnauthorizedException;
import com.salesmanager.shop.store.controller.category.facade.CategoryFacade;
import com.salesmanager.shop.store.controller.user.facade.UserFacade;

@RunWith(MockitoJUnitRunner.class)
public class CategoryApiTest {

	@Mock
	private CategoryFacade categoryFacade;

	@Mock
	private UserFacade userFacade;

	@InjectMocks
	private CategoryApi categoryApi;

	private MerchantStore store;
	private Language language;

	@Before
	public void setUp() {
		store = new MerchantStore();
		store.setCode("DEFAULT");
		language = new Language();
		language.setCode("en");
	}

	@Test
	public void publicGetUsesVisibleFacadeLookup() {
		categoryApi.get(42L, store, language);

		verify(categoryFacade).getVisibleById(store, 42L, language);
		verify(categoryFacade, never()).getById(store, 42L, language);
	}

	@Test(expected = UnauthorizedException.class)
	public void privateGetRejectsUnauthenticatedUserBeforeFacadeCall() {
		when(userFacade.authenticatedUser()).thenReturn(null);

		try {
			categoryApi.getPrivate(42L, store, language);
		} finally {
			verifyNoInteractions(categoryFacade);
		}
	}

	@Test(expected = UnauthorizedException.class)
	public void privateGetPropagatesUnauthorizedGroupBeforeFacadeCall() {
		when(userFacade.authenticatedUser()).thenReturn("catalog-admin");
		doThrow(new UnauthorizedException()).when(userFacade).authorizedGroup(eq("catalog-admin"), anyList());

		try {
			categoryApi.getPrivate(42L, store, language);
		} finally {
			verify(categoryFacade, never()).getById(store, 42L, language);
			verify(categoryFacade, never()).getVisibleById(store, 42L, language);
		}
	}

	@Test(expected = UnauthorizedException.class)
	public void privateGetRejectsUserWithoutStoreAccessBeforeFacadeCall() {
		when(userFacade.authenticatedUser()).thenReturn("catalog-admin");
		when(userFacade.userInRoles("catalog-admin", Collections.singletonList(Constants.GROUP_SUPERADMIN)))
				.thenReturn(false);
		when(userFacade.authorizedStore("catalog-admin", "DEFAULT")).thenReturn(false);

		try {
			categoryApi.getPrivate(42L, store, language);
		} finally {
			verify(categoryFacade, never()).getById(store, 42L, language);
			verify(categoryFacade, never()).getVisibleById(store, 42L, language);
			verify(userFacade).authorizedGroup("catalog-admin", allowedGroups());
		}
	}

	@Test
	public void superadminPrivateGetUsesAdminFacadeLookupWithoutStoreCheck() {
		when(userFacade.authenticatedUser()).thenReturn("root");
		when(userFacade.userInRoles("root", Collections.singletonList(Constants.GROUP_SUPERADMIN))).thenReturn(true);

		categoryApi.getPrivate(42L, store, language);

		verify(userFacade).authorizedGroup("root", allowedGroups());
		verify(userFacade, never()).authorizedStore("root", "DEFAULT");
		verify(categoryFacade).getById(store, 42L, language);
		verify(categoryFacade, never()).getVisibleById(store, 42L, language);
	}

	@Test
	public void authorizedStoreAdminPrivateGetUsesAdminFacadeLookup() {
		when(userFacade.authenticatedUser()).thenReturn("catalog-admin");
		when(userFacade.userInRoles("catalog-admin", Collections.singletonList(Constants.GROUP_SUPERADMIN)))
				.thenReturn(false);
		when(userFacade.authorizedStore("catalog-admin", "DEFAULT")).thenReturn(true);

		categoryApi.getPrivate(42L, store, language);

		verify(userFacade).authorizedGroup("catalog-admin", allowedGroups());
		verify(categoryFacade).getById(store, 42L, language);
		verify(categoryFacade, never()).getVisibleById(store, 42L, language);
	}

	private static java.util.List<String> allowedGroups() {
		return Arrays.asList(Constants.GROUP_SUPERADMIN, Constants.GROUP_ADMIN,
				Constants.GROUP_ADMIN_CATALOGUE, Constants.GROUP_ADMIN_RETAIL);
	}
}
