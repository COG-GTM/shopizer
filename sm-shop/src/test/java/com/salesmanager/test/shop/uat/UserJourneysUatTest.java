package com.salesmanager.test.shop.uat;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
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

import javax.inject.Inject;

import com.salesmanager.core.business.constants.Constants;
import com.salesmanager.core.business.services.merchant.MerchantStoreService;
import com.salesmanager.core.business.services.payments.PaymentService;
import com.salesmanager.core.model.merchant.MerchantStore;
import com.salesmanager.core.model.system.IntegrationConfiguration;
import com.salesmanager.core.model.customer.CustomerGender;
import com.salesmanager.shop.application.ShopApplication;
import com.salesmanager.shop.model.catalog.product.ReadableProduct;
import com.salesmanager.shop.model.customer.PersistableCustomer;
import com.salesmanager.shop.model.customer.address.Address;
import com.salesmanager.shop.model.order.v1.PersistableOrder;
import com.salesmanager.shop.model.order.v1.ReadableOrderConfirmation;
import com.salesmanager.shop.model.order.transaction.PersistablePayment;
import com.salesmanager.shop.model.shoppingcart.PersistableShoppingCartItem;
import com.salesmanager.shop.model.shoppingcart.ReadableShoppingCart;
import com.salesmanager.shop.store.security.AuthenticationRequest;
import com.salesmanager.shop.store.security.AuthenticationResponse;
import com.salesmanager.test.shop.common.ServicesTestSupport;

@SpringBootTest(classes = ShopApplication.class, webEnvironment = WebEnvironment.RANDOM_PORT)
@RunWith(SpringRunner.class)
public class UserJourneysUatTest extends ServicesTestSupport {

	private static final String SUPERADMIN_USER = "admin@shopizer.com";
	private static final String SUPERADMIN_PASSWORD = "password";

	@Inject
	private TestRestTemplate testRestTemplate;

	@Inject
	private MerchantStoreService merchantStoreService;

	@Inject
	private PaymentService paymentService;

