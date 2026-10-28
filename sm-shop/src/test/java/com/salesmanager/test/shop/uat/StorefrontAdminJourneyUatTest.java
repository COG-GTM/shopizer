package com.salesmanager.test.shop.uat;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.is;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.Collections;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.SpringBootTest.WebEnvironment;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.test.context.junit4.SpringRunner;

import com.salesmanager.core.model.customer.CustomerGender;
import com.salesmanager.shop.application.ShopApplication;
import com.salesmanager.shop.model.catalog.category.ReadableCategoryList;
import com.salesmanager.shop.model.catalog.product.ReadableProduct;
import com.salesmanager.shop.model.catalog.product.ReadableProductList;
import com.salesmanager.shop.model.customer.PersistableCustomer;
import com.salesmanager.shop.model.customer.address.Address;
import com.salesmanager.shop.model.order.transaction.PersistablePayment;
import com.salesmanager.shop.model.order.v1.PersistableOrder;
import com.salesmanager.shop.model.references.PersistableAddress;
import com.salesmanager.shop.model.security.PersistableGroup;
import com.salesmanager.shop.model.shoppingcart.PersistableShoppingCartItem;
import com.salesmanager.shop.model.shoppingcart.ReadableShoppingCart;
import com.salesmanager.shop.model.store.PersistableMerchantStore;
import com.salesmanager.shop.model.store.ReadableMerchantStore;
import com.salesmanager.shop.model.system.IntegrationModuleConfiguration;
import com.salesmanager.shop.model.user.PersistableUser;
import com.salesmanager.shop.store.security.AuthenticationRequest;
import com.salesmanager.shop.store.security.AuthenticationResponse;
import com.salesmanager.test.shop.common.ServicesTestSupport;

@SpringBootTest(classes = ShopApplication.class, webEnvironment = WebEnvironment.RANDOM_PORT)
@RunWith(SpringRunner.class)
public class StorefrontAdminJourneyUatTest extends ServicesTestSupport {

	private static final String CUSTOMER_PASSWORD = "clear123";
	private static final String STORE_ADMIN_PASSWORD = "Password1";

	@Test
	public void adminLoginProtectsPrivateApi() {
		HttpHeaders adminHeaders = getHeader("admin@shopizer.com", "password");
		assertThat(privateStore("DEFAULT", adminHeaders), is(HttpStatus.OK));

		assertUnauthorized(privateStore("DEFAULT", new HttpHeaders()));
	}

	@Test
	public void customerCanRegisterLoginAndReadAuthenticatedProfile() {
		String email = uniqueEmail("profile");
		registerCustomer(email);

		ResponseEntity<AuthenticationResponse> login = testRestTemplate.postForEntity("/api/v1/customer/login",
				new HttpEntity<>(new AuthenticationRequest(email, CUSTOMER_PASSWORD)), AuthenticationResponse.class);
		assertThat(login.getStatusCode(), is(HttpStatus.OK));
		assertNotNull(login.getBody());
		assertNotNull(login.getBody().getToken());

		HttpHeaders headers = new HttpHeaders();
		headers.setBearerAuth(login.getBody().getToken());
		ResponseEntity<String> profile = testRestTemplate.exchange("/api/v1/auth/customer/profile?store=DEFAULT",
				HttpMethod.GET, new HttpEntity<>(headers), String.class);
		assertThat(profile.getStatusCode(), is(HttpStatus.OK));
		assertTrue(profile.getBody().contains(email));
	}

