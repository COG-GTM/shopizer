package com.salesmanager.shop.store.api.v1.product;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotEquals;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

import org.junit.Before;
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
public class ProductApiUpdateTest {

	@InjectMocks
	private ProductApi productApi;

	@Mock
	private ProductCommonFacade productCommonFacade;

	private MerchantStore store;
	private Language defaultLanguage;

	@Before
	public void setUp() {
		store = new MerchantStore();
		defaultLanguage = new Language();
		store.setDefaultLanguage(defaultLanguage);
	}

	@Test
	public void updateWithMismatchedIdReturns400AndDoesNotSave() {
		PersistableProduct body = new PersistableProduct();
		body.setId(6L);
		MockHttpServletResponse response = new MockHttpServletResponse();

		productApi.update(5L, body, store, new MockHttpServletRequest(), response);

		assertEquals(400, response.getStatus());
		verifyNoInteractions(productCommonFacade);
	}

	@Test
	public void updatePropagatesResourceNotFound() {
		PersistableProduct body = new PersistableProduct();
		body.setId(5L);
		MockHttpServletResponse response = new MockHttpServletResponse();
		doThrow(new ResourceNotFoundException("missing")).when(productCommonFacade)
				.saveProduct(store, body, defaultLanguage);

		try {
			productApi.update(5L, body, store, new MockHttpServletRequest(), response);
		} catch (ResourceNotFoundException expected) {
			assertNotEquals(503, response.getStatus());
			return;
		}
		throw new AssertionError("Expected ResourceNotFoundException");
	}

	@Test
	public void updateMapsOtherErrorsTo503() {
		PersistableProduct body = new PersistableProduct();
		body.setId(5L);
		MockHttpServletResponse response = new MockHttpServletResponse();
		doThrow(new ServiceRuntimeException("failure")).when(productCommonFacade)
				.saveProduct(store, body, defaultLanguage);

		productApi.update(5L, body, store, new MockHttpServletRequest(), response);

		assertEquals(503, response.getStatus());
	}

	@Test
	public void updateWithMatchingIdSavesWithStoreDefaultLanguage() {
		PersistableProduct body = new PersistableProduct();
		body.setId(5L);

		productApi.update(5L, body, store, new MockHttpServletRequest(), new MockHttpServletResponse());

		verify(productCommonFacade).saveProduct(store, body, defaultLanguage);
	}
}
