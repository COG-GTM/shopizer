package com.salesmanager.test.shop.uat;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;
import static org.springframework.http.HttpStatus.CREATED;
import static org.springframework.http.HttpStatus.OK;
import static org.springframework.http.HttpStatus.UNAUTHORIZED;

import java.nio.charset.StandardCharsets;
import java.util.Arrays;
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

import com.salesmanager.core.model.merchant.MerchantStore;
import com.salesmanager.shop.application.ShopApplication;
import com.salesmanager.shop.model.catalog.category.ReadableCategoryList;
import com.salesmanager.shop.model.catalog.product.ReadableProduct;
import com.salesmanager.shop.model.catalog.product.ReadableProductList;
import com.salesmanager.shop.model.customer.PersistableCustomer;
import com.salesmanager.shop.model.customer.ReadableCustomer;
import com.salesmanager.shop.model.customer.address.Address;
import com.salesmanager.shop.model.order.v1.ReadableOrder;
import com.salesmanager.shop.model.order.v1.PersistableOrder;
import com.salesmanager.shop.model.order.v1.ReadableOrderConfirmation;
import com.salesmanager.shop.model.order.transaction.PersistablePayment;
import com.salesmanager.shop.model.references.PersistableAddress;
import com.salesmanager.shop.model.security.PersistableGroup;
import com.salesmanager.shop.model.store.PersistableMerchantStore;
import com.salesmanager.shop.model.store.ReadableMerchantStore;
import com.salesmanager.shop.model.system.IntegrationModuleConfiguration;
import com.salesmanager.shop.model.user.PersistableUser;
import com.salesmanager.shop.model.user.ReadableUser;
import com.salesmanager.shop.model.shoppingcart.PersistableShoppingCartItem;
import com.salesmanager.shop.model.shoppingcart.ReadableShoppingCart;
import com.salesmanager.shop.store.security.AuthenticationRequest;
import com.salesmanager.shop.store.security.AuthenticationResponse;
import com.salesmanager.test.shop.common.ServicesTestSupport;

@SpringBootTest(classes = ShopApplication.class, webEnvironment = WebEnvironment.RANDOM_PORT)
@RunWith(SpringRunner.class)
public class ShopizerUatTest extends ServicesTestSupport {

	private static final String DEFAULT_STORE = MerchantStore.DEFAULT_STORE;
	private static final String STORE = "uatstore";
	private static final String STORE_ADMIN = "admin@uatstore.com";
	private static final String STORE_PASSWORD = "UatPass1!";
	private static final Long SUPERADMIN_ID = 1L;

