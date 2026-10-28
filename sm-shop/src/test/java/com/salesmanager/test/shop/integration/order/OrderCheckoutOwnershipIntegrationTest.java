package com.salesmanager.test.shop.integration.order;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;
import static org.springframework.http.HttpMethod.GET;
import static org.springframework.http.HttpMethod.POST;
import static org.springframework.http.HttpStatus.CREATED;
import static org.springframework.http.HttpStatus.FORBIDDEN;
import static org.springframework.http.HttpStatus.NOT_FOUND;
import static org.springframework.http.HttpStatus.OK;
import static org.springframework.http.HttpStatus.UNAUTHORIZED;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.SpringBootTest.WebEnvironment;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.test.context.junit4.SpringRunner;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.salesmanager.core.business.constants.Constants;
import com.salesmanager.core.model.customer.CustomerGender;
import com.salesmanager.shop.application.ShopApplication;
import com.salesmanager.shop.model.customer.PersistableCustomer;
import com.salesmanager.shop.model.customer.address.Address;
import com.salesmanager.shop.model.order.ReadableOrderTotalSummary;
import com.salesmanager.shop.model.order.transaction.PersistablePayment;
import com.salesmanager.shop.model.order.v1.PersistableOrder;
import com.salesmanager.shop.model.order.v1.ReadableOrder;
import com.salesmanager.shop.model.order.v1.ReadableOrderConfirmation;
import com.salesmanager.shop.model.order.v1.ReadableOrderList;
import com.salesmanager.shop.model.shoppingcart.PersistableShoppingCartItem;
import com.salesmanager.shop.model.shoppingcart.ReadableShoppingCart;
import com.salesmanager.shop.model.system.IntegrationModuleConfiguration;
import com.salesmanager.shop.store.security.AuthenticationRequest;
import com.salesmanager.shop.store.security.AuthenticationResponse;
import com.salesmanager.test.shop.common.ServicesTestSupport;

@SpringBootTest(classes = ShopApplication.class, webEnvironment = WebEnvironment.RANDOM_PORT)
@RunWith(SpringRunner.class)
public class OrderCheckoutOwnershipIntegrationTest extends ServicesTestSupport {

	@Test
	public void otherCustomerCannotCheckoutOwnedCart() throws Exception {
		CustomerAccount owner = registerCustomer("owner");
		CustomerAccount intruder = registerCustomer("intruder");
		ReadableShoppingCart anonymousCart = createCart("owner-cart");
		ReadableShoppingCart claimedCart = claimCart(owner, anonymousCart);
		assertEquals(owner.id, claimedCart.getCustomer());

		PersistableOrder order = orderWithAmount("0");
		ResponseEntity<String> checkout = testRestTemplate.exchange(
				"/api/v1/auth/cart/" + claimedCart.getCode() + "/checkout?store=DEFAULT", POST,
				new HttpEntity<>(order, headers(intruder.token)), String.class);

		assertEquals(NOT_FOUND, checkout.getStatusCode());
		assertNotNull(checkout.getBody());
		JsonNode errorBody = new ObjectMapper().readTree(checkout.getBody());
		assertTrue(errorBody.has("errorCode"));
		assertTrue(errorBody.has("message"));
		ReadableShoppingCart stillOwned = claimCart(owner, anonymousCart);
		assertEquals(claimedCart.getCode(), stillOwned.getCode());
		assertEquals(claimedCart.getQuantity(), stillOwned.getQuantity());

		ResponseEntity<ReadableOrderList> orders = testRestTemplate.exchange("/api/v1/auth/orders", GET,
				new HttpEntity<>(headers(intruder.token)), ReadableOrderList.class);
		assertEquals(OK, orders.getStatusCode());
		assertTrue(orders.getBody().getOrders() == null || orders.getBody().getOrders().isEmpty());
	}

