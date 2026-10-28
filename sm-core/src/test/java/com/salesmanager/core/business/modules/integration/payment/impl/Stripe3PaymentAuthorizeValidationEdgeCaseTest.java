package com.salesmanager.core.business.modules.integration.payment.impl;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.fail;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.util.Collections;

import org.junit.Before;
import org.junit.Test;

import com.salesmanager.core.model.merchant.MerchantStore;
import com.salesmanager.core.model.reference.currency.Currency;
import com.salesmanager.core.model.shoppingcart.ShoppingCart;
import com.salesmanager.core.modules.integration.IntegrationException;
import com.stripe.model.PaymentIntent;

public class Stripe3PaymentAuthorizeValidationEdgeCaseTest {

	private static final BigDecimal ORDER_TOTAL = new BigDecimal("12.34");

	private Stripe3Payment stripe;
	private MerchantStore store;
	private Currency currency;

	@Before
	public void setUp() {
		stripe = new Stripe3Payment();
		store = mock(MerchantStore.class);
		currency = mock(Currency.class);
		when(currency.getCode()).thenReturn("USD");
		when(store.getCurrency()).thenReturn(currency);
	}

	@Test
	public void fallsBackToIntentAmountWhenCapturableAmountIsZero() throws Exception {
		stripe.validateAuthorizedIntent(intent("requires_capture", 1234L, 0L), store, ORDER_TOTAL, cart(42L, null));
	}

	@Test
	public void rejectsFallbackAmountBelowOrderTotal() {
		assertRejected(intent("requires_capture", 1233L, 0L), store, ORDER_TOTAL, cart(42L, null));
	}

	@Test
	public void rejectsIntentWhenBothAmountsAreNull() {
		assertRejected(intent("requires_capture", null, null), store, ORDER_TOTAL, cart(42L, null));
	}

	@Test
	public void matchesCurrencyWithoutCaseSensitivity() throws Exception {
		PaymentIntent intent = intent("requires_capture", 1234L, 1234L);
		intent.setCurrency("USD");
		when(currency.getCode()).thenReturn("usd");
		stripe.validateAuthorizedIntent(intent, store, ORDER_TOTAL, cart(42L, null));

		when(currency.getCode()).thenReturn("USD");
		stripe.validateAuthorizedIntent(intent, store, ORDER_TOTAL, cart(42L, null));
	}

	@Test
	public void rejectsWhenStoreCurrencyIsNull() {
		when(store.getCurrency()).thenReturn(null);
		assertRejected(intent("requires_capture", 1234L, 1234L), store, ORDER_TOTAL, cart(42L, null));
	}

	@Test
	public void acceptsExactAmountAndRejectsOneUnitBelow() throws Exception {
		stripe.validateAuthorizedIntent(intent("requires_capture", 1234L, 1234L), store, ORDER_TOTAL, cart(42L, null));
		assertRejected(intent("requires_capture", 1233L, 1233L), store, ORDER_TOTAL, cart(42L, null));
	}

	@Test
	public void declineContainsTransactionExceptionAndPaymentDeclinedCode() {
		try {
			stripe.validateAuthorizedIntent(intent("requires_payment_method", 1234L, 1234L),
					store, ORDER_TOTAL, cart(42L, null));
			fail("Expected IntegrationException");
		} catch (IntegrationException e) {
			assertEquals(IntegrationException.TRANSACTION_EXCEPTION, e.getExceptionType());
			assertEquals(IntegrationException.TRANSACTION_EXCEPTION, e.getErrorCode());
			assertEquals("message.payment.declined", e.getMessageCode());
		}
	}

	@Test
	public void allowsUntaggedIntentWithoutCartButRejectsTaggedIntentWithoutIdentity() throws Exception {
		stripe.validateAuthorizedIntent(intent("requires_capture", 1234L, 1234L), store, ORDER_TOTAL, null);

		PaymentIntent unknownCart = intent("requires_capture", 1234L, 1234L);
		unknownCart.setMetadata(Collections.singletonMap(Stripe3Payment.AUTHORIZED_METADATA, "unknown"));
		assertRejected(unknownCart, store, ORDER_TOTAL, null);

		ShoppingCart unidentifiedCart = cart(null, null);
		PaymentIntent cartNull = intent("requires_capture", 1234L, 1234L);
		cartNull.setMetadata(Collections.singletonMap(Stripe3Payment.AUTHORIZED_METADATA, "cart-null"));
		assertRejected(cartNull, store, ORDER_TOTAL, unidentifiedCart);

		PaymentIntent unknownCartWithUnidentifiedCart = intent("requires_capture", 1234L, 1234L);
		unknownCartWithUnidentifiedCart.setMetadata(
				Collections.singletonMap(Stripe3Payment.AUTHORIZED_METADATA, "unknown"));
		assertRejected(unknownCartWithUnidentifiedCart, store, ORDER_TOTAL, unidentifiedCart);
	}

	@Test
	public void rejectsTaggedIntentForAnotherCartOrAnyCartWithAnOrder() {
		PaymentIntent taggedForCartSeven = intent("requires_capture", 1234L, 1234L);
		taggedForCartSeven.setMetadata(
				Collections.singletonMap(Stripe3Payment.AUTHORIZED_METADATA, "cart-7"));
		assertRejected(taggedForCartSeven, store, ORDER_TOTAL, cart(8L, null));
		assertRejected(taggedForCartSeven, store, ORDER_TOTAL, cart(7L, 99L));
	}

	private void assertRejected(PaymentIntent intent, MerchantStore selectedStore, BigDecimal total, ShoppingCart cart) {
		try {
			stripe.validateAuthorizedIntent(intent, selectedStore, total, cart);
			fail("Expected IntegrationException");
		} catch (IntegrationException expected) {
			// expected
		}
	}

	private ShoppingCart cart(Long id, Long orderId) {
		ShoppingCart cart = new ShoppingCart();
		cart.setId(id);
		cart.setOrderId(orderId);
		return cart;
	}

	private PaymentIntent intent(String status, Long amount, Long amountCapturable) {
		PaymentIntent intent = new PaymentIntent();
		intent.setStatus(status);
		intent.setAmount(amount);
		intent.setAmountCapturable(amountCapturable);
		intent.setCurrency("usd");
		return intent;
	}
}
