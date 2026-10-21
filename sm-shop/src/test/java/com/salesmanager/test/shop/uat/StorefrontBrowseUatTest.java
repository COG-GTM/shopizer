package com.salesmanager.test.shop.uat;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpMethod;
import org.springframework.http.ResponseEntity;

import com.fasterxml.jackson.databind.JsonNode;

class StorefrontBrowseUatTest extends UatTestSupport {

	@Test
	void storefrontCanBrowseStoreCategoriesAndCreatedProduct() {
		ResponseEntity<JsonNode> store = testRestTemplate.getForEntity("/api/v1/store/DEFAULT", JsonNode.class);
		assertEquals(200, store.getStatusCodeValue());
		assertNotNull(store.getBody());
		assertEquals("DEFAULT", store.getBody().path("code").asText());
		assertTrue(store.getBody().path("currency").isTextual());

		ResponseEntity<JsonNode> categories = testRestTemplate.getForEntity("/api/v1/category?store=DEFAULT",
				JsonNode.class);
		assertEquals(200, categories.getStatusCodeValue());
		assertNotNull(categories.getBody());
		assertTrue(categories.getBody().path("categories").isArray());

		UatProduct product = createUatProduct("browse");
		ResponseEntity<JsonNode> listing = testRestTemplate.getForEntity(
				"/api/v1/products?store=DEFAULT&sku=" + product.sku, JsonNode.class);
		assertEquals(200, listing.getStatusCodeValue());
		assertNotNull(listing.getBody());
		assertTrue(listing.getBody().path("products").findValuesAsText("sku").contains(product.sku));

		ResponseEntity<JsonNode> byId = testRestTemplate.exchange(
				"/api/v2/private/product/" + product.id + "?store=DEFAULT", HttpMethod.GET,
				new HttpEntity<>(bearerHeaders(product.adminToken)), JsonNode.class);
		assertEquals(200, byId.getStatusCodeValue());
		assertNotNull(byId.getBody());
		assertEquals(product.sku, byId.getBody().path("sku").asText());

		ResponseEntity<JsonNode> bySku = testRestTemplate.getForEntity(
				"/api/v2/product/" + product.sku + "?store=DEFAULT", JsonNode.class);
		assertEquals(200, bySku.getStatusCodeValue());
		assertNotNull(bySku.getBody());
		assertEquals(product.sku, bySku.getBody().path("sku").asText());
	}
}
