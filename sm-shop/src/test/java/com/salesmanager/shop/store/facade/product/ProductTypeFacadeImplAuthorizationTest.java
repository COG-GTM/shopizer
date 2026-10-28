package com.salesmanager.shop.store.facade.product;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.fail;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.Collections;
import java.util.List;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnitRunner;

import com.salesmanager.core.business.exception.ServiceException;
import com.salesmanager.core.business.services.catalog.product.type.ProductTypeService;
import com.salesmanager.core.model.catalog.product.type.ProductType;
import com.salesmanager.core.model.merchant.MerchantStore;
import com.salesmanager.core.model.reference.language.Language;
import com.salesmanager.shop.constants.Constants;
import com.salesmanager.shop.mapper.catalog.PersistableProductTypeMapper;
import com.salesmanager.shop.mapper.catalog.ReadableProductTypeMapper;
import com.salesmanager.shop.model.catalog.product.type.PersistableProductType;
import com.salesmanager.shop.model.catalog.product.type.ReadableProductType;
import com.salesmanager.shop.store.api.exception.ResourceNotFoundException;
import com.salesmanager.shop.store.api.exception.ServiceRuntimeException;
import com.salesmanager.shop.store.api.exception.UnauthorizedException;
import com.salesmanager.shop.store.controller.user.facade.UserFacade;

@RunWith(MockitoJUnitRunner.Silent.class)
public class ProductTypeFacadeImplAuthorizationTest {

	private static final Long TYPE_ID = 42L;
	private static final String ADMIN = "admin@example.test";

	@Mock
	private ProductTypeService productTypeService;

	@Mock
	private ReadableProductTypeMapper readableProductTypeMapper;

	@Mock
	private PersistableProductTypeMapper persistableProductTypeMapper;

	@Mock
	private UserFacade userFacade;

	@InjectMocks
	private ProductTypeFacadeImpl facade;

	private MerchantStore defaultStore;
	private MerchantStore otherStore;
	private Language language;

	@Before
	public void setUp() {
		defaultStore = store(1, "DEFAULT");
		otherStore = store(3, "OTHER");
		language = new Language();
		when(userFacade.authenticatedUser()).thenReturn(ADMIN);
		when(persistableProductTypeMapper.merge(any(), any(), any(), any()))
				.thenAnswer(invocation -> invocation.getArgument(1));
	}

	@Test
	public void storeAdminCannotMergeGlobalType() throws Exception {
		ProductType shared = type(null);
		when(productTypeService.getById(TYPE_ID, defaultStore, language)).thenReturn(shared);
		when(userFacade.userInRoles(eq(ADMIN), anyList())).thenReturn(false);

		try {
			facade.update(persistable(), TYPE_ID, defaultStore, language);
			fail("Expected a global type update to be rejected");
		} catch (UnauthorizedException expected) {
			assertEquals(TYPE_ID, shared.getId());
			assertEquals(ProductType.GENERAL_TYPE, shared.getCode());
			assertNull(shared.getMerchantStore());
		}

		verify(persistableProductTypeMapper, never()).merge(any(), any(), any(), any());
		verify(productTypeService, never()).saveOrUpdate(any());
	}

	@Test
	public void missingAuthenticatedUserCannotUpdateOrDeleteGlobalType() throws Exception {
		when(userFacade.authenticatedUser()).thenReturn(null);
		when(productTypeService.getById(TYPE_ID, defaultStore, language)).thenReturn(type(null));

		try {
			facade.update(persistable(), TYPE_ID, defaultStore, language);
			fail("Expected an unauthenticated update to be rejected");
		} catch (UnauthorizedException expected) {
			// Expected.
		}

		try {
			facade.delete(TYPE_ID, defaultStore, language);
			fail("Expected an unauthenticated delete to be rejected");
		} catch (UnauthorizedException expected) {
			// Expected.
		}

		verify(userFacade, never()).userInRoles(any(), anyList());
		verify(productTypeService, never()).saveOrUpdate(any());
		verify(productTypeService, never()).delete(any());
	}

	@Test
	public void superAdminCheckUsesOnlyTheSuperAdminRole() throws Exception {
		ProductType shared = type(null);
		when(productTypeService.getById(TYPE_ID, defaultStore, language)).thenReturn(shared);
		when(userFacade.userInRoles(eq(ADMIN), anyList())).thenReturn(true);

		facade.update(persistable(), TYPE_ID, defaultStore, language);

		ArgumentCaptor<List<String>> roles = ArgumentCaptor.forClass(List.class);
		verify(userFacade).userInRoles(eq(ADMIN), roles.capture());
		assertEquals(Collections.singletonList(Constants.GROUP_SUPERADMIN), roles.getValue());
	}

