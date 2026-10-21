package com.salesmanager.shop.store.api.v1.product;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotSame;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.assertThrows;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.lang.reflect.Method;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnitRunner;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;

import com.salesmanager.core.business.exception.ServiceException;
import com.salesmanager.core.business.services.catalog.product.ProductService;
import com.salesmanager.core.business.services.catalog.product.image.ProductImageService;
import com.salesmanager.core.model.catalog.product.Product;
import com.salesmanager.core.model.catalog.product.image.ProductImage;
import com.salesmanager.core.model.merchant.MerchantStore;
import com.salesmanager.core.model.reference.language.Language;
import com.salesmanager.shop.mapper.catalog.ReadableProductImageMapper;
import com.salesmanager.shop.model.catalog.product.ReadableImage;
import com.salesmanager.shop.store.api.exception.ResourceNotFoundException;
import com.salesmanager.shop.store.api.exception.ServiceRuntimeException;

@RunWith(MockitoJUnitRunner.class)
public class ProductImageApiStoreOwnershipTest {

	@Mock
	private ProductImageService productImageService;

	@Mock
	private ProductService productService;

	@Mock
	private ReadableProductImageMapper readableProductImageMapper;

	@InjectMocks
	private ProductImageApi productImageApi;

	@Test
	public void deleteImageWithNullProductReturnsNotFound() throws Exception {
		ProductImage image = org.mockito.Mockito.mock(ProductImage.class);
		when(productImageService.getById(10L)).thenReturn(image);
		when(image.getProduct()).thenReturn(null);
		MockHttpServletResponse response = new MockHttpServletResponse();

		productImageApi.deleteImage(10L, storeWithId(1000), new MockHttpServletRequest(), response);

		assertEquals(404, response.getStatus());
		verify(productImageService, never()).delete(image);
	}

	@Test
	public void deleteImageWithNullProductStoreReturnsNotFound() throws Exception {
		ProductImage image = org.mockito.Mockito.mock(ProductImage.class);
		Product product = org.mockito.Mockito.mock(Product.class);
		when(productImageService.getById(10L)).thenReturn(image);
		when(image.getProduct()).thenReturn(product);
		when(product.getMerchantStore()).thenReturn(null);
		MockHttpServletResponse response = new MockHttpServletResponse();

		productImageApi.deleteImage(10L, storeWithId(1000), new MockHttpServletRequest(), response);

		assertEquals(404, response.getStatus());
		verify(productImageService, never()).delete(image);
	}

	@Test
	public void deleteImageAcceptsEqualStoreIdsWithDistinctReferences() throws Exception {
		Integer imageStoreId = Integer.valueOf(1000);
		Integer requestStoreId = Integer.valueOf(1000);
		assertNotSame(imageStoreId, requestStoreId);
		ProductImage image = imageForStore(imageStoreId);
		when(productImageService.getById(10L)).thenReturn(image);
		MockHttpServletResponse response = new MockHttpServletResponse();

		productImageApi.deleteImage(10L, storeWithId(requestStoreId), new MockHttpServletRequest(), response);

		assertEquals(200, response.getStatus());
		verify(productImageService, times(1)).delete(image);
	}

	@Test
	public void deleteImageLookupFailureReturnsServiceUnavailable() throws Exception {
		when(productImageService.getById(10L)).thenThrow(new IllegalStateException("lookup failed"));
		MockHttpServletResponse response = new MockHttpServletResponse();

		productImageApi.deleteImage(10L, storeWithId(1000), new MockHttpServletRequest(), response);

		assertEquals(503, response.getStatus());
		verify(productImageService, never()).delete(org.mockito.ArgumentMatchers.any(ProductImage.class));
	}

	@Test
	public void deleteImageServiceFailureReturnsServiceUnavailable() throws Exception {
		ProductImage image = imageForStore(1000);
		when(productImageService.getById(10L)).thenReturn(image);
		doThrow(new ServiceException("delete failed")).when(productImageService).delete(image);
		MockHttpServletResponse response = new MockHttpServletResponse();

		productImageApi.deleteImage(10L, storeWithId(1000), new MockHttpServletRequest(), response);

		assertEquals(503, response.getStatus());
	}

	@Test
	public void imagesForMissingProductThrowsNotFound() {
		MerchantStore store = storeWithId(1000);
		when(productService.getById(12L)).thenReturn(null);

		assertThrows(ResourceNotFoundException.class,
				() -> productImageApi.images(12L, store, org.mockito.Mockito.mock(Language.class)));
	}

	@Test
	public void imagesForProductInAnotherStoreThrowsNotFound() {
		MerchantStore requestStore = storeWithId(1000);
		Product product = org.mockito.Mockito.mock(Product.class);
		MerchantStore productStore = storeWithId(1001);
		when(productService.getById(12L)).thenReturn(product);
		when(product.getMerchantStore()).thenReturn(productStore);

		assertThrows(ResourceNotFoundException.class,
				() -> productImageApi.images(12L, requestStore, org.mockito.Mockito.mock(Language.class)));
	}

