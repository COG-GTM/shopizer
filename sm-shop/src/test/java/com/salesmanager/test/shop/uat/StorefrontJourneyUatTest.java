package com.salesmanager.test.shop.uat;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNotEquals;
import static org.junit.Assert.assertTrue;
import static org.springframework.http.HttpMethod.GET;
import static org.springframework.http.HttpMethod.POST;
import static org.springframework.http.HttpMethod.PUT;
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
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.test.context.junit4.SpringRunner;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.salesmanager.core.business.constants.Constants;
import com.salesmanager.core.model.customer.CustomerGender;
import com.salesmanager.shop.application.ShopApplication;
import com.salesmanager.shop.model.catalog.category.ReadableCategoryList;
import com.salesmanager.shop.model.catalog.product.ReadableProduct;
import com.salesmanager.shop.model.catalog.product.ReadableProductList;
import com.salesmanager.shop.model.customer.PersistableCustomer;
import com.salesmanager.shop.model.customer.ReadableCustomer;
import com.salesmanager.shop.model.customer.address.Address;
import com.salesmanager.shop.model.order.v0.ReadableOrder;
import com.salesmanager.shop.model.order.v1.ReadableOrderConfirmation;
import com.salesmanager.shop.model.shoppingcart.PersistableShoppingCartItem;
import com.salesmanager.shop.model.shoppingcart.ReadableShoppingCart;
import com.salesmanager.shop.model.store.ReadableMerchantStore;
import com.salesmanager.shop.store.security.AuthenticationRequest;
import com.salesmanager.shop.store.security.AuthenticationResponse;
import com.salesmanager.test.shop.common.ServicesTestSupport;

@SpringBootTest(classes = ShopApplication.class, webEnvironment = WebEnvironment.RANDOM_PORT)
@RunWith(SpringRunner.class)
public class StorefrontJourneyUatTest extends ServicesTestSupport {

	@Autowired
	private TestRestTemplate rest;
	@Autowired
	private ObjectMapper objectMapper;

