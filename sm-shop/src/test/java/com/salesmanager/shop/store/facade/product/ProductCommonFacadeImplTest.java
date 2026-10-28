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
public class ProductCommonFacadeImplTest {

	@InjectMocks
	private ProductCommonFacadeImpl productCommonFacade;

	@Mock
	private ProductService productService;

	@Mock
	private PersistableProductMapper persistableProductMapper;

	@Test
	public void saveProductRejectsProductOwnedByAnotherStore() throws Exception {
		MerchantStore acme = store(1, "ACME");
		MerchantStore globex = store(2, "GLOBEX");
		Product acmeProduct = product(10L, acme);
		PersistableProduct body = persistableProduct(10L);
		Language language = new Language();

		when(productService.getById(10L)).thenReturn(acmeProduct);

		try {
			productCommonFacade.saveProduct(globex, body, language);
			fail("Expected ResourceNotFoundException");
		} catch (ResourceNotFoundException expected) {
			verify(persistableProductMapper, never()).merge(any(PersistableProduct.class), any(Product.class),
					any(MerchantStore.class), any(Language.class));
			verify(productService, never()).saveProduct(any(Product.class));
			assertEquals(acme, acmeProduct.getMerchantStore());
		}
	}

	@Test
	public void saveProductRejectsUnknownProductId() throws Exception {
		MerchantStore store = store(2, "GLOBEX");
		PersistableProduct body = persistableProduct(99L);

		when(productService.getById(99L)).thenReturn(null);

		try {
			productCommonFacade.saveProduct(store, body, new Language());
			fail("Expected ResourceNotFoundException");
		} catch (ResourceNotFoundException expected) {
			verify(productService, never()).saveProduct(any(Product.class));
		}
	}

	@Test
	public void saveProductUpdatesProductOwnedBySameStore() throws Exception {
		MerchantStore store = store(2, "GLOBEX");
		Product existing = product(10L, store);
		PersistableProduct body = persistableProduct(10L);
		Language language = new Language();

		when(productService.getById(10L)).thenReturn(existing);
		when(persistableProductMapper.merge(body, existing, store, language)).thenReturn(existing);
		when(productService.saveProduct(existing)).thenReturn(existing);

		assertEquals(Long.valueOf(10L), productCommonFacade.saveProduct(store, body, language));
		verify(productService).saveProduct(existing);
	}

	@Test
	public void saveProductCreatesNewProductWithoutLookup() throws Exception {
		MerchantStore store = store(2, "GLOBEX");
		PersistableProduct body = persistableProduct(null);
		Language language = new Language();
		Product created = product(11L, store);

		when(persistableProductMapper.merge(any(PersistableProduct.class), any(Product.class),
				any(MerchantStore.class), any(Language.class))).thenReturn(created);
		when(productService.saveProduct(created)).thenReturn(created);

		assertEquals(Long.valueOf(11L), productCommonFacade.saveProduct(store, body, language));
		verify(productService, never()).getById(any(Long.class));
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
