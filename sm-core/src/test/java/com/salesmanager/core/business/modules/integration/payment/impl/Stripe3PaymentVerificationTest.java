package com.salesmanager.core.business.modules.integration.payment.impl;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertThrows;

import java.math.BigDecimal;
import java.util.HashMap;

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

public class Stripe3PaymentVerificationTest {

	private static final String INTENT_ID = "pi_verification_123";
	private static final BigDecimal ORDER_AMOUNT = new BigDecimal("1234.56");
	private static final long EXPECTED_AMOUNT = 123456L;

	private MerchantStore store;
	private IntegrationConfiguration configuration;
	private FakeStripe3Payment module;

	@Before
	public void setUp() {
		store = storeWithCurrency("USD");
		configuration = new IntegrationConfiguration();
		configuration.getIntegrationKeys().put("secretKey", "sk_test_dummy");
		module = new FakeStripe3Payment();
		module.intent = intent(EXPECTED_AMOUNT, "usd", Stripe3Payment.STATUS_REQUIRES_CAPTURE);
		module.captureResult = intent(EXPECTED_AMOUNT, "usd", Stripe3Payment.STATUS_SUCCEEDED);
		module.captureResult.setAmountReceived(EXPECTED_AMOUNT);
	}

	@Test
	public void convertsUsdAmountsWithoutFormattingOrScaleDependence() throws Exception {
		assertEquals(123456L, Stripe3Payment.toStripeAmount(ORDER_AMOUNT, store));
		assertEquals(55347L, Stripe3Payment.toStripeAmount(new BigDecimal("553.47"), store));
		assertEquals(1000L, Stripe3Payment.toStripeAmount(new BigDecimal("10"), store));
		assertEquals(1001L, Stripe3Payment.toStripeAmount(new BigDecimal("10.005"), store));
		assertEquals(100000L, Stripe3Payment.toStripeAmount(new BigDecimal("1000.00"), store));
	}

	@Test
	public void convertsYenWithoutDecimalPlaces() throws Exception {
		assertEquals(900L, Stripe3Payment.toStripeAmount(new BigDecimal("900"), storeWithCurrency("JPY")));
	}

	@Test
	public void convertsIskWithStripeTwoDecimalExponent() throws Exception {
		assertEquals(50000L, Stripe3Payment.toStripeAmount(new BigDecimal("500"), storeWithCurrency("ISK")));
	}

	@Test
	public void convertsUgxWithStripeTwoDecimalExponent() throws Exception {
		assertEquals(50000L, Stripe3Payment.toStripeAmount(new BigDecimal("500"), storeWithCurrency("UGX")));
	}

	@Test
	public void rejectsNullAmount() {
		assertPaymentError(assertThrows(IntegrationException.class, () -> Stripe3Payment.toStripeAmount(null, store)));
	}

	@Test
	public void rejectsZeroAmount() {
		assertPaymentError(assertThrows(IntegrationException.class,
				() -> Stripe3Payment.toStripeAmount(BigDecimal.ZERO, store)));
	}

	@Test
	public void rejectsNegativeAmount() {
		assertPaymentError(assertThrows(IntegrationException.class,
				() -> Stripe3Payment.toStripeAmount(new BigDecimal("-1.00"), store)));
	}

	@Test
	public void verifiesMatchingIntent() throws Exception {
		Stripe3Payment.verifyPaymentIntent(module.intent, EXPECTED_AMOUNT, "usd", Stripe3Payment.STATUS_REQUIRES_CAPTURE);
	}

	@Test
	public void verifiesCurrencyIgnoringCase() throws Exception {
		Stripe3Payment.verifyPaymentIntent(module.intent, EXPECTED_AMOUNT, "USD", Stripe3Payment.STATUS_REQUIRES_CAPTURE);
		module.intent.setCurrency("USD");
		Stripe3Payment.verifyPaymentIntent(module.intent, EXPECTED_AMOUNT, "usd", Stripe3Payment.STATUS_REQUIRES_CAPTURE);
	}

	@Test
	public void rejectsNullIntent() {
		assertIntentRejected(null);
	}

	@Test
	public void rejectsIntentWithNullAmount() {
		module.intent.setAmount(null);
		assertIntentRejected(module.intent);
	}

	@Test
	public void rejectsIntentWithWrongAmount() {
		module.intent.setAmount(EXPECTED_AMOUNT - 1);
		assertIntentRejected(module.intent);
	}

	@Test
	public void rejectsIntentWithNullCurrency() {
		module.intent.setCurrency(null);
		assertIntentRejected(module.intent);
	}

	@Test
	public void rejectsIntentWithWrongCurrency() {
		module.intent.setCurrency("jpy");
		assertIntentRejected(module.intent);
	}

