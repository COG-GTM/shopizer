package com.salesmanager.test.shop.uat;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.ResponseEntity;

import com.fasterxml.jackson.databind.JsonNode;
import com.salesmanager.shop.model.catalog.product.ReadableProduct;
import com.salesmanager.test.shop.common.InventoryJourneySupport;

class StorefrontJourneyTest extends InventoryJourneySupport {

	@Test
	void adminLoginAuthorizesPrivateApiAndRejectsMissingToken() throws Exception {
		json(request("/api/v1/private/users/1", HttpMethod.GET, null, getHeader()), 200);
		ResponseEntity<String> missing = request("/api/v1/private/users/1", HttpMethod.GET, null, new HttpHeaders());
		System.out.println("ADMIN_MISSING_TOKEN_HTTP " + missing.getStatusCodeValue() + " " + missing.getBody());
		assertTrue(missing.getStatusCodeValue() == 401 || missing.getStatusCodeValue() == 403);
	}

	@Test
	void registeredCustomerBrowsesUpdatesCartChecksOutAndReadsOrder() throws Exception {
		HttpHeaders customer = customerHeaders();
		ReadableProduct product = stockedProduct("journey", 10);
		json(request("/api/v1/store/DEFAULT", HttpMethod.GET, null, new HttpHeaders()), 200);
		json(request("/api/v1/category", HttpMethod.GET, null, new HttpHeaders()), 200);
		json(request("/api/v1/products", HttpMethod.GET, null, new HttpHeaders()), 200);
		json(request("/api/v2/product/" + product.getSku(), HttpMethod.GET, null, new HttpHeaders()), 200);
		JsonNode cart = cart(product.getSku(), 1, customer);
		String code = cart.get("code").asText();
		JsonNode updated = json(request("/api/v1/cart/" + code, HttpMethod.PUT,
				mapper.createObjectNode().put("product", product.getSku()).put("quantity", 2), customer), 201);
		assertEquals(2, updated.get("quantity").asInt());
		JsonNode readCart = json(request("/api/v1/cart/" + code, HttpMethod.GET, null, customer), 200);
		assertEquals(2, readCart.get("quantity").asInt());
		JsonNode confirmation = json(checkout(code, customer), 200);
		assertEquals(8, stock(product.getId()));
		JsonNode order = json(request("/api/v1/auth/orders/" + confirmation.get("id").asLong(), HttpMethod.GET, null, customer), 200);
		assertEquals(confirmation.get("id").asLong(), order.get("id").asLong());
	}

	@Test
	void legacyServicesRequireValidBasicAuthentication() throws Exception {
		HttpHeaders valid = new HttpHeaders();
		valid.setContentType(org.springframework.http.MediaType.APPLICATION_JSON);
		valid.setBasicAuth("admin@shopizer.com", "password");
		json(request("/services/private/system/optin", HttpMethod.POST, "{}", valid), 200);
		HttpHeaders invalid = new HttpHeaders();
		invalid.setContentType(org.springframework.http.MediaType.APPLICATION_JSON);
		invalid.setBasicAuth("admin@shopizer.com", "wrong-password");
		ResponseEntity<String> rejected = request("/services/private/system/optin", HttpMethod.POST, "{}", invalid);
		System.out.println("SERVICES_BAD_BASIC_HTTP " + rejected.getStatusCodeValue() + " " + rejected.getBody());
		assertTrue(rejected.getStatusCodeValue() == 401 || rejected.getStatusCodeValue() == 403);
	}

	@Test
	void healthAndSwaggerArePubliclyAvailable() throws Exception {
		JsonNode health = json(request("/actuator/health", HttpMethod.GET, null, new HttpHeaders()), 200);
		assertEquals("UP", health.get("status").asText());
		JsonNode docs = json(request("/v2/api-docs", HttpMethod.GET, null, new HttpHeaders()), 200);
		assertEquals("2.0", docs.get("swagger").asText());
	}
}
