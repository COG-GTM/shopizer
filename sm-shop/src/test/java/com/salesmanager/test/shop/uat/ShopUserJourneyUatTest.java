package com.salesmanager.test.shop.uat;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import java.nio.charset.StandardCharsets;
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
import org.springframework.http.ResponseEntity;
import org.springframework.test.context.junit4.SpringRunner;

import com.salesmanager.core.business.constants.Constants;
import com.salesmanager.core.model.customer.CustomerGender;
import com.salesmanager.shop.application.ShopApplication;
import com.salesmanager.shop.model.catalog.product.ReadableProduct;
import com.salesmanager.shop.model.customer.PersistableCustomer;
import com.salesmanager.shop.model.customer.ReadableCustomer;
import com.salesmanager.shop.model.customer.address.Address;
import com.salesmanager.shop.model.order.v1.ReadableOrder;
import com.salesmanager.shop.model.order.v1.ReadableOrderConfirmation;
import com.salesmanager.shop.model.shoppingcart.PersistableShoppingCartItem;
import com.salesmanager.shop.model.shoppingcart.ReadableShoppingCart;
import com.salesmanager.shop.model.store.ReadableMerchantStore;
import com.salesmanager.shop.model.system.IntegrationModuleConfiguration;
import com.salesmanager.shop.store.security.AuthenticationRequest;
import com.salesmanager.shop.store.security.AuthenticationResponse;
import com.salesmanager.test.shop.common.ServicesTestSupport;

@SpringBootTest(classes = ShopApplication.class, webEnvironment = WebEnvironment.RANDOM_PORT)
@RunWith(SpringRunner.class)
public class ShopUserJourneyUatTest extends ServicesTestSupport {

	private static final String ADMIN = "admin@shopizer.com";
	private static final String ADMIN_PASSWORD = "password";

	@Test
	public void adminAndServicesJourneysAuthenticateAndExposeOperationalEndpoints() {
		ResponseEntity<AuthenticationResponse> login = testRestTemplate.postForEntity("/api/v1/private/login",
				new HttpEntity<>(new AuthenticationRequest(ADMIN, ADMIN_PASSWORD)), AuthenticationResponse.class);
		assertEquals(HttpStatus.OK, login.getStatusCode());
		assertNotNull(login.getBody());

		HttpHeaders adminToken = bearer(login.getBody().getToken());
		assertEquals(HttpStatus.OK, get("/api/v1/private/users/1", adminToken).getStatusCode());
		assertEquals(HttpStatus.UNAUTHORIZED, get("/api/v1/private/users/1", new HttpHeaders()).getStatusCode());
		assertEquals(HttpStatus.UNAUTHORIZED, get("/api/v1/private/users/1", bearer("not-a-jwt")).getStatusCode());

		HttpHeaders validBasic = new HttpHeaders();
		validBasic.setBasicAuth(ADMIN, ADMIN_PASSWORD, StandardCharsets.UTF_8);
		ResponseEntity<String> basicSuccess = testRestTemplate.exchange("/services/private/system/optin",
				HttpMethod.POST, new HttpEntity<>("uat-optin", validBasic), String.class);
		assertTrue(basicSuccess.getStatusCode() != HttpStatus.UNAUTHORIZED);
		HttpHeaders invalidBasic = new HttpHeaders();
		invalidBasic.setBasicAuth(ADMIN, "wrong-password", StandardCharsets.UTF_8);
		assertEquals(HttpStatus.UNAUTHORIZED, testRestTemplate.exchange("/services/private/system/optin",
				HttpMethod.POST, new HttpEntity<>("uat-optin", invalidBasic), String.class).getStatusCode());

		ResponseEntity<String> health = get("/actuator/health", new HttpHeaders());
		assertEquals(HttpStatus.OK, health.getStatusCode());
		assertTrue(health.getBody().contains("\"status\":\"UP\""));
		ResponseEntity<String> apiDocs = get("/v2/api-docs", new HttpHeaders());
		assertEquals(HttpStatus.OK, apiDocs.getStatusCode());
		assertTrue(apiDocs.getBody().contains("\"swagger\""));
	}

