package com.salesmanager.shop.store.api.v1.product;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.lang.reflect.Method;
import java.util.Arrays;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.web.bind.annotation.RequestMapping;

import com.salesmanager.core.business.services.catalog.product.ProductService;
import com.salesmanager.core.business.services.catalog.product.image.ProductImageService;
import com.salesmanager.core.model.catalog.product.Product;
import com.salesmanager.core.model.catalog.product.image.ProductImage;
import com.salesmanager.core.model.merchant.MerchantStore;
import com.salesmanager.shop.store.api.exception.UnauthorizedException;
import com.salesmanager.shop.utils.AuthorizationUtils;

@ExtendWith(MockitoExtension.class)
class ProductImageApiTest {

	private static final String ADMIN = "admin@store-b.com";

	@Mock
	private ProductImageService productImageService;

	@Mock
	private ProductService productService;

	@Mock
	private AuthorizationUtils authorizationUtils;

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
	void deleteByIdRejectsImageOfAnotherStore() throws Exception {
		when(authorizationUtils.authenticatedUser()).thenReturn(ADMIN);
		ProductImage image = image(57L, storeA);
		when(productImageService.getById(57L)).thenReturn(image);

		MockHttpServletResponse response = new MockHttpServletResponse();
		api.deleteImage(57L, storeB, new MockHttpServletRequest(), response);

		assertEquals(404, response.getStatus());
		verify(productImageService, never()).delete(any(ProductImage.class));
	}

	@Test
	void deleteByIdDeletesImageOfCallerStore() throws Exception {
		when(authorizationUtils.authenticatedUser()).thenReturn(ADMIN);
		ProductImage image = image(57L, storeB);
		when(productImageService.getById(57L)).thenReturn(image);

		MockHttpServletResponse response = new MockHttpServletResponse();
		api.deleteImage(57L, storeB, new MockHttpServletRequest(), response);

		assertEquals(200, response.getStatus());
		verify(productImageService).delete(image);
		verify(authorizationUtils).authorizeUser(eq(ADMIN), anyList(), eq(storeB));
	}

	@Test
	void deleteByIdComparesStoreIdsByValue() throws Exception {
		when(authorizationUtils.authenticatedUser()).thenReturn(ADMIN);
		MerchantStore bigIdStore = store(1000, "BIG");
		ProductImage image = image(57L, store(1000, "BIG"));
		when(productImageService.getById(57L)).thenReturn(image);

		MockHttpServletResponse response = new MockHttpServletResponse();
		api.deleteImage(57L, bigIdStore, new MockHttpServletRequest(), response);

		verify(productImageService).delete(image);
	}

	@Test
	void deleteByIdReturns404WhenImageMissing() throws Exception {
		when(authorizationUtils.authenticatedUser()).thenReturn(ADMIN);
		when(productImageService.getById(99L)).thenReturn(null);

		MockHttpServletResponse response = new MockHttpServletResponse();
		api.deleteImage(99L, storeB, new MockHttpServletRequest(), response);

		assertEquals(404, response.getStatus());
		verify(productImageService, never()).delete(any(ProductImage.class));
	}

	@Test
	void deleteByIdRequiresCatalogueAdminOfStore() throws Exception {
		when(authorizationUtils.authenticatedUser()).thenReturn("customer@test.com");
		doThrow(new UnauthorizedException("no")).when(authorizationUtils).authorizeUser(eq("customer@test.com"),
				anyList(), eq(storeA));

		assertThrows(UnauthorizedException.class,
				() -> api.deleteImage(57L, storeA, new MockHttpServletRequest(), new MockHttpServletResponse()));
		verify(productImageService, never()).getById(anyLong());
		verify(productImageService, never()).delete(any(ProductImage.class));
	}

	@Test
	void productScopedDeleteRequiresCatalogueAdminOfStore() throws Exception {
		when(authorizationUtils.authenticatedUser()).thenReturn("customer@test.com");
		doThrow(new UnauthorizedException("no")).when(authorizationUtils).authorizeUser(eq("customer@test.com"),
				anyList(), eq(storeA));

		assertThrows(UnauthorizedException.class, () -> api.deleteImage(1L, 57L, null, storeA, null));
		verify(productImageService, never()).delete(any(ProductImage.class));
	}

	@Test
	void imageWriteEndpointsAreNotExposedUnderCustomerAuthPath() {
		for (Method method : ProductImageApi.class.getDeclaredMethods()) {
			RequestMapping mapping = method.getAnnotation(RequestMapping.class);
			if (mapping == null) {
				continue;
			}
			for (String path : mapping.value()) {
				assertFalse(path.startsWith("/auth/"),
						method.getName() + " is mapped to customer path " + path + " " + Arrays.toString(mapping.method()));
			}
		}
	}

	private static MerchantStore store(int id, String code) {
		MerchantStore store = new MerchantStore();
		store.setId(id);
		store.setCode(code);
		return store;
	}

	private static ProductImage image(Long id, MerchantStore store) {
		Product product = new Product();
		product.setId(10L);
		product.setMerchantStore(store);
		ProductImage image = new ProductImage();
		image.setId(id);
		image.setProduct(product);
		return image;
	}
}
