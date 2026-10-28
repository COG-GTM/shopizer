package com.salesmanager.core.business.modules.integration.payment.impl;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import java.math.BigDecimal;
import java.util.HashMap;
import java.util.Map;

import org.junit.Before;
import org.junit.Test;

import com.salesmanager.core.model.merchant.MerchantStore;
import com.salesmanager.core.model.payments.Payment;
import com.salesmanager.core.model.payments.Transaction;
import com.salesmanager.core.model.payments.TransactionType;
import com.salesmanager.core.model.reference.currency.Currency;
import com.salesmanager.core.model.system.IntegrationConfiguration;
import com.salesmanager.core.modules.integration.IntegrationException;
import com.stripe.model.PaymentIntent;
import com.stripe.param.PaymentIntentCaptureParams;

public class Stripe3PaymentTest {

	private static final String INTENT_ID = "pi_test_123";

	private MerchantStore store;
	private IntegrationConfiguration configuration;
	private FakeStripe3Payment module;

	@Before
	public void setUp() {
		Currency currency = new Currency();
		currency.setCurrency(java.util.Currency.getInstance("USD"));
		store = new MerchantStore();
		store.setCurrency(currency);
		store.setStorename("Acme Shop");

		configuration = new IntegrationConfiguration();
		configuration.getIntegrationKeys().put("secretKey", "sk_test_dummy");

		module = new FakeStripe3Payment();
	}

	@Test
	public void authorizeAcceptsHeldIntentMatchingOrderTotal() throws Exception {
		module.intent = intent(90000L, "usd", Stripe3Payment.STATUS_REQUIRES_CAPTURE);

		Transaction transaction = module.authorize(store, null, null, new BigDecimal("900.00"),
				payment("stripe_token"), configuration, null);

		assertEquals(TransactionType.AUTHORIZE, transaction.getTransactionType());
		assertEquals(new BigDecimal("900.00"), transaction.getAmount());
		assertEquals(INTENT_ID, transaction.getTransactionDetails().get("TRNORDERNUMBER"));
	}

	@Test
	public void authorizeAcceptsApiPaymentToken() throws Exception {
		module.intent = intent(90000L, "usd", Stripe3Payment.STATUS_REQUIRES_CAPTURE);

		Transaction transaction = module.authorize(store, null, null, new BigDecimal("900.00"),
				payment("paymentToken"), configuration, null);

		assertEquals(INTENT_ID, transaction.getTransactionDetails().get("TRANSACTIONID"));
	}

	@Test
	public void authorizeRejectsIntentForSmallerAmount() {
		module.intent = intent(50L, "usd", Stripe3Payment.STATUS_REQUIRES_CAPTURE);
		assertAuthorizeRejected("amount");
	}

	@Test
	public void authorizeRejectsUnconfirmedIntent() {
		module.intent = intent(90000L, "usd", "requires_payment_method");
		assertAuthorizeRejected("status");
	}

	@Test
	public void authorizeRejectsIntentInOtherCurrency() {
		module.intent = intent(90000L, "jpy", Stripe3Payment.STATUS_REQUIRES_CAPTURE);
		assertAuthorizeRejected("currency");
	}

	@Test
	public void authorizeAndCaptureCapturesMatchingIntent() throws Exception {
		module.intent = intent(123456L, "usd", Stripe3Payment.STATUS_REQUIRES_CAPTURE);

		Transaction transaction = module.authorizeAndCapture(store, null, null, new BigDecimal("1234.56"),
				payment("stripe_token"), configuration, null);

		assertTrue(module.captured);
		assertEquals(Long.valueOf(123456L), module.capturedAmount);
		assertEquals(TransactionType.AUTHORIZECAPTURE, transaction.getTransactionType());
		assertEquals(Stripe3Payment.STATUS_SUCCEEDED, transaction.getTransactionDetails().get("TRNAPPROVED"));
	}