	@Test
	public void visitorCanBrowseStoreCategoriesProductsAndProductDetails() {
		ReadableProduct createdProduct = sampleProduct(uniqueProductCode("browse"));
		ResponseEntity<ReadableMerchantStore> store = testRestTemplate.getForEntity("/api/v1/store/DEFAULT",
				ReadableMerchantStore.class);
		assertThat(store.getStatusCode(), is(HttpStatus.OK));
		assertNotNull(store.getBody());

		ResponseEntity<ReadableCategoryList> categories = testRestTemplate
				.getForEntity("/api/v1/category?store=DEFAULT", ReadableCategoryList.class);
		assertThat(categories.getStatusCode(), is(HttpStatus.OK));
		assertNotNull(categories.getBody());

		ResponseEntity<ReadableProductList> products = testRestTemplate
				.getForEntity("/api/v1/products?store=DEFAULT", ReadableProductList.class);
		assertThat(products.getStatusCode(), is(HttpStatus.OK));
		assertNotNull(products.getBody());
		assertTrue(products.getBody().getProducts().stream()
				.anyMatch(product -> product.getSku().equals(createdProduct.getSku())));

		ResponseEntity<ReadableProduct> detail = testRestTemplate.getForEntity(
				"/api/v2/product/" + createdProduct.getSku(), ReadableProduct.class);
		assertThat(detail.getStatusCode(), is(HttpStatus.OK));
		assertNotNull(detail.getBody());
	}

	@Test
	public void visitorCanCreateUpdateAndReadCart() {
		ReadableProduct product = sampleProduct(uniqueProductCode("cart"));
		PersistableShoppingCartItem item = cartItem(product.getSku(), 1);
		ResponseEntity<ReadableShoppingCart> created = testRestTemplate.postForEntity("/api/v1/cart?store=DEFAULT",
				new HttpEntity<>(item, getHeader()), ReadableShoppingCart.class);
		assertThat(created.getStatusCode(), is(HttpStatus.CREATED));
		assertNotNull(created.getBody());

		ResponseEntity<ReadableShoppingCart> updated = testRestTemplate.exchange(
				"/api/v1/cart/" + created.getBody().getCode() + "?store=DEFAULT", HttpMethod.PUT,
				new HttpEntity<>(cartItem(product.getSku(), 2), getHeader()), ReadableShoppingCart.class);
		assertThat(updated.getStatusCode(), is(HttpStatus.CREATED));
		assertThat(updated.getBody().getQuantity(), is(2));

		ResponseEntity<ReadableShoppingCart> read = testRestTemplate.getForEntity(
				"/api/v1/cart/" + created.getBody().getCode() + "?store=DEFAULT", ReadableShoppingCart.class);
		assertThat(read.getStatusCode(), is(HttpStatus.OK));
		assertThat(read.getBody().getQuantity(), is(2));
	}

	@Test
	public void authenticatedCustomerCanCheckoutAndReadOrder() {
		String email = uniqueEmail("checkout");
		registerCustomer(email);
		HttpHeaders customerHeaders = customerHeaders(email);

		ReadableProduct product = sampleProduct(uniqueProductCode("checkout"));
		ResponseEntity<ReadableShoppingCart> cartResponse = testRestTemplate.postForEntity("/api/v1/cart?store=DEFAULT",
				new HttpEntity<>(cartItem(product.getSku(), 1), customerHeaders), ReadableShoppingCart.class);
		assertThat(cartResponse.getStatusCode(), is(HttpStatus.CREATED));
		ReadableShoppingCart cart = cartResponse.getBody();
		assertNotNull(cart);

		PersistablePayment payment = new PersistablePayment();
		payment.setPaymentModule("moneyorder");
		payment.setPaymentType("MONEYORDER");
		payment.setTransactionType("AUTHORIZE");
		payment.setAmount(cart.getTotal().toPlainString());
		PersistableOrder order = new PersistableOrder();
		order.setCurrency("CAD");
		order.setCustomerAgreement(true);
		order.setPayment(payment);
		configureMoneyOrderPayment();

		ResponseEntity<String> checkout = testRestTemplate.exchange(
				"/api/v1/auth/cart/" + cart.getCode() + "/checkout?store=DEFAULT", HttpMethod.POST,
				new HttpEntity<>(order, customerHeaders), String.class);
		assertThat(checkout.getStatusCode(), is(HttpStatus.OK));

		ResponseEntity<String> orders = testRestTemplate.exchange("/api/v1/auth/orders?store=DEFAULT",
				HttpMethod.GET, new HttpEntity<>(customerHeaders), String.class);
		assertThat(orders.getStatusCode(), is(HttpStatus.OK));
		assertNotNull(orders.getBody());
		assertTrue(!orders.getBody().isEmpty());
	}

