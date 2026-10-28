package com.salesmanager.test.shop.uat;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.Collections;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.SpringBootTest.WebEnvironment;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.test.context.junit4.SpringRunner;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.salesmanager.core.business.constants.Constants;
import com.salesmanager.core.model.customer.CustomerGender;
import com.salesmanager.shop.application.ShopApplication;
import com.salesmanager.shop.model.catalog.product.ReadableProduct;
import com.salesmanager.shop.model.customer.PersistableCustomer;
import com.salesmanager.shop.model.customer.address.Address;
import com.salesmanager.shop.model.order.transaction.PersistablePayment;
import com.salesmanager.shop.model.order.v1.PersistableOrder;
import com.salesmanager.shop.model.shoppingcart.PersistableShoppingCartItem;
import com.salesmanager.shop.model.shoppingcart.ReadableShoppingCart;
import com.salesmanager.shop.model.system.IntegrationModuleConfiguration;
import com.salesmanager.shop.store.security.AuthenticationRequest;
import com.salesmanager.shop.store.security.AuthenticationResponse;
import com.salesmanager.test.shop.common.ServicesTestSupport;

@SpringBootTest(classes = ShopApplication.class, webEnvironment = WebEnvironment.RANDOM_PORT)
@RunWith(SpringRunner.class)
public class StorefrontUserJourneyUatTest extends ServicesTestSupport {

	@Test
	public void adminAuthenticationRequiresAValidBearerToken() {
		ResponseEntity<AuthenticationResponse> login = testRestTemplate.postForEntity("/api/v1/private/login",
				new HttpEntity<AuthenticationRequest>(new AuthenticationRequest("admin@shopizer.com", "password")),
				AuthenticationResponse.class);
		assertEquals(HttpStatus.OK, login.getStatusCode());
		String token = login.getBody().getToken();
		assertNotNull(token);

		ResponseEntity<String> allowed = testRestTemplate.exchange("/api/v1/private/customers?store=DEFAULT",
				HttpMethod.GET, new HttpEntity<String>(bearer(token)), String.class);
		assertEquals(HttpStatus.OK, allowed.getStatusCode());
		assertEquals(HttpStatus.UNAUTHORIZED, testRestTemplate.getForEntity(
				"/api/v1/private/customers?store=DEFAULT", String.class).getStatusCode());

	}

	@Test
	public void customerCanRegisterLoginAndReadTheirProfile() {
		PersistableCustomer customer = newCustomer();
		ResponseEntity<String> registration = testRestTemplate.postForEntity("/api/v1/customer/register",
				new HttpEntity<PersistableCustomer>(customer, getHeader()), String.class);
		assertTrue(registration.getStatusCode() == HttpStatus.OK
				|| registration.getStatusCode() == HttpStatus.CREATED);

		ResponseEntity<AuthenticationResponse> login = testRestTemplate.postForEntity("/api/v1/customer/login",
				new HttpEntity<AuthenticationRequest>(
						new AuthenticationRequest(customer.getEmailAddress(), customer.getPassword())),
				AuthenticationResponse.class);
		assertEquals(HttpStatus.OK, login.getStatusCode());

		String profileUrl = "/api/v1/auth/customer/profile?store=DEFAULT";
		ResponseEntity<String> profile = testRestTemplate.exchange(profileUrl, HttpMethod.GET,
				new HttpEntity<String>(bearer(login.getBody().getToken())), String.class);
		assertEquals(HttpStatus.OK, profile.getStatusCode());
		assertTrue(profile.getBody().contains(customer.getEmailAddress()));
		assertEquals(HttpStatus.UNAUTHORIZED, testRestTemplate.getForEntity(profileUrl, String.class).getStatusCode());
	}

	@Test
	public void storefrontCanBrowseStoreCategoriesAndProducts() {
		assertEquals(HttpStatus.OK, testRestTemplate.getForEntity("/api/v1/store/DEFAULT", String.class).getStatusCode());
		assertEquals(HttpStatus.OK,
				testRestTemplate.getForEntity("/api/v1/category?store=DEFAULT", String.class).getStatusCode());

		String sku = "uat-browse-" + System.currentTimeMillis();
		ReadableProduct product = sampleProduct(sku);
		ResponseEntity<String> products = testRestTemplate.getForEntity(
				"/api/v1/products?store=DEFAULT&sku=" + sku, String.class);
		assertEquals(HttpStatus.OK, products.getStatusCode());
		assertTrue(products.getBody().contains(sku));

		ResponseEntity<String> detail = testRestTemplate.getForEntity(
				"/api/v2/product/" + product.getSku() + "?store=DEFAULT", String.class);
		assertEquals(HttpStatus.OK, detail.getStatusCode());
		assertTrue(detail.getBody().contains("\"sku\":\"" + sku + "\""));
	}