	@Test
	public void imagesForSameStoreWithDistinctIdReferencesAreSorted() {
		Integer productStoreId = Integer.valueOf(1000);
		Integer requestStoreId = Integer.valueOf(1000);
		assertNotSame(productStoreId, requestStoreId);
		MerchantStore requestStore = storeWithId(requestStoreId);
		Language language = org.mockito.Mockito.mock(Language.class);
		Product product = org.mockito.Mockito.mock(Product.class);
		MerchantStore productStore = storeWithId(productStoreId);
		ProductImage firstImage = org.mockito.Mockito.mock(ProductImage.class);
		ProductImage secondImage = org.mockito.Mockito.mock(ProductImage.class);
		ReadableImage firstReadableImage = new ReadableImage();
		firstReadableImage.setOrder(1);
		ReadableImage secondReadableImage = new ReadableImage();
		secondReadableImage.setOrder(2);
		Set<ProductImage> productImages = new HashSet<>(Arrays.asList(firstImage, secondImage));
		when(productService.getById(12L)).thenReturn(product);
		when(product.getMerchantStore()).thenReturn(productStore);
		when(product.getImages()).thenReturn(productImages);
		when(readableProductImageMapper.convert(firstImage, requestStore, language)).thenReturn(secondReadableImage);
		when(readableProductImageMapper.convert(secondImage, requestStore, language)).thenReturn(firstReadableImage);

		List<ReadableImage> result = productImageApi.images(12L, requestStore, language);

		assertEquals(2, result.size());
		assertSame(firstReadableImage, result.get(0));
		assertSame(secondReadableImage, result.get(1));
	}

	@Test
	public void imageDetailsForAnotherStoreThrowsAndDoesNotUpdate() {
		MerchantStore requestStore = storeWithId(1000);
		Product product = org.mockito.Mockito.mock(Product.class);
		MerchantStore productStore = storeWithId(1001);
		when(productService.getById(12L)).thenReturn(product);
		when(product.getMerchantStore()).thenReturn(productStore);

		assertThrows(ServiceRuntimeException.class,
				() -> productImageApi.imageDetails(12L, 10L, 5, requestStore, org.mockito.Mockito.mock(Language.class)));
		verify(productImageService, never()).updateProductImage(eq(product),
				org.mockito.ArgumentMatchers.any(ProductImage.class));
	}

	@Test
	public void imageDetailsUpdatesForSameStoreWithDistinctIdReferences() throws Exception {
		Integer productStoreId = Integer.valueOf(1000);
		Integer requestStoreId = Integer.valueOf(1000);
		assertNotSame(productStoreId, requestStoreId);
		MerchantStore requestStore = storeWithId(requestStoreId);
		Language language = org.mockito.Mockito.mock(Language.class);
		Product product = org.mockito.Mockito.mock(Product.class);
		MerchantStore productStore = storeWithId(productStoreId);
		ProductImage image = org.mockito.Mockito.mock(ProductImage.class);
		when(productService.getById(12L)).thenReturn(product);
		when(product.getMerchantStore()).thenReturn(productStore);
		when(productImageService.getProductImage(10L, 12L, requestStore)).thenReturn(Optional.of(image));

		productImageApi.imageDetails(12L, 10L, 5, requestStore, language);

		verify(image).setSortOrder(5);
		verify(productImageService, times(1)).updateProductImage(product, image);
	}

	@Test
	public void mappingsKeepMutationsPrivateAndPublicImageReadAvailable() {
		boolean foundDeleteImage = false;
		boolean foundPublicImageRead = false;

		for (Method method : ProductImageApi.class.getDeclaredMethods()) {
			RequestMapping mapping = method.getAnnotation(RequestMapping.class);
			if (mapping == null) {
				continue;
			}

			boolean mutation = Arrays.stream(mapping.method()).anyMatch(requestMethod ->
					requestMethod == RequestMethod.POST || requestMethod == RequestMethod.DELETE
							|| requestMethod == RequestMethod.PATCH);
			if (mutation) {
				for (String path : mapping.value()) {
					assertTrue(path.startsWith("/private/"));
					if (requestMethodIs(mapping, RequestMethod.DELETE)
							&& "/private/product/image/{id}".equals(path)) {
						foundDeleteImage = true;
						assertTrue(Arrays.stream(method.getParameterTypes())
								.anyMatch(MerchantStore.class::equals));
					}
				}
			}

			if (requestMethodIs(mapping, RequestMethod.GET)
					&& Arrays.asList(mapping.value()).contains("/product/{productId}/images")) {
				foundPublicImageRead = true;
			}
		}

		assertTrue(foundDeleteImage);
		assertTrue(foundPublicImageRead);
	}

	private boolean requestMethodIs(RequestMapping mapping, RequestMethod expected) {
		return Arrays.asList(mapping.method()).contains(expected);
	}

	private ProductImage imageForStore(Integer storeId) {
		ProductImage image = org.mockito.Mockito.mock(ProductImage.class);
		Product product = org.mockito.Mockito.mock(Product.class);
		MerchantStore store = storeWithId(storeId);
		when(image.getProduct()).thenReturn(product);
		when(product.getMerchantStore()).thenReturn(store);
		return image;
	}

	private MerchantStore storeWithId(Integer id) {
		MerchantStore store = org.mockito.Mockito.mock(MerchantStore.class);
		when(store.getId()).thenReturn(id);
		return store;
	}
}
