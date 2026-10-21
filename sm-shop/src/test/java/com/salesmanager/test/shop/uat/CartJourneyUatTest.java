package com.salesmanager.test.shop.uat;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.test.context.junit4.SpringRunner;

import com.salesmanager.shop.application.ShopApplication;
import com.salesmanager.shop.model.catalog.product.ReadableProduct;
import com.salesmanager.shop.model.shoppingcart.PersistableShoppingCartItem;
import com.salesmanager.shop.model.shoppingcart.ReadableShoppingCart;
import com.salesmanager.test.shop.common.ServicesTestSupport;

@RunWith(SpringRunner.class)
@SpringBootTest(classes = ShopApplication.class, webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
public class CartJourneyUatTest extends ServicesTestSupport {

	@Test
	public void visitorCanCreateUpdateAndReadCart() {
		String productCode = "ab534cart";
		ReadableProduct product = sampleProduct(productCode);
		PersistableShoppingCartItem item = new PersistableShoppingCartItem();
		item.setProduct(product.getSku());
		item.setQuantity(1);

		ResponseEntity<ReadableShoppingCart> created = testRestTemplate.postForEntity(
				"/api/v1/cart?store=DEFAULT", new HttpEntity<>(item, jsonHeaders()), ReadableShoppingCart.class);
		assertEquals(HttpStatus.CREATED, created.getStatusCode());
		assertNotNull(created.getBody());
		assertEquals(1, created.getBody().getQuantity());
		assertNotNull(created.getBody().getCode());
		assertFalse(created.getBody().getCode().isEmpty());
		String cartCode = created.getBody().getCode();

		item.setQuantity(2);
		ResponseEntity<ReadableShoppingCart> updated = testRestTemplate.exchange(
				"/api/v1/cart/" + cartCode + "?store=DEFAULT", HttpMethod.PUT,
				new HttpEntity<>(item, jsonHeaders()), ReadableShoppingCart.class);
		assertEquals(HttpStatus.CREATED, updated.getStatusCode());
		assertNotNull(updated.getBody());
		assertEquals(2, updated.getBody().getQuantity());

		ResponseEntity<ReadableShoppingCart> cart = testRestTemplate.getForEntity(
				"/api/v1/cart/" + cartCode + "?store=DEFAULT", ReadableShoppingCart.class);
		assertEquals(HttpStatus.OK, cart.getStatusCode());
		assertNotNull(cart.getBody());
		assertEquals(2, cart.getBody().getQuantity());

		ResponseEntity<String> missingCart = testRestTemplate.getForEntity(
				"/api/v1/cart/ab534-missing-cart?store=DEFAULT", String.class);
		assertEquals(HttpStatus.NOT_FOUND, missingCart.getStatusCode());
	}

	private static HttpHeaders jsonHeaders() {
		HttpHeaders headers = new HttpHeaders();
		headers.setContentType(MediaType.APPLICATION_JSON);
		return headers;
	}
}
