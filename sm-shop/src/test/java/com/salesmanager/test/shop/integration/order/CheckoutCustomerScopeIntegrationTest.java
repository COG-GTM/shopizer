package com.salesmanager.test.shop.integration.order;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNotEquals;
import static org.junit.Assert.assertTrue;
import static org.springframework.http.HttpMethod.GET;
import static org.springframework.http.HttpStatus.BAD_REQUEST;
import static org.springframework.http.HttpStatus.OK;

import java.util.UUID;

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
import com.salesmanager.shop.model.order.v1.PersistableOrder;
import com.salesmanager.shop.model.shoppingcart.PersistableShoppingCartItem;
import com.salesmanager.shop.model.shoppingcart.ReadableShoppingCart;
import com.salesmanager.shop.model.store.ReadableMerchantStore;
import com.salesmanager.shop.store.security.AuthenticationRequest;
import com.salesmanager.shop.store.security.AuthenticationResponse;
import com.salesmanager.test.shop.common.ServicesTestSupport;

@SpringBootTest(classes = ShopApplication.class, webEnvironment = WebEnvironment.RANDOM_PORT)
@RunWith(SpringRunner.class)
public class CheckoutCustomerScopeIntegrationTest extends ServicesTestSupport {

	private final ObjectMapper objectMapper = new ObjectMapper();

	@Test
	public void anonymousCheckoutWithoutCustomerIdReachesPaymentProcessing() {
		ReadableShoppingCart cart = createCart();
		String email = email("guest");
		String firstName = unique("Guest");

		ResponseEntity<String> checkout = checkoutAnonymous(cart,
				anonymousOrder(cart, email, firstName), String.class);

		assertEquals(BAD_REQUEST, checkout.getStatusCode());
		assertNotNull(checkout.getBody());
		assertTrue(checkout.getBody().contains("APP-BACKEND"));
	}

	@Test
	public void anonymousCheckoutWithZeroIdsReachesPaymentProcessing() {
		ReadableShoppingCart cart = createCart();
		PersistableAnonymousOrder order = anonymousOrder(cart, unique("guest"), unique("Guest"));
		order.getCustomer().setId(0L);

		ResponseEntity<String> checkout = checkoutAnonymousWithCustomerId(cart, order, 0L);

		assertEquals(BAD_REQUEST, checkout.getStatusCode());
		assertNotNull(checkout.getBody());
		assertTrue(checkout.getBody().contains("APP-BACKEND"));
		assertFalse(checkout.getBody().contains("customerId is not accepted"));
		assertFalse(checkout.getBody().contains("customer.id is not accepted"));
	}

	@Test
	public void anonymousCheckoutWithForeignCustomerIdDoesNotDiscloseVictim() {
		Account victim = registerCustomer(unique("Victim"));
		ReadableShoppingCart cart = createCart();
		PersistableAnonymousOrder order = anonymousOrder(cart, email("attacker"), unique("Attacker"));

		ResponseEntity<String> checkout = checkoutAnonymousWithCustomerId(cart, order, victim.id);

		assertEquals(BAD_REQUEST, checkout.getStatusCode());
		assertNotNull(checkout.getBody());
		assertFalse(checkout.getBody().contains(victim.email));
		assertFalse(checkout.getBody().contains(victim.firstName));
		assertProfileUnchanged(victim);
	}

	@Test
	public void anonymousCheckoutWithForeignCustomerEntityIdDoesNotOverwriteCustomer() {
		Account victim = registerCustomer(unique("Victim"));
		ReadableShoppingCart cart = createCart();
		PersistableAnonymousOrder order = anonymousOrder(cart, email("attacker"), unique("Attacker"));
		order.getCustomer().setId(victim.id);
		order.getCustomer().setPassword("attacker-pass-123");
		order.getCustomer().setRepeatPassword("attacker-pass-123");

		ResponseEntity<String> checkout = checkoutAnonymous(cart, order, String.class);

		assertEquals(BAD_REQUEST, checkout.getStatusCode());
		assertNotNull(checkout.getBody());
		assertFalse(checkout.getBody().contains(victim.email));
		assertFalse(checkout.getBody().contains(victim.firstName));
		assertProfileUnchanged(victim);
		assertEquals(OK, login(victim.email, victim.password).getStatusCode());
		assertNotEquals(OK, login(order.getCustomer().getEmailAddress(), "attacker-pass-123").getStatusCode());
	}

