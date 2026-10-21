package com.salesmanager.test.shop.uat;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotEquals;
import static org.junit.Assert.assertTrue;

import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.UUID;

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
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.salesmanager.shop.application.ShopApplication;
import com.salesmanager.shop.model.catalog.product.ReadableProduct;
import com.salesmanager.test.shop.common.ServicesTestSupport;

/**
 * Black-box user journeys (storefront + admin) against the running app on a random port.
 */
@SpringBootTest(classes = ShopApplication.class, webEnvironment = WebEnvironment.RANDOM_PORT)
@RunWith(SpringRunner.class)
public class StorefrontAndAdminJourneysUatTest extends ServicesTestSupport {

	private static final ObjectMapper JSON = new ObjectMapper();

	@Test
	public void adminLoginThenPrivateCallAndRejectionWithoutOrBadToken() throws Exception {
		JsonNode login = json(call(HttpMethod.POST, "/api/v1/private/login",
				"{\"username\":\"admin@shopizer.com\",\"password\":\"password\"}", null), HttpStatus.OK);
		String token = login.get("token").asText();
		assertFalse(token.isEmpty());

		JsonNode users = json(call(HttpMethod.GET, "/api/v1/private/users/1?store=DEFAULT", null, bearer(token)),
				HttpStatus.OK);
		assertEquals("admin@shopizer.com", users.get("userName").asText());
		assertTrue(users.get("groups").isArray());

		assertEquals(HttpStatus.UNAUTHORIZED,
				call(HttpMethod.GET, "/api/v1/private/users/1?store=DEFAULT", null, plain()).getStatusCode());
		assertFalse(call(HttpMethod.GET, "/api/v1/private/users/1?store=DEFAULT", null, bearer("bad.token.value"))
				.getStatusCode().is2xxSuccessful());
		assertEquals(HttpStatus.UNAUTHORIZED, call(HttpMethod.POST, "/api/v1/private/login",
				"{\"username\":\"admin@shopizer.com\",\"password\":\"wrong\"}", null).getStatusCode());
	}

	@Test
	public void adminUserManagementJourney() throws Exception {
		HttpHeaders admin = getHeader();
		String username = "uat-" + UUID.randomUUID() + "@test.com";
		JsonNode created = json(call(HttpMethod.POST, "/api/v1/private/user/?store=DEFAULT",
				userJson(username, "Uat", "ADMIN_CATALOGUE", "Password1"), admin), HttpStatus.OK);
		long id = created.get("id").asLong();

		HttpHeaders catalogue = getHeader(username, "Password1");

		JsonNode self = json(call(HttpMethod.PUT, "/api/v1/private/user/" + id + "?store=DEFAULT",
				userJson(username, "Renamed", "ADMIN_CATALOGUE", null), catalogue), HttpStatus.OK);
		assertEquals("Renamed", self.get("firstName").asText());
		assertEquals(username, self.get("userName").asText());
		assertEquals("DEFAULT", self.get("merchant").asText());

		ResponseEntity<String> takeover = call(HttpMethod.PUT, "/api/v1/private/user/1?store=DEFAULT",
				userJson("admin@shopizer.com", "Pwned", "ADMIN", "Attacker1!"), catalogue);
		assertEquals(HttpStatus.UNAUTHORIZED, takeover.getStatusCode());

		JsonNode adminAfter = json(call(HttpMethod.GET, "/api/v1/private/users/1?store=DEFAULT", null, getHeader()),
				HttpStatus.OK);
		assertNotEquals("Pwned", adminAfter.get("firstName").asText());

		JsonNode byAdmin = json(call(HttpMethod.PUT, "/api/v1/private/user/" + id + "?store=DEFAULT",
				userJson(username, "ByAdmin", "ADMIN_CATALOGUE", null), admin), HttpStatus.OK);
		assertEquals("ByAdmin", byAdmin.get("firstName").asText());
	}

