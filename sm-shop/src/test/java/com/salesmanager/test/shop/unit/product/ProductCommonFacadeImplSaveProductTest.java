package com.salesmanager.test.shop.unit.product;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.fail;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.same;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnitRunner;

import com.salesmanager.core.business.services.catalog.product.ProductService;
import com.salesmanager.core.business.services.catalog.product.review.ProductReviewService;
import com.salesmanager.core.business.services.customer.CustomerService;
import com.salesmanager.core.business.services.reference.language.LanguageService;
import com.salesmanager.core.model.catalog.product.Product;
import com.salesmanager.core.model.merchant.MerchantStore;
import com.salesmanager.core.model.reference.language.Language;
import com.salesmanager.shop.mapper.catalog.product.PersistableProductMapper;
import com.salesmanager.shop.model.catalog.product.product.PersistableProduct;
import com.salesmanager.shop.store.api.exception.ResourceNotFoundException;
import com.salesmanager.shop.store.facade.product.ProductCommonFacadeImpl;
import com.salesmanager.shop.utils.ImageFilePath;

@RunWith(MockitoJUnitRunner.class)
public class ProductCommonFacadeImplSaveProductTest {

	@Mock
	private LanguageService languageService;

	@Mock
	private ProductService productService;

	@Mock
	private CustomerService customerService;

	@Mock
	private ProductReviewService productReviewService;

	@Mock
	private PersistableProductMapper persistableProductMapper;

	@Mock
	private ImageFilePath imageUtils;

	@InjectMocks
	private ProductCommonFacadeImpl productCommonFacade;

	private MerchantStore store;
	private Language language;

	@Before
	public void setUp() {
		store = new MerchantStore(Integer.valueOf(1000), "DEFAULT", "Default");
		language = new Language();
	}

	@Test
	public void missingExistingProductDoesNotMergeOrSave() throws Exception {
		PersistableProduct product = productWithId(5L);
		when(productService.getById(5L)).thenReturn(null);

		assertNotFound(product);

		verify(productService, never()).saveProduct(any(Product.class));
		verify(persistableProductMapper, never()).merge(any(PersistableProduct.class), any(Product.class),
				any(MerchantStore.class), any(Language.class));
	}

	@Test
	public void productOwnedByAnotherStoreDoesNotMergeOrSave() throws Exception {
		PersistableProduct product = productWithId(5L);
		Product existing = productForStore(new MerchantStore(Integer.valueOf(2000), "OTHER", "Other"));
		when(productService.getById(5L)).thenReturn(existing);

		assertNotFound(product);

		verify(productService, never()).saveProduct(any(Product.class));
		verify(persistableProductMapper, never()).merge(any(PersistableProduct.class), any(Product.class),
				any(MerchantStore.class), any(Language.class));
	}

	@Test
	public void productWithoutStoreDoesNotMergeOrSave() throws Exception {
		PersistableProduct product = productWithId(5L);
		Product existing = new Product();
		when(productService.getById(5L)).thenReturn(existing);

		assertNotFound(product);

		verify(productService, never()).saveProduct(any(Product.class));
		verify(persistableProductMapper, never()).merge(any(PersistableProduct.class), any(Product.class),
				any(MerchantStore.class), any(Language.class));
	}

	@Test
	public void storeIdsAreComparedByValueAndExistingTargetIsMerged() throws Exception {
		PersistableProduct product = productWithId(5L);
		MerchantStore equivalentStore = new MerchantStore(new Integer(1000), "DEFAULT", "Default");
		Product existing = productForStore(equivalentStore);
		Product merged = new Product();
		Product saved = new Product();
		saved.setId(50L);
		when(productService.getById(5L)).thenReturn(existing);
		when(persistableProductMapper.merge(product, existing, store, language)).thenReturn(merged);
		when(productService.saveProduct(merged)).thenReturn(saved);

		Long result = productCommonFacade.saveProduct(store, product, language);

		assertEquals(Long.valueOf(50L), result);
		verify(persistableProductMapper).merge(same(product), same(existing), same(store), same(language));
		verify(productService).saveProduct(same(merged));
	}

	@Test
	public void nullIdCreatesNewProductWithoutLoadingExistingProduct() throws Exception {
		assertNewProductIsSaved(null);
	}

	@Test
	public void zeroIdCreatesNewProductWithoutLoadingExistingProduct() throws Exception {
		assertNewProductIsSaved(0L);
	}

	private void assertNewProductIsSaved(Long id) throws Exception {
		PersistableProduct product = productWithId(id);
		Product merged = new Product();
		Product saved = new Product();
		saved.setId(50L);
		when(persistableProductMapper.merge(eq(product), any(Product.class), eq(store), eq(language)))
				.thenReturn(merged);
		when(productService.saveProduct(merged)).thenReturn(saved);

		Long result = productCommonFacade.saveProduct(store, product, language);

		assertEquals(Long.valueOf(50L), result);
		verify(productService, never()).getById(anyLong());
		verify(persistableProductMapper).merge(eq(product), any(Product.class), eq(store), eq(language));
		verify(productService).saveProduct(merged);
	}

	private void assertNotFound(PersistableProduct product) throws Exception {
		try {
			productCommonFacade.saveProduct(store, product, language);
			fail("Expected ResourceNotFoundException");
		} catch (ResourceNotFoundException expected) {
			assertSame(expected.getClass(), ResourceNotFoundException.class);
		}
	}

	private Product productForStore(MerchantStore merchantStore) {
		Product product = new Product();
		product.setMerchantStore(merchantStore);
		return product;
	}

	private PersistableProduct productWithId(Long id) {
		PersistableProduct product = new PersistableProduct();
		product.setId(id);
		return product;
	}
}
