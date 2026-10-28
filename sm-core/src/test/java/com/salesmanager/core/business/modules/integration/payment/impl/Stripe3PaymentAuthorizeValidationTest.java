package com.salesmanager.core.business.modules.integration.payment.impl;

import static org.junit.Assert.fail;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.util.Collections;

import org.junit.Before;
import org.junit.Test;

import com.salesmanager.core.model.merchant.MerchantStore;
import com.salesmanager.core.model.reference.currency.Currency;
import com.salesmanager.core.modules.integration.IntegrationException;
import com.stripe.model.PaymentIntent;

public class Stripe3PaymentAuthorizeValidationTest {

	private static final BigDecimal ORDER_TOTAL = new BigDecimal("120.00");

	private Stripe3Payment stripe;
	private MerchantStore store;

	@Before
	public void setUp() {
		stripe = new Stripe3Payment();
		store = mock(MerchantStore.class);
		Currency currency = mock(Currency.class);
		when(currency.getCode()).thenReturn("USD");
		when(store.getCurrency()).thenReturn(currency);
	}

	@Test
	public void acceptsCapturableIntentCoveringOrderTotal() throws Exception {
		stripe.validateAuthorizedIntent(intent("requires_capture", 12000L, 12000L), store, ORDER_TOTAL);
	}

	@Test
	public void rejectsUnconfirmedIntent() {
		assertRejected(intent("requires_payment_method", 12000L, null));
	}

	@Test
	public void rejectsIntentWithInsufficientAmount() {
		assertRejected(intent("requires_capture", 50L, 50L));
	}

	@Test
	public void rejectsIntentInAnotherCurrency() {
		PaymentIntent intent = intent("requires_capture", 12000L, 12000L);
		intent.setCurrency("eur");
		assertRejected(intent);
	}

	@Test
	public void rejectsIntentWithoutCurrency() {
		PaymentIntent intent = intent("requires_capture", 12000L, 12000L);
		intent.setCurrency(null);
		assertRejected(intent);
	}

	@Test
	public void rejectsIntentAlreadyUsedByAnotherOrder() {
		PaymentIntent intent = intent("requires_capture", 12000L, 12000L);
		intent.setMetadata(Collections.singletonMap(Stripe3Payment.AUTHORIZED_METADATA, "true"));
		assertRejected(intent);
	}

	@Test
	public void rejectsMissingIntent() {
		assertRejected(null);
	}

	private void assertRejected(PaymentIntent intent) {
		try {
			stripe.validateAuthorizedIntent(intent, store, ORDER_TOTAL);
			fail("Expected IntegrationException");
		} catch (IntegrationException e) {
			// expected
		}
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