	private ResponseEntity<String> checkoutAnonymous(
			ReadableShoppingCart cart, PersistableAnonymousOrder order, Class<String> responseType) {
		return testRestTemplate.postForEntity(
				"/api/v1/cart/" + cart.getCode() + "/checkout", new HttpEntity<>(order), responseType);
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

	private PersistableAnonymousOrder anonymousOrder(
			ReadableShoppingCart cart, String email, String firstName) {
		PersistableAnonymousOrder order = new PersistableAnonymousOrder();
		order.setCustomer(guest(email, firstName));
		setCheckoutFields(order, cart);
		return order;
	}

	private void setCheckoutFields(PersistableOrder order, ReadableShoppingCart cart) {
		ReadableMerchantStore store = fetchStore();
		PersistablePayment payment = new PersistablePayment();
		payment.setPaymentModule("moneyorder");
		payment.setPaymentType("MONEYORDER");
		payment.setTransactionType("CAPTURE");
		payment.setAmount(cart.getTotal().toPlainString());
		order.setPayment(payment);
		order.setCurrency(store.getCurrency());
		order.setCustomerAgreement(true);
	}

	private PersistableCustomer guest(String email, String firstName) {
		PersistableCustomer customer = new PersistableCustomer();
		customer.setEmailAddress(email);
		customer.setGender(CustomerGender.M.name());
		customer.setLanguage("en");
		customer.setStoreCode(Constants.DEFAULT_STORE);
		customer.setBilling(address(firstName));
		customer.setDelivery(address(firstName));
		return customer;
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

	private ReadableShoppingCart createCart() {
		String productCode = unique("checkout");
		ReadableProduct product = sampleProduct(productCode);
		PersistableShoppingCartItem item = new PersistableShoppingCartItem();
		item.setProduct(product.getSku());
		item.setQuantity(1);
		ResponseEntity<ReadableShoppingCart> response = testRestTemplate.postForEntity(
				"/api/v1/cart", new HttpEntity<>(item), ReadableShoppingCart.class);
		assertEquals(201, response.getStatusCodeValue());
		assertNotNull(response.getBody());
		assertNotNull(response.getBody().getTotal());
		return response.getBody();
	}

	private Account registerCustomer(String firstName) {
		String email = email("customer");
		String password = "clear123";
		PersistableCustomer request = guest(email, firstName);
		request.setPassword(password);
		request.setRepeatPassword(password);

		ResponseEntity<AuthenticationResponse> registration = testRestTemplate.postForEntity(
				"/api/v1/customer/register", new HttpEntity<>(request), AuthenticationResponse.class);
		assertEquals(OK, registration.getStatusCode());

		ResponseEntity<AuthenticationResponse> login = login(email, password);
		assertEquals(OK, login.getStatusCode());
		assertNotNull(login.getBody());
		String token = login.getBody().getToken();

		ResponseEntity<ReadableCustomer> profile = testRestTemplate.exchange(
				"/api/v1/auth/customer/profile", GET, new HttpEntity<>(bearer(token)), ReadableCustomer.class);
		assertEquals(OK, profile.getStatusCode());
		assertNotNull(profile.getBody());
		return new Account(profile.getBody().getId(), email, password, firstName, token);
	}

	private ResponseEntity<AuthenticationResponse> login(String email, String password) {
		return testRestTemplate.postForEntity("/api/v1/customer/login",
				new HttpEntity<>(new AuthenticationRequest(email, password)), AuthenticationResponse.class);
	}

	private void assertProfileUnchanged(Account account) {
		ResponseEntity<ReadableCustomer> profile = testRestTemplate.exchange(
				"/api/v1/auth/customer/profile", GET, new HttpEntity<>(bearer(account.token)),
				ReadableCustomer.class);
		assertEquals(OK, profile.getStatusCode());
		assertEquals(account.email, profile.getBody().getEmailAddress());
		assertEquals(account.firstName, profile.getBody().getBilling().getFirstName());
	}

	private HttpHeaders bearer(String token) {
		HttpHeaders headers = new HttpHeaders();
		headers.set("Authorization", "Bearer " + token);
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
		private final String password;
		private final String firstName;
		private final String token;

		private Account(Long id, String email, String password, String firstName, String token) {
			this.id = id;
			this.email = email;
			this.password = password;
			this.firstName = firstName;
			this.token = token;
		}
	}
}