	@Test
	public void completesCustomerAdminCatalogCartAndSystemJourneys() throws Exception {
		AuthenticationResponse adminLogin = login(SUPERADMIN_USER, SUPERADMIN_PASSWORD);
		HttpHeaders adminHeaders = bearerHeaders(adminLogin.getToken());
		assertEquals(HttpStatus.OK, get("/api/v1/private/users/1", adminHeaders).getStatusCode());
		assertUnauthorized(get("/api/v1/private/users/1", new HttpHeaders()).getStatusCode());

		String customerUser = "uat-customer-" + System.nanoTime() + "@example.com";
		registerCustomer(customerUser);
		AuthenticationResponse customerLogin = login(customerUser, "Customer123");
		HttpHeaders customerHeaders = bearerHeaders(customerLogin.getToken());
		ResponseEntity<String> profile = get("/api/v1/auth/customer/profile", customerHeaders);
		assertEquals(profile.toString(), HttpStatus.OK, profile.getStatusCode());

		assertEquals(HttpStatus.OK, get("/api/v1/store/DEFAULT", new HttpHeaders()).getStatusCode());
		assertEquals(HttpStatus.OK, get("/api/v1/category?store=DEFAULT", new HttpHeaders()).getStatusCode());
		assertEquals(HttpStatus.OK, get("/api/v1/products?store=DEFAULT", new HttpHeaders()).getStatusCode());

		String productCode = "uat-product-" + System.nanoTime();
		ReadableProduct product = sampleProduct(productCode);
		assertNotNull(product);
		ResponseEntity<String> productDetails = get("/api/v2/product/" + productCode + "?store=DEFAULT",
				new HttpHeaders());
		assertEquals(productDetails.toString(), HttpStatus.OK, productDetails.getStatusCode());

		PersistableShoppingCartItem cartItem = new PersistableShoppingCartItem();
		cartItem.setProduct(product.getSku());
		cartItem.setQuantity(1);
		ResponseEntity<ReadableShoppingCart> cartResponse = testRestTemplate.postForEntity(
				"/api/v1/cart?store=DEFAULT", new HttpEntity<PersistableShoppingCartItem>(cartItem, customerHeaders),
				ReadableShoppingCart.class);
		assertEquals(cartResponse.toString(), HttpStatus.CREATED, cartResponse.getStatusCode());
		assertNotNull(cartResponse.getBody());
		String cartCode = cartResponse.getBody().getCode();

		cartItem.setQuantity(2);
		ResponseEntity<ReadableShoppingCart> updatedCart = testRestTemplate.exchange(
				"/api/v1/cart/" + cartCode + "?store=DEFAULT", HttpMethod.PUT,
				new HttpEntity<PersistableShoppingCartItem>(cartItem, customerHeaders), ReadableShoppingCart.class);
		assertEquals(updatedCart.toString(), HttpStatus.CREATED, updatedCart.getStatusCode());
		ResponseEntity<ReadableShoppingCart> cartReadBack = testRestTemplate.exchange(
				"/api/v1/cart/" + cartCode + "?store=DEFAULT", HttpMethod.GET,
				new HttpEntity<Void>(customerHeaders), ReadableShoppingCart.class);
		assertEquals(cartReadBack.toString(), HttpStatus.OK, cartReadBack.getStatusCode());
		assertNotNull(cartReadBack.getBody());
		assertEquals(2, cartReadBack.getBody().getQuantity());

		configureMoneyOrder();
		PersistableOrder order = new PersistableOrder();
		order.setCurrency("USD");
		order.setCustomerAgreement(true);
		PersistablePayment payment = new PersistablePayment();
		payment.setAmount(cartReadBack.getBody().getTotal().setScale(2, BigDecimal.ROUND_HALF_UP).toPlainString());
		payment.setPaymentModule("moneyorder");
		payment.setPaymentType("MONEYORDER");
		payment.setTransactionType("CAPTURE");
		order.setPayment(payment);
		ResponseEntity<ReadableOrderConfirmation> checkout = testRestTemplate.postForEntity(
				"/api/v1/auth/cart/" + cartCode + "/checkout?store=DEFAULT",
				new HttpEntity<PersistableOrder>(order, customerHeaders), ReadableOrderConfirmation.class);
		assertEquals(checkout.toString(), HttpStatus.OK, checkout.getStatusCode());
		assertNotNull(checkout.getBody());
		assertNotNull(checkout.getBody().getId());
		ResponseEntity<String> orderReadBack = get("/api/v1/auth/orders/" + checkout.getBody().getId()
				+ "?store=DEFAULT", customerHeaders);
		assertEquals(orderReadBack.toString(), HttpStatus.OK, orderReadBack.getStatusCode());

		HttpHeaders superadminBasic = basicHeaders(SUPERADMIN_USER, SUPERADMIN_PASSWORD);
		ResponseEntity<String> validModule = testRestTemplate.exchange("/services/private/system/module",
				HttpMethod.POST, new HttpEntity<String>(upsJson(), superadminBasic), String.class);
		assertEquals(validModule.toString(), HttpStatus.OK, validModule.getStatusCode());
		ResponseEntity<String> invalidCredentials = testRestTemplate.exchange(
				"/services/private/system/module", HttpMethod.POST,
				new HttpEntity<String>(upsJson(), basicHeaders(SUPERADMIN_USER, "wrong-password")), String.class);
		assertAuthenticationDenied(invalidCredentials.getStatusCode());

		ResponseEntity<String> health = get("/actuator/health", new HttpHeaders());
		assertEquals(health.toString(), HttpStatus.OK, health.getStatusCode());
		assertTrue(health.getBody(), health.getBody().contains("\"status\":\"UP\""));
		assertEquals(HttpStatus.OK, get("/v2/api-docs", new HttpHeaders()).getStatusCode());
	}

