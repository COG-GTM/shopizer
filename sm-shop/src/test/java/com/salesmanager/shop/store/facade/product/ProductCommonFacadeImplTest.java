package com.salesmanager.shop.store.facade.product;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.fail;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnitRunner;

import com.salesmanager.core.business.services.catalog.product.ProductService;
import com.salesmanager.core.model.catalog.product.Product;
import com.salesmanager.core.model.merchant.MerchantStore;
import com.salesmanager.core.model.reference.language.Language;
import com.salesmanager.shop.mapper.catalog.product.PersistableProductMapper;
import com.salesmanager.shop.model.catalog.product.product.PersistableProduct;
import com.salesmanager.shop.store.api.exception.ResourceNotFoundException;

@RunWith(MockitoJUnitRunner.class)
public class ProductCommonFacadeImplTest {

	@InjectMocks
	private ProductCommonFacadeImpl productCommonFacade;

	@Mock
	private ProductService productService;

	@Mock
	private PersistableProductMapper persistableProductMapper;

	@Test
	public void saveProductRejectsProductOwnedByParentStore() throws Exception {
		MerchantStore parentStore = store(1, "PARENT");
		MerchantStore childStore = store(2, "CHILD");
		childStore.setParent(parentStore);
		Product parentProduct = new Product();
		parentProduct.setId(10L);
		parentProduct.setMerchantStore(parentStore);
		PersistableProduct product = persistableProduct(10L);

		when(productService.findOne(10L, childStore)).thenReturn(parentProduct);

		try {
			productCommonFacade.saveProduct(childStore, product, null);
			fail("Expected ResourceNotFoundException");
		} catch (ResourceNotFoundException expected) {
			verify(productService).findOne(10L, childStore);
			verify(persistableProductMapper, never()).merge(any(PersistableProduct.class), any(Product.class),
					any(MerchantStore.class), isNull());
			verify(productService, never()).saveProduct(any(Product.class));
		}
	}

	@Test
	public void saveProductRejectsProductNotFoundInStore() throws Exception {
		MerchantStore store = store(2, "STORE");
		PersistableProduct product = persistableProduct(10L);

		when(productService.findOne(10L, store)).thenReturn(null);

		try {
			productCommonFacade.saveProduct(store, product, null);
			fail("Expected ResourceNotFoundException");
		} catch (ResourceNotFoundException expected) {
			verify(productService).findOne(10L, store);
			verify(persistableProductMapper, never()).merge(any(PersistableProduct.class), any(Product.class),
					any(MerchantStore.class), isNull());
			verify(productService, never()).saveProduct(any(Product.class));
		}
	}

	@Test
	public void saveProductUpdatesProductOwnedByStore() throws Exception {
		MerchantStore store = store(2, "STORE");
		Product existingProduct = new Product();
		existingProduct.setId(10L);
		existingProduct.setMerchantStore(store);
		PersistableProduct product = persistableProduct(10L);
		Language language = new Language();

		when(productService.findOne(10L, store)).thenReturn(existingProduct);
		when(persistableProductMapper.merge(product, existingProduct, store, language)).thenReturn(existingProduct);
		when(productService.saveProduct(existingProduct)).thenReturn(existingProduct);

		Long savedProductId = productCommonFacade.saveProduct(store, product, language);

		assertEquals(Long.valueOf(10L), savedProductId);
		verify(productService).findOne(10L, store);
		verify(persistableProductMapper).merge(product, existingProduct, store, language);
		verify(productService).saveProduct(existingProduct);
	}

	private MerchantStore store(int id, String code) {
		MerchantStore store = new MerchantStore();
		store.setId(id);
		store.setCode(code);
		return store;
	}

	private PersistableProduct persistableProduct(Long id) {
		PersistableProduct product = new PersistableProduct();
		product.setId(id);
		return product;
	}
}
