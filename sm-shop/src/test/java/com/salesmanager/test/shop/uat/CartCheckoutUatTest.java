package com.salesmanager.test.shop.uat;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.SpringBootTest.WebEnvironment;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.test.context.junit4.SpringRunner;

import com.salesmanager.shop.application.ShopApplication;
import com.salesmanager.shop.model.catalog.product.ReadableProduct;
import com.salesmanager.shop.model.shoppingcart.PersistableShoppingCartItem;
import com.salesmanager.shop.model.shoppingcart.ReadableShoppingCart;
import com.salesmanager.test.shop.common.ServicesTestSupport;

@SpringBootTest(classes = ShopApplication.class, webEnvironment = WebEnvironment.RANDOM_PORT)
@RunWith(SpringRunner.class)
public class CartCheckoutUatTest extends ServicesTestSupport {

	@Test
	public void customerCanCreateUpdateAndReadCart() {
		ReadableProduct product = sampleProduct("cart-uat-" + System.nanoTime());

		PersistableShoppingCartItem item = new PersistableShoppingCartItem();
		item.setProduct(product.getSku());
		item.setQuantity(1);
		ResponseEntity<ReadableShoppingCart> createResponse = testRestTemplate.postForEntity("/api/v1/cart?store=DEFAULT",
				new HttpEntity<>(item), ReadableShoppingCart.class);
		assertEquals(HttpStatus.CREATED, createResponse.getStatusCode());
		assertNotNull(createResponse.getBody());

		item.setQuantity(2);
		ResponseEntity<ReadableShoppingCart> updateResponse = testRestTemplate.exchange(
				"/api/v1/cart/" + createResponse.getBody().getCode() + "?store=DEFAULT", HttpMethod.PUT,
				new HttpEntity<>(item), ReadableShoppingCart.class);
		assertEquals(HttpStatus.CREATED, updateResponse.getStatusCode());

		ResponseEntity<ReadableShoppingCart> readResponse = testRestTemplate.getForEntity(
				"/api/v1/cart/" + createResponse.getBody().getCode() + "?store=DEFAULT",
				ReadableShoppingCart.class);
		assertEquals(HttpStatus.OK, readResponse.getStatusCode());
		assertNotNull(readResponse.getBody());
		assertEquals(2, readResponse.getBody().getQuantity());
	}
}
