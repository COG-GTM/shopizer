package com.salesmanager.shop.store.facade.product;

import static org.junit.Assert.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
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
import com.salesmanager.shop.model.catalog.product.LightPersistableProduct;
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

	private final MerchantStore parent = store(1, "PARENT");
	private final MerchantStore store = store(2, "STORE");
	private final Language language = new Language();

	@Test(expected = ResourceNotFoundException.class)
	public void saveProductRejectsIdNotInStore() throws Exception {
		when(productService.findOne(10L, store)).thenReturn(null);
		try {
			productCommonFacade.saveProduct(store, persistable(10L), language);
		} finally {
			verifyNoInteractions(persistableProductMapper);
			verify(productService, never()).saveProduct(any(Product.class));
		}
	}

	@Test(expected = ResourceNotFoundException.class)
	public void saveProductRejectsParentStoreProduct() throws Exception {
		store.setParent(parent);
		when(productService.findOne(10L, store)).thenReturn(product(10L, parent));
		try {
			productCommonFacade.saveProduct(store, persistable(10L), language);
		} finally {
			verifyNoInteractions(persistableProductMapper);
			verify(productService, never()).saveProduct(any(Product.class));
		}
	}

	@Test
	public void saveProductUpdatesOwnProduct() throws Exception {
		Product existing = product(10L, store);
		PersistableProduct body = persistable(10L);
		when(productService.findOne(10L, store)).thenReturn(existing);
		when(persistableProductMapper.merge(body, existing, store, language)).thenReturn(existing);
		when(productService.saveProduct(existing)).thenReturn(existing);

		assertEquals(Long.valueOf(10L), productCommonFacade.saveProduct(store, body, language));
		verify(productService, never()).getById(any());
	}

	@Test(expected = ResourceNotFoundException.class)
	public void deleteProductRejectsParentStoreProduct() throws Exception {
		store.setParent(parent);
		when(productService.findOne(10L, store)).thenReturn(product(10L, parent));
		try {
			productCommonFacade.deleteProduct(10L, store);
		} finally {
			verify(productService, never()).delete(any(Product.class));
		}
	}

	@Test(expected = ResourceNotFoundException.class)
	public void deleteProductRejectsIdNotInStore() throws Exception {
		when(productService.findOne(10L, store)).thenReturn(null);
		try {
			productCommonFacade.deleteProduct(10L, store);
		} finally {
			verify(productService, never()).delete(any(Product.class));
		}
	}

	@Test
	public void deleteProductDeletesOwnProduct() throws Exception {
		Product existing = product(10L, store);
		when(productService.findOne(10L, store)).thenReturn(existing);

		productCommonFacade.deleteProduct(10L, store);

		verify(productService).delete(existing);
	}

	@Test(expected = ResourceNotFoundException.class)
	public void lightUpdateRejectsParentStoreProduct() throws Exception {
		store.setParent(parent);
		when(productService.findOne(10L, store)).thenReturn(product(10L, parent));
		try {
			productCommonFacade.update(10L, new LightPersistableProduct(), store, language);
		} finally {
			verify(productService, never()).save(any(Product.class));
		}
	}

	private static MerchantStore store(int id, String code) {
		MerchantStore s = new MerchantStore();
		s.setId(id);
		s.setCode(code);
		return s;
	}

	private static Product product(Long id, MerchantStore owner) {
		Product p = new Product();
		p.setId(id);
		p.setMerchantStore(owner);
		return p;
	}

	private static PersistableProduct persistable(Long id) {
		PersistableProduct p = new PersistableProduct();
		p.setId(id);
		return p;
	}
}
