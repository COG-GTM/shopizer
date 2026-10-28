package com.salesmanager.test.payment;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.lang.reflect.Proxy;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Callable;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.CyclicBarrier;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

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
import com.salesmanager.core.model.payments.Transaction;
import com.salesmanager.core.model.reference.currency.Currency;
import com.salesmanager.core.model.system.IntegrationConfiguration;
import com.salesmanager.core.modules.integration.payment.model.PaymentModule;
import com.stripe.Stripe;
import com.stripe.net.ApiResource;
import com.stripe.net.LiveStripeResponseGetter;
import com.stripe.net.RequestOptions;
import com.stripe.net.StripeResponseGetter;

/**
 * Each Stripe call must carry the calling store's secret key in its own
 * RequestOptions instead of relying on the process-wide Stripe.apiKey.
 */
public class StripeRequestApiKeyTest {

	private static final String GLOBAL_KEY = "sk_test_global_must_not_be_used";
	private static final String ACME_KEY = "sk_test_acme";
	private static final String GLOBEX_KEY = "sk_test_globex";

	private final List<String> requestKeys = new CopyOnWriteArrayList<>();
	private final List<String> mismatches = new CopyOnWriteArrayList<>();
	private final ThreadLocal<String> expectedKey = new ThreadLocal<>();

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
	public void stripe3PaymentSendsStoreKeyOnEveryCall() throws Exception {
		Stripe3Payment module = module(new Stripe3Payment());
		MerchantStore store = store("Acme Outdoor");
		IntegrationConfiguration config = config(ACME_KEY);

		module.initTransaction(store, null, new BigDecimal("10.00"), null, config, null);
		module.authorize(store, null, null, new BigDecimal("10.00"), payment("pi_1"), config, null);
		module.capture(store, null, order(), transaction("pi_1"), config, null);
		module.authorizeAndCapture(store, null, null, new BigDecimal("10.00"), payment("pi_1"), config, null);
		module.refund(false, store, transaction("pi_1"), order(), new BigDecimal("10.00"), config, null);

		assertOnlyKey(ACME_KEY, 9);
	}

	@Test
	public void stripePaymentSendsStoreKeyOnEveryCall() throws Exception {
		StripePayment module = module(new StripePayment());
		MerchantStore store = store("Acme Outdoor");
		IntegrationConfiguration config = config(ACME_KEY);

		module.authorize(store, null, null, new BigDecimal("10.00"), payment("tok_1"), config, null);
		module.capture(store, null, order(), transaction("ch_1"), config, null);
		module.authorizeAndCapture(store, null, null, new BigDecimal("10.00"), payment("tok_1"), config, null);
		module.refund(false, store, transaction("ch_1"), order(), new BigDecimal("10.00"), config, null);

		assertOnlyKey(ACME_KEY, 6);
	}

	@Test
	public void concurrentCheckoutsForTwoStoresKeepTheirOwnKeys() throws Exception {
		final Stripe3Payment stripe3 = module(new Stripe3Payment());
		final StripePayment stripe = module(new StripePayment());
		final int iterations = 200;
		final CyclicBarrier start = new CyclicBarrier(2);

		ExecutorService pool = Executors.newFixedThreadPool(2);
		try {
			List<Future<Void>> futures = new ArrayList<>();
			for (final String key : new String[] { ACME_KEY, GLOBEX_KEY }) {
				futures.add(pool.submit(new Callable<Void>() {
					@Override
					public Void call() throws Exception {
						expectedKey.set(key);
						MerchantStore store = store(key);
						IntegrationConfiguration config = config(key);
						start.await(10, TimeUnit.SECONDS);
						for (int i = 0; i < iterations; i++) {
							stripe3.initTransaction(store, null, new BigDecimal("10.00"), null, config, null);
							stripe3.authorizeAndCapture(store, null, null, new BigDecimal("10.00"), payment("pi_1"), config, null);
							stripe.authorizeAndCapture(store, null, null, new BigDecimal("10.00"), payment("tok_1"), config, null);
						}
						return null;
					}
				}));
			}
			for (Future<Void> f : futures) {
				f.get(60, TimeUnit.SECONDS);
			}
		} finally {
			pool.shutdownNow();
		}

		assertEquals(2 * iterations * 4, requestKeys.size());
		assertTrue("Stripe requests sent with another store's key: " + mismatches.subList(0, Math.min(5, mismatches.size())),
				mismatches.isEmpty());
		assertEquals(GLOBAL_KEY, Stripe.apiKey);
	}

	private void assertOnlyKey(String key, int expectedRequests) {
		assertEquals(expectedRequests, requestKeys.size());
		assertEquals(Collections.nCopies(expectedRequests, key), new ArrayList<>(requestKeys));
		assertFalse(requestKeys.contains(GLOBAL_KEY));
		assertEquals("modules must not mutate the global Stripe key", GLOBAL_KEY, Stripe.apiKey);
	}

	private StripeResponseGetter recordingGetter() {
		return (StripeResponseGetter) Proxy.newProxyInstance(
				StripeResponseGetter.class.getClassLoader(),
				new Class<?>[] { StripeResponseGetter.class },
				(proxy, method, args) -> {
					if (method.getDeclaringClass() == Object.class) {
						return method.invoke(this, args);
					}
					RequestOptions options = null;
					Class<?> responseClass = null;
					String url = null;
					for (Object arg : args) {
						if (arg instanceof RequestOptions) {
							options = (RequestOptions) arg;
						} else if (arg instanceof Class) {
							responseClass = (Class<?>) arg;
						} else if (arg instanceof String && url == null) {
							url = (String) arg;
						}
					}
					String key = options == null ? null : options.getApiKey();
					requestKeys.add(String.valueOf(key));
					String expected = expectedKey.get();
					if (expected != null && !expected.equals(key)) {
						mismatches.add(expected + " -> " + key);
					}
					Thread.yield();
					return ApiResource.GSON.fromJson(responseFor(url), responseClass);
				});
	}

	private static String responseFor(String url) {
		if (url.contains("/payment_intents")) {
			return "{\"id\":\"pi_1\",\"object\":\"payment_intent\",\"status\":\"requires_capture\",\"client_secret\":\"pi_1_secret\"}";
		}
		if (url.contains("/charges")) {
			return "{\"id\":\"ch_1\",\"object\":\"charge\",\"status\":\"succeeded\"}";
		}
		if (url.contains("/refunds")) {
			return "{\"id\":\"re_1\",\"object\":\"refund\",\"status\":\"succeeded\"}";
		}
		throw new IllegalArgumentException("unexpected Stripe url " + url);
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

	private static IntegrationConfiguration config(String secretKey) {
		IntegrationConfiguration config = new IntegrationConfiguration();
		Map<String, String> keys = new HashMap<>();
		keys.put("secretKey", secretKey);
		keys.put("publishableKey", "pk_test");
		config.setIntegrationKeys(keys);
		return config;
	}

	private static Payment payment(String token) {
		Payment payment = new Payment();
		Map<String, String> metaData = new HashMap<>();
		metaData.put("stripe_token", token);
		payment.setPaymentMetaData(metaData);
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
		order.setTotal(new BigDecimal("10.00"));
		return order;
	}
}
