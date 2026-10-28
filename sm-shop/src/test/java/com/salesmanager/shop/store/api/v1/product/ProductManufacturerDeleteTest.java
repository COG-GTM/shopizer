package com.salesmanager.shop.store.api.v1.product;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mock.web.MockHttpServletResponse;

import com.salesmanager.core.business.services.catalog.product.manufacturer.ManufacturerService;
import com.salesmanager.core.model.catalog.product.manufacturer.Manufacturer;
import com.salesmanager.core.model.merchant.MerchantStore;
import com.salesmanager.core.model.reference.language.Language;
import com.salesmanager.shop.store.api.exception.ResourceNotFoundException;
import com.salesmanager.shop.store.controller.manufacturer.facade.ManufacturerFacade;

@ExtendWith(MockitoExtension.class)
class ProductManufacturerDeleteTest {
  @Mock private ManufacturerService manufacturerService;
  @Mock private ManufacturerFacade manufacturerFacade;
  @InjectMocks private ProductManufacturerApi api;
  private final MerchantStore store = new MerchantStore();
  private final Language language = new Language();
  private final MockHttpServletResponse response = new MockHttpServletResponse();

  @Test
  void missingManufacturerReturns404() {
    api.delete(17L, store, language, response);
    assertEquals(404, response.getStatus());
    verifyNoInteractions(manufacturerFacade);
  }

  @Test
  void scopeRejectionReturns404() throws Exception {
    Manufacturer loaded = new Manufacturer();
    when(manufacturerService.getById(17L)).thenReturn(loaded);
    doThrow(new ResourceNotFoundException("scope")).when(manufacturerFacade)
        .deleteManufacturer(loaded, store, language);
    api.delete(17L, store, language, response);
    assertEquals(404, response.getStatus());
  }

  @Test
  void unexpectedFailureReturns503() throws Exception {
    Manufacturer loaded = new Manufacturer();
    when(manufacturerService.getById(17L)).thenReturn(loaded);
    doThrow(new RuntimeException("unavailable")).when(manufacturerFacade)
        .deleteManufacturer(loaded, store, language);
    api.delete(17L, store, language, response);
    assertEquals(503, response.getStatus());
  }

  @Test
  void successDeletesLoadedManufacturerWithoutError() throws Exception {
    Manufacturer loaded = new Manufacturer();
    when(manufacturerService.getById(17L)).thenReturn(loaded);
    api.delete(17L, store, language, response);
    verify(manufacturerFacade).deleteManufacturer(same(loaded), same(store), same(language));
    assertEquals(200, response.getStatus());
    assertNull(response.getErrorMessage());
  }
}
