package com.salesmanager.shop.store.controller.order.facade;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.Collections;
import java.util.Locale;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.mockito.Spy;
import org.springframework.test.util.ReflectionTestUtils;

import com.salesmanager.core.business.exception.ServiceException;
import com.salesmanager.core.business.services.order.OrderService;
import com.salesmanager.core.business.services.shipping.ShippingQuoteService;
import com.salesmanager.core.business.services.shipping.ShippingService;
import com.salesmanager.core.business.services.shoppingcart.ShoppingCartService;
import com.salesmanager.core.model.customer.Customer;
import com.salesmanager.core.model.merchant.MerchantStore;
import com.salesmanager.core.model.order.OrderSummary;
import com.salesmanager.core.model.reference.language.Language;
import com.salesmanager.core.model.shipping.ShippingQuote;
import com.salesmanager.core.model.shipping.ShippingSummary;
import com.salesmanager.core.model.shoppingcart.ShoppingCart;
import com.salesmanager.shop.model.order.v1.PersistableOrder;
import com.salesmanager.shop.populator.order.PersistableOrderApiPopulator;

class OrderFacadeImplShippingQuoteTest {

	private static final Long QUOTE_ID = 101L;

	@Mock
	private OrderService orderService;
	@Mock
	private ShoppingCartService shoppingCartService;
	@Mock
	private ShippingService shippingService;
	@Mock
	private ShippingQuoteService shippingQuoteService;
	@Mock
	private PersistableOrderApiPopulator persistableOrderApiPopulator;
	@Spy
	@InjectMocks
	private OrderFacadeImpl facade;

	private Customer customer;
	private MerchantStore store;
	private Language language;
	private ShoppingCart cart;
	private PersistableOrder order;
	private RuntimeException orderTotalSentinel;

	@BeforeEach
	void setUp() throws Exception {
		MockitoAnnotations.openMocks(this);
		ReflectionTestUtils.setField(facade, "orderService", orderService);
		ReflectionTestUtils.setField(facade, "shoppingCartService", shoppingCartService);
		ReflectionTestUtils.setField(facade, "shippingService", shippingService);
		ReflectionTestUtils.setField(facade, "shippingQuoteService", shippingQuoteService);
		ReflectionTestUtils.setField(facade, "persistableOrderApiPopulator", persistableOrderApiPopulator);

		customer = new Customer();
		store = new MerchantStore();
		language = new Language();
		cart = new ShoppingCart();
		cart.setId(12L);
		cart.setLineItems(Collections.emptySet());
		order = new PersistableOrder();
		order.setShoppingCartId(cart.getId());
		when(shoppingCartService.getById(cart.getId(), store)).thenReturn(cart);

		orderTotalSentinel = new IllegalStateException("order-total-reached");
		when(orderService.caculateOrderTotal(any(OrderSummary.class), eq(customer), eq(store), eq(language)))
				.thenThrow(orderTotalSentinel);
	}

	private ServiceException processOrder() {
		return assertThrows(ServiceException.class,
				() -> facade.processOrder(order, customer, store, language, Locale.CANADA));
	}

	private ShippingQuote pricedQuote() {
		ShippingQuote quote = new ShippingQuote();
		quote.setShippingOptions(Collections.singletonList(new com.salesmanager.core.model.shipping.ShippingOption()));
		return quote;
	}

	@Test
	void validatesSuppliedQuoteWithTheExactCartAndCustomerAndPropagatesFailure() throws Exception {
		order.setShippingQuote(QUOTE_ID);
		ServiceException validationFailure = new ServiceException(ServiceException.EXCEPTION_VALIDATION, "invalid");
		when(shippingQuoteService.getShippingSummary(QUOTE_ID, cart, customer, store)).thenThrow(validationFailure);

		ServiceException thrown = processOrder();

		assertSame(validationFailure, thrown.getCause());
		verify(shippingQuoteService).getShippingSummary(QUOTE_ID, cart, customer, store);
		verify(shippingService, never()).requiresShipping(anyList(), eq(store));
	}

	@Test
	void requiresAQuoteWhenTheServerReturnsPricedOptions() throws Exception {
		when(shippingService.requiresShipping(anyList(), eq(store))).thenReturn(true);
		ShippingQuote quote = pricedQuote();
		doReturn(quote).when(facade).getShippingQuote(customer, cart, store, language);

		ServiceException thrown = processOrder();

		assertTrue(thrown.getMessage().contains("A shipping quote is required for this order"));
		assertTrue(thrown.getCause() instanceof ServiceException);
		assertEquals(ServiceException.EXCEPTION_VALIDATION,
				((ServiceException) thrown.getCause()).getExceptionType());
	}

	@Test
	void acceptsNoConfiguredShippingModule() throws Exception {
		when(shippingService.requiresShipping(anyList(), eq(store))).thenReturn(true);
		ShippingQuote quote = pricedQuote();
		quote.setShippingReturnCode(ShippingQuote.NO_SHIPPING_MODULE_CONFIGURED);
		doReturn(quote).when(facade).getShippingQuote(customer, cart, store, language);

		assertSame(orderTotalSentinel, processOrder().getCause());
	}

	@Test
	void acceptsFreeShippingWithoutQuoteId() throws Exception {
		when(shippingService.requiresShipping(anyList(), eq(store))).thenReturn(true);
		ShippingQuote quote = pricedQuote();
		quote.setFreeShipping(true);
		doReturn(quote).when(facade).getShippingQuote(customer, cart, store, language);

		assertSame(orderTotalSentinel, processOrder().getCause());
	}

	@Test
	void proceedsWhenCartHasNoShippableProducts() throws Exception {
		when(shippingService.requiresShipping(anyList(), eq(store))).thenReturn(true);
		when(shoppingCartService.createShippingProduct(cart)).thenReturn(Collections.emptyList());

		assertSame(orderTotalSentinel, processOrder().getCause());
		verify(shippingService, never()).getShippingQuote(any(), eq(store), any(), anyList(), eq(language));
	}

	@Test
	void skipsServerQuoteWhenShippingIsNotRequired() throws Exception {
		when(shippingService.requiresShipping(anyList(), eq(store))).thenReturn(false);

		assertSame(orderTotalSentinel, processOrder().getCause());
		verify(facade, never()).getShippingQuote(customer, cart, store, language);
	}
}