	@Test
	public void unknownCartCodeReturnsNotFound() {
		CustomerAccount customer = registerCustomer("unknown");
		String randomCode = String.format("%032x", System.nanoTime());
		ResponseEntity<ReadableOrderConfirmation> checkout = checkout(randomCode, orderWithAmount("0"), customer.token);

		assertEquals(NOT_FOUND, checkout.getStatusCode());
	}

	@Test
	public void ownerCanCheckoutOwnCart() {
		CustomerAccount owner = registerCustomer("owner-checkout");
		ReadableShoppingCart cart = claimCart(owner, createCart("owner-checkout-cart"));

		ResponseEntity<ReadableOrderConfirmation> checkout = checkout(cart.getCode(), orderFor(owner, cart), owner.token);

		assertEquals(OK, checkout.getStatusCode());
		assertNotNull(checkout.getBody());
		assertTrue(checkout.getBody().getId() > 0L);
		ResponseEntity<ReadableOrder> savedOrder = testRestTemplate.exchange(
				"/api/v1/auth/orders/" + checkout.getBody().getId(), GET,
				new HttpEntity<>(headers(owner.token)), ReadableOrder.class);
		assertEquals(OK, savedOrder.getStatusCode());
		assertEquals(checkout.getBody().getId(), savedOrder.getBody().getId());
	}

	@Test
	public void authenticatedCustomerCanCheckoutAnonymousCart() {
		CustomerAccount customer = registerCustomer("anonymous-checkout");
		ReadableShoppingCart cart = createCart("anonymous-checkout-cart");
		PersistableOrder order = orderWithAmount(publicCartTotal(cart));

		ResponseEntity<ReadableOrderConfirmation> checkout = checkout(cart.getCode(), order, customer.token);

		assertEquals(OK, checkout.getStatusCode());
		assertNotNull(checkout.getBody());
		assertTrue(checkout.getBody().getId() > 0L);
	}

	@Test
	public void checkoutWithoutTokenIsRejected() {
		CustomerAccount customer = registerCustomer("no-token");
		ReadableShoppingCart cart = claimCart(customer, createCart("no-token-cart"));
		HttpHeaders headers = new HttpHeaders();
		headers.setContentType(MediaType.APPLICATION_JSON);
		ResponseEntity<ReadableOrderConfirmation> checkout = testRestTemplate.exchange(
				"/api/v1/auth/cart/" + cart.getCode() + "/checkout?store=DEFAULT", POST,
				new HttpEntity<>(orderWithAmount("0"), headers), ReadableOrderConfirmation.class);

		assertTrue(checkout.getStatusCode().equals(UNAUTHORIZED) || checkout.getStatusCode().equals(FORBIDDEN));
	}

	private CustomerAccount registerCustomer(String name) {
		String email = name + "-" + System.nanoTime() + "@checkout-test.com";
		PersistableCustomer customer = new PersistableCustomer();
		customer.setEmailAddress(email);
		customer.setPassword("clear123");
		customer.setGender(CustomerGender.M.name());
		customer.setLanguage("en");
		customer.setStoreCode(Constants.DEFAULT_STORE);
		Address billing = new Address();
		billing.setFirstName(name);
		billing.setLastName("Checkout");
		billing.setCountry("BE");
		customer.setBilling(billing);

		ResponseEntity<PersistableCustomer> registered = testRestTemplate.postForEntity("/api/v1/customer/register",
				new HttpEntity<>(customer, getHeader()), PersistableCustomer.class);
		assertEquals(OK, registered.getStatusCode());
		assertNotNull(registered.getBody());

		ResponseEntity<AuthenticationResponse> login = testRestTemplate.postForEntity("/api/v1/customer/login",
				new HttpEntity<>(new AuthenticationRequest(email, "clear123")), AuthenticationResponse.class);
		assertEquals(OK, login.getStatusCode());
		assertNotNull(login.getBody());
		assertNotNull(login.getBody().getToken());
		return new CustomerAccount(registered.getBody().getId(), login.getBody().getToken());
	}

