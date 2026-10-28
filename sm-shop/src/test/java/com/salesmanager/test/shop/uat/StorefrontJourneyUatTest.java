package com.salesmanager.test.shop.uat;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import java.util.Collections;

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

import com.salesmanager.core.business.constants.Constants;
import com.salesmanager.core.model.customer.CustomerGender;
import com.salesmanager.shop.application.ShopApplication;
import com.salesmanager.shop.model.catalog.product.ReadableProduct;
import com.salesmanager.shop.model.customer.PersistableCustomer;
import com.salesmanager.shop.model.customer.address.Address;
import com.salesmanager.shop.model.order.transaction.PersistablePayment;
import com.salesmanager.shop.model.order.v1.PersistableOrder;
import com.salesmanager.shop.model.order.v1.ReadableOrder;
import com.salesmanager.shop.model.order.v1.ReadableOrderConfirmation;
import com.salesmanager.shop.model.shoppingcart.PersistableShoppingCartItem;
import com.salesmanager.shop.model.shoppingcart.ReadableShoppingCart;
import com.salesmanager.shop.store.security.AuthenticationRequest;
import com.salesmanager.shop.store.security.AuthenticationResponse;
import com.salesmanager.test.shop.common.ServicesTestSupport;

@SpringBootTest(classes = ShopApplication.class, webEnvironment = WebEnvironment.RANDOM_PORT)
@RunWith(SpringRunner.class)
public class StorefrontJourneyUatTest extends ServicesTestSupport {

