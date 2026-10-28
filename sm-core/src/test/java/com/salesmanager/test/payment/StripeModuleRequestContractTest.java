package com.salesmanager.test.payment;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.lang.reflect.Proxy;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.springframework.test.util.ReflectionTestUtils;

import com.salesmanager.core.business.modules.integration.payment.impl.Stripe3Payment;
import com.salesmanager.core.business.modules.integration.payment.impl.StripePayment;
import com.salesmanager.core.business.utils.ProductPriceUtils;
import com.salesmanager.core.model.merchant.MerchantStore;
import com.salesmanager.core.model.order.Order;
import com.salesmanager.core.model.payments.Payment;
import com.salesmanager.core.model.payments.PaymentType;
import com.salesmanager.core.model.payments.Transaction;
import com.salesmanager.core.model.payments.TransactionType;
import com.salesmanager.core.model.reference.currency.Currency;
import com.salesmanager.core.model.system.IntegrationConfiguration;
import com.salesmanager.core.modules.integration.IntegrationException;
import com.salesmanager.core.modules.integration.payment.model.PaymentModule;
import com.stripe.Stripe;
import com.stripe.exception.AuthenticationException;
import com.stripe.exception.CardException;
import com.stripe.exception.StripeException;
import com.stripe.net.ApiResource;
import com.stripe.net.LiveStripeResponseGetter;
import com.stripe.net.RequestOptions;
import com.stripe.net.StripeResponseGetter;

public class StripeModuleRequestContractTest {

	private static final String GLOBAL_KEY = "sk_test_global";
	private static final String STORE_KEY = "sk_test_store";
	private static final BigDecimal AMOUNT = new BigDecimal("10.00");

	private final List<RecordedRequest> requests = new ArrayList<>();
	private Function<RecordedRequest, String> responseFactory = this::defaultResponse;
	private StripeException stripeException;
	private String previousGlobalKey;

	@Before
	public void installRecordingStripeClient() {
		previousGlobalKey = Stripe.apiKey;
		Stripe.apiKey = GLOBAL_KEY;
		ApiResource.setStripeResponseGetter(recordingGetter());
	}

	@After
	public void restoreStripeClient() {
		ApiResource.setStripeResponseGetter(new LiveStripeResponseGetter());
		Stripe.apiKey = previousGlobalKey;
	}

	@Test
	public void stripe3InitTransactionCreatesManualCaptureIntentWithStoreKey() throws Exception {
		Stripe.apiKey = null;
		Stripe3Payment module = module(new Stripe3Payment());
		IntegrationConfiguration configuration = configuration(STORE_KEY);

		Transaction transaction = module.initTransaction(store("Acme Outdoor"), null, AMOUNT, null, configuration, null);

		assertEquals(1, requests.size());
		RecordedRequest request = requests.get(0);
		assertRequest(request, ApiResource.RequestMethod.POST, "/v1/payment_intents", STORE_KEY);
		assertEquals("1000", String.valueOf(request.params.get("amount")));
		assertEquals("usd", String.valueOf(request.params.get("currency")).toLowerCase());
		assertEquals("manual", String.valueOf(request.params.get("capture_method")).toLowerCase());
		assertEquals("pi_init", transaction.getTransactionDetails().get("TRANSACTIONID"));
		assertEquals("pi_init", transaction.getTransactionDetails().get("TRNORDERNUMBER"));
		assertEquals("pi_init_secret", transaction.getTransactionDetails().get("INTENTSECRET"));
		assertEquals("requires_capture", transaction.getTransactionDetails().get("TRNAPPROVED"));
		assertEquals(TransactionType.AUTHORIZE, transaction.getTransactionType());
		assertEquals(null, Stripe.apiKey);
	}

	@Test
	public void stripe3CaptureRetrievesThenCapturesWithStoreKeyAndTruncatedDescriptor() throws Exception {
		Stripe3Payment module = module(new Stripe3Payment());
		MerchantStore store = store("Acme Outdoor Supplies Incorporated");

		Transaction captured = module.capture(store, null, order(), transaction("pi_1"),
				configuration(STORE_KEY), null);

		assertEquals(2, requests.size());
		assertRequest(requests.get(0), ApiResource.RequestMethod.GET, "/v1/payment_intents/pi_1", STORE_KEY);
		RecordedRequest capture = requests.get(1);
		assertRequest(capture, ApiResource.RequestMethod.POST, "/v1/payment_intents/pi_1/capture", STORE_KEY);
		assertEquals("1000", String.valueOf(capture.params.get("amount_to_capture")));
		assertEquals(store.getStorename().substring(0, 22), capture.params.get("statement_descriptor"));
		assertEquals("succeeded", captured.getTransactionDetails().get("TRNAPPROVED"));
		assertEquals(TransactionType.CAPTURE, captured.getTransactionType());
	}

