package com.salesmanager.test.shop.integration.cart;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.springframework.http.HttpMethod.GET;
import static org.springframework.http.HttpMethod.POST;
import static org.springframework.http.HttpMethod.PUT;
import static org.springframework.http.HttpStatus.CREATED;
import static org.springframework.http.HttpStatus.NOT_FOUND;
import static org.springframework.http.HttpStatus.OK;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.SpringBootTest.WebEnvironment;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.http.HttpEntity;
import org.springframework.http.ResponseEntity;
import org.springframework.test.context.junit.jupiter.SpringExtension;

import com.salesmanager.core.business.configuration.DroolsBeanFactory;
import com.salesmanager.shop.application.ShopApplication;
import com.salesmanager.shop.model.catalog.product.ReadableProduct;
import com.salesmanager.shop.model.shoppingcart.PersistableShoppingCartItem;
import com.salesmanager.shop.model.shoppingcart.ReadableShoppingCart;
import com.salesmanager.shop.store.api.exception.ErrorEntity;
import com.salesmanager.test.shop.common.ServicesTestSupport;

@SpringBootTest(classes = ShopApplication.class, webEnvironment = WebEnvironment.RANDOM_PORT)
@ExtendWith(SpringExtension.class)
public class ShoppingCartPromoCodeIntegrationTest extends ServicesTestSupport {

	@Autowired
	private TestRestTemplate testRestTemplate;

	@Autowired
	private DroolsBeanFactory droolsBeanFactory;

	@Test
	public void appliesValidPromoToFreshCart() {
		ReadableShoppingCart cart = createCart("promoValidFreshCart", null);

		ResponseEntity<ReadableShoppingCart> response = applyPromo(cart.getCode(), "Test1234");

		assertEquals(CREATED, response.getStatusCode());
	}

	@Test
	public void rejectsInvalidPromosWithStructuredError() {
		ReadableShoppingCart cart = createCart("promoInvalidValuesCart", null);

		for (String promo : new String[] { "bad$code", "x".repeat(33), "a b" }) {
			ResponseEntity<ErrorEntity> response = testRestTemplate.exchange(
					"/api/v1/cart/{code}/promo/{promo}", POST, new HttpEntity<>(getHeader()),
					ErrorEntity.class, cart.getCode(), promo);

			assertEquals(org.springframework.http.HttpStatus.BAD_REQUEST, response.getStatusCode());
			assertNotNull(response.getBody());
			assertEquals("400", response.getBody().getErrorCode());
			assertEquals("Invalid promo code", response.getBody().getMessage());
		}
	}

	@Test
	public void returnsNotFoundForValidPromoOnUnknownCart() {
		ResponseEntity<ErrorEntity> response = testRestTemplate.exchange(
				"/api/v1/cart/{code}/promo/{promo}", POST, new HttpEntity<>(getHeader()),
				ErrorEntity.class, "doesnotexist123", "Test1234");

		assertEquals(NOT_FOUND, response.getStatusCode());
	}

	@Test
	public void validatesPromoOnCartCreation() {
		ReadableProduct product = sampleProduct("promoCreateInvalidCart");
		PersistableShoppingCartItem invalidItem = cartItem(product.getSku(), "bad$code");
		ResponseEntity<ErrorEntity> invalidResponse = testRestTemplate.postForEntity(
				"/api/v1/cart", new HttpEntity<>(invalidItem, getHeader()), ErrorEntity.class);

		assertEquals(org.springframework.http.HttpStatus.BAD_REQUEST, invalidResponse.getStatusCode());
		assertNotNull(invalidResponse.getBody());
		assertEquals("400", invalidResponse.getBody().getErrorCode());

		ReadableProduct validProduct = sampleProduct("promoCreateValidCart");
		PersistableShoppingCartItem validItem = cartItem(validProduct.getSku(), "SUMMER_2024");
		ResponseEntity<ReadableShoppingCart> validResponse = testRestTemplate.postForEntity(
				"/api/v1/cart", new HttpEntity<>(validItem, getHeader()), ReadableShoppingCart.class);

		assertEquals(CREATED, validResponse.getStatusCode());
	}

	@Test
	public void validatesPromoBeforeUpdatingCartContents() {
		ReadableShoppingCart cart = createCart("promoUpdateExistingCart", null);
		int originalQuantity = cart.getQuantity();
		ReadableProduct secondProduct = sampleProduct("promoUpdateSecondProduct");
		PersistableShoppingCartItem invalidItem = cartItem(secondProduct.getSku(), "bad$code");

		ResponseEntity<ErrorEntity> updateResponse = testRestTemplate.exchange(
				"/api/v1/cart/{code}", PUT, new HttpEntity<>(invalidItem, getHeader()),
				ErrorEntity.class, cart.getCode());
		ResponseEntity<ReadableShoppingCart> afterUpdate = testRestTemplate.exchange(
				"/api/v1/cart/{code}", GET, new HttpEntity<>(getHeader()),
				ReadableShoppingCart.class, cart.getCode());

		assertEquals(org.springframework.http.HttpStatus.BAD_REQUEST, updateResponse.getStatusCode());
		assertEquals(OK, afterUpdate.getStatusCode());
		assertNotNull(afterUpdate.getBody());
		assertEquals(originalQuantity, afterUpdate.getBody().getQuantity());
	}

	@Test
	public void repeatedCartReadsReusePromoContainer() {
		ReadableShoppingCart cart = createCart("promoRepeatedReadsCart", null);
		assertEquals(CREATED, applyPromo(cart.getCode(), "Test1234").getStatusCode());

		var containerBefore = droolsBeanFactory.getKieContainer("PromoCoupon.drl");
		for (int i = 0; i < 20; i++) {
			ResponseEntity<ReadableShoppingCart> response = testRestTemplate.exchange(
					"/api/v1/cart/{code}", GET, new HttpEntity<>(getHeader()),
					ReadableShoppingCart.class, cart.getCode());
			assertEquals(OK, response.getStatusCode());
		}
		var containerAfter = droolsBeanFactory.getKieContainer("PromoCoupon.drl");

		assertSame(containerBefore, containerAfter);
	}

	private ReadableShoppingCart createCart(String productCode, String promoCode) {
		ReadableProduct product = sampleProduct(productCode);
		assertNotNull(product);
		ResponseEntity<ReadableShoppingCart> response = testRestTemplate.postForEntity(
				"/api/v1/cart", new HttpEntity<>(cartItem(product.getSku(), promoCode), getHeader()),
				ReadableShoppingCart.class);
		assertEquals(CREATED, response.getStatusCode());
		assertNotNull(response.getBody());
		return response.getBody();
	}

	private ResponseEntity<ReadableShoppingCart> applyPromo(String cartCode, String promoCode) {
		return testRestTemplate.exchange("/api/v1/cart/{code}/promo/{promo}", POST,
				new HttpEntity<>(getHeader()), ReadableShoppingCart.class, cartCode, promoCode);
	}

	private static PersistableShoppingCartItem cartItem(String productSku, String promoCode) {
		PersistableShoppingCartItem item = new PersistableShoppingCartItem();
		item.setProduct(productSku);
		item.setQuantity(1);
		item.setPromoCode(promoCode);
		return item;
	}
}
