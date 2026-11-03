package com.salesmanager.test.catalog;

import static org.junit.Assert.*;
import static org.mockito.Mockito.*;

import org.junit.Before;
import org.junit.Test;

import com.salesmanager.core.business.repositories.catalog.product.availability.ProductAvailabilityRepository;
import com.salesmanager.core.business.services.catalog.product.availability.ProductAvailabilityServiceImpl;
import com.salesmanager.core.model.catalog.product.availability.ProductAvailability;
import com.salesmanager.core.model.merchant.MerchantStore;

public class ProductAvailabilityStoreScopeTest {

	private ProductAvailabilityRepository repository;
	private ProductAvailabilityServiceImpl service;
	private MerchantStore store;

	@Before
	public void setUp() {
		repository = mock(ProductAvailabilityRepository.class);
		service = new ProductAvailabilityServiceImpl(repository);
		store = new MerchantStore(23, "child", "Child store");
	}

	@Test
	public void returnsInventoryVisibleToRequestingStore() {
		ProductAvailability inventory = new ProductAvailability();
		when(repository.getById(1000L, store.getId())).thenReturn(inventory);
		assertSame(inventory, service.getById(1000L, store).get());
		verify(repository).getById(1000L, store.getId());
		verifyNoMoreInteractions(repository);
	}

	@Test
	public void doesNotFallBackToUnscopedInventoryWhenStoreLookupMisses() {
		assertFalse(service.getById(1000L, store).isPresent());
		verify(repository).getById(1000L, store.getId());
		verifyNoMoreInteractions(repository);
	}

	@Test(expected = NullPointerException.class)
	public void requiresStoreForInventoryLookup() {
		service.getById(1000L, null);
	}
}
