package com.salesmanager.shop.store.api.v1.product;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.multipart.MultipartFile;

import com.salesmanager.core.business.services.catalog.product.ProductService;
import com.salesmanager.core.business.services.catalog.product.image.ProductImageService;
import com.salesmanager.core.model.catalog.product.Product;
import com.salesmanager.core.model.catalog.product.image.ProductImage;
import com.salesmanager.core.model.merchant.MerchantStore;
import com.salesmanager.shop.constants.Constants;
import com.salesmanager.shop.mapper.catalog.ReadableProductImageMapper;
import com.salesmanager.shop.model.catalog.product.ReadableImage;
import com.salesmanager.shop.store.api.exception.ResourceNotFoundException;
import com.salesmanager.shop.store.api.exception.ServiceRuntimeException;
import com.salesmanager.shop.store.api.exception.UnauthorizedException;
import com.salesmanager.shop.utils.AuthorizationUtils;

/**
 * Store scoping and authorization of the ProductImageApi write routes (PR #186).
 */
@ExtendWith(MockitoExtension.class)
class ProductImageApiStoreScopeTest {

	private static final String ADMIN = "admin@store.com";

	@Mock
	private ProductImageService productImageService;

	@Mock
	private ProductService productService;

	@Mock
	private AuthorizationUtils authorizationUtils;

	@Mock
	private ReadableProductImageMapper readableProductImageMapper;

	@InjectMocks
	private ProductImageApi api;

	private MerchantStore storeA;
	private MerchantStore storeB;

	@BeforeEach
	void setUp() {
		storeA = store(1, "ACME");
		storeB = store(2, "GLOBEX");
	}

	@Test
	void deleteByIdRequiresCatalogueAdminRoles() throws Exception {
		when(authorizationUtils.authenticatedUser()).thenReturn(ADMIN);
		when(productImageService.getById(57L)).thenReturn(image(57L, storeA));

		api.deleteImage(57L, storeA, new MockHttpServletRequest(), new MockHttpServletResponse());

		@SuppressWarnings("unchecked")
		ArgumentCaptor<List<String>> roles = ArgumentCaptor.forClass(List.class);
		verify(authorizationUtils).authorizeUser(eq(ADMIN), roles.capture(), eq(storeA));
		assertEquals(new HashSet<>(Arrays.asList(Constants.GROUP_SUPERADMIN, Constants.GROUP_ADMIN,
				Constants.GROUP_ADMIN_CATALOGUE, Constants.GROUP_ADMIN_RETAIL)), new HashSet<>(roles.getValue()));
	}

	@Test
	void deleteByIdRejectsAnonymousCallerBeforeLookup() {
		when(authorizationUtils.authenticatedUser()).thenThrow(new UnauthorizedException());

		assertThrows(UnauthorizedException.class,
				() -> api.deleteImage(57L, storeA, new MockHttpServletRequest(), new MockHttpServletResponse()));
		verify(productImageService, never()).getById(anyLong());
	}

	@Test
	void deleteByIdReturns404WhenImageHasNoProduct() throws Exception {
		when(authorizationUtils.authenticatedUser()).thenReturn(ADMIN);
		ProductImage orphan = new ProductImage();
		orphan.setId(57L);
		when(productImageService.getById(57L)).thenReturn(orphan);

		MockHttpServletResponse response = new MockHttpServletResponse();
		api.deleteImage(57L, storeA, new MockHttpServletRequest(), response);

		assertEquals(404, response.getStatus());
		verify(productImageService, never()).delete(any(ProductImage.class));
	}

	@Test
	void deleteByIdReturns404WhenProductHasNoStore() throws Exception {
		when(authorizationUtils.authenticatedUser()).thenReturn(ADMIN);
		when(productImageService.getById(57L)).thenReturn(image(57L, null));

		MockHttpServletResponse response = new MockHttpServletResponse();
		api.deleteImage(57L, storeA, new MockHttpServletRequest(), response);

		assertEquals(404, response.getStatus());
		verify(productImageService, never()).delete(any(ProductImage.class));
	}

	@Test
	void deleteByIdOtherStoreResponseDoesNotDifferFromMissingImage() throws Exception {
		when(authorizationUtils.authenticatedUser()).thenReturn(ADMIN);
		when(productImageService.getById(57L)).thenReturn(image(57L, storeA));
		when(productImageService.getById(58L)).thenReturn(null);

		MockHttpServletResponse otherStore = new MockHttpServletResponse();
		api.deleteImage(57L, storeB, new MockHttpServletRequest(), otherStore);
		MockHttpServletResponse missing = new MockHttpServletResponse();
		api.deleteImage(58L, storeB, new MockHttpServletRequest(), missing);

		assertEquals(missing.getStatus(), otherStore.getStatus());
		assertEquals(404, otherStore.getStatus());
	}

	@Test
	void productScopedDeleteDeletesImageFoundForStore() throws Exception {
		when(authorizationUtils.authenticatedUser()).thenReturn(ADMIN);
		ProductImage image = image(57L, storeA);
		when(productImageService.getProductImage(57L, 10L, storeA)).thenReturn(Optional.of(image));

		api.deleteImage(10L, 57L, null, storeA, null);

		verify(authorizationUtils).authorizeUser(eq(ADMIN), anyList(), eq(storeA));
		verify(productImageService).delete(image);
	}

	@Test
	void productScopedDeleteThrowsNotFoundWhenImageNotInStore() throws Exception {
		when(authorizationUtils.authenticatedUser()).thenReturn(ADMIN);
		when(productImageService.getProductImage(57L, 10L, storeB)).thenReturn(Optional.empty());

		assertThrows(ResourceNotFoundException.class, () -> api.deleteImage(10L, 57L, null, storeB, null));
		verify(productImageService, never()).delete(any(ProductImage.class));
	}

