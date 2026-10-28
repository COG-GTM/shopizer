package com.salesmanager.test.shop.uat;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.test.context.junit4.SpringRunner;

import com.fasterxml.jackson.databind.JsonNode;
import com.salesmanager.shop.model.shoppingcart.PersistableShoppingCartItem;
import com.salesmanager.shop.model.shoppingcart.ReadableShoppingCart;
import com.salesmanager.test.shop.common.SkuStoreTestSupport;

@RunWith(SpringRunner.class)
public class ProductSkuStoreJourneyTest extends SkuStoreTestSupport {

	@Test
	public void adminChecksSkuAvailabilityWithoutLeakingAnotherStore() {
		HttpHeaders headers = getHeader();
		String path = "/api/v1/private/product/unique?store=DEFAULT&code=";
		ResponseEntity<JsonNode> shared = testRestTemplate.exchange(path + sharedSku, HttpMethod.GET,
				new HttpEntity<>(headers), JsonNode.class);
		assertEquals(HttpStatus.OK, shared.getStatusCode());
		assertTrue(shared.getBody().path("exists").isBoolean());
		assertTrue(shared.getBody().path("exists").booleanValue());
		ResponseEntity<JsonNode> otherOnly = testRestTemplate.exchange(path + otherOnlySku, HttpMethod.GET,
				new HttpEntity<>(headers), JsonNode.class);
		assertEquals(HttpStatus.OK, otherOnly.getStatusCode());
		assertTrue(otherOnly.getBody().path("exists").isBoolean());
		assertFalse(otherOnly.getBody().path("exists").booleanValue());
		assertDenied(testRestTemplate.getForEntity(path + sharedSku, String.class));
	}

	@Test
	public void shopperAddsAndUpdatesTheLocalProductWhenAnotherStoreSharesItsSku() {
		PersistableShoppingCartItem item = new PersistableShoppingCartItem();
		item.setProduct(sharedSku);
		item.setQuantity(1);
		ResponseEntity<ReadableShoppingCart> created = testRestTemplate.postForEntity("/api/v1/cart?store=DEFAULT",
				item, ReadableShoppingCart.class);
		assertEquals(HttpStatus.CREATED, created.getStatusCode());
		ReadableShoppingCart cart = created.getBody();
		assertNotNull(cart.getCode());
		assertEquals(1, cart.getQuantity());
		assertEquals(1, cart.getProducts().size());
		assertEquals(defaultProduct.getId(), cart.getProducts().get(0).getId());
		assertEquals(sharedSku, cart.getProducts().get(0).getSku());
		item.setQuantity(2);
		String path = "/api/v1/cart/" + cart.getCode() + "?store=DEFAULT";
		ResponseEntity<ReadableShoppingCart> updated = testRestTemplate.exchange(path, HttpMethod.PUT,
				new HttpEntity<>(item), ReadableShoppingCart.class);
		assertEquals(HttpStatus.CREATED, updated.getStatusCode());
		ResponseEntity<ReadableShoppingCart> fetched = testRestTemplate.getForEntity(path, ReadableShoppingCart.class);
		assertEquals(HttpStatus.OK, fetched.getStatusCode());
		assertEquals(2, fetched.getBody().getQuantity());
		assertEquals(defaultProduct.getId(), fetched.getBody().getProducts().get(0).getId());
	}

	@Test
	public void shopperCannotAddAnOtherStoreOnlySku() {
		PersistableShoppingCartItem item = new PersistableShoppingCartItem();
		item.setProduct(otherOnlySku);
		item.setQuantity(1);
		assertEquals(HttpStatus.BAD_REQUEST, testRestTemplate.postForEntity("/api/v1/cart?store=DEFAULT",
				item, String.class).getStatusCode());
	}

	private void assertDenied(ResponseEntity<?> response) {
		assertTrue("Expected 401/403 but got " + response.getStatusCode(),
				response.getStatusCode() == HttpStatus.UNAUTHORIZED || response.getStatusCode() == HttpStatus.FORBIDDEN);
	}
}