	@Test(expected = ResourceNotFoundException.class)
	public void superAdminCannotUpdateAnotherStoresType() throws Exception {
		when(productTypeService.getById(TYPE_ID, defaultStore, language)).thenReturn(type(otherStore));
		when(userFacade.userInRoles(eq(ADMIN), anyList())).thenReturn(true);

		try {
			facade.update(persistable(), TYPE_ID, defaultStore, language);
		} finally {
			verify(productTypeService, never()).saveOrUpdate(any());
		}
	}

	@Test(expected = ResourceNotFoundException.class)
	public void superAdminCannotDeleteAnotherStoresType() throws Exception {
		when(productTypeService.getById(TYPE_ID, defaultStore, language)).thenReturn(type(otherStore));
		when(userFacade.userInRoles(eq(ADMIN), anyList())).thenReturn(true);

		try {
			facade.delete(TYPE_ID, defaultStore, language);
		} finally {
			verify(productTypeService, never()).delete(any());
		}
	}

	@Test
	public void updateRestoresOriginalOwnerAfterMapperChangesIt() throws Exception {
		ProductType owned = type(defaultStore);
		when(productTypeService.getById(TYPE_ID, defaultStore, language)).thenReturn(owned);
		when(persistableProductTypeMapper.merge(any(), any(), any(), any())).thenAnswer(invocation -> {
			ProductType merged = invocation.getArgument(1);
			merged.setMerchantStore(otherStore);
			return merged;
		});

		facade.update(persistable(), TYPE_ID, defaultStore, language);

		ArgumentCaptor<ProductType> saved = ArgumentCaptor.forClass(ProductType.class);
		verify(productTypeService).saveOrUpdate(saved.capture());
		assertSame(defaultStore, saved.getValue().getMerchantStore());
	}

	@Test
	public void saveOrUpdateServiceExceptionIsWrapped() throws Exception {
		ProductType owned = type(defaultStore);
		when(productTypeService.getById(TYPE_ID, defaultStore, language)).thenReturn(owned);
		ServiceException cause = new ServiceException("save failed");
		when(productTypeService.saveOrUpdate(any())).thenThrow(cause);

		try {
			facade.update(persistable(), TYPE_ID, defaultStore, language);
			fail("Expected service failure to be wrapped");
		} catch (ServiceRuntimeException expected) {
			assertSame(cause, expected.getCause());
		}
	}

	@Test
	public void deleteServiceExceptionIsWrapped() throws Exception {
		ProductType owned = type(defaultStore);
		when(productTypeService.getById(TYPE_ID, defaultStore, language)).thenReturn(owned);
		ServiceException cause = new ServiceException("delete failed");
		org.mockito.Mockito.doThrow(cause).when(productTypeService).delete(owned);

		try {
			facade.delete(TYPE_ID, defaultStore, language);
			fail("Expected service failure to be wrapped");
		} catch (ServiceRuntimeException expected) {
			assertSame(cause, expected.getCause());
		}
	}

	@Test
	public void missingUpdateTargetRemainsNotFound() throws Exception {
		when(productTypeService.getById(TYPE_ID, defaultStore, language)).thenReturn(null);

		try {
			facade.update(persistable(), TYPE_ID, defaultStore, language);
			fail("Expected missing type to remain not found");
		} catch (ResourceNotFoundException expected) {
			verify(productTypeService, never()).saveOrUpdate(any());
		}
	}

	@Test
	public void readingGlobalTypeDoesNotCheckUserRoles() throws Exception {
		ProductType shared = type(null);
		ReadableProductType readable = new ReadableProductType();
		when(productTypeService.getById(TYPE_ID, defaultStore, language)).thenReturn(shared);
		when(readableProductTypeMapper.convert(shared, defaultStore, language)).thenReturn(readable);

		assertSame(readable, facade.get(defaultStore, TYPE_ID, language));

		verify(userFacade, never()).authenticatedUser();
		verify(userFacade, never()).userInRoles(any(), anyList());
	}

	private static MerchantStore store(Integer id, String code) {
		MerchantStore store = new MerchantStore();
		store.setId(id);
		store.setCode(code);
		return store;
	}

	private static ProductType type(MerchantStore owner) {
		ProductType type = new ProductType();
		type.setId(TYPE_ID);
		type.setCode(ProductType.GENERAL_TYPE);
		type.setMerchantStore(owner);
		return type;
	}

	private static PersistableProductType persistable() {
		PersistableProductType type = new PersistableProductType();
		type.setCode("hijacked");
		return type;
	}
}
