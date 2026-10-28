package com.salesmanager.shop.store.facade.product;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.util.HashSet;
import java.util.Set;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnitRunner;

import com.salesmanager.core.business.services.catalog.pricing.PricingService;
import com.salesmanager.core.business.services.catalog.product.ProductService;
import com.salesmanager.core.model.catalog.product.Product;
import com.salesmanager.core.model.catalog.product.availability.ProductAvailability;
import com.salesmanager.core.model.catalog.product.price.ProductPrice;
import com.salesmanager.core.model.merchant.MerchantStore;
import com.salesmanager.core.model.reference.language.Language;
import com.salesmanager.shop.mapper.catalog.product.PersistableProductMapper;
import com.salesmanager.shop.model.catalog.product.LightPersistableProduct;
import com.salesmanager.shop.model.catalog.product.product.PersistableProduct;
import com.salesmanager.shop.store.api.exception.ResourceNotFoundException;

@RunWith(MockitoJUnitRunner.class)
public class ProductCommonFacadeImplOwnershipTest {

	@InjectMocks
	private ProductCommonFacadeImpl productCommonFacade;

	@Mock
	private ProductService productService;

	@Mock
	private PersistableProductMapper persistableProductMapper;

	@Mock
	private PricingService pricingService;

	private final MerchantStore store = store(2, "STORE");
	private final Language language = new Language();

	@Test
	public void saveProductWithNullIdCreatesNewProductWithoutLookup() throws Exception {
		assertNewProductIsSaved(persistable(null));
	}

	@Test
	public void saveProductWithZeroIdIsTreatedAsNew() throws Exception {
		assertNewProductIsSaved(persistable(0L));
	}

	@Test(expected = ResourceNotFoundException.class)
	public void saveProductRejectsProductWithoutMerchantStore() throws Exception {
		Product existing = product(10L, null);
		when(productService.findOne(10L, store)).thenReturn(existing);

		try {
			productCommonFacade.saveProduct(store, persistable(10L), language);
		} finally {
			verifyNoInteractions(persistableProductMapper);
			verify(productService, never()).saveProduct(any(Product.class));
		}
	}

	@Test
	@SuppressWarnings({ "deprecation", "removal" })
	public void saveProductAcceptsOwnProductWhenStoreIdAbove127() throws Exception {
		store.setId(Integer.valueOf(500));
		MerchantStore productOwner = store(500, "STORE");
		productOwner.setId(new Integer(500));
		Product existing = product(10L, productOwner);
		PersistableProduct body = persistable(10L);
		when(productService.findOne(10L, store)).thenReturn(existing);
		when(persistableProductMapper.merge(body, existing, store, language)).thenReturn(existing);
		when(productService.saveProduct(existing)).thenReturn(existing);

		assertEquals(Long.valueOf(10L), productCommonFacade.saveProduct(store, body, language));
		verify(productService).saveProduct(existing);
	}

	@Test(expected = ResourceNotFoundException.class)
	public void deleteProductRejectsOtherStoreProduct() throws Exception {
		MerchantStore otherStore = store(3, "OTHER");
		when(productService.findOne(10L, store)).thenReturn(product(10L, otherStore));

		try {
			productCommonFacade.deleteProduct(10L, store);
		} finally {
			verify(productService, never()).delete(any(Product.class));
		}
	}

	@Test(expected = ResourceNotFoundException.class)
	public void lightUpdateUnknownIdThrowsNotFound() throws Exception {
		when(productService.findOne(10L, store)).thenReturn(null);

		try {
			productCommonFacade.update(10L, new LightPersistableProduct(), store, language);
		} finally {
			verify(productService, never()).save(any(Product.class));
		}
	}

	@Test
	public void lightUpdateOwnProductUpdatesAvailabilityQuantityAndPrice() throws Exception {
		Product existing = product(10L, store);
		ProductAvailability availability = new ProductAvailability(existing, store);
		ProductPrice price = new ProductPrice();
		price.setDefaultPrice(true);
		Set<ProductPrice> prices = new HashSet<>();
		prices.add(price);
		availability.setPrices(prices);
		Set<ProductAvailability> availabilities = new HashSet<>();
		availabilities.add(availability);
		existing.setAvailabilities(availabilities);
		LightPersistableProduct update = new LightPersistableProduct();
		update.setPrice("12.50");
		update.setAvailable(true);
		update.setQuantity(7);
		when(productService.findOne(10L, store)).thenReturn(existing);
		when(pricingService.getAmount("12.50")).thenReturn(new BigDecimal("12.50"));

		productCommonFacade.update(10L, update, store, language);

		assertEquals(Integer.valueOf(7), availability.getProductQuantity());
		assertEquals(0, new BigDecimal("12.50").compareTo(price.getProductPriceAmount()));
		assertTrue(existing.isAvailable());
		verify(productService).save(existing);
	}

	private void assertNewProductIsSaved(PersistableProduct body) throws Exception {
		when(persistableProductMapper.merge(eq(body), any(Product.class), eq(store), eq(language)))
				.thenAnswer(invocation -> invocation.getArgument(1));
		when(productService.saveProduct(any(Product.class))).thenAnswer(invocation -> invocation.getArgument(0));

		assertNull(productCommonFacade.saveProduct(store, body, language));

		ArgumentCaptor<Product> productCaptor = ArgumentCaptor.forClass(Product.class);
		verify(persistableProductMapper).merge(eq(body), productCaptor.capture(), eq(store), eq(language));
		assertNull(productCaptor.getValue().getId());
		verify(productService).saveProduct(productCaptor.getValue());
		verify(productService, never()).findOne(anyLong(), eq(store));
		verify(productService, never()).getById(anyLong());
	}

	private static MerchantStore store(int id, String code) {
		MerchantStore merchantStore = new MerchantStore();
		merchantStore.setId(id);
		merchantStore.setCode(code);
		return merchantStore;
	}

	private static Product product(Long id, MerchantStore owner) {
		Product product = new Product();
		product.setId(id);
		product.setMerchantStore(owner);
		return product;
	}

	private static PersistableProduct persistable(Long id) {
		PersistableProduct product = new PersistableProduct();
		product.setId(id);
		return product;
	}
}
