package com.salesmanager.test.shop.uat;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import java.util.HashMap;
import java.util.Map;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.SpringBootTest.WebEnvironment;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.test.context.junit4.SpringRunner;

import com.fasterxml.jackson.databind.JsonNode;
import com.salesmanager.core.business.constants.Constants;
import com.salesmanager.core.model.customer.CustomerGender;
import com.salesmanager.shop.application.ShopApplication;
import com.salesmanager.shop.model.catalog.product.ReadableProduct;
import com.salesmanager.shop.model.catalog.product.product.PersistableProduct;
import com.salesmanager.shop.model.customer.PersistableCustomer;
import com.salesmanager.shop.model.customer.ReadableCustomer;
import com.salesmanager.shop.model.customer.address.Address;
import com.salesmanager.shop.model.shoppingcart.PersistableShoppingCartItem;
import com.salesmanager.shop.model.shoppingcart.ReadableShoppingCart;
import com.salesmanager.shop.model.store.ReadableMerchantStore;
import com.salesmanager.shop.store.security.AuthenticationRequest;
import com.salesmanager.shop.store.security.AuthenticationResponse;
import com.salesmanager.test.shop.common.ServicesTestSupport;

@SpringBootTest(classes = ShopApplication.class, webEnvironment = WebEnvironment.RANDOM_PORT)
@RunWith(SpringRunner.class)
public class StorefrontUserJourneyUatTest extends ServicesTestSupport {

	private static final String PASSWORD = "StorePass123";

	@Test
	public void adminCanListStoresAndUnauthenticatedRequestsAreRejected() {
		ResponseEntity<String> stores = testRestTemplate.exchange("/api/v1/private/stores", HttpMethod.GET,
				new HttpEntity<Void>(getHeader()), String.class);
		assertEquals(HttpStatus.OK, stores.getStatusCode());

		ResponseEntity<String> noToken = testRestTemplate.exchange("/api/v1/private/stores", HttpMethod.GET,
				new HttpEntity<Void>(new HttpHeaders()), String.class);
		assertTrue(noToken.getStatusCode() == HttpStatus.UNAUTHORIZED
				|| noToken.getStatusCode() == HttpStatus.FORBIDDEN);

		HttpHeaders badTokenHeaders = new HttpHeaders();
		badTokenHeaders.set("Authorization", "Bearer invalid-token");
		ResponseEntity<String> badToken = testRestTemplate.exchange("/api/v1/private/stores", HttpMethod.GET,
				new HttpEntity<Void>(badTokenHeaders), String.class);
		assertNotEquals(HttpStatus.OK, badToken.getStatusCode());
	}

	@Test
	public void customerCanRegisterLoginAndReadOwnProfile() {
		String email = newEmail();
		registerCustomer(Constants.DEFAULT_STORE, email);
		AuthenticationResponse login = login(Constants.DEFAULT_STORE, email);

		ResponseEntity<ReadableCustomer> profile = testRestTemplate.exchange(
				"/api/v1/auth/customer/profile?store=" + Constants.DEFAULT_STORE, HttpMethod.GET,
				new HttpEntity<Void>(bearer(login.getToken())), ReadableCustomer.class);

		assertEquals(HttpStatus.OK, profile.getStatusCode());
		assertEquals(email, profile.getBody().getEmailAddress());
	}

	@Test
	public void storefrontBrowseIncludesStoreCategoryProductAndProductDetail() {
		ReadableMerchantStore store = testRestTemplate.getForObject(
				"/api/v1/store/" + Constants.DEFAULT_STORE, ReadableMerchantStore.class);
		assertNotNull(store);
		assertEquals(Constants.DEFAULT_STORE, store.getCode());

		ResponseEntity<String> categories = testRestTemplate.getForEntity(
				"/api/v1/category?store=" + Constants.DEFAULT_STORE + "&lang=en", String.class);
		ResponseEntity<String> products = testRestTemplate.getForEntity(
				"/api/v1/products?store=" + Constants.DEFAULT_STORE + "&lang=en", String.class);
		assertEquals(HttpStatus.OK, categories.getStatusCode());
		assertEquals(HttpStatus.OK, products.getStatusCode());

		String sku = newSku("uat");
		ReadableProduct createdProduct = sampleProduct(sku);
		ResponseEntity<ReadableProduct> detail = testRestTemplate.getForEntity(
				"/api/v2/product/" + createdProduct.getSku() + "?store=" + Constants.DEFAULT_STORE + "&lang=en",
				ReadableProduct.class);

		assertEquals(HttpStatus.OK, detail.getStatusCode());
		assertEquals(createdProduct.getSku(), detail.getBody().getSku());
	}

