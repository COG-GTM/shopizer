package com.salesmanager.test.shop.unit.product;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.lang.reflect.Method;
import java.util.Arrays;
import java.util.List;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import org.junit.Before;
import org.junit.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;

import com.salesmanager.core.business.services.catalog.product.ProductService;
import com.salesmanager.core.business.services.catalog.product.image.ProductImageService;
import com.salesmanager.core.model.catalog.product.Product;
import com.salesmanager.core.model.catalog.product.image.ProductImage;
import com.salesmanager.core.model.merchant.MerchantStore;
import com.salesmanager.shop.constants.Constants;
import com.salesmanager.shop.store.api.exception.ResourceNotFoundException;
import com.salesmanager.shop.store.api.exception.UnauthorizedException;
import com.salesmanager.shop.store.api.v1.product.ProductImageApi;
import com.salesmanager.shop.utils.AuthorizationUtils;

/**
 * Unit tests for ProductImageApi#deleteImage(Long, MerchantStore, ...) store scoping (PR #137).
 */
public class ProductImageApiDeleteTest {

	private static final String USER = "admin@store.com";

	private ProductImageApi api;
	private ProductImageService productImageService;
	private AuthorizationUtils authorizationUtils;
	private HttpServletRequest request;
	private HttpServletResponse response;

	private MerchantStore defaultStore;
	private MerchantStore otherStore;

	@Before
	public void setUp() {
		api = new ProductImageApi();
		productImageService = mock(ProductImageService.class);
		authorizationUtils = mock(AuthorizationUtils.class);
		ReflectionTestUtils.setField(api, "productImageService", productImageService);
		ReflectionTestUtils.setField(api, "productService", mock(ProductService.class));
		ReflectionTestUtils.setField(api, "authorizationUtils", authorizationUtils);
		request = mock(HttpServletRequest.class);
		response = mock(HttpServletResponse.class);

		when(authorizationUtils.authenticatedUser()).thenReturn(USER);

		defaultStore = store(1, "DEFAULT");
		otherStore = store(2, "OTHER");
	}

	@Test
	public void deletesImageOwnedByCallerStore() throws Exception {
		ProductImage image = image(10L, defaultStore);
		when(productImageService.getById(10L)).thenReturn(image);

		api.deleteImage(10L, defaultStore, request, response);

		verify(productImageService).delete(image);
	}

	@Test
	public void storeIdComparisonIsByValueNotIdentity() throws Exception {
		// Integer ids above the Integer cache range must still match
		MerchantStore callerStore = store(1000, "BIG");
		ProductImage image = image(11L, store(1000, "BIG"));
		when(productImageService.getById(11L)).thenReturn(image);

		api.deleteImage(11L, callerStore, request, response);

		verify(productImageService).delete(image);
	}

	@Test
	public void imageOwnedByAnotherStoreIsNotFoundAndNotDeleted() throws Exception {
		ProductImage image = image(20L, otherStore);
		when(productImageService.getById(20L)).thenReturn(image);

		try {
			api.deleteImage(20L, defaultStore, request, response);
			throw new AssertionError("expected ResourceNotFoundException");
		} catch (ResourceNotFoundException expected) {
			assertTrue(expected.getErrorMessage().contains("20"));
		}
		verify(productImageService, never()).delete(any(ProductImage.class));
	}

	@Test(expected = ResourceNotFoundException.class)
	public void missingImageIsNotFound() throws Exception {
		when(productImageService.getById(30L)).thenReturn(null);
		api.deleteImage(30L, defaultStore, request, response);
	}

	@Test
	public void imageWithoutProductIsNotFoundAndNotDeleted() throws Exception {
		ProductImage image = new ProductImage();
		image.setId(40L);
		when(productImageService.getById(40L)).thenReturn(image);
		try {
			api.deleteImage(40L, defaultStore, request, response);
			throw new AssertionError("expected ResourceNotFoundException");
		} catch (ResourceNotFoundException expected) {
			// expected
		}
		verify(productImageService, never()).delete(any(ProductImage.class));
	}

	@Test
	public void imageWhoseProductHasNoStoreIsNotFoundAndNotDeleted() throws Exception {
		ProductImage image = image(41L, null);
		when(productImageService.getById(41L)).thenReturn(image);
		try {
			api.deleteImage(41L, defaultStore, request, response);
			throw new AssertionError("expected ResourceNotFoundException");
		} catch (ResourceNotFoundException expected) {
			// expected
		}
		verify(productImageService, never()).delete(any(ProductImage.class));
	}

	@Test
	public void unauthorizedUserIsRejectedBeforeImageLookup() throws Exception {
		doThrow(new UnauthorizedException("no")).when(authorizationUtils).authorizeUser(eq(USER), anyList(),
				eq(defaultStore));
		try {
			api.deleteImage(50L, defaultStore, request, response);
			throw new AssertionError("expected UnauthorizedException");
		} catch (UnauthorizedException expected) {
			// expected
		}
		verify(productImageService, never()).getById(anyLong());
		verify(productImageService, never()).delete(any(ProductImage.class));
	}

	@Test
	@SuppressWarnings("unchecked")
	public void authorizesAgainstAdminCatalogueRolesAndResolvedStore() throws Exception {
		when(productImageService.getById(60L)).thenReturn(image(60L, defaultStore));

		api.deleteImage(60L, defaultStore, request, response);

		ArgumentCaptor<List<String>> roles = ArgumentCaptor.forClass(List.class);
		verify(authorizationUtils).authorizeUser(eq(USER), roles.capture(), eq(defaultStore));
		assertEquals(Arrays.asList(Constants.GROUP_SUPERADMIN, Constants.GROUP_ADMIN,
				Constants.GROUP_ADMIN_CATALOGUE, Constants.GROUP_ADMIN_RETAIL), roles.getValue());
	}

	@Test
	public void serviceFailureIsReportedAs503() throws Exception {
		ProductImage image = image(70L, defaultStore);
		when(productImageService.getById(70L)).thenReturn(image);
		doThrow(new RuntimeException("boom")).when(productImageService).delete(image);

		api.deleteImage(70L, defaultStore, request, response);

		verify(response).sendError(eq(503), any(String.class));
	}

	@Test
	public void deleteByIdIsOnlyMappedOnPrivatePath() throws Exception {
		Method m = ProductImageApi.class.getMethod("deleteImage", Long.class, MerchantStore.class,
				HttpServletRequest.class, HttpServletResponse.class);
		RequestMapping mapping = m.getAnnotation(RequestMapping.class);
		assertArrayEquals(new String[] { "/private/product/image/{id}" }, mapping.value());
		assertArrayEquals(new RequestMethod[] { RequestMethod.DELETE }, mapping.method());
	}

	@Test
	public void noHandlerMapsCustomerImageDeletePath() {
		for (Method m : ProductImageApi.class.getDeclaredMethods()) {
			RequestMapping mapping = m.getAnnotation(RequestMapping.class);
			if (mapping == null) {
				continue;
			}
			for (String path : mapping.value()) {
				assertTrue("unexpected mapping " + path, !path.startsWith("/auth/product/images"));
			}
		}
	}

	private static MerchantStore store(int id, String code) {
		MerchantStore s = new MerchantStore();
		s.setId(Integer.valueOf(id));
		s.setCode(code);
		return s;
	}

	private static ProductImage image(Long id, MerchantStore owner) {
		Product p = new Product();
		p.setId(id * 100);
		p.setMerchantStore(owner);
		ProductImage img = new ProductImage();
		img.setId(id);
		img.setProduct(p);
		return img;
	}
}
