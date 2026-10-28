package com.salesmanager.test.shop.uat;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;
import static org.springframework.http.HttpMethod.GET;
import static org.springframework.http.HttpStatus.CREATED;
import static org.springframework.http.HttpStatus.OK;
import static org.springframework.http.HttpStatus.UNAUTHORIZED;

import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.UUID;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.SpringBootTest.WebEnvironment;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.test.context.junit4.SpringRunner;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.salesmanager.core.business.constants.Constants;
import com.salesmanager.core.model.customer.CustomerGender;
import com.salesmanager.shop.application.ShopApplication;
import com.salesmanager.shop.model.catalog.product.ReadableProduct;
import com.salesmanager.shop.model.customer.PersistableCustomer;
import com.salesmanager.shop.model.customer.ReadableCustomer;
import com.salesmanager.shop.model.customer.address.Address;
import com.salesmanager.shop.model.order.transaction.PersistablePayment;
import com.salesmanager.shop.model.order.v1.PersistableAnonymousOrder;
import com.salesmanager.shop.model.shoppingcart.PersistableShoppingCartItem;
import com.salesmanager.shop.model.shoppingcart.ReadableShoppingCart;
import com.salesmanager.shop.model.store.ReadableMerchantStore;
import com.salesmanager.shop.store.security.AuthenticationRequest;
import com.salesmanager.shop.store.security.AuthenticationResponse;
import com.salesmanager.test.shop.common.ServicesTestSupport;

@SpringBootTest(classes = ShopApplication.class, webEnvironment = WebEnvironment.RANDOM_PORT)
@RunWith(SpringRunner.class)
public class StorefrontAdminJourneyUatTest extends ServicesTestSupport {

	private final ObjectMapper objectMapper = new ObjectMapper();

	@Test
	public void adminAuthenticationAndPrivateCustomerEndpoint() {
		ResponseEntity<AuthenticationResponse> login = testRestTemplate.postForEntity(
				"/api/v1/private/login",
				new HttpEntity<>(new AuthenticationRequest("admin@shopizer.com", "password")),
				AuthenticationResponse.class);
		assertEquals(OK, login.getStatusCode());
		assertNotNull(login.getBody());

		ResponseEntity<String> authorized = testRestTemplate.exchange("/api/v1/private/customers", GET,
				new HttpEntity<>(bearer(login.getBody().getToken())), String.class);
		assertEquals(OK, authorized.getStatusCode());

		ResponseEntity<String> anonymous = testRestTemplate.getForEntity("/api/v1/private/customers", String.class);
		assertEquals(UNAUTHORIZED, anonymous.getStatusCode());
	}

	@Test
	public void customerRegistrationLoginProfileAndUnauthenticatedDenial() {
		Account customer = registerCustomer(unique("profile"));

		ResponseEntity<ReadableCustomer> profile = testRestTemplate.exchange(
				"/api/v1/auth/customer/profile", GET, new HttpEntity<>(bearer(customer.token)),
				ReadableCustomer.class);
		assertEquals(OK, profile.getStatusCode());
		assertEquals(customer.email, profile.getBody().getEmailAddress());

		ResponseEntity<String> anonymous = testRestTemplate.getForEntity(
				"/api/v1/auth/customer/profile", String.class);
		assertEquals(UNAUTHORIZED, anonymous.getStatusCode());
	}

	@Test
	public void storefrontBrowsingAndCreatedProductRetrieval() {
		ResponseEntity<ReadableMerchantStore> store = testRestTemplate.getForEntity(
				"/api/v1/store/DEFAULT", ReadableMerchantStore.class);
		assertEquals(OK, store.getStatusCode());
		assertEquals(Constants.DEFAULT_STORE, store.getBody().getCode());

		assertEquals(OK, testRestTemplate.getForEntity("/api/v1/category?store=DEFAULT", String.class)
				.getStatusCode());
		assertEquals(OK, testRestTemplate.getForEntity("/api/v1/products?store=DEFAULT", String.class)
				.getStatusCode());

		ReadableProduct created = sampleProduct(unique("browse"));
		ResponseEntity<ReadableProduct> retrieved = testRestTemplate.getForEntity(
				"/api/v2/product/" + created.getSku(), ReadableProduct.class);
		assertEquals(OK, retrieved.getStatusCode());
		assertEquals(created.getSku(), retrieved.getBody().getSku());
	}

