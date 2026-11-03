package com.salesmanager.test.shop.uat;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;

import com.salesmanager.core.business.services.merchant.MerchantStoreService;
import com.salesmanager.core.business.services.payments.PaymentService;
import com.salesmanager.core.model.merchant.MerchantStore;
import com.salesmanager.core.model.system.IntegrationConfiguration;
import com.salesmanager.shop.model.catalog.product.ReadableProduct;
import com.salesmanager.shop.model.catalog.product.product.PersistableProduct;
import com.salesmanager.shop.model.customer.PersistableCustomer;
import com.salesmanager.shop.model.customer.address.Address;
import com.salesmanager.shop.model.order.ReadableOrderTotalSummary;
import com.salesmanager.shop.model.order.transaction.PersistablePayment;
import com.salesmanager.shop.model.order.v1.PersistableOrder;
import com.salesmanager.shop.model.order.v1.ReadableOrder;
import com.salesmanager.shop.model.order.v1.ReadableOrderConfirmation;
import com.salesmanager.shop.model.shoppingcart.PersistableShoppingCartItem;
import com.salesmanager.shop.model.shoppingcart.ReadableShoppingCart;
import com.salesmanager.shop.model.store.ReadableMerchantStore;
import com.salesmanager.shop.model.system.IntegrationModuleConfiguration;
import com.salesmanager.shop.store.security.AuthenticationRequest;
import com.salesmanager.shop.store.security.AuthenticationResponse;
import com.salesmanager.test.shop.common.ServicesTestSupport;

class ShopperJourneyUatTest extends ServicesTestSupport {

	@Autowired
	private MerchantStoreService merchantStoreService;
	@Autowired
	private PaymentService paymentService;

	private IntegrationConfiguration originalMoneyOrderConfiguration;

	@BeforeEach
	void preservePaymentConfiguration() throws Exception {
		MerchantStore store = merchantStoreService.getByCode("DEFAULT");
		originalMoneyOrderConfiguration = paymentService.getPaymentConfiguration("moneyorder", store);
	}

	@AfterEach
	void restorePaymentConfiguration() throws Exception {
		MerchantStore store = merchantStoreService.getByCode("DEFAULT");
		if (originalMoneyOrderConfiguration == null) {
			paymentService.removePaymentModuleConfiguration("moneyorder", store);
		} else {
			paymentService.savePaymentModuleConfiguration(originalMoneyOrderConfiguration, store);
		}
	}

	@Override
	protected PersistableProduct product(String code) {
		PersistableProduct product = super.product(code);
		product.setProductShipeable(true);
		product.setProductVirtual(false);
		return product;
	}

