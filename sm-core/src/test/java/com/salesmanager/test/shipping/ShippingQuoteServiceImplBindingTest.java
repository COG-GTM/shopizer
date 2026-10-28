package com.salesmanager.test.shipping;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotSame;
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
import org.mockito.ArgumentCaptor;
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

/**
 * Complements ShippingQuoteServiceImplTest (PR #185): handling fee checks, scoped summary mapping,
 * customer binding edge cases and the non persisting requote path.
 */
public class ShippingQuoteServiceImplBindingTest {

	private static final long CART_ID = 20L;
	private static final long QUOTE_ID = 900L;

	private ShippingQuoteRepository repository;
	private ShippingService shippingService;
	private ShoppingCartService shoppingCartService;
	private ShippingQuoteServiceImpl service;
	private MerchantStore store;
	private ShoppingCart cart;
	private Language language;
	private Quote quote;

	@Before
	public void setUp() throws Exception {
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

		Delivery delivery = new Delivery();
		delivery.setPostalCode("H2H2H2");
		delivery.setCity("Montreal");
		quote = new Quote();
		quote.setId(QUOTE_ID);
		quote.setCartId(CART_ID);
		quote.setModule("weightBased");
		quote.setOptionCode("STD");
		quote.setOptionName("Standard");
		quote.setPrice(new BigDecimal("12.00"));
		quote.setHandling(new BigDecimal("2.00"));
		quote.setDelivery(delivery);
		when(repository.findById(QUOTE_ID)).thenReturn(Optional.of(quote));
		when(shoppingCartService.createShippingProduct(cart))
				.thenReturn(Collections.singletonList(new ShippingProduct(null)));
	}

	private void requote(String price, String handling) throws ServiceException {
		ShippingOption option = new ShippingOption();
		option.setShippingModuleCode("weightBased");
		option.setOptionCode("STD");
		option.setOptionPrice(new BigDecimal(price));
		ShippingQuote current = new ShippingQuote();
		current.setShippingOptions(Collections.singletonList(option));
		current.setHandlingFees(handling == null ? null : new BigDecimal(handling));
		when(shippingService.calculateShippingQuote(eq(CART_ID), eq(store), any(), any(), eq(language)))
				.thenReturn(current);
	}

	private void expectRejected(String code) {
		try {
			service.getCheckoutShippingSummary(QUOTE_ID, cart, store, language);
			fail("expected " + code);
		} catch (ServiceException e) {
			assertEquals(ServiceException.EXCEPTION_VALIDATION, e.getExceptionType());
			assertEquals(code, e.getMessageCode());
		}
	}

	@Test
	public void scopedSummaryMapsStoredQuoteValues() throws Exception {
		when(shippingService.hasTaxOnShipping(store)).thenReturn(true);
		ShippingSummary summary = service.getShippingSummary(QUOTE_ID, cart, store);
		assertEquals(0, new BigDecimal("12.00").compareTo(summary.getShipping()));
		assertEquals(0, new BigDecimal("2.00").compareTo(summary.getHandling()));
		assertEquals("weightBased", summary.getShippingModule());
		assertEquals("STD", summary.getShippingOptionCode());
		assertEquals("Standard", summary.getShippingOption());
		assertTrue(summary.isTaxOnShipping());
		assertFalse(summary.isFreeShipping());
		verify(shippingService, never()).calculateShippingQuote(any(), any(), any(), any(), any());
	}

	@Test
	public void scopedSummaryRejectsQuoteOfAnotherCart() {
		quote.setCartId(CART_ID + 1);
		try {
			service.getShippingSummary(QUOTE_ID, cart, store);
			fail("expected rejection");
		} catch (ServiceException e) {
			assertEquals("shipping.quote.invalid", e.getMessageCode());
		}
	}

	@Test
	public void quoteWithoutCustomerIsAcceptedForCustomerCart() throws Exception {
		cart.setCustomerId(5L);
		assertEquals(quote, service.getQuote(QUOTE_ID, cart, store));
	}

