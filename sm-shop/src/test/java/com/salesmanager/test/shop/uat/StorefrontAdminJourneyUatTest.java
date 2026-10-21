package com.salesmanager.test.shop.uat;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;
import static org.springframework.http.HttpMethod.GET;
import static org.springframework.http.HttpMethod.POST;
import static org.springframework.http.HttpMethod.PUT;
import static org.springframework.http.HttpStatus.CREATED;
import static org.springframework.http.HttpStatus.ACCEPTED;
import static org.springframework.http.HttpStatus.OK;
import static org.springframework.http.HttpStatus.UNAUTHORIZED;

import java.math.BigDecimal;
import java.util.List;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.test.context.junit4.SpringRunner;

import com.fasterxml.jackson.databind.JsonNode;
import com.salesmanager.shop.application.ShopApplication;
import com.salesmanager.shop.model.catalog.product.ReadableProduct;
import com.salesmanager.shop.model.order.transaction.PersistablePayment;
import com.salesmanager.shop.model.order.v1.PersistableOrder;
import com.salesmanager.shop.model.order.v1.ReadableOrder;
import com.salesmanager.shop.model.order.v1.ReadableOrderConfirmation;
import com.salesmanager.shop.model.shoppingcart.PersistableShoppingCartItem;
import com.salesmanager.shop.model.shoppingcart.ReadableShoppingCart;
import com.salesmanager.shop.model.system.IntegrationModuleConfiguration;
import com.salesmanager.shop.store.security.AuthenticationRequest;
import com.salesmanager.shop.store.security.AuthenticationResponse;
import com.salesmanager.shop.model.customer.PersistableCustomer;
import com.salesmanager.shop.model.customer.ReadableCustomer;
import com.salesmanager.shop.model.customer.address.Address;
import com.salesmanager.test.shop.common.ServicesTestSupport;

