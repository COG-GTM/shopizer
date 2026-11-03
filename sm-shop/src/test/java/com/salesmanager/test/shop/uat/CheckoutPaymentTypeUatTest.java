package com.salesmanager.test.shop.uat;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.HashMap;
import java.util.Map;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.SpringBootTest.WebEnvironment;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.test.context.junit.jupiter.SpringExtension;

import com.salesmanager.core.business.constants.Constants;
import com.salesmanager.core.model.order.orderstatus.OrderStatus;
import com.salesmanager.core.model.payments.PaymentType;
import com.salesmanager.shop.application.ShopApplication;
import com.salesmanager.shop.model.catalog.product.ReadableProduct;
import com.salesmanager.shop.model.customer.PersistableCustomer;
import com.salesmanager.shop.model.customer.address.Address;
import com.salesmanager.shop.model.order.ReadableOrderTotalSummary;
import com.salesmanager.shop.model.order.transaction.PersistablePayment;
import com.salesmanager.shop.model.order.v0.ReadableOrder;
import com.salesmanager.shop.model.order.v1.PersistableOrder;
import com.salesmanager.shop.model.order.v1.ReadableOrderConfirmation;
import com.salesmanager.shop.model.shoppingcart.PersistableShoppingCartItem;
import com.salesmanager.shop.model.shoppingcart.ReadableShoppingCart;
import com.salesmanager.shop.model.store.ReadableMerchantStore;
import com.salesmanager.shop.model.system.IntegrationModuleConfiguration;
import com.salesmanager.shop.store.security.AuthenticationRequest;
import com.salesmanager.shop.store.security.AuthenticationResponse;
import com.salesmanager.test.shop.common.ServicesTestSupport;

@SpringBootTest(classes = ShopApplication.class, webEnvironment = WebEnvironment.RANDOM_PORT)
@ExtendWith(SpringExtension.class)
public class CheckoutPaymentTypeUatTest extends ServicesTestSupport {

	@Test
	public void adminAndServicesAuthenticationJourneys() {
		ResponseEntity<AuthenticationResponse> login = testRestTemplate.postForEntity("/api/v1/private/login",
				new HttpEntity<>(new AuthenticationRequest("admin@shopizer.com", "password")),
				AuthenticationResponse.class);
		assertEquals(200, login.getStatusCode().value());
		assertNotNull(login.getBody());
		assertNotNull(login.getBody().getToken());

		HttpHeaders adminHeaders = bearerHeaders(login.getBody().getToken());
		ResponseEntity<String> privateResponse = testRestTemplate.exchange(
				"/api/v1/private/stores?store=" + Constants.DEFAULT_STORE, HttpMethod.GET,
				new HttpEntity<>(adminHeaders), String.class);
		assertEquals(200, privateResponse.getStatusCode().value());

		ResponseEntity<String> anonymousResponse = testRestTemplate.getForEntity(
				"/api/v1/private/stores?store=" + Constants.DEFAULT_STORE, String.class);
		assertTrue(isUnauthorized(anonymousResponse.getStatusCode().value()));

		HttpHeaders invalidHeaders = bearerHeaders("invalid-token");
		ResponseEntity<String> invalidResponse = testRestTemplate.exchange(
				"/api/v1/private/stores?store=" + Constants.DEFAULT_STORE, HttpMethod.GET,
				new HttpEntity<>(invalidHeaders), String.class);
		assertFalse(invalidResponse.getStatusCode().is2xxSuccessful(),
				"Invalid token unexpectedly succeeded: " + invalidResponse.getStatusCode());

		HttpHeaders validBasic = new HttpHeaders();
		validBasic.setBasicAuth("admin@shopizer.com", "password");
		validBasic.setContentType(MediaType.APPLICATION_JSON);
		ResponseEntity<String> servicesSuccess = testRestTemplate.exchange("/services/private/system/optin",
				HttpMethod.POST, new HttpEntity<>("{}", validBasic), String.class);
		assertTrue(servicesSuccess.getStatusCode().is2xxSuccessful());

		HttpHeaders invalidBasic = new HttpHeaders();
		invalidBasic.setBasicAuth("admin@shopizer.com", "invalid");
		invalidBasic.setContentType(MediaType.APPLICATION_JSON);
		ResponseEntity<String> servicesFailure = testRestTemplate.exchange("/services/private/system/optin",
				HttpMethod.POST, new HttpEntity<>("{}", invalidBasic), String.class);
		assertEquals(401, servicesFailure.getStatusCode().value());
	}

