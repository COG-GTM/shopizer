package com.salesmanager.test.shop.integration.payment;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;
import static org.junit.Assume.assumeTrue;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.test.context.junit4.SpringRunner;
import org.springframework.transaction.annotation.Transactional;

import com.salesmanager.core.business.constants.Constants;
import com.salesmanager.core.business.services.payments.TransactionService;
import com.salesmanager.core.model.payments.Transaction;
import com.salesmanager.shop.application.ShopApplication;
import com.salesmanager.shop.model.catalog.product.ReadableProduct;
import com.salesmanager.shop.model.customer.PersistableCustomer;
import com.salesmanager.shop.model.customer.address.Address;
import com.salesmanager.shop.model.order.transaction.ReadableTransaction;
import com.salesmanager.shop.model.shoppingcart.PersistableShoppingCartItem;
import com.salesmanager.shop.model.shoppingcart.ReadableShoppingCart;
import com.salesmanager.shop.model.system.IntegrationModuleConfiguration;
import com.salesmanager.shop.store.security.AuthenticationRequest;
import com.salesmanager.shop.store.security.AuthenticationResponse;
import com.salesmanager.test.shop.common.ServicesTestSupport;

@SpringBootTest(classes = ShopApplication.class, webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@RunWith(SpringRunner.class)
public class PaymentInitIntegrationTest extends ServicesTestSupport {

	private static final String PUBLISHABLE_KEY = "pk_test_dummy";

	@Autowired
	private TransactionService transactionService;

	@Before
	public void configureStripe() {
		IntegrationModuleConfiguration configuration = new IntegrationModuleConfiguration();
		configuration.setCode("stripe");
		configuration.setActive(true);
		configuration.getIntegrationKeys().put("publishableKey", PUBLISHABLE_KEY);
		configuration.getIntegrationKeys().put("secretKey", "sk_test_dummy");
		ResponseEntity<Void> response = testRestTemplate.exchange("/api/v1/private/modules/payment?store=DEFAULT",
				HttpMethod.POST, new HttpEntity<>(configuration, adminHeaders()), Void.class);
		assertEquals(HttpStatus.OK, response.getStatusCode());
	}

	@Test
	public void initializesWithoutSubmittedAmountAndReturnsServerTransaction() {
		CartContext context = createCart();
		ReadableShoppingCart cart = readCart(context.cart.getCode());

		ResponseEntity<ReadableTransaction> response = initialize(context.cart.getCode(), payment(null, null, null), null);

		assertEquals(HttpStatus.OK, response.getStatusCode());
		assertNotNull(response.getBody());
		assertAmountEquals(cart.getTotal(), response.getBody().getAmount());
		assertEquals(com.salesmanager.core.model.payments.TransactionType.INIT, response.getBody().getTransactionType());
		assertTrue(response.getBody().getId() > 0);
	}

	@Test
	@Transactional
	public void persistsShoppingCartIdInTransactionDetailsJson() throws Exception {
		CartContext context = createCart();
		ResponseEntity<ReadableTransaction> response = initialize(context.cart.getCode(), payment(null, null, null), null);
		assertEquals(HttpStatus.OK, response.getStatusCode());
		assertNotNull(response.getBody());

		Transaction persisted = transactionService.getById(response.getBody().getId());
		assertNotNull(persisted);
		Map<String, String> details = new ObjectMapper().readValue(persisted.getDetails(),
				new TypeReference<Map<String, String>>() { });
		assertEquals(String.valueOf(context.cart.getId()), details.get("SHOPPING_CART_ID"));
	}

	@Test
	public void acceptsAmountEqualToCartTotal() {
		CartContext context = createCart();
		ReadableShoppingCart cart = readCart(context.cart.getCode());

		ResponseEntity<ReadableTransaction> response = initialize(context.cart.getCode(),
				payment(cart.getTotal().toPlainString(), null, null), null);

		assertEquals(HttpStatus.OK, response.getStatusCode());
		assertNotNull(response.getBody());
		assertAmountEquals(cart.getTotal(), response.getBody().getAmount());
	}

	@Test
	public void rejectsAmountThatDoesNotMatchCartTotal() {
		CartContext context = createCart();

		ResponseEntity<String> response = initializeForError(context.cart.getCode(), payment("1.00", null, null), null);

		assertEquals(HttpStatus.BAD_REQUEST, response.getStatusCode());
		assertTrue(response.getBody().contains("does not match"));
	}

	@Test
	public void forcesInitDespiteCaptureRequest() {
		CartContext context = createCart();

		ResponseEntity<ReadableTransaction> response = initialize(context.cart.getCode(),
				payment(null, "CAPTURE", null), null);

		assertEquals(HttpStatus.OK, response.getStatusCode());
		assertEquals(com.salesmanager.core.model.payments.TransactionType.INIT, response.getBody().getTransactionType());
	}

	@Test
	public void rejectsShippingQuoteThatDoesNotBelongToCart() {
		CartContext context = createCart();

		ResponseEntity<String> response = initializeForError(context.cart.getCode(),
				payment(null, null, 987654321L), null);

		assertEquals(HttpStatus.BAD_REQUEST, response.getStatusCode());
	}

	@Test
	public void returnsNotFoundForUnknownCart() {
		ResponseEntity<String> response = initializeForError("missing-" + uniqueCode(), payment(null, null, null), null);

		assertEquals(HttpStatus.NOT_FOUND, response.getStatusCode());
	}

	@Test
	public void authenticatedCustomerCanInitializeAndAmountMustMatch() {
		CartContext context = createCart();
		CustomerSession customer = registerAndLoginCustomer();
		ReadableShoppingCart boundCart = bindCart(context.cart.getCode(), customer.token);

		ResponseEntity<ReadableTransaction> accepted = initialize(context.cart.getCode(), payment(null, null, null),
				customer.token);
		assertEquals(HttpStatus.OK, accepted.getStatusCode());
		assertAmountEquals(boundCart.getTotal(), accepted.getBody().getAmount());

		ResponseEntity<String> rejected = initializeForError(context.cart.getCode(), payment("1.00", null, null),
				customer.token);
		assertEquals(HttpStatus.BAD_REQUEST, rejected.getStatusCode());
	}

	@Test
	public void requiresAuthenticationAndEnforcesCartOwnership() {
		CartContext context = createCart();
		CustomerSession owner = registerAndLoginCustomer();
		bindCart(context.cart.getCode(), owner.token);
		CustomerSession other = registerAndLoginCustomer();

		ResponseEntity<String> anonymous = initializeAuthenticatedForError(
				context.cart.getCode(), payment(null, null, null), null);
		assertEquals(HttpStatus.UNAUTHORIZED, anonymous.getStatusCode());

		ResponseEntity<String> wrongOwner = initializeForError(context.cart.getCode(), payment(null, null, null),
				other.token);
		assertEquals(HttpStatus.NOT_FOUND, wrongOwner.getStatusCode());
	}

	@Test
	public void emptyCartIsRejectedIfCartRemainsAfterItemRemoval() {
		CartContext context = createCart();
		String deleteUrl = "/api/v1/cart/" + context.cart.getCode() + "/product/" + context.product.getSku()
				+ "?store=DEFAULT&body=true";
		testRestTemplate.exchange(deleteUrl, HttpMethod.DELETE, new HttpEntity<>(jsonHeaders()), ReadableShoppingCart.class);
		ResponseEntity<ReadableShoppingCart> afterRemoval = testRestTemplate.getForEntity(
				"/api/v1/cart/" + context.cart.getCode() + "?store=DEFAULT", ReadableShoppingCart.class);
		assumeTrue("Cart is removed with its final item; empty-cart init case is not applicable",
				afterRemoval.getStatusCode() == HttpStatus.OK);

		ResponseEntity<String> response = initializeForError(context.cart.getCode(), payment(null, null, null), null);
		assertEquals(HttpStatus.BAD_REQUEST, response.getStatusCode());
	}

	private ResponseEntity<ReadableTransaction> initialize(String cartCode, Object payment, String token) {
		String path = token == null ? "/api/v1/cart/" : "/api/v1/auth/cart/";
		return testRestTemplate.exchange(path + cartCode + "/payment/init?store=DEFAULT", HttpMethod.POST,
				new HttpEntity<>(payment, token == null ? jsonHeaders() : authorizedHeaders(token)),
				ReadableTransaction.class);
	}

	private ResponseEntity<String> initializeForError(String cartCode, Object payment, String token) {
		String path = token == null ? "/api/v1/cart/" : "/api/v1/auth/cart/";
		return testRestTemplate.exchange(path + cartCode + "/payment/init?store=DEFAULT", HttpMethod.POST,
				new HttpEntity<>(payment, token == null ? jsonHeaders() : authorizedHeaders(token)), String.class);
	}

	private ResponseEntity<String> initializeAuthenticatedForError(String cartCode, Object payment,
			String token) {
		return testRestTemplate.exchange("/api/v1/auth/cart/" + cartCode + "/payment/init?store=DEFAULT",
				HttpMethod.POST, new HttpEntity<>(payment, token == null ? jsonHeaders() : authorizedHeaders(token)),
				String.class);
	}

	private Map<String, Object> payment(String amount, String transactionType, Long shippingQuote) {
		Map<String, Object> payment = new HashMap<>();
		payment.put("paymentType", "CREDITCARD");
		payment.put("paymentModule", "stripe");
		if (amount != null) {
			payment.put("amount", amount);
		}
		if (transactionType != null) {
			payment.put("transactionType", transactionType);
		}
		if (shippingQuote != null) {
			payment.put("shippingQuote", shippingQuote);
		}
		return payment;
	}

	private CartContext createCart() {
		ReadableProduct product = sampleProduct(uniqueCode());
		PersistableShoppingCartItem item = new PersistableShoppingCartItem();
		item.setProduct(product.getSku());
		item.setQuantity(1);
		ResponseEntity<ReadableShoppingCart> response = testRestTemplate.postForEntity(
				"/api/v1/cart?store=DEFAULT", new HttpEntity<>(item, jsonHeaders()), ReadableShoppingCart.class);
		assertEquals(HttpStatus.CREATED, response.getStatusCode());
		assertNotNull(response.getBody());
		return new CartContext(product, response.getBody());
	}

	private ReadableShoppingCart readCart(String cartCode) {
		ResponseEntity<ReadableShoppingCart> response = testRestTemplate.getForEntity(
				"/api/v1/cart/" + cartCode + "?store=DEFAULT", ReadableShoppingCart.class);
		assertEquals(HttpStatus.OK, response.getStatusCode());
		return response.getBody();
	}

	private ReadableShoppingCart bindCart(String cartCode, String token) {
		ResponseEntity<ReadableShoppingCart> response = testRestTemplate.exchange(
				"/api/v1/auth/customer/cart?cart=" + cartCode + "&store=DEFAULT", HttpMethod.GET,
				new HttpEntity<>(authorizedHeaders(token)), ReadableShoppingCart.class);
		assertEquals(HttpStatus.OK, response.getStatusCode());
		return response.getBody();
	}

	private CustomerSession registerAndLoginCustomer() {
		String email = "payment-" + uniqueCode() + "@test.com";
		PersistableCustomer customer = new PersistableCustomer();
		customer.setEmailAddress(email);
		customer.setPassword("clear123");
		customer.setGender("M");
		customer.setLanguage("en");
		customer.setStoreCode(Constants.DEFAULT_STORE);
		Address billing = new Address();
		billing.setFirstName("Payment");
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
		assertNotNull(login.getBody());
		return new CustomerSession(email, login.getBody().getToken());
	}

	private HttpHeaders adminHeaders() {
		HttpHeaders headers = getHeader();
		headers.setContentType(MediaType.APPLICATION_JSON);
		return headers;
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

	private void assertAmountEquals(BigDecimal expected, String actual) {
		String numericAmount = actual.replaceAll("[^0-9,.-]", "").replace(",", "");
		assertEquals(expected.setScale(2, RoundingMode.HALF_UP).toPlainString(), numericAmount);
	}

	private String uniqueCode() {
		return UUID.randomUUID().toString().replace("-", "");
	}

	private static class CartContext {
		private final ReadableProduct product;
		private final ReadableShoppingCart cart;

		private CartContext(ReadableProduct product, ReadableShoppingCart cart) {
			this.product = product;
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
