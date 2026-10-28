package com.salesmanager.test.shop.util;

import static org.junit.Assert.*;
import static org.mockito.Mockito.*;

import java.time.Duration;
import java.time.Instant;
import java.util.Collections;
import java.util.Date;

import org.junit.Before;
import org.junit.Test;
import org.springframework.test.util.ReflectionTestUtils;

import com.salesmanager.core.business.services.catalog.product.ProductService;
import com.salesmanager.core.business.services.shoppingcart.ShoppingCartCalculationService;
import com.salesmanager.core.business.services.shoppingcart.ShoppingCartService;
import com.salesmanager.core.model.catalog.product.Product;
import com.salesmanager.core.model.catalog.product.availability.ProductAvailability;
import com.salesmanager.core.model.merchant.MerchantStore;
import com.salesmanager.core.model.reference.language.Language;
import com.salesmanager.core.model.shoppingcart.ShoppingCart;
import com.salesmanager.core.model.shoppingcart.ShoppingCartItem;
import com.salesmanager.shop.mapper.cart.ReadableShoppingCartMapper;
import com.salesmanager.shop.model.shoppingcart.PersistableShoppingCartItem;
import com.salesmanager.shop.model.shoppingcart.ReadableShoppingCart;
import com.salesmanager.shop.store.controller.shoppingCart.facade.ShoppingCartFacadeImpl;

public class ShoppingCartFacadePromoCodeTest {

	private ShoppingCartFacadeImpl facade;
	private ShoppingCart cart;
	private ShoppingCartService cartService;
	private final MerchantStore store = new MerchantStore();
	private final Language language = new Language();

	@Before
	public void setUp() throws Exception {
		facade = new ShoppingCartFacadeImpl();
		cart = new ShoppingCart();
		cart.setId(1L);
		store.setId(1);
		store.setDefaultLanguage(language);
		cartService = mock(ShoppingCartService.class);
		when(cartService.getByCode("cart", store)).thenReturn(cart);
		when(cartService.getById(1L, store)).thenReturn(cart);
		ProductService products = mock(ProductService.class);
		Product product = new Product();
		product.setId(2L);
		product.setSku("sku");
		product.setMerchantStore(store);
		product.setAvailable(true);
		product.setDateAvailable(Date.from(Instant.now().minus(Duration.ofDays(1))));
		ProductAvailability availability = new ProductAvailability();
		availability.setProductQuantity(10);
		product.setAvailabilities(Collections.singleton(availability));
		when(products.getBySku("sku", store, language)).thenReturn(product);
		when(cartService.populateShoppingCartItem(product, store)).thenReturn(new ShoppingCartItem());
		ReadableShoppingCartMapper mapper = mock(ReadableShoppingCartMapper.class);
		when(mapper.convert(cart, store, language)).thenAnswer(invocation -> new ReadableShoppingCart());
		ReflectionTestUtils.setField(facade, "shoppingCartService", cartService);
		ReflectionTestUtils.setField(facade, "productService", products);
		ReflectionTestUtils.setField(facade, "shoppingCartCalculationService",
				mock(ShoppingCartCalculationService.class));
		ReflectionTestUtils.setField(facade, "readableShoppingCartMapper", mapper);
	}

	@Test
	public void submittedPromoReplacesExpiredPromoAndResetsAddedDate() throws Exception {
		cart.setPromoCode("OLD");
		cart.setPromoAdded(Date.from(Instant.now().minus(Duration.ofDays(2))));
		assertSubmittedPromoApplied();
	}

	@Test
	public void submittedPromoAppliesToCartWithoutPromo() throws Exception {
		assertSubmittedPromoApplied();
	}

	@Test
	public void submittedPromoDoesNotReplaceActivePromo() throws Exception {
		Date added = Date.from(Instant.now().minus(Duration.ofHours(1)));
		cart.setPromoCode("OLD");
		cart.setPromoAdded(added);
		modify();
		assertEquals("OLD", cart.getPromoCode());
		assertSame(added, cart.getPromoAdded());
		verify(cartService).save(cart);
	}

	@Test
	public void activePromoIsVisibleByCode() throws Exception {
		cart.setPromoCode("OLD");
		cart.setPromoAdded(Date.from(Instant.now().minus(Duration.ofHours(1))));
		assertEquals("OLD", facade.getByCode("cart", store, language).getPromoCode());
	}

	@Test
	public void expiredPromoIsNotExposedByCode() throws Exception {
		cart.setPromoCode("OLD");
		cart.setPromoAdded(Date.from(Instant.now().minus(Duration.ofDays(2))));
		assertNull(facade.getByCode("cart", store, language).getPromoCode());
	}

	@Test
	public void missingPromoDateIsNotExposedByCode() throws Exception {
		cart.setPromoCode("OLD");
		cart.setPromoAdded(null);
		assertNull(facade.getByCode("cart", store, language).getPromoCode());
	}

	@Test
	public void missingCartReturnsNull() throws Exception {
		assertNull(facade.getByCode("missing", store, language));
	}

	private void assertSubmittedPromoApplied() throws Exception {
		long before = System.currentTimeMillis();
		modify();
		long after = System.currentTimeMillis();
		assertEquals("NEW", cart.getPromoCode());
		assertNotNull(cart.getPromoAdded());
		assertTrue(cart.getPromoAdded().getTime() >= before);
		assertTrue(cart.getPromoAdded().getTime() <= after);
		verify(cartService).save(cart);
	}

	private void modify() throws Exception {
		PersistableShoppingCartItem item = new PersistableShoppingCartItem();
		item.setProduct("sku");
		item.setQuantity(1);
		item.setPromoCode("NEW");
		facade.modifyCart("cart", item, store, language);
	}
}
