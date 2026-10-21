package com.salesmanager.shop.store.facade.product;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNotSame;
import static org.junit.Assert.fail;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.ArgumentCaptor;
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
public class ProductCommonFacadeImplStoreScopeTest {

	@InjectMocks
	private ProductCommonFacadeImpl productCommonFacade;

	@Mock
	private ProductService productService;

	@Mock
	private PersistableProductMapper persistableProductMapper;

	@Test
	public void treatsNullIdAsNewProduct() throws Exception {
		assertNewProductIsSaved(null);
	}

	@Test
	public void treatsZeroIdAsNewProduct() throws Exception {
		assertNewProductIsSaved(0L);
	}

	@Test
	public void rejectsExistingProductWithoutMerchantStore() throws Exception {
		MerchantStore store = store(1000, "STORE");
		PersistableProduct request = product(10L);
		Product target = new Product();
		target.setId(10L);
		when(productService.findOne(10L, store)).thenReturn(target);

		try {
			productCommonFacade.saveProduct(store, request, new Language());
			fail("Expected ResourceNotFoundException");
		} catch (ResourceNotFoundException expected) {
			verify(productService).findOne(10L, store);
			verify(persistableProductMapper, never()).merge(any(PersistableProduct.class), any(Product.class),
					any(MerchantStore.class), any(Language.class));
			verify(productService, never()).saveProduct(any(Product.class));
		}
	}

	@Test
	public void updatesProductWhenStoreIdsHaveEqualValues() throws Exception {
		MerchantStore requestStore = store(1000, "STORE");
		MerchantStore productStore = store(1000, "ANOTHER_INSTANCE");
		assertNotSame(requestStore.getId(), productStore.getId());

		PersistableProduct request = product(10L);
		Language language = new Language();
		Product target = new Product();
		target.setId(10L);
		target.setMerchantStore(productStore);
		when(productService.findOne(10L, requestStore)).thenReturn(target);
		when(persistableProductMapper.merge(request, target, requestStore, language)).thenReturn(target);
		when(productService.saveProduct(target)).thenReturn(target);

		assertEquals(Long.valueOf(10L), productCommonFacade.saveProduct(requestStore, request, language));

		verify(productService).findOne(10L, requestStore);
		verify(persistableProductMapper).merge(request, target, requestStore, language);
		verify(productService).saveProduct(target);
	}

	@Test
	public void rejectsProductOwnedByAnotherStore() throws Exception {
		MerchantStore requestStore = store(1001, "REQUEST");
		MerchantStore productStore = store(1000, "OWNER");
		PersistableProduct request = product(10L);
		Product target = new Product();
		target.setId(10L);
		target.setMerchantStore(productStore);
		when(productService.findOne(10L, requestStore)).thenReturn(target);

		try {
			productCommonFacade.saveProduct(requestStore, request, new Language());
			fail("Expected ResourceNotFoundException");
		} catch (ResourceNotFoundException expected) {
			verify(productService).findOne(10L, requestStore);
			verify(persistableProductMapper, never()).merge(any(PersistableProduct.class), any(Product.class),
					any(MerchantStore.class), any(Language.class));
			verify(productService, never()).saveProduct(any(Product.class));
		}
	}

	private void assertNewProductIsSaved(Long id) throws Exception {
		MerchantStore store = store(1000, "STORE");
		PersistableProduct request = product(id);
		Language language = new Language();
		when(persistableProductMapper.merge(eq(request), any(Product.class), eq(store), eq(language)))
				.thenAnswer(invocation -> invocation.getArgument(1));
		when(productService.saveProduct(any(Product.class))).thenAnswer(invocation -> {
			Product target = invocation.getArgument(0);
			target.setId(20L);
			return target;
		});

		assertEquals(Long.valueOf(20L), productCommonFacade.saveProduct(store, request, language));

		verify(productService, never()).findOne(any(Long.class), any(MerchantStore.class));
		ArgumentCaptor<Product> target = ArgumentCaptor.forClass(Product.class);
		verify(persistableProductMapper).merge(eq(request), target.capture(), eq(store), eq(language));
		assertNotNull(target.getValue());
		verify(productService).saveProduct(target.getValue());
	}

	private MerchantStore store(int id, String code) {
		MerchantStore store = new MerchantStore();
		store.setId(Integer.valueOf(id));
		store.setCode(code);
		return store;
	}

	private PersistableProduct product(Long id) {
		PersistableProduct product = new PersistableProduct();
		product.setId(id);
		return product;
	}
}