	@Test
	public void guestCanCreateUpdateAndRetrieveCart() {
		String sku = newSku("cart");
		ReadableProduct product = sampleProduct(sku);

		PersistableShoppingCartItem item = cartItem(product.getSku(), 1);
		ResponseEntity<ReadableShoppingCart> created = testRestTemplate.postForEntity(
				"/api/v1/cart?store=" + Constants.DEFAULT_STORE,
				new HttpEntity<>(item), ReadableShoppingCart.class);
		assertEquals(HttpStatus.CREATED, created.getStatusCode());
		assertEquals(1, created.getBody().getQuantity());

		ResponseEntity<ReadableShoppingCart> updated = testRestTemplate.exchange(
				"/api/v1/cart/" + created.getBody().getCode() + "?store=" + Constants.DEFAULT_STORE,
				HttpMethod.PUT, new HttpEntity<>(cartItem(product.getSku(), 2)), ReadableShoppingCart.class);
		assertEquals(HttpStatus.CREATED, updated.getStatusCode());
		assertEquals(2, updated.getBody().getQuantity());

		ResponseEntity<ReadableShoppingCart> fetched = testRestTemplate.getForEntity(
				"/api/v1/cart/" + created.getBody().getCode() + "?store=" + Constants.DEFAULT_STORE,
				ReadableShoppingCart.class);
		assertEquals(HttpStatus.OK, fetched.getStatusCode());
		assertEquals(2, fetched.getBody().getQuantity());
	}

	@Test
	public void authenticatedCustomerCanCheckoutAndReadOrder() {
		String email = newEmail();
		registerCustomer(Constants.DEFAULT_STORE, email);
		AuthenticationResponse login = login(Constants.DEFAULT_STORE, email);

		String sku = newSku("order");
		ReadableProduct product = sampleProduct(sku);
		ResponseEntity<ReadableShoppingCart> cart = testRestTemplate.postForEntity(
				"/api/v1/cart?store=" + Constants.DEFAULT_STORE,
				new HttpEntity<>(cartItem(product.getSku(), 1)), ReadableShoppingCart.class);
		assertEquals(HttpStatus.CREATED, cart.getStatusCode());

		Map<String, Object> paymentModule = new HashMap<>();
		paymentModule.put("code", "moneyorder");
		paymentModule.put("active", true);
		paymentModule.put("defaultSelected", true);
		Map<String, String> integrationKeys = new HashMap<>();
		integrationKeys.put("address", "1 rue, Montreal");
		paymentModule.put("integrationKeys", integrationKeys);
		ResponseEntity<String> configuredPayment = testRestTemplate.postForEntity(
				"/api/v1/private/modules/payment?store=" + Constants.DEFAULT_STORE,
				new HttpEntity<>(paymentModule, getHeader()), String.class);
		assertEquals(HttpStatus.OK, configuredPayment.getStatusCode());

		Map<String, Object> payment = new HashMap<>();
		payment.put("paymentType", "MONEYORDER");
		payment.put("transactionType", "AUTHORIZECAPTURE");
		payment.put("paymentModule", "moneyorder");
		payment.put("amount", cart.getBody().getTotal().toString());
		Map<String, Object> order = new HashMap<>();
		order.put("currency", "CAD");
		order.put("customerAgreement", true);
		order.put("payment", payment);

		ResponseEntity<JsonNode> checkout = testRestTemplate.postForEntity(
				"/api/v1/auth/cart/" + cart.getBody().getCode() + "/checkout?store="
						+ Constants.DEFAULT_STORE + "&lang=en",
				new HttpEntity<>(order, bearer(login.getToken())), JsonNode.class);
		assertEquals(HttpStatus.OK, checkout.getStatusCode());
		long orderId = checkout.getBody().path("id").asLong();
		assertTrue(orderId > 0);

		ResponseEntity<String> fetchedOrder = testRestTemplate.exchange(
				"/api/v1/auth/orders/" + orderId + "?store=" + Constants.DEFAULT_STORE, HttpMethod.GET,
				new HttpEntity<Void>(bearer(login.getToken())), String.class);
		ResponseEntity<String> orders = testRestTemplate.exchange(
				"/api/v1/auth/orders?store=" + Constants.DEFAULT_STORE, HttpMethod.GET,
				new HttpEntity<Void>(bearer(login.getToken())), String.class);
		assertEquals(HttpStatus.OK, fetchedOrder.getStatusCode());
		assertEquals(HttpStatus.OK, orders.getStatusCode());
	}

