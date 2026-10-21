package com.salesmanager.test.shop.uat;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;
import static org.springframework.http.HttpStatus.CREATED;
import static org.springframework.http.HttpStatus.OK;

import java.util.List;
import java.util.Map;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpMethod;
import org.springframework.http.ResponseEntity;
import org.springframework.test.context.junit4.SpringRunner;

import com.salesmanager.shop.application.ShopApplication;
import com.salesmanager.shop.model.shoppingcart.PersistableShoppingCartItem;
import com.salesmanager.shop.model.shoppingcart.ReadableShoppingCart;
import com.salesmanager.test.shop.common.ServicesTestSupport;

@RunWith(SpringRunner.class)
@SpringBootTest(classes = ShopApplication.class, webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
public class BrowseAndCartUatTest extends ServicesTestSupport {

	@Test
	public void browseProductAndUpdateCart() {
		ResponseEntity<Map<String, Object>> store = testRestTemplate.exchange(
				"/api/v1/store/DEFAULT", HttpMethod.GET, HttpEntity.EMPTY,
				new ParameterizedTypeReference<Map<String, Object>>() { });
		assertEquals(OK, store.getStatusCode());
		assertEquals("DEFAULT", store.getBody().get("code"));

		ResponseEntity<String> categories = testRestTemplate.getForEntity(
				"/api/v1/category?store=DEFAULT", String.class);
		assertEquals(OK, categories.getStatusCode());

		ResponseEntity<Map<String, Object>> productList = testRestTemplate.exchange(
				"/api/v1/products?store=DEFAULT", HttpMethod.GET, HttpEntity.EMPTY,
				new ParameterizedTypeReference<Map<String, Object>>() { });
		assertEquals(OK, productList.getStatusCode());
		assertNotNull(productList.getBody());
		List<?> products = (List<?>) productList.getBody().get("products");
		assertNotNull(products);
		assertTrue(!products.isEmpty());
		Map<?, ?> firstProduct = (Map<?, ?>) products.get(0);
		String sku = String.valueOf(firstProduct.get("sku"));

		ResponseEntity<Map<String, Object>> product = testRestTemplate.exchange(
				"/api/v2/product/" + sku + "?store=DEFAULT", HttpMethod.GET, HttpEntity.EMPTY,
				new ParameterizedTypeReference<Map<String, Object>>() { });
		assertEquals(OK, product.getStatusCode());
		assertEquals(sku, product.getBody().get("sku"));

		PersistableShoppingCartItem item = new PersistableShoppingCartItem();
		item.setProduct(sku);
		item.setQuantity(1);
		ResponseEntity<ReadableShoppingCart> created = testRestTemplate.postForEntity(
				"/api/v1/cart?store=DEFAULT", new HttpEntity<>(item), ReadableShoppingCart.class);
		assertEquals(CREATED, created.getStatusCode());
		assertNotNull(created.getBody());
		String cartCode = created.getBody().getCode();

		try {
			item.setQuantity(2);
			ResponseEntity<ReadableShoppingCart> updated = testRestTemplate.exchange(
					"/api/v1/cart/" + cartCode + "?store=DEFAULT", HttpMethod.PUT,
					new HttpEntity<>(item), ReadableShoppingCart.class);
			assertEquals(CREATED, updated.getStatusCode());

			ResponseEntity<ReadableShoppingCart> fetched = testRestTemplate.getForEntity(
					"/api/v1/cart/" + cartCode + "?store=DEFAULT", ReadableShoppingCart.class);
			assertEquals(OK, fetched.getStatusCode());
			assertNotNull(fetched.getBody());
			assertEquals(2, fetched.getBody().getQuantity());
		} finally {
			testRestTemplate.exchange("/api/v1/cart/" + cartCode + "/product/" + sku
							+ "?store=DEFAULT&body=true",
					HttpMethod.DELETE, HttpEntity.EMPTY, String.class);
		}
	}
}
