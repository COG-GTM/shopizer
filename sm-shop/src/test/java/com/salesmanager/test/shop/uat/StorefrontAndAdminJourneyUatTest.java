package com.salesmanager.test.shop.uat;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

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

import com.salesmanager.shop.application.ShopApplication;
import com.salesmanager.shop.model.catalog.product.ReadableProduct;
import com.salesmanager.test.shop.common.ServicesTestSupport;

/**
 * Black-box journeys the Angular admin and React storefront rely on (REST + JWT contract).
 */
@SpringBootTest(classes = ShopApplication.class, webEnvironment = WebEnvironment.RANDOM_PORT)
@RunWith(SpringRunner.class)
public class StorefrontAndAdminJourneyUatTest extends ServicesTestSupport {

	private static final String ADMIN = "admin@shopizer.com";

	@Test
	public void adminLogsInAndCallsPrivateApi() {
		ResponseEntity<Map> login = post("/api/v1/private/login", credentials(ADMIN, "password"), null);
		assertEquals(HttpStatus.OK, login.getStatusCode());
		String token = (String) login.getBody().get("token");
		assertNotNull(token);

		ResponseEntity<Map> profile = call(HttpMethod.GET, "/api/v1/private/user/profile", null, token);
		assertEquals(HttpStatus.OK, profile.getStatusCode());
		assertEquals(ADMIN, profile.getBody().get("emailAddress"));

		assertEquals(HttpStatus.UNAUTHORIZED,
				call(HttpMethod.GET, "/api/v1/private/user/profile", null, null).getStatusCode());
		assertFalse(call(HttpMethod.GET, "/api/v1/private/user/profile", null, token.substring(0, token.length() - 4) + "AAAA")
				.getStatusCode().is2xxSuccessful());
		assertEquals(HttpStatus.UNAUTHORIZED,
				post("/api/v1/private/login", credentials(ADMIN, "wrong"), null).getStatusCode());
	}

	@Test
	public void customerRegistersLogsInAndReadsProfile() {
		String email = registerCustomer("uat-profile");
		String token = customerToken(email);
		ResponseEntity<Map> profile = call(HttpMethod.GET, "/api/v1/auth/customer/profile", null, token);
		assertEquals(HttpStatus.OK, profile.getStatusCode());
		assertEquals(email, profile.getBody().get("emailAddress"));
		assertEquals(HttpStatus.UNAUTHORIZED,
				call(HttpMethod.GET, "/api/v1/auth/customer/profile", null, null).getStatusCode());
	}

	@Test
	public void shopperBrowsesStoreCategoriesAndProducts() {
		ReadableProduct created = sampleProduct("uatbrowse");

		ResponseEntity<Map> store = call(HttpMethod.GET, "/api/v1/store/DEFAULT", null, null);
		assertEquals(HttpStatus.OK, store.getStatusCode());
		assertEquals("DEFAULT", store.getBody().get("code"));

		ResponseEntity<Map> categories = call(HttpMethod.GET, "/api/v1/category?store=DEFAULT&lang=en", null, null);
		assertEquals(HttpStatus.OK, categories.getStatusCode());
		assertFalse(((List<?>) categories.getBody().get("categories")).isEmpty());

		ResponseEntity<Map> products = call(HttpMethod.GET, "/api/v1/products?store=DEFAULT&lang=en", null, null);
		assertEquals(HttpStatus.OK, products.getStatusCode());
		assertTrue(((Number) products.getBody().get("recordsTotal")).intValue() >= 1);
		assertTrue(((List<Map<String, Object>>) products.getBody().get("products")).stream()
				.anyMatch(p -> "uatbrowse".equals(p.get("sku"))));

		ResponseEntity<Map> detail = call(HttpMethod.GET, "/api/v2/product/uatbrowse?store=DEFAULT&lang=en", null, null);
		assertEquals(HttpStatus.OK, detail.getStatusCode());
		assertEquals("uatbrowse", detail.getBody().get("sku"));
		assertEquals(created.getId().longValue(), ((Number) detail.getBody().get("id")).longValue());
	}

