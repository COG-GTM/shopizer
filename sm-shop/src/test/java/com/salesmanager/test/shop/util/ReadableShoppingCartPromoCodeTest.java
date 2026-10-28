package com.salesmanager.test.shop.util;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.time.Duration;
import java.time.Instant;
import java.util.Collections;
import java.util.Date;

import org.junit.Before;
import org.junit.Test;
import org.springframework.test.util.ReflectionTestUtils;

import com.salesmanager.core.business.services.catalog.pricing.PricingService;
import com.salesmanager.core.business.services.shoppingcart.ShoppingCartCalculationService;
import com.salesmanager.core.model.merchant.MerchantStore;
import com.salesmanager.core.model.order.OrderTotalSummary;
import com.salesmanager.core.model.reference.language.Language;
import com.salesmanager.core.model.shoppingcart.ShoppingCart;
import com.salesmanager.shop.mapper.cart.ReadableShoppingCartMapper;

public class ReadableShoppingCartPromoCodeTest {

	private ReadableShoppingCartMapper mapper;
	private ShoppingCart cart;
	private final MerchantStore store = new MerchantStore();
	private final Language language = new Language();

	@Before
	public void setUp() throws Exception {
		mapper = new ReadableShoppingCartMapper();
		cart = new ShoppingCart();
		cart.setLineItems(Collections.emptySet());
		cart.setPromoCode("ONEDAY");
		ShoppingCartCalculationService calculation = mock(ShoppingCartCalculationService.class);
		when(calculation.calculate(cart, store, language)).thenReturn(new OrderTotalSummary());
		ReflectionTestUtils.setField(mapper, "shoppingCartCalculationService", calculation);
		ReflectionTestUtils.setField(mapper, "pricingService", mock(PricingService.class));
	}

	@Test
	public void recentPromoIsVisible() {
		cart.setPromoAdded(Date.from(Instant.now().minus(Duration.ofHours(1))));
		assertEquals("ONEDAY", mapper.convert(cart, store, language).getPromoCode());
	}

	@Test
	public void expiredPromoIsHidden() {
		cart.setPromoAdded(Date.from(Instant.now().minus(Duration.ofDays(2))));
		assertNull(mapper.convert(cart, store, language).getPromoCode());
	}

	@Test
	public void missingAddedDateIsHidden() {
		cart.setPromoAdded(null);
		assertNull(mapper.convert(cart, store, language).getPromoCode());
	}

	@Test
	public void futureAddedDateIsHidden() {
		cart.setPromoAdded(Date.from(Instant.now().plus(Duration.ofDays(1))));
		assertNull(mapper.convert(cart, store, language).getPromoCode());
	}
}