	@Test
	public void rejectsIntentWithWrongStatus() {
		module.intent.setStatus("requires_confirmation");
		assertIntentRejected(module.intent);
	}

	@Test
	public void authorizesApiTokenAndRecordsOrderAmount() throws Exception {
		Payment payment = payment();
		assertFalse(payment.getPaymentMetaData().containsKey("stripe_token"));
		Transaction transaction = module.authorize(store, null, null, ORDER_AMOUNT, payment, configuration, null);
		assertEquals(TransactionType.AUTHORIZE, transaction.getTransactionType());
		assertEquals(ORDER_AMOUNT, transaction.getAmount());
		assertEquals(INTENT_ID, transaction.getTransactionDetails().get("TRANSACTIONID"));
		assertEquals(INTENT_ID, transaction.getTransactionDetails().get("TRNORDERNUMBER"));
		assertEquals(Stripe3Payment.STATUS_REQUIRES_CAPTURE, transaction.getTransactionDetails().get("TRNAPPROVED"));
	}

	@Test
	public void rejectsAuthorizationWithoutToken() {
		Payment payment = payment();
		payment.getPaymentMetaData().clear();
		assertPaymentError(assertThrows(IntegrationException.class,
				() -> module.authorize(store, null, null, ORDER_AMOUNT, payment, configuration, null)));
	}

	@Test
	public void rejectsCaptureWithWrongAmountReceived() {
		module.captureResult.setAmountReceived(EXPECTED_AMOUNT - 1);
		assertCaptureRejected();
	}

	@Test
	public void rejectsCaptureWithoutSucceededStatus() {
		module.captureResult.setStatus(Stripe3Payment.STATUS_REQUIRES_CAPTURE);
		assertCaptureRejected();
	}

	@Test
	public void capturesExpectedAmountAndRecordsSuccessfulTransaction() throws Exception {
		Transaction transaction = module.authorizeAndCapture(store, null, null, ORDER_AMOUNT,
				payment(), configuration, null);
		assertNotNull(module.captureParams);
		assertEquals(Long.valueOf(EXPECTED_AMOUNT), module.captureParams.getAmountToCapture());
		assertEquals(TransactionType.AUTHORIZECAPTURE, transaction.getTransactionType());
		assertEquals(ORDER_AMOUNT, transaction.getAmount());
		assertEquals(Stripe3Payment.STATUS_SUCCEEDED, transaction.getTransactionDetails().get("TRNAPPROVED"));
		assertEquals(INTENT_ID, transaction.getTransactionDetails().get("TRANSACTIONID"));
		assertEquals(INTENT_ID, transaction.getTransactionDetails().get("TRNORDERNUMBER"));
	}

	private void assertIntentRejected(PaymentIntent intent) {
		assertPaymentError(assertThrows(IntegrationException.class, () -> Stripe3Payment.verifyPaymentIntent(
				intent, EXPECTED_AMOUNT, "USD", Stripe3Payment.STATUS_REQUIRES_CAPTURE)));
	}

	private void assertCaptureRejected() {
		assertPaymentError(assertThrows(IntegrationException.class, () -> module.authorizeAndCapture(
				store, null, null, ORDER_AMOUNT, payment(), configuration, null)));
		assertNotNull(module.captureParams);
		assertEquals(Long.valueOf(EXPECTED_AMOUNT), module.captureParams.getAmountToCapture());
	}

	private static void assertPaymentError(IntegrationException exception) {
		assertEquals("message.payment.error", exception.getMessageCode());
		assertEquals(IntegrationException.TRANSACTION_EXCEPTION, exception.getExceptionType());
		assertEquals(IntegrationException.TRANSACTION_EXCEPTION, exception.getErrorCode());
	}

	private static MerchantStore storeWithCurrency(String code) {
		Currency currency = new Currency();
		currency.setCurrency(java.util.Currency.getInstance(code));
		MerchantStore store = new MerchantStore();
		store.setCurrency(currency);
		store.setStorename("Acme Shop");
		return store;
	}

	private static Payment payment() {
		Payment payment = new Payment();
		payment.setPaymentMetaData(new HashMap<String, String>());
		payment.getPaymentMetaData().put("paymentToken", INTENT_ID);
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
		PaymentIntent captureResult;
		PaymentIntentCaptureParams captureParams;

		@Override
		PaymentIntent retrievePaymentIntent(String paymentIntentId) {
			assertEquals(INTENT_ID, paymentIntentId);
			return intent;
		}

		@Override
		PaymentIntent capturePaymentIntent(PaymentIntent paymentIntent, PaymentIntentCaptureParams params) {
			assertEquals(intent, paymentIntent);
			captureParams = params;
			return captureResult;
		}
	}
}
