package com.salesmanager.shop.store.api.v1.product;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertThrows;
import static org.junit.Assert.assertTrue;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.io.ByteArrayInputStream;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnitRunner;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.multipart.MultipartFile;

import com.salesmanager.core.business.services.catalog.product.ProductService;
import com.salesmanager.core.business.services.catalog.product.image.ProductImageService;
import com.salesmanager.core.model.catalog.product.Product;
import com.salesmanager.core.model.catalog.product.image.ProductImage;
import com.salesmanager.core.model.merchant.MerchantStore;
import com.salesmanager.core.model.reference.language.Language;
import com.salesmanager.shop.constants.Constants;
import com.salesmanager.shop.mapper.catalog.ReadableProductImageMapper;
import com.salesmanager.shop.model.entity.NameEntity;
import com.salesmanager.shop.store.api.exception.UnauthorizedException;
import com.salesmanager.shop.store.controller.user.facade.UserFacade;

@RunWith(MockitoJUnitRunner.class)
public class ProductImageApiAuthorizationTest {

	private static final Long PRODUCT_ID = 23L;
	private static final Long IMAGE_ID = 47L;
	private static final String USERNAME = "image-admin";

	@InjectMocks
	private ProductImageApi api;

	@Mock
	private ProductImageService productImageService;

	@Mock
	private ProductService productService;

	@Mock
	private UserFacade userFacade;

	@Mock
	private ReadableProductImageMapper readableProductImageMapper;

	@Mock
	private HttpServletRequest request;

	@Mock
	private HttpServletResponse response;

	@Captor
	private ArgumentCaptor<List<String>> authorizedGroups;

	private MerchantStore requestStore;

	@Before
	public void setUp() {
		when(userFacade.authenticatedUser()).thenReturn(USERNAME);
		requestStore = store(1L);
	}

	@Test
	public void legacyDeleteRejectsMissingAuthenticatedUserBeforeLookingUpImage() {
		when(userFacade.authenticatedUser()).thenReturn(null);

		assertThrows(UnauthorizedException.class, () -> invokeLegacyDelete());

		verifyNoInteractions(productImageService);
	}

	@Test
	public void uploadRejectsUnauthorizedGroupBeforeCallingServices() throws Exception {
		rejectGroup();

		assertThrows(UnauthorizedException.class, () -> upload());

		verifyNoInteractions(productService, productImageService);
	}

	@Test
	public void legacyDeleteRejectsUnauthorizedGroupBeforeCallingServices() throws Exception {
		rejectGroup();

		assertThrows(UnauthorizedException.class, () -> invokeLegacyDelete());

		verifyNoInteractions(productService, productImageService);
	}

	@Test
	public void productDeleteRejectsUnauthorizedGroupBeforeCallingServices() {
		rejectGroup();

		assertThrows(UnauthorizedException.class, () -> api.deleteImage(PRODUCT_ID, IMAGE_ID,
				new NameEntity(), requestStore, new Language()));

		verifyNoInteractions(productService, productImageService);
	}

	@Test
	public void imageDetailsRejectsUnauthorizedGroupBeforeCallingServices() throws Exception {
		rejectGroup();

		assertThrows(UnauthorizedException.class,
				() -> api.imageDetails(PRODUCT_ID, IMAGE_ID, 3, requestStore, new Language()));

		verifyNoInteractions(productService, productImageService);
	}

	@Test
	public void allWriteHandlersAuthorizeTheSameAdministrativeGroups() throws Exception {
		Product product = productInStore(1L);
		ProductImage image = image(product);
		when(productService.getById(PRODUCT_ID)).thenReturn(product);
		when(productImageService.getById(IMAGE_ID)).thenReturn(image);
		when(productImageService.getProductImage(IMAGE_ID, PRODUCT_ID, requestStore))
				.thenReturn(Optional.of(image));

		upload();
		invokeLegacyDelete();
		api.deleteImage(PRODUCT_ID, IMAGE_ID, new NameEntity(), requestStore, new Language());
		api.imageDetails(PRODUCT_ID, IMAGE_ID, 3, requestStore, new Language());

		List<String> expected = Arrays.asList(Constants.GROUP_SUPERADMIN, Constants.GROUP_ADMIN,
				Constants.GROUP_ADMIN_CATALOGUE, Constants.GROUP_ADMIN_RETAIL);
		verify(userFacade, times(4)).authorizedGroup(eq(USERNAME), authorizedGroups.capture());
		for (List<String> actual : authorizedGroups.getAllValues()) {
			assertEquals(expected, actual);
		}
	}

	@Test
	public void legacyDeleteReturnsNotFoundWhenImageBelongsToAnotherStore() throws Exception {
		ProductImage image = image(productInStore(2L));
		when(productImageService.getById(IMAGE_ID)).thenReturn(image);

		invokeLegacyDelete();

		verify(response).sendError(eq(404), org.mockito.ArgumentMatchers.anyString());
		verify(productImageService, never()).delete(image);
	}