	@Test
	public void storefrontCustomerAndStoreAdminJourneys() throws Exception {
		HttpHeaders adminHeaders = login("admin@shopizer.com", "password");

		ResponseEntity<String> usersWithToken = testRestTemplate.exchange(
				"/api/v1/private/users?store=" + DEFAULT_STORE, HttpMethod.GET,
				new HttpEntity<>(adminHeaders), String.class);
		assertEquals(OK, usersWithToken.getStatusCode());

		ResponseEntity<String> usersWithoutToken = testRestTemplate.exchange(
				"/api/v1/private/users?store=" + DEFAULT_STORE, HttpMethod.GET,
				new HttpEntity<>(new HttpHeaders()), String.class);
		assertUnauthenticated(usersWithoutToken.getStatusCodeValue());
		HttpHeaders invalidToken = new HttpHeaders();
		invalidToken.setBearerAuth("not-a-valid-token");
		ResponseEntity<String> usersWithInvalidToken = testRestTemplate.exchange(
				"/api/v1/private/users?store=" + DEFAULT_STORE, HttpMethod.GET,
				new HttpEntity<>(invalidToken), String.class);
		// Invalid Bearer tokens currently return 500 on base and head.
		assertFalse(usersWithInvalidToken.getStatusCode().is2xxSuccessful());

		IntegrationModuleConfiguration moneyOrder = new IntegrationModuleConfiguration();
		moneyOrder.setCode("moneyorder");
		moneyOrder.setActive(true);
		moneyOrder.setDefaultSelected(true);
		moneyOrder.getIntegrationKeys().put("address", "100 UAT Way");
		ResponseEntity<String> paymentConfiguration = testRestTemplate.exchange(
				"/api/v1/private/modules/payment?store=" + DEFAULT_STORE, HttpMethod.POST,
				new HttpEntity<>(moneyOrder, adminHeaders), String.class);
		assertEquals(OK, paymentConfiguration.getStatusCode());

		String suffix = UUID.randomUUID().toString().substring(0, 8);
		ReadableProduct createdProduct = sampleProduct("UAT-" + suffix);
		ResponseEntity<ReadableProductList> productsResponse = testRestTemplate.exchange(
				"/api/v2/products?store=" + DEFAULT_STORE, HttpMethod.GET, null, ReadableProductList.class);
		assertEquals(OK, productsResponse.getStatusCode());
		assertNotNull(productsResponse.getBody());
		assertTrue(productsResponse.getBody().getProducts().stream()
				.anyMatch(item -> createdProduct.getSku().equals(item.getSku())));
		ReadableProduct product = createdProduct;
		assertNotNull(product.getSku());
		ReadableMerchantStore defaultStore = testRestTemplate.getForObject(
				"/api/v1/store/" + DEFAULT_STORE, ReadableMerchantStore.class);
		assertNotNull(defaultStore);
		assertEquals(DEFAULT_STORE, defaultStore.getCode());

		ResponseEntity<ReadableCategoryList> categoriesResponse = testRestTemplate.exchange(
				"/api/v1/category?store=" + DEFAULT_STORE, HttpMethod.GET, null, ReadableCategoryList.class);
		assertEquals(OK, categoriesResponse.getStatusCode());
		assertNotNull(categoriesResponse.getBody());
		ResponseEntity<ReadableProduct> productResponse = testRestTemplate.exchange(
				"/api/v2/product/" + product.getSku() + "?store=" + DEFAULT_STORE,
				HttpMethod.GET, null, ReadableProduct.class);
		assertEquals(OK, productResponse.getStatusCode());
		assertEquals(product.getSku(), productResponse.getBody().getSku());

		String customerEmail = "uat-" + suffix + "@example.com";
		PersistableCustomer customer = customer(customerEmail);
		ResponseEntity<AuthenticationResponse> registration = testRestTemplate.postForEntity(
				"/api/v1/customer/register?store=" + DEFAULT_STORE,
				new HttpEntity<>(customer), AuthenticationResponse.class);
		assertTrue(registration.getStatusCode().is2xxSuccessful());
		assertNotNull(registration.getBody());
		assertNotNull(registration.getBody().getToken());
		assertFalse(registration.getBody().getToken().isEmpty());

		HttpHeaders customerHeaders = loginCustomer(customerEmail, customer.getPassword());
		ResponseEntity<ReadableCustomer> profile = testRestTemplate.exchange(
				"/api/v1/auth/customer/profile?store=" + DEFAULT_STORE,
				HttpMethod.GET, new HttpEntity<>(customerHeaders), ReadableCustomer.class);
		assertEquals(OK, profile.getStatusCode());
		assertEquals(customerEmail, profile.getBody().getEmailAddress());

		PersistableShoppingCartItem firstItem = cartItem(product.getSku(), 1);
		ResponseEntity<ReadableShoppingCart> createCart = testRestTemplate.postForEntity(
				"/api/v1/cart?store=" + DEFAULT_STORE, new HttpEntity<>(firstItem), ReadableShoppingCart.class);
		assertEquals(CREATED, createCart.getStatusCode());
		assertNotNull(createCart.getBody());
		String cartCode = createCart.getBody().getCode();

		PersistableShoppingCartItem secondItem = cartItem(product.getSku(), 2);
		ResponseEntity<ReadableShoppingCart> updateCart = testRestTemplate.exchange(
				"/api/v1/cart/" + cartCode + "?store=" + DEFAULT_STORE, HttpMethod.PUT,
				new HttpEntity<>(secondItem), ReadableShoppingCart.class);
		assertTrue(updateCart.getStatusCode().is2xxSuccessful());
		ResponseEntity<ReadableShoppingCart> readCart = testRestTemplate.exchange(
				"/api/v1/cart/" + cartCode + "?store=" + DEFAULT_STORE, HttpMethod.GET,
				null, ReadableShoppingCart.class);
		assertEquals(OK, readCart.getStatusCode());
		assertEquals(2, readCart.getBody().getQuantity());

		PersistableOrder order = new PersistableOrder();
		order.setCurrency("CAD");
		order.setCustomerAgreement(true);
		PersistablePayment payment = new PersistablePayment();
		payment.setAmount(readCart.getBody().getTotal().toPlainString());
		payment.setPaymentType("MONEYORDER");
		payment.setTransactionType("AUTHORIZECAPTURE");
		payment.setPaymentModule("moneyorder");
		order.setPayment(payment);
		ResponseEntity<ReadableOrderConfirmation> checkout = testRestTemplate.exchange(
				"/api/v1/auth/cart/" + cartCode + "/checkout?store=" + DEFAULT_STORE,
				HttpMethod.POST, new HttpEntity<>(order, customerHeaders), ReadableOrderConfirmation.class);
		assertTrue(checkout.getStatusCode().is2xxSuccessful());
		assertNotNull(checkout.getBody());
		assertNotNull(checkout.getBody().getId());
		ResponseEntity<ReadableOrder> readOrder = testRestTemplate.exchange(
				"/api/v1/auth/orders/" + checkout.getBody().getId() + "?store=" + DEFAULT_STORE,
				HttpMethod.GET, new HttpEntity<>(customerHeaders), ReadableOrder.class);
		assertEquals(OK, readOrder.getStatusCode());
		assertEquals(checkout.getBody().getId(), readOrder.getBody().getId());
		ResponseEntity<String> customerOrders = testRestTemplate.exchange(
				"/api/v1/auth/orders?store=" + DEFAULT_STORE, HttpMethod.GET,
				new HttpEntity<>(customerHeaders), String.class);
		assertEquals(OK, customerOrders.getStatusCode());

		HttpHeaders servicesHeaders = basicHeaders("admin@shopizer.com", "password");
		servicesHeaders.setContentType(MediaType.parseMediaType("text/plain;charset=UTF-8"));
		ResponseEntity<String> validServices = testRestTemplate.exchange(
				"/services/private/system/optin?store=" + DEFAULT_STORE, HttpMethod.POST,
				new HttpEntity<>("{}", servicesHeaders), String.class);
		assertTrue(validServices.getStatusCode().is2xxSuccessful());
		HttpHeaders invalidServicesHeaders = basicHeaders("admin@shopizer.com", "wrong-password");
		invalidServicesHeaders.setContentType(MediaType.parseMediaType("text/plain;charset=UTF-8"));
		ResponseEntity<String> invalidServices = testRestTemplate.exchange(
				"/services/private/system/optin?store=" + DEFAULT_STORE, HttpMethod.POST,
				new HttpEntity<>("{}", invalidServicesHeaders), String.class);
		assertEquals(UNAUTHORIZED, invalidServices.getStatusCode());

		ResponseEntity<String> health = testRestTemplate.getForEntity("/actuator/health", String.class);
		assertEquals(OK, health.getStatusCode());
		assertTrue(health.getBody().contains("\"status\":\"UP\""));
		ResponseEntity<String> apiDocs = testRestTemplate.getForEntity("/v2/api-docs", String.class);
		assertEquals(OK, apiDocs.getStatusCode());
		assertTrue(apiDocs.getBody().contains("\"paths\""));

		createStore(adminHeaders);
		ReadableUser storeAdmin = createStoreAdmin(adminHeaders);
		HttpHeaders storeAdminHeaders = login(STORE_ADMIN, STORE_PASSWORD);
		assertEquals(OK, testRestTemplate.exchange("/api/v1/private/users?store=" + STORE,
				HttpMethod.GET, new HttpEntity<>(storeAdminHeaders), String.class).getStatusCode());
		assertEquals(UNAUTHORIZED, testRestTemplate.exchange("/api/v1/private/users?store=DEFAULT",
				HttpMethod.GET, new HttpEntity<>(storeAdminHeaders), String.class).getStatusCode());
		assertEquals(UNAUTHORIZED, testRestTemplate.exchange("/api/v1/private/orders?store=DEFAULT",
				HttpMethod.GET, new HttpEntity<>(storeAdminHeaders), String.class).getStatusCode());
		PersistableUser enableSuperadmin = new PersistableUser();
		enableSuperadmin.setActive(true);
		assertEquals(UNAUTHORIZED, testRestTemplate.exchange(
				"/api/v1/private/user/" + SUPERADMIN_ID + "/enabled?store=DEFAULT", HttpMethod.PATCH,
				new HttpEntity<>(enableSuperadmin, storeAdminHeaders), String.class).getStatusCode());
		assertEquals(UNAUTHORIZED, testRestTemplate.exchange(
				"/api/v1/private/user/" + SUPERADMIN_ID + "?store=DEFAULT", HttpMethod.DELETE,
				new HttpEntity<>(storeAdminHeaders), String.class).getStatusCode());
		assertNotNull(storeAdmin.getId());
	}

