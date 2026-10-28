package com.salesmanager.test.shop.uat;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import java.lang.reflect.Proxy;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.SpringBootTest.WebEnvironment;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.test.context.junit4.SpringRunner;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.salesmanager.shop.application.ShopApplication;
import com.salesmanager.test.shop.common.ServicesTestSupport;
import com.stripe.Stripe;
import com.stripe.net.ApiResource;
import com.stripe.net.LiveStripeResponseGetter;
import com.stripe.net.RequestOptions;
import com.stripe.net.StripeResponseGetter;

@SpringBootTest(classes = ShopApplication.class, webEnvironment = WebEnvironment.RANDOM_PORT)
@RunWith(SpringRunner.class)
public class StripeCheckoutJourneyUatTest extends ServicesTestSupport {

	private static final String GLOBAL_KEY = "sk_test_global_sentinel";
	private static final String STORE_KEY = "sk_test_uat_store";
	private static final ObjectMapper JSON = new ObjectMapper();

	private final List<RecordedRequest> requests = new ArrayList<>();
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
	public void stripeCheckoutUsesStoreKeyForInitAndCapture() throws Exception {
		HttpHeaders admin = getHeader();
		ResponseEntity<String> configured = call(HttpMethod.POST, "/api/v1/private/modules/payment?store=DEFAULT",
				map("code", "stripe3", "active", true, "defaultSelected", false,
						"integrationKeys", map("secretKey", STORE_KEY, "publishableKey", "pk_test_uat_store")),
				admin);
		assertEquals(HttpStatus.OK, configured.getStatusCode());

		String sku = "stripeuatcheckout";
		sampleProduct(sku);
		String email = "stripeuat" + java.util.UUID.randomUUID().toString().substring(0, 8) + "@test.com";
		call(HttpMethod.POST, "/api/v1/customer/register",
				map("emailAddress", email, "password", "clear123", "gender", "M", "language", "en",
						"storeCode", "DEFAULT", "billing",
						map("firstName", "Stripe", "lastName", "Customer", "country", "CA")),
				json());
		ResponseEntity<String> login = call(HttpMethod.POST, "/api/v1/customer/login",
				map("username", email, "password", "clear123"), json());
		assertEquals(HttpStatus.OK, login.getStatusCode());
		HttpHeaders customer = json();
		customer.setBearerAuth(JSON.readTree(login.getBody()).get("token").asText());

		ResponseEntity<String> cartResponse = call(HttpMethod.POST, "/api/v1/cart?store=DEFAULT",
				map("product", sku, "quantity", 1), customer);
		assertEquals(HttpStatus.CREATED, cartResponse.getStatusCode());
		JsonNode cart = JSON.readTree(cartResponse.getBody());
		String code = cart.get("code").asText();
		String total = cart.get("total").asText();

		ResponseEntity<String> init = call(HttpMethod.POST,
				"/api/v1/cart/" + code + "/payment/init?store=DEFAULT",
				map("paymentModule", "stripe3", "paymentType", "CREDITCARD", "transactionType", "INIT",
						"amount", total),
				customer);
		assertEquals(init.getBody(), HttpStatus.OK, init.getStatusCode());

		ResponseEntity<String> checkout = call(HttpMethod.POST,
				"/api/v1/auth/cart/" + code + "/checkout?store=DEFAULT",
				map("payment", map("paymentModule", "stripe3", "paymentType", "CREDITCARD",
						"transactionType", "AUTHORIZECAPTURE", "paymentToken", "pi_uat", "amount", total),
						"currency", "CAD", "customerAgreement", true),
				customer);
		assertEquals(checkout.getBody(), HttpStatus.OK, checkout.getStatusCode());
		long orderId = JSON.readTree(checkout.getBody()).get("id").asLong();
		assertTrue(orderId > 0);
		ResponseEntity<String> order = call(HttpMethod.GET, "/api/v1/auth/orders/" + orderId + "?store=DEFAULT", null,
				customer);
		assertEquals(HttpStatus.OK, order.getStatusCode());

		assertEquals(3, requests.size());
		assertRecorded(requests.get(0), ApiResource.RequestMethod.POST, "/v1/payment_intents");
		assertRecorded(requests.get(1), ApiResource.RequestMethod.GET, "/v1/payment_intents/pi_uat");
		assertRecorded(requests.get(2), ApiResource.RequestMethod.POST, "/v1/payment_intents/pi_uat/capture");
		assertEquals(String.valueOf(new java.math.BigDecimal(total).movePointRight(2).intValue()),
				String.valueOf(requests.get(2).params.get("amount_to_capture")));
		assertEquals(GLOBAL_KEY, Stripe.apiKey);
	}

	private void assertRecorded(RecordedRequest request, ApiResource.RequestMethod method, String path) {
		assertEquals(method, request.method);
		assertTrue(request.url.endsWith(path));
		assertEquals(STORE_KEY, request.apiKey);
	}

	private ResponseEntity<String> call(HttpMethod method, String url, Object body, HttpHeaders headers) {
		return testRestTemplate.exchange(url, method, new HttpEntity<>(body, headers), String.class);
	}

	private static Map<String, Object> map(Object... values) {
		Map<String, Object> result = new HashMap<>();
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
					String response;
					if (request.url.endsWith("/v1/payment_intents")) {
						response = "{\"id\":\"pi_init\",\"object\":\"payment_intent\","
								+ "\"status\":\"requires_capture\",\"client_secret\":\"pi_init_secret\"}";
					} else if (request.url.endsWith("/v1/payment_intents/pi_uat")) {
						response = "{\"id\":\"pi_uat\",\"object\":\"payment_intent\","
								+ "\"status\":\"requires_capture\",\"client_secret\":\"pi_uat_secret\"}";
					} else {
						response = "{\"id\":\"pi_uat\",\"object\":\"payment_intent\",\"status\":\"succeeded\"}";
					}
					return ApiResource.GSON.fromJson(response, (Class<?>) args[3]);
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
