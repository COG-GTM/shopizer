package com.salesmanager.test.shop.uat;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import java.util.HashMap;
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

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.salesmanager.shop.application.ShopApplication;
import com.salesmanager.shop.model.catalog.product.ReadableProduct;
import com.salesmanager.test.shop.common.ServicesTestSupport;

/**
 * Black-box user journeys over the public REST contract (JSON in, JSON out).
 */
@SpringBootTest(classes = ShopApplication.class, webEnvironment = WebEnvironment.RANDOM_PORT)
@RunWith(SpringRunner.class)
public class StorefrontAndAdminJourneyUatTest extends ServicesTestSupport {

	private static final ObjectMapper JSON = new ObjectMapper();

	@Test
	public void adminLoginThenAdminOnlyCall() throws Exception {
		ResponseEntity<String> login = post("/api/v1/private/login",
				body("username", "admin@shopizer.com", "password", "password"), null);
		assertEquals(HttpStatus.OK, login.getStatusCode());
		String token = json(login).get("token").asText();
		assertFalse(token.isEmpty());

		ResponseEntity<String> groups = call(HttpMethod.GET, "/api/v1/private/product/groups?store=DEFAULT", null,
				token);
		assertEquals(HttpStatus.OK, groups.getStatusCode());
		assertTrue(json(groups).isArray());

		assertEquals(HttpStatus.UNAUTHORIZED,
				call(HttpMethod.GET, "/api/v1/private/product/groups?store=DEFAULT", null, null).getStatusCode());
		// a malformed JWT is rejected (currently surfaces as 500 from AuthenticationTokenFilter on base and head)
		assertFalse(call(HttpMethod.GET, "/api/v1/private/product/groups?store=DEFAULT", null, "bad.token.value")
				.getStatusCode().is2xxSuccessful());

		ResponseEntity<String> badLogin = post("/api/v1/private/login",
				body("username", "admin@shopizer.com", "password", "wrong"), null);
		assertEquals(HttpStatus.UNAUTHORIZED, badLogin.getStatusCode());
	}

	@Test
	public void customerRegisterLoginAndProfile() throws Exception {
		String token = registerAndLoginCustomer("uatjourney1@test.com");

		ResponseEntity<String> profile = call(HttpMethod.GET, "/api/v1/auth/customer/profile?store=DEFAULT", null,
				token);
		assertEquals(HttpStatus.OK, profile.getStatusCode());
		assertEquals("uatjourney1@test.com", json(profile).get("emailAddress").asText());

		assertEquals(HttpStatus.UNAUTHORIZED,
				call(HttpMethod.GET, "/api/v1/auth/customer/profile?store=DEFAULT", null, null).getStatusCode());
	}

	@Test
	public void browseStoreCategoriesAndProducts() throws Exception {
		ReadableProduct product = sampleProduct("uatbrowse");

		ResponseEntity<String> store = call(HttpMethod.GET, "/api/v1/store/DEFAULT", null, null);
		assertEquals(HttpStatus.OK, store.getStatusCode());
		assertEquals("DEFAULT", json(store).get("code").asText());

		ResponseEntity<String> categories = call(HttpMethod.GET, "/api/v1/category?store=DEFAULT", null, null);
		assertEquals(HttpStatus.OK, categories.getStatusCode());
		assertTrue(json(categories).has("categories"));

		ResponseEntity<String> products = call(HttpMethod.GET, "/api/v1/products?store=DEFAULT", null, null);
		assertEquals(HttpStatus.OK, products.getStatusCode());
		JsonNode list = json(products);
		assertTrue(list.get("products").isArray());
		assertTrue(list.get("recordsTotal").asLong() >= 1);

		ResponseEntity<String> detail = call(HttpMethod.GET, "/api/v2/product/" + product.getSku() + "?store=DEFAULT",
				null, null);
		assertEquals(HttpStatus.OK, detail.getStatusCode());
		assertEquals(product.getId().longValue(), json(detail).get("id").asLong());
		assertEquals("uatbrowse", json(detail).get("sku").asText());
	}

	@Test
	public void cartCreateUpdateAndReadBack() throws Exception {
		ReadableProduct product = sampleProduct("uatcart");

		ResponseEntity<String> created = post("/api/v1/cart?store=DEFAULT", item("uatcart", 1), null);
		assertEquals(HttpStatus.CREATED, created.getStatusCode());
		String code = json(created).get("code").asText();
		assertFalse(code.isEmpty());

		ResponseEntity<String> updated = call(HttpMethod.PUT, "/api/v1/cart/" + code + "?store=DEFAULT",
				item("uatcart", 3), null);
		assertEquals(HttpStatus.CREATED, updated.getStatusCode());

		ResponseEntity<String> read = call(HttpMethod.GET, "/api/v1/cart/" + code + "?store=DEFAULT", null, null);
		assertEquals(HttpStatus.OK, read.getStatusCode());
		JsonNode cart = json(read);
		assertEquals(code, cart.get("code").asText());
		assertEquals(1, cart.get("products").size());
		assertEquals(product.getId().longValue(), cart.get("products").get(0).get("id").asLong());
		assertEquals(3, cart.get("products").get(0).get("quantity").asInt());
	}

