package com.salesmanager.test.shop.uat;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.http.HttpStatus.CREATED;
import static org.springframework.http.HttpStatus.OK;

import java.util.UUID;
import java.util.Collections;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.junit.jupiter.api.TestInstance.Lifecycle;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.SpringBootTest.WebEnvironment;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.annotation.DirtiesContext;

import com.salesmanager.core.business.constants.Constants;
import com.salesmanager.core.business.services.user.UserService;
import com.salesmanager.core.model.user.User;
import com.salesmanager.core.model.customer.CustomerGender;
import com.salesmanager.core.model.payments.PaymentType;
import com.salesmanager.core.model.payments.TransactionType;
import com.salesmanager.shop.application.ShopApplication;
import com.salesmanager.shop.model.customer.PersistableCustomer;
import com.salesmanager.shop.model.customer.address.Address;
import com.salesmanager.shop.model.order.v1.PersistableOrder;
import com.salesmanager.shop.model.order.v1.ReadableOrderConfirmation;
import com.salesmanager.shop.model.order.transaction.PersistablePayment;
import com.salesmanager.shop.model.shoppingcart.PersistableShoppingCartItem;
import com.salesmanager.shop.model.shoppingcart.ReadableShoppingCart;
import com.salesmanager.shop.model.system.IntegrationModuleConfiguration;
import com.salesmanager.shop.store.security.AuthenticationRequest;
import com.salesmanager.shop.store.security.AuthenticationResponse;
import com.salesmanager.test.shop.common.ServicesTestSupport;

@SpringBootTest(classes = ShopApplication.class, webEnvironment = WebEnvironment.RANDOM_PORT)
@TestInstance(Lifecycle.PER_CLASS)
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
class StorefrontAdminJourneyUatTest extends ServicesTestSupport {

	private static final String ADMIN_USERNAME = "admin@shopizer.com";
	private static final String CUSTOMER_PASSWORD = "Uat-Customer-Pass-123";
	private String productCode;
	private String originalAdminPassword;

	@Autowired
	private UserService userService;

	@Autowired
	private PasswordEncoder passwordEncoder;

	@BeforeAll
	void prepareAdminAndSampleProduct() throws Exception {
		User admin = userService.getByUserName(ADMIN_USERNAME);
		assertNotNull(admin);
		originalAdminPassword = admin.getAdminPassword();
		// bootstrap password is covered by the integration tests
		admin.setAdminPassword(passwordEncoder.encode(ADMIN_PASSWORD));
		userService.saveOrUpdate(admin);

		productCode = uniqueCode("uat-product");
		assertNotNull(sampleProduct(productCode));
	}

	@AfterAll
	void restoreAdminPassword() throws Exception {
		User admin = userService.getByUserName(ADMIN_USERNAME);
		assertNotNull(admin);
		admin.setAdminPassword(originalAdminPassword);
		userService.saveOrUpdate(admin);
	}

	@Test
	void adminLoginAndPrivateProfileRequireValidJwt() {
		AuthenticationResponse admin = login(ADMIN_USERNAME, ADMIN_PASSWORD, "/api/v1/private/login");
		assertNotNull(admin.getToken());

		ResponseEntity<String> profile = testRestTemplate.exchange(
				"/api/v1/private/user/profile", HttpMethod.GET, new HttpEntity<>(bearer(admin.getToken())),
				String.class);
		assertEquals(OK, profile.getStatusCode());

		ResponseEntity<String> missingToken = testRestTemplate.getForEntity(
				"/api/v1/private/user/profile", String.class);
		assertTrue(isAuthenticationFailure(missingToken.getStatusCode()));

	}

	@Test
	void customerCanRegisterLoginAndReadAuthenticatedProfile() {
		String email = "uat-" + UUID.randomUUID() + "@example.com";
		registerCustomer(email);
		AuthenticationResponse customer = login(email, CUSTOMER_PASSWORD, "/api/v1/customer/login");
		assertNotNull(customer.getToken());

		ResponseEntity<String> profile = testRestTemplate.exchange(
				"/api/v1/auth/customer/profile", HttpMethod.GET, new HttpEntity<>(bearer(customer.getToken())),
				String.class);
		assertEquals(OK, profile.getStatusCode());
	}