	@Test
	public void customerRegisterLoginAndAuthenticatedProfile() throws Exception {
		String email = "c" + UUID.randomUUID().toString().substring(0, 8) + "@test.com";
		String token = registerAndLogin(email);

		JsonNode profile = json(call(HttpMethod.GET, "/api/v1/auth/customer/profile", null, bearer(token)),
				HttpStatus.OK);
		assertEquals(email, profile.get("emailAddress").asText());

		assertEquals(HttpStatus.UNAUTHORIZED,
				call(HttpMethod.GET, "/api/v1/auth/customer/profile", null, plain()).getStatusCode());
	}

	@Test
	public void browseStoreCategoriesAndProducts() throws Exception {
		ReadableProduct product = sampleProduct("uatbrowse" + System.nanoTime() % 100000);

		JsonNode store = json(call(HttpMethod.GET, "/api/v1/store/DEFAULT", null, plain()), HttpStatus.OK);
		assertEquals("DEFAULT", store.get("code").asText());

		JsonNode categories = json(call(HttpMethod.GET, "/api/v1/category?store=DEFAULT", null, plain()),
				HttpStatus.OK);
		assertTrue(categories.has("categories"));

		JsonNode products = json(call(HttpMethod.GET, "/api/v1/products?store=DEFAULT", null, plain()),
				HttpStatus.OK);
		assertTrue(products.get("products").isArray());
		assertTrue(products.get("recordsTotal").asLong() >= 1);

		JsonNode detail = json(call(HttpMethod.GET, "/api/v2/product/" + product.getSku() + "?store=DEFAULT", null,
				plain()), HttpStatus.OK);
		assertEquals(product.getId().longValue(), detail.get("id").asLong());
		assertEquals(product.getSku(), detail.get("sku").asText());
	}

	@Test
	public void cartCreateAddUpdateAndRead() throws Exception {
		ReadableProduct p1 = sampleProduct("uatcart1" + System.nanoTime() % 100000);
		ReadableProduct p2 = sampleProduct("uatcart2" + System.nanoTime() % 100000);

		JsonNode cart = json(call(HttpMethod.POST, "/api/v1/cart/", item(p1.getSku(), 1), plain()),
				HttpStatus.CREATED);
		String code = cart.get("code").asText();
		assertEquals(1, cart.get("quantity").asInt());

		JsonNode added = json(call(HttpMethod.PUT, "/api/v1/cart/" + code, item(p2.getSku(), 1), plain()),
				HttpStatus.CREATED);
		assertEquals(2, added.get("quantity").asInt());

		JsonNode updated = json(call(HttpMethod.PUT, "/api/v1/cart/" + code, item(p2.getSku(), 3), plain()),
				HttpStatus.CREATED);
		assertEquals(4, updated.get("quantity").asInt());

		JsonNode read = json(call(HttpMethod.GET, "/api/v1/cart/" + code, null, plain()), HttpStatus.OK);
		assertEquals(code, read.get("code").asText());
		assertEquals(2, read.get("products").size());
	}

	@Test
	public void authenticatedCustomerCheckoutAndReadOrderBack() throws Exception {
		ReadableProduct product = sampleProduct("uatorder" + System.nanoTime() % 100000);
		ResponseEntity<String> configured = call(HttpMethod.POST, "/api/v1/private/modules/payment?store=DEFAULT",
				"{\"code\":\"moneyorder\",\"active\":true,\"defaultSelected\":true,"
						+ "\"integrationKeys\":{\"address\":\"1 Uat street\"}}",
				getHeader());
		assertTrue("payment config failed: " + configured, configured.getStatusCode().is2xxSuccessful());
		String token = registerAndLogin("b" + UUID.randomUUID().toString().substring(0, 8) + "@test.com");

		JsonNode cart = json(call(HttpMethod.POST, "/api/v1/cart/", item(product.getSku(), 1), plain()),
				HttpStatus.CREATED);
		String code = cart.get("code").asText();

		ObjectNode order = JSON.createObjectNode();
		order.put("currency", "CAD");
		order.put("customerAgreement", true);
		ObjectNode payment = order.putObject("payment");
		payment.put("paymentModule", "moneyorder");
		payment.put("paymentType", "MONEYORDER");
		payment.put("transactionType", "CAPTURE");
		payment.put("amount", cart.get("total").asText());

		JsonNode confirmation = json(call(HttpMethod.POST, "/api/v1/auth/cart/" + code + "/checkout",
				order.toString(), bearer(token)), HttpStatus.OK);
		long orderId = confirmation.get("id").asLong();
		assertTrue(orderId > 0);

		JsonNode readBack = json(call(HttpMethod.GET, "/api/v1/auth/orders/" + orderId, null, bearer(token)),
				HttpStatus.OK);
		assertEquals(orderId, readBack.path("id").asLong());
		assertTrue(readBack.path("products").size() >= 1);
	}

