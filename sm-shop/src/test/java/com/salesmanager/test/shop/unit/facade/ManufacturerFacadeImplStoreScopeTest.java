package com.salesmanager.test.shop.unit.facade;

import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.salesmanager.core.business.services.catalog.product.manufacturer.ManufacturerService;
import com.salesmanager.core.model.catalog.product.manufacturer.Manufacturer;
import com.salesmanager.core.model.merchant.MerchantStore;
import com.salesmanager.shop.store.api.exception.ResourceNotFoundException;
import com.salesmanager.shop.store.facade.manufacturer.ManufacturerFacadeImpl;

@ExtendWith(MockitoExtension.class)
public class ManufacturerFacadeImplStoreScopeTest {

	@Mock
	private ManufacturerService manufacturerService;

	@InjectMocks
	private ManufacturerFacadeImpl manufacturerFacade;

	private static MerchantStore store(Integer id) {
		MerchantStore store = new MerchantStore();
		store.setId(id);
		return store;
	}

	private static Manufacturer manufacturer(MerchantStore owner) {
		Manufacturer manufacturer = new Manufacturer();
		manufacturer.setId(17L);
		manufacturer.setMerchantStore(owner);
		return manufacturer;
	}

	@Test
	public void manufacturerWithoutOwnerIsRejected() throws Exception {
		Manufacturer manufacturer = manufacturer(null);

		assertThrows(ResourceNotFoundException.class,
				() -> manufacturerFacade.deleteManufacturer(manufacturer, store(1000), null));

		verify(manufacturerService, never()).delete(any(Manufacturer.class));
	}

	@Test
	public void nullManufacturerIsRejected() throws Exception {
		assertThrows(IllegalArgumentException.class,
				() -> manufacturerFacade.deleteManufacturer(null, store(1000), null));

		verify(manufacturerService, never()).delete(any(Manufacturer.class));
	}

	@Test
	public void nullStoreIsRejected() throws Exception {
		assertThrows(IllegalArgumentException.class,
				() -> manufacturerFacade.deleteManufacturer(manufacturer(store(1000)), null, null));

		verify(manufacturerService, never()).delete(any(Manufacturer.class));
	}

	@Test
	public void equalStoreIdsOutsideIntegerCacheAreAllowed() throws Exception {
		Integer ownerId = new Integer(1000);
		Integer requestId = new Integer(1000);
		assertNotSame(ownerId, requestId);
		Manufacturer manufacturer = manufacturer(store(ownerId));

		manufacturerFacade.deleteManufacturer(manufacturer, store(requestId), null);

		verify(manufacturerService).delete(manufacturer);
	}

	@Test
	public void differentStoreIdsAreRejected() throws Exception {
		Manufacturer manufacturer = manufacturer(store(1000));

		assertThrows(ResourceNotFoundException.class,
				() -> manufacturerFacade.deleteManufacturer(manufacturer, store(1001), null));

		verify(manufacturerService, never()).delete(any(Manufacturer.class));
	}
}
