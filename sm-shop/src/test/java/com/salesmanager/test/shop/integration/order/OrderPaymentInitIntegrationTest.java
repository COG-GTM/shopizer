package com.salesmanager.test.shop.integration.order;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.math.BigDecimal;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.TestInstance;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;

import com.salesmanager.shop.model.catalog.product.ReadableProduct;
import com.salesmanager.shop.model.customer.PersistableCustomer;
import com.salesmanager.shop.model.customer.address.Address;
import com.salesmanager.shop.model.order.transaction.PersistablePayment;
import com.salesmanager.shop.model.shoppingcart.PersistableShoppingCartItem;
import com.salesmanager.shop.model.shoppingcart.ReadableShoppingCart;
import com.salesmanager.shop.store.security.AuthenticationRequest;
import com.salesmanager.shop.store.security.AuthenticationResponse;
import com.salesmanager.test.shop.common.ServicesTestSupport;

@TestInstance(TestInstance.Lifecycle.PER_CLASS)
public class OrderPaymentInitIntegrationTest extends ServicesTestSupport {

	private ReadableShoppingCart anonymousCart;
	private ReadableShoppingCart customerCart;
	private HttpHeaders customerHeaders;

	@BeforeAll
	public void setUpCarts() {
		ReadableProduct product = sampleProduct("payment-init-test");
		anonymousCart = createCart(product.getSku());
		ReadableShoppingCart cartToMerge = createCart(product.getSku());

		PersistableCustomer customer = new PersistableCustomer();
		customer.setEmailAddress("payment-init@test.com");
		customer.setPassword("clear123");
		customer.setGender("M");
		customer.setLanguage("en");
		customer.setStoreCode("DEFAULT");
		Address billing = new Address();
		billing.setFirstName("Payment");
		billing.setLastName("Tester");
		billing.setCountry("BE");
		customer.setBilling(billing);
		ResponseEntity<String> registration = testRestTemplate.postForEntity("/api/v1/customer/register",
				new HttpEntity<>(customer, getHeader()), String.class);
		assertEquals(HttpStatus.OK, registration.getStatusCode(), registration.getBody());

		ResponseEntity<AuthenticationResponse> login = testRestTemplate.postForEntity("/api/v1/customer/login",
				new AuthenticationRequest(customer.getEmailAddress(), "clear123"), AuthenticationResponse.class);
		assertEquals(HttpStatus.OK, login.getStatusCode());
		assertNotNull(login.getBody());
		assertNotNull(login.getBody().getToken());
		customerHeaders = new HttpHeaders();
		customerHeaders.setContentType(MediaType.APPLICATION_JSON);
		customerHeaders.setBearerAuth(login.getBody().getToken());

		ResponseEntity<ReadableShoppingCart> merged = testRestTemplate.exchange(
				"/api/v1/auth/customer/cart?cart=" + cartToMerge.getCode(), HttpMethod.GET,
				new HttpEntity<>(customerHeaders), ReadableShoppingCart.class);
		assertEquals(HttpStatus.OK, merged.getStatusCode());
		customerCart = merged.getBody();
		assertNotNull(customerCart);
		assertTrue(anonymousCart.getTotal().compareTo(BigDecimal.ONE) > 0);
		assertTrue(customerCart.getTotal().compareTo(BigDecimal.ONE) > 0);
	}

	@ParameterizedTest
	@ValueSource(booleans = { false, true })
	public void rejectsZeroAmount(boolean authenticated) {
		assertValidationError(init(authenticated, "0.00"), "Payment.amount must be greater than 0");
	}

	@ParameterizedTest
	@ValueSource(booleans = { false, true })
	public void rejectsAmountBelowCartTotal(boolean authenticated) {
		assertValidationError(init(authenticated, "0.01"), "is lower than the cart total");
	}

	@ParameterizedTest
	@ValueSource(booleans = { false, true })
	public void acceptsAmountAtCartTotal(boolean authenticated) {
		assertPassesValidation(init(authenticated, cart(authenticated).getTotal().toPlainString()), authenticated);
	}

	@ParameterizedTest
	@ValueSource(booleans = { false, true })
	public void acceptsAmountAboveCartTotal(boolean authenticated) {
		assertPassesValidation(init(authenticated,
				cart(authenticated).getTotal().add(BigDecimal.TEN).toPlainString()), authenticated);
	}

	@ParameterizedTest
	@ValueSource(booleans = { false, true })
	public void preservesUnknownCartStatus(boolean authenticated) {
		ResponseEntity<String> response = init(authenticated, "unknown-payment-init-cart", "125.00");
		assertEquals(authenticated ? HttpStatus.SERVICE_UNAVAILABLE : HttpStatus.NOT_FOUND,
				response.getStatusCode(), response.getBody());
	}

	private ReadableShoppingCart createCart(String sku) {
		PersistableShoppingCartItem item = new PersistableShoppingCartItem();
		item.setProduct(sku);
		item.setQuantity(1);
		ResponseEntity<ReadableShoppingCart> response = testRestTemplate.postForEntity("/api/v1/cart",
				item, ReadableShoppingCart.class);
		assertEquals(HttpStatus.CREATED, response.getStatusCode());
		assertNotNull(response.getBody());
		return response.getBody();
	}

	private ReadableShoppingCart cart(boolean authenticated) {
		return authenticated ? customerCart : anonymousCart;
	}

	private ResponseEntity<String> init(boolean authenticated, String amount) {
		return init(authenticated, cart(authenticated).getCode(), amount);
	}

	private ResponseEntity<String> init(boolean authenticated, String code, String amount) {
		PersistablePayment payment = new PersistablePayment();
		payment.setPaymentModule("stripe3");
		payment.setPaymentType("CREDITCARD");
		payment.setTransactionType("AUTHORIZECAPTURE");
		payment.setAmount(amount);
		HttpHeaders headers = authenticated ? customerHeaders : new HttpHeaders();
		return testRestTemplate.postForEntity("/api/v1/" + (authenticated ? "auth/" : "")
				+ "cart/" + code + "/payment/init", new HttpEntity<>(payment, headers), String.class);
	}

	private void assertValidationError(ResponseEntity<String> response, String message) {
		assertEquals(HttpStatus.BAD_REQUEST, response.getStatusCode(), response.getBody());
		assertNotNull(response.getBody());
		assertTrue(response.getBody().contains(message), response.getBody());
	}

	private void assertPassesValidation(ResponseEntity<String> response, boolean authenticated) {
		System.out.println("Payment init downstream status (authenticated=" + authenticated + "): "
				+ response.getStatusCodeValue());
		assertNotEquals(HttpStatus.BAD_REQUEST, response.getStatusCode(), response.getBody());
		assertFalse(response.getStatusCode().is4xxClientError(), response.getBody());
		if (response.getBody() != null) {
			assertFalse(response.getBody().contains("Payment.amount"), response.getBody());
		}
	}
}