	@Test
	void exercisesAdminCustomerCatalogCartCheckoutAndServiceContracts() {
		HttpHeaders noAuth = new HttpHeaders();
		ResponseEntity<String> anonymousAdminCall = testRestTemplate.exchange(
				"/api/v1/private/customers", HttpMethod.GET, new HttpEntity<>(noAuth), String.class);
		assertTrue(!anonymousAdminCall.getStatusCode().is2xxSuccessful());

		HttpHeaders badToken = new HttpHeaders();
		badToken.setBearerAuth("not-a-valid-token");
		ResponseEntity<String> invalidAdminCall = testRestTemplate.exchange(
				"/api/v1/private/customers", HttpMethod.GET, new HttpEntity<>(badToken), String.class);
		assertTrue(!invalidAdminCall.getStatusCode().is2xxSuccessful());

		ResponseEntity<AuthenticationResponse> adminLogin = testRestTemplate.postForEntity(
				"/api/v1/private/login", new AuthenticationRequest("admin@shopizer.com", "password"),
				AuthenticationResponse.class);
		assertTrue(adminLogin.getStatusCode().is2xxSuccessful());
		assertNotNull(adminLogin.getBody().getToken());
		ResponseEntity<String> authorizedAdminCall = testRestTemplate.exchange(
				"/api/v1/private/customers", HttpMethod.GET,
				new HttpEntity<>(bearer(adminLogin.getBody().getToken())), String.class);
		assertTrue(authorizedAdminCall.getStatusCode().is2xxSuccessful());

		ReadableMerchantStore store = testRestTemplate.getForObject(
				"/api/v1/store/DEFAULT", ReadableMerchantStore.class);
		assertNotNull(store);
		assertEquals("DEFAULT", store.getCode());
		assertTrue(testRestTemplate.getForEntity("/api/v1/category", String.class)
				.getStatusCode().is2xxSuccessful());
		assertTrue(testRestTemplate.getForEntity("/api/v1/products?available=true", String.class)
				.getStatusCode().is2xxSuccessful());

		String sku = "uat-" + System.nanoTime();
		ReadableProduct product = sampleProduct(sku);
		assertTrue(testRestTemplate.getForEntity("/api/v2/product/" + product.getSku(), String.class)
				.getStatusCode().is2xxSuccessful());

		String email = "uat-" + System.nanoTime() + "@example.com";
		PersistableCustomer customer = customer(email);
		ResponseEntity<AuthenticationResponse> registration = testRestTemplate.postForEntity(
				"/api/v1/customer/register", customer, AuthenticationResponse.class);
		assertTrue(registration.getStatusCode().is2xxSuccessful());
		assertNotNull(registration.getBody().getToken());
		ResponseEntity<AuthenticationResponse> customerLogin = testRestTemplate.postForEntity(
				"/api/v1/customer/login", new AuthenticationRequest(email, "password"),
				AuthenticationResponse.class);
		assertTrue(customerLogin.getStatusCode().is2xxSuccessful());
		assertNotNull(customerLogin.getBody().getToken());
		HttpHeaders customerHeaders = bearer(customerLogin.getBody().getToken());
		ResponseEntity<String> customerProfile = testRestTemplate.exchange(
				"/api/v1/auth/customer/profile", HttpMethod.GET,
				new HttpEntity<>(customerHeaders), String.class);
		assertTrue(customerProfile.getStatusCode().is2xxSuccessful());

		PersistableShoppingCartItem cartItem = new PersistableShoppingCartItem();
		cartItem.setProduct(product.getSku());
		cartItem.setQuantity(1);
		ResponseEntity<ReadableShoppingCart> createdCart = testRestTemplate.postForEntity(
				"/api/v1/cart", new HttpEntity<>(cartItem, customerHeaders), ReadableShoppingCart.class);
		assertEquals(HttpStatus.CREATED, createdCart.getStatusCode());
		cartItem.setQuantity(2);
		ResponseEntity<ReadableShoppingCart> updatedCart = testRestTemplate.exchange(
				"/api/v1/cart/" + createdCart.getBody().getCode(), HttpMethod.PUT,
				new HttpEntity<>(cartItem, customerHeaders), ReadableShoppingCart.class);
		assertTrue(updatedCart.getStatusCode().is2xxSuccessful());
		ResponseEntity<ReadableShoppingCart> readCart = testRestTemplate.getForEntity(
				"/api/v1/cart/" + createdCart.getBody().getCode(), ReadableShoppingCart.class);
		assertTrue(readCart.getStatusCode().is2xxSuccessful());
		assertNotNull(readCart.getBody());

		enableMoneyOrder();
		ReadableOrderTotalSummary total = testRestTemplate.getForObject(
				"/api/v1/cart/" + createdCart.getBody().getCode() + "/total", ReadableOrderTotalSummary.class);
		PersistablePayment payment = new PersistablePayment();
		payment.setPaymentModule("moneyorder");
		payment.setPaymentType("MONEYORDER");
		payment.setTransactionType("AUTHORIZE");
		payment.setAmount(total.getTotal().replaceAll("[^0-9.\\-]", ""));
		PersistableOrder checkoutOrder = new PersistableOrder();
		checkoutOrder.setPayment(payment);
		checkoutOrder.setCurrency(store.getCurrency());
		checkoutOrder.setCustomerAgreement(true);
		ResponseEntity<ReadableOrderConfirmation> checkout = testRestTemplate.postForEntity(
				"/api/v1/auth/cart/" + createdCart.getBody().getCode() + "/checkout",
				new HttpEntity<>(checkoutOrder, customerHeaders), ReadableOrderConfirmation.class);
		assertTrue(checkout.getStatusCode().is2xxSuccessful(), checkout.getStatusCode() + " " + checkout.getBody());
		ResponseEntity<ReadableOrder> readOrder = testRestTemplate.exchange(
				"/api/v1/auth/orders/" + checkout.getBody().getId(), HttpMethod.GET,
				new HttpEntity<>(customerHeaders), ReadableOrder.class);
		assertTrue(readOrder.getStatusCode().is2xxSuccessful());
		assertEquals(checkout.getBody().getId(), readOrder.getBody().getId());

		String health = testRestTemplate.getForObject("/actuator/health", String.class);
		assertTrue(health.contains("\"status\":\"UP\""), health);
		ResponseEntity<String> apiDocs = testRestTemplate.getForEntity("/v2/api-docs", String.class);
		assertTrue(apiDocs.getStatusCode().is2xxSuccessful());

		HttpHeaders basicAuth = new HttpHeaders();
		basicAuth.setBasicAuth("admin@shopizer.com", "password", StandardCharsets.UTF_8);
		basicAuth.setContentType(MediaType.TEXT_PLAIN);
		ResponseEntity<String> serviceWithAuth = testRestTemplate.exchange(
				"/services/private/system/optin", HttpMethod.POST,
				new HttpEntity<>("uat", basicAuth), String.class);
		assertTrue(serviceWithAuth.getStatusCode().is2xxSuccessful(),
				serviceWithAuth.getStatusCode() + " " + serviceWithAuth.getBody());
		HttpHeaders badBasicAuth = new HttpHeaders();
		badBasicAuth.setBasicAuth("admin@shopizer.com", "invalid", StandardCharsets.UTF_8);
		badBasicAuth.setContentType(MediaType.TEXT_PLAIN);
		ResponseEntity<String> serviceWithoutValidAuth = testRestTemplate.exchange(
				"/services/private/system/optin", HttpMethod.POST,
				new HttpEntity<>("uat", badBasicAuth), String.class);
		assertTrue(!serviceWithoutValidAuth.getStatusCode().is2xxSuccessful());
	}