	@Test
	public void productGroupJourneyAdminCuratesStorefrontReads() throws Exception {
		ReadableProduct product = sampleProduct("uatgroup");
		String admin = adminToken();

		assertEquals(HttpStatus.OK, call(HttpMethod.POST, "/api/v1/private/products/group?store=DEFAULT",
				body("code", "UAT_FEATURED", "active", true), admin).getStatusCode());
		assertEquals(HttpStatus.CREATED, call(HttpMethod.POST,
				"/api/v1/private/products/" + product.getId() + "/group/UAT_FEATURED?store=DEFAULT", null, admin)
						.getStatusCode());

		ResponseEntity<String> storefront = call(HttpMethod.GET, "/api/v1/products/group/UAT_FEATURED?store=DEFAULT",
				null, null);
		assertEquals(HttpStatus.OK, storefront.getStatusCode());
		assertEquals(product.getId().longValue(), json(storefront).get("products").get(0).get("id").asLong());

		// anonymous shopper cannot delete it on either path
		assertFalse(call(HttpMethod.DELETE, "/api/v1/products/group/UAT_FEATURED?store=DEFAULT", null, null)
				.getStatusCode().is2xxSuccessful());
		assertEquals(HttpStatus.UNAUTHORIZED,
				call(HttpMethod.DELETE, "/api/v1/private/products/group/UAT_FEATURED?store=DEFAULT", null, null)
						.getStatusCode());
		assertEquals(1, json(call(HttpMethod.GET, "/api/v1/products/group/UAT_FEATURED?store=DEFAULT", null, null))
				.get("products").size());

		// admin deletes it
		assertEquals(HttpStatus.OK,
				call(HttpMethod.DELETE, "/api/v1/private/products/group/UAT_FEATURED?store=DEFAULT", null, admin)
						.getStatusCode());
		ResponseEntity<String> after = call(HttpMethod.GET, "/api/v1/products/group/UAT_FEATURED?store=DEFAULT", null,
				null);
		assertEquals(HttpStatus.OK, after.getStatusCode());
		assertEquals(0, json(after).get("products").size());
	}

	@Test
	public void servicesBasicAuth() {
		HttpHeaders valid = new HttpHeaders();
		valid.setBasicAuth("admin@shopizer.com", "password");
		valid.setContentType(MediaType.APPLICATION_JSON);
		ResponseEntity<String> ok = testRestTemplate.exchange("/services/private/system/optin", HttpMethod.POST,
				new HttpEntity<>("{\"code\":\"UAT\",\"optinType\":\"PROMOTIONS\"}", valid), String.class);
		assertNotEquals(HttpStatus.UNAUTHORIZED, ok.getStatusCode());
		assertNotEquals(HttpStatus.FORBIDDEN, ok.getStatusCode());

		HttpHeaders invalid = new HttpHeaders();
		invalid.setBasicAuth("admin@shopizer.com", "wrong");
		invalid.setContentType(MediaType.APPLICATION_JSON);
		ResponseEntity<String> denied = testRestTemplate.exchange("/services/private/system/optin", HttpMethod.POST,
				new HttpEntity<>("{\"code\":\"UAT\",\"optinType\":\"PROMOTIONS\"}", invalid), String.class);
		assertEquals(HttpStatus.UNAUTHORIZED, denied.getStatusCode());
	}

	@Test
	public void actuatorHealthUpAndApiDocsServed() throws Exception {
		ResponseEntity<String> health = call(HttpMethod.GET, "/actuator/health", null, null);
		assertEquals(HttpStatus.OK, health.getStatusCode());
		assertEquals("UP", json(health).get("status").asText());

		ResponseEntity<String> docs = call(HttpMethod.GET, "/v2/api-docs", null, null);
		assertEquals(HttpStatus.OK, docs.getStatusCode());
		JsonNode paths = json(docs).get("paths");
		assertNotNull(paths);
		assertTrue(paths.has("/api/v1/private/products/group/{code}"));
		assertTrue(paths.get("/api/v1/private/products/group/{code}").has("delete"));
		assertTrue(paths.get("/api/v1/products/group/{code}").has("get"));
		assertFalse("public DELETE must not be documented",
				paths.get("/api/v1/products/group/{code}").has("delete"));
	}

	private String adminToken() throws Exception {
		return json(post("/api/v1/private/login", body("username", "admin@shopizer.com", "password", "password"), null))
				.get("token").asText();
	}

	private String registerAndLoginCustomer(String email) throws Exception {
		Map<String, Object> billing = body("firstName", "Uat", "lastName", "Shopper", "country", "CA");
		Map<String, Object> customer = body("emailAddress", email, "password", "clear123", "gender", "M",
				"language", "en", "storeCode", "DEFAULT", "billing", billing);
		assertEquals(HttpStatus.OK, post("/api/v1/customer/register", customer, null).getStatusCode());

		ResponseEntity<String> login = post("/api/v1/customer/login", body("username", email, "password", "clear123"),
				null);
		assertEquals(HttpStatus.OK, login.getStatusCode());
		String token = json(login).get("token").asText();
		assertFalse(token.isEmpty());
		return token;
	}

	private ResponseEntity<String> post(String url, Object payload, String token) {
		return call(HttpMethod.POST, url, payload, token);
	}

	private ResponseEntity<String> call(HttpMethod method, String url, Object payload, String token) {
		HttpHeaders headers = new HttpHeaders();
		headers.setContentType(MediaType.APPLICATION_JSON);
		if (token != null) {
			headers.add("Authorization", "Bearer " + token);
		}
		return testRestTemplate.exchange(url, method, new HttpEntity<>(payload, headers), String.class);
	}

	private static Map<String, Object> item(String sku, int quantity) {
		return body("product", sku, "quantity", quantity);
	}

	private static Map<String, Object> body(Object... kv) {
		Map<String, Object> map = new HashMap<>();
		for (int i = 0; i < kv.length; i += 2) {
			map.put((String) kv[i], kv[i + 1]);
		}
		return map;
	}

	private static JsonNode json(ResponseEntity<String> response) throws Exception {
		return JSON.readTree(response.getBody());
	}
}