	@Test
	public void quoteOfCustomerIsAcceptedForSameCustomerCart() throws Exception {
		cart.setCustomerId(5L);
		quote.setCustomerId(5L);
		assertEquals(quote, service.getQuote(QUOTE_ID, cart, store));
	}

	@Test
	public void quoteOfCustomerIsRejectedForGuestCart() {
		quote.setCustomerId(5L);
		try {
			service.getQuote(QUOTE_ID, cart, store);
			fail("expected rejection");
		} catch (ServiceException e) {
			assertEquals("shipping.quote.invalid", e.getMessageCode());
		}
	}

	@Test
	public void unsavedCartIsRejected() {
		cart.setId(null);
		try {
			service.getQuote(QUOTE_ID, cart, store);
			fail("expected rejection");
		} catch (ServiceException e) {
			assertEquals("shipping.quote.invalid", e.getMessageCode());
		}
	}

	@Test
	public void cartWithoutStoreIsRejected() {
		cart.setMerchantStore(null);
		try {
			service.getQuote(QUOTE_ID, cart, store);
			fail("expected rejection");
		} catch (ServiceException e) {
			assertEquals("shipping.quote.invalid", e.getMessageCode());
		}
	}

	@Test(expected = NullPointerException.class)
	public void nullQuoteIdIsRejected() throws Exception {
		service.getQuote(null, cart, store);
	}

	@Test
	public void checkoutAcceptsMatchingPriceAndHandling() throws Exception {
		requote("12.0", "2");
		ShippingSummary summary = service.getCheckoutShippingSummary(QUOTE_ID, cart, store, language);
		assertEquals(0, new BigDecimal("12.00").compareTo(summary.getShipping()));
		assertEquals(0, new BigDecimal("2.00").compareTo(summary.getHandling()));
	}

	@Test
	public void checkoutRejectsChangedHandlingFee() throws Exception {
		requote("12.00", "5.00");
		expectRejected("shipping.quote.expired");
	}

	@Test
	public void checkoutRejectsRemovedHandlingFee() throws Exception {
		requote("12.00", null);
		expectRejected("shipping.quote.expired");
	}

	@Test
	public void checkoutRejectsHigherCurrentPrice() throws Exception {
		requote("15.00", "2.00");
		expectRejected("shipping.quote.expired");
	}

	@Test
	public void checkoutRejectsWhenRequoteReturnsNothing() throws Exception {
		when(shippingService.calculateShippingQuote(eq(CART_ID), eq(store), any(), any(), eq(language)))
				.thenReturn(null);
		expectRejected("shipping.quote.expired");
	}

	@Test
	public void checkoutRejectsSameOptionCodeFromAnotherModule() throws Exception {
		quote.setModule("ups");
		requote("12.00", "2.00");
		expectRejected("shipping.quote.expired");
	}

	@Test
	public void checkoutRequotesWithoutPersistingToACopyOfTheQuotedAddress() throws Exception {
		requote("12.00", "2.00");
		service.getCheckoutShippingSummary(QUOTE_ID, cart, store, language);
		ArgumentCaptor<Delivery> delivery = ArgumentCaptor.forClass(Delivery.class);
		verify(shippingService).calculateShippingQuote(eq(CART_ID), eq(store), delivery.capture(), any(),
				eq(language));
		verify(shippingService, never()).getShippingQuote(any(), any(), any(), any(), any());
		assertNotSame(quote.getDelivery(), delivery.getValue());
		assertEquals("H2H2H2", delivery.getValue().getPostalCode());
		assertEquals("Montreal", delivery.getValue().getCity());
	}

	@Test
	public void checkoutFreeShippingRequoteChargesNothing() throws Exception {
		ShippingQuote free = new ShippingQuote();
		free.setFreeShipping(true);
		when(shippingService.calculateShippingQuote(eq(CART_ID), eq(store), any(), any(), eq(language)))
				.thenReturn(free);
		ShippingSummary summary = service.getCheckoutShippingSummary(QUOTE_ID, cart, store, language);
		assertTrue(summary.isFreeShipping());
		assertEquals(0, BigDecimal.ZERO.compareTo(summary.getShipping()));
		assertEquals(null, summary.getHandling());
	}
}
