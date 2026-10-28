package com.salesmanager.shop.store.facade.product;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnitRunner;

import com.salesmanager.core.business.services.catalog.product.ProductService;
import com.salesmanager.core.model.catalog.product.Product;
import com.salesmanager.core.model.merchant.MerchantStore;
import com.salesmanager.shop.mapper.catalog.product.PersistableProductMapper;
import com.salesmanager.shop.model.catalog.product.product.PersistableProduct;
import com.salesmanager.shop.store.api.exception.ResourceNotFoundException;

@RunWith(MockitoJUnitRunner.class)
public class ProductCommonFacadeImplTest {

	private static final Long PRODUCT_ID = 17L;

	@Mock
	private ProductService productService;

	@Mock
	private PersistableProductMapper persistableProductMapper;

	@InjectMocks
	private ProductCommonFacadeImpl facade;

	private MerchantStore childStore;

	@Before
	public void setUp() {
		childStore = new MerchantStore();
		childStore.setId(2);
		childStore.setCode("CHILD");
	}

	@Test
	public void saveProductThrowsWhenProductIsMissingForStore() throws Exception {
		PersistableProduct product = productWithId(PRODUCT_ID);
		when(productService.findOne(PRODUCT_ID, childStore)).thenReturn(null);

		assertThrows(ResourceNotFoundException.class, () -> facade.saveProduct(childStore, product, null));

		verify(productService, never()).getById(anyLong());
		verify(persistableProductMapper, never()).merge(any(), any(), any(), any());
		verify(productService, never()).saveProduct(any());
	}

	@Test
	public void saveProductRejectsProductOwnedByAnotherStore() throws Exception {
		PersistableProduct product = productWithId(PRODUCT_ID);
		Product target = productOwnedBy(1);
		when(productService.findOne(PRODUCT_ID, childStore)).thenReturn(target);

		assertThrows(ResourceNotFoundException.class, () -> facade.saveProduct(childStore, product, null));

		verify(persistableProductMapper, never()).merge(any(), any(), any(), any());
		verify(productService, never()).saveProduct(any());
	}

	@Test
	public void saveProductMergesExistingProductOwnedByRequestStore() throws Exception {
		PersistableProduct product = productWithId(PRODUCT_ID);
		Product target = productOwnedBy(2);
		target.setId(PRODUCT_ID);
		when(productService.findOne(PRODUCT_ID, childStore)).thenReturn(target);
		when(persistableProductMapper.merge(product, target, childStore, null)).thenReturn(target);
		when(productService.saveProduct(target)).thenReturn(target);

		Long result = facade.saveProduct(childStore, product, null);

		assertEquals(PRODUCT_ID, result);
		verify(persistableProductMapper).merge(product, target, childStore, null);
		verify(productService).saveProduct(target);
	}

	@Test
	public void saveProductCreatesProductWhenIdIsNull() throws Exception {
		PersistableProduct product = new PersistableProduct();
		Product saved = new Product();
		saved.setId(PRODUCT_ID);
		when(persistableProductMapper.merge(eq(product), any(Product.class), eq(childStore), eq(null)))
				.thenReturn(saved);
		when(productService.saveProduct(saved)).thenReturn(saved);

		Long result = facade.saveProduct(childStore, product, null);

		assertEquals(PRODUCT_ID, result);
		verify(productService, never()).findOne(anyLong(), any());
		verify(productService, never()).getById(anyLong());
		verify(persistableProductMapper).merge(eq(product), any(Product.class), eq(childStore), eq(null));
		verify(productService).saveProduct(saved);
	}

	@Test
	public void saveProductRequiresStore() throws Exception {
		assertThrows(NullPointerException.class, () -> facade.saveProduct(null, new PersistableProduct(), null));
	}

	@Test
	public void deleteProductThrowsWhenProductIsMissingForStore() throws Exception {
		when(productService.findOne(PRODUCT_ID, childStore)).thenReturn(null);

		assertThrows(ResourceNotFoundException.class, () -> facade.deleteProduct(PRODUCT_ID, childStore));

		verify(productService, never()).getById(anyLong());
		verify(productService, never()).delete(any());
	}

	@Test
	public void deleteProductRejectsProductOwnedByAnotherStore() throws Exception {
		Product target = productOwnedBy(1);
		when(productService.findOne(PRODUCT_ID, childStore)).thenReturn(target);

		assertThrows(ResourceNotFoundException.class, () -> facade.deleteProduct(PRODUCT_ID, childStore));

		verify(productService, never()).getById(anyLong());
		verify(productService, never()).delete(any());
	}

	@Test
	public void deleteProductDeletesProductOwnedByRequestStore() throws Exception {
		Product target = productOwnedBy(2);
		when(productService.findOne(PRODUCT_ID, childStore)).thenReturn(target);

		facade.deleteProduct(PRODUCT_ID, childStore);

		verify(productService, never()).getById(anyLong());
		verify(productService).delete(target);
	}

	private PersistableProduct productWithId(Long id) {
		PersistableProduct product = new PersistableProduct();
		product.setId(id);
		return product;
	}

	private Product productOwnedBy(int storeId) {
		MerchantStore store = new MerchantStore();
		store.setId(storeId);
		Product product = new Product();
		product.setMerchantStore(store);
		return product;
	}
}
