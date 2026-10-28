package com.salesmanager.shop.store.controller.order.facade;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.util.Locale;

import org.junit.Before;
import org.junit.Test;
import org.springframework.test.util.ReflectionTestUtils;

import com.salesmanager.core.business.exception.ServiceException;
import com.salesmanager.core.business.services.order.OrderService;
import com.salesmanager.core.business.services.shipping.ShippingQuoteService;
import com.salesmanager.core.business.services.shipping.ShippingService;
import com.salesmanager.core.business.services.shoppingcart.ShoppingCartService;
import com.salesmanager.core.model.customer.Customer;
import com.salesmanager.core.model.merchant.MerchantStore;
import com.salesmanager.core.model.reference.language.Language;
import com.salesmanager.core.model.shoppingcart.ShoppingCart;
import com.salesmanager.shop.model.order.v1.PersistableOrder;
import com.salesmanager.shop.populator.order.PersistableOrderApiPopulator;

/**
 * Checkout (PR #185) must refuse a cart that belongs to another customer before any shipping or
 * order processing happens.
 */
public class OrderFacadeCheckoutCartOwnershipTest {

	private static final long CART_ID = 31L;

	private OrderFacadeImpl facade;
	private ShippingService shippingService;
	private ShippingQuoteService shippingQuoteService;
	private ShoppingCartService shoppingCartService;
	private OrderService orderService;
	private MerchantStore store;
	private ShoppingCart cart;
	private PersistableOrder order;
	private Language language;

	@Before
	public void setUp() throws Exception {
		shippingService = mock(ShippingService.class);
		shippingQuoteService = mock(ShippingQuoteService.class);
		shoppingCartService = mock(ShoppingCartService.class);
		orderService = mock(OrderService.class);
		facade = new OrderFacadeImpl();
		ReflectionTestUtils.setField(facade, "shippingService", shippingService);
		ReflectionTestUtils.setField(facade, "shippingQuoteService", shippingQuoteService);
		ReflectionTestUtils.setField(facade, "shoppingCartService", shoppingCartService);
		ReflectionTestUtils.setField(facade, "orderService", orderService);
		ReflectionTestUtils.setField(facade, "persistableOrderApiPopulator", mock(PersistableOrderApiPopulator.class));

		store = new MerchantStore(1, "DEFAULT", "Default");
		language = new Language();
		cart = new ShoppingCart();
		cart.setId(CART_ID);
		cart.setMerchantStore(store);
		when(shoppingCartService.getById(CART_ID, store)).thenReturn(cart);

		order = new PersistableOrder();
		order.setShoppingCartId(CART_ID);
		order.setShippingQuote(55L);
	}

	private static Customer customer(Long id) {
		Customer customer = new Customer();
		customer.setId(id);
		return customer;
	}

	private ServiceException checkout(Customer customer) {
		try {
			facade.processOrder(order, customer, store, language, Locale.CANADA);
			fail("expected checkout to be rejected");
			return null;
		} catch (ServiceException e) {
			return e;
		}
	}

	private static String messages(Throwable t) {
		StringBuilder all = new StringBuilder();
		for (Throwable c = t; c != null; c = c.getCause()) {
			all.append(c.getMessage()).append('|');
		}
		return all.toString();
	}

	@Test
	public void cartOfAnotherCustomerIsRejectedBeforeShipping() throws Exception {
		cart.setCustomerId(2L);
		ServiceException e = checkout(customer(1L));
		assertTrue(messages(e), messages(e).contains("does not belong to this customer"));
		verifyNoInteractions(shippingQuoteService, shippingService);
		verify(orderService, never()).caculateOrderTotal(any(), any(), any(), any());
	}

	@Test
	public void missingCartIsRejected() throws Exception {
		when(shoppingCartService.getById(CART_ID, store)).thenReturn(null);
		checkout(customer(1L));
		verifyNoInteractions(shippingQuoteService, shippingService, orderService);
	}

	@Test
	public void ownCartProceedsToShippingQuoteValidation() throws Exception {
		cart.setCustomerId(1L);
		ServiceException invalid = new ServiceException(ServiceException.EXCEPTION_VALIDATION, "foreign",
				"shipping.quote.invalid");
		when(shippingService.requiresShipping(any(), any())).thenReturn(true);
		when(shippingQuoteService.getCheckoutShippingSummary(55L, cart, store, language)).thenThrow(invalid);
		ServiceException e = checkout(customer(1L));
		assertTrue(messages(e), messages(e).contains("foreign"));
		verify(shippingQuoteService).getCheckoutShippingSummary(55L, cart, store, language);
		verify(orderService, never()).caculateOrderTotal(any(), any(), any(), any());
	}

	@Test
	public void guestCheckoutOfUnownedCartIsNotBlockedByOwnershipCheck() throws Exception {
		when(shippingService.requiresShipping(any(), any())).thenReturn(true);
		ServiceException invalid = new ServiceException(ServiceException.EXCEPTION_VALIDATION, "foreign",
				"shipping.quote.invalid");
		when(shippingQuoteService.getCheckoutShippingSummary(55L, cart, store, language)).thenThrow(invalid);
		ServiceException e = checkout(customer(null));
		assertEquals(false, messages(e).contains("does not belong"));
		verify(shippingQuoteService).getCheckoutShippingSummary(55L, cart, store, language);
	}
}