@RunWith(SpringRunner.class)
@SpringBootTest(classes = ShopApplication.class, webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
public class StorefrontAdminJourneyUatTest extends ServicesTestSupport {

	@Test
	public void adminCanLoginAndReadProfileWhileAnonymousRequestsAreRejected() {
		ResponseEntity<AuthenticationResponse> login = testRestTemplate.postForEntity("/api/v1/private/login",
				new HttpEntity<>(new AuthenticationRequest("admin@shopizer.com", "password")), AuthenticationResponse.class);
		assertEquals(OK, login.getStatusCode());
		assertNotNull(login.getBody());
		assertNotNull(login.getBody().getToken());

		HttpHeaders authenticated = bearerHeaders(login.getBody().getToken());
		ResponseEntity<JsonNode> profile = testRestTemplate.exchange("/api/v1/private/user/profile?store=DEFAULT",
				GET, new HttpEntity<>(authenticated), JsonNode.class);
		assertEquals(OK, profile.getStatusCode());
		assertEquals(UNAUTHORIZED, testRestTemplate.exchange("/api/v1/private/user/profile?store=DEFAULT",
				GET, new HttpEntity<>(new HttpHeaders()), JsonNode.class).getStatusCode());

		HttpHeaders invalid = new HttpHeaders();
		invalid.setBearerAuth("eyJhbGciOiJIUzI1NiJ9.eyJzdWIiOiJub25lIn0.AAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAA");
		assertTrue(testRestTemplate.exchange("/api/v1/private/user/profile?store=DEFAULT",
				GET, new HttpEntity<>(invalid), JsonNode.class).getStatusCode().isError());
	}

	@Test
	public void customerCanRegisterLoginAndReadProfile() {
		String email = "uat-customer-" + System.nanoTime() + "@example.com";
		PersistableCustomer customer = new PersistableCustomer();
		customer.setEmailAddress(email);
		customer.setPassword("clear123");
		customer.setLanguage("en");
		customer.setStoreCode("DEFAULT");
		customer.setFirstName("Uat");
		customer.setLastName("Customer");
		Address billing = new Address();
		billing.setFirstName("Uat");
		billing.setLastName("Customer");
		billing.setCountry("BE");
		customer.setBilling(billing);
		ResponseEntity<ReadableCustomer> registered = testRestTemplate.postForEntity("/api/v1/customer/register",
				new HttpEntity<>(customer, getHeader()), ReadableCustomer.class);
		assertEquals(OK, registered.getStatusCode());

		ResponseEntity<AuthenticationResponse> login = testRestTemplate.postForEntity("/api/v1/customer/login",
				new HttpEntity<>(new AuthenticationRequest(email, "clear123")), AuthenticationResponse.class);
		assertEquals(OK, login.getStatusCode());
		assertNotNull(login.getBody());
		ResponseEntity<ReadableCustomer> profile = testRestTemplate.exchange(
				"/api/v1/auth/customer/profile?store=DEFAULT", GET,
				new HttpEntity<>(bearerHeaders(login.getBody().getToken())), ReadableCustomer.class);
		assertEquals(OK, profile.getStatusCode());
		assertEquals(email, profile.getBody().getEmailAddress());
	}

	@Test
	public void storefrontCanBrowseCatalogAndReadProductDetails() {
		assertEquals(OK, testRestTemplate.getForEntity("/api/v1/store/DEFAULT", JsonNode.class).getStatusCode());
		assertEquals(OK, testRestTemplate.getForEntity("/api/v1/category?store=DEFAULT", JsonNode.class).getStatusCode());
		assertEquals(OK, testRestTemplate.getForEntity("/api/v1/products?store=DEFAULT", JsonNode.class).getStatusCode());

		ReadableProduct product = sampleProduct("uat-browse-" + System.nanoTime());
		ResponseEntity<ReadableProduct> detail = testRestTemplate.getForEntity(
				"/api/v2/product/" + product.getSku(), ReadableProduct.class);
		assertEquals(OK, detail.getStatusCode());
		assertNotNull(detail.getBody());
		assertEquals(product.getSku(), detail.getBody().getSku());
		assertNotNull(detail.getBody().getId());
	}

	@Test
	public void cartCanBeCreatedUpdatedAndReadWithTotals() {
		ReadableProduct product = sampleProduct("uat-cart-" + System.nanoTime());
		PersistableShoppingCartItem firstItem = new PersistableShoppingCartItem();
		firstItem.setProduct(product.getSku());
		firstItem.setQuantity(1);
		ResponseEntity<ReadableShoppingCart> created = testRestTemplate.postForEntity("/api/v1/cart",
				new HttpEntity<>(firstItem, getHeader()), ReadableShoppingCart.class);
		assertEquals(CREATED, created.getStatusCode());
		assertNotNull(created.getBody());

		PersistableShoppingCartItem updateItem = new PersistableShoppingCartItem();
		updateItem.setProduct(product.getSku());
		updateItem.setQuantity(2);
		ResponseEntity<ReadableShoppingCart> updated = testRestTemplate.exchange(
				"/api/v1/cart/" + created.getBody().getCode(), PUT,
				new HttpEntity<>(updateItem, getHeader()), ReadableShoppingCart.class);
		assertEquals(CREATED, updated.getStatusCode());

		ResponseEntity<ReadableShoppingCart> cart = testRestTemplate.getForEntity(
				"/api/v1/cart/" + created.getBody().getCode(), ReadableShoppingCart.class);
		assertEquals(OK, cart.getStatusCode());
		assertEquals(2, cart.getBody().getQuantity());
		assertTrue(cart.getBody().getSubtotal().compareTo(BigDecimal.ZERO) > 0);
		assertTrue(cart.getBody().getTotal().compareTo(BigDecimal.ZERO) > 0);
	}

	@Test
	public void customerCanCheckoutAndAdminCanReadTheOrder() {
		String email = "uat-checkout-" + System.nanoTime() + "@example.com";
		PersistableCustomer customer = new PersistableCustomer();
		customer.setEmailAddress(email);
		customer.setPassword("clear123");
		customer.setLanguage("en");
		customer.setStoreCode("DEFAULT");
		Address billing = new Address();
		billing.setFirstName("Uat");
		billing.setLastName("Checkout");
		billing.setCountry("BE");
		customer.setBilling(billing);
		assertEquals(OK, testRestTemplate.postForEntity("/api/v1/customer/register",
				new HttpEntity<>(customer, getHeader()), ReadableCustomer.class).getStatusCode());
		ResponseEntity<AuthenticationResponse> login = testRestTemplate.postForEntity("/api/v1/customer/login",
				new HttpEntity<>(new AuthenticationRequest(email, "clear123")), AuthenticationResponse.class);
		assertEquals(OK, login.getStatusCode());
		HttpHeaders customerHeaders = bearerHeaders(login.getBody().getToken());

		IntegrationModuleConfiguration moneyorder = new IntegrationModuleConfiguration();
		moneyorder.setCode("moneyorder");
		moneyorder.setActive(true);
		moneyorder.setDefaultSelected(true);
		moneyorder.getIntegrationKeys().put("address", "Shopizer test payment address");
		assertEquals(OK, testRestTemplate.postForEntity("/api/v1/private/modules/payment?store=DEFAULT",
				new HttpEntity<>(moneyorder, getHeader()), String.class).getStatusCode());

		ReadableProduct product = sampleProduct("uat-checkout-" + System.nanoTime());
		PersistableShoppingCartItem cartItem = new PersistableShoppingCartItem();
		cartItem.setProduct(product.getSku());
		cartItem.setQuantity(1);
		ResponseEntity<ReadableShoppingCart> cart = testRestTemplate.postForEntity("/api/v1/cart?store=DEFAULT",
				new HttpEntity<>(cartItem, customerHeaders), ReadableShoppingCart.class);
		assertEquals(CREATED, cart.getStatusCode());

		PersistablePayment payment = new PersistablePayment();
		payment.setPaymentModule("moneyorder");
		payment.setPaymentType("MONEYORDER");
		payment.setTransactionType("CAPTURE");
		payment.setAmount(cart.getBody().getTotal().toPlainString());
		PersistableOrder order = new PersistableOrder();
		order.setId(0L);
		order.setCurrency("USD");
		order.setCustomerAgreement(true);
		order.setPayment(payment);
		ResponseEntity<ReadableOrderConfirmation> confirmation = testRestTemplate.postForEntity(
				"/api/v1/auth/cart/" + cart.getBody().getCode() + "/checkout?store=DEFAULT",
				new HttpEntity<>(order, customerHeaders), ReadableOrderConfirmation.class);
		assertEquals(OK, confirmation.getStatusCode());
		assertNotNull(confirmation.getBody());
		assertTrue(confirmation.getBody().getId() > 0);
		assertEquals(OK, testRestTemplate.exchange("/api/v1/auth/orders/" + confirmation.getBody().getId(),
				GET, new HttpEntity<>(customerHeaders), ReadableOrder.class).getStatusCode());
		assertEquals(OK, testRestTemplate.exchange("/api/v1/private/orders/" + confirmation.getBody().getId(),
				GET, new HttpEntity<>(getHeader()), ReadableOrder.class).getStatusCode());
	}

	@Test
	public void serviceEndpointsEnforceAuthentication() {
		assertEquals(ACCEPTED,
				testRestTemplate.getForEntity("/services/public/DEFAULT", JsonNode.class).getStatusCode());

		HttpHeaders invalid = new HttpHeaders();
		invalid.setBasicAuth("wrong-user", "wrong-password");
		assertEquals(UNAUTHORIZED, testRestTemplate.exchange("/services/private/DEFAULT", GET,
				new HttpEntity<>(invalid), JsonNode.class).getStatusCode());

		HttpHeaders admin = new HttpHeaders();
		admin.setBasicAuth("admin@shopizer.com", "password");
		HttpStatus adminStatus = testRestTemplate.exchange("/services/private/DEFAULT", GET,
				new HttpEntity<>(admin), JsonNode.class).getStatusCode();
		assertTrue(adminStatus != UNAUTHORIZED);
	}

	@Test
	public void healthAndApiDocumentationAreAvailable() {
		ResponseEntity<JsonNode> health = testRestTemplate.getForEntity("/actuator/health", JsonNode.class);
		assertEquals(OK, health.getStatusCode());
		assertEquals("UP", health.getBody().path("status").asText());
		ResponseEntity<JsonNode> apiDocs = testRestTemplate.getForEntity("/v2/api-docs", JsonNode.class);
		assertEquals(OK, apiDocs.getStatusCode());
		assertTrue(apiDocs.getBody().has("swagger") || apiDocs.getBody().has("paths"));
	}

	private HttpHeaders bearerHeaders(String token) {
		HttpHeaders headers = new HttpHeaders();
		headers.setBearerAuth(token);
		return headers;
	}
}