	@Test
	public void legacyDeleteReturnsNotFoundWhenImageDoesNotExist() throws Exception {
		when(productImageService.getById(IMAGE_ID)).thenReturn(null);

		invokeLegacyDelete();

		verify(response).sendError(eq(404), org.mockito.ArgumentMatchers.anyString());
		verify(productImageService, never()).delete(org.mockito.ArgumentMatchers.any(ProductImage.class));
	}

	@Test
	public void legacyDeleteReturnsNotFoundWhenImageHasNoProduct() throws Exception {
		when(productImageService.getById(IMAGE_ID)).thenReturn(image(null));

		invokeLegacyDelete();

		verify(response).sendError(eq(404), org.mockito.ArgumentMatchers.anyString());
		verify(productImageService, never()).delete(org.mockito.ArgumentMatchers.any(ProductImage.class));
	}

	@Test
	public void legacyDeleteRemovesImageFromTheRequestedStore() throws Exception {
		ProductImage image = image(productInStore(1L));
		when(productImageService.getById(IMAGE_ID)).thenReturn(image);

		invokeLegacyDelete();

		verify(productImageService).delete(image);
		verify(response, never()).sendError(org.mockito.ArgumentMatchers.anyInt(),
				org.mockito.ArgumentMatchers.anyString());
	}

	@Test
	public void writeMappingsUsePrivatePathsAndDoNotExposeAuthAliases() {
		Set<String> writePaths = Arrays.stream(ProductImageApi.class.getDeclaredMethods())
				.filter(method -> method.getAnnotation(RequestMapping.class) != null)
				.filter(method -> Arrays.stream(method.getAnnotation(RequestMapping.class).method())
						.anyMatch(this::isWriteMethod))
				.flatMap(method -> Arrays.stream(method.getAnnotation(RequestMapping.class).value()))
				.collect(Collectors.toSet());

		assertFalse(writePaths.stream().anyMatch(path -> path.contains("/auth/")));
		assertTrue(writePaths.contains("/private/product/{id}/image"));
		assertTrue(writePaths.contains("/private/product/image/{id}"));
		assertTrue(writePaths.contains("/private/product/{id}/image/{imageId}"));
	}

	private boolean isWriteMethod(RequestMethod method) {
		return method == RequestMethod.POST || method == RequestMethod.DELETE
				|| method == RequestMethod.PATCH || method == RequestMethod.PUT;
	}

	private void rejectGroup() {
		doThrow(new UnauthorizedException()).when(userFacade).authorizedGroup(eq(USERNAME), anyList());
	}

	private void invokeLegacyDelete() throws Exception {
		Method method = Arrays.stream(ProductImageApi.class.getDeclaredMethods())
				.filter(candidate -> candidate.getName().equals("deleteImage"))
				.filter(candidate -> candidate.getParameterTypes().length >= 3)
				.filter(candidate -> candidate.getParameterTypes()[0] == Long.class)
				.filter(candidate -> candidate.getParameterTypes()[1] == HttpServletRequest.class)
				.filter(candidate -> candidate.getParameterTypes()[2] == HttpServletResponse.class)
				.findFirst().orElseThrow(NoSuchMethodException::new);
		Object[] arguments = method.getParameterTypes().length == 4
				? new Object[] { IMAGE_ID, request, response, requestStore }
				: new Object[] { IMAGE_ID, request, response };
		try {
			method.invoke(api, arguments);
		} catch (InvocationTargetException exception) {
			Throwable cause = exception.getCause();
			if (cause instanceof Exception) {
				throw (Exception) cause;
			}
			if (cause instanceof Error) {
				throw (Error) cause;
			}
			throw exception;
		}
	}

	private void upload() throws Exception {
		Product product = productInStore(1L);
		MultipartFile file = org.mockito.Mockito.mock(MultipartFile.class);
		when(file.isEmpty()).thenReturn(false);
		when(file.getInputStream()).thenReturn(new ByteArrayInputStream(new byte[] { 1 }));
		when(file.getOriginalFilename()).thenReturn("image.jpg");
		when(productService.getById(PRODUCT_ID)).thenReturn(product);

		api.uploadImage(PRODUCT_ID, new MultipartFile[] { file }, 0, false, requestStore, new Language());
	}

	private Product productInStore(Long storeId) {
		Product product = new Product();
		product.setMerchantStore(store(storeId));
		return product;
	}

	private ProductImage image(Product product) {
		ProductImage image = new ProductImage();
		image.setProduct(product);
		return image;
	}

	private MerchantStore store(Long id) {
		MerchantStore store = new MerchantStore();
		store.setId(id.intValue());
		return store;
	}

}
