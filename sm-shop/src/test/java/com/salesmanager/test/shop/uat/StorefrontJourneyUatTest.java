package com.salesmanager.test.shop.uat;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import java.math.BigDecimal;
import java.util.UUID;

import com.fasterxml.jackson.databind.JsonNode;
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

import com.salesmanager.core.business.constants.Constants;
import com.salesmanager.shop.application.ShopApplication;
import com.salesmanager.shop.model.catalog.product.ReadableProduct;
import com.salesmanager.shop.model.customer.PersistableCustomer;
import com.salesmanager.shop.model.customer.address.Address;
import com.salesmanager.shop.model.order.transaction.PersistablePayment;
import com.salesmanager.shop.model.order.v1.PersistableOrder;
import com.salesmanager.shop.model.order.v1.ReadableOrderConfirmation;
import com.salesmanager.shop.model.shoppingcart.PersistableShoppingCartItem;
import com.salesmanager.shop.model.shoppingcart.ReadableShoppingCart;
import com.salesmanager.shop.model.system.IntegrationModuleConfiguration;
import com.salesmanager.shop.store.security.AuthenticationRequest;
import com.salesmanager.shop.store.security.AuthenticationResponse;
import com.salesmanager.test.shop.common.ServicesTestSupport;

@SpringBootTest(classes = ShopApplication.class, webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@RunWith(SpringRunner.class)
public class StorefrontJourneyUatTest extends ServicesTestSupport {

	@Test
	public void administratorLoginAndPrivateCustomersAuthorization() {
		AuthenticationRequest credentials = new AuthenticationRequest("admin@shopizer.com", "password");
		ResponseEntity<AuthenticationResponse> login = testRestTemplate.postForEntity(
				"/api/v1/private/login", new HttpEntity<>(credentials, jsonHeaders()), AuthenticationResponse.class);
		assertEquals(HttpStatus.OK, login.getStatusCode());
		assertNotNull(login.getBody());
		assertNotNull(login.getBody().getToken());

		ResponseEntity<String> customers = testRestTemplate.exchange("/api/v1/private/customers?store=DEFAULT",
				HttpMethod.GET, new HttpEntity<>(authorizedHeaders(login.getBody().getToken())), String.class);
		assertEquals(HttpStatus.OK, customers.getStatusCode());

		ResponseEntity<String> anonymous = testRestTemplate.exchange("/api/v1/private/customers?store=DEFAULT",
				HttpMethod.GET, new HttpEntity<>(jsonHeaders()), String.class);
		assertUnauthorized(anonymous.getStatusCode());

		HttpHeaders invalidHeaders = jsonHeaders();
		invalidHeaders.setBearerAuth("not-a-valid-token");
		ResponseEntity<String> invalid = testRestTemplate.exchange("/api/v1/private/customers?store=DEFAULT",
				HttpMethod.GET, new HttpEntity<>(invalidHeaders), String.class);
		assertFalse("Invalid bearer token was accepted: " + invalid.getStatusCode(),
				invalid.getStatusCode().is2xxSuccessful());
	}

	@Test
	public void customerCanRegisterLoginAndReadProfile() {
		CustomerSession customer = registerAndLoginCustomer();

		ResponseEntity<JsonNode> profile = testRestTemplate.exchange("/api/v1/auth/customer/profile?store=DEFAULT",
				HttpMethod.GET, new HttpEntity<>(authorizedHeaders(customer.token)), JsonNode.class);

		assertEquals(HttpStatus.OK, profile.getStatusCode());
		assertEquals(customer.email, profile.getBody().get("emailAddress").asText());
	}

	@Test
	public void storefrontListsStoreCategoriesProductsAndProductDetails() {
		ResponseEntity<JsonNode> store = testRestTemplate.getForEntity("/api/v1/store/DEFAULT?store=DEFAULT",
				JsonNode.class);
		assertEquals(HttpStatus.OK, store.getStatusCode());
		assertEquals("DEFAULT", store.getBody().get("code").asText());

		ResponseEntity<String> categories = testRestTemplate.getForEntity("/api/v1/category?store=DEFAULT", String.class);
		assertEquals(HttpStatus.OK, categories.getStatusCode());

		ResponseEntity<String> products = testRestTemplate.getForEntity("/api/v1/products?store=DEFAULT", String.class);
		assertEquals(HttpStatus.OK, products.getStatusCode());

		ReadableProduct created = sampleProduct(uniqueCode());
		ResponseEntity<ReadableProduct> byCode = testRestTemplate.getForEntity(
				"/api/v2/product/" + created.getSku() + "?store=DEFAULT", ReadableProduct.class);
		assertEquals(HttpStatus.OK, byCode.getStatusCode());
		assertEquals(created.getSku(), byCode.getBody().getSku());
	}

	@Test
	public void cartCanBeCreatedUpdatedAndReadWithRecalculatedTotal() {
		ReadableProduct product = sampleProduct(uniqueCode());
		PersistableShoppingCartItem item = new PersistableShoppingCartItem();
		item.setProduct(product.getSku());
		item.setQuantity(1);
		ResponseEntity<ReadableShoppingCart> created = testRestTemplate.postForEntity(
				"/api/v1/cart?store=DEFAULT", new HttpEntity<>(item, jsonHeaders()), ReadableShoppingCart.class);
		assertEquals(HttpStatus.CREATED, created.getStatusCode());
		assertNotNull(created.getBody());
		BigDecimal unitCartTotal = created.getBody().getTotal();

		item.setQuantity(2);
		ResponseEntity<ReadableShoppingCart> updated = testRestTemplate.exchange(
				"/api/v1/cart/" + created.getBody().getCode() + "?store=DEFAULT", HttpMethod.PUT,
				new HttpEntity<>(item, jsonHeaders()), ReadableShoppingCart.class);
		assertEquals(HttpStatus.CREATED, updated.getStatusCode());
		assertEquals(2, updated.getBody().getQuantity());

		ResponseEntity<ReadableShoppingCart> read = testRestTemplate.getForEntity(
				"/api/v1/cart/" + created.getBody().getCode() + "?store=DEFAULT", ReadableShoppingCart.class);
		assertEquals(HttpStatus.OK, read.getStatusCode());
		assertEquals(0, unitCartTotal.multiply(new BigDecimal("2")).compareTo(read.getBody().getTotal()));
	}

	@Test
	public void customerCanCheckoutWithMoneyOrderAndReadOrder() {
		configureMoneyOrder();
		CartContext context = createCart();
		CustomerSession customer = registerAndLoginCustomer();
		ReadableShoppingCart boundCart = bindCart(context.cart.getCode(), customer.token);

		ResponseEntity<ReadableOrderConfirmation> checkout = testRestTemplate.exchange(
				"/api/v1/auth/cart/" + context.cart.getCode() + "/checkout?store=DEFAULT", HttpMethod.POST,
				new HttpEntity<>(checkoutOrder(boundCart.getTotal().toPlainString()), authorizedHeaders(customer.token)),
				ReadableOrderConfirmation.class);
		assertEquals(HttpStatus.OK, checkout.getStatusCode());
		assertNotNull(checkout.getBody());
		assertTrue(checkout.getBody().getId() > 0);

		ResponseEntity<JsonNode> order = testRestTemplate.exchange(
				"/api/v1/auth/orders/" + checkout.getBody().getId() + "?store=DEFAULT", HttpMethod.GET,
				new HttpEntity<>(authorizedHeaders(customer.token)), JsonNode.class);
		assertEquals(HttpStatus.OK, order.getStatusCode());
	}

	@Test
	public void checkoutRejectsIncorrectPaymentAmount() {
		configureMoneyOrder();
		CartContext context = createCart();
		CustomerSession customer = registerAndLoginCustomer();
		ReadableShoppingCart boundCart = bindCart(context.cart.getCode(), customer.token);
		assertNotNull(boundCart);

		ResponseEntity<String> checkout = testRestTemplate.exchange(
				"/api/v1/auth/cart/" + context.cart.getCode() + "/checkout?store=DEFAULT", HttpMethod.POST,
				new HttpEntity<>(checkoutOrder("1.00"), authorizedHeaders(customer.token)), String.class);

		assertFalse(checkout.getStatusCode().is2xxSuccessful());
	}

	@Test
	public void servicesEndpointUsesBasicAuthentication() {
		HttpHeaders validHeaders = jsonHeaders();
		validHeaders.setBasicAuth("admin@shopizer.com", "password");
		ResponseEntity<String> valid = testRestTemplate.exchange("/services/private/system/optin", HttpMethod.POST,
				new HttpEntity<>("{}", validHeaders), String.class);
		assertTrue(valid.getStatusCode().is2xxSuccessful());

		HttpHeaders invalidHeaders = jsonHeaders();
		invalidHeaders.setBasicAuth("admin@shopizer.com", "wrong-password");
		ResponseEntity<String> invalid = testRestTemplate.exchange("/services/private/system/optin", HttpMethod.POST,
				new HttpEntity<>("{}", invalidHeaders), String.class);
		assertServicesAuthorizationFailure(invalid.getStatusCode());

		ResponseEntity<String> anonymous = testRestTemplate.exchange("/services/private/system/optin", HttpMethod.POST,
				new HttpEntity<>("{}", jsonHeaders()), String.class);
		assertServicesAuthorizationFailure(anonymous.getStatusCode());
	}

	@Test
	public void actuatorHealthAndApiDocumentationAreAvailable() {
		ResponseEntity<JsonNode> health = testRestTemplate.getForEntity("/actuator/health", JsonNode.class);
		if (health.getStatusCode() != HttpStatus.OK || health.getBody() == null
				|| !"UP".equals(health.getBody().path("status").asText())) {
			ResponseEntity<String> ping = testRestTemplate.getForEntity("/actuator/health/ping", String.class);
			assertEquals("Health was not UP: " + health.getBody(), HttpStatus.OK, ping.getStatusCode());
		}

		ResponseEntity<String> docs = testRestTemplate.getForEntity("/v2/api-docs", String.class);
		assertEquals(HttpStatus.OK, docs.getStatusCode());
		assertTrue(docs.getBody().contains("/api/v1/cart/{code}/payment/init"));
	}

	private void configureMoneyOrder() {
		IntegrationModuleConfiguration configuration = new IntegrationModuleConfiguration();
		configuration.setCode("moneyorder");
		configuration.setActive(true);
		configuration.getIntegrationKeys().put("address", "Money order payable to Test Store");
		ResponseEntity<Void> response = testRestTemplate.exchange("/api/v1/private/modules/payment?store=DEFAULT",
				HttpMethod.POST, new HttpEntity<>(configuration, getHeader()), Void.class);
		assertEquals(HttpStatus.OK, response.getStatusCode());
	}

	private CartContext createCart() {
		ReadableProduct product = sampleProduct(uniqueCode());
		PersistableShoppingCartItem item = new PersistableShoppingCartItem();
		item.setProduct(product.getSku());
		item.setQuantity(1);
		ResponseEntity<ReadableShoppingCart> response = testRestTemplate.postForEntity(
				"/api/v1/cart?store=DEFAULT", new HttpEntity<>(item, jsonHeaders()), ReadableShoppingCart.class);
		assertEquals(HttpStatus.CREATED, response.getStatusCode());
		return new CartContext(response.getBody());
	}

	private ReadableShoppingCart bindCart(String cartCode, String token) {
		ResponseEntity<ReadableShoppingCart> response = testRestTemplate.exchange(
				"/api/v1/auth/customer/cart?cart=" + cartCode + "&store=DEFAULT", HttpMethod.GET,
				new HttpEntity<>(authorizedHeaders(token)), ReadableShoppingCart.class);
		assertEquals(HttpStatus.OK, response.getStatusCode());
		return response.getBody();
	}

	private CustomerSession registerAndLoginCustomer() {
		String email = "uat-" + uniqueCode() + "@test.com";
		PersistableCustomer customer = new PersistableCustomer();
		customer.setEmailAddress(email);
		customer.setPassword("clear123");
		customer.setGender("M");
		customer.setLanguage("en");
		customer.setStoreCode(Constants.DEFAULT_STORE);
		Address billing = new Address();
		billing.setFirstName("UAT");
		billing.setLastName("Tester");
		billing.setCountry("BE");
		customer.setBilling(billing);
		ResponseEntity<AuthenticationResponse> registration = testRestTemplate.postForEntity(
				"/api/v1/customer/register?store=DEFAULT",
				new HttpEntity<>(customer, jsonHeaders()), AuthenticationResponse.class);
		assertTrue(registration.getStatusCode().is2xxSuccessful());

		AuthenticationRequest credentials = new AuthenticationRequest(email, "clear123");
		ResponseEntity<AuthenticationResponse> login = testRestTemplate.postForEntity(
				"/api/v1/customer/login", new HttpEntity<>(credentials, jsonHeaders()), AuthenticationResponse.class);
		assertEquals(HttpStatus.OK, login.getStatusCode());
		return new CustomerSession(email, login.getBody().getToken());
	}

	private PersistableOrder checkoutOrder(String amount) {
		PersistablePayment payment = new PersistablePayment();
		payment.setPaymentType("MONEYORDER");
		payment.setPaymentModule("moneyorder");
		payment.setTransactionType("AUTHORIZECAPTURE");
		payment.setAmount(amount);

		PersistableOrder order = new PersistableOrder();
		order.setPayment(payment);
		order.setCustomerAgreement(true);
		order.setCurrency("USD");
		return order;
	}

	private void assertUnauthorized(HttpStatus status) {
		assertTrue(status == HttpStatus.UNAUTHORIZED || status == HttpStatus.FORBIDDEN);
	}

	private void assertServicesAuthorizationFailure(HttpStatus status) {
		assertTrue(status == HttpStatus.UNAUTHORIZED || status == HttpStatus.FORBIDDEN || status == HttpStatus.FOUND);
	}

	private HttpHeaders jsonHeaders() {
		HttpHeaders headers = new HttpHeaders();
		headers.setContentType(MediaType.APPLICATION_JSON);
		return headers;
	}

	private HttpHeaders authorizedHeaders(String token) {
		HttpHeaders headers = jsonHeaders();
		headers.setBearerAuth(token);
		return headers;
	}

	private String uniqueCode() {
		return UUID.randomUUID().toString().replace("-", "");
	}

	private static class CartContext {
		private final ReadableShoppingCart cart;

		private CartContext(ReadableShoppingCart cart) {
			this.cart = cart;
		}
	}

	private static class CustomerSession {
		private final String email;
		private final String token;

		private CustomerSession(String email, String token) {
			this.email = email;
			this.token = token;
		}
	}
}