	@Test
	public void cartCreationUpdateAndRead() {
		ReadableProduct product = sampleProduct(unique("cart"));
		PersistableShoppingCartItem initialItem = cartItem(product.getSku(), 1);
		ResponseEntity<ReadableShoppingCart> created = testRestTemplate.postForEntity(
				"/api/v1/cart", new HttpEntity<>(initialItem), ReadableShoppingCart.class);
		assertEquals(CREATED, created.getStatusCode());
		assertNotNull(created.getBody());

		ResponseEntity<ReadableShoppingCart> updated = testRestTemplate.exchange(
				"/api/v1/cart/" + created.getBody().getCode(), HttpMethod.PUT,
				new HttpEntity<>(cartItem(product.getSku(), 3)), ReadableShoppingCart.class);
		assertEquals(CREATED, updated.getStatusCode());
		assertEquals(3, updated.getBody().getQuantity());

		ResponseEntity<ReadableShoppingCart> retrieved = testRestTemplate.getForEntity(
				"/api/v1/cart/" + created.getBody().getCode(), ReadableShoppingCart.class);
		assertEquals(OK, retrieved.getStatusCode());
		assertEquals(3, retrieved.getBody().getQuantity());
	}

	@Test
	public void anonymousCheckoutRejectsForeignCustomerIdWithoutLeakingProfile() {
		Account victim = registerCustomer(unique("victim"));
		ReadableShoppingCart cart = createCart();
		PersistableAnonymousOrder order = anonymousOrder(cart, email("attacker"));

		ResponseEntity<String> checkout = checkoutAnonymousWithCustomerId(cart, order, victim.id);

		assertEquals(org.springframework.http.HttpStatus.BAD_REQUEST, checkout.getStatusCode());
		assertNotNull(checkout.getBody());
		assertFalse(checkout.getBody().contains(victim.email));
		assertFalse(checkout.getBody().contains(victim.firstName));
	}

	@Test
	public void servicesBasicAuthenticationAndPublicEndpoint() {
		HttpHeaders invalidCredentials = basic("admin@shopizer.com", "incorrect");
		ResponseEntity<String> denied = testRestTemplate.exchange(
				"/services/private/system/optin", GET, new HttpEntity<>(invalidCredentials), String.class);
		assertEquals(UNAUTHORIZED, denied.getStatusCode());

		HttpHeaders validCredentials = basic("admin@shopizer.com", "password");
		ResponseEntity<String> authenticated = testRestTemplate.exchange(
				"/services/private/system/optin", GET, new HttpEntity<>(validCredentials), String.class);
		assertNotEquals(UNAUTHORIZED, authenticated.getStatusCode());

		ResponseEntity<String> publicContact = testRestTemplate.getForEntity(
				"/services/public/DEFAULT", String.class);
		assertNotEquals(UNAUTHORIZED, publicContact.getStatusCode());
	}

	@Test
	public void healthAndSwaggerDocumentationAreAvailable() {
		ResponseEntity<JsonNode> health = testRestTemplate.getForEntity(
				"/actuator/health", JsonNode.class);
		assertEquals(OK, health.getStatusCode());
		assertEquals("UP", health.getBody().path("status").asText());

		ResponseEntity<String> apiDocs = testRestTemplate.getForEntity("/v2/api-docs", String.class);
		assertEquals(OK, apiDocs.getStatusCode());
		assertTrue(apiDocs.getBody().toLowerCase().contains("swagger"));
	}

	private Account registerCustomer(String firstName) {
		String email = email("customer");
		String password = "clear123";
		PersistableCustomer request = new PersistableCustomer();
		request.setEmailAddress(email);
		request.setPassword(password);
		request.setRepeatPassword(password);
		request.setGender(CustomerGender.M.name());
		request.setLanguage("en");
		request.setStoreCode(Constants.DEFAULT_STORE);
		request.setBilling(address(firstName));
		request.setDelivery(address(firstName));

		ResponseEntity<AuthenticationResponse> registered = testRestTemplate.postForEntity(
				"/api/v1/customer/register", new HttpEntity<>(request), AuthenticationResponse.class);
		assertEquals(OK, registered.getStatusCode());

		ResponseEntity<AuthenticationResponse> login = testRestTemplate.postForEntity(
				"/api/v1/customer/login", new HttpEntity<>(new AuthenticationRequest(email, password)),
				AuthenticationResponse.class);
		assertEquals(OK, login.getStatusCode());
		assertNotNull(login.getBody());

		ResponseEntity<ReadableCustomer> profile = testRestTemplate.exchange(
				"/api/v1/auth/customer/profile", GET, new HttpEntity<>(bearer(login.getBody().getToken())),
				ReadableCustomer.class);
		assertEquals(OK, profile.getStatusCode());
		return new Account(profile.getBody().getId(), email, firstName, login.getBody().getToken());
	}

