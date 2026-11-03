package com.salesmanager.test.shop.unit.api;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.mockito.Mockito.doThrow;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mock.web.MockHttpServletResponse;

import com.salesmanager.core.business.services.catalog.product.manufacturer.ManufacturerService;
import com.salesmanager.core.model.catalog.product.manufacturer.Manufacturer;
import com.salesmanager.shop.store.api.exception.ResourceNotFoundException;
import com.salesmanager.shop.store.controller.manufacturer.facade.ManufacturerFacade;
import com.salesmanager.shop.store.api.v1.product.ProductManufacturerApi;

@ExtendWith(MockitoExtension.class)
public class ProductManufacturerApiDeleteTest {

	@Mock
	private ManufacturerService manufacturerService;

	@Mock
	private ManufacturerFacade manufacturerFacade;

	@InjectMocks
	private ProductManufacturerApi manufacturerApi;

	@Test
	public void crossStoreDeleteReturnsNotFound() throws Exception {
		Manufacturer manufacturer = new Manufacturer();
		when(manufacturerService.getById(5L)).thenReturn(manufacturer);
		doThrow(new ResourceNotFoundException("out of scope"))
				.when(manufacturerFacade).deleteManufacturer(manufacturer, null, null);
		MockHttpServletResponse response = new MockHttpServletResponse();

		manufacturerApi.delete(5L, null, null, response);

		assertEquals(404, response.getStatus());
		assertEquals("No Manufacturer found for ID : 5", response.getErrorMessage());
	}

	@Test
	public void missingManufacturerReturnsNotFoundWithoutCallingFacade() throws Exception {
		when(manufacturerService.getById(5L)).thenReturn(null);
		MockHttpServletResponse response = new MockHttpServletResponse();

		manufacturerApi.delete(5L, null, null, response);

		assertEquals(404, response.getStatus());
		verify(manufacturerFacade, never()).deleteManufacturer(org.mockito.ArgumentMatchers.any(),
				org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.any());
	}

	@Test
	public void unexpectedDeleteErrorReturnsServiceUnavailable() throws Exception {
		Manufacturer manufacturer = new Manufacturer();
		when(manufacturerService.getById(5L)).thenReturn(manufacturer);
		doThrow(new RuntimeException("boom")).when(manufacturerFacade).deleteManufacturer(manufacturer, null, null);
		MockHttpServletResponse response = new MockHttpServletResponse();

		manufacturerApi.delete(5L, null, null, response);

		assertEquals(503, response.getStatus());
	}

	@Test
	public void successfulDeleteReturnsOk() throws Exception {
		Manufacturer manufacturer = new Manufacturer();
		when(manufacturerService.getById(5L)).thenReturn(manufacturer);
		MockHttpServletResponse response = new MockHttpServletResponse();

		manufacturerApi.delete(5L, null, null, response);

		assertEquals(200, response.getStatus());
		verify(manufacturerFacade).deleteManufacturer(manufacturer, null, null);
	}
}
