package com.salesmanager.test.shop.integration.order;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;
import static org.springframework.http.HttpMethod.GET;
import static org.springframework.http.HttpMethod.POST;
import static org.springframework.http.HttpStatus.CREATED;
import static org.springframework.http.HttpStatus.OK;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.util.UUID;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.SpringBootTest.WebEnvironment;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.test.context.junit4.SpringRunner;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.salesmanager.core.business.constants.Constants;
import com.salesmanager.core.model.customer.CustomerGender;
import com.salesmanager.shop.application.ShopApplication;
import com.salesmanager.shop.model.catalog.product.ReadableProduct;
import com.salesmanager.shop.model.customer.PersistableCustomer;
import com.salesmanager.shop.model.customer.ReadableCustomer;
import com.salesmanager.shop.model.customer.address.Address;
import com.salesmanager.shop.model.order.v0.ReadableOrder;
import com.salesmanager.shop.model.order.v1.PersistableAnonymousOrder;
import com.salesmanager.shop.model.order.v1.ReadableOrderConfirmation;
import com.salesmanager.shop.model.order.transaction.PersistablePayment;
import com.salesmanager.shop.model.shoppingcart.PersistableShoppingCartItem;
import com.salesmanager.shop.model.shoppingcart.ReadableShoppingCart;
import com.salesmanager.shop.store.security.AuthenticationRequest;
import com.salesmanager.shop.store.security.AuthenticationResponse;
import com.salesmanager.test.shop.common.ServicesTestSupport;

@SpringBootTest(classes = ShopApplication.class, webEnvironment = WebEnvironment.RANDOM_PORT)
@RunWith(SpringRunner.class)
public class AnonymousCheckoutCustomerScopeIntegrationTest extends ServicesTestSupport {

	@Autowired
	private TestRestTemplate rest;
	@Autowired
	private ObjectMapper objectMapper;

	@Test
	public void anonymousCheckoutUsesSubmittedCustomerRatherThanCustomerId() throws Exception {
		configureMoneyOrder();
		CustomerSession victim = registerCustomer("victim");
		ReadableShoppingCart cart = uniqueCart();
		PersistableAnonymousOrder order = anonymousOrder(cart.getTotal(), "attacker");
		order.setCustomerId(victim.customer.getId());

		ObjectNode request = objectMapper.valueToTree(order);
		request.put("customerId", victim.customer.getId());
		ResponseEntity<ReadableOrderConfirmation> checkout = rest.postForEntity(
				"/api/v1/cart/" + cart.getCode() + "/checkout",
				new HttpEntity<>(objectMapper.writeValueAsString(request), jsonHeaders()),
				ReadableOrderConfirmation.class);

		assertEquals(OK, checkout.getStatusCode());
		assertNotNull(checkout.getBody());
		ReadableOrder savedOrder = rest.exchange("/api/v1/private/orders/" + checkout.getBody().getId(), GET,
				new HttpEntity<>(adminHeaders()), ReadableOrder.class).getBody();
		assertNotNull(savedOrder);
		assertEquals("attacker@example.com", savedOrder.getCustomer().getEmailAddress());
		assertEquals("Attacker", savedOrder.getBilling().getLastName());
		assertNotEquals(victim.customer.getId(), savedOrder.getCustomer().getId());

		ReadableCustomer unchangedVictim = profile(victim);
		assertEquals(victim.customer.getEmailAddress(), unchangedVictim.getEmailAddress());
		assertEquals("Victim", unchangedVictim.getBilling().getLastName());
	}

	@Test
	public void authenticatedCheckoutUsesPrincipalInsteadOfBodyCustomerId() throws Exception {
		configureMoneyOrder();
		CustomerSession customer = registerCustomer("buyer");
		CustomerSession foreignCustomer = registerCustomer("foreign");
		ReadableShoppingCart cart = uniqueCart();

		ObjectNode order = objectMapper.createObjectNode();
		order.put("customerId", foreignCustomer.customer.getId());
		order.put("currency", "USD");
		order.set("payment", objectMapper.valueToTree(payment(cart.getTotal())));
		ResponseEntity<ReadableOrderConfirmation> checkout = rest.postForEntity(
				"/api/v1/auth/cart/" + cart.getCode() + "/checkout",
				new HttpEntity<>(objectMapper.writeValueAsString(order), jsonHeaders(customer.token)),
				ReadableOrderConfirmation.class);

		assertEquals(OK, checkout.getStatusCode());
		assertNotNull(checkout.getBody());
		ReadableOrder savedOrder = rest.exchange("/api/v1/auth/orders/" + checkout.getBody().getId(), GET,
				new HttpEntity<>(jsonHeaders(customer.token)), ReadableOrder.class).getBody();
		assertNotNull(savedOrder);
		assertEquals(customer.customer.getId(), savedOrder.getCustomer().getId());
		assertEquals(customer.customer.getEmailAddress(), savedOrder.getCustomer().getEmailAddress());
		assertNotEquals(foreignCustomer.customer.getId(), savedOrder.getCustomer().getId());
	}

