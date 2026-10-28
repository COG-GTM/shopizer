package com.salesmanager.shop.store.facade.manufacturer;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.salesmanager.core.business.services.catalog.product.manufacturer.ManufacturerService;
import com.salesmanager.core.model.catalog.product.manufacturer.Manufacturer;
import com.salesmanager.core.model.merchant.MerchantStore;
import com.salesmanager.core.model.reference.language.Language;
import com.salesmanager.shop.store.api.exception.ResourceNotFoundException;

@ExtendWith(MockitoExtension.class)
class ManufacturerFacadeImplTest {

  @Mock
  private ManufacturerService manufacturerService;

  @InjectMocks
  private ManufacturerFacadeImpl facade;

  private final Language language = new Language();

  @Test
  void deleteManufacturerOfAnotherStoreIsRejected() throws Exception {
    Manufacturer manufacturer = manufacturer(17L, store(1000, "ACME"));
    when(manufacturerService.getById(17L)).thenReturn(manufacturer);

    assertThrows(ResourceNotFoundException.class,
        () -> facade.deleteManufacturer(manufacturer, store(2000, "GLOBEX"), language));

    verify(manufacturerService, never()).delete(any(Manufacturer.class));
  }

  @Test
  void deleteUsesPersistedStoreNotCallerSuppliedEntity() throws Exception {
    Manufacturer persisted = manufacturer(17L, store(1000, "ACME"));
    when(manufacturerService.getById(17L)).thenReturn(persisted);
    Manufacturer forged = manufacturer(17L, store(2000, "GLOBEX"));

    assertThrows(ResourceNotFoundException.class,
        () -> facade.deleteManufacturer(forged, store(2000, "GLOBEX"), language));

    verify(manufacturerService, never()).delete(any(Manufacturer.class));
  }

  @Test
  void deleteUnknownManufacturerIsRejected() throws Exception {
    when(manufacturerService.getById(17L)).thenReturn(null);

    assertThrows(ResourceNotFoundException.class,
        () -> facade.deleteManufacturer(manufacturer(17L, store(1000, "ACME")), store(1000, "ACME"), language));

    verify(manufacturerService, never()).delete(any(Manufacturer.class));
  }

  @Test
  void deleteManufacturerOfOwnStoreSucceeds() throws Exception {
    // ids above the Integer cache range guard against reference comparison of boxed ids
    Manufacturer manufacturer = manufacturer(17L, store(1000, "ACME"));
    when(manufacturerService.getById(17L)).thenReturn(manufacturer);

    facade.deleteManufacturer(manufacturer, store(1000, "ACME"), language);

    verify(manufacturerService).delete(manufacturer);
  }

  @Test
  void getManufacturerOfOwnStoreWithLargeStoreIdIsNotRejected() throws Exception {
    when(manufacturerService.getById(17L)).thenReturn(manufacturer(17L, store(1000, "ACME")));

    // ReadableManufacturerPopulator needs more state than this mock provides; only the
    // store check must not reject the call.
    try {
      facade.getManufacturer(17L, store(1000, "ACME"), language);
    } catch (ResourceNotFoundException e) {
      throw new AssertionError("Own-store manufacturer rejected", e);
    } catch (Exception ignore) {
    }
  }

  @Test
  void getManufacturerOfAnotherStoreIsRejected() throws Exception {
    when(manufacturerService.getById(17L)).thenReturn(manufacturer(17L, store(1000, "ACME")));

    assertThrows(ResourceNotFoundException.class,
        () -> facade.getManufacturer(17L, store(2000, "GLOBEX"), language));
  }

  private static MerchantStore store(int id, String code) {
    return new MerchantStore(Integer.valueOf(id), code, code);
  }

  private static Manufacturer manufacturer(Long id, MerchantStore store) {
    Manufacturer manufacturer = new Manufacturer();
    manufacturer.setId(id);
    manufacturer.setMerchantStore(store);
    return manufacturer;
  }
}