	@Test
	public void stripe3AuthorizeAndCaptureAcceptsApiPaymentToken() throws Exception {
		Stripe3Payment module = module(new Stripe3Payment());
		Payment payment = payment("paymentToken", "pi_api");

		Transaction transaction = module.authorizeAndCapture(store("Acme Outdoor"), null, null, AMOUNT, payment,
				configuration(STORE_KEY), null);

		assertEquals(2, requests.size());
		assertRequest(requests.get(0), ApiResource.RequestMethod.GET, "/v1/payment_intents/pi_api", STORE_KEY);
		assertRequest(requests.get(1), ApiResource.RequestMethod.POST, "/v1/payment_intents/pi_api/capture", STORE_KEY);
		assertEquals("pi_api", transaction.getTransactionDetails().get("TRANSACTIONID"));
		assertEquals("succeeded", transaction.getTransactionDetails().get("TRNAPPROVED"));
	}

	@Test
	public void stripe3RefundPostsRefundForIntentWithStoreKey() throws Exception {
		Stripe3Payment module = module(new Stripe3Payment());

		module.refund(false, store("Acme Outdoor"), transaction("pi_1"), order(), AMOUNT,
				configuration(STORE_KEY), null);

		assertEquals(2, requests.size());
		assertRequest(requests.get(0), ApiResource.RequestMethod.GET, "/v1/payment_intents/pi_1", STORE_KEY);
		RecordedRequest refund = requests.get(1);
		assertRequest(refund, ApiResource.RequestMethod.POST, "/v1/refunds", STORE_KEY);
		assertEquals("pi_1", refund.params.get("payment_intent"));
		assertEquals("1000", String.valueOf(refund.params.get("amount")));
	}

	@Test
	public void stripePaymentAuthorizePostsUncapturedChargeWithStoreKey() throws Exception {
		StripePayment module = module(new StripePayment());
		MerchantStore store = store("Acme Outdoor");

		module.authorize(store, null, null, AMOUNT, payment("stripe_token", "tok_1"),
				configuration(STORE_KEY), null);

		assertEquals(1, requests.size());
		RecordedRequest request = requests.get(0);
		assertRequest(request, ApiResource.RequestMethod.POST, "/v1/charges", STORE_KEY);
		assertEquals("1000", String.valueOf(request.params.get("amount")));
		assertEquals("false", String.valueOf(request.params.get("capture")));
		assertEquals("tok_1", request.params.get("source"));
		assertEquals("usd", String.valueOf(request.params.get("currency")).toLowerCase());
		assertEquals("Transaction - " + store.getStorename(), request.params.get("description"));
	}

	@Test
	public void stripePaymentCaptureReportsStatusAfterCapture() throws Exception {
		StripePayment module = module(new StripePayment());

		Transaction captured = module.capture(store("Acme Outdoor"), null, order(), transaction("ch_1"),
				configuration(STORE_KEY), null);

		assertEquals(2, requests.size());
		assertRequest(requests.get(0), ApiResource.RequestMethod.GET, "/v1/charges/ch_1", STORE_KEY);
		assertRequest(requests.get(1), ApiResource.RequestMethod.POST, "/v1/charges/ch_1/capture", STORE_KEY);
		assertEquals("succeeded", captured.getTransactionDetails().get("TRNAPPROVED"));
	}

	@Test
	public void missingSecretKeyFailsWithoutCallingStripeOrFallingBackToGlobalKey() throws Exception {
		Stripe3Payment stripe3 = module(new Stripe3Payment());
		StripePayment stripe = module(new StripePayment());
		MerchantStore store = store("Acme Outdoor");
		IntegrationConfiguration missingKey = configuration(null);
		Transaction existing = transaction("pi_1");

		expectIntegrationException(() -> stripe3.initTransaction(store, null, AMOUNT, null, missingKey, null));
		expectIntegrationException(() -> stripe3.authorize(store, null, null, AMOUNT,
				payment("stripe_token", "pi_1"), missingKey, null));
		expectIntegrationException(() -> stripe3.capture(store, null, order(), existing, missingKey, null));
		expectIntegrationException(() -> stripe3.authorizeAndCapture(store, null, null, AMOUNT,
				payment("stripe_token", "pi_1"), missingKey, null));
		expectIntegrationException(() -> stripe3.refund(false, store, existing, order(), AMOUNT, missingKey, null));
		expectIntegrationException(() -> stripe.authorize(store, null, null, AMOUNT,
				payment("stripe_token", "tok_1"), missingKey, null));
		expectIntegrationException(() -> stripe.capture(store, null, order(), transaction("ch_1"), missingKey, null));
		expectIntegrationException(() -> stripe.authorizeAndCapture(store, null, null, AMOUNT,
				payment("stripe_token", "tok_1"), missingKey, null));
		expectIntegrationException(() -> stripe.refund(false, store, transaction("ch_1"), order(), AMOUNT,
				missingKey, null));
		expectIntegrationException(() -> stripe3.initTransaction(store, null, AMOUNT, null, configuration(""), null));

		assertTrue(requests.isEmpty());
		assertEquals(GLOBAL_KEY, Stripe.apiKey);
	}

