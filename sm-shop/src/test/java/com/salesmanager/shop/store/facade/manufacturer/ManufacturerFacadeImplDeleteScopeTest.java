package com.salesmanager.shop.store.facade.manufacturer;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

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
class ManufacturerFacadeImplDeleteScopeTest {
  @Mock private ManufacturerService manufacturerService;
  @InjectMocks private ManufacturerFacadeImpl facade;
  private final Language language = new Language();

  @Test
  void nullManufacturerIdDoesNotReachService() {
    assertThrows(ResourceNotFoundException.class,
        () -> facade.deleteManufacturer(manufacturer(null, store(1000)), store(1000), language));
    verifyNoInteractions(manufacturerService);
  }

  @Test
  void persistedNullMerchantStoreIsRejected() throws Exception {
    when(manufacturerService.getById(17L)).thenReturn(manufacturer(17L, null));
    assertThrows(ResourceNotFoundException.class,
        () -> facade.deleteManufacturer(manufacturer(17L, store(1000)), store(1000), language));
    verify(manufacturerService, never()).delete(any());
  }

  @Test
  void persistedNullStoreIdIsRejected() throws Exception {
    when(manufacturerService.getById(17L)).thenReturn(manufacturer(17L, store(null)));
    assertThrows(ResourceNotFoundException.class,
        () -> facade.deleteManufacturer(manufacturer(17L, store(1000)), store(1000), language));
    verify(manufacturerService, never()).delete(any());
  }

  @Test
  void deletesPersistedInstanceRatherThanCaller() throws Exception {
    Manufacturer persisted = manufacturer(17L, store(1000));
    Manufacturer caller = manufacturer(17L, store(1000));
    assertNotSame(caller, persisted);
    when(manufacturerService.getById(17L)).thenReturn(persisted);
    facade.deleteManufacturer(caller, store(1000), language);
    verify(manufacturerService).delete(same(persisted));
  }

  @Test
  void distinctEqualIntegerStoreIdsAreAccepted() throws Exception {
    MerchantStore owner = store(Integer.valueOf(1000));
    MerchantStore requested = store(Integer.valueOf(1000));
    assertNotSame(owner.getId(), requested.getId());
    assertEquals(owner.getId(), requested.getId());
    Manufacturer persisted = manufacturer(17L, owner);
    when(manufacturerService.getById(17L)).thenReturn(persisted);
    facade.deleteManufacturer(manufacturer(17L, owner), requested, language);
    verify(manufacturerService).delete(same(persisted));
  }

  @Test
  void nullManufacturerFailsValidation() {
    assertThrows(IllegalArgumentException.class, () -> facade.deleteManufacturer(null, store(1000), language));
    verifyNoInteractions(manufacturerService);
  }

  @Test
  void nullStoreFailsValidation() {
    assertThrows(IllegalArgumentException.class,
        () -> facade.deleteManufacturer(manufacturer(17L, store(1000)), null, language));
    verifyNoInteractions(manufacturerService);
  }

  private static MerchantStore store(Integer id) {
    return new MerchantStore(id, "scope", "scope");
  }

  private static Manufacturer manufacturer(Long id, MerchantStore store) {
    Manufacturer manufacturer = new Manufacturer();
    manufacturer.setId(id);
    manufacturer.setMerchantStore(store);
    return manufacturer;
  }
}
