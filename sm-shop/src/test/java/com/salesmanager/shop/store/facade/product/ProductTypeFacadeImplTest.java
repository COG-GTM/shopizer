package com.salesmanager.shop.store.facade.product;

import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertSame;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnitRunner;

import com.salesmanager.core.business.services.catalog.product.type.ProductTypeService;
import com.salesmanager.core.model.catalog.product.type.ProductType;
import com.salesmanager.core.model.merchant.MerchantStore;
import com.salesmanager.core.model.reference.language.Language;
import com.salesmanager.shop.mapper.catalog.PersistableProductTypeMapper;
import com.salesmanager.shop.mapper.catalog.ReadableProductTypeMapper;
import com.salesmanager.shop.model.catalog.product.type.PersistableProductType;
import com.salesmanager.shop.store.api.exception.ResourceNotFoundException;
import com.salesmanager.shop.store.api.exception.UnauthorizedException;
import com.salesmanager.shop.store.controller.user.facade.UserFacade;

@RunWith(MockitoJUnitRunner.Silent.class)
public class ProductTypeFacadeImplTest {

	private static final Long TYPE_ID = 1L;

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

	private MerchantStore initech;
	private MerchantStore acme;
	private Language language;

	@Before
	public void setUp() {
		initech = store(3, "INITECH");
		acme = store(1, "ACME");
		language = new Language();
		when(userFacade.authenticatedUser()).thenReturn("mallory@initech.com");
		when(persistableProductTypeMapper.merge(any(), any(), any(), any()))
				.thenAnswer(invocation -> invocation.getArgument(1));
	}

	@Test(expected = UnauthorizedException.class)
	public void storeAdminCannotUpdateGlobalType() throws Exception {
		ProductType general = type(null);
		when(productTypeService.getById(TYPE_ID, initech, language)).thenReturn(general);

		try {
			facade.update(persistable(), TYPE_ID, initech, language);
		} finally {
			assertNull(general.getMerchantStore());
			verify(productTypeService, never()).saveOrUpdate(any());
		}
	}

	@Test(expected = UnauthorizedException.class)
	public void storeAdminCannotDeleteGlobalType() throws Exception {
		when(productTypeService.getById(TYPE_ID, initech, language)).thenReturn(type(null));

		try {
			facade.delete(TYPE_ID, initech, language);
		} finally {
			verify(productTypeService, never()).delete(any());
		}
	}

	@Test
	public void superAdminUpdateKeepsGlobalTypeShared() throws Exception {
		ProductType general = type(null);
		when(productTypeService.getById(TYPE_ID, initech, language)).thenReturn(general);
		when(userFacade.userInRoles(eq("mallory@initech.com"), anyList())).thenReturn(true);

		facade.update(persistable(), TYPE_ID, initech, language);

		ArgumentCaptor<ProductType> saved = ArgumentCaptor.forClass(ProductType.class);
		verify(productTypeService).saveOrUpdate(saved.capture());
		assertNull(saved.getValue().getMerchantStore());
	}

	@Test
	public void superAdminCanDeleteGlobalType() throws Exception {
		ProductType general = type(null);
		when(productTypeService.getById(TYPE_ID, initech, language)).thenReturn(general);
		when(userFacade.userInRoles(eq("mallory@initech.com"), anyList())).thenReturn(true);

		facade.delete(TYPE_ID, initech, language);

		verify(productTypeService).delete(general);
	}

	@Test
	public void storeAdminCanUpdateOwnType() throws Exception {
		ProductType own = type(initech);
		when(productTypeService.getById(TYPE_ID, initech, language)).thenReturn(own);

		facade.update(persistable(), TYPE_ID, initech, language);

		ArgumentCaptor<ProductType> saved = ArgumentCaptor.forClass(ProductType.class);
		verify(productTypeService).saveOrUpdate(saved.capture());
		assertSame(initech, saved.getValue().getMerchantStore());
	}

	@Test
	public void storeAdminCanDeleteOwnType() throws Exception {
		ProductType own = type(initech);
		when(productTypeService.getById(TYPE_ID, initech, language)).thenReturn(own);

		facade.delete(TYPE_ID, initech, language);

		verify(productTypeService).delete(own);
	}

	@Test(expected = ResourceNotFoundException.class)
	public void typeOwnedByAnotherStoreIsNotFound() throws Exception {
		when(productTypeService.getById(TYPE_ID, initech, language)).thenReturn(type(acme));

		try {
			facade.update(persistable(), TYPE_ID, initech, language);
		} finally {
			verify(productTypeService, never()).saveOrUpdate(any());
		}
	}

	@Test(expected = ResourceNotFoundException.class)
	public void missingTypeIsNotFound() throws Exception {
		when(productTypeService.getById(TYPE_ID, initech, language)).thenReturn(null);

		facade.delete(TYPE_ID, initech, language);
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