	@Test
	public void stripeAuthenticationErrorBecomesIntegrationException() throws Exception {
		Stripe3Payment stripe3 = module(new Stripe3Payment());
		stripeException = new AuthenticationException("Invalid API Key provided", "req_1", "invalid_api_key", 401);

		IntegrationException stripe3Error = expectIntegrationException(
				() -> stripe3.initTransaction(store("Acme Outdoor"), null, AMOUNT, null, configuration(STORE_KEY), null));
		assertEquals(IntegrationException.TRANSACTION_EXCEPTION, stripe3Error.getExceptionType());
		assertEquals("message.payment.error", stripe3Error.getMessageCode());

		stripeException = new AuthenticationException("Invalid API Key provided", "req_1", "invalid_api_key", 401);
		StripePayment stripe = module(new StripePayment());
		IntegrationException stripeError = expectIntegrationException(() -> stripe.authorize(store("Acme Outdoor"),
				null, null, AMOUNT, payment("stripe_token", "tok_1"), configuration(STORE_KEY), null));
		assertEquals(IntegrationException.TRANSACTION_EXCEPTION, stripeError.getExceptionType());
		assertEquals("message.payment.error", stripeError.getMessageCode());
		assertEquals(2, requests.size());
	}

	@Test
	public void stripeCardDeclinedMapsToPaymentDeclined() throws Exception {
		StripePayment stripe = module(new StripePayment());
		stripeException = cardDeclined();

		IntegrationException stripeError = expectIntegrationException(() -> stripe.authorize(store("Acme Outdoor"),
				null, null, AMOUNT, payment("stripe_token", "tok_1"), configuration(STORE_KEY), null));
		assertEquals(IntegrationException.EXCEPTION_PAYMENT_DECLINED, stripeError.getExceptionType());
		assertEquals("message.payment.declined", stripeError.getMessageCode());

		Stripe3Payment stripe3 = module(new Stripe3Payment());
		stripeException = cardDeclined();
		IntegrationException stripe3Error = expectIntegrationException(() -> stripe3.authorizeAndCapture(
				store("Acme Outdoor"), null, null, AMOUNT, payment("stripe_token", "pi_1"),
				configuration(STORE_KEY), null));
		assertEquals(IntegrationException.EXCEPTION_PAYMENT_DECLINED, stripe3Error.getExceptionType());
		assertEquals("message.payment.declined", stripe3Error.getMessageCode());
		assertEquals(2, requests.size());
	}

	@Test
	public void eachStoreKeyIsUsedWhenStoresAlternateOnOneThread() throws Exception {
		Stripe3Payment stripe3 = module(new Stripe3Payment());
		StripePayment stripe = module(new StripePayment());
		MerchantStore storeA = store("Store A");
		MerchantStore storeB = store("Store B");

		stripe3.initTransaction(storeA, null, AMOUNT, null, configuration("sk_a"), null);
		stripe3.initTransaction(storeB, null, AMOUNT, null, configuration("sk_b"), null);
		stripe3.initTransaction(storeA, null, AMOUNT, null, configuration("sk_a"), null);
		stripe3.initTransaction(storeB, null, AMOUNT, null, configuration("sk_b"), null);
		stripe.authorize(storeA, null, null, AMOUNT, payment("stripe_token", "tok_a"),
				configuration("sk_a"), null);
		stripe.authorize(storeB, null, null, AMOUNT, payment("stripe_token", "tok_b"),
				configuration("sk_b"), null);

		assertEquals(Arrays.asList("sk_a", "sk_b", "sk_a", "sk_b", "sk_a", "sk_b"),
				requests.stream().map(request -> request.apiKey).collect(Collectors.toList()));
		assertEquals(GLOBAL_KEY, Stripe.apiKey);
	}

	private StripeResponseGetter recordingGetter() {
		return (StripeResponseGetter) Proxy.newProxyInstance(
				StripeResponseGetter.class.getClassLoader(),
				new Class<?>[] { StripeResponseGetter.class },
				(proxy, method, args) -> {
				if (method.getDeclaringClass() == Object.class) {
					return method.invoke(this, args);
				}
				RequestOptions options = args.length > 4 ? (RequestOptions) args[4] : null;
				RecordedRequest request = new RecordedRequest((ApiResource.RequestMethod) args[0],
						(String) args[1], (Map<String, Object>) args[2], (Class<?>) args[3],
						options == null ? null : options.getApiKey());
					requests.add(request);
					if (stripeException != null) {
						throw stripeException;
					}
					return ApiResource.GSON.fromJson(responseFactory.apply(request), request.responseClass);
				});
	}