	@Test
	public void servicePingUsesBasicAuthentication() {
		HttpHeaders validCredentials = new HttpHeaders();
		validCredentials.setBasicAuth("admin@shopizer.com", "password");
		ResponseEntity<String> valid = testRestTemplate.exchange(
				"/services/private/DEFAULT/ping", HttpMethod.GET, new HttpEntity<Void>(validCredentials),
				String.class);
		assertEquals(HttpStatus.NOT_FOUND, valid.getStatusCode());

		HttpHeaders invalidCredentials = new HttpHeaders();
		invalidCredentials.setBasicAuth("admin@shopizer.com", "wrong-password");
		ResponseEntity<String> invalid = testRestTemplate.exchange(
				"/services/private/DEFAULT/ping", HttpMethod.GET, new HttpEntity<Void>(invalidCredentials),
				String.class);
		assertEquals(HttpStatus.UNAUTHORIZED, invalid.getStatusCode());
	}

	@Test
	public void healthAndApiDocumentationAreAvailable() {
		ResponseEntity<JsonNode> health = testRestTemplate.getForEntity("/actuator/health", JsonNode.class);
		ResponseEntity<String> apiDocs = testRestTemplate.getForEntity("/v2/api-docs", String.class);

		assertEquals(HttpStatus.OK, health.getStatusCode());
		assertEquals("UP", health.getBody().path("status").asText());
		assertEquals(HttpStatus.OK, apiDocs.getStatusCode());
	}

	private void registerCustomer(String storeCode, String email) {
		PersistableCustomer customer = new PersistableCustomer();
		customer.setEmailAddress(email);
		customer.setPassword(PASSWORD);
		customer.setGender(CustomerGender.M.name());
		customer.setLanguage("en");
		Address billing = new Address();
		billing.setFirstName("uat");
		billing.setLastName("customer");
		billing.setCountry("BE");
		customer.setBilling(billing);
		customer.setStoreCode(storeCode);

		ResponseEntity<String> response = testRestTemplate.postForEntity(
				"/api/v1/customer/register?store=" + storeCode,
				new HttpEntity<>(customer, getHeader()), String.class);
		assertEquals(HttpStatus.OK, response.getStatusCode());
	}

	private AuthenticationResponse login(String storeCode, String email) {
		ResponseEntity<AuthenticationResponse> response = testRestTemplate.postForEntity(
				"/api/v1/customer/login?store=" + storeCode,
				new HttpEntity<>(new AuthenticationRequest(email, PASSWORD)), AuthenticationResponse.class);
		assertEquals(HttpStatus.OK, response.getStatusCode());
		assertNotNull(response.getBody());
		return response.getBody();
	}

	private HttpHeaders bearer(String token) {
		HttpHeaders headers = new HttpHeaders();
		headers.setContentType(MediaType.APPLICATION_JSON);
		headers.set("Authorization", "Bearer " + token);
		return headers;
	}

	private PersistableShoppingCartItem cartItem(String sku, int quantity) {
		PersistableShoppingCartItem item = new PersistableShoppingCartItem();
		item.setProduct(sku);
		item.setQuantity(quantity);
		return item;
	}

	private String newEmail() {
		return "uat." + nanoTimeSuffix(13) + "@example.com";
	}

	private String newSku(String prefix) {
		return prefix + nanoTimeSuffix(8);
	}

	private String nanoTimeSuffix(int length) {
		String value = Long.toUnsignedString(System.nanoTime(), 36);
		return value.substring(Math.max(0, value.length() - length));
	}
}