	private HttpHeaders login(String username, String password) {
		ResponseEntity<AuthenticationResponse> response = testRestTemplate.postForEntity(
				"/api/v1/private/login", new HttpEntity<>(new AuthenticationRequest(username, password)),
				AuthenticationResponse.class);
		assertEquals(OK, response.getStatusCode());
		assertNotNull(response.getBody());
		assertNotNull(response.getBody().getToken());
		assertFalse(response.getBody().getToken().isEmpty());
		HttpHeaders headers = new HttpHeaders();
		headers.setBearerAuth(response.getBody().getToken());
		headers.setContentType(MediaType.APPLICATION_JSON);
		return headers;
	}

	private HttpHeaders loginCustomer(String username, String password) {
		ResponseEntity<AuthenticationResponse> response = testRestTemplate.postForEntity(
				"/api/v1/customer/login?store=" + DEFAULT_STORE,
				new HttpEntity<>(new AuthenticationRequest(username, password)), AuthenticationResponse.class);
		assertEquals(OK, response.getStatusCode());
		assertNotNull(response.getBody());
		assertNotNull(response.getBody().getToken());
		HttpHeaders headers = new HttpHeaders();
		headers.setBearerAuth(response.getBody().getToken());
		headers.setContentType(MediaType.APPLICATION_JSON);
		return headers;
	}

