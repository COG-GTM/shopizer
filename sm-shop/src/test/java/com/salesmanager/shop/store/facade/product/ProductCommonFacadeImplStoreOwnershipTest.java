package com.salesmanager.shop.store.facade.product;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.fail;
import static org.mockito.ArgumentMatchers.any;
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
public class ProductCommonFacadeImplStoreOwnershipTest {

	@InjectMocks
	private ProductCommonFacadeImpl productCommonFacade;

	@Mock
	private ProductService productService;

	@Mock
	private PersistableProductMapper persistableProductMapper;

	@Test
	public void saveProductRejectsTargetWithoutMerchantStore() throws Exception {
		MerchantStore store = store(2, "GLOBEX");
		Product existing = new Product();
		existing.setId(10L);
		when(productService.findOne(10L, store)).thenReturn(existing);

		try {
			productCommonFacade.saveProduct(store, persistableProduct(10L), new Language());
			fail("Expected ResourceNotFoundException");
		} catch (ResourceNotFoundException expected) {
			verify(persistableProductMapper, never()).merge(any(PersistableProduct.class), any(Product.class),
					any(MerchantStore.class), any(Language.class));
			verify(productService, never()).saveProduct(any(Product.class));
		}
	}

	@Test
	public void saveProductRejectsChildStoreProductWhenCalledForParent() throws Exception {
		MerchantStore parent = store(1, "PARENT");
		MerchantStore child = store(2, "CHILD");
		Product existing = product(10L, child);
		when(productService.findOne(10L, parent)).thenReturn(existing);

		try {
			productCommonFacade.saveProduct(parent, persistableProduct(10L), new Language());
			fail("Expected ResourceNotFoundException");
		} catch (ResourceNotFoundException expected) {
			verify(productService, never()).saveProduct(any(Product.class));
		}
	}

	@Test
	public void saveProductCreatesWithoutLookingUpWhenIdIsZero() throws Exception {
		MerchantStore store = store(2, "GLOBEX");
		Language language = new Language();
		PersistableProduct body = persistableProduct(0L);
		Product created = product(11L, store);
		when(persistableProductMapper.merge(any(PersistableProduct.class), any(Product.class),
				any(MerchantStore.class), any(Language.class))).thenReturn(created);
		when(productService.saveProduct(created)).thenReturn(created);

		assertEquals(Long.valueOf(11L), productCommonFacade.saveProduct(store, body, language));

		verify(productService, never()).findOne(any(Long.class), any(MerchantStore.class));
		verify(productService, never()).getById(any(Long.class));
		verify(productService).saveProduct(created);
	}

	@Test
	public void saveProductAcceptsDifferentStoreInstanceWithSameId() throws Exception {
		MerchantStore store = store(2, "GLOBEX");
		MerchantStore separatelyLoadedStore = store(2, "GLOBEX");
		Language language = new Language();
		PersistableProduct body = persistableProduct(10L);
		Product existing = product(10L, separatelyLoadedStore);
		when(productService.findOne(10L, store)).thenReturn(existing);
		when(persistableProductMapper.merge(body, existing, store, language)).thenReturn(existing);
		when(productService.saveProduct(existing)).thenReturn(existing);

		assertEquals(Long.valueOf(10L), productCommonFacade.saveProduct(store, body, language));

		verify(productService, never()).getById(any(Long.class));
		verify(productService).saveProduct(existing);
	}

	private static MerchantStore store(int id, String code) {
		MerchantStore store = new MerchantStore();
		store.setId(id);
		store.setCode(code);
		return store;
	}

	private static Product product(Long id, MerchantStore store) {
		Product product = new Product();
		product.setId(id);
		product.setMerchantStore(store);
		return product;
	}

	private static PersistableProduct persistableProduct(Long id) {
		PersistableProduct product = new PersistableProduct();
		product.setId(id);
		return product;
	}
}