	@Test
	void uploadRequiresCatalogueAdminOfStoreBeforeProductLookup() throws Exception {
		when(authorizationUtils.authenticatedUser()).thenReturn("customer@test.com");
		doThrow(new UnauthorizedException("no")).when(authorizationUtils).authorizeUser(eq("customer@test.com"),
				anyList(), eq(storeA));

		assertThrows(UnauthorizedException.class,
				() -> api.uploadImage(10L, new MultipartFile[] { jpg() }, 0, false, storeA, null));
		verify(productService, never()).getById(anyLong());
		verify(productImageService, never()).addProductImages(any(), any());
	}

	@Test
	void uploadAddsImageForSameStoreProduct() throws Exception {
		when(authorizationUtils.authenticatedUser()).thenReturn(ADMIN);
		Product product = product(10L, store(1000, "BIG"));
		when(productService.getById(10L)).thenReturn(product);

		api.uploadImage(10L, new MultipartFile[] { jpg() }, 0, false, store(1000, "BIG"), null);

		verify(productImageService).addProductImages(eq(product), anyList());
	}

	@Test
	void uploadRejectsProductOfAnotherStore() throws Exception {
		when(authorizationUtils.authenticatedUser()).thenReturn(ADMIN);
		when(productService.getById(10L)).thenReturn(product(10L, storeA));

		assertThrows(ServiceRuntimeException.class,
				() -> api.uploadImage(10L, new MultipartFile[] { jpg() }, 0, false, storeB, null));
		verify(productImageService, never()).addProductImages(any(), any());
	}

	@Test
	void patchRequiresCatalogueAdminOfStoreBeforeProductLookup() throws Exception {
		when(authorizationUtils.authenticatedUser()).thenReturn("customer@test.com");
		doThrow(new UnauthorizedException("no")).when(authorizationUtils).authorizeUser(eq("customer@test.com"),
				anyList(), eq(storeA));

		assertThrows(UnauthorizedException.class, () -> api.imageDetails(10L, 57L, 3, storeA, null));
		verify(productService, never()).getById(anyLong());
		verify(productImageService, never()).updateProductImage(any(), any());
	}

	@Test
	void patchUpdatesSortOrderWhenStoreIdsAreEqualButNotCached() throws Exception {
		when(authorizationUtils.authenticatedUser()).thenReturn(ADMIN);
		MerchantStore callerStore = store(1000, "BIG");
		Product product = product(10L, store(1000, "BIG"));
		ProductImage image = image(57L, product.getMerchantStore());
		when(productService.getById(10L)).thenReturn(product);
		when(productImageService.getProductImage(57L, 10L, callerStore)).thenReturn(Optional.of(image));

		api.imageDetails(10L, 57L, 3, callerStore, null);

		assertEquals(Integer.valueOf(3), image.getSortOrder());
		verify(productImageService).updateProductImage(product, image);
	}

	@Test
	void patchRejectsProductOfAnotherStore() throws Exception {
		when(authorizationUtils.authenticatedUser()).thenReturn(ADMIN);
		when(productService.getById(10L)).thenReturn(product(10L, storeA));

		assertThrows(ServiceRuntimeException.class, () -> api.imageDetails(10L, 57L, 3, storeB, null));
		verify(productImageService, never()).updateProductImage(any(), any());
	}

	@Test
	void publicImageListWorksForStoreIdsAbove127() {
		Product product = product(10L, store(1000, "BIG"));
		ProductImage image = image(57L, product.getMerchantStore());
		product.getImages().add(image);
		when(productService.getById(10L)).thenReturn(product);
		ReadableImage readable = new ReadableImage();
		readable.setId(57L);
		MerchantStore callerStore = store(1000, "BIG");
		when(readableProductImageMapper.convert(image, callerStore, null)).thenReturn(readable);

		List<ReadableImage> images = api.images(10L, callerStore, null);

		assertEquals(1, images.size());
		assertEquals(Long.valueOf(57L), images.get(0).getId());
	}

	@Test
	void publicImageListRejectsProductOfAnotherStore() {
		when(productService.getById(10L)).thenReturn(product(10L, storeA));

		assertThrows(ResourceNotFoundException.class, () -> api.images(10L, storeB, null));
	}

	@Test
	void everyWriteRouteIsPrivateAndReadRouteStaysPublic() {
		List<String> writePaths = new ArrayList<>();
		List<String> readPaths = new ArrayList<>();
		for (Method method : ProductImageApi.class.getDeclaredMethods()) {
			RequestMapping mapping = method.getAnnotation(RequestMapping.class);
			if (mapping == null) {
				continue;
			}
			boolean read = Arrays.asList(mapping.method()).contains(RequestMethod.GET);
			(read ? readPaths : writePaths).addAll(Arrays.asList(mapping.value()));
		}
		assertEquals(4, writePaths.size(), writePaths.toString());
		for (String path : writePaths) {
			assertTrue(path.startsWith("/private/"), path);
		}
		assertEquals(Arrays.asList("/product/{productId}/images"), readPaths);
	}

	private static MockMultipartFile jpg() {
		return new MockMultipartFile("file", "image.jpg", "image/jpeg", new byte[] { 1, 2, 3 });
	}

	private static MerchantStore store(int id, String code) {
		MerchantStore store = new MerchantStore();
		store.setId(new Integer(id));
		store.setCode(code);
		return store;
	}

	private static Product product(Long id, MerchantStore store) {
		Product product = new Product();
		product.setId(id);
		product.setMerchantStore(store);
		return product;
	}

	private static ProductImage image(Long id, MerchantStore store) {
		ProductImage image = new ProductImage();
		image.setId(id);
		image.setProduct(product(10L, store));
		return image;
	}
}