	@Test
	public void shopperCreatesUpdatesAndReadsCart() {
		sampleProduct("uatcart");
		ResponseEntity<Map> created = post("/api/v1/cart?store=DEFAULT", cartItem("uatcart", 1), null);
		assertEquals(HttpStatus.CREATED, created.getStatusCode());
		String code = (String) created.getBody().get("code");
		assertNotNull(code);

		ResponseEntity<Map> updated = call(HttpMethod.PUT, "/api/v1/cart/" + code + "?store=DEFAULT", cartItem("uatcart", 3), null);
		assertEquals(HttpStatus.CREATED, updated.getStatusCode());

		ResponseEntity<Map> read = call(HttpMethod.GET, "/api/v1/cart/" + code + "?store=DEFAULT", null, null);
		assertEquals(HttpStatus.OK, read.getStatusCode());
		assertEquals(code, read.getBody().get("code"));
		assertEquals(3, ((Number) read.getBody().get("quantity")).intValue());
		List<Map<String, Object>> items = (List<Map<String, Object>>) read.getBody().get("products");
		assertEquals(1, items.size());
		assertEquals("uatcart", items.get(0).get("sku"));
	}

	@Test
	public void authenticatedCustomerChecksOutAndReadsOrderBack() {
		sampleProduct("uatcheckout");
		Map<String, Object> moneyOrder = new HashMap<>();
		moneyOrder.put("code", "moneyorder");
		moneyOrder.put("active", true);
		moneyOrder.put("defaultSelected", true);
		moneyOrder.put("integrationKeys", map("address", "1 Main"));
		assertEquals(HttpStatus.OK, post("/api/v1/private/modules/payment", moneyOrder, adminToken()).getStatusCode());

		String token = customerToken(registerCustomer("uat-checkout"));
		ResponseEntity<Map> cart = post("/api/v1/cart?store=DEFAULT", cartItem("uatcheckout", 1), null);
		assertEquals(HttpStatus.CREATED, cart.getStatusCode());
		String code = (String) cart.getBody().get("code");

		Map<String, Object> payment = new HashMap<>();
		payment.put("paymentType", "MONEYORDER");
		payment.put("transactionType", "CAPTURE");
		payment.put("paymentModule", "moneyorder");
		payment.put("amount", String.valueOf(cart.getBody().get("total")));
		Map<String, Object> order = new HashMap<>();
		order.put("currency", "CAD");
		order.put("payment", payment);

		ResponseEntity<Map> confirmation = post("/api/v1/auth/cart/" + code + "/checkout?store=DEFAULT", order, token);
		assertEquals(HttpStatus.OK, confirmation.getStatusCode());
		Number orderId = (Number) confirmation.getBody().get("id");
		assertNotNull(orderId);

		ResponseEntity<Map> readBack = call(HttpMethod.GET, "/api/v1/auth/orders/" + orderId + "?store=DEFAULT", null, token);
		assertEquals(HttpStatus.OK, readBack.getStatusCode());
		assertEquals(orderId.longValue(), ((Number) readBack.getBody().get("id")).longValue());
		assertEquals("moneyorder", readBack.getBody().get("paymentModule"));

		ResponseEntity<Map> history = call(HttpMethod.GET, "/api/v1/auth/orders?store=DEFAULT", null, token);
		assertEquals(HttpStatus.OK, history.getStatusCode());
		assertTrue(((List<Map<String, Object>>) history.getBody().get("orders")).stream()
				.anyMatch(o -> orderId.longValue() == ((Number) o.get("id")).longValue()));
	}