	@Test
	public void servicesEndpointAcceptsValidBasicAuthAndRejectsInvalidCredentials() {
		HttpHeaders valid = new HttpHeaders();
		valid.setBasicAuth("admin@shopizer.com", "password", StandardCharsets.UTF_8);
		valid.setContentType(MediaType.APPLICATION_JSON);
		ResponseEntity<String> accepted = testRestTemplate.exchange("/services/private/system/optin",
				HttpMethod.POST, new HttpEntity<>("{}", valid), String.class);
		assertThat(accepted.getStatusCode(), is(HttpStatus.OK));

		HttpHeaders invalid = new HttpHeaders();
		invalid.setBasicAuth("admin@shopizer.com", "wrong-password", StandardCharsets.UTF_8);
		invalid.setContentType(MediaType.APPLICATION_JSON);
		ResponseEntity<String> rejected = testRestTemplate.exchange("/services/private/system/optin",
				HttpMethod.POST, new HttpEntity<>("{}", invalid), String.class);
		assertThat(rejected.getStatusCode(), is(HttpStatus.UNAUTHORIZED));
	}

	@Test
	public void actuatorHealthIsUp() {
		ResponseEntity<String> health = testRestTemplate.getForEntity("/actuator/health", String.class);

		assertThat(health.getStatusCode(), is(HttpStatus.OK));
		assertTrue(health.getBody().contains("\"status\":\"UP\""));
	}

	@Test
	public void swaggerV2DocumentationIsAvailable() {
		ResponseEntity<String> docs = testRestTemplate.getForEntity("/v2/api-docs", String.class);

		assertThat(docs.getStatusCode(), is(HttpStatus.OK));
		assertTrue(docs.getBody().contains("\"swagger\":\"2.0\""));
	}

	@Test
	public void storeAdminCannotReparentOrReadDefaultStoreCustomers() {
		String code = "pr210uatstore";
		String adminName = uniqueEmail("store-admin");
		createStore(code, null);
		createAdmin(code, adminName);
		HttpHeaders adminHeaders = getHeader(adminName, STORE_ADMIN_PASSWORD);

		assertThat(customers(code, adminHeaders), is(HttpStatus.OK));

		ResponseEntity<Void> reparent = testRestTemplate.exchange("/api/v1/private/store/" + code, HttpMethod.PUT,
				new HttpEntity<>(store(code, "DEFAULT"), adminHeaders), Void.class);
		assertThat(reparent.getStatusCode(), is(HttpStatus.UNAUTHORIZED));
		assertThat(customers("DEFAULT", adminHeaders), is(HttpStatus.UNAUTHORIZED));
	}

	private HttpStatus customers(String code, HttpHeaders headers) {
		return testRestTemplate.exchange("/api/v1/private/customers?store=" + code, HttpMethod.GET,
				new HttpEntity<>(headers), String.class).getStatusCode();
	}

	private HttpStatus privateStore(String code, HttpHeaders headers) {
		return testRestTemplate.exchange("/api/v1/private/store/" + code, HttpMethod.GET,
				new HttpEntity<>(headers), String.class).getStatusCode();
	}

	private static void assertUnauthorized(HttpStatus status) {
		assertTrue("Expected 401 or 403, received " + status,
				status == HttpStatus.UNAUTHORIZED || status == HttpStatus.FORBIDDEN);
	}