	private AuthenticationResponse login(String username, String password) {
		ResponseEntity<AuthenticationResponse> response;
		if (SUPERADMIN_USER.equals(username)) {
			response = testRestTemplate.postForEntity("/api/v1/private/login",
					new HttpEntity<AuthenticationRequest>(new AuthenticationRequest(username, password)),
					AuthenticationResponse.class);
		} else {
			response = testRestTemplate.postForEntity("/api/v1/customer/login",
					new HttpEntity<AuthenticationRequest>(new AuthenticationRequest(username, password)),
					AuthenticationResponse.class);
		}
		assertEquals(response.toString(), HttpStatus.OK, response.getStatusCode());
		assertNotNull(response.getBody());
		assertNotNull(response.getBody().getToken());
		return response.getBody();
	}

	private void registerCustomer(String username) {
		PersistableCustomer customer = new PersistableCustomer();
		customer.setEmailAddress(username);
		customer.setPassword("Customer123");
		customer.setGender(CustomerGender.M.name());
		customer.setLanguage("en");
		customer.setStoreCode(Constants.DEFAULT_STORE);
		Address billing = new Address();
		billing.setFirstName("UAT");
		billing.setLastName("Customer");
		billing.setCountry("US");
		billing.setAddress("1 Main Street");
		billing.setCity("New York");
		customer.setBilling(billing);
		ResponseEntity<String> response = testRestTemplate.postForEntity("/api/v1/customer/register",
				new HttpEntity<PersistableCustomer>(customer), String.class);
		assertEquals(response.toString(), HttpStatus.OK, response.getStatusCode());
	}

	private void configureMoneyOrder() throws Exception {
		IntegrationConfiguration configuration = new IntegrationConfiguration();
		configuration.setModuleCode("moneyorder");
		configuration.setActive(true);
		configuration.setIntegrationKeys(Collections.singletonMap("address", "1 Main Street"));
		MerchantStore store = merchantStoreService.getByCode(Constants.DEFAULT_STORE);
		paymentService.savePaymentModuleConfiguration(configuration, store);
	}

	private ResponseEntity<String> get(String path, HttpHeaders headers) {
		return testRestTemplate.exchange(path, HttpMethod.GET, new HttpEntity<Void>(headers), String.class);
	}

	private static HttpHeaders bearerHeaders(String token) {
		HttpHeaders headers = new HttpHeaders();
		headers.setBearerAuth(token);
		headers.setContentType(MediaType.APPLICATION_JSON);
		return headers;
	}

	private static HttpHeaders basicHeaders(String username, String password) {
		HttpHeaders headers = new HttpHeaders();
		headers.setContentType(MediaType.parseMediaType("text/plain;charset=UTF-8"));
		headers.setBasicAuth(username, password, StandardCharsets.UTF_8);
		return headers;
	}

	private static void assertUnauthorized(HttpStatus status) {
		assertTrue("Expected an authorization error but received " + status,
				status == HttpStatus.UNAUTHORIZED || status == HttpStatus.FORBIDDEN);
	}

	private static void assertAuthenticationDenied(HttpStatus status) {
		assertTrue("Expected authentication to be denied but received " + status,
				status == HttpStatus.UNAUTHORIZED || status == HttpStatus.FORBIDDEN || status == HttpStatus.FOUND);
	}

	private String upsJson() throws Exception {
		try (java.io.InputStream input = getClass().getClassLoader()
				.getResourceAsStream("reference/integrationmodules.json")) {
			com.fasterxml.jackson.databind.JsonNode modules = new com.fasterxml.jackson.databind.ObjectMapper()
					.readTree(input);
			for (com.fasterxml.jackson.databind.JsonNode module : modules) {
				if ("ups".equals(module.path("code").asText())) {
					return new com.fasterxml.jackson.databind.ObjectMapper().writeValueAsString(module);
				}
			}
		}
		throw new IllegalStateException("Reference UPS module not found");
	}
}
