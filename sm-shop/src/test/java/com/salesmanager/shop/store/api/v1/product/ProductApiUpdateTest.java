package com.salesmanager.shop.store.api.v1.product;

import static org.junit.Assert.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

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
import com.salesmanager.shop.model.catalog.product.product.PersistableProduct;
import com.salesmanager.shop.store.controller.product.facade.ProductCommonFacade;
import com.salesmanager.shop.store.controller.product.facade.ProductFacade;
import com.salesmanager.shop.utils.ImageFilePath;

@RunWith(MockitoJUnitRunner.class)
public class ProductApiUpdateTest {

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
	public void updateReturnsBadRequestWhenUrlAndBodyIdsDiffer() {
		PersistableProduct product = new PersistableProduct();
		product.setId(2L);
		MerchantStore store = new MerchantStore();
		MockHttpServletRequest request = new MockHttpServletRequest();
		MockHttpServletResponse response = new MockHttpServletResponse();

		productApi.update(1L, product, store, request, response);

		assertEquals(400, response.getStatus());
		verify(productCommonFacade, never()).saveProduct(any(), any(), any());
	}
}
