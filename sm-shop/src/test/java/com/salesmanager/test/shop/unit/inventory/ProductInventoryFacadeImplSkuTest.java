package com.salesmanager.test.shop.unit.inventory;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;

import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnitRunner;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;

import com.salesmanager.core.business.exception.ServiceException;
import com.salesmanager.core.business.services.catalog.product.ProductService;
import com.salesmanager.core.business.services.catalog.product.availability.ProductAvailabilityService;
import com.salesmanager.core.model.catalog.product.Product;
import com.salesmanager.core.model.catalog.product.availability.ProductAvailability;
import com.salesmanager.core.model.merchant.MerchantStore;
import com.salesmanager.core.model.reference.language.Language;
import com.salesmanager.shop.mapper.inventory.ReadableInventoryMapper;
import com.salesmanager.shop.model.catalog.product.inventory.ReadableInventory;
import com.salesmanager.shop.model.entity.ReadableEntityList;
import com.salesmanager.shop.store.api.exception.ServiceRuntimeException;
import com.salesmanager.shop.store.facade.product.ProductInventoryFacadeImpl;

@RunWith(MockitoJUnitRunner.class)
public class ProductInventoryFacadeImplSkuTest {

	@Mock
	private ProductAvailabilityService productAvailabilityService;
	@Mock
	private ProductService productService;
	@Mock
	private ReadableInventoryMapper readableInventoryMapper;

	@InjectMocks
	private ProductInventoryFacadeImpl facade;

	private MerchantStore store;
	private Language language;

	@Before
	public void setUp() {
		store = new MerchantStore();
		store.setCode("STOREA");
		language = new Language("en");
	}

	@Test
	public void getBySkuUsesStoreScopedPagedLookup() {
		ProductAvailability a1 = new ProductAvailability();
		ProductAvailability a2 = new ProductAvailability();
		Page<ProductAvailability> page = new PageImpl<>(Arrays.asList(a1, a2));
		when(productAvailabilityService.getBySku("SKU-1", store, 3, 7)).thenReturn(page);
		ReadableInventory r1 = new ReadableInventory();
		ReadableInventory r2 = new ReadableInventory();
		when(readableInventoryMapper.convert(a1, store, language)).thenReturn(r1);
		when(readableInventoryMapper.convert(a2, store, language)).thenReturn(r2);

		ReadableEntityList<ReadableInventory> result = facade.get("SKU-1", store, language, 3, 7);

		assertEquals(Arrays.asList(r1, r2), result.getItems());
		assertEquals(2, result.getRecordsTotal());
		verify(productAvailabilityService).getBySku("SKU-1", store, 3, 7);
		verifyNoMoreInteractions(productAvailabilityService);
		verifyNoInteractions(productService);
	}

	@Test
	public void emptyStorePageFallsBackToParentProductOfSameStore() throws Exception {
		when(productAvailabilityService.getBySku("VARIANT-1", store, 0, 10)).thenReturn(Page.empty());
		ProductAvailability parentAvailability = new ProductAvailability();
		Product parent = new Product();
		parent.setAvailabilities(new HashSet<>(Collections.singletonList(parentAvailability)));
		when(productService.getBySku("VARIANT-1", store)).thenReturn(parent);
		ReadableInventory readable = new ReadableInventory();
		when(readableInventoryMapper.convert(parentAvailability, store, language)).thenReturn(readable);

		ReadableEntityList<ReadableInventory> result = facade.get("VARIANT-1", store, language, 0, 10);

		assertEquals(1, result.getItems().size());
		assertSame(readable, result.getItems().get(0));
		verify(productService).getBySku("VARIANT-1", store);
	}

	@Test
	public void skuUnknownInStoreReturnsEmptyList() throws Exception {
		when(productAvailabilityService.getBySku("OTHER-STORE-SKU", store, 0, 10)).thenReturn(Page.empty());
		when(productService.getBySku("OTHER-STORE-SKU", store)).thenReturn(null);

		ReadableEntityList<ReadableInventory> result = facade.get("OTHER-STORE-SKU", store, language, 0, 10);

		assertTrue(result.getItems().isEmpty());
		assertEquals(0, result.getRecordsTotal());
		verifyNoInteractions(readableInventoryMapper);
	}

	@Test(expected = ServiceRuntimeException.class)
	public void parentLookupFailureIsWrapped() throws Exception {
		when(productAvailabilityService.getBySku("SKU-ERR", store, 0, 10)).thenReturn(Page.empty());
		when(productService.getBySku("SKU-ERR", store)).thenThrow(new ServiceException("boom"));

		facade.get("SKU-ERR", store, language, 0, 10);
	}

	@Test
	public void nullStoreIsRejectedBeforeAnyLookup() {
		try {
			facade.get("SKU-1", null, language, 0, 10);
		} catch (NullPointerException expected) {
			// Validate.notNull
		}
		verify(productAvailabilityService, never()).getBySku(anyString(), eq((MerchantStore) null), anyInt(), anyInt());
		verifyNoInteractions(productAvailabilityService, productService);
	}
}
