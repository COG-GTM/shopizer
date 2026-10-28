package com.salesmanager.test.shipping;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.util.Collections;
import java.util.Optional;

import org.junit.Before;
import org.junit.Test;
import org.springframework.test.util.ReflectionTestUtils;

import com.salesmanager.core.business.exception.ServiceException;
import com.salesmanager.core.business.repositories.shipping.ShippingQuoteRepository;
import com.salesmanager.core.business.services.shipping.ShippingQuoteServiceImpl;
import com.salesmanager.core.business.services.shipping.ShippingService;
import com.salesmanager.core.business.services.shoppingcart.ShoppingCartService;
import com.salesmanager.core.model.common.Delivery;
import com.salesmanager.core.model.merchant.MerchantStore;
import com.salesmanager.core.model.reference.language.Language;
import com.salesmanager.core.model.shipping.Quote;
import com.salesmanager.core.model.shipping.ShippingOption;
import com.salesmanager.core.model.shipping.ShippingProduct;
import com.salesmanager.core.model.shipping.ShippingQuote;
import com.salesmanager.core.model.shipping.ShippingSummary;
import com.salesmanager.core.model.shoppingcart.ShoppingCart;

public class ShippingQuoteServiceImplTest {

	private static final long CART_ID = 10L;
	private static final long QUOTE_ID = 500L;

	private ShippingQuoteRepository repository;
	private ShippingService shippingService;
	private ShoppingCartService shoppingCartService;
	private ShippingQuoteServiceImpl service;

	private MerchantStore store;
	private ShoppingCart cart;
	private Language language;

	@Before
	public void setUp() {
		repository = mock(ShippingQuoteRepository.class);
		shippingService = mock(ShippingService.class);
		shoppingCartService = mock(ShoppingCartService.class);
		service = new ShippingQuoteServiceImpl(repository);
		ReflectionTestUtils.setField(service, "shippingService", shippingService);
		ReflectionTestUtils.setField(service, "shoppingCartService", shoppingCartService);

		store = new MerchantStore(1, "DEFAULT", "Default");
		cart = new ShoppingCart();
		cart.setId(CART_ID);
		cart.setMerchantStore(store);
		language = new Language();
	}

	private Quote quote(Long cartId, String price) {
		Quote q = new Quote();
		q.setId(QUOTE_ID);
		q.setCartId(cartId);
		q.setModule("weightBased");
		q.setOptionCode("STD");
		q.setOptionName("Standard");
		q.setPrice(new BigDecimal(price));
		q.setDelivery(new Delivery());
		when(repository.findById(QUOTE_ID)).thenReturn(Optional.of(q));
		return q;
	}

	private ShippingQuote currentQuote(String module, String optionCode, String price) {
		ShippingOption option = new ShippingOption();
		option.setShippingModuleCode(module);
		option.setOptionCode(optionCode);
		option.setOptionPrice(new BigDecimal(price));
		ShippingQuote current = new ShippingQuote();
		current.setShippingOptions(Collections.singletonList(option));
		return current;
	}

	private void stubRequote(ShippingQuote current) throws ServiceException {
		when(shoppingCartService.createShippingProduct(cart))
				.thenReturn(Collections.singletonList(new ShippingProduct(null)));
		when(shippingService.getShippingQuote(eq(CART_ID), eq(store), any(Delivery.class), any(), eq(language)))
				.thenReturn(current);
	}

	private static void assertRejected(ServiceException e, String messageCode) {
		assertEquals(ServiceException.EXCEPTION_VALIDATION, e.getExceptionType());
		assertEquals(messageCode, e.getMessageCode());
	}

	@Test
	public void quoteOfThisCartIsAccepted() throws Exception {
		quote(CART_ID, "45.00");
		ShippingSummary summary = service.getShippingSummary(QUOTE_ID, cart, store);
		assertEquals(0, new BigDecimal("45.00").compareTo(summary.getShipping()));
		assertEquals("weightBased", summary.getShippingModule());
	}