	@Test
	public void shopperCanCreateUpdateAndReadCart() {
		ReadableProduct product = sampleProduct("uat-cart-" + System.currentTimeMillis());
		PersistableShoppingCartItem item = new PersistableShoppingCartItem();
		item.setProduct(product.getSku());
		item.setQuantity(1);

		ResponseEntity<ReadableShoppingCart> created = testRestTemplate.postForEntity(
				"/api/v1/cart?store=DEFAULT", new HttpEntity<PersistableShoppingCartItem>(item, getHeader()),
				ReadableShoppingCart.class);
		assertEquals(HttpStatus.CREATED, created.getStatusCode());
		assertEquals(1, created.getBody().getQuantity());

		item.setQuantity(2);
		ResponseEntity<ReadableShoppingCart> updated = testRestTemplate.exchange(
				"/api/v1/cart/" + created.getBody().getCode() + "?store=DEFAULT", HttpMethod.PUT,
				new HttpEntity<PersistableShoppingCartItem>(item, getHeader()), ReadableShoppingCart.class);
		assertEquals(HttpStatus.CREATED, updated.getStatusCode());

		ResponseEntity<ReadableShoppingCart> read = testRestTemplate.getForEntity(
				"/api/v1/cart/" + created.getBody().getCode() + "?store=DEFAULT", ReadableShoppingCart.class);
		assertEquals(HttpStatus.OK, read.getStatusCode());
		assertEquals(2, read.getBody().getQuantity());
	}

	@Test
	public void registeredCustomerCanCheckoutCartAndReadOrder() throws Exception {
		PersistableCustomer customer = newCustomer();
		ResponseEntity<String> registration = testRestTemplate.postForEntity("/api/v1/customer/register",
				new HttpEntity<PersistableCustomer>(customer, getHeader()), String.class);
		assertTrue(registration.getStatusCode() == HttpStatus.OK
				|| registration.getStatusCode() == HttpStatus.CREATED);
		ResponseEntity<AuthenticationResponse> login = testRestTemplate.postForEntity("/api/v1/customer/login",
				new HttpEntity<AuthenticationRequest>(
						new AuthenticationRequest(customer.getEmailAddress(), customer.getPassword())),
				AuthenticationResponse.class);
		assertEquals(HttpStatus.OK, login.getStatusCode());

		ReadableProduct product = sampleProduct("uat-checkout-" + System.currentTimeMillis());
		PersistableShoppingCartItem item = new PersistableShoppingCartItem();
		item.setProduct(product.getSku());
		item.setQuantity(1);
		ResponseEntity<ReadableShoppingCart> cart = testRestTemplate.postForEntity("/api/v1/cart?store=DEFAULT",
				new HttpEntity<PersistableShoppingCartItem>(item, getHeader()), ReadableShoppingCart.class);
		assertEquals(HttpStatus.CREATED, cart.getStatusCode());

		PersistableOrder order = new PersistableOrder();
		order.setCurrency("CAD");
		order.setCustomerAgreement(true);
		PersistablePayment payment = new PersistablePayment();
		payment.setPaymentType("MONEYORDER");
		payment.setTransactionType("CAPTURE");
		payment.setPaymentModule("moneyorder");
		payment.setAmount(cart.getBody().getTotal().toPlainString());
		order.setPayment(payment);

		IntegrationModuleConfiguration paymentConfiguration = new IntegrationModuleConfiguration();
		paymentConfiguration.setCode("moneyorder");
		paymentConfiguration.setActive(true);
		paymentConfiguration.setDefaultSelected(true);
		paymentConfiguration.setIntegrationKeys(Collections.singletonMap("address", "Test payment address"));
		ResponseEntity<String> configured = testRestTemplate.postForEntity(
				"/api/v1/private/modules/payment?store=DEFAULT",
				new HttpEntity<IntegrationModuleConfiguration>(paymentConfiguration, getHeader()), String.class);
		assertEquals(HttpStatus.OK, configured.getStatusCode());

		HttpHeaders headers = bearer(login.getBody().getToken());
		ResponseEntity<String> confirmation = testRestTemplate.postForEntity(
				"/api/v1/auth/cart/" + cart.getBody().getCode() + "/checkout?store=DEFAULT",
				new HttpEntity<PersistableOrder>(order, headers), String.class);
		assertEquals(confirmation.getBody(), HttpStatus.OK, confirmation.getStatusCode());
		JsonNode confirmationJson = new ObjectMapper().readTree(confirmation.getBody());
		String orderId = confirmationJson.path("id").asText();
		assertTrue("order confirmation omitted id: " + confirmation.getBody(), !orderId.isEmpty());

		ResponseEntity<String> persisted = testRestTemplate.exchange(
				"/api/v1/auth/orders/" + orderId + "?store=DEFAULT", HttpMethod.GET,
				new HttpEntity<String>(headers), String.class);
		assertEquals(HttpStatus.OK, persisted.getStatusCode());
	}

