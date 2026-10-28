package com.salesmanager.shop.store.api.v1.product;

import static org.junit.Assert.assertEquals;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.http.HttpStatus.BAD_REQUEST;
import static org.springframework.http.HttpStatus.OK;
import static org.springframework.http.HttpStatus.SERVICE_UNAVAILABLE;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnitRunner;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

import com.salesmanager.core.model.merchant.MerchantStore;
import com.salesmanager.core.model.reference.language.Language;
import com.salesmanager.shop.model.catalog.product.product.PersistableProduct;
import com.salesmanager.shop.store.api.exception.ResourceNotFoundException;
import com.salesmanager.shop.store.api.exception.ServiceRuntimeException;
import com.salesmanager.shop.store.controller.product.facade.ProductCommonFacade;

@RunWith(MockitoJUnitRunner.class)
public class ProductApiTest {

	@InjectMocks
	private ProductApi productApi;

	@Mock
	private ProductCommonFacade productCommonFacade;

	@Test
	public void updateRejectsMismatchedIdsBeforeSaving() {
		MerchantStore store = new MerchantStore();
		PersistableProduct product = product(2L);
		MockHttpServletResponse response = new MockHttpServletResponse();

		productApi.update(1L, product, store, new MockHttpServletRequest(), response);

		assertEquals(BAD_REQUEST.value(), response.getStatus());
		verify(productCommonFacade, never()).saveProduct(store, product, store.getDefaultLanguage());
	}

	@Test(expected = ResourceNotFoundException.class)
	public void updatePropagatesResourceNotFoundException() {
		MerchantStore store = new MerchantStore();
		PersistableProduct product = product(1L);
		when(productCommonFacade.saveProduct(store, product, store.getDefaultLanguage()))
				.thenThrow(new ResourceNotFoundException("Not found"));

		productApi.update(1L, product, store, new MockHttpServletRequest(), new MockHttpServletResponse());
	}

	@Test
	public void updateMapsOtherRuntimeExceptionsToServiceUnavailable() {
		MerchantStore store = new MerchantStore();
		PersistableProduct product = product(1L);
		when(productCommonFacade.saveProduct(store, product, store.getDefaultLanguage()))
				.thenThrow(new ServiceRuntimeException("Service error"));
		MockHttpServletResponse response = new MockHttpServletResponse();

		productApi.update(1L, product, store, new MockHttpServletRequest(), response);

		assertEquals(SERVICE_UNAVAILABLE.value(), response.getStatus());
	}

	@Test
	public void updateSavesProductWhenIdsMatch() {
		MerchantStore store = new MerchantStore();
		Language language = store.getDefaultLanguage();
		PersistableProduct product = product(1L);
		MockHttpServletResponse response = new MockHttpServletResponse();
		when(productCommonFacade.saveProduct(store, product, language)).thenReturn(1L);

		productApi.update(1L, product, store, new MockHttpServletRequest(), response);

		assertEquals(OK.value(), response.getStatus());
		verify(productCommonFacade).saveProduct(store, product, language);
	}

	private static PersistableProduct product(Long id) {
		PersistableProduct product = new PersistableProduct();
		product.setId(id);
		return product;
	}
}
