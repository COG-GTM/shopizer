package com.salesmanager.test.shop.integration.product;

import static org.junit.Assert.*;
import static org.mockito.Mockito.*;

import java.util.Optional;

import org.junit.Before;
import org.junit.Test;
import org.springframework.test.util.ReflectionTestUtils;

import com.salesmanager.core.business.services.catalog.product.availability.ProductAvailabilityService;
import com.salesmanager.core.model.catalog.product.Product;
import com.salesmanager.core.model.catalog.product.availability.ProductAvailability;
import com.salesmanager.core.model.merchant.MerchantStore;
import com.salesmanager.shop.store.api.exception.ResourceNotFoundException;
import com.salesmanager.shop.store.facade.product.ProductInventoryFacadeImpl;

public class ProductInventoryDeletionTest {

	private ProductAvailabilityService service;
	private ProductInventoryFacadeImpl facade;
	private MerchantStore store;
	private ProductAvailability inventory;

	@Before
	public void setUp() {
		service = mock(ProductAvailabilityService.class);
		facade = new ProductInventoryFacadeImpl();
		ReflectionTestUtils.setField(facade, "productAvailabilityService", service);
		store = new MerchantStore(23, "owner", "Owner");
		Product product = new Product();
		product.setId(Long.valueOf("1000"));
		inventory = new ProductAvailability(product, store);
		inventory.setId(2000L);
	}

	@Test
	public void deletesWhenProductIdsHaveEqualValuesButDifferentReferences() throws Exception {
		Long requestId = Long.valueOf("1000");
		assertNotSame(requestId, inventory.getProduct().getId());
		when(service.getById(2000L, store)).thenReturn(Optional.of(inventory));

		facade.delete(requestId, 2000L, store);

		verify(service).delete(inventory);
	}

	@Test
	public void rejectsInventoryForDifferentProduct() throws Exception {
		when(service.getById(2000L, store)).thenReturn(Optional.of(inventory));
		assertThrows(ResourceNotFoundException.class, () -> facade.delete(1001L, 2000L, store));
		verify(service, never()).delete(any(ProductAvailability.class));
	}

	@Test
	public void rejectsInventoryHiddenFromRequestingStore() throws Exception {
		when(service.getById(2000L, store)).thenReturn(Optional.empty());
		assertThrows(ResourceNotFoundException.class, () -> facade.delete(1000L, 2000L, store));
		verify(service, never()).delete(any(ProductAvailability.class));
	}
}