	private PersistableCustomer customer(String email) {
		PersistableCustomer customer = new PersistableCustomer();
		customer.setEmailAddress(email);
		customer.setUserName(email);
		customer.setFirstName("UAT");
		customer.setLastName("Customer");
		customer.setGender("M");
		customer.setLanguage("en");
		customer.setStoreCode("DEFAULT");
		customer.setPassword("password");
		customer.setRepeatPassword("password");
		Address billing = new Address();
		billing.setAddress("123 Main Street");
		billing.setCity("Montreal");
		billing.setCountry("CA");
		billing.setPostalCode("H3B 1A1");
		customer.setBilling(billing);
		customer.setDelivery(billing);
		return customer;
	}

	private HttpHeaders bearer(String token) {
		HttpHeaders headers = new HttpHeaders();
		headers.setContentType(MediaType.APPLICATION_JSON);
		headers.setBearerAuth(token);
		return headers;
	}

	private void enableMoneyOrder() {
		IntegrationModuleConfiguration configuration = new IntegrationModuleConfiguration();
		configuration.setCode("moneyorder");
		configuration.setActive(true);
		configuration.setDefaultSelected(true);
		Map<String, String> keys = new HashMap<>();
		keys.put("address", "Money order payment address");
		configuration.setIntegrationKeys(keys);
		HttpHeaders headers = getHeader();
		headers.setContentType(MediaType.APPLICATION_JSON);
		ResponseEntity<Void> response = testRestTemplate.exchange(
				"/api/v1/private/modules/payment?store=DEFAULT", HttpMethod.POST,
				new HttpEntity<>(configuration, headers), Void.class);
		assertTrue(response.getStatusCode().is2xxSuccessful());
	}
}
