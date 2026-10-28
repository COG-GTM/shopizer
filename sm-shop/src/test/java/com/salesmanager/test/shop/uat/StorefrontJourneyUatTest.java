package com.salesmanager.test.shop.uat;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.SpringBootTest.WebEnvironment;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.test.context.junit4.SpringRunner;

import com.salesmanager.shop.application.ShopApplication;
import com.salesmanager.shop.model.catalog.product.ReadableProduct;
import com.salesmanager.shop.model.shoppingcart.PersistableShoppingCartItem;
import com.salesmanager.shop.model.shoppingcart.ReadableShoppingCart;
import com.salesmanager.shop.model.store.ReadableMerchantStore;
import com.salesmanager.test.shop.common.ServicesTestSupport;

@SpringBootTest(classes = ShopApplication.class, webEnvironment = WebEnvironment.RANDOM_PORT)
@RunWith(SpringRunner.class)
public class StorefrontJourneyUatTest extends ServicesTestSupport {

	@Test
	public void storefrontCanBrowseProductsAndManageCart() {
		ResponseEntity<ReadableMerchantStore> store = testRestTemplate.getForEntity(
				"/api/v1/store/DEFAULT", ReadableMerchantStore.class);
		assertEquals(HttpStatus.OK, store.getStatusCode());
		assertNotNull(store.getBody());

		ResponseEntity<String> categories = testRestTemplate.getForEntity(
				"/api/v1/category?store=DEFAULT", String.class);
		assertEquals(HttpStatus.OK, categories.getStatusCode());

		ResponseEntity<String> products = testRestTemplate.getForEntity(
				"/api/v1/products?store=DEFAULT&count=10", String.class);
		assertEquals(HttpStatus.OK, products.getStatusCode());

		String sku = "uat-product-" + System.nanoTime();
		ReadableProduct createdProduct = sampleProduct(sku);
		assertNotNull(createdProduct);
		ResponseEntity<ReadableProduct> detail = testRestTemplate.getForEntity(
				"/api/v2/product/" + sku + "?store=DEFAULT", ReadableProduct.class);
		assertEquals(HttpStatus.OK, detail.getStatusCode());
		assertEquals(sku, detail.getBody().getSku());

		PersistableShoppingCartItem item = new PersistableShoppingCartItem();
		item.setProduct(sku);
		item.setQuantity(1);
		ResponseEntity<ReadableShoppingCart> createdCart = testRestTemplate.postForEntity(
				"/api/v1/cart?store=DEFAULT", new HttpEntity<>(item), ReadableShoppingCart.class);
		assertEquals(HttpStatus.CREATED, createdCart.getStatusCode());
		assertNotNull(createdCart.getBody());

		item.setQuantity(3);
		ResponseEntity<ReadableShoppingCart> updatedCart = testRestTemplate.exchange(
				"/api/v1/cart/" + createdCart.getBody().getCode() + "?store=DEFAULT", HttpMethod.PUT,
				new HttpEntity<>(item), ReadableShoppingCart.class);
		assertEquals(HttpStatus.CREATED, updatedCart.getStatusCode());
		assertEquals(3, updatedCart.getBody().getProducts().get(0).getQuantity());

		ResponseEntity<ReadableShoppingCart> readCart = testRestTemplate.getForEntity(
				"/api/v1/cart/" + createdCart.getBody().getCode() + "?store=DEFAULT", ReadableShoppingCart.class);
		assertEquals(HttpStatus.OK, readCart.getStatusCode());
		assertEquals(3, readCart.getBody().getProducts().get(0).getQuantity());
	}
}