	private void registerCustomer(String email) {
		PersistableCustomer customer = new PersistableCustomer();
		customer.setEmailAddress(email);
		customer.setPassword(CUSTOMER_PASSWORD);
		customer.setGender(CustomerGender.M.name());
		customer.setLanguage("en");
		customer.setStoreCode("DEFAULT");
		Address billing = new Address();
		billing.setFirstName("UAT");
		billing.setLastName("Customer");
		billing.setCountry("US");
		customer.setBilling(billing);

		ResponseEntity<String> response = testRestTemplate.postForEntity("/api/v1/customer/register",
				new HttpEntity<>(customer), String.class);
		assertTrue(response.getStatusCode().is2xxSuccessful());
	}

	private HttpHeaders customerHeaders(String email) {
		ResponseEntity<AuthenticationResponse> login = testRestTemplate.postForEntity("/api/v1/customer/login",
				new HttpEntity<>(new AuthenticationRequest(email, CUSTOMER_PASSWORD)), AuthenticationResponse.class);
		assertThat(login.getStatusCode(), is(HttpStatus.OK));
		assertNotNull(login.getBody());
		HttpHeaders headers = new HttpHeaders();
		headers.setBearerAuth(login.getBody().getToken());
		return headers;
	}

	private static PersistableShoppingCartItem cartItem(String sku, int quantity) {
		PersistableShoppingCartItem item = new PersistableShoppingCartItem();
		item.setProduct(sku);
		item.setQuantity(quantity);
		return item;
	}

	private void configureMoneyOrderPayment() {
		IntegrationModuleConfiguration paymentConfig = new IntegrationModuleConfiguration();
		paymentConfig.setCode("moneyorder");
		paymentConfig.setActive(true);
		paymentConfig.setDefaultSelected(true);
		paymentConfig.setIntegrationKeys(Collections.singletonMap("address", "Test payment address"));
		ResponseEntity<Void> response = testRestTemplate.exchange("/api/v1/private/modules/payment?store=DEFAULT",
				HttpMethod.POST, new HttpEntity<>(paymentConfig, getHeader()), Void.class);
		assertThat(response.getStatusCode(), is(HttpStatus.OK));
	}

	private void createStore(String code, String parent) {
		ResponseEntity<Void> response = testRestTemplate.exchange("/api/v1/private/store", HttpMethod.POST,
				new HttpEntity<>(store(code, parent), getHeader()), Void.class);
		assertThat(response.getStatusCode(), is(HttpStatus.OK));
	}

	private void createAdmin(String storeCode, String userName) {
		PersistableUser user = new PersistableUser();
		user.setDefaultLanguage("en");
		user.setEmailAddress(userName);
		user.setFirstName("UAT");
		user.setLastName("Admin");
		user.setUserName(userName);
		user.setPassword(STORE_ADMIN_PASSWORD);
		user.setRepeatPassword(STORE_ADMIN_PASSWORD);
		user.setActive(true);
		PersistableGroup group = new PersistableGroup();
		group.setName("ADMIN");
		user.getGroups().add(group);
		ResponseEntity<String> response = testRestTemplate.exchange("/api/v1/private/user/?store=" + storeCode,
				HttpMethod.POST, new HttpEntity<>(user, getHeader()), String.class);
		assertThat(response.getStatusCode(), is(HttpStatus.OK));
	}

	private PersistableMerchantStore store(String code, String parent) {
		PersistableAddress address = new PersistableAddress();
		address.setAddress("121212 simple address");
		address.setPostalCode("12345");
		address.setCountry("US");
		address.setCity("FT LD");
		address.setStateProvince("FL");

		PersistableMerchantStore store = new PersistableMerchantStore();
		store.setCode(code);
		store.setCurrency("CAD");
		store.setDefaultLanguage("en");
		store.setEmail("store-" + code + "@example.com");
		store.setName(code);
		store.setPhone("444-555-6666");
		store.setSupportedLanguages(Arrays.asList("en"));
		store.setAddress(address);
		store.setRetailerStore(parent);
		return store;
	}

	private static String uniqueEmail(String prefix) {
		return prefix + "-" + System.nanoTime() + "@example.test";
	}

	private static String uniqueProductCode(String prefix) {
		return prefix + System.nanoTime();
	}
}