	private String defaultResponse(RecordedRequest request) {
		String url = request.url;
		if (url.endsWith("/v1/payment_intents")) {
			return "{\"id\":\"pi_init\",\"object\":\"payment_intent\",\"status\":\"requires_capture\","
					+ "\"client_secret\":\"pi_init_secret\"}";
		}
		if (url.contains("/v1/payment_intents/")) {
			String id = resourceId(url, "/v1/payment_intents/");
			String status = url.endsWith("/capture") ? "succeeded" : "requires_capture";
			return "{\"id\":\"" + id + "\",\"object\":\"payment_intent\",\"status\":\"" + status
					+ "\",\"client_secret\":\"" + id + "_secret\"}";
		}
		if (url.endsWith("/v1/charges")) {
			return "{\"id\":\"ch_created\",\"object\":\"charge\",\"status\":\"succeeded\"}";
		}
		if (url.contains("/v1/charges/")) {
			String id = resourceId(url, "/v1/charges/");
			if (url.endsWith("/capture")) {
				return "{\"id\":\"" + id + "\",\"object\":\"charge\",\"status\":\"succeeded\"}";
			}
			return "{\"id\":\"" + id + "\",\"object\":\"charge\",\"status\":\"pending\",\"captured\":false}";
		}
		if (url.endsWith("/v1/refunds")) {
			return "{\"id\":\"re_1\",\"object\":\"refund\",\"reason\":\"requested_by_customer\"}";
		}
		throw new IllegalArgumentException("unexpected Stripe url " + url);
	}

	private static String resourceId(String url, String marker) {
		String resource = url.substring(url.indexOf(marker) + marker.length());
		int slash = resource.indexOf('/');
		return slash < 0 ? resource : resource.substring(0, slash);
	}

	private static void assertRequest(RecordedRequest request, ApiResource.RequestMethod method, String urlSuffix,
			String apiKey) {
		assertEquals(method, request.method);
		assertTrue("unexpected request URL " + request.url, request.url.endsWith(urlSuffix));
		assertEquals(apiKey, request.apiKey);
	}

	private static <T extends PaymentModule> T module(T module) throws Exception {
		ProductPriceUtils priceUtils = mock(ProductPriceUtils.class);
		when(priceUtils.getAdminFormatedAmount(any(), any())).thenReturn("10.00");
		ReflectionTestUtils.setField(module, "productPriceUtils", priceUtils);
		return module;
	}

	private static MerchantStore store(String name) {
		Currency currency = new Currency();
		currency.setCurrency(java.util.Currency.getInstance("USD"));
		MerchantStore store = new MerchantStore();
		store.setStorename(name);
		store.setCurrency(currency);
		return store;
	}

	private static IntegrationConfiguration configuration(String secretKey) {
		IntegrationConfiguration configuration = new IntegrationConfiguration();
		Map<String, String> keys = new HashMap<>();
		keys.put("secretKey", secretKey);
		keys.put("publishableKey", "pk_test");
		configuration.setIntegrationKeys(keys);
		return configuration;
	}

	private static Payment payment(String metadataKey, String token) {
		Payment payment = new Payment();
		payment.setPaymentType(PaymentType.CREDITCARD);
		Map<String, String> metadata = new HashMap<>();
		metadata.put(metadataKey, token);
		payment.setPaymentMetaData(metadata);
		return payment;
	}

	private static Transaction transaction(String id) {
		Transaction transaction = new Transaction();
		transaction.getTransactionDetails().put("TRANSACTIONID", id);
		transaction.getTransactionDetails().put("TRNORDERNUMBER", id);
		return transaction;
	}

	private static Order order() {
		Order order = new Order();
		order.setTotal(AMOUNT);
		return order;
	}

	private static CardException cardDeclined() {
		return new CardException("Card was declined", "req_2", "card_declined", null,
				"card_declined", "ch_1", 402, null);
	}

	private static IntegrationException expectIntegrationException(CheckedCall call) throws Exception {
		try {
			call.run();
			fail("expected IntegrationException");
		} catch (IntegrationException exception) {
			return exception;
		}
		throw new AssertionError("expected IntegrationException");
	}

	private interface CheckedCall {
		void run() throws Exception;
	}

	private static final class RecordedRequest {
		private final ApiResource.RequestMethod method;
		private final String url;
		private final Map<String, Object> params;
		private final Class<?> responseClass;
		private final String apiKey;

		private RecordedRequest(ApiResource.RequestMethod method, String url, Map<String, Object> params,
				Class<?> responseClass, String apiKey) {
			this.method = method;
			this.url = url;
			this.params = params;
			this.responseClass = responseClass;
			this.apiKey = apiKey;
		}
	}
}
