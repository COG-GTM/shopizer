package com.salesmanager.test.shop.uat;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;
import static org.springframework.http.HttpMethod.PUT;
import static org.springframework.http.HttpStatus.CREATED;
import static org.springframework.http.HttpStatus.OK;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.SpringBootTest.WebEnvironment;
import org.springframework.http.HttpEntity;
import org.springframework.http.ResponseEntity;
import org.springframework.test.context.junit4.SpringRunner;

import com.salesmanager.shop.application.ShopApplication;
import com.salesmanager.shop.model.catalog.product.ReadableProduct;
import com.salesmanager.shop.model.shoppingcart.PersistableShoppingCartItem;
import com.salesmanager.shop.model.shoppingcart.ReadableShoppingCart;
import com.salesmanager.test.shop.common.ServicesTestSupport;

@RunWith(SpringRunner.class)
@SpringBootTest(classes = ShopApplication.class, webEnvironment = WebEnvironment.RANDOM_PORT)
public class CartJourneyUatTest extends ServicesTestSupport {

	@Test
	public void shopperCanCreateAndUpdateCart() throws Exception {
		String sku = "uat-cart-" + System.nanoTime();
		ReadableProduct product = sampleProduct(sku);
		PersistableShoppingCartItem item = new PersistableShoppingCartItem();
		item.setProduct(product.getSku());
		item.setQuantity(1);

		ResponseEntity<ReadableShoppingCart> added = testRestTemplate.postForEntity(
				"/api/v1/cart?store=DEFAULT", new HttpEntity<>(item), ReadableShoppingCart.class);
		assertEquals(CREATED, added.getStatusCode());
		assertNotNull(added.getBody());
		String cartCode = added.getBody().getCode();
		assertNotNull(cartCode);

		item.setQuantity(2);
		ResponseEntity<ReadableShoppingCart> updated = testRestTemplate.exchange(
				"/api/v1/cart/" + cartCode + "?store=DEFAULT", PUT,
				new HttpEntity<>(item), ReadableShoppingCart.class);
		assertTrue(updated.getStatusCode() == OK || updated.getStatusCode() == CREATED);

		ResponseEntity<ReadableShoppingCart> cart = testRestTemplate.getForEntity(
				"/api/v1/cart/" + cartCode + "?store=DEFAULT", ReadableShoppingCart.class);
		assertEquals(OK, cart.getStatusCode());
		assertNotNull(cart.getBody());
		assertEquals(2, cart.getBody().getQuantity());
	}
}