	@Test
	public void customerCanRegisterBrowseManageCartAndCheckout() {
		String customerEmail = "uat-" + System.currentTimeMillis() + "@example.test";
		PersistableCustomer customer = new PersistableCustomer();
		customer.setEmailAddress(customerEmail);
		customer.setPassword("clear123");
		customer.setRepeatPassword("clear123");
		customer.setGender("M");
		customer.setLanguage("en");
		customer.setStoreCode(Constants.DEFAULT_STORE);
		Address billing = address();
		customer.setBilling(billing);
		ResponseEntity<String> registration = testRestTemplate.postForEntity("/api/v1/customer/register",
				new HttpEntity<>(customer, getHeader()), String.class);
		assertTrue(registration.getStatusCode().is2xxSuccessful());

		ResponseEntity<AuthenticationResponse> login = testRestTemplate.postForEntity("/api/v1/customer/login",
				new HttpEntity<>(new AuthenticationRequest(customerEmail, "clear123")), AuthenticationResponse.class);
		assertEquals(200, login.getStatusCode().value());
		assertNotNull(login.getBody());
		HttpHeaders customerHeaders = bearerHeaders(login.getBody().getToken());

		ResponseEntity<String> profile = testRestTemplate.exchange(
				"/api/v1/auth/customer/profile?store=" + Constants.DEFAULT_STORE, HttpMethod.GET,
				new HttpEntity<>(customerHeaders), String.class);
		assertEquals(200, profile.getStatusCode().value());

		ResponseEntity<ReadableMerchantStore> store = testRestTemplate.getForEntity(
				"/api/v1/store/" + Constants.DEFAULT_STORE, ReadableMerchantStore.class);
		assertEquals(200, store.getStatusCode().value());
		ResponseEntity<String> categories = testRestTemplate.getForEntity(
				"/api/v1/category?store=" + Constants.DEFAULT_STORE, String.class);
		assertEquals(200, categories.getStatusCode().value());

		String sku = "uatCheckout" + System.currentTimeMillis();
		ReadableProduct product = sampleProduct(sku);
		assertNotNull(product);
		ResponseEntity<String> productList = testRestTemplate.getForEntity(
				"/api/v1/products?store=" + Constants.DEFAULT_STORE, String.class);
		assertEquals(200, productList.getStatusCode().value());
		ResponseEntity<ReadableProduct> productDetails = testRestTemplate.getForEntity(
				"/api/v2/product/" + product.getSku() + "?store=" + Constants.DEFAULT_STORE, ReadableProduct.class);
		assertEquals(200, productDetails.getStatusCode().value());
		assertNotNull(productDetails.getBody());

		PersistableShoppingCartItem item = new PersistableShoppingCartItem();
		item.setProduct(product.getSku());
		item.setQuantity(1);
		ResponseEntity<ReadableShoppingCart> cartResponse = testRestTemplate.postForEntity(
				"/api/v1/cart?store=" + Constants.DEFAULT_STORE, new HttpEntity<>(item), ReadableShoppingCart.class);
		assertEquals(201, cartResponse.getStatusCode().value());
		assertNotNull(cartResponse.getBody());
		String cartCode = cartResponse.getBody().getCode();

		item.setQuantity(2);
		ResponseEntity<ReadableShoppingCart> updatedCart = testRestTemplate.exchange(
				"/api/v1/cart/" + cartCode + "?store=" + Constants.DEFAULT_STORE,
				HttpMethod.PUT, new HttpEntity<>(item), ReadableShoppingCart.class);
		assertEquals(201, updatedCart.getStatusCode().value());
		ResponseEntity<ReadableShoppingCart> readCart = testRestTemplate.getForEntity(
				"/api/v1/cart/" + cartCode + "?store=" + Constants.DEFAULT_STORE, ReadableShoppingCart.class);
		assertEquals(200, readCart.getStatusCode().value());
		assertEquals(2, readCart.getBody().getQuantity());

		configureMoneyOrder();
		String amount = testRestTemplate.getForObject(
				"/api/v1/cart/" + cartCode + "/total?store=" + Constants.DEFAULT_STORE,
				ReadableOrderTotalSummary.class).getTotal().replaceAll("[^\\d.]", "");
		ResponseEntity<String> spoofResponse = testRestTemplate.postForEntity(
				"/api/v1/auth/cart/" + cartCode + "/checkout?store=" + Constants.DEFAULT_STORE,
				new HttpEntity<>(checkoutOrder("CREDITCARD", amount), customerHeaders), String.class);
		assertTrue(!spoofResponse.getStatusCode().is2xxSuccessful(),
				"Payment type spoof unexpectedly succeeded: " + spoofResponse.getBody());

		ReadableShoppingCart validCart = addCartItem(product.getSku());
		String validAmount = testRestTemplate.getForObject(
				"/api/v1/cart/" + validCart.getCode() + "/total?store=" + Constants.DEFAULT_STORE,
				ReadableOrderTotalSummary.class).getTotal().replaceAll("[^\\d.]", "");
		ResponseEntity<ReadableOrderConfirmation> checkout = testRestTemplate.postForEntity(
				"/api/v1/auth/cart/" + validCart.getCode() + "/checkout?store=" + Constants.DEFAULT_STORE,
				new HttpEntity<>(checkoutOrder("MONEYORDER", validAmount), customerHeaders),
				ReadableOrderConfirmation.class);
		assertEquals(200, checkout.getStatusCode().value(), String.valueOf(checkout.getBody()));
		assertNotNull(checkout.getBody());

		ReadableOrder order = testRestTemplate.exchange(
				"/api/v1/auth/orders/" + checkout.getBody().getId() + "?store=" + Constants.DEFAULT_STORE,
				HttpMethod.GET, new HttpEntity<>(customerHeaders), ReadableOrder.class).getBody();
		assertNotNull(order);
		assertEquals(OrderStatus.ORDERED, order.getOrderStatus());
		assertEquals(PaymentType.MONEYORDER, order.getPaymentType());
	}