	private ReadableShoppingCart createCart(String productCode) {
		com.salesmanager.shop.model.catalog.product.ReadableProduct product = sampleProduct(
				productCode + System.nanoTime());
		PersistableShoppingCartItem item = new PersistableShoppingCartItem();
		item.setProduct(product.getSku());
		item.setQuantity(1);
		ResponseEntity<ReadableShoppingCart> cart = testRestTemplate.postForEntity("/api/v1/cart",
				new HttpEntity<>(item, getHeader()), ReadableShoppingCart.class);
		assertEquals(CREATED, cart.getStatusCode());
		assertNotNull(cart.getBody());
		return cart.getBody();
	}

	private ReadableShoppingCart claimCart(CustomerAccount customer, ReadableShoppingCart cart) {
		ResponseEntity<ReadableShoppingCart> claimed = testRestTemplate.exchange(
				"/api/v1/auth/customer/cart?cart=" + cart.getCode(), GET,
				new HttpEntity<>(headers(customer.token)), ReadableShoppingCart.class);
		assertEquals(OK, claimed.getStatusCode());
		assertNotNull(claimed.getBody());
		return claimed.getBody();
	}

	private PersistableOrder orderFor(CustomerAccount customer, ReadableShoppingCart cart) {
		ResponseEntity<ReadableOrderTotalSummary> total = testRestTemplate.exchange(
				"/api/v1/auth/cart/" + cart.getId() + "/total", GET,
				new HttpEntity<>(headers(customer.token)), ReadableOrderTotalSummary.class);
		if (total.getStatusCode() == OK && total.getBody() != null) {
			return orderWithAmount(numericAmount(total.getBody().getTotal()));
		}
		return orderWithAmount(publicCartTotal(cart));
	}

	private String publicCartTotal(ReadableShoppingCart cart) {
		ResponseEntity<ReadableOrderTotalSummary> total = testRestTemplate.getForEntity(
				"/api/v1/cart/" + cart.getCode() + "/total", ReadableOrderTotalSummary.class);
		assertEquals(OK, total.getStatusCode());
		assertNotNull(total.getBody());
		return numericAmount(total.getBody().getTotal());
	}

	private String numericAmount(String amount) {
		return amount.replaceAll("[^0-9.]", "");
	}

	private PersistableOrder orderWithAmount(String amount) {
		IntegrationModuleConfiguration paymentModule = new IntegrationModuleConfiguration();
		paymentModule.setCode("moneyorder");
		paymentModule.setActive(true);
		paymentModule.setDefaultSelected(true);
		paymentModule.getIntegrationKeys().put("address", "Checkout Test");
		ResponseEntity<Void> configured = testRestTemplate.postForEntity("/api/v1/private/modules/payment",
				new HttpEntity<>(paymentModule, getHeader()), Void.class);
		assertEquals(OK, configured.getStatusCode());

		PersistablePayment payment = new PersistablePayment();
		payment.setPaymentType("MONEYORDER");
		payment.setTransactionType("CAPTURE");
		payment.setPaymentModule("moneyorder");
		payment.setAmount(amount);
		PersistableOrder order = new PersistableOrder();
		order.setPayment(payment);
		order.setCurrency("USD");
		order.setCustomerAgreement(true);
		return order;
	}

	private ResponseEntity<ReadableOrderConfirmation> checkout(String code, PersistableOrder order, String token) {
		return testRestTemplate.exchange("/api/v1/auth/cart/" + code + "/checkout?store=DEFAULT", POST,
				new HttpEntity<>(order, headers(token)), ReadableOrderConfirmation.class);
	}

	private HttpHeaders headers(String token) {
		HttpHeaders headers = new HttpHeaders();
		headers.setContentType(MediaType.APPLICATION_JSON);
		headers.setBearerAuth(token);
		return headers;
	}

	private static class CustomerAccount {
		private final Long id;
		private final String token;

		private CustomerAccount(Long id, String token) {
			this.id = id;
			this.token = token;
		}
	}
}
