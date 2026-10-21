package com.salesmanager.test.shop.uat;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.math.BigDecimal;
import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;

import org.junit.jupiter.api.Test;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpMethod;
import org.springframework.http.ResponseEntity;

import com.salesmanager.shop.model.shoppingcart.ReadableShoppingCart;
import com.salesmanager.shop.model.shoppingcart.ReadableShoppingCartItem;

class CartJourneyUatTest extends UatTestSupport {

	@Test
	void cartReadBackIncludesBothProductsQuantitiesAndTotals() {
		UatProduct first = createUatProduct("cartfirst");
		UatProduct second = createUatProduct("cartsecond");
		ResponseEntity<ReadableShoppingCart> created = testRestTemplate.postForEntity("/api/v1/cart",
				new HttpEntity<>(cartItem(first.sku, 1), jsonHeaders()), ReadableShoppingCart.class);
		assertEquals(201, created.getStatusCodeValue(), created.toString());
		assertNotNull(created.getBody());
		String code = created.getBody().getCode();

		ResponseEntity<ReadableShoppingCart> added = testRestTemplate.exchange("/api/v1/cart/" + code,
				HttpMethod.PUT, new HttpEntity<>(cartItem(second.sku, 2), jsonHeaders()),
				ReadableShoppingCart.class);
		assertEquals(201, added.getStatusCodeValue(), added.toString());
		ResponseEntity<ReadableShoppingCart> updated = testRestTemplate.exchange("/api/v1/cart/" + code,
				HttpMethod.PUT, new HttpEntity<>(cartItem(first.sku, 3), jsonHeaders()),
				ReadableShoppingCart.class);
		assertEquals(201, updated.getStatusCodeValue(), updated.toString());

		ResponseEntity<ReadableShoppingCart> readBack = testRestTemplate.getForEntity(
				"/api/v1/cart/" + code, ReadableShoppingCart.class);
		assertEquals(200, readBack.getStatusCodeValue(), readBack.toString());
		assertNotNull(readBack.getBody());
		assertEquals(code, readBack.getBody().getCode());
		assertEquals(5, readBack.getBody().getQuantity());
		assertEquals(2, readBack.getBody().getProducts().size());
		assertTrue(readBack.getBody().getProducts().stream()
				.anyMatch(item -> first.sku.equals(item.getSku()) && item.getQuantity() == 3));
		assertTrue(readBack.getBody().getProducts().stream()
				.anyMatch(item -> second.sku.equals(item.getSku()) && item.getQuantity() == 2));
		assertNotNull(readBack.getBody().getSubtotal());
		assertNotNull(readBack.getBody().getTotal());
		assertTrue(readBack.getBody().getSubtotal().compareTo(BigDecimal.ZERO) > 0);
		assertTrue(readBack.getBody().getTotal().compareTo(BigDecimal.ZERO) > 0);
		List<String> skus = readBack.getBody().getProducts().stream()
				.map(ReadableShoppingCartItem::getSku).collect(Collectors.toList());
		assertTrue(skus.containsAll(Arrays.asList(first.sku, second.sku)));
	}
}