	@Test
	public void actuatorHealthAndApiDocumentationAreAvailable() {
		ResponseEntity<String> health = testRestTemplate.getForEntity("/actuator/health", String.class);
		assertEquals(200, health.getStatusCode().value());
		assertTrue(health.getBody().contains("\"status\":\"UP\""), health.getBody());

		ResponseEntity<String> apiDocs = testRestTemplate.getForEntity("/v2/api-docs", String.class);
		assertEquals(200, apiDocs.getStatusCode().value());
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

	private ReadableShoppingCart addCartItem(String sku) {
		PersistableShoppingCartItem item = new PersistableShoppingCartItem();
		item.setProduct(sku);
		item.setQuantity(1);
		ResponseEntity<ReadableShoppingCart> response = testRestTemplate.postForEntity(
				"/api/v1/cart?store=" + Constants.DEFAULT_STORE, new HttpEntity<>(item), ReadableShoppingCart.class);
		assertEquals(201, response.getStatusCode().value());
		assertNotNull(response.getBody());
		return response.getBody();
	}

	private PersistableOrder checkoutOrder(String paymentType, String amount) {
		PersistablePayment payment = new PersistablePayment();
		payment.setPaymentModule("moneyorder");
		payment.setPaymentType(paymentType);
		payment.setTransactionType("AUTHORIZECAPTURE");
		payment.setAmount(amount);
		PersistableOrder order = new PersistableOrder();
		order.setPayment(payment);
		order.setCurrency("USD");
		return order;
	}

	private static Address address() {
		Address address = new Address();
		address.setFirstName("UAT");
		address.setLastName("Customer");
		address.setAddress("1 Test Street");
		address.setCity("Testville");
		address.setCountry("BE");
		address.setBillingAddress(true);
		return address;
	}

	private static HttpHeaders bearerHeaders(String token) {
		HttpHeaders headers = new HttpHeaders();
		headers.setContentType(MediaType.APPLICATION_JSON);
		headers.setBearerAuth(token);
		return headers;
	}

	private static boolean isUnauthorized(int status) {
		return status == 401 || status == 403;
	}
}
