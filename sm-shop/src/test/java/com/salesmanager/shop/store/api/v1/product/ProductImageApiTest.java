package com.salesmanager.shop.store.api.v1.product;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.lang.reflect.Method;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnitRunner;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.web.bind.annotation.RequestMapping;

import com.salesmanager.core.business.services.catalog.product.ProductService;
import com.salesmanager.core.business.services.catalog.product.image.ProductImageService;
import com.salesmanager.core.model.catalog.product.Product;
import com.salesmanager.core.model.catalog.product.image.ProductImage;
import com.salesmanager.core.model.merchant.MerchantStore;
import com.salesmanager.shop.mapper.catalog.ReadableProductImageMapper;

@RunWith(MockitoJUnitRunner.class)
public class ProductImageApiTest {

	@Mock
	private ProductImageService productImageService;

	@Mock
	private ProductService productService;

	@Mock
	private ReadableProductImageMapper readableProductImageMapper;

	@InjectMocks
	private ProductImageApi productImageApi;

	@Test
	public void deleteImageWhenImageBelongsToAnotherStoreReturnsNotFound() throws Exception {
		ProductImage productImage = imageForStore(2);
		when(productImageService.getById(10L)).thenReturn(productImage);
		MockHttpServletResponse response = new MockHttpServletResponse();

		productImageApi.deleteImage(10L, storeWithId(1), new MockHttpServletRequest(), response);

		assertEquals(404, response.getStatus());
		verify(productImageService, never()).delete(productImage);
	}

	@Test
	public void deleteImageWhenImageBelongsToRequestStoreDeletesImage() throws Exception {
		ProductImage productImage = imageForStore(1);
		when(productImageService.getById(10L)).thenReturn(productImage);
		MockHttpServletResponse response = new MockHttpServletResponse();

		productImageApi.deleteImage(10L, storeWithId(1), new MockHttpServletRequest(), response);

		verify(productImageService, times(1)).delete(productImage);
	}

	@Test
	public void deleteImageWhenImageDoesNotExistReturnsNotFound() throws Exception {
		when(productImageService.getById(10L)).thenReturn(null);
		MockHttpServletResponse response = new MockHttpServletResponse();

		productImageApi.deleteImage(10L, storeWithId(1), new MockHttpServletRequest(), response);

		assertEquals(404, response.getStatus());
		verify(productImageService, never()).delete(org.mockito.ArgumentMatchers.any(ProductImage.class));
	}

	@Test
	public void requestMappingsDoNotIncludeAuthAliases() {
		for (Method method : ProductImageApi.class.getDeclaredMethods()) {
			RequestMapping requestMapping = method.getAnnotation(RequestMapping.class);
			if (requestMapping != null) {
				for (String value : requestMapping.value()) {
					assertFalse(value.contains("/auth/"));
				}
			}
		}
	}

	private ProductImage imageForStore(Integer storeId) {
		ProductImage productImage = org.mockito.Mockito.mock(ProductImage.class);
		Product product = org.mockito.Mockito.mock(Product.class);
		MerchantStore merchantStore = storeWithId(storeId);
		when(productImage.getProduct()).thenReturn(product);
		when(product.getMerchantStore()).thenReturn(merchantStore);
		return productImage;
	}

	private MerchantStore storeWithId(Integer id) {
		MerchantStore merchantStore = org.mockito.Mockito.mock(MerchantStore.class);
		when(merchantStore.getId()).thenReturn(id);
		return merchantStore;
	}
}
