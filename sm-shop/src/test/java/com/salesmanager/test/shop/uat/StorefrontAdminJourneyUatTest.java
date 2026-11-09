package com.salesmanager.test.shop.uat;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.HashMap;
import java.util.Map;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.SpringBootTest.WebEnvironment;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.salesmanager.shop.application.ShopApplication;
import com.salesmanager.shop.model.catalog.product.ReadableProduct;
import com.salesmanager.test.shop.common.ServicesTestSupport;

/**
 * Black-box journeys the Angular admin and React shop rely on (REST/JWT contract).
 */
@SpringBootTest(classes = ShopApplication.class, webEnvironment = WebEnvironment.RANDOM_PORT)
public class StorefrontAdminJourneyUatTest extends ServicesTestSupport {

	private static final ObjectMapper MAPPER = new ObjectMapper();

	private ResponseEntity<String> call(HttpMethod method, String url, Object body, HttpHeaders headers) {
		HttpHeaders h = headers == null ? new HttpHeaders() : headers;
		if (h.getContentType() == null) {
			h.setContentType(MediaType.APPLICATION_JSON);
		}
		return testRestTemplate.exchange(url, method, new HttpEntity<>(body, h), String.class);
	}

	private JsonNode ok(ResponseEntity<String> r) throws Exception {
		assertTrue(r.getStatusCode().is2xxSuccessful(), r.getStatusCode() + " " + r.getBody());
		return r.getBody() == null ? MAPPER.missingNode() : MAPPER.readTree(r.getBody());
	}

	private static HttpHeaders bearer(String token) {
		HttpHeaders h = new HttpHeaders();
		h.setContentType(MediaType.APPLICATION_JSON);
		h.setBearerAuth(token);
		return h;
	}

	private static HttpHeaders basic(String user, String password) {
		HttpHeaders h = new HttpHeaders();
		h.set(HttpHeaders.AUTHORIZATION, "Basic "
				+ Base64.getEncoder().encodeToString((user + ":" + password).getBytes(StandardCharsets.UTF_8)));
		return h;
	}

	private String registerAndLoginCustomer(String email) throws Exception {
		Map<String, Object> billing = new HashMap<>();
		billing.put("firstName", "Uat");
		billing.put("lastName", "Customer");
		billing.put("country", "CA");
		billing.put("stateProvince", "QC");
		billing.put("city", "Montreal");
		billing.put("postalCode", "H2H2H2");
		billing.put("address", "1 Main St");
		billing.put("phone", "5145555555");
		Map<String, Object> customer = new HashMap<>();
		customer.put("emailAddress", email);
		customer.put("password", "clear123");
		customer.put("gender", "M");
		customer.put("language", "en");
		customer.put("storeCode", "DEFAULT");
		customer.put("billing", billing);
		ok(call(HttpMethod.POST, "/api/v1/customer/register", customer, null));

		JsonNode login = ok(call(HttpMethod.POST, "/api/v1/customer/login",
				Map.of("username", email, "password", "clear123"), null));
		String token = login.get("token").asText();
		assertTrue(token.split("\\.").length == 3, "JWT expected: " + token);
		return token;
	}

	@Test
	void adminLoginThenAdminOnlyCallAndRejections() throws Exception {
		JsonNode login = ok(call(HttpMethod.POST, "/api/v1/private/login",
				Map.of("username", "admin@shopizer.com", "password", "password"), null));
		assertTrue(login.get("token").isTextual());
		assertTrue(login.has("id"), login.toString());
		String token = login.get("token").asText();

		JsonNode customers = ok(call(HttpMethod.GET, "/api/v1/private/customers", null, bearer(token)));
		assertTrue(customers.has("customers"), customers.toString());
		assertTrue(customers.has("recordsTotal"), customers.toString());
		JsonNode profile = ok(call(HttpMethod.GET, "/api/v1/private/user/profile", null, bearer(token)));
		assertEquals("admin@shopizer.com", profile.get("emailAddress").asText());

		assertEquals(HttpStatus.UNAUTHORIZED, call(HttpMethod.GET, "/api/v1/private/customers", null, null).getStatusCode());
		// a malformed bearer token is rejected (currently 500 on base and head, not 401)
		assertTrue(call(HttpMethod.GET, "/api/v1/private/customers", null, bearer("not.a.jwt")).getStatusCode().isError());
		assertEquals(HttpStatus.UNAUTHORIZED, call(HttpMethod.POST, "/api/v1/private/login",
				Map.of("username", "admin@shopizer.com", "password", "wrong"), null).getStatusCode());
	}

	@Test
	void customerRegisterLoginAndAuthenticatedCall() throws Exception {
		String token = registerAndLoginCustomer("uat.customer1@test.com");
		JsonNode profile = ok(call(HttpMethod.GET, "/api/v1/auth/customer/profile", null, bearer(token)));
		assertEquals("uat.customer1@test.com", profile.get("emailAddress").asText());
		assertEquals("CA", profile.get("billing").get("country").asText());

		assertEquals(HttpStatus.UNAUTHORIZED,
				call(HttpMethod.GET, "/api/v1/auth/customer/profile", null, null).getStatusCode());
	}

