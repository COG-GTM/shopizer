package com.salesmanager.test.shop.uat;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.http.HttpMethod.GET;
import static org.springframework.http.HttpMethod.PUT;
import static org.springframework.http.HttpStatus.CREATED;
import static org.springframework.http.HttpStatus.OK;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestMethodOrder;
import org.junit.jupiter.api.MethodOrderer.OrderAnnotation;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseEntity;

import com.salesmanager.core.business.constants.Constants;
import com.salesmanager.core.model.customer.CustomerGender;
import com.salesmanager.shop.model.catalog.product.ReadableProduct;
import com.salesmanager.shop.model.customer.PersistableCustomer;
import com.salesmanager.shop.model.customer.address.Address;
import com.salesmanager.shop.model.order.transaction.PersistablePayment;
import com.salesmanager.shop.model.order.v1.PersistableOrder;
import com.salesmanager.shop.model.order.v1.ReadableOrder;
import com.salesmanager.shop.model.order.v1.ReadableOrderConfirmation;
import com.salesmanager.shop.model.order.v1.ReadableOrderList;
import com.salesmanager.shop.model.shoppingcart.PersistableShoppingCartItem;
import com.salesmanager.shop.model.shoppingcart.ReadableShoppingCart;
import com.salesmanager.shop.model.system.IntegrationModuleConfiguration;
import com.salesmanager.shop.model.store.ReadableMerchantStore;
import com.salesmanager.shop.store.security.AuthenticationRequest;
import com.salesmanager.shop.store.security.AuthenticationResponse;
import com.salesmanager.test.shop.common.ServicesTestSupport;

@TestMethodOrder(OrderAnnotation.class)
class CartCheckoutJourneyUatTest extends ServicesTestSupport {

	@Test
	@Order(1)
	void visitorCanBuildCartAndAuthenticatedCustomerCanCheckout() {
		long suffix = System.nanoTime();
		ReadableProduct firstProduct = sampleProduct("checkout" + suffix + "a");
		ReadableProduct secondProduct = sampleProduct("checkout" + suffix + "b");
		HttpHeaders adminHeaders = getHeader();

		PersistableShoppingCartItem firstItem = cartItem(firstProduct, 1);
		ResponseEntity<ReadableShoppingCart> created = testRestTemplate.postForEntity("/api/v1/cart",
				new HttpEntity<>(firstItem, adminHeaders), ReadableShoppingCart.class);
		assertEquals(CREATED, created.getStatusCode());
		assertNotNull(created.getBody());
		String cartCode = created.getBody().getCode();

		PersistableShoppingCartItem secondItem = cartItem(secondProduct, 1);
		ResponseEntity<ReadableShoppingCart> updated = testRestTemplate.exchange(
				"/api/v1/cart/" + cartCode, PUT,
				new HttpEntity<>(secondItem, adminHeaders), ReadableShoppingCart.class);
		assertEquals(CREATED, updated.getStatusCode());
		assertEquals(2, updated.getBody().getQuantity());

		ResponseEntity<ReadableShoppingCart> fetched = testRestTemplate.exchange(
				"/api/v1/cart/" + cartCode, GET, new HttpEntity<>(adminHeaders),
				ReadableShoppingCart.class);
		assertEquals(OK, fetched.getStatusCode());
		assertEquals(2, fetched.getBody().getQuantity());
		assertEquals(2, fetched.getBody().getProducts().size());

		String email = "checkout-" + suffix + "@test.com";
		PersistableCustomer customer = new PersistableCustomer();
		customer.setEmailAddress(email);
		customer.setPassword("clear123");
		customer.setGender(CustomerGender.M.name());
		customer.setLanguage("en");
		customer.setStoreCode(Constants.DEFAULT_STORE);
		Address billing = new Address();
		billing.setFirstName("Checkout");
		billing.setLastName("Customer");
		billing.setCountry("BE");
		customer.setBilling(billing);
		ResponseEntity<PersistableCustomer> registered = testRestTemplate.postForEntity(
				"/api/v1/customer/register", new HttpEntity<>(customer, adminHeaders),
				PersistableCustomer.class);
		assertEquals(OK, registered.getStatusCode());

		ResponseEntity<AuthenticationResponse> login = testRestTemplate.postForEntity(
				"/api/v1/customer/login",
				new HttpEntity<>(new AuthenticationRequest(email, "clear123")),
				AuthenticationResponse.class);
		assertEquals(OK, login.getStatusCode());
		assertNotNull(login.getBody());

		ReadableMerchantStore store = testRestTemplate.getForObject(
				"/api/v1/store/DEFAULT", ReadableMerchantStore.class);
		PersistablePayment payment = new PersistablePayment();
		payment.setPaymentModule("moneyorder");
		payment.setPaymentType("MONEYORDER");
		payment.setTransactionType("AUTHORIZECAPTURE");
		payment.setAmount(fetched.getBody().getTotal().toPlainString());
		PersistableOrder order = new PersistableOrder();
		order.setCurrency(store.getCurrency());
		order.setCustomerAgreement(true);
		order.setPayment(payment);

		IntegrationModuleConfiguration moneyOrder = new IntegrationModuleConfiguration();
		moneyOrder.setCode("moneyorder");
		moneyOrder.setActive(true);
		Map<String, String> paymentKeys = new HashMap<>();
		paymentKeys.put("address", "UAT payment address");
		moneyOrder.setIntegrationKeys(paymentKeys);
		ResponseEntity<Void> configuredPayment = testRestTemplate.postForEntity(
				"/api/v1/private/modules/payment?store=DEFAULT",
				new HttpEntity<>(moneyOrder, adminHeaders), Void.class);
		assertEquals(OK, configuredPayment.getStatusCode());

		HttpHeaders customerHeaders = new HttpHeaders();
		customerHeaders.setBearerAuth(login.getBody().getToken());
		ResponseEntity<ReadableOrderConfirmation> checkout = testRestTemplate.postForEntity(
				"/api/v1/auth/cart/" + cartCode + "/checkout?store=DEFAULT",
				new HttpEntity<>(order, customerHeaders), ReadableOrderConfirmation.class);
		assertEquals(OK, checkout.getStatusCode());
		assertNotNull(checkout.getBody());
		assertNotNull(checkout.getBody().getId());

		ResponseEntity<ReadableOrderList> orders = testRestTemplate.exchange(
				"/api/v1/auth/orders?store=DEFAULT", GET, new HttpEntity<>(customerHeaders),
				ReadableOrderList.class);
		assertEquals(OK, orders.getStatusCode());
		List<Long> orderIds = orders.getBody().getOrders().stream()
				.map(ReadableOrder::getId).collect(Collectors.toList());
		assertTrue(orderIds.contains(checkout.getBody().getId()));

		ResponseEntity<ReadableOrder> detail = testRestTemplate.exchange(
				"/api/v1/auth/orders/" + checkout.getBody().getId() + "?store=DEFAULT",
				GET, new HttpEntity<>(customerHeaders), ReadableOrder.class);
		assertEquals(OK, detail.getStatusCode());
		assertEquals(checkout.getBody().getId(), detail.getBody().getId());
	}

	private static PersistableShoppingCartItem cartItem(ReadableProduct product, int quantity) {
		PersistableShoppingCartItem item = new PersistableShoppingCartItem();
		item.setProduct(product.getSku());
		item.setQuantity(quantity);
		return item;
	}
}