	@Test
	void storefrontStoreCategoriesProductListAndDetailAreAvailable() {
		ResponseEntity<String> store = testRestTemplate.getForEntity("/api/v1/store/DEFAULT", String.class);
		assertEquals(OK, store.getStatusCode());

		ResponseEntity<String> categories = testRestTemplate.getForEntity(
				"/api/v1/category?store=DEFAULT", String.class);
		assertEquals(OK, categories.getStatusCode());

		ResponseEntity<String> products = testRestTemplate.getForEntity(
				"/api/v1/products?store=DEFAULT&sku=" + productCode, String.class);
		assertEquals(OK, products.getStatusCode());

		ResponseEntity<String> detail = testRestTemplate.getForEntity(
				"/api/v2/product/" + productCode, String.class);
		assertEquals(OK, detail.getStatusCode());
	}

	@Test
	void cartCanBeCreatedUpdatedAndReadBack() {
		PersistableShoppingCartItem item = new PersistableShoppingCartItem();
		item.setProduct(productCode);
		item.setQuantity(1);
		ResponseEntity<ReadableShoppingCart> created = testRestTemplate.postForEntity(
				"/api/v1/cart", new HttpEntity<>(item), ReadableShoppingCart.class);
		assertEquals(CREATED, created.getStatusCode());
		assertNotNull(created.getBody());
		assertFalse(created.getBody().getCode().trim().isEmpty());

		item.setQuantity(2);
		ResponseEntity<ReadableShoppingCart> updated = testRestTemplate.exchange(
				"/api/v1/cart/" + created.getBody().getCode(), HttpMethod.PUT,
				new HttpEntity<>(item), ReadableShoppingCart.class);
		assertEquals(CREATED, updated.getStatusCode());
		assertNotNull(updated.getBody());
		assertEquals(2, updated.getBody().getQuantity());

		ResponseEntity<ReadableShoppingCart> readBack = testRestTemplate.getForEntity(
				"/api/v1/cart/" + created.getBody().getCode(), ReadableShoppingCart.class);
		assertEquals(OK, readBack.getStatusCode());
		assertNotNull(readBack.getBody());
		assertEquals(2, readBack.getBody().getQuantity());
	}

	@Test
	void authenticatedCustomerCanCheckoutAndReadOrder() {
		AuthenticationResponse admin = login(ADMIN_USERNAME, ADMIN_PASSWORD, "/api/v1/private/login");
		IntegrationModuleConfiguration paymentModule = new IntegrationModuleConfiguration();
		paymentModule.setCode("moneyorder");
		paymentModule.setActive(true);
		paymentModule.setDefaultSelected(true);
		paymentModule.setIntegrationKeys(Collections.singletonMap("address", "UAT test"));
		ResponseEntity<Void> configuredPayment = testRestTemplate.postForEntity(
				"/api/v1/private/modules/payment?store=DEFAULT",
				new HttpEntity<>(paymentModule, bearer(admin.getToken())), Void.class);
		assertEquals(OK, configuredPayment.getStatusCode());

		String email = "uo-" + UUID.randomUUID().toString().replace("-", "").substring(0, 12) + "@example.com";
		registerCustomer(email);
		AuthenticationResponse customer = login(email, CUSTOMER_PASSWORD, "/api/v1/customer/login");

		PersistableShoppingCartItem item = new PersistableShoppingCartItem();
		item.setProduct(productCode);
		item.setQuantity(1);
		ResponseEntity<ReadableShoppingCart> cart = testRestTemplate.postForEntity(
				"/api/v1/cart", new HttpEntity<>(item), ReadableShoppingCart.class);
		assertEquals(CREATED, cart.getStatusCode());
		assertNotNull(cart.getBody());

		PersistableOrder order = new PersistableOrder();
		order.setCustomerAgreement(true);
		order.setCurrency("USD");
		PersistablePayment payment = new PersistablePayment();
		payment.setPaymentModule("moneyorder");
		payment.setPaymentType(PaymentType.MONEYORDER.name());
		payment.setTransactionType(TransactionType.INIT.name());
		payment.setAmount(cart.getBody().getTotal().toPlainString());
		order.setPayment(payment);
		ResponseEntity<ReadableOrderConfirmation> checkout = testRestTemplate.exchange(
				"/api/v1/auth/cart/" + cart.getBody().getCode() + "/checkout", HttpMethod.POST,
				new HttpEntity<>(order, bearer(customer.getToken())), ReadableOrderConfirmation.class);
		assertEquals(OK, checkout.getStatusCode());
		assertNotNull(checkout.getBody());
		assertNotNull(checkout.getBody().getId());

		ResponseEntity<String> readBack = testRestTemplate.exchange(
				"/api/v1/auth/orders/" + checkout.getBody().getId(), HttpMethod.GET,
				new HttpEntity<>(bearer(customer.getToken())), String.class);
		assertEquals(OK, readBack.getStatusCode());
	}

