package com.salesmanager.shop.store.api.v1.product;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.fail;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.lang.reflect.Method;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnitRunner;
import org.springframework.core.annotation.AnnotatedElementUtils;
import org.springframework.http.HttpStatus;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.web.bind.annotation.RequestMapping;

import com.salesmanager.core.model.merchant.MerchantStore;
import com.salesmanager.core.model.reference.language.Language;
import com.salesmanager.shop.model.catalog.product.LightPersistableProduct;
import com.salesmanager.shop.model.catalog.product.product.PersistableProduct;
import com.salesmanager.shop.store.api.exception.ResourceNotFoundException;
import com.salesmanager.shop.store.controller.product.facade.ProductCommonFacade;

@RunWith(MockitoJUnitRunner.class)
public class ProductApiUpdateTest {

	@InjectMocks
	private ProductApi productApi;

	@Mock
	private ProductCommonFacade productCommonFacade;

	@Test
	public void rejectsMismatchedUrlAndBodyIds() throws Exception {
		MerchantStore store = store();
		PersistableProduct product = product(11L);
		MockHttpServletResponse response = new MockHttpServletResponse();

		productApi.update(10L, product, store, new MockHttpServletRequest(), response);

		assertEquals(HttpStatus.BAD_REQUEST.value(), response.getStatus());
		verify(productCommonFacade, never()).saveProduct(store, product, store.getDefaultLanguage());
	}

	@Test
	public void savesProductWhenIdsMatch() throws Exception {
		MerchantStore store = store();
		PersistableProduct product = product(10L);
		MockHttpServletResponse response = new MockHttpServletResponse();

		productApi.update(10L, product, store, new MockHttpServletRequest(), response);

		verify(productCommonFacade, times(1)).saveProduct(store, product, store.getDefaultLanguage());
		assertEquals(HttpStatus.OK.value(), response.getStatus());
	}

	@Test
	public void propagatesResourceNotFoundWithoutSendingServiceUnavailable() throws Exception {
		MerchantStore store = store();
		PersistableProduct product = product(10L);
		MockHttpServletResponse response = new MockHttpServletResponse();
		ResourceNotFoundException expected = new ResourceNotFoundException("missing product");
		when(productCommonFacade.saveProduct(store, product, store.getDefaultLanguage())).thenThrow(expected);

		try {
			productApi.update(10L, product, store, new MockHttpServletRequest(), response);
			fail("Expected ResourceNotFoundException");
		} catch (ResourceNotFoundException actual) {
			assertSame(expected, actual);
		}

		assertFalse(HttpStatus.SERVICE_UNAVAILABLE.value() == response.getStatus());
		assertEquals(HttpStatus.OK.value(), response.getStatus());
	}

	@Test
	public void convertsUnexpectedFacadeFailureToServiceUnavailable() throws Exception {
		MerchantStore store = store();
		PersistableProduct product = product(10L);
		MockHttpServletResponse response = new MockHttpServletResponse();
		when(productCommonFacade.saveProduct(store, product, store.getDefaultLanguage()))
				.thenThrow(new RuntimeException("unexpected"));

		productApi.update(10L, product, store, new MockHttpServletRequest(), response);

		assertEquals(HttpStatus.SERVICE_UNAVAILABLE.value(), response.getStatus());
	}

	@Test
	public void productWriteHandlersUsePrivatePathsAndHaveNoAuthAliases() throws Exception {
		for (Method method : ProductApi.class.getDeclaredMethods()) {
			RequestMapping mapping = AnnotatedElementUtils.findMergedAnnotation(method, RequestMapping.class);
			if (mapping != null) {
				for (String path : mapping.path()) {
					assertFalse("Unexpected auth alias on " + method.getName() + ": " + path,
							path.startsWith("/auth/"));
				}
			}
		}

		assertPath("create", new Class<?>[] { PersistableProduct.class, MerchantStore.class, Language.class },
				"/private/product");
		assertPath("update",
				new Class<?>[] { Long.class, PersistableProduct.class, MerchantStore.class,
						HttpServletRequest.class, HttpServletResponse.class },
				"/private/product/{id}");
		assertPath("delete", new Class<?>[] { Long.class, MerchantStore.class, Language.class },
				"/private/product/{id}");
		assertPath("changeProductOrder",
				new Class<?>[] { Long.class, Integer.class, MerchantStore.class, Language.class },
				"/private/product/{id}");

		assertPath("update",
				new Class<?>[] { Long.class, LightPersistableProduct.class, MerchantStore.class, Language.class },
				"/private/product/{id}");
	}

	private void assertPath(String methodName, Class<?>[] parameters, String expectedPath) throws Exception {
		Method method = ProductApi.class.getMethod(methodName, parameters);
		RequestMapping mapping = AnnotatedElementUtils.findMergedAnnotation(method, RequestMapping.class);
		assertArrayEquals(new String[] { expectedPath }, mapping.path());
	}

	private MerchantStore store() {
		MerchantStore store = new MerchantStore();
		store.setDefaultLanguage(new Language());
		return store;
	}

	private PersistableProduct product(Long id) {
		PersistableProduct product = new PersistableProduct();
		product.setId(id);
		return product;
	}
}