	@Test
	public void contactAcceptsValidEmail() {
		com.salesmanager.shop.model.shop.ContactForm contact = contact("visitor@example.com");
		assertEquals(HttpStatus.OK, testRestTemplate.postForEntity("/api/v1/contact?store=DEFAULT",
				new HttpEntity<com.salesmanager.shop.model.shop.ContactForm>(contact), String.class).getStatusCode());
	}

	@Test
	public void servicesRequireValidBasicAuthenticationExceptPublicEndpoints() {
		String credentials = Base64.getEncoder().encodeToString(
				"admin@shopizer.com:password".getBytes(StandardCharsets.UTF_8));
		HttpHeaders validHeaders = new HttpHeaders();
		validHeaders.set("Authorization", "Basic " + credentials);
		validHeaders.setContentType(MediaType.TEXT_PLAIN);
		ResponseEntity<String> valid = testRestTemplate.exchange("/services/private/system/optin",
				HttpMethod.POST, new HttpEntity<String>("uat", validHeaders), String.class);
		assertTrue(valid.getStatusCode() != HttpStatus.UNAUTHORIZED && valid.getStatusCode() != HttpStatus.FORBIDDEN);

		HttpHeaders invalidHeaders = new HttpHeaders();
		invalidHeaders.set("Authorization", "Basic " + Base64.getEncoder().encodeToString(
				"admin@shopizer.com:wrong-password".getBytes(StandardCharsets.UTF_8)));
		ResponseEntity<String> invalid = testRestTemplate.exchange("/services/private/system/optin",
				HttpMethod.POST, new HttpEntity<String>("uat", invalidHeaders), String.class);
		assertEquals(HttpStatus.UNAUTHORIZED, invalid.getStatusCode());
		ResponseEntity<String> publicService = testRestTemplate.getForEntity("/services/public/DEFAULT", String.class);
		assertTrue(publicService.getStatusCode() != HttpStatus.UNAUTHORIZED
				&& publicService.getStatusCode() != HttpStatus.FORBIDDEN);
	}

	@Test
	public void healthAndSwaggerEndpointsAreAvailable() {
		ResponseEntity<String> health = testRestTemplate.getForEntity("/actuator/health", String.class);
		if (health.getStatusCode() == HttpStatus.OK && health.getBody().contains("\"status\":\"UP\"")) {
			assertTrue(health.getBody().contains("\"status\":\"UP\""));
		} else {
			assertEquals(HttpStatus.OK,
					testRestTemplate.getForEntity("/actuator/health/ping", String.class).getStatusCode());
		}

		ResponseEntity<String> swagger = testRestTemplate.getForEntity("/v2/api-docs", String.class);
		assertEquals(HttpStatus.OK, swagger.getStatusCode());
		assertTrue(swagger.getBody().contains("\"swagger\":\"2.0\""));
		assertTrue(swagger.getBody().contains("/api/v1/contact"));
	}

	private PersistableCustomer newCustomer() {
		String email = "uat-" + System.currentTimeMillis() + "@example.com";
		PersistableCustomer customer = new PersistableCustomer();
		customer.setEmailAddress(email);
		customer.setPassword("clear123");
		customer.setGender(CustomerGender.M.name());
		customer.setLanguage("en");
		customer.setStoreCode(Constants.DEFAULT_STORE);
		Address billing = new Address();
		billing.setFirstName("Uat");
		billing.setLastName("Customer");
		billing.setCountry("BE");
		customer.setBilling(billing);
		return customer;
	}

	private HttpHeaders bearer(String token) {
		HttpHeaders headers = new HttpHeaders();
		headers.setContentType(MediaType.APPLICATION_JSON);
		headers.set("Authorization", "Bearer " + token);
		return headers;
	}

	private com.salesmanager.shop.model.shop.ContactForm contact(String email) {
		com.salesmanager.shop.model.shop.ContactForm contact = new com.salesmanager.shop.model.shop.ContactForm();
		contact.setName("UAT visitor");
		contact.setEmail(email);
		contact.setSubject("UAT");
		contact.setComment("UAT contact message");
		return contact;
	}
}