	@Test
	public void servicesBasicAuthRejectsMissingOrBadCredentials() throws Exception {
		HttpHeaders bad = plain();
		bad.add("Authorization", basic("admin@shopizer.com", "wrong"));
		assertFalse(call(HttpMethod.GET, "/services/private/ping", null, bad).getStatusCode().is2xxSuccessful());
		assertFalse(call(HttpMethod.GET, "/services/private/ping", null, plain()).getStatusCode().is2xxSuccessful());
	}

	@Test
	public void actuatorHealthUpAndApiDocsAvailable() throws Exception {
		JsonNode health = json(call(HttpMethod.GET, "/actuator/health", null, plain()), HttpStatus.OK);
		assertEquals("UP", health.get("status").asText());

		JsonNode docs = json(call(HttpMethod.GET, "/v2/api-docs", null, plain()), HttpStatus.OK);
		assertEquals("2.0", docs.get("swagger").asText());
		assertTrue(docs.path("paths").has("/api/v1/private/user/{id}"));
	}

	private String registerAndLogin(String email) throws Exception {
		ObjectNode customer = JSON.createObjectNode();
		customer.put("emailAddress", email);
		customer.put("password", "clear123");
		customer.put("gender", "M");
		customer.put("language", "en");
		customer.put("storeCode", "DEFAULT");
		ObjectNode billing = customer.putObject("billing");
		billing.put("firstName", "Uat");
		billing.put("lastName", "Customer");
		billing.put("country", "CA");
		billing.put("stateProvince", "QC");
		billing.put("address", "1 Uat street");
		billing.put("city", "Montreal");
		billing.put("postalCode", "H2H2H2");
		customer.set("delivery", billing.deepCopy());
		json(call(HttpMethod.POST, "/api/v1/customer/register", customer.toString(), plain()), HttpStatus.OK);

		JsonNode login = json(call(HttpMethod.POST, "/api/v1/customer/login",
				"{\"username\":\"" + email + "\",\"password\":\"clear123\"}", null), HttpStatus.OK);
		return login.get("token").asText();
	}

	private String userJson(String username, String firstName, String group, String password) {
		ObjectNode u = JSON.createObjectNode();
		u.put("userName", username);
		u.put("emailAddress", username);
		u.put("firstName", firstName);
		u.put("lastName", "User");
		u.put("defaultLanguage", "en");
		u.put("store", "DEFAULT");
		u.put("active", true);
		if (password != null) {
			u.put("password", password);
			u.put("repeatPassword", password);
		}
		u.putArray("groups").addObject().put("name", group);
		return u.toString();
	}

	private String item(String sku, int quantity) {
		return "{\"product\":\"" + sku + "\",\"quantity\":" + quantity + "}";
	}

	private ResponseEntity<String> call(HttpMethod method, String url, String body, HttpHeaders headers) {
		HttpHeaders h = headers == null ? plain() : headers;
		return testRestTemplate.exchange(url, method, new HttpEntity<>(body, h), String.class);
	}

	private JsonNode json(ResponseEntity<String> response, HttpStatus expected) throws Exception {
		assertEquals("unexpected status, body=" + response.getBody(), expected, response.getStatusCode());
		return JSON.readTree(response.getBody());
	}

	private HttpHeaders plain() {
		HttpHeaders h = new HttpHeaders();
		h.setContentType(MediaType.APPLICATION_JSON);
		return h;
	}

	private HttpHeaders bearer(String token) {
		HttpHeaders h = plain();
		h.add("Authorization", "Bearer " + token);
		return h;
	}

	private String basic(String user, String password) {
		return "Basic " + Base64.getEncoder().encodeToString((user + ":" + password).getBytes(StandardCharsets.UTF_8));
	}
}
