package com.salesmanager.test.order;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.fail;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Duration;
import java.time.Instant;
import java.util.Date;
import java.util.concurrent.atomic.AtomicReference;

import org.junit.Before;
import org.junit.Test;
import org.springframework.test.util.ReflectionTestUtils;

import com.salesmanager.core.business.exception.ServiceException;
import com.salesmanager.core.business.repositories.order.OrderRepository;
import com.salesmanager.core.business.services.order.OrderServiceImpl;
import com.salesmanager.core.business.services.order.ordertotal.OrderTotalService;
import com.salesmanager.core.business.services.shoppingcart.ShoppingCartService;
import com.salesmanager.core.model.customer.Customer;
import com.salesmanager.core.model.merchant.MerchantStore;
import com.salesmanager.core.model.order.OrderSummary;
import com.salesmanager.core.model.reference.language.Language;
import com.salesmanager.core.model.shoppingcart.ShoppingCart;

public class OrderServicePromoCodeTest {

	private static final String PROMO = "ONEDAY";

	private OrderServiceImpl orderService;
	private ShoppingCartService shoppingCartService;
	private final AtomicReference<String> forwardedPromo = new AtomicReference<>();

	@Before
	public void setUp() throws Exception {
		orderService = new OrderServiceImpl(mock(OrderRepository.class));
		shoppingCartService = mock(ShoppingCartService.class);
		OrderTotalService orderTotalService = mock(OrderTotalService.class);
		when(orderTotalService.findOrderTotalVariation(any(OrderSummary.class), any(), any(), any()))
				.thenAnswer(invocation -> {
					forwardedPromo.set(((OrderSummary) invocation.getArgument(0)).getPromoCode());
					throw new IllegalStateException("stop after promo code is forwarded");
				});
		ReflectionTestUtils.setField(orderService, "shoppingCartService", shoppingCartService);
		ReflectionTestUtils.setField(orderService, "orderTotalService", orderTotalService);
	}

	@Test
	public void expiredPromoIsClearedAndNotForwarded() throws Exception {
		ShoppingCart cart = cartWithPromoAdded(Date.from(Instant.now().minus(Duration.ofDays(2))));

		calculate(cart);

		assertNull(forwardedPromo.get());
		assertNull(cart.getPromoCode());
		verify(shoppingCartService).saveOrUpdate(cart);
	}

	@Test
	public void promoWithoutAddedDateIsClearedAndNotForwarded() throws Exception {
		ShoppingCart cart = cartWithPromoAdded(null);

		calculate(cart);

		assertNull(forwardedPromo.get());
		assertNull(cart.getPromoCode());
		verify(shoppingCartService).saveOrUpdate(cart);
	}

	@Test
	public void recentPromoIsForwarded() throws Exception {
		ShoppingCart cart = cartWithPromoAdded(Date.from(Instant.now().minus(Duration.ofHours(1))));

		calculate(cart);

		assertEquals(PROMO, forwardedPromo.get());
		assertEquals(PROMO, cart.getPromoCode());
		verify(shoppingCartService, never()).saveOrUpdate(any(ShoppingCart.class));
	}

	private ShoppingCart cartWithPromoAdded(Date promoAdded) {
		ShoppingCart cart = new ShoppingCart();
		cart.setPromoCode(PROMO);
		cart.setPromoAdded(promoAdded);
		return cart;
	}

	private void calculate(ShoppingCart cart) {
		try {
			orderService.calculateShoppingCartTotal(cart, new Customer(), new MerchantStore(), new Language());
			fail("expected the stubbed order total service to stop the calculation");
		} catch (ServiceException expected) {
			// thrown by the stub once the promo code decision has been made
		}
	}
}
