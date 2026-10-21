package com.salesmanager.test.shop.unit.product;

import static org.junit.Assert.assertEquals;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnitRunner;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

import com.salesmanager.core.business.services.catalog.category.CategoryService;
import com.salesmanager.core.business.services.catalog.product.ProductService;
import com.salesmanager.core.model.merchant.MerchantStore;
import com.salesmanager.core.model.reference.language.Language;
import com.salesmanager.shop.model.catalog.product.product.PersistableProduct;
import com.salesmanager.shop.store.api.v1.product.ProductApi;
import com.salesmanager.shop.store.controller.product.facade.ProductCommonFacade;
import com.salesmanager.shop.store.controller.product.facade.ProductFacade;
import com.salesmanager.shop.utils.ImageFilePath;

@RunWith(MockitoJUnitRunner.class)
public class ProductApiUpdateIdMismatchTest {

	@Mock
	private CategoryService categoryService;

	@Mock
	private ProductService productService;

	@Mock
	private ProductFacade productFacade;

	@Mock
	private ProductCommonFacade productCommonFacade;

	@Mock
	private ImageFilePath imageUtils;

	@InjectMocks
	private ProductApi productApi;

	@Test
	public void mismatchedUrlAndBodyIdsReturnBadRequestWithoutSaving() throws Exception {
		MerchantStore store = new MerchantStore(Integer.valueOf(1000), "DEFAULT", "Default");
		PersistableProduct product = productWithId(2L);
		MockHttpServletResponse response = new MockHttpServletResponse();

		productApi.update(1L, product, store, new MockHttpServletRequest(), response);

		assertEquals(400, response.getStatus());
		verifyNoInteractions(productCommonFacade);
	}

	@Test
	public void matchingIdsSaveUsingStoreDefaultLanguage() throws Exception {
		Language language = new Language();
		MerchantStore store = new MerchantStore(Integer.valueOf(1000), "DEFAULT", "Default");
		store.setDefaultLanguage(language);
		PersistableProduct product = productWithId(1L);
		MockHttpServletRequest request = new MockHttpServletRequest();
		MockHttpServletResponse response = new MockHttpServletResponse();

		productApi.update(1L, product, store, request, response);

		assertEquals(200, response.getStatus());
		verify(productCommonFacade).saveProduct(store, product, language);
	}

	private PersistableProduct productWithId(Long id) {
		PersistableProduct product = new PersistableProduct();
		product.setId(id);
		return product;
	}
}
