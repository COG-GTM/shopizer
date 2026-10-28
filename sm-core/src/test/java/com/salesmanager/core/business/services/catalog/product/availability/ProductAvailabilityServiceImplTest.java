package com.salesmanager.core.business.services.catalog.product.availability;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import org.junit.jupiter.api.Test;

import org.mockito.Mockito;

import com.salesmanager.core.business.repositories.catalog.product.availability.ProductAvailabilityRepository;
import com.salesmanager.core.model.catalog.product.availability.ProductAvailability;
import com.salesmanager.core.model.merchant.MerchantStore;

class ProductAvailabilityServiceImplTest {

	private final ProductAvailabilityRepository repository = Mockito.mock(ProductAvailabilityRepository.class);
	private final ProductAvailabilityServiceImpl service = new ProductAvailabilityServiceImpl(repository);

	@Test
	void getByIdIsScopedToStore() {
		MerchantStore store = new MerchantStore();
		store.setId(2);
		ProductAvailability availability = new ProductAvailability();
		when(repository.getById(77L, 2)).thenReturn(availability);

		assertSame(availability, service.getById(77L, store).get());
		verify(repository, never()).getById(anyLong());
	}

	@Test
	void getByIdReturnsEmptyForOtherStore() {
		MerchantStore store = new MerchantStore();
		store.setId(3);

		assertFalse(service.getById(77L, store).isPresent());
		verify(repository, never()).getById(anyLong());
	}
}