	private ReadableShoppingCart createCart() {
		ReadableProduct product = sampleProduct(unique("checkout"));
		ResponseEntity<ReadableShoppingCart> cart = testRestTemplate.postForEntity(
				"/api/v1/cart", new HttpEntity<>(cartItem(product.getSku(), 1)),
				ReadableShoppingCart.class);
		assertEquals(CREATED, cart.getStatusCode());
		return cart.getBody();
	}

	private PersistableAnonymousOrder anonymousOrder(ReadableShoppingCart cart, String email) {
		PersistableAnonymousOrder order = new PersistableAnonymousOrder();
		PersistableCustomer customer = new PersistableCustomer();
		customer.setEmailAddress(email);
		customer.setGender(CustomerGender.M.name());
		customer.setLanguage("en");
		customer.setStoreCode(Constants.DEFAULT_STORE);
		customer.setBilling(address(unique("Attacker")));
		customer.setDelivery(address(unique("Attacker")));
		order.setCustomer(customer);
		PersistablePayment payment = payment(cart);
		order.setPayment(payment);
		order.setCurrency(fetchStore().getCurrency());
		order.setCustomerAgreement(true);
		return order;
	}

	private ResponseEntity<String> checkoutAnonymousWithCustomerId(
			ReadableShoppingCart cart, PersistableAnonymousOrder order, Long customerId) {
		ObjectNode body = objectMapper.valueToTree(order);
		body.put("customerId", customerId);
		HttpHeaders headers = new HttpHeaders();
		headers.setContentType(MediaType.APPLICATION_JSON);
		return testRestTemplate.postForEntity(
				"/api/v1/cart/" + cart.getCode() + "/checkout",
				new HttpEntity<>(body.toString(), headers), String.class);
	}

	private PersistablePayment payment(ReadableShoppingCart cart) {
		PersistablePayment payment = new PersistablePayment();
		payment.setPaymentModule("moneyorder");
		payment.setPaymentType("MONEYORDER");
		payment.setTransactionType("CAPTURE");
		payment.setAmount(cart.getTotal().toPlainString());
		return payment;
	}

	private PersistableShoppingCartItem cartItem(String product, int quantity) {
		PersistableShoppingCartItem item = new PersistableShoppingCartItem();
		item.setProduct(product);
		item.setQuantity(quantity);
		return item;
	}

	private Address address(String firstName) {
		Address address = new Address();
		address.setFirstName(firstName);
		address.setLastName("Shopizer");
		address.setAddress("123 Main Street");
		address.setCity("Brussels");
		address.setPostalCode("1000");
		address.setCountry("BE");
		address.setCountryCode("BE");
		address.setStateProvince("Brussels");
		return address;
	}

	private HttpHeaders bearer(String token) {
		HttpHeaders headers = new HttpHeaders();
		headers.set("Authorization", "Bearer " + token);
		return headers;
	}

	private HttpHeaders basic(String username, String password) {
		String credentials = username + ":" + password;
		String encoded = Base64.getEncoder().encodeToString(credentials.getBytes(StandardCharsets.UTF_8));
		HttpHeaders headers = new HttpHeaders();
		headers.set("Authorization", "Basic " + encoded);
		return headers;
	}

	private String unique(String prefix) {
		return prefix + UUID.randomUUID().toString().replace("-", "");
	}

	private String email(String prefix) {
		return prefix + UUID.randomUUID().toString().replace("-", "") + "@example.com";
	}

	private static final class Account {
		private final Long id;
		private final String email;
		private final String firstName;
		private final String token;

		private Account(Long id, String email, String firstName, String token) {
			this.id = id;
			this.email = email;
			this.firstName = firstName;
			this.token = token;
		}
	}
}
