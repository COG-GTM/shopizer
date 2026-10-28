package com.salesmanager.test.shop.util;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertSame;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

import java.time.Duration;
import java.time.Instant;
import java.util.Collections;
import java.util.Date;

import org.junit.Before;
import org.junit.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.test.util.ReflectionTestUtils;

import com.salesmanager.core.business.services.order.OrderService;
import com.salesmanager.core.business.services.shoppingcart.ShoppingCartService;
import com.salesmanager.core.model.merchant.MerchantStore;
import com.salesmanager.core.model.order.OrderSummary;
import com.salesmanager.core.model.order.OrderTotalSummary;
import com.salesmanager.core.model.reference.language.Language;
import com.salesmanager.core.model.shoppingcart.ShoppingCart;
import com.salesmanager.shop.model.order.ShopOrder;
import com.salesmanager.shop.store.controller.customer.facade.CustomerFacade;
import com.salesmanager.shop.store.controller.order.facade.OrderFacadeImpl;
import com.salesmanager.shop.store.controller.shoppingCart.facade.ShoppingCartFacade;

public class OrderFacadePromoCodeTest {

	private OrderFacadeImpl facade;
	private ShoppingCart cart;
	private ShoppingCartService cartService;
	private OrderService orderService;
	private final MerchantStore store = new MerchantStore();
	private final Language language = new Language();

	@Before
	public void setUp() throws Exception {
		facade = new OrderFacadeImpl();
		cart = new ShoppingCart();
		cart.setPromoCode("ONEDAY");
		cartService = mock(ShoppingCartService.class);
		orderService = mock(OrderService.class);
		ShoppingCartFacade cartFacade = mock(ShoppingCartFacade.class);
		when(cartFacade.getShoppingCartModel("cart", store)).thenReturn(cart);
		OrderTotalSummary totals = new OrderTotalSummary();
		totals.setTotals(Collections.emptyList());
		when(orderService.caculateOrderTotal(any(OrderSummary.class), any(), any(), any())).thenReturn(totals);
		ReflectionTestUtils.setField(facade, "shoppingCartFacade", cartFacade);
		ReflectionTestUtils.setField(facade, "shoppingCartService", cartService);
		ReflectionTestUtils.setField(facade, "orderService", orderService);
		ReflectionTestUtils.setField(facade, "customerFacade", mock(CustomerFacade.class));
	}

	@Test
	public void expiredPromoIsCleared() throws Exception {
		cart.setPromoAdded(Date.from(Instant.now().minus(Duration.ofDays(2))));
		assertNull(calculate().getPromoCode());
		assertNull(cart.getPromoCode());
		verify(cartService).saveOrUpdate(cart);
	}

	@Test
	public void missingAddedDateIsClearedWithoutNullPointer() throws Exception {
		cart.setPromoAdded(null);
		assertNull(calculate().getPromoCode());
		assertNull(cart.getPromoCode());
		verify(cartService).saveOrUpdate(cart);
	}

	@Test
	public void activePromoIsForwarded() throws Exception {
		Date added = Date.from(Instant.now().minus(Duration.ofHours(1)));
		cart.setPromoAdded(added);
		assertEquals("ONEDAY", calculate().getPromoCode());
		assertEquals("ONEDAY", cart.getPromoCode());
		assertSame(added, cart.getPromoAdded());
		verify(cartService, never()).saveOrUpdate(any(ShoppingCart.class));
	}

	private OrderSummary calculate() throws Exception {
		ShopOrder order = new ShopOrder();
		order.setCartCode("cart");
		facade.calculateOrderTotal(store, order, language);
		ArgumentCaptor<OrderSummary> summary = ArgumentCaptor.forClass(OrderSummary.class);
		verify(orderService).caculateOrderTotal(summary.capture(), any(), any(), any());
		return summary.getValue();
	}
}
