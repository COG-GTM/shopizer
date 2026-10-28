package com.salesmanager.shop.store.facade.product;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.salesmanager.core.business.services.catalog.product.ProductService;
import com.salesmanager.core.model.catalog.product.Product;
import com.salesmanager.core.model.merchant.MerchantStore;
import com.salesmanager.core.model.reference.language.Language;
import com.salesmanager.shop.mapper.catalog.product.PersistableProductMapper;
import com.salesmanager.shop.model.catalog.product.product.PersistableProduct;
import com.salesmanager.shop.store.api.exception.ResourceNotFoundException;

@ExtendWith(MockitoExtension.class)
class ProductCommonFacadeImplTest {

	@Mock
	private ProductService productService;

	@Mock
	private PersistableProductMapper persistableProductMapper;

	@InjectMocks
	private ProductCommonFacadeImpl facade;

	private static MerchantStore store(int id, String code) {
		MerchantStore store = new MerchantStore();
		store.setId(id);
		store.setCode(code);
		return store;
	}

	@Test
	void saveProductRejectsProductOwnedByAnotherStore() throws Exception {
		MerchantStore acme = store(1, "ACME");
		MerchantStore globex = store(2, "GLOBEX");

		Product acmeProduct = new Product();
		acmeProduct.setId(10L);
		acmeProduct.setMerchantStore(acme);
		when(productService.getProductWithOnlyMerchantStoreById(10L)).thenReturn(acmeProduct);

		PersistableProduct body = new PersistableProduct();
		body.setId(10L);

		assertThrows(ResourceNotFoundException.class,
				() -> facade.saveProduct(globex, body, new Language()));

		verify(productService, never()).getById(any());
		verify(persistableProductMapper, never()).merge(any(), any(), any(), any());
		verify(productService, never()).saveProduct(any());
	}

	@Test
	void saveProductRejectsUnknownProductId() throws Exception {
		when(productService.getProductWithOnlyMerchantStoreById(99L)).thenReturn(null);

		PersistableProduct body = new PersistableProduct();
		body.setId(99L);

		assertThrows(ResourceNotFoundException.class,
				() -> facade.saveProduct(store(2, "GLOBEX"), body, new Language()));

		verify(productService, never()).saveProduct(any());
	}

	@Test
	void saveProductAllowsProductOwnedBySameStore() throws Exception {
		MerchantStore globex = store(2, "GLOBEX");

		Product globexProduct = new Product();
		globexProduct.setId(20L);
		globexProduct.setMerchantStore(globex);
		when(productService.getProductWithOnlyMerchantStoreById(20L)).thenReturn(globexProduct);
		when(productService.getById(20L)).thenReturn(globexProduct);
		when(persistableProductMapper.merge(any(), any(), any(), any())).thenReturn(globexProduct);
		when(productService.saveProduct(globexProduct)).thenReturn(globexProduct);

		PersistableProduct body = new PersistableProduct();
		body.setId(20L);

		facade.saveProduct(globex, body, new Language());

		verify(productService).saveProduct(globexProduct);
	}
}
