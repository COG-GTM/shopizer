package com.salesmanager.shop.store.api.v1.product;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import javax.servlet.http.HttpServletResponse;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnitRunner;

import com.salesmanager.core.business.services.catalog.product.ProductService;
import com.salesmanager.core.model.merchant.MerchantStore;
import com.salesmanager.core.model.reference.language.Language;
import com.salesmanager.shop.constants.Constants;
import com.salesmanager.shop.store.api.exception.UnauthorizedException;
import com.salesmanager.shop.store.controller.items.facade.ProductItemsFacade;
import com.salesmanager.shop.store.controller.user.facade.UserFacade;

@RunWith(MockitoJUnitRunner.class)
public class ProductGroupApiTest {

	@Mock
	private UserFacade userFacade;

	@Mock
	private ProductItemsFacade productItemsFacade;

	@Mock
	private ProductService productService;

	@InjectMocks
	private ProductGroupApi productGroupApi;

	@Test
	public void deleteGroupRejectsMissingAuthenticatedUser() {
		when(userFacade.authenticatedUser()).thenReturn(null);

		try {
			productGroupApi.deleteGroup("GROUP", new MerchantStore(), new Language(), null);
			fail("Expected UnauthorizedException");
		} catch (UnauthorizedException expected) {
			verify(userFacade, never()).authorizedGroup(org.mockito.ArgumentMatchers.anyString(),
					org.mockito.ArgumentMatchers.anyList());
			verify(productItemsFacade, never()).deleteGroup(org.mockito.ArgumentMatchers.anyString(),
					org.mockito.ArgumentMatchers.any(MerchantStore.class));
		}
	}

	@Test
	public void deleteGroupPropagatesAuthenticationFailure() {
		when(userFacade.authenticatedUser()).thenThrow(new UnauthorizedException());

		try {
			productGroupApi.deleteGroup("GROUP", new MerchantStore(), new Language(), null);
			fail("Expected UnauthorizedException");
		} catch (UnauthorizedException expected) {
			verify(productItemsFacade, never()).deleteGroup(org.mockito.ArgumentMatchers.anyString(),
					org.mockito.ArgumentMatchers.any(MerchantStore.class));
		}
	}

	@Test
	public void deleteGroupPropagatesAuthorizationFailure() {
		when(userFacade.authenticatedUser()).thenReturn("user");
		org.mockito.Mockito.doThrow(new UnauthorizedException()).when(userFacade).authorizedGroup(
				org.mockito.ArgumentMatchers.eq("user"), org.mockito.ArgumentMatchers.anyList());

		try {
			productGroupApi.deleteGroup("GROUP", new MerchantStore(), new Language(), null);
			fail("Expected UnauthorizedException");
		} catch (UnauthorizedException expected) {
			verify(productItemsFacade, never()).deleteGroup(org.mockito.ArgumentMatchers.anyString(),
					org.mockito.ArgumentMatchers.any(MerchantStore.class));
		}
	}

	@Test
	public void deleteGroupAuthorizesOnlyProductManagementGroups() {
		MerchantStore store = new MerchantStore();
		when(userFacade.authenticatedUser()).thenReturn("user");
		ArgumentCaptor<List<String>> groupsCaptor = ArgumentCaptor.forClass(List.class);

		productGroupApi.deleteGroup("GROUP", store, new Language(), null);

		verify(userFacade).authorizedGroup(org.mockito.ArgumentMatchers.eq("user"), groupsCaptor.capture());
		Set<String> groups = new HashSet<>(groupsCaptor.getValue());
		assertEquals(4, groups.size());
		assertEquals(new HashSet<>(Arrays.asList(Constants.GROUP_SUPERADMIN, Constants.GROUP_ADMIN,
				Constants.GROUP_ADMIN_CATALOGUE, Constants.GROUP_ADMIN_RETAIL)), groups);
		assertFalse(groups.contains(Constants.GROUP_ADMIN_ORDER));
		assertFalse(groups.contains(Constants.GROUP_CUSTOMER));
		assertFalse(groups.contains(Constants.GROUP_SHIPPING));
		verify(productItemsFacade, times(1)).deleteGroup("GROUP", store);
		assertTrue(groups.contains(Constants.GROUP_ADMIN_CATALOGUE));
	}
}
