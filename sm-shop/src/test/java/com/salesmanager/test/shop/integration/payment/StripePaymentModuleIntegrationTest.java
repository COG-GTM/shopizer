package com.salesmanager.test.shop.integration.payment;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import java.lang.reflect.Proxy;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.function.Function;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.SpringBootTest.WebEnvironment;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.test.context.junit4.SpringRunner;
import org.springframework.test.util.ReflectionTestUtils;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.salesmanager.core.business.services.payments.PaymentService;
import com.salesmanager.shop.application.ShopApplication;
import com.salesmanager.test.shop.common.ServicesTestSupport;
import com.stripe.Stripe;
import com.stripe.net.ApiResource;
import com.stripe.net.LiveStripeResponseGetter;
import com.stripe.net.RequestOptions;
import com.stripe.net.StripeResponseGetter;

@SpringBootTest(classes = ShopApplication.class, webEnvironment = WebEnvironment.RANDOM_PORT)
@RunWith(SpringRunner.class)
public class StripePaymentModuleIntegrationTest extends ServicesTestSupport {

	private static final String GLOBAL_KEY = "sk_test_global_sentinel";
	private static final ObjectMapper JSON = new ObjectMapper();

	@Autowired
	private PaymentService paymentService;

	private final List<RecordedRequest> requests = new ArrayList<>();
	private String previousGlobalKey;
	private Function<RecordedRequest, String> responseFactory = request -> "{\"id\":\"pi_integration\","
			+ "\"object\":\"payment_intent\",\"status\":\"requires_capture\","
			+ "\"client_secret\":\"pi_integration_secret\"}";

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
	public void paymentModulesAreWiredInContext() throws Exception {
		Object stripe3 = paymentService.getPaymentModule("stripe3");
		Object stripe = paymentService.getPaymentModule("stripe");

		assertTrue(stripe3 instanceof com.salesmanager.core.business.modules.integration.payment.impl.Stripe3Payment);
		assertTrue(stripe instanceof com.salesmanager.core.business.modules.integration.payment.impl.StripePayment);
		assertNotNull(ReflectionTestUtils.getField(stripe3, "productPriceUtils"));
		assertNotNull(ReflectionTestUtils.getField(stripe, "productPriceUtils"));
	}

	@Test
	public void adminConfiguresStripe3ThenCartPaymentInitUsesStoreKey() throws Exception {
		HttpHeaders admin = getHeader();
		ResponseEntity<String> configured = post("/api/v1/private/modules/payment?store=DEFAULT",
				config("sk_test_default_store", "pk_test_default_store"), admin);
		assertEquals(HttpStatus.OK, configured.getStatusCode());

		ResponseEntity<String> readConfig = get("/api/v1/private/modules/payment/stripe3?store=DEFAULT", admin);
		assertEquals(HttpStatus.OK, readConfig.getStatusCode());
		assertTrue(JSON.readTree(readConfig.getBody()).get("active").asBoolean());

		String sku = "stripeintegration";
		sampleProduct(sku);
		ResponseEntity<String> cartResponse = post("/api/v1/cart?store=DEFAULT",
				map("product", sku, "quantity", 1), json());
		assertEquals(HttpStatus.CREATED, cartResponse.getStatusCode());
		String cartCode = JSON.readTree(cartResponse.getBody()).get("code").asText();

		ResponseEntity<String> init = post("/api/v1/cart/" + cartCode + "/payment/init?store=DEFAULT",
				map("paymentModule", "stripe3", "paymentType", "CREDITCARD", "transactionType", "INIT",
						"amount", "10.00"),
				json());
		assertEquals(init.getBody(), HttpStatus.OK, init.getStatusCode());
		JsonNode transaction = JSON.readTree(init.getBody());
		assertEquals("AUTHORIZE", transaction.get("transactionType").asText());

		assertEquals(1, requests.size());
		RecordedRequest request = requests.get(0);
		assertEquals(ApiResource.RequestMethod.POST, request.method);
		assertTrue(request.url.endsWith("/v1/payment_intents"));
		assertEquals("sk_test_default_store", request.apiKey);
		assertEquals("1000", String.valueOf(request.params.get("amount")));
		assertEquals(GLOBAL_KEY, Stripe.apiKey);
	}

	@Test
	public void stripe3ConfigurationWithoutSecretKeyIsRejected() throws Exception {
		HttpHeaders admin = getHeader();
		ResponseEntity<String> configured = post("/api/v1/private/modules/payment?store=DEFAULT",
				map("code", "stripe3", "active", true, "defaultSelected", false,
						"integrationKeys", map("publishableKey", "pk_test_missing_secret")),
				admin);
		assertFalse(configured.getStatusCode().is2xxSuccessful());
		assertTrue(requests.isEmpty());
		assertEquals(GLOBAL_KEY, Stripe.apiKey);
	}

	private ResponseEntity<String> get(String url, HttpHeaders headers) {
		return testRestTemplate.exchange(url, HttpMethod.GET, new HttpEntity<>(headers), String.class);
	}

	private ResponseEntity<String> post(String url, Object body, HttpHeaders headers) {
		return testRestTemplate.exchange(url, HttpMethod.POST, new HttpEntity<>(body, headers), String.class);
	}

	private static Map<String, Object> config(String secretKey, String publishableKey) {
		return map("code", "stripe3", "active", true, "defaultSelected", false,
				"integrationKeys", map("secretKey", secretKey, "publishableKey", publishableKey));
	}

	private static Map<String, Object> map(Object... values) {
		java.util.HashMap<String, Object> result = new java.util.HashMap<>();
		for (int i = 0; i < values.length; i += 2) {
			result.put((String) values[i], values[i + 1]);
		}
		return result;
	}

	private static HttpHeaders json() {
		HttpHeaders headers = new HttpHeaders();
		headers.setContentType(MediaType.APPLICATION_JSON);
		return headers;
	}

	private StripeResponseGetter recordingGetter() {
		return (StripeResponseGetter) Proxy.newProxyInstance(StripeResponseGetter.class.getClassLoader(),
				new Class<?>[] { StripeResponseGetter.class }, (proxy, method, args) -> {
					if (method.getDeclaringClass() == Object.class) {
						return method.invoke(this, args);
					}
					RequestOptions options = args.length > 4 ? (RequestOptions) args[4] : null;
					RecordedRequest request = new RecordedRequest((ApiResource.RequestMethod) args[0], (String) args[1],
							(Map<String, Object>) args[2], options == null ? null : options.getApiKey());
					requests.add(request);
					return ApiResource.GSON.fromJson(responseFactory.apply(request), (Class<?>) args[3]);
				});
	}

	private static final class RecordedRequest {
		private final ApiResource.RequestMethod method;
		private final String url;
		private final Map<String, Object> params;
		private final String apiKey;

		private RecordedRequest(ApiResource.RequestMethod method, String url, Map<String, Object> params,
				String apiKey) {
			this.method = method;
			this.url = url;
			this.params = params;
			this.apiKey = apiKey;
		}
	}
}
