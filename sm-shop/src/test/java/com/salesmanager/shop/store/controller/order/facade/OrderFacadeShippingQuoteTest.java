package com.salesmanager.shop.store.controller.order.facade;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.fail;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.lang.reflect.UndeclaredThrowableException;
import java.math.BigDecimal;
import java.util.Collections;
import java.util.List;

import org.junit.Before;
import org.junit.Test;
import org.springframework.test.util.ReflectionTestUtils;

import com.salesmanager.core.business.exception.ServiceException;
import com.salesmanager.core.business.services.shipping.ShippingQuoteService;
import com.salesmanager.core.business.services.shipping.ShippingService;
import com.salesmanager.core.business.services.shoppingcart.ShoppingCartService;
import com.salesmanager.core.model.customer.Customer;
import com.salesmanager.core.model.merchant.MerchantStore;
import com.salesmanager.core.model.reference.language.Language;
import com.salesmanager.core.model.shipping.ShippingOption;
import com.salesmanager.core.model.shipping.ShippingProduct;
import com.salesmanager.core.model.shipping.ShippingQuote;
import com.salesmanager.core.model.shipping.ShippingSummary;
import com.salesmanager.core.model.shoppingcart.ShoppingCart;
import com.salesmanager.core.model.shoppingcart.ShoppingCartItem;

public class OrderFacadeShippingQuoteTest {

	private static final long CART_ID = 10L;

	private OrderFacadeImpl facade;
	private ShippingService shippingService;
	private ShippingQuoteService shippingQuoteService;
	private ShoppingCartService shoppingCartService;

	private MerchantStore store;
	private ShoppingCart cart;
	private List<ShoppingCartItem> items;
	private Customer customer;
	private Language language;

	@Before
	public void setUp() throws Exception {
		shippingService = mock(ShippingService.class);
		shippingQuoteService = mock(ShippingQuoteService.class);
		shoppingCartService = mock(ShoppingCartService.class);
		facade = new OrderFacadeImpl();
		ReflectionTestUtils.setField(facade, "shippingService", shippingService);
		ReflectionTestUtils.setField(facade, "shippingQuoteService", shippingQuoteService);
		ReflectionTestUtils.setField(facade, "shoppingCartService", shoppingCartService);

		store = new MerchantStore(1, "DEFAULT", "Default");
		cart = new ShoppingCart();
		cart.setId(CART_ID);
		cart.setMerchantStore(store);
		items = Collections.singletonList(new ShoppingCartItem());
		customer = new Customer();
		language = new Language();
	}

	private ShippingSummary resolve(Long quoteId) throws Exception {
		try {
			return ReflectionTestUtils.invokeMethod(facade, "resolveShippingSummary", quoteId, cart, items,
					customer, store, language);
		} catch (UndeclaredThrowableException e) {
			throw (Exception) e.getUndeclaredThrowable();
		}
	}

	private void shippable(boolean shippable) throws ServiceException {
		when(shippingService.requiresShipping(items, store)).thenReturn(shippable);
	}

	private void storeQuotes(ShippingQuote quote) throws ServiceException {
		when(shoppingCartService.createShippingProduct(cart))
				.thenReturn(Collections.singletonList(new ShippingProduct(null)));
		when(shippingService.calculateShippingQuote(eq(CART_ID), eq(store), any(), any(), eq(language)))
				.thenReturn(quote);
	}

	private static ShippingQuote paidShipping() {
		ShippingOption option = new ShippingOption();
		option.setShippingModuleCode("weightBased");
		option.setOptionCode("STD");
		option.setOptionPrice(new BigDecimal("45.00"));
		ShippingQuote quote = new ShippingQuote();
		quote.setShippingOptions(Collections.singletonList(option));
		return quote;
	}

	@Test
	public void shippableCartWithoutQuoteIsRejected() throws Exception {
		shippable(true);
		storeQuotes(paidShipping());
		for (Long missing : new Long[] { null, 0L, -1L }) {
			try {
				resolve(missing);
				fail("expected rejection for quote " + missing);
			} catch (ServiceException e) {
				assertEquals(ServiceException.EXCEPTION_VALIDATION, e.getExceptionType());
				assertEquals("shipping.required", e.getMessageCode());
			}
		}
		verify(shippingQuoteService, never()).getCheckoutShippingSummary(any(), any(), any(), any());
		verify(shippingService, never()).getShippingQuote(any(), any(), any(), any(), any());
	}

	@Test
	public void shippableCartWithoutQuoteToUnsupportedDestinationIsRejected() throws Exception {
		shippable(true);
		ShippingQuote unsupported = new ShippingQuote();
		unsupported.setShippingReturnCode(ShippingQuote.NO_SHIPPING_TO_SELECTED_COUNTRY + " ZZ");
		storeQuotes(unsupported);
		try {
			resolve(null);
			fail("expected rejection");
		} catch (ServiceException e) {
			assertEquals("shipping.required", e.getMessageCode());
		}
	}

	@Test
	public void shippableCartWithQuoteOfAnotherCartIsRejected() throws Exception {
		shippable(true);
		ServiceException foreign = new ServiceException(ServiceException.EXCEPTION_VALIDATION, "foreign",
				"shipping.quote.invalid");
		when(shippingQuoteService.getCheckoutShippingSummary(777L, cart, store, language)).thenThrow(foreign);
		try {
			resolve(777L);
			fail("expected rejection");
		} catch (ServiceException e) {
			assertSame(foreign, e);
		}
	}

	@Test
	public void shippableCartWithOwnQuoteUsesValidatedSummary() throws Exception {
		shippable(true);
		ShippingSummary summary = new ShippingSummary();
		when(shippingQuoteService.getCheckoutShippingSummary(5L, cart, store, language)).thenReturn(summary);
		assertSame(summary, resolve(5L));
	}

	@Test
	public void shippableCartWithoutQuoteIsAllowedWhenShippingIsFree() throws Exception {
		shippable(true);
		ShippingQuote free = new ShippingQuote();
		free.setFreeShipping(true);
		storeQuotes(free);
		assertNull(resolve(null));
	}

	@Test
	public void shippableCartWithoutQuoteIsAllowedWhenStoreHasNoShippingModule() throws Exception {
		shippable(true);
		ShippingQuote noModule = new ShippingQuote();
		noModule.setShippingReturnCode(ShippingQuote.NO_SHIPPING_MODULE_CONFIGURED);
		storeQuotes(noModule);
		assertNull(resolve(null));
	}

	@Test
	public void virtualCartIgnoresQuote() throws Exception {
		shippable(false);
		assertNull(resolve(5L));
		verify(shippingQuoteService, never()).getCheckoutShippingSummary(any(), any(), any(), any());
	}
}