	@Test
	public void authorizeAndCaptureDoesNotCaptureSmallerIntent() {
		module.intent = intent(50L, "usd", Stripe3Payment.STATUS_REQUIRES_CAPTURE);
		try {
			module.authorizeAndCapture(store, null, null, new BigDecimal("900.00"),
					payment("stripe_token"), configuration, null);
			fail("Expected IntegrationException");
		} catch (IntegrationException e) {
			assertTrue(e.getMessage().contains("amount"));
		}
		assertFalse(module.captured);
	}

	@Test
	public void authorizeAndCaptureRejectsUnconfirmedIntent() {
		module.intent = intent(90000L, "usd", "requires_confirmation");
		try {
			module.authorizeAndCapture(store, null, null, new BigDecimal("900.00"),
					payment("stripe_token"), configuration, null);
			fail("Expected IntegrationException");
		} catch (IntegrationException e) {
			assertTrue(e.getMessage().contains("status"));
		}
		assertFalse(module.captured);
	}

	@Test
	public void toStripeAmountUsesCurrencyMinorUnits() throws Exception {
		assertEquals(50L, Stripe3Payment.toStripeAmount(new BigDecimal("0.5"), store));
		assertEquals(123456L, Stripe3Payment.toStripeAmount(new BigDecimal("1234.56"), store));
		assertEquals(90000L, Stripe3Payment.toStripeAmount(new BigDecimal("900"), store));

		Currency yen = new Currency();
		yen.setCurrency(java.util.Currency.getInstance("JPY"));
		MerchantStore yenStore = new MerchantStore();
		yenStore.setCurrency(yen);
		assertEquals(900L, Stripe3Payment.toStripeAmount(new BigDecimal("900"), yenStore));

		assertEquals(50000L, Stripe3Payment.toStripeAmount(new BigDecimal("500"), storeWithCurrency("ISK")));
		assertEquals(50000L, Stripe3Payment.toStripeAmount(new BigDecimal("500"), storeWithCurrency("UGX")));
	}

	private static MerchantStore storeWithCurrency(String code) {
		Currency currency = new Currency();
		currency.setCurrency(java.util.Currency.getInstance(code));
		MerchantStore merchantStore = new MerchantStore();
		merchantStore.setCurrency(currency);
		return merchantStore;
	}

	@Test(expected = IntegrationException.class)
	public void toStripeAmountRejectsNonPositiveAmount() throws Exception {
		Stripe3Payment.toStripeAmount(BigDecimal.ZERO, store);
	}

	private void assertAuthorizeRejected(String reason) {
		try {
			module.authorize(store, null, null, new BigDecimal("900.00"), payment("stripe_token"), configuration, null);
			fail("Expected IntegrationException");
		} catch (IntegrationException e) {
			assertEquals(IntegrationException.TRANSACTION_EXCEPTION, e.getErrorCode());
			assertTrue(e.getMessage(), e.getMessage().contains(reason));
		}
	}

	private static Payment payment(String tokenKey) {
		Payment payment = new Payment();
		Map<String, String> metadata = new HashMap<String, String>();
		metadata.put(tokenKey, INTENT_ID);
		payment.setPaymentMetaData(metadata);
		return payment;
	}

	private static PaymentIntent intent(Long amount, String currency, String status) {
		PaymentIntent intent = new PaymentIntent();
		intent.setId(INTENT_ID);
		intent.setAmount(amount);
		intent.setCurrency(currency);
		intent.setStatus(status);
		return intent;
	}

	private static class FakeStripe3Payment extends Stripe3Payment {
		PaymentIntent intent;
		boolean captured;
		Long capturedAmount;

		@Override
		PaymentIntent retrievePaymentIntent(String paymentIntentId) {
			assertEquals(INTENT_ID, paymentIntentId);
			return intent;
		}

		@Override
		PaymentIntent capturePaymentIntent(PaymentIntent paymentIntent, PaymentIntentCaptureParams params) {
			captured = true;
			capturedAmount = params.getAmountToCapture();
			paymentIntent.setStatus(STATUS_SUCCEEDED);
			paymentIntent.setAmountReceived(params.getAmountToCapture());
			return paymentIntent;
		}
	}
}