	@Test
	public void quoteOfAnotherCartIsRejected() {
		quote(99L, "3.00");
		try {
			service.getShippingSummary(QUOTE_ID, cart, store);
			fail("expected rejection");
		} catch (ServiceException e) {
			assertRejected(e, "shipping.quote.invalid");
		}
	}

	@Test
	public void unknownQuoteIsRejected() {
		when(repository.findById(QUOTE_ID)).thenReturn(Optional.empty());
		try {
			service.getQuote(QUOTE_ID, cart, store);
			fail("expected rejection");
		} catch (ServiceException e) {
			assertRejected(e, "shipping.quote.invalid");
		}
	}

	@Test
	public void cartOfAnotherStoreIsRejected() {
		quote(CART_ID, "3.00");
		MerchantStore other = new MerchantStore(2, "OTHER", "Other");
		try {
			service.getQuote(QUOTE_ID, cart, other);
			fail("expected rejection");
		} catch (ServiceException e) {
			assertRejected(e, "shipping.quote.invalid");
		}
	}

	@Test
	public void quoteOfAnotherCustomerIsRejected() {
		Quote q = quote(CART_ID, "45.00");
		q.setCustomerId(7L);
		cart.setCustomerId(8L);
		try {
			service.getQuote(QUOTE_ID, cart, store);
			fail("expected rejection");
		} catch (ServiceException e) {
			assertRejected(e, "shipping.quote.invalid");
		}
	}

	@Test
	public void checkoutAcceptsQuoteMatchingCurrentCart() throws Exception {
		quote(CART_ID, "45.00");
		stubRequote(currentQuote("weightBased", "STD", "45"));
		ShippingSummary summary = service.getCheckoutShippingSummary(QUOTE_ID, cart, store, language);
		assertEquals(0, new BigDecimal("45").compareTo(summary.getShipping()));
	}

	@Test
	public void checkoutRejectsQuoteCheaperThanCurrentCart() throws Exception {
		quote(CART_ID, "3.00");
		stubRequote(currentQuote("weightBased", "STD", "45.00"));
		try {
			service.getCheckoutShippingSummary(QUOTE_ID, cart, store, language);
			fail("expected rejection");
		} catch (ServiceException e) {
			assertRejected(e, "shipping.quote.expired");
		}
	}

	@Test
	public void checkoutRejectsQuoteForOptionNoLongerOffered() throws Exception {
		quote(CART_ID, "45.00");
		stubRequote(currentQuote("weightBased", "EXPRESS", "45.00"));
		try {
			service.getCheckoutShippingSummary(QUOTE_ID, cart, store, language);
			fail("expected rejection");
		} catch (ServiceException e) {
			assertRejected(e, "shipping.quote.expired");
		}
	}

	@Test
	public void checkoutHonoursFreeShippingOnRequote() throws Exception {
		quote(CART_ID, "45.00");
		ShippingQuote free = new ShippingQuote();
		free.setFreeShipping(true);
		stubRequote(free);
		ShippingSummary summary = service.getCheckoutShippingSummary(QUOTE_ID, cart, store, language);
		assertTrue(summary.isFreeShipping());
		assertEquals(0, BigDecimal.ZERO.compareTo(summary.getShipping()));
	}

	@Test
	public void checkoutDoesNotRequoteForeignQuote() throws Exception {
		quote(99L, "3.00");
		try {
			service.getCheckoutShippingSummary(QUOTE_ID, cart, store, language);
			fail("expected rejection");
		} catch (ServiceException e) {
			assertRejected(e, "shipping.quote.invalid");
		}
		verify(shippingService, never()).getShippingQuote(any(), any(), any(), any(), any());
	}

	@Test
	public void checkoutWithoutShippableProductsHasNoShipping() throws Exception {
		quote(CART_ID, "45.00");
		when(shoppingCartService.createShippingProduct(cart)).thenReturn(Collections.emptyList());
		assertNull(service.getCheckoutShippingSummary(QUOTE_ID, cart, store, language));
	}
}
