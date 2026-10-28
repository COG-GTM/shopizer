package com.salesmanager.test.shop.uat;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

import org.junit.Before;
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
import com.salesmanager.shop.model.order.transaction.ReadableTransaction;
import com.salesmanager.shop.model.shoppingcart.PersistableShoppingCartItem;
import com.salesmanager.shop.model.shoppingcart.ReadableShoppingCart;
import com.salesmanager.shop.model.system.IntegrationModuleConfiguration;
import com.salesmanager.test.shop.common.ServicesTestSupport;

@SpringBootTest(classes = ShopApplication.class, webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@RunWith(SpringRunner.class)
public class PaymentJourneyUatTest extends ServicesTestSupport {

	@Before
	public void configureStripe() {
		IntegrationModuleConfiguration configuration = new IntegrationModuleConfiguration();
		configuration.setCode("stripe");
		configuration.setActive(true);
		configuration.getIntegrationKeys().put("publishableKey", "pk_test_dummy");
		configuration.getIntegrationKeys().put("secretKey", "sk_test_dummy");
		ResponseEntity<Void> response = testRestTemplate.exchange("/api/v1/private/modules/payment?store=DEFAULT",
				HttpMethod.POST, new HttpEntity<>(configuration, getHeader()), Void.class);
		assertEquals(HttpStatus.OK, response.getStatusCode());
	}

	@Test
	public void initializesCartAtServerTotalAndRejectsInflatedAmount() {
		ReadableProduct product = sampleProduct(uniqueCode());
		PersistableShoppingCartItem cartItem = new PersistableShoppingCartItem();
		cartItem.setProduct(product.getSku());
		cartItem.setQuantity(1);
		ResponseEntity<ReadableShoppingCart> cartResponse = testRestTemplate.postForEntity(
				"/api/v1/cart?store=DEFAULT", new HttpEntity<>(cartItem, jsonHeaders()), ReadableShoppingCart.class);
		assertEquals(HttpStatus.CREATED, cartResponse.getStatusCode());
		ReadableShoppingCart cart = cartResponse.getBody();
		assertNotNull(cart);

		ResponseEntity<ReadableTransaction> initialized = testRestTemplate.exchange(
				"/api/v1/cart/" + cart.getCode() + "/payment/init?store=DEFAULT", HttpMethod.POST,
				new HttpEntity<>(payment(null), jsonHeaders()), ReadableTransaction.class);
		assertEquals(HttpStatus.OK, initialized.getStatusCode());
		assertNotNull(initialized.getBody());
		assertEquals(cart.getTotal().setScale(2, RoundingMode.HALF_UP).toPlainString(),
				initialized.getBody().getAmount().replaceAll("[^0-9,.-]", "").replace(",", ""));
		assertEquals(com.salesmanager.core.model.payments.TransactionType.INIT,
				initialized.getBody().getTransactionType());

		Map<String, Object> inflatedPayment = new LinkedHashMap<>();
		inflatedPayment.put("paymentType", "CREDITCARD");
		inflatedPayment.put("paymentModule", "stripe");
		inflatedPayment.put("amount", cart.getTotal().add(new BigDecimal("1.00")).toPlainString());
		inflatedPayment.put("bogusExtra", "ignored by payment DTO");
		ResponseEntity<String> rejected = testRestTemplate.exchange(
				"/api/v1/cart/" + cart.getCode() + "/payment/init?store=DEFAULT", HttpMethod.POST,
				new HttpEntity<>(inflatedPayment, jsonHeaders()), String.class);
		assertEquals(HttpStatus.BAD_REQUEST, rejected.getStatusCode());
	}

	private Map<String, Object> payment(String amount) {
		Map<String, Object> payment = new LinkedHashMap<>();
		payment.put("paymentType", "CREDITCARD");
		payment.put("paymentModule", "stripe");
		if (amount != null) {
			payment.put("amount", amount);
		}
		return payment;
	}

	private HttpHeaders jsonHeaders() {
		HttpHeaders headers = new HttpHeaders();
		headers.setContentType(MediaType.APPLICATION_JSON);
		return headers;
	}

	private String uniqueCode() {
		return UUID.randomUUID().toString().replace("-", "");
	}
}
