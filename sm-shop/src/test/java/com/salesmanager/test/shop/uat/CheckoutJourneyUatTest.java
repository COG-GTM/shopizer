package com.salesmanager.test.shop.uat;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;

import java.util.HashMap;
import java.util.Map;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.test.context.junit4.SpringRunner;

import com.salesmanager.shop.application.ShopApplication;
import com.salesmanager.shop.model.catalog.product.ReadableProduct;
import com.salesmanager.shop.model.shoppingcart.PersistableShoppingCartItem;
import com.salesmanager.shop.model.shoppingcart.ReadableShoppingCart;
import com.salesmanager.shop.model.store.ReadableMerchantStore;
import com.salesmanager.test.shop.common.ServicesTestSupport;

@RunWith(SpringRunner.class)
@SpringBootTest(classes = ShopApplication.class, webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
public class CheckoutJourneyUatTest extends ServicesTestSupport {

	@Test
	public void checkoutRequiresAuthentication() {
		ReadableShoppingCart cart = createCart("ab534-checkout-anonymous");
		ReadableMerchantStore store = testRestTemplate.getForObject(
				"/api/v1/store/DEFAULT", ReadableMerchantStore.class);
		assertNotNull(store);

		ResponseEntity<String> response = testRestTemplate.postForEntity(
				"/api/v1/auth/cart/" + cart.getCode() + "/checkout?store=DEFAULT",
				new HttpEntity<>(checkoutBody(store.getCurrency(), cart.getTotal()), jsonHeaders()), String.class);
		assertEquals(HttpStatus.UNAUTHORIZED, response.getStatusCode());
	}

	private ReadableShoppingCart createCart(String productCode) {
		ReadableProduct product = sampleProduct(productCode);
		PersistableShoppingCartItem item = new PersistableShoppingCartItem();
		item.setProduct(product.getSku());
		item.setQuantity(1);
		ResponseEntity<ReadableShoppingCart> cart = testRestTemplate.postForEntity(
				"/api/v1/cart?store=DEFAULT", new HttpEntity<>(item, jsonHeaders()), ReadableShoppingCart.class);
		assertEquals(HttpStatus.CREATED, cart.getStatusCode());
		assertNotNull(cart.getBody());
		assertNotNull(cart.getBody().getCode());
		assertNotNull(cart.getBody().getTotal());
		return cart.getBody();
	}

	private static Map<String, Object> checkoutBody(String currency, java.math.BigDecimal amount) {
		Map<String, Object> payment = new HashMap<>();
		payment.put("paymentType", "MONEYORDER");
		payment.put("transactionType", "CAPTURE");
		payment.put("paymentModule", "moneyorder");
		payment.put("amount", amount);

		Map<String, Object> body = new HashMap<>();
		body.put("customerAgreement", true);
		body.put("currency", currency);
		body.put("payment", payment);
		return body;
	}

	private static HttpHeaders jsonHeaders() {
		HttpHeaders headers = new HttpHeaders();
		headers.setContentType(MediaType.APPLICATION_JSON);
		return headers;
	}
}
