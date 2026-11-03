package com.salesmanager.test.shop.unit.facade;

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
public class ManufacturerFacadeImplTest {

	@Mock
	private ManufacturerService manufacturerService;

	@InjectMocks
	private ManufacturerFacadeImpl manufacturerFacade;

	private static MerchantStore store(int id) {
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
	public void deleteManufacturerOfAnotherStoreIsRejected() throws Exception {
		Manufacturer manufacturer = manufacturer(store(1));

		assertThrows(ResourceNotFoundException.class,
				() -> manufacturerFacade.deleteManufacturer(manufacturer, store(2), null));

		verify(manufacturerService, never()).delete(any(Manufacturer.class));
	}

	@Test
	public void deleteManufacturerOfOwnStoreIsAllowed() throws Exception {
		Manufacturer manufacturer = manufacturer(store(1000));

		manufacturerFacade.deleteManufacturer(manufacturer, store(1000), null);

		verify(manufacturerService).delete(manufacturer);
	}
}