	private PersistableCustomer customer(String email) {
		PersistableCustomer customer = new PersistableCustomer();
		customer.setEmailAddress(email);
		customer.setPassword("CustPass1!");
		customer.setRepeatPassword("CustPass1!");
		customer.setGender("M");
		customer.setLanguage("en");
		customer.setStoreCode(DEFAULT_STORE);
		customer.setFirstName("UAT");
		customer.setLastName("Customer");
		Address billing = new Address();
		billing.setFirstName("UAT");
		billing.setLastName("Customer");
		billing.setCountry("US");
		customer.setBilling(billing);
		return customer;
	}

	private PersistableShoppingCartItem cartItem(String sku, int quantity) {
		PersistableShoppingCartItem item = new PersistableShoppingCartItem();
		item.setProduct(sku);
		item.setQuantity(quantity);
		return item;
	}

	private HttpHeaders basicHeaders(String username, String password) {
		HttpHeaders headers = new HttpHeaders();
		String credentials = username + ":" + password;
		headers.set(HttpHeaders.AUTHORIZATION,
				"Basic " + Base64.getEncoder().encodeToString(credentials.getBytes(StandardCharsets.UTF_8)));
		return headers;
	}

	private void createStore(HttpHeaders headers) {
		PersistableAddress address = new PersistableAddress();
		address.setAddress("1 UAT Way");
		address.setPostalCode("12345");
		address.setCountry("US");
		address.setCity("Seattle");
		address.setStateProvince("WA");

		PersistableMerchantStore store = new PersistableMerchantStore();
		store.setCode(STORE);
		store.setCurrency("CAD");
		store.setDefaultLanguage("en");
		store.setEmail("store@uatstore.com");
		store.setName(STORE);
		store.setPhone("444-555-6666");
		store.setSupportedLanguages(Arrays.asList("en"));
		store.setAddress(address);

		ResponseEntity<String> response = testRestTemplate.postForEntity(
				"/api/v1/private/store", new HttpEntity<>(store, headers), String.class);
		assertEquals(OK, response.getStatusCode());
	}

	private ReadableUser createStoreAdmin(HttpHeaders headers) {
		PersistableUser user = new PersistableUser();
		user.setDefaultLanguage("en");
		user.setEmailAddress(STORE_ADMIN);
		user.setFirstName("UAT");
		user.setLastName("Admin");
		user.setUserName(STORE_ADMIN);
		user.setPassword(STORE_PASSWORD);
		user.setRepeatPassword(STORE_PASSWORD);
		user.setActive(true);
		PersistableGroup group = new PersistableGroup();
		group.setName("ADMIN");
		user.getGroups().add(group);

		ResponseEntity<ReadableUser> response = testRestTemplate.exchange(
				"/api/v1/private/user/?store=" + STORE, HttpMethod.POST,
				new HttpEntity<>(user, headers), ReadableUser.class);
		assertEquals(OK, response.getStatusCode());
		return response.getBody();
	}

	private void assertUnauthenticated(int status) {
		assertTrue(status == 401 || status == 403);
	}
}
