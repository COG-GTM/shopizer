package com.salesmanager.test.shop.uat;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.test.context.junit.jupiter.SpringExtension;

import com.salesmanager.shop.application.ShopApplication;
import com.salesmanager.shop.model.catalog.product.ReadableProduct;
import com.salesmanager.shop.model.shoppingcart.PersistableShoppingCartItem;
import com.salesmanager.shop.model.shoppingcart.ReadableShoppingCart;
import com.salesmanager.test.shop.common.ServicesTestSupport;

@SpringBootTest(classes = ShopApplication.class, webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ExtendWith(SpringExtension.class)
public class StorefrontJourneyUatTest extends ServicesTestSupport {

	@Test
	public void browseAndCartJourneysWork() {
		assertEquals(HttpStatus.OK, testRestTemplate.getForEntity(
				"/api/v1/store/DEFAULT", String.class).getStatusCode());
		assertEquals(HttpStatus.OK, testRestTemplate.getForEntity(
				"/api/v1/category?store=DEFAULT", String.class).getStatusCode());
		assertEquals(HttpStatus.OK, testRestTemplate.getForEntity(
				"/api/v2/products?store=DEFAULT", String.class).getStatusCode());

		String suffix = UUID.randomUUID().toString().replace("-", "");
		ReadableProduct firstProduct = sampleProduct("uatfirst" + suffix);
		assertNotNull(firstProduct);
		assertEquals(HttpStatus.OK, testRestTemplate.getForEntity(
				"/api/v2/product/" + firstProduct.getSku() + "?store=DEFAULT", String.class).getStatusCode());

		ReadableProduct secondProduct = sampleProduct("uatsecond" + suffix);
		PersistableShoppingCartItem firstItem = cartItem(firstProduct.getSku(), 1);
		ResponseEntity<ReadableShoppingCart> created = testRestTemplate.postForEntity(
				"/api/v1/cart/?store=DEFAULT", new HttpEntity<>(firstItem, getHeader()),
				ReadableShoppingCart.class);
		assertEquals(HttpStatus.CREATED, created.getStatusCode());

		ResponseEntity<ReadableShoppingCart> secondAdded = testRestTemplate.exchange(
				"/api/v1/cart/" + created.getBody().getCode() + "?store=DEFAULT", HttpMethod.PUT,
				new HttpEntity<>(cartItem(secondProduct.getSku(), 1), getHeader()), ReadableShoppingCart.class);
		assertEquals(HttpStatus.CREATED, secondAdded.getStatusCode());
		assertEquals(2, secondAdded.getBody().getQuantity());

		ResponseEntity<ReadableShoppingCart> quantityUpdated = testRestTemplate.exchange(
				"/api/v1/cart/" + created.getBody().getCode() + "?store=DEFAULT", HttpMethod.PUT,
				new HttpEntity<>(cartItem(firstProduct.getSku(), 3), getHeader()), ReadableShoppingCart.class);
		assertEquals(HttpStatus.CREATED, quantityUpdated.getStatusCode());
		assertEquals(4, quantityUpdated.getBody().getQuantity());

		ResponseEntity<ReadableShoppingCart> fetched = testRestTemplate.getForEntity(
				"/api/v1/cart/" + created.getBody().getCode() + "?store=DEFAULT", ReadableShoppingCart.class);
		assertEquals(HttpStatus.OK, fetched.getStatusCode());
		assertEquals(4, fetched.getBody().getQuantity());
	}

	private static PersistableShoppingCartItem cartItem(String sku, int quantity) {
		PersistableShoppingCartItem item = new PersistableShoppingCartItem();
		item.setProduct(sku);
		item.setQuantity(quantity);
		return item;
	}
}
