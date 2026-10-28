package com.salesmanager.shop.store.api.v1.product;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.lang.reflect.Method;
import java.util.Arrays;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnitRunner;
import org.springframework.core.annotation.AnnotatedElementUtils;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;

import com.salesmanager.core.business.services.catalog.product.ProductService;
import com.salesmanager.core.model.catalog.product.Product;
import com.salesmanager.core.model.merchant.MerchantStore;
import com.salesmanager.core.model.reference.language.Language;
import com.salesmanager.shop.model.catalog.product.ReadableProductList;
import com.salesmanager.shop.store.controller.items.facade.ProductItemsFacade;

@RunWith(MockitoJUnitRunner.class)
public class ProductGroupApiTest {

	private static final Long PRODUCT_ID = 42L;
	private static final String GROUP = "FEATURED_ITEM";

	@Mock
	private ProductService productService;

	@Mock
	private ProductItemsFacade productItemsFacade;

	@InjectMocks
	private ProductGroupApi api;

	private final MerchantStore store = store("DEFAULT");
	private final Language language = new Language();

	@Test
	public void deleteGroupIsMappedUnderPrivatePath() throws Exception {
		RequestMapping mapping = mapping("deleteGroup", String.class, MerchantStore.class, Language.class,
				javax.servlet.http.HttpServletResponse.class);
		assertArrayEquals(new RequestMethod[] { RequestMethod.DELETE }, mapping.method());
		assertArrayEquals(new String[] { "/private/products/group/{code}" }, mapping.path());
	}

	@Test
	public void everyWriteEndpointIsUnderPrivatePath() {
		for (Method m : ProductGroupApi.class.getDeclaredMethods()) {
			RequestMapping mapping = AnnotatedElementUtils.findMergedAnnotation(m, RequestMapping.class);
			if (mapping == null) {
				continue;
			}
			boolean readOnly = Arrays.equals(mapping.method(), new RequestMethod[] { RequestMethod.GET });
			if (readOnly) {
				continue;
			}
			for (String path : mapping.path()) {
				assertTrue(m.getName() + " exposes write path " + path + " outside /private/",
						path.startsWith("/private/"));
			}
		}
	}

	@Test
	public void publicGetByGroupStaysPublic() throws Exception {
		RequestMapping mapping = mapping("getProductItemsByGroup", String.class, MerchantStore.class,
				Language.class, javax.servlet.http.HttpServletResponse.class);
		assertArrayEquals(new RequestMethod[] { RequestMethod.GET }, mapping.method());
		assertArrayEquals(new String[] { "/products/group/{code}" }, mapping.path());
	}

	@Test
	public void deleteGroupDelegatesWithResolvedStore() {
		api.deleteGroup(GROUP, store, language, new MockHttpServletResponse());
		verify(productItemsFacade).deleteGroup(GROUP, store);
	}

	@Test
	public void addProductToGroupLooksUpProductScopedToStore() throws Exception {
		Product product = new Product();
		product.setId(PRODUCT_ID);
		ReadableProductList list = new ReadableProductList();
		when(productService.findOne(PRODUCT_ID, store)).thenReturn(product);
		when(productItemsFacade.addItemToGroup(product, GROUP, store, language)).thenReturn(list);

		MockHttpServletResponse response = new MockHttpServletResponse();
		ReadableProductList result = api.addProductToGroup(PRODUCT_ID, GROUP, store, language, response);

		assertSame(list, result);
		verify(productService, never()).getById(anyLong());
	}

	@Test
	public void addProductToGroupReturns404ForProductOutsideStore() throws Exception {
		when(productService.findOne(PRODUCT_ID, store)).thenReturn(null);

		MockHttpServletResponse response = new MockHttpServletResponse();
		ReadableProductList result = api.addProductToGroup(PRODUCT_ID, GROUP, store, language, response);

		assertNull(result);
		assertEquals(404, response.getStatus());
		verify(productService, never()).getById(anyLong());
		verify(productItemsFacade, never()).addItemToGroup(any(), anyString(), any(), any());
	}

	@Test
	public void removeProductFromGroupLooksUpProductScopedToStore() throws Exception {
		Product product = new Product();
		product.setId(PRODUCT_ID);
		ReadableProductList list = new ReadableProductList();
		when(productService.findOne(PRODUCT_ID, store)).thenReturn(product);
		when(productItemsFacade.removeItemFromGroup(product, GROUP, store, language)).thenReturn(list);

		ReadableProductList result = api.removeProductFromGroup(PRODUCT_ID, GROUP, store, language,
				new MockHttpServletRequest(), new MockHttpServletResponse());

		assertSame(list, result);
		verify(productService, never()).getById(anyLong());
	}

	@Test
	public void removeProductFromGroupReturns404ForProductOutsideStore() throws Exception {
		when(productService.findOne(PRODUCT_ID, store)).thenReturn(null);

		MockHttpServletResponse response = new MockHttpServletResponse();
		ReadableProductList result = api.removeProductFromGroup(PRODUCT_ID, GROUP, store, language,
				new MockHttpServletRequest(), response);

		assertNull(result);
		assertEquals(404, response.getStatus());
		verify(productService, never()).getById(anyLong());
		verify(productItemsFacade, never()).removeItemFromGroup(any(), anyString(), any(), any());
	}

	private static RequestMapping mapping(String name, Class<?>... params) throws NoSuchMethodException {
		Method m = ProductGroupApi.class.getMethod(name, params);
		return AnnotatedElementUtils.findMergedAnnotation(m, RequestMapping.class);
	}

	private static MerchantStore store(String code) {
		MerchantStore s = new MerchantStore();
		s.setCode(code);
		s.setId(1);
		return s;
	}
}
