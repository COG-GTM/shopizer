package com.salesmanager.test.shop.uat;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.math.BigDecimal;

import org.junit.jupiter.api.Test;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import com.salesmanager.shop.model.customer.PersistableCustomer;
import com.salesmanager.shop.model.shoppingcart.PersistableShoppingCartItem;
import com.salesmanager.shop.model.shoppingcart.ReadableShoppingCart;
import com.salesmanager.shop.store.security.AuthenticationRequest;
import com.salesmanager.shop.store.security.AuthenticationResponse;
import com.salesmanager.test.shop.common.CheckoutJourneySupport;

/**
 * Black box storefront/admin journeys around checkout (PR #185), using only public REST endpoints.
 */
class StorefrontCheckoutJourneyUatTest extends CheckoutJourneySupport {

	private ResponseEntity<String> get(String url, HttpHeaders headers) {
		return testRestTemplate.exchange(url, HttpMethod.GET, new HttpEntity<>(headers), String.class);
	}

	private static void assertRefused(ResponseEntity<String> response) {
		assertFalse(response.getStatusCode().is2xxSuccessful(), String.valueOf(response.getStatusCode()));
		assertFalse(response.getBody() != null && response.getBody().contains("\"paymentModules\""));
	}

	private String registerAndLogin() {
		String email = unique("uat") + "@test.com";
		PersistableCustomer customer = new PersistableCustomer();
		customer.setEmailAddress(email);
		customer.setUserName(email);
		customer.setPassword("clear123");
		customer.setFirstName("Uat");
		customer.setLastName("Buyer");
		customer.setLanguage("en");
		customer.setStoreCode("DEFAULT");
		customer.setBilling(quebecAddress());
		customer.setDelivery(quebecAddress());
		ResponseEntity<String> registered = testRestTemplate.postForEntity("/api/v1/customer/register",
				new HttpEntity<>(customer, jsonHeaders()), String.class);
		assertEquals(HttpStatus.OK, registered.getStatusCode(), registered.getBody());
		ResponseEntity<AuthenticationResponse> login = testRestTemplate.postForEntity("/api/v1/customer/login",
				new HttpEntity<>(new AuthenticationRequest(email, "clear123"), jsonHeaders()),
				AuthenticationResponse.class);
		assertEquals(HttpStatus.OK, login.getStatusCode());
		assertNotNull(login.getBody().getToken());
		return login.getBody().getToken();
	}

	@Test
	void adminLoginGrantsPrivateApiAndMissingOrBadTokenIsRefused() {
		assertEquals(HttpStatus.OK, get("/api/v1/private/modules/payment", getHeader()).getStatusCode());
		assertRefused(get("/api/v1/private/modules/payment", jsonHeaders()));
		assertRefused(get("/api/v1/private/modules/payment", bearer("not.a.jwt")));
	}

	@Test
	void customerRegistersLogsInAndReadsProfile() {
		String token = registerAndLogin();
		ResponseEntity<String> profile = get("/api/v1/auth/customer/profile", bearer(token));
		assertEquals(HttpStatus.OK, profile.getStatusCode());
		assertTrue(profile.getBody().contains("\"emailAddress\""));
	}

	@Test
	void shopperBrowsesStoreCategoriesAndProducts() {
		String sku = createProduct(unique("uatb"), true);
		assertEquals(HttpStatus.OK, get("/api/v1/store/DEFAULT", jsonHeaders()).getStatusCode());
		assertEquals(HttpStatus.OK, get("/api/v1/category", jsonHeaders()).getStatusCode());
		ResponseEntity<String> products = get("/api/v1/products", jsonHeaders());
		assertEquals(HttpStatus.OK, products.getStatusCode());
		ResponseEntity<String> detail = get("/api/v2/product/" + sku, jsonHeaders());
		assertEquals(HttpStatus.OK, detail.getStatusCode());
		assertTrue(detail.getBody().contains("\"sku\":\"" + sku + "\""));
	}

	@Test
	void shopperCreatesUpdatesAndReadsCart() {
		String sku = createProduct(unique("uatc"), true);
		ReadableShoppingCart cart = createCart(sku, 1);
		PersistableShoppingCartItem item = new PersistableShoppingCartItem();
		item.setProduct(sku);
		item.setQuantity(3);
		ResponseEntity<ReadableShoppingCart> updated = testRestTemplate.exchange("/api/v1/cart/" + cart.getCode(),
				HttpMethod.PUT, new HttpEntity<>(item, jsonHeaders()), ReadableShoppingCart.class);
		assertEquals(HttpStatus.CREATED, updated.getStatusCode());
		ResponseEntity<ReadableShoppingCart> read = testRestTemplate.exchange("/api/v1/cart/" + cart.getCode(),
				HttpMethod.GET, new HttpEntity<>(jsonHeaders()), ReadableShoppingCart.class);
		assertEquals(HttpStatus.OK, read.getStatusCode());
		assertEquals(3, read.getBody().getQuantity());
		assertEquals(0, cart.getSubtotal().multiply(new BigDecimal(3)).compareTo(read.getBody().getSubtotal()));
	}

	@Test
	void customerChecksOutVirtualCartAndReadsOrderBack() {
		enableMoneyOrder();
		String token = registerAndLogin();
		ReadableShoppingCart cart = createCart(createProduct(unique("uatv"), false), 1);
		ResponseEntity<String> checkout = testRestTemplate.postForEntity(
				"/api/v1/auth/cart/" + cart.getCode() + "/checkout",
				new HttpEntity<>(order(cart.getTotal(), null), bearer(token)), String.class);
		assertEquals(HttpStatus.OK, checkout.getStatusCode(), checkout.getBody());
		String id = checkout.getBody().replaceAll("(?s).*?\"id\":(\\d+).*", "$1");
		ResponseEntity<String> order = get("/api/v1/auth/orders/" + id, bearer(token));
		assertEquals(HttpStatus.OK, order.getStatusCode(), order.getBody());
		assertTrue(order.getBody().contains("\"id\":" + id));
	}

	@Test
	void guestCannotCheckOutShippableCartToUnservedDestinationWithoutShippingQuote() {
		enableMoneyOrder();
		ReadableShoppingCart cart = createCart(createProduct(unique("uats"), true), 1);
		ResponseEntity<String> checkout = guestCheckout(cart.getCode(),
				guestOrder(cart.getTotal(), null, newYorkAddress()));
		assertEquals(HttpStatus.BAD_REQUEST, checkout.getStatusCode(), checkout.getBody());
	}

	@Test
	void servicesEndpointsRequireValidBasicCredentials() {
		assertRefused(get("/services/private/DEFAULT/orders", jsonHeaders()));
		HttpHeaders bad = jsonHeaders();
		bad.setBasicAuth("nobody@test.com", "wrong");
		assertRefused(get("/services/private/DEFAULT/orders", bad));
	}

	@Test
	void healthIsUpAndApiDocsRespond() {
		ResponseEntity<String> health = get("/actuator/health", jsonHeaders());
		assertEquals(HttpStatus.OK, health.getStatusCode());
		assertTrue(health.getBody().contains("\"UP\""));
		assertEquals(HttpStatus.OK, get("/v2/api-docs", jsonHeaders()).getStatusCode());
	}
}