	@Test
	public void servicesApiRequiresValidBasicAuth() {
		HttpEntity<Void> json = new HttpEntity<>(jsonHeaders());
		HttpStatus valid = testRestTemplate.withBasicAuth(ADMIN, "password")
				.exchange("/services/private/system/module", HttpMethod.GET, json, String.class).getStatusCode();
		assertNotEquals(HttpStatus.UNAUTHORIZED, valid);
		assertNotEquals(HttpStatus.FORBIDDEN, valid);
		assertEquals(HttpStatus.UNAUTHORIZED, testRestTemplate.withBasicAuth(ADMIN, "wrong")
				.exchange("/services/private/system/module", HttpMethod.GET, json, String.class).getStatusCode());
		assertEquals(HttpStatus.UNAUTHORIZED, testRestTemplate
				.exchange("/services/private/system/module", HttpMethod.GET, json, String.class).getStatusCode());
	}

	@Test
	public void healthIsUpAndApiDocsAreServed() {
		ResponseEntity<Map> health = call(HttpMethod.GET, "/actuator/health", null, null);
		assertEquals(HttpStatus.OK, health.getStatusCode());
		assertEquals("UP", health.getBody().get("status"));

		ResponseEntity<Map> docs = call(HttpMethod.GET, "/v2/api-docs", null, null);
		assertEquals(HttpStatus.OK, docs.getStatusCode());
		assertEquals("2.0", docs.getBody().get("swagger"));
		assertFalse(((Map<?, ?>) docs.getBody().get("paths")).isEmpty());
	}

	private String registerCustomer(String prefix) {
		String email = prefix + "-" + System.nanoTime() + "@test.com";
		Map<String, Object> billing = new HashMap<>();
		billing.put("firstName", "Uat");
		billing.put("lastName", "Shopper");
		billing.put("country", "CA");
		billing.put("zone", "QC");
		billing.put("city", "Montreal");
		billing.put("address", "1 Main");
		billing.put("postalCode", "H2H2H2");
		Map<String, Object> customer = new HashMap<>();
		customer.put("emailAddress", email);
		customer.put("password", "clear123");
		customer.put("gender", "M");
		customer.put("language", "en");
		customer.put("storeCode", "DEFAULT");
		customer.put("billing", billing);
		assertEquals(HttpStatus.OK, post("/api/v1/customer/register", customer, null).getStatusCode());
		return email;
	}

	private String customerToken(String email) {
		ResponseEntity<Map> login = post("/api/v1/customer/login", credentials(email, "clear123"), null);
		assertEquals(HttpStatus.OK, login.getStatusCode());
		return (String) login.getBody().get("token");
	}

	private String adminToken() {
		return (String) post("/api/v1/private/login", credentials(ADMIN, "password"), null).getBody().get("token");
	}

	private static Map<String, Object> credentials(String username, String password) {
		Map<String, Object> body = map("username", username);
		body.put("password", password);
		return body;
	}

	private static Map<String, Object> cartItem(String sku, int quantity) {
		Map<String, Object> item = map("product", sku);
		item.put("quantity", quantity);
		return item;
	}

	private static Map<String, Object> map(String key, Object value) {
		Map<String, Object> m = new HashMap<>();
		m.put(key, value);
		return m;
	}

	private static HttpHeaders jsonHeaders() {
		HttpHeaders headers = new HttpHeaders();
		headers.setContentType(MediaType.APPLICATION_JSON);
		headers.setAccept(java.util.Collections.singletonList(MediaType.APPLICATION_JSON));
		return headers;
	}

	private ResponseEntity<Map> post(String path, Object body, String token) {
		return call(HttpMethod.POST, path, body, token);
	}

	private ResponseEntity<Map> call(HttpMethod method, String path, Object body, String token) {
		HttpHeaders headers = jsonHeaders();
		if (token != null) {
			headers.add("Authorization", "Bearer " + token);
		}
		return testRestTemplate.exchange(path, method, new HttpEntity<>(body, headers), Map.class);
	}
}