	@Test
	public void customerCanBrowseCartAndAttemptCheckout() {
		String suffix = Long.toHexString(System.nanoTime());
		ReadableProduct product = sampleProduct("uat" + suffix);
		assertNotNull(product);

		assertEquals(HttpStatus.OK, testRestTemplate.getForEntity("/api/v1/store/DEFAULT", String.class)
				.getStatusCode());
		assertEquals(HttpStatus.OK, testRestTemplate.getForEntity("/api/v1/category?store=DEFAULT", String.class)
				.getStatusCode());
		assertEquals(HttpStatus.OK, testRestTemplate.getForEntity("/api/v1/products?store=DEFAULT", String.class)
				.getStatusCode());
		assertEquals(HttpStatus.OK, testRestTemplate.getForEntity("/api/v2/product/" + product.getSku(),
				String.class).getStatusCode());

		String customerEmail = "journey-customer-" + suffix + "@example.com";
		PersistableCustomer customer = new PersistableCustomer();
		customer.setEmailAddress(customerEmail);
		customer.setPassword("clear123");
		customer.setGender(CustomerGender.M.name());
		customer.setLanguage("en");
		customer.setStoreCode(Constants.DEFAULT_STORE);
		Address billing = new Address();
		billing.setFirstName("Journey");
		billing.setLastName("Customer");
		billing.setAddress("1 Main Street");
		billing.setCity("Los Angeles");
		billing.setCountry("US");
		billing.setStateProvince("CA");
		customer.setBilling(billing);

		ResponseEntity<String> registration = testRestTemplate.postForEntity("/api/v1/customer/register",
				new HttpEntity<>(customer), String.class);
		assertEquals(HttpStatus.OK, registration.getStatusCode());

		ResponseEntity<AuthenticationResponse> login = testRestTemplate.postForEntity("/api/v1/customer/login",
				new HttpEntity<>(new AuthenticationRequest(customerEmail, "clear123")),
				AuthenticationResponse.class);
		assertEquals(HttpStatus.OK, login.getStatusCode());
		assertNotNull(login.getBody());
		assertNotNull(login.getBody().getToken());
		HttpHeaders customerHeaders = bearerHeaders(login.getBody().getToken());

		assertEquals(HttpStatus.OK, testRestTemplate.exchange("/api/v1/auth/customer/profile?store=DEFAULT",
				HttpMethod.GET, new HttpEntity<>(customerHeaders), String.class).getStatusCode());

		PersistableShoppingCartItem item = new PersistableShoppingCartItem();
		item.setProduct(product.getSku());
		item.setQuantity(1);
		ResponseEntity<ReadableShoppingCart> cart = testRestTemplate.postForEntity("/api/v1/cart/",
				new HttpEntity<>(item, customerHeaders), ReadableShoppingCart.class);
		assertEquals(HttpStatus.CREATED, cart.getStatusCode());
		assertNotNull(cart.getBody());

		item.setQuantity(2);
		ResponseEntity<ReadableShoppingCart> updatedCart = testRestTemplate.exchange(
				"/api/v1/cart/" + cart.getBody().getCode(), HttpMethod.PUT,
				new HttpEntity<>(item, customerHeaders), ReadableShoppingCart.class);
		assertEquals(HttpStatus.CREATED, updatedCart.getStatusCode());
		assertEquals(HttpStatus.OK, testRestTemplate.getForEntity(
				"/api/v1/cart/" + cart.getBody().getCode(), String.class).getStatusCode());

		PersistableOrder order = new PersistableOrder();
		order.setCurrency("USD");
		order.setCustomerAgreement(true);
		PersistablePayment payment = new PersistablePayment();
		payment.setPaymentModule("moneyorder");
		payment.setPaymentType("MONEYORDER");
		payment.setTransactionType("AUTHORIZE");
		payment.setAmount("0");
		order.setPayment(payment);

		ResponseEntity<ReadableOrderConfirmation> checkout = testRestTemplate.exchange(
				"/api/v1/auth/cart/" + cart.getBody().getCode() + "/checkout?store=DEFAULT", HttpMethod.POST,
				new HttpEntity<>(order, customerHeaders), ReadableOrderConfirmation.class);
		assertTrue("Checkout returned unexpected status " + checkout.getStatusCode(),
				checkout.getStatusCode() == HttpStatus.OK || checkout.getStatusCode() == HttpStatus.SERVICE_UNAVAILABLE);
		if (checkout.getStatusCode() == HttpStatus.OK) {
			assertNotNull(checkout.getBody());
			assertNotNull(checkout.getBody().getId());
			ResponseEntity<ReadableOrder> orderReadback = testRestTemplate.exchange(
					"/api/v1/auth/orders/" + checkout.getBody().getId() + "?store=DEFAULT", HttpMethod.GET,
					new HttpEntity<>(customerHeaders), ReadableOrder.class);
			assertEquals(HttpStatus.OK, orderReadback.getStatusCode());
			assertNotNull(orderReadback.getBody());
		}
	}

	@Test
	public void legacyPrivateServicesRequireValidBasicAuthenticationAndPublicDocsAreAvailable() {
		HttpHeaders validBasic = new HttpHeaders();
		validBasic.setBasicAuth("admin@shopizer.com", "password");
		ResponseEntity<String> validServices = testRestTemplate.postForEntity(
				"/services/private/system/optin", new HttpEntity<>("{}", validBasic), String.class);
		assertEquals(HttpStatus.OK, validServices.getStatusCode());

		HttpHeaders invalidBasic = new HttpHeaders();
		invalidBasic.setBasicAuth("admin@shopizer.com", "incorrect-password");
		ResponseEntity<String> invalidServices = testRestTemplate.postForEntity(
				"/services/private/system/optin", new HttpEntity<>("{}", invalidBasic), String.class);
		assertEquals(HttpStatus.UNAUTHORIZED, invalidServices.getStatusCode());

		ResponseEntity<String> health = testRestTemplate.getForEntity("/actuator/health", String.class);
		assertEquals(HttpStatus.OK, health.getStatusCode());
		assertTrue(health.getBody().contains("\"status\":\"UP\""));

		assertEquals(HttpStatus.OK, testRestTemplate.getForEntity("/v2/api-docs", String.class).getStatusCode());
	}

	private HttpHeaders bearerHeaders(String token) {
		HttpHeaders headers = new HttpHeaders();
		headers.setBearerAuth(token);
		return headers;
	}
}
