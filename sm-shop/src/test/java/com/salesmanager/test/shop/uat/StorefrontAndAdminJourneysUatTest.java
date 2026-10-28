package com.salesmanager.test.shop.uat;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.UUID;

import javax.inject.Inject;

import org.junit.jupiter.api.Test;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.salesmanager.test.shop.common.ServicesTestSupport;

public class StorefrontAndAdminJourneysUatTest extends ServicesTestSupport {

	@Inject
	private ObjectMapper mapper;

	private HttpHeaders jsonHeaders() {
		HttpHeaders headers = new HttpHeaders();
		headers.setContentType(MediaType.APPLICATION_JSON);
		return headers;
	}

	private JsonNode exchange(String path, HttpMethod method, HttpHeaders headers, Object body, int status)
			throws Exception {
		ResponseEntity<String> response = testRestTemplate.exchange(path, method, new HttpEntity<>(body, headers),
				String.class);
		assertEquals(status, response.getStatusCodeValue(), method + " " + path + ": " + response.getBody());
		return response.getBody() == null ? mapper.nullNode() : mapper.readTree(response.getBody());
	}

	@Test
	void adminLoginAndPrivateUserListing() throws Exception {
		JsonNode login = exchange("/api/v1/private/login", HttpMethod.POST, jsonHeaders(),
				mapper.createObjectNode().put("username", "admin@shopizer.com").put("password", "password"), 200);
		assertFalse(login.path("token").asText().isEmpty());
		HttpHeaders admin = jsonHeaders();
		admin.setBearerAuth(login.path("token").asText());
		exchange("/api/v1/private/users?store=DEFAULT", HttpMethod.GET, admin, null, 200);
		exchange("/api/v1/private/users?store=DEFAULT", HttpMethod.GET, jsonHeaders(), null, 401);
	}

	@Test
	void customerBrowsesAddsToCartAndChecksOutWithMoneyOrder() throws Exception {
		String email = UUID.randomUUID().toString().replace("-", "") + "@example.com";
		ObjectNode customer = mapper.createObjectNode().put("emailAddress", email).put("password", "Clear1234")
				.put("language", "en").put("storeCode", "DEFAULT");
		customer.putObject("billing").put("firstName", "Uat").put("lastName", "Customer")
				.put("country", "CA").put("zone", "QC").put("address", "1 Main St").put("city", "Montreal")
				.put("postalCode", "H2X1Y4").put("phone", "5145550100");
		exchange("/api/v1/customer/register", HttpMethod.POST, jsonHeaders(), customer, 200);
		JsonNode login = exchange("/api/v1/customer/login", HttpMethod.POST, jsonHeaders(),
				mapper.createObjectNode().put("username", email).put("password", "Clear1234"), 200);
		assertFalse(login.path("token").asText().isEmpty());
		HttpHeaders customerHeaders = jsonHeaders();
		customerHeaders.setBearerAuth(login.path("token").asText());
		JsonNode profile = exchange("/api/v1/auth/customer/profile", HttpMethod.GET, customerHeaders, null, 200);
		assertEquals(email, profile.path("emailAddress").asText());
		exchange("/api/v1/private/modules/shipping", HttpMethod.GET, customerHeaders, null, 401);

		exchange("/api/v1/store/DEFAULT", HttpMethod.GET, jsonHeaders(), null, 200);
		exchange("/api/v1/category?store=DEFAULT", HttpMethod.GET, jsonHeaders(), null, 200);
		JsonNode products = exchange("/api/v1/products?store=DEFAULT", HttpMethod.GET, jsonHeaders(), null, 200);
		String sku;
		if (products.path("products").isEmpty()) {
			sku = sampleProduct("uat-" + UUID.randomUUID()).getSku();
		} else {
			sku = products.path("products").get(0).path("sku").asText();
		}
		assertFalse(sku.isEmpty());
		JsonNode detail = exchange("/api/v2/product/" + sku, HttpMethod.GET, jsonHeaders(), null, 200);
		assertEquals(sku, detail.path("sku").asText());

		ObjectNode item = mapper.createObjectNode().put("product", sku).put("quantity", 1);
		JsonNode cart = exchange("/api/v1/cart", HttpMethod.POST, customerHeaders, item, 201);
		String code = cart.path("code").asText();
		assertFalse(code.isEmpty());
		assertEquals(1, cart.path("quantity").asInt());
		item.put("quantity", 2);
		exchange("/api/v1/cart/" + code, HttpMethod.PUT, customerHeaders, item, 201);
		cart = exchange("/api/v1/cart/" + code, HttpMethod.GET, customerHeaders, null, 200);
		assertEquals(2, cart.path("quantity").asInt());
		assertTrue(cart.path("total").isNumber());

		ObjectNode moneyorder = mapper.createObjectNode().put("code", "moneyorder").put("active", true)
				.put("defaultSelected", true);
		moneyorder.putObject("integrationKeys").put("address", "1 Main St");
		exchange("/api/v1/private/modules/payment", HttpMethod.POST, getHeader(), moneyorder, 200);
		JsonNode configuration = exchange("/api/v1/private/modules/payment/moneyorder", HttpMethod.GET,
				getHeader(), null, 200);
		assertEquals("1 Main St", configuration.path("integrationKeys").path("address").asText());
		ObjectNode checkout = mapper.createObjectNode().put("currency", "CAD");
		checkout.putObject("payment").put("paymentType", "MONEYORDER").put("transactionType", "CAPTURE")
				.put("paymentModule", "moneyorder").put("amount", cart.path("total").asText());
		JsonNode confirmation = exchange("/api/v1/auth/cart/" + code + "/checkout", HttpMethod.POST,
				customerHeaders, checkout, 200);
		assertTrue(confirmation.path("id").asLong() > 0);
		JsonNode order = exchange("/api/v1/auth/orders/" + confirmation.path("id").asLong(), HttpMethod.GET,
				customerHeaders, null, 200);
		assertEquals("moneyorder", order.path("paymentModule").asText());
	}

	@Test
	void legacyBasicAuthenticationRejectsWrongPassword() {
		HttpHeaders headers = new HttpHeaders();
		headers.setBasicAuth("admin@shopizer.com", "password");
		ResponseEntity<String> accepted = testRestTemplate.exchange("/services/private/system/optin", HttpMethod.GET,
				new HttpEntity<>(headers), String.class);
		assertNotEquals(401, accepted.getStatusCodeValue());
		assertEquals(405, accepted.getStatusCodeValue());
		headers.setBasicAuth("admin@shopizer.com", "wrong-password");
		ResponseEntity<String> rejected = testRestTemplate.exchange("/services/private/system/optin", HttpMethod.GET,
				new HttpEntity<>(headers), String.class);
		assertEquals(401, rejected.getStatusCodeValue());
	}

	@Test
	void healthAndApiDocumentationArePublic() throws Exception {
		assertEquals("UP", exchange("/actuator/health", HttpMethod.GET, jsonHeaders(), null, 200).path("status").asText());
		JsonNode docs = exchange("/v2/api-docs", HttpMethod.GET, jsonHeaders(), null, 200);
		assertTrue(docs.path("paths").has("/api/v1/private/modules/shipping"));
	}
}
