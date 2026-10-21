package com.salesmanager.test.shop.smoke;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

import org.junit.jupiter.api.Test;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpMethod;
import org.springframework.http.ResponseEntity;

import com.salesmanager.shop.model.shoppingcart.PersistableShoppingCartItem;
import com.salesmanager.shop.model.shoppingcart.ReadableShoppingCart;

class CartSmokeTest extends SmokeTestSupport {

	@Test
	void createsUpdatesReadsAndRemovesCartItems() {
		ProductFixture first = createProduct("cartfirst");
		ProductFixture second = createProduct("cartsecond");
		PersistableShoppingCartItem firstItem = item(first.product.getSku(), 1);
		ResponseEntity<ReadableShoppingCart> created = testRestTemplate.postForEntity("/api/v1/cart",
				new HttpEntity<>(firstItem, jsonHeaders()), ReadableShoppingCart.class);
		assertEquals(201, created.getStatusCodeValue());
		String code = created.getBody().getCode();

		ResponseEntity<ReadableShoppingCart> quantityUpdated = testRestTemplate.exchange("/api/v1/cart/" + code,
				HttpMethod.PUT, new HttpEntity<>(item(first.product.getSku(), 3), jsonHeaders()),
				ReadableShoppingCart.class);
		assertEquals(201, quantityUpdated.getStatusCodeValue());
		assertEquals(3, quantityUpdated.getBody().getQuantity());

		ResponseEntity<ReadableShoppingCart> secondAdded = testRestTemplate.exchange("/api/v1/cart/" + code,
				HttpMethod.PUT, new HttpEntity<>(item(second.product.getSku(), 1), jsonHeaders()),
				ReadableShoppingCart.class);
		assertEquals(201, secondAdded.getStatusCodeValue());
		assertEquals(4, secondAdded.getBody().getQuantity());

		ResponseEntity<ReadableShoppingCart> retrieved = testRestTemplate.getForEntity("/api/v1/cart/" + code,
				ReadableShoppingCart.class);
		assertEquals(200, retrieved.getStatusCodeValue());
		assertEquals(2, retrieved.getBody().getProducts().size());

		ResponseEntity<ReadableShoppingCart> removed = testRestTemplate.exchange(
				"/api/v1/cart/" + code + "/product/" + second.product.getSku() + "?body=true", HttpMethod.DELETE,
				HttpEntity.EMPTY, ReadableShoppingCart.class);
		assertEquals(200, removed.getStatusCodeValue());
		ResponseEntity<ReadableShoppingCart> afterDelete = testRestTemplate.getForEntity("/api/v1/cart/" + code,
				ReadableShoppingCart.class);
		assertEquals(200, afterDelete.getStatusCodeValue());
		assertFalse(afterDelete.getBody().getProducts().stream()
				.anyMatch(cartItem -> second.product.getSku().equals(cartItem.getSku())));
	}

	private PersistableShoppingCartItem item(String sku, int quantity) {
		PersistableShoppingCartItem item = new PersistableShoppingCartItem();
		item.setProduct(sku);
		item.setQuantity(quantity);
		return item;
	}
}