	private CustomerSession registerCustomer(String label) throws Exception {
		String suffix = UUID.randomUUID().toString().replace("-", "");
		String email = label + "-" + suffix + "@example.com";
		PersistableCustomer customer = new PersistableCustomer();
		customer.setEmailAddress(email);
		customer.setPassword("clear123");
		customer.setGender(CustomerGender.M.name());
		customer.setLanguage("en");
		customer.setStoreCode(Constants.DEFAULT_STORE);
		customer.setBilling(address(label.equals("victim") ? "Victim" : "Customer"));
		customer.setDelivery(address(label.equals("victim") ? "Victim" : "Customer"));
		ResponseEntity<String> register = rest.postForEntity("/api/v1/customer/register",
				new HttpEntity<>(customer, jsonHeaders()), String.class);
		assertTrue(register.getStatusCode().is2xxSuccessful());

		ResponseEntity<AuthenticationResponse> login = rest.postForEntity("/api/v1/customer/login",
				new HttpEntity<>(new AuthenticationRequest(email, "clear123"), jsonHeaders()),
				AuthenticationResponse.class);
		assertEquals(OK, login.getStatusCode());
		assertNotNull(login.getBody());
		CustomerSession session = new CustomerSession();
		session.suffix = suffix;
		session.token = login.getBody().getToken();
		session.customer = profile(session);
		return session;
	}

	private ReadableCustomer profile(CustomerSession session) {
		return rest.exchange("/api/v1/auth/customer/profile", GET,
				new HttpEntity<>(jsonHeaders(session.token)), ReadableCustomer.class).getBody();
	}

	private PersistableAnonymousOrder anonymousOrder(BigDecimal amount, String label) {
		PersistableAnonymousOrder order = new PersistableAnonymousOrder();
		order.setCurrency("USD");
		order.setPayment(payment(amount));
		PersistableCustomer customer = new PersistableCustomer();
		customer.setEmailAddress(label + "@example.com");
		customer.setGender(CustomerGender.M.name());
		customer.setBilling(address("Attacker"));
		customer.setDelivery(address("Attacker"));
		order.setCustomer(customer);
		return order;
	}

	private PersistablePayment payment(BigDecimal amount) {
		PersistablePayment payment = new PersistablePayment();
		payment.setPaymentModule("moneyorder");
		payment.setPaymentType("MONEYORDER");
		payment.setTransactionType("CAPTURE");
		payment.setAmount(amount.toPlainString());
		return payment;
	}

	private ReadableShoppingCart uniqueCart() {
		ReadableProduct product = sampleProduct("checkout-" + UUID.randomUUID().toString().replace("-", ""));
		PersistableShoppingCartItem item = new PersistableShoppingCartItem();
		item.setProduct(product.getSku());
		item.setQuantity(1);
		ResponseEntity<ReadableShoppingCart> response = rest.postForEntity("/api/v1/cart/",
				new HttpEntity<>(item, adminHeaders()), ReadableShoppingCart.class);
		assertEquals(CREATED, response.getStatusCode());
		return response.getBody();
	}

	private void configureMoneyOrder() {
		ObjectNode configuration = objectMapper.createObjectNode();
		configuration.put("code", "moneyorder");
		configuration.put("active", true);
		configuration.put("defaultSelected", true);
		configuration.putObject("integrationKeys").put("address", "Test payment address");
		ResponseEntity<Void> response = rest.postForEntity("/api/v1/private/modules/payment?store=DEFAULT",
				new HttpEntity<>(configuration, adminHeaders()), Void.class);
		assertTrue(response.getStatusCode().is2xxSuccessful());
	}

	private Address address(String lastName) {
		Address address = new Address();
		address.setFirstName("Test");
		address.setLastName(lastName);
		address.setCountry("BE");
		return address;
	}

	private HttpHeaders adminHeaders() {
		return getHeader();
	}

	private HttpHeaders jsonHeaders() {
		return jsonHeaders(null);
	}

	private HttpHeaders jsonHeaders(String token) {
		HttpHeaders headers = new HttpHeaders();
		headers.setContentType(new MediaType("application", "json", StandardCharsets.UTF_8));
		if (token != null) {
			headers.setBearerAuth(token);
		}
		return headers;
	}

	private static class CustomerSession {
		private String suffix;
		private String token;
		private ReadableCustomer customer;
	}
}