	@Test
	void servicesBasicAuthenticationAcceptsAdminAndRejectsInvalidPassword() {
		HttpHeaders validCredentials = new HttpHeaders();
		validCredentials.setBasicAuth(ADMIN_USERNAME, ADMIN_PASSWORD);
		validCredentials.setContentType(MediaType.APPLICATION_JSON);
		ResponseEntity<String> valid = testRestTemplate.postForEntity(
				"/services/private/system/optin", new HttpEntity<>("{}", validCredentials), String.class);
		assertEquals(OK, valid.getStatusCode());

		HttpHeaders invalidCredentials = new HttpHeaders();
		invalidCredentials.setBasicAuth(ADMIN_USERNAME, "wrong-password");
		invalidCredentials.setContentType(MediaType.APPLICATION_JSON);
		ResponseEntity<String> invalid = testRestTemplate.postForEntity(
				"/services/private/system/optin", new HttpEntity<>("{}", invalidCredentials), String.class);
		assertEquals(HttpStatus.UNAUTHORIZED, invalid.getStatusCode());
	}

	@Test
	void actuatorHealthReportsUp() {
		ResponseEntity<String> health = testRestTemplate.getForEntity("/actuator/health", String.class);

		assertEquals(OK, health.getStatusCode());
		assertTrue(health.getBody().contains("\"status\":\"UP\""));
	}

	@Test
	void swaggerV2ApiDocsAreAvailable() {
		ResponseEntity<String> apiDocs = testRestTemplate.getForEntity("/v2/api-docs", String.class);

		assertEquals(OK, apiDocs.getStatusCode());
	}

	private void registerCustomer(String email) {
		PersistableCustomer customer = new PersistableCustomer();
		customer.setEmailAddress(email);
		customer.setPassword(CUSTOMER_PASSWORD);
		customer.setGender(CustomerGender.M.name());
		customer.setLanguage("en");
		customer.setStoreCode(Constants.DEFAULT_STORE);

		Address billing = new Address();
		billing.setFirstName("Uat");
		billing.setLastName("Customer");
		billing.setCountry("BE");
		customer.setBilling(billing);

		ResponseEntity<PersistableCustomer> response = testRestTemplate.postForEntity(
				"/api/v1/customer/register", new HttpEntity<>(customer), PersistableCustomer.class);
		assertEquals(OK, response.getStatusCode());
	}

	private AuthenticationResponse login(String username, String password, String path) {
		ResponseEntity<AuthenticationResponse> response = testRestTemplate.postForEntity(
				path, new HttpEntity<>(new AuthenticationRequest(username, password)), AuthenticationResponse.class);
		assertEquals(OK, response.getStatusCode());
		assertNotNull(response.getBody());
		return response.getBody();
	}

	private HttpHeaders bearer(String token) {
		HttpHeaders headers = new HttpHeaders();
		headers.set("Authorization", "Bearer " + token);
		return headers;
	}

	private String uniqueCode(String prefix) {
		return prefix + UUID.randomUUID().toString().replace("-", "").substring(0, 12);
	}

	private boolean isAuthenticationFailure(HttpStatus status) {
		return status == HttpStatus.UNAUTHORIZED || status == HttpStatus.FORBIDDEN;
	}
}
