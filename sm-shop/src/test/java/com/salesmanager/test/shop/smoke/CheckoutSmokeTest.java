package com.salesmanager.test.shop.smoke;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpMethod;
import org.springframework.http.ResponseEntity;

import com.fasterxml.jackson.databind.JsonNode;
import com.salesmanager.shop.model.order.transaction.PersistablePayment;
import com.salesmanager.shop.model.order.v1.PersistableAnonymousOrder;
import com.salesmanager.shop.model.customer.PersistableCustomer;
import com.salesmanager.shop.model.customer.address.Address;
import com.salesmanager.shop.model.shoppingcart.PersistableShoppingCartItem;
import com.salesmanager.shop.model.shoppingcart.ReadableShoppingCart;
import com.salesmanager.shop.model.store.ReadableMerchantStore;

class CheckoutSmokeTest extends SmokeTestSupport {

	@Test
	void anonymousMoneyOrderCheckoutReturnsReadableAdminOrder() {
		String adminToken = adminToken();
		ResponseEntity<Void> paymentModule = testRestTemplate.exchange("/api/v1/private/modules/payment",
				HttpMethod.POST, new HttpEntity<>("{\"code\":\"moneyorder\",\"active\":true,"
						+ "\"defaultSelected\":true,\"integrationKeys\":{\"address\":\"AB-533 test address\","
						+ "\"transaction\":\"AUTHORIZECAPTURE\"}}",
						bearerHeaders(adminToken)), Void.class);
		assertEquals(200, paymentModule.getStatusCodeValue());

		ProductFixture product = createProduct("checkout");
		PersistableShoppingCartItem item = new PersistableShoppingCartItem();
		item.setProduct(product.product.getSku());
		item.setQuantity(1);
		ResponseEntity<ReadableShoppingCart> cart = testRestTemplate.postForEntity("/api/v1/cart",
				new HttpEntity<>(item, jsonHeaders()), ReadableShoppingCart.class);
		assertEquals(201, cart.getStatusCodeValue());

		PersistableAnonymousOrder order = new PersistableAnonymousOrder();
		PersistableCustomer customer = new PersistableCustomer();
		customer.setEmailAddress(uniqueCode("checkout") + "@example.test");
		customer.setGender("M");
		customer.setLanguage("en");
		customer.setStoreCode("DEFAULT");
		ResponseEntity<ReadableMerchantStore> store = testRestTemplate.getForEntity(
				"/api/v1/store/DEFAULT", ReadableMerchantStore.class);
		assertEquals(200, store.getStatusCodeValue());
		assertNotNull(store.getBody());
		order.setCurrency(store.getBody().getCurrency());
		Address billing = new Address();
		billing.setFirstName("Checkout");
		billing.setLastName("Smoke");
		billing.setCountry("BE");
		billing.setAddress("Main Street 1");
		billing.setCity("Brussels");
		customer.setBilling(billing);
		order.setCustomer(customer);
		PersistablePayment payment = new PersistablePayment();
		payment.setPaymentType("MONEYORDER");
		payment.setAmount("10.00");
		payment.setPaymentModule("moneyorder");
		payment.setTransactionType("AUTHORIZECAPTURE");
		order.setPayment(payment);

		ResponseEntity<JsonNode> checkout = testRestTemplate.postForEntity(
				"/api/v1/cart/" + cart.getBody().getCode() + "/checkout",
				new HttpEntity<>(order, jsonHeaders()), JsonNode.class);
		assertEquals(200, checkout.getStatusCodeValue(), String.valueOf(checkout.getBody()));
		assertNotNull(checkout.getBody());
		long orderId = checkout.getBody().path("id").asLong();
		assertTrue(orderId > 0, "Checkout response did not contain an order id: " + checkout.getBody());

		ResponseEntity<String> orderDetails = testRestTemplate.exchange(
				"/api/v1/private/orders/" + orderId, HttpMethod.GET,
				new HttpEntity<>(bearerHeaders(adminToken)), String.class);
		assertEquals(200, orderDetails.getStatusCodeValue());
	}
}