	@Test
	public void storefrontAuthenticationBrowseCartAndCheckoutJourneys() throws Exception {
		AuthenticationResponse admin = rest.postForEntity("/api/v1/private/login",
				new HttpEntity<>(new AuthenticationRequest("admin@shopizer.com", "password"), jsonHeaders()),
				AuthenticationResponse.class).getBody();
		assertNotNull(admin);
		assertEquals(OK, rest.exchange("/api/v1/private/customers", GET,
				new HttpEntity<>(bearer(admin.getToken())), String.class).getStatusCode());
		int anonymousCode = protectedRequestStatus("/api/v1/private/customers", null);
		assertTrue(anonymousCode == 401 || anonymousCode == 403);
		int invalidTokenCode = protectedRequestStatus("/api/v1/private/customers", "not-a-valid-token");
		assertEquals(500, invalidTokenCode);
		System.out.println("UAT_ADMIN_AUTH noToken=" + anonymousCode + " invalidToken=" + invalidTokenCode);
		configureMoneyOrder(admin.getToken());

		CustomerSession customer = registerCustomer("journey-customer");
		ReadableCustomer profile = rest.exchange("/api/v1/auth/customer/profile", GET,
				new HttpEntity<>(bearer(customer.token)), ReadableCustomer.class).getBody();
		assertNotNull(profile);
		assertEquals(customer.email, profile.getEmailAddress());

		assertEquals(OK, rest.getForEntity("/api/v1/store/DEFAULT", ReadableMerchantStore.class).getStatusCode());
		assertEquals(OK, rest.getForEntity("/api/v1/category?store=DEFAULT", ReadableCategoryList.class)
				.getStatusCode());
		ResponseEntity<ReadableProductList> productList = rest.getForEntity("/api/v1/products?store=DEFAULT",
				ReadableProductList.class);
		assertEquals(OK, productList.getStatusCode());
		ReadableProductList products = productList.getBody();
		assertNotNull(products);
		ReadableProduct product = products.getProducts().isEmpty()
				? createProductForJourney() : products.getProducts().get(0);
		ResponseEntity<ReadableProduct> productDetail = rest.getForEntity(
				"/api/v2/product/" + product.getSku() + "?store=DEFAULT", ReadableProduct.class);
		assertEquals(OK, productDetail.getStatusCode());

		PersistableShoppingCartItem item = cartItem(product.getSku(), 1);
		ReadableShoppingCart cart = rest.postForEntity("/api/v1/cart?store=DEFAULT",
				new HttpEntity<>(item, jsonHeaders()), ReadableShoppingCart.class).getBody();
		assertNotNull(cart);
		assertNotNull(cart.getCode());
		ResponseEntity<ReadableShoppingCart> updated = rest.exchange("/api/v1/cart/" + cart.getCode() + "?store=DEFAULT",
				PUT, new HttpEntity<>(cartItem(product.getSku(), 2), jsonHeaders()), ReadableShoppingCart.class);
		assertEquals(CREATED, updated.getStatusCode());
		ReadableShoppingCart readCart = rest.getForEntity("/api/v1/cart/" + cart.getCode() + "?store=DEFAULT",
				ReadableShoppingCart.class).getBody();
		assertNotNull(readCart);
		assertEquals(2, readCart.getQuantity());

		ReadableShoppingCart authenticatedCart = createCart(product.getSku());
		ObjectNode authenticatedOrder = checkoutBody(authenticatedCart.getTotal());
		authenticatedOrder.put("customerId", customer.foreignId);
		ResponseEntity<ReadableOrderConfirmation> authenticatedCheckout = rest.postForEntity(
				"/api/v1/auth/cart/" + authenticatedCart.getCode() + "/checkout",
				new HttpEntity<>(objectMapper.writeValueAsString(authenticatedOrder), bearer(customer.token)),
				ReadableOrderConfirmation.class);
		assertEquals(OK, authenticatedCheckout.getStatusCode());
		ReadableOrder authenticatedOrderRead = rest.exchange(
				"/api/v1/auth/orders/" + authenticatedCheckout.getBody().getId(), GET,
				new HttpEntity<>(bearer(customer.token)), ReadableOrder.class).getBody();
		assertNotNull(authenticatedOrderRead);
		assertEquals(customer.profile.getId(), authenticatedOrderRead.getCustomer().getId());

		ReadableShoppingCart anonymousCart = createCart(product.getSku());
		ObjectNode anonymousOrder = checkoutBody(anonymousCart.getTotal());
		anonymousOrder.put("customerId", customer.profile.getId());
		ObjectNode attacker = anonymousOrder.putObject("customer");
		attacker.put("emailAddress", "anon-" + UUID.randomUUID().toString().substring(0, 8) + "@example.com");
		attacker.put("gender", CustomerGender.M.name());
		attacker.set("billing", objectMapper.valueToTree(address("Attacker")));
		attacker.set("delivery", objectMapper.valueToTree(address("Attacker")));
		ResponseEntity<ReadableOrderConfirmation> anonymousCheckout = rest.postForEntity(
				"/api/v1/cart/" + anonymousCart.getCode() + "/checkout",
				new HttpEntity<>(objectMapper.writeValueAsString(anonymousOrder), jsonHeaders()),
				ReadableOrderConfirmation.class);
		assertEquals(OK, anonymousCheckout.getStatusCode());
		ReadableOrder anonymousOrderRead = rest.exchange(
				"/api/v1/private/orders/" + anonymousCheckout.getBody().getId(), GET,
				new HttpEntity<>(bearer(admin.getToken())), ReadableOrder.class).getBody();
		assertNotNull(anonymousOrderRead);
		assertEquals("Attacker", anonymousOrderRead.getBilling().getLastName());
		assertNotEquals(customer.profile.getId(), anonymousOrderRead.getCustomer().getId());

		ResponseEntity<String> validBasic = rest.withBasicAuth("admin@shopizer.com", "password").exchange(
				"/services/private/system/optin", POST, new HttpEntity<>("{}", textHeaders()), String.class);
		ResponseEntity<String> invalidBasic = rest.withBasicAuth("admin@shopizer.com", "incorrect").exchange(
				"/services/private/system/optin", POST, new HttpEntity<>("{}", textHeaders()), String.class);
		assertEquals(OK, validBasic.getStatusCode());
		assertTrue(invalidBasic.getStatusCode().value() == 401 || invalidBasic.getStatusCode().value() == 403);
		System.out.println("UAT_SERVICES_BASIC valid=" + validBasic.getStatusCode().value()
				+ " invalid=" + invalidBasic.getStatusCode().value());

		ResponseEntity<String> health = rest.getForEntity("/actuator/health", String.class);
		assertEquals(OK, health.getStatusCode());
		assertTrue(health.getBody().contains("\"status\":\"UP\""));
		assertEquals(OK, rest.getForEntity("/v2/api-docs", String.class).getStatusCode());
	}

	private int protectedRequestStatus(String path, String token) {
		HttpHeaders headers = new HttpHeaders();
		if (token != null) {
			headers.setBearerAuth(token);
		}
		return rest.exchange(path, GET, new HttpEntity<>(headers), String.class).getStatusCode().value();
	}

