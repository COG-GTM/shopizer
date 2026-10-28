package com.salesmanager.shop.store.facade.product;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.Optional;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.salesmanager.core.business.services.catalog.product.availability.ProductAvailabilityService;
import com.salesmanager.core.model.catalog.product.Product;
import com.salesmanager.core.model.catalog.product.availability.ProductAvailability;
import com.salesmanager.core.model.merchant.MerchantStore;
import com.salesmanager.shop.store.api.exception.ResourceNotFoundException;

@ExtendWith(MockitoExtension.class)
class ProductInventoryFacadeImplTest {

	private static final Long INVENTORY_ID = 77L;

	@Mock
	private ProductAvailabilityService productAvailabilityService;

	@InjectMocks
	private ProductInventoryFacadeImpl facade;

	@Test
	void deletesInventoryOwnedByCallerStore() throws Exception {
		MerchantStore store = store(1);
		ProductAvailability availability = availability(42L, store);
		when(productAvailabilityService.getById(INVENTORY_ID, store)).thenReturn(Optional.of(availability));

		facade.delete(42L, INVENTORY_ID, store);

		verify(productAvailabilityService).delete(availability);
	}

	@Test
	void deletesInventoryForProductIdOutsideLongCache() throws Exception {
		MerchantStore store = store(1);
		ProductAvailability availability = availability(Long.valueOf(5000L), store);
		when(productAvailabilityService.getById(INVENTORY_ID, store)).thenReturn(Optional.of(availability));

		facade.delete(Long.valueOf(5000L), INVENTORY_ID, store);

		verify(productAvailabilityService).delete(availability);
	}

	@Test
	void rejectsInventoryOfAnotherStore() throws Exception {
		MerchantStore caller = store(2);
		ProductAvailability availability = availability(42L, store(1));
		when(productAvailabilityService.getById(INVENTORY_ID, caller)).thenReturn(Optional.of(availability));

		assertThrows(ResourceNotFoundException.class, () -> facade.delete(42L, INVENTORY_ID, caller));

		verify(productAvailabilityService, never()).delete(any());
	}

	@Test
	void rejectsInventoryOfAnotherProduct() throws Exception {
		MerchantStore store = store(1);
		ProductAvailability availability = availability(43L, store);
		when(productAvailabilityService.getById(INVENTORY_ID, store)).thenReturn(Optional.of(availability));

		assertThrows(ResourceNotFoundException.class, () -> facade.delete(42L, INVENTORY_ID, store));

		verify(productAvailabilityService, never()).delete(any());
	}

	@Test
	void rejectsUnknownInventory() throws Exception {
		MerchantStore store = store(1);
		when(productAvailabilityService.getById(INVENTORY_ID, store)).thenReturn(Optional.empty());

		assertThrows(ResourceNotFoundException.class, () -> facade.delete(42L, INVENTORY_ID, store));

		verify(productAvailabilityService, never()).delete(any());
	}

	private static MerchantStore store(int id) {
		MerchantStore store = new MerchantStore();
		store.setId(id);
		return store;
	}

	private static ProductAvailability availability(Long productId, MerchantStore productStore) {
		Product product = new Product();
		product.setId(productId);
		product.setMerchantStore(productStore);
		ProductAvailability availability = new ProductAvailability();
		availability.setProduct(product);
		return availability;
	}
}