	@Test
	void browseStoreCategoriesProductsAndDetail() throws Exception {
		ReadableProduct created = sampleProduct("uatbrowse");
		assertNotNull(created);

		JsonNode store = ok(call(HttpMethod.GET, "/api/v1/store/DEFAULT", null, null));
		assertEquals("DEFAULT", store.get("code").asText());
		assertTrue(store.has("currency") && store.has("defaultLanguage"), store.toString());

		JsonNode categories = ok(call(HttpMethod.GET, "/api/v1/category?store=DEFAULT&lang=en", null, null));
		assertTrue(categories.get("categories").isArray(), categories.toString());
		assertTrue(categories.get("categories").size() > 0, categories.toString());

		JsonNode products = ok(call(HttpMethod.GET, "/api/v1/products?store=DEFAULT&lang=en", null, null));
		assertTrue(products.get("products").isArray(), products.toString());
		assertTrue(products.get("recordsTotal").asInt() > 0, products.toString());

		JsonNode detail = ok(call(HttpMethod.GET, "/api/v2/product/" + created.getSku() + "?store=DEFAULT&lang=en", null, null));
		assertEquals(created.getId(), detail.get("id").asLong());
		assertEquals(created.getSku(), detail.get("sku").asText());
		assertTrue(detail.has("price") && detail.has("finalPrice"), detail.toString());
	}

	@Test
	void cartCreateAddUpdateRead() throws Exception {
		ReadableProduct p1 = sampleProduct("uatcart1");
		JsonNode cart = ok(call(HttpMethod.POST, "/api/v1/cart?store=DEFAULT",
				Map.of("product", p1.getSku(), "quantity", 1), null));
		String code = cart.get("code").asText();
		assertNotNull(code);
		assertEquals(1, cart.get("quantity").asInt(), cart.toString());

		JsonNode updated = ok(call(HttpMethod.PUT, "/api/v1/cart/" + code + "?store=DEFAULT",
				Map.of("product", p1.getSku(), "quantity", 3), null));
		assertEquals(3, updated.get("products").get(0).get("quantity").asInt(), updated.toString());

		JsonNode read = ok(call(HttpMethod.GET, "/api/v1/cart/" + code + "?store=DEFAULT", null, null));
		assertEquals(code, read.get("code").asText());
		assertEquals(1, read.get("products").size(), read.toString());
		assertEquals(3, read.get("products").get(0).get("quantity").asInt());
		assertTrue(read.has("subtotal") && read.has("total") && read.has("totals"), read.toString());
	}

	@Test
	void authenticatedCustomerCheckoutAndOrderReadBack() throws Exception {
		HttpHeaders admin = getHeader();
		ok(call(HttpMethod.POST, "/api/v1/private/modules/payment",
				Map.of("code", "moneyorder", "active", true, "defaultSelected", true,
						"integrationKeys", Map.of("address", "1 Main St")), admin));
		ReadableProduct product = sampleProduct("uatcheckout");
		String token = registerAndLoginCustomer("uat.checkout@test.com");

		JsonNode cart = ok(call(HttpMethod.POST, "/api/v1/cart?store=DEFAULT",
				Map.of("product", product.getSku(), "quantity", 2), null));
		String code = cart.get("code").asText();
		String total = cart.get("total").asText().replaceAll("[^0-9.]", "");

		Map<String, Object> payment = new HashMap<>();
		payment.put("paymentType", "MONEYORDER");
		payment.put("transactionType", "CAPTURE");
		payment.put("paymentModule", "moneyorder");
		payment.put("amount", total);
		payment.put("currency", "CAD");
		JsonNode confirmation = ok(call(HttpMethod.POST, "/api/v1/auth/cart/" + code + "/checkout?store=DEFAULT",
				Map.of("currency", "CAD", "payment", payment), bearer(token)));
		long orderId = confirmation.get("id").asLong();
		assertTrue(orderId > 0, confirmation.toString());
		assertEquals("CA", confirmation.get("billing").get("country").asText());

		JsonNode order = ok(call(HttpMethod.GET, "/api/v1/auth/orders/" + orderId + "?store=DEFAULT", null, bearer(token)));
		assertEquals(orderId, order.get("id").asLong(), order.toString());
		assertEquals(1, order.get("products").size(), order.toString());

		JsonNode orders = ok(call(HttpMethod.GET, "/api/v1/auth/orders?store=DEFAULT", null, bearer(token)));
		assertEquals(1, orders.get("orders").size(), orders.toString());
	}

	@Test
	void servicesBasicAuth() throws Exception {
		// packaged app answers 401 for missing/invalid credentials; asserting "rejected" keeps the test
		// independent of how the test client surfaces the Basic challenge
		ResponseEntity<String> noAuth = call(HttpMethod.POST, "/services/private/system/optin", Map.of("code", "uat"), null);
		assertTrue(noAuth.getStatusCode().is4xxClientError(), noAuth.getStatusCode().toString());
		ResponseEntity<String> bad = call(HttpMethod.POST, "/services/private/system/optin", Map.of("code", "uat"),
				basic("admin@shopizer.com", "wrong"));
		assertTrue(bad.getStatusCode().is4xxClientError(), bad.getStatusCode().toString());
		ResponseEntity<String> good = call(HttpMethod.POST, "/services/private/system/optin", Map.of("code", "uat"),
				basic("admin@shopizer.com", "password"));
		assertTrue(good.getStatusCode().is2xxSuccessful(), good.getStatusCode() + " " + good.getBody());
		ResponseEntity<String> pub = call(HttpMethod.GET, "/services/public/DEFAULT", null, null);
		assertTrue(pub.getStatusCode().is2xxSuccessful(), pub.getStatusCode().toString());
	}

	@Test
	void actuatorHealthUpAndApiDocs() throws Exception {
		JsonNode health = ok(call(HttpMethod.GET, "/actuator/health", null, null));
		assertEquals("UP", health.get("status").asText());

		JsonNode docs = ok(call(HttpMethod.GET, "/v3/api-docs", null, null));
		assertTrue(docs.get("openapi").asText().startsWith("3."), docs.path("openapi").toString());
		java.util.List<String> paths = new java.util.ArrayList<>();
		docs.get("paths").fieldNames().forEachRemaining(paths::add);
		assertTrue(paths.stream().anyMatch(x -> x.startsWith("/api/v1/")), paths.toString());
	}
}
