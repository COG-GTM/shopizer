package com.salesmanager.test.shop.uat;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import java.util.Map;
import java.util.UUID;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.test.context.junit4.SpringRunner;

import com.fasterxml.jackson.databind.JsonNode;
import com.salesmanager.test.shop.common.SkuStoreTestSupport;

@RunWith(SpringRunner.class)
public class StorefrontContractJourneyTest extends SkuStoreTestSupport {

	@Test
	public void healthAndSpringfoxDocumentsRemainAvailable() {
		JsonNode health = get("/actuator/health", new HttpHeaders());
		assertEquals("UP", health.path("status").asText());
		JsonNode docs = get("/v2/api-docs", new HttpHeaders());
		assertEquals("2.0", docs.path("swagger").asText());
		assertTrue(docs.path("paths").has("/api/v1/cart"));
	}

	@Test
	public void shopperBrowsesTheStoreCategoriesProductsAndLocalProductDetail() {
		assertEquals("DEFAULT", get("/api/v1/store/DEFAULT", new HttpHeaders()).path("code").asText());
		assertTrue(get("/api/v1/category/", new HttpHeaders()).path("categories").isArray());
		assertTrue(get("/api/v1/products", new HttpHeaders()).path("products").isArray());
		JsonNode detail = get("/api/v2/product/" + sharedSku, new HttpHeaders());
		assertEquals(defaultProduct.getId().longValue(), detail.path("id").asLong());
		assertEquals(sharedSku, detail.path("sku").asText());
	}

	@Test
	public void legacyServicesRequireValidBasicAuthentication() {
		HttpHeaders valid = new HttpHeaders();
		valid.setBasicAuth("admin@shopizer.com", "password");
		ResponseEntity<JsonNode> accepted = testRestTemplate.postForEntity("/services/private/system/optin",
				new HttpEntity<>(Map.of(), valid), JsonNode.class);
		assertEquals(HttpStatus.OK, accepted.getStatusCode());
		assertEquals(200, accepted.getBody().path("status").asInt());
		HttpHeaders invalid = new HttpHeaders();
		invalid.setBasicAuth("admin@shopizer.com", "incorrect");
		assertEquals(HttpStatus.UNAUTHORIZED, testRestTemplate.postForEntity("/services/private/system/optin",
				new HttpEntity<>(Map.of(), invalid), String.class).getStatusCode());
	}

	@Test
	public void customerRegistersAuthenticatesChecksOutAndReadsTheirOrder() {
		String email = "upgrade_" + UUID.randomUUID().toString().replace("-", "").substring(0, 12) + "@example.test";
		Map<String, String> address = Map.of("firstName", "Upgrade", "lastName", "Test", "country", "CA",
				"zone", "QC", "city", "Montreal", "postalCode", "H2X1Y4", "address", "123 Test Street",
				"phone", "5145550100");
		post("/api/v1/customer/register", Map.of("emailAddress", email, "password", "Upgrade1!", "gender", "M",
				"language", "en", "billing", address, "delivery", address, "storeCode", "DEFAULT"), new HttpHeaders(), HttpStatus.OK);
		JsonNode login = post("/api/v1/customer/login", Map.of("username", email, "password", "Upgrade1!"),
				new HttpHeaders(), HttpStatus.OK);
		assertTrue(login.path("token").isTextual());
		assertTrue(login.path("token").asText().length() > 0);
		HttpHeaders customer = new HttpHeaders();
		customer.setBearerAuth(login.path("token").asText());
		assertEquals(email, get("/api/v1/auth/customer/profile", customer).path("emailAddress").asText());

		post("/api/v1/private/modules/payment", Map.of("code", "moneyorder", "active", true,
				"defaultSelected", true, "integrationKeys", Map.of("address", "Test fixture only",
						"transaction", "AUTHORIZECAPTURE")), getHeader(), HttpStatus.OK);
		JsonNode cart = post("/api/v1/cart", Map.of("product", sharedSku, "quantity", 1),
				new HttpHeaders(), HttpStatus.CREATED);
		assertEquals(defaultProduct.getId().longValue(), cart.path("products").get(0).path("id").asLong());
		JsonNode order = post("/api/v1/auth/cart/" + cart.path("code").asText() + "/checkout",
				Map.of("currency", "CAD", "customerAgreement", true, "payment", Map.of("paymentModule", "moneyorder",
						"paymentType", "MONEYORDER", "transactionType", "AUTHORIZECAPTURE",
						"amount", cart.path("total").asText())), customer, HttpStatus.OK);
		assertTrue(order.path("id").isIntegralNumber());
		assertEquals(order.path("id").asLong(),
				get("/api/v1/auth/orders/" + order.path("id").asText(), customer).path("id").asLong());
	}

	private JsonNode get(String path, HttpHeaders headers) {
		ResponseEntity<JsonNode> response = testRestTemplate.exchange(path, HttpMethod.GET,
				new HttpEntity<>(headers), JsonNode.class);
		assertEquals(HttpStatus.OK, response.getStatusCode());
		return response.getBody();
	}

	private JsonNode post(String path, Map<String, ?> payload, HttpHeaders headers, HttpStatus expected) {
		ResponseEntity<JsonNode> response = testRestTemplate.postForEntity(path, new HttpEntity<>(payload, headers), JsonNode.class);
		assertEquals(String.valueOf(response.getBody()), expected, response.getStatusCode());
		return response.getBody();
	}
}
