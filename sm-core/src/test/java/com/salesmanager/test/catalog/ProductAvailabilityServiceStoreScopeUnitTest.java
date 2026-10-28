package com.salesmanager.test.catalog;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertThrows;
import static org.junit.Assert.assertTrue;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.Optional;

import org.junit.Before;
import org.junit.Test;

import com.salesmanager.core.business.repositories.catalog.product.availability.ProductAvailabilityRepository;
import com.salesmanager.core.business.services.catalog.product.availability.ProductAvailabilityServiceImpl;
import com.salesmanager.core.model.catalog.product.availability.ProductAvailability;
import com.salesmanager.core.model.merchant.MerchantStore;

/**
 * getById(id, store) must delegate to the store-scoped repository query and never to the unscoped lookup.
 */
public class ProductAvailabilityServiceStoreScopeUnitTest {

	private ProductAvailabilityRepository repository;
	private ProductAvailabilityServiceImpl service;
	private MerchantStore store;

	@Before
	public void setUp() {
		repository = mock(ProductAvailabilityRepository.class);
		service = new ProductAvailabilityServiceImpl(repository);
		store = new MerchantStore();
		store.setId(7);
		store.setCode("SCOPED");
	}

	@Test
	public void getByIdUsesStoreScopedQuery() {
		ProductAvailability inventory = new ProductAvailability();
		when(repository.getById(42L, 7)).thenReturn(inventory);

		Optional<ProductAvailability> result = service.getById(42L, store);

		assertTrue(result.isPresent());
		assertSame(inventory, result.get());
		verify(repository).getById(42L, 7);
		verify(repository, never()).getById(anyLong());
	}

	@Test
	public void getByIdReturnsEmptyWhenInventoryNotInStore() {
		when(repository.getById(42L, 7)).thenReturn(null);

		assertFalse(service.getById(42L, store).isPresent());
		verify(repository, never()).getById(anyLong());
	}

	@Test
	public void getByIdRequiresStore() {
		assertThrows(NullPointerException.class, () -> service.getById(42L, (MerchantStore) null));
	}
}
