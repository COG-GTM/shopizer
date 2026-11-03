package com.salesmanager.test.shop.integration.order;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

import org.junit.jupiter.api.Test;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpMethod;
import org.springframework.http.ResponseEntity;

import com.salesmanager.core.business.constants.Constants;
import com.salesmanager.core.model.order.orderstatus.OrderStatus;
import com.salesmanager.core.model.payments.PaymentType;
import com.salesmanager.shop.model.customer.PersistableCustomer;
import com.salesmanager.shop.model.customer.address.Address;
import com.salesmanager.shop.model.order.ReadableOrderTotalSummary;
import com.salesmanager.shop.model.order.transaction.PersistablePayment;
import com.salesmanager.shop.model.order.v0.ReadableOrder;
import com.salesmanager.shop.model.order.v0.ReadableOrderList;
import com.salesmanager.shop.model.order.v1.PersistableAnonymousOrder;
import com.salesmanager.shop.model.order.v1.ReadableOrderConfirmation;
import com.salesmanager.shop.model.shoppingcart.PersistableShoppingCartItem;
import com.salesmanager.shop.model.shoppingcart.ReadableShoppingCart;
import com.salesmanager.shop.model.system.IntegrationModuleConfiguration;
import com.salesmanager.test.shop.common.ServicesTestSupport;

public class CheckoutPaymentTypeIntegrationTest extends ServicesTestSupport {

	@Test
	public void anonymousCheckoutRejectsSpoofAndAcceptsMatchingMoneyOrderType() {
		configureMoneyOrder();

		ReadableShoppingCart spoofCart = sampleCart();
		String spoofTotal = getCartTotal(spoofCart.getCode());
		Set<Long> ordersBeforeSpoof = orderIds();
		ResponseEntity<String> spoofResponse = checkout(spoofCart.getCode(), "CREDITCARD", spoofTotal);

		assertFalse(spoofResponse.getStatusCode().is2xxSuccessful(),
				"Expected spoof checkout to fail, got " + spoofResponse.getStatusCode() + ": " + spoofResponse.getBody());
		Set<Long> ordersAfterSpoof = orderIds();
		assertEquals(ordersBeforeSpoof, ordersAfterSpoof,
				"Rejected checkout must not create an order: " + spoofResponse.getBody());

		ReadableShoppingCart validCart = createCartForExistingSampleProduct();
		String validTotal = getCartTotal(validCart.getCode());
		ResponseEntity<ReadableOrderConfirmation> validResponse =
				testRestTemplate.postForEntity(checkoutUri(validCart.getCode()),
						new HttpEntity<>(anonymousOrder("MONEYORDER", validTotal)), ReadableOrderConfirmation.class);

		assertEquals(200, validResponse.getStatusCode().value(), String.valueOf(validResponse.getBody()));
		assertNotNull(validResponse.getBody());
		assertNotNull(validResponse.getBody().getId());
		assertTrue(validResponse.getBody().getId() > 0);

		ReadableOrder acceptedOrder = testRestTemplate.exchange(
				"/api/v1/private/orders/" + validResponse.getBody().getId() + "?store=" + Constants.DEFAULT_STORE,
				HttpMethod.GET, new HttpEntity<>(getHeader()), ReadableOrder.class).getBody();
		assertNotNull(acceptedOrder);
		assertEquals(OrderStatus.ORDERED, acceptedOrder.getOrderStatus());
		assertEquals(PaymentType.MONEYORDER, acceptedOrder.getPaymentType());
	}

	private void configureMoneyOrder() {
		IntegrationModuleConfiguration configuration = new IntegrationModuleConfiguration();
		configuration.setCode("moneyorder");
		configuration.setActive(true);
		configuration.setDefaultSelected(true);
		Map<String, String> integrationKeys = new HashMap<>();
		integrationKeys.put("address", "Payment by mail");
		integrationKeys.put("transaction", "AUTHORIZECAPTURE");
		configuration.setIntegrationKeys(integrationKeys);

		ResponseEntity<Void> response = testRestTemplate.exchange(
				"/api/v1/private/modules/payment?store=" + Constants.DEFAULT_STORE,
				HttpMethod.POST, new HttpEntity<>(configuration, getHeader()), Void.class);
		assertTrue(response.getStatusCode().is2xxSuccessful(),
				"Could not configure moneyorder: " + response.getStatusCode());
	}

	private ReadableShoppingCart createCartForExistingSampleProduct() {
		PersistableShoppingCartItem cartItem = new PersistableShoppingCartItem();
		cartItem.setProduct("sampleCart");
		cartItem.setQuantity(1);
		ResponseEntity<ReadableShoppingCart> response = testRestTemplate.postForEntity(
				"/api/v1/cart?store=" + Constants.DEFAULT_STORE, new HttpEntity<>(cartItem), ReadableShoppingCart.class);
		assertEquals(201, response.getStatusCode().value());
		assertNotNull(response.getBody());
		return response.getBody();
	}

	private String getCartTotal(String cartCode) {
		ReadableOrderTotalSummary summary = testRestTemplate.getForObject(
				"/api/v1/cart/" + cartCode + "/total?store=" + Constants.DEFAULT_STORE,
				ReadableOrderTotalSummary.class);
		assertNotNull(summary);
		assertNotNull(summary.getTotal());
		return summary.getTotal().replaceAll("[^\\d.]", "");
	}

	private ResponseEntity<String> checkout(String cartCode, String paymentType, String amount) {
		return testRestTemplate.postForEntity(checkoutUri(cartCode),
				new HttpEntity<>(anonymousOrder(paymentType, amount)), String.class);
	}

	private String checkoutUri(String cartCode) {
		return "/api/v1/cart/" + cartCode + "/checkout?store=" + Constants.DEFAULT_STORE;
	}

	private PersistableAnonymousOrder anonymousOrder(String paymentType, String amount) {
		Address billing = new Address();
		billing.setFirstName("Payment");
		billing.setLastName("Type");
		billing.setAddress("1 Test Street");
		billing.setCity("Testville");
		billing.setCountry("US");
		billing.setBillingAddress(true);

		PersistableCustomer customer = new PersistableCustomer();
		customer.setEmailAddress("payment-type-" + System.nanoTime() + "@example.test");
		customer.setFirstName("Payment");
		customer.setLastName("Type");
		customer.setGender("M");
		customer.setLanguage("en");
		customer.setStoreCode(Constants.DEFAULT_STORE);
		customer.setBilling(billing);

		PersistablePayment payment = new PersistablePayment();
		payment.setPaymentModule("moneyorder");
		payment.setPaymentType(paymentType);
		payment.setTransactionType("AUTHORIZECAPTURE");
		payment.setAmount(amount);

		PersistableAnonymousOrder order = new PersistableAnonymousOrder();
		order.setCustomer(customer);
		order.setPayment(payment);
		order.setCurrency("USD");
		return order;
	}

	private Set<Long> orderIds() {
		ReadableOrderList orders = testRestTemplate.exchange(
				"/api/v1/private/orders?count=100&page=0&store=" + Constants.DEFAULT_STORE,
				HttpMethod.GET, new HttpEntity<>(getHeader()), ReadableOrderList.class).getBody();
		assertNotNull(orders);
		if (orders.getOrders() == null) {
			return Collections.emptySet();
		}
		return orders.getOrders().stream().map(ReadableOrder::getId).collect(Collectors.toSet());
	}
}
