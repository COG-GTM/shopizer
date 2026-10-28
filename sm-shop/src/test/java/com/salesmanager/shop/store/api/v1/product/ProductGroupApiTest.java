package com.salesmanager.shop.store.api.v1.product;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.fail;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.util.Arrays;
import java.util.HashSet;
import java.util.List;

import javax.servlet.http.HttpServletResponse;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnitRunner;
import org.springframework.web.bind.annotation.DeleteMapping;

import com.salesmanager.core.business.services.catalog.product.ProductService;
import com.salesmanager.core.model.catalog.product.Product;
import com.salesmanager.core.model.merchant.MerchantStore;
import com.salesmanager.core.model.reference.language.Language;
import com.salesmanager.shop.constants.Constants;
import com.salesmanager.shop.model.catalog.product.ReadableProductList;
import com.salesmanager.shop.model.catalog.product.group.ProductGroup;
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

	@Mock
	private HttpServletResponse response;

	@InjectMocks
	private ProductGroupApi productGroupApi;

	private final MerchantStore store = new MerchantStore();

	private final Language language = new Language();

	@Test
	public void deleteGroupIsMappedUnderPrivateApi() throws Exception {
		DeleteMapping mapping = ProductGroupApi.class.getMethod("deleteGroup", String.class, MerchantStore.class,
				Language.class, HttpServletResponse.class).getAnnotation(DeleteMapping.class);
		assertEquals(Arrays.asList("/private/products/group/{code}"), Arrays.asList(mapping.value()));
	}

	@Test
	public void deleteGroupRejectsAnonymousCaller() {
		when(userFacade.authenticatedUser()).thenReturn(null);
		try {
			productGroupApi.deleteGroup("FEATURED_ITEM", store, language, response);
			fail("Expected UnauthorizedException");
		} catch (UnauthorizedException expected) {
			verifyNoInteractions(productItemsFacade);
		}
	}

	@Test
	public void deleteGroupRejectsUserOutsideCatalogueGroups() {
		when(userFacade.authenticatedUser()).thenReturn("order@shop.com");
		doThrow(new UnauthorizedException()).when(userFacade).authorizedGroup(eq("order@shop.com"), anyList());
		try {
			productGroupApi.deleteGroup("FEATURED_ITEM", store, language, response);
			fail("Expected UnauthorizedException");
		} catch (UnauthorizedException expected) {
			verify(productItemsFacade, never()).deleteGroup(anyString(), any(MerchantStore.class));
		}
	}

	@SuppressWarnings({ "unchecked", "rawtypes" })
	@Test
	public void deleteGroupAllowsCatalogueAdministrators() {
		when(userFacade.authenticatedUser()).thenReturn("admin@shop.com");
		ArgumentCaptor<List> groups = ArgumentCaptor.forClass(List.class);

		productGroupApi.deleteGroup("FEATURED_ITEM", store, language, response);

		verify(userFacade).authorizedGroup(eq("admin@shop.com"), groups.capture());
		assertEquals(new HashSet<>(Arrays.asList(Constants.GROUP_SUPERADMIN, Constants.GROUP_ADMIN,
				Constants.GROUP_ADMIN_CATALOGUE, Constants.GROUP_ADMIN_RETAIL)), new HashSet<>(groups.getValue()));
		verify(productItemsFacade).deleteGroup("FEATURED_ITEM", store);
	}

	@Test
	public void createAndUpdateGroupRequireCatalogueAdministrator() throws Exception {
		when(userFacade.authenticatedUser()).thenReturn(null);
		try {
			productGroupApi.creteGroup(new ProductGroup(), store, language, response);
			fail("Expected UnauthorizedException");
		} catch (UnauthorizedException expected) {
		}
		try {
			productGroupApi.updateGroup(new ProductGroup(), "FEATURED_ITEM", store, language, response);
			fail("Expected UnauthorizedException");
		} catch (UnauthorizedException expected) {
		}
		verifyNoInteractions(productItemsFacade);
	}

	@Test
	public void addProductToGroupLooksUpProductInCallerStore() throws Exception {
		when(userFacade.authenticatedUser()).thenReturn("admin@shop.com");
		Product product = new Product();
		ReadableProductList list = new ReadableProductList();
		when(productService.findOne(5L, store)).thenReturn(product);
		when(productItemsFacade.addItemToGroup(product, "FEATURED_ITEM", store, language)).thenReturn(list);

		assertEquals(list, productGroupApi.addProductToGroup(5L, "FEATURED_ITEM", store, language, response));
		verify(productService, never()).getById(anyLong());
	}

	@Test
	public void addProductToGroupReturns404ForOtherStoresProduct() throws Exception {
		when(userFacade.authenticatedUser()).thenReturn("admin@shop.com");
		when(productService.findOne(99L, store)).thenReturn(null);

		assertNull(productGroupApi.addProductToGroup(99L, "FEATURED_ITEM", store, language, response));
		verify(response).sendError(eq(404), anyString());
		verify(productService, never()).getById(anyLong());
		verifyNoInteractions(productItemsFacade);
	}

	@Test
	public void removeProductFromGroupReturns404ForOtherStoresProduct() throws Exception {
		when(userFacade.authenticatedUser()).thenReturn("admin@shop.com");
		when(productService.findOne(99L, store)).thenReturn(null);

		assertNull(productGroupApi.removeProductFromGroup(99L, "FEATURED_ITEM", store, language, null, response));
		verify(response).sendError(eq(404), anyString());
		verify(productService, never()).getById(anyLong());
		verifyNoInteractions(productItemsFacade);
	}

	@Test
	public void addAndRemoveProductRequireCatalogueAdministrator() {
		when(userFacade.authenticatedUser()).thenReturn(null);
		try {
			productGroupApi.addProductToGroup(5L, "FEATURED_ITEM", store, language, response);
			fail("Expected UnauthorizedException");
		} catch (UnauthorizedException expected) {
		}
		try {
			productGroupApi.removeProductFromGroup(5L, "FEATURED_ITEM", store, language, null, response);
			fail("Expected UnauthorizedException");
		} catch (UnauthorizedException expected) {
		}
		verifyNoInteractions(productService, productItemsFacade);
	}
}