	private CustomerSession registerCustomer(String label) throws Exception {
		String suffix = UUID.randomUUID().toString().replace("-", "");
		String email = label + "-" + suffix.substring(0, 8) + "@example.com";
		PersistableCustomer customer = new PersistableCustomer();
		customer.setEmailAddress(email);
		customer.setPassword("clear123");
		customer.setGender(CustomerGender.M.name());
		customer.setLanguage("en");
		customer.setStoreCode(Constants.DEFAULT_STORE);
		customer.setBilling(address("Customer"));
		customer.setDelivery(address("Customer"));
		ResponseEntity<String> registered = rest.postForEntity("/api/v1/customer/register",
				new HttpEntity<>(customer, jsonHeaders()), String.class);
		assertTrue(registered.getStatusCode().is2xxSuccessful());
		AuthenticationResponse login = rest.postForEntity("/api/v1/customer/login",
				new HttpEntity<>(new AuthenticationRequest(email, "clear123"), jsonHeaders()),
				AuthenticationResponse.class).getBody();
		assertNotNull(login);
		CustomerSession session = new CustomerSession();
		session.email = email;
		session.token = login.getToken();
		session.profile = rest.exchange("/api/v1/auth/customer/profile", GET,
				new HttpEntity<>(bearer(session.token)), ReadableCustomer.class).getBody();
		assertNotNull(session.profile);

		PersistableCustomer foreign = new PersistableCustomer();
		foreign.setEmailAddress("journey-foreign-" + suffix.substring(0, 8) + "@example.com");
		foreign.setPassword("clear123");
		foreign.setGender(CustomerGender.M.name());
		foreign.setLanguage("en");
		foreign.setStoreCode(Constants.DEFAULT_STORE);
		foreign.setBilling(address("Foreign"));
		foreign.setDelivery(address("Foreign"));
		rest.postForEntity("/api/v1/customer/register", new HttpEntity<>(foreign, jsonHeaders()), String.class);
		AuthenticationResponse foreignLogin = rest.postForEntity("/api/v1/customer/login",
				new HttpEntity<>(new AuthenticationRequest(foreign.getEmailAddress(), "clear123"), jsonHeaders()),
				AuthenticationResponse.class).getBody();
		assertNotNull(foreignLogin);
		ReadableCustomer foreignProfile = rest.exchange("/api/v1/auth/customer/profile", GET,
				new HttpEntity<>(bearer(foreignLogin.getToken())), ReadableCustomer.class).getBody();
		session.foreignId = foreignProfile.getId();
		return session;
	}

	private ReadableProduct createProductForJourney() {
		return sampleProduct("UatJourney" + UUID.randomUUID().toString().replace("-", ""));
	}

	private void configureMoneyOrder(String token) {
		ObjectNode configuration = objectMapper.createObjectNode();
		configuration.put("code", "moneyorder");
		configuration.put("active", true);
		configuration.put("defaultSelected", true);
		configuration.putObject("integrationKeys").put("address", "Test payment address");
		ResponseEntity<Void> response = rest.postForEntity("/api/v1/private/modules/payment?store=DEFAULT",
				new HttpEntity<>(configuration, bearer(token)), Void.class);
		assertTrue(response.getStatusCode().is2xxSuccessful());
	}

	private ReadableShoppingCart createCart(String sku) {
		return rest.postForEntity("/api/v1/cart?store=DEFAULT",
				new HttpEntity<>(cartItem(sku, 1), jsonHeaders()), ReadableShoppingCart.class).getBody();
	}

	private PersistableShoppingCartItem cartItem(String sku, int quantity) {
		PersistableShoppingCartItem item = new PersistableShoppingCartItem();
		item.setProduct(sku);
		item.setQuantity(quantity);
		return item;
	}

	private ObjectNode checkoutBody(BigDecimal amount) {
		ObjectNode order = objectMapper.createObjectNode();
		order.put("currency", "USD");
		ObjectNode payment = order.putObject("payment");
		payment.put("paymentModule", "moneyorder");
		payment.put("paymentType", "MONEYORDER");
		payment.put("transactionType", "CAPTURE");
		payment.put("amount", amount.toPlainString());
		return order;
	}

	private Address address(String lastName) {
		Address address = new Address();
		address.setFirstName("UAT");
		address.setLastName(lastName);
		address.setCountry("BE");
		return address;
	}

	private HttpHeaders jsonHeaders() {
		HttpHeaders headers = new HttpHeaders();
		headers.setContentType(new MediaType("application", "json", StandardCharsets.UTF_8));
		return headers;
	}

	private HttpHeaders bearer(String token) {
		HttpHeaders headers = jsonHeaders();
		headers.setBearerAuth(token);
		return headers;
	}

	private HttpHeaders textHeaders() {
		HttpHeaders headers = new HttpHeaders();
		headers.setContentType(MediaType.TEXT_PLAIN);
		return headers;
	}

	private static class CustomerSession {
		private String email;
		private String token;
		private Long foreignId;
		private ReadableCustomer profile;
	}
}