	@Test
	public void customerBrowsesAddsToCartAndChecksOut() {
		String email = "jwt-uat-" + System.nanoTime() + "@test.com";
		registerCustomer(email);
		ResponseEntity<AuthenticationResponse> login = testRestTemplate.postForEntity("/api/v1/customer/login",
				new HttpEntity<>(new AuthenticationRequest(email, "clear123")), AuthenticationResponse.class);
		assertEquals(HttpStatus.OK, login.getStatusCode());
		assertNotNull(login.getBody());
		HttpHeaders customerToken = bearer(login.getBody().getToken());

		ResponseEntity<ReadableCustomer> profile = testRestTemplate.exchange("/api/v1/auth/customer/profile",
				HttpMethod.GET, new HttpEntity<>(customerToken), ReadableCustomer.class);
		assertEquals(HttpStatus.OK, profile.getStatusCode());
		assertEquals(email, profile.getBody().getEmailAddress());
		assertNotNull(profile.getBody().getId());

		ResponseEntity<ReadableMerchantStore> storeResponse = testRestTemplate.getForEntity(
				"/api/v1/store/DEFAULT", ReadableMerchantStore.class);
		assertEquals(HttpStatus.OK, storeResponse.getStatusCode());
		assertNotNull(storeResponse.getBody());
		assertEquals(HttpStatus.OK, testRestTemplate.getForEntity("/api/v1/category?store=DEFAULT",
				String.class).getStatusCode());

		String sku = "jwt-uat-product-" + System.nanoTime();
		ReadableProduct createdProduct = sampleProduct(sku);
		assertNotNull(createdProduct);
		assertEquals(HttpStatus.OK, testRestTemplate.getForEntity("/api/v1/products?store=DEFAULT",
				String.class).getStatusCode());
		assertEquals(HttpStatus.OK, testRestTemplate.getForEntity(
				"/api/v2/product/" + sku + "?store=DEFAULT&lang=en",
				ReadableProduct.class).getStatusCode());

		ResponseEntity<AuthenticationResponse> adminLogin = testRestTemplate.postForEntity(
				"/api/v1/private/login", new HttpEntity<>(new AuthenticationRequest(ADMIN, ADMIN_PASSWORD)),
				AuthenticationResponse.class);
		assertEquals(HttpStatus.OK, adminLogin.getStatusCode());
		assertNotNull(adminLogin.getBody());
		configureMoneyOrder(bearer(adminLogin.getBody().getToken()));

		PersistableShoppingCartItem cartItem = new PersistableShoppingCartItem();
		cartItem.setProduct(sku);
		cartItem.setQuantity(1);
		ResponseEntity<ReadableShoppingCart> createdCart = testRestTemplate.postForEntity("/api/v1/cart",
				new HttpEntity<>(cartItem), ReadableShoppingCart.class);
		assertEquals(HttpStatus.CREATED, createdCart.getStatusCode());
		assertNotNull(createdCart.getBody());

		cartItem.setQuantity(2);
		ResponseEntity<ReadableShoppingCart> updatedCart = testRestTemplate.exchange(
				"/api/v1/cart/" + createdCart.getBody().getCode(), HttpMethod.PUT,
				new HttpEntity<>(cartItem), ReadableShoppingCart.class);
		assertEquals(HttpStatus.CREATED, updatedCart.getStatusCode());
		ResponseEntity<ReadableShoppingCart> fetchedCart = testRestTemplate.exchange(
				"/api/v1/cart/" + createdCart.getBody().getCode(), HttpMethod.GET,
				new HttpEntity<>(new HttpHeaders()), ReadableShoppingCart.class);
		assertEquals(HttpStatus.OK, fetchedCart.getStatusCode());
		assertEquals(2, fetchedCart.getBody().getQuantity());

		Map<String, String> payment = new HashMap<>();
		payment.put("paymentType", "MONEYORDER");
		payment.put("transactionType", "CAPTURE");
		payment.put("paymentModule", "moneyorder");
		payment.put("amount", fetchedCart.getBody().getTotal().toPlainString());
		Map<String, Object> order = new HashMap<>();
		order.put("payment", payment);
		order.put("customerId", profile.getBody().getId());
		order.put("currency", storeResponse.getBody().getCurrency());
		order.put("customerAgreement", true);
		ResponseEntity<ReadableOrderConfirmation> checkout = testRestTemplate.postForEntity(
				"/api/v1/auth/cart/" + createdCart.getBody().getCode() + "/checkout?store=DEFAULT",
				new HttpEntity<>(order, customerToken), ReadableOrderConfirmation.class);
		assertEquals(HttpStatus.OK, checkout.getStatusCode());
		assertNotNull(checkout.getBody());
		assertNotNull(checkout.getBody().getId());

		ResponseEntity<ReadableOrder> savedOrder = testRestTemplate.exchange(
				"/api/v1/auth/orders/" + checkout.getBody().getId() + "?store=DEFAULT", HttpMethod.GET,
				new HttpEntity<>(customerToken), ReadableOrder.class);
		assertEquals(HttpStatus.OK, savedOrder.getStatusCode());
	}

	private void registerCustomer(String email) {
		PersistableCustomer customer = new PersistableCustomer();
		customer.setEmailAddress(email);
		customer.setPassword("clear123");
		customer.setGender(CustomerGender.M.name());
		customer.setLanguage("en");
		customer.setStoreCode(Constants.DEFAULT_STORE);
		customer.setBilling(customerAddress());
		customer.setDelivery(customerAddress());
		ResponseEntity<String> response = testRestTemplate.postForEntity("/api/v1/customer/register",
				new HttpEntity<>(customer), String.class);
		assertEquals(HttpStatus.OK, response.getStatusCode());
	}

	private void configureMoneyOrder(HttpHeaders adminToken) {
		IntegrationModuleConfiguration configuration = new IntegrationModuleConfiguration();
		configuration.setCode("moneyorder");
		configuration.setActive(true);
		configuration.setDefaultSelected(true);
		Map<String, String> integrationKeys = new HashMap<>();
		integrationKeys.put("address", "1 Main St");
		configuration.setIntegrationKeys(integrationKeys);
		ResponseEntity<String> response = testRestTemplate.postForEntity(
				"/api/v1/private/modules/payment?store=DEFAULT",
				new HttpEntity<>(configuration, adminToken), String.class);
		assertEquals(HttpStatus.OK, response.getStatusCode());
	}

	private Address customerAddress() {
		Address address = new Address();
		address.setFirstName("JWT");
		address.setLastName("UAT");
		address.setCountry("CA");
		address.setStateProvince("ON");
		address.setZone("ON");
		address.setCity("Toronto");
		address.setPostalCode("M5V 1A1");
		address.setAddress("1 Main St");
		address.setPhone("416-555-0100");
		return address;
	}

	private ResponseEntity<String> get(String path, HttpHeaders headers) {
		return testRestTemplate.exchange(path, HttpMethod.GET, new HttpEntity<>(headers), String.class);
	}

	private static HttpHeaders bearer(String token) {
		HttpHeaders headers = new HttpHeaders();
		headers.add("Authorization", "Bearer " + token);
		return headers;
	}
}
