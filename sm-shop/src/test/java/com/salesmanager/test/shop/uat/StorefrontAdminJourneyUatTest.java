package com.salesmanager.test.shop.uat;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.UUID;

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
import com.salesmanager.shop.model.catalog.category.Category;
import com.salesmanager.shop.model.catalog.category.CategoryDescription;
import com.salesmanager.shop.model.catalog.category.PersistableCategory;
import com.salesmanager.shop.model.catalog.manufacturer.PersistableManufacturer;
import com.salesmanager.shop.model.catalog.product.product.PersistableProduct;
import com.salesmanager.shop.model.catalog.product.product.ProductSpecification;
import com.salesmanager.shop.application.ShopApplication;
import com.salesmanager.shop.model.catalog.product.ReadableProduct;
import com.salesmanager.shop.model.catalog.product.ReadableProductList;
import com.salesmanager.shop.model.customer.PersistableCustomer;
import com.salesmanager.shop.model.customer.address.Address;
import com.salesmanager.shop.model.entity.Entity;
import com.salesmanager.shop.model.order.v1.PersistableOrder;
import com.salesmanager.shop.model.order.v1.ReadableOrderConfirmation;
import com.salesmanager.shop.model.order.transaction.PersistablePayment;
import com.salesmanager.shop.model.references.PersistableAddress;
import com.salesmanager.shop.model.security.PersistableGroup;
import com.salesmanager.shop.model.store.PersistableBrand;
import com.salesmanager.shop.model.store.PersistableMerchantStore;
import com.salesmanager.shop.model.system.IntegrationModuleConfiguration;
import com.salesmanager.shop.model.user.PersistableUser;
import com.salesmanager.shop.store.security.AuthenticationRequest;
import com.salesmanager.shop.store.security.AuthenticationResponse;
import com.salesmanager.shop.model.shoppingcart.PersistableShoppingCartItem;
import com.salesmanager.shop.model.shoppingcart.ReadableShoppingCart;
import com.salesmanager.test.shop.common.ServicesTestSupport;

@RunWith(SpringRunner.class)
@SpringBootTest(classes = ShopApplication.class, webEnvironment = WebEnvironment.RANDOM_PORT)
public class StorefrontAdminJourneyUatTest extends ServicesTestSupport {

	private static final String ADMIN_PASSWORD = "password";
	private static final String USER_PASSWORD = "Password1";
	private final String suffix = UUID.randomUUID().toString().replace("-", "").substring(0, 12);

	@Test
	public void adminLoginJwtAndPrivateUserAccess() {
		ResponseEntity<?> login = testRestTemplate.postForEntity("/api/v1/private/login",
				new HttpEntity<>(new AuthenticationRequest("admin@shopizer.com", ADMIN_PASSWORD)), Object.class);
		assertEquals(HttpStatus.OK, login.getStatusCode());
		assertNotNull(login.getBody());
		assertEquals(HttpStatus.OK, testRestTemplate.exchange("/api/v1/private/users/1?store=DEFAULT",
				HttpMethod.GET, new HttpEntity<>(getHeader()), String.class).getStatusCode());

		ResponseEntity<String> anonymous = testRestTemplate.exchange("/api/v1/private/users/1?store=DEFAULT",
				HttpMethod.GET, new HttpEntity<>(new HttpHeaders()), String.class);
		assertTrue(anonymous.getStatusCode() == HttpStatus.UNAUTHORIZED
				|| anonymous.getStatusCode() == HttpStatus.FORBIDDEN);

	}

	@Test
	public void customerRegistrationLoginAndAuthenticatedProfile() {
		String email = "customer-" + suffix + "@example.com";
		PersistableCustomer customer = new PersistableCustomer();
		customer.setEmailAddress(email);
		customer.setPassword(USER_PASSWORD);
		customer.setGender(CustomerGender.M.name());
		customer.setLanguage("en");
		customer.setFirstName("UAT");
		customer.setLastName("Customer");
		customer.setStoreCode("DEFAULT");
		Address billing = new Address();
		billing.setFirstName("UAT");
		billing.setLastName("Customer");
		billing.setCountry("US");
		customer.setBilling(billing);
		ResponseEntity<String> registration = testRestTemplate.postForEntity("/api/v1/customer/register?store=DEFAULT",
				new HttpEntity<>(customer, jsonHeaders()), String.class);
		assertTrue(registration.getStatusCode() == HttpStatus.OK || registration.getStatusCode() == HttpStatus.CREATED);

		ResponseEntity<AuthenticationResponse> login = testRestTemplate.postForEntity("/api/v1/customer/login",
				new HttpEntity<>(new AuthenticationRequest(email, USER_PASSWORD), jsonHeaders()),
				AuthenticationResponse.class);
		assertEquals(HttpStatus.OK, login.getStatusCode());
		assertNotNull(login.getBody().getToken());
		HttpHeaders customerHeaders = bearer(login.getBody().getToken());
		ResponseEntity<String> profile = testRestTemplate.exchange("/api/v1/auth/customer/profile?store=DEFAULT",
				HttpMethod.GET, new HttpEntity<>(customerHeaders), String.class);
		assertEquals(HttpStatus.OK, profile.getStatusCode());
	}

	@Test
	public void browseStoreCategoriesProductsAndProductDetails() {
		assertEquals(HttpStatus.OK, testRestTemplate.getForEntity("/api/v1/store/DEFAULT", String.class).getStatusCode());
		assertEquals(HttpStatus.OK,
				testRestTemplate.getForEntity("/api/v1/category?store=DEFAULT", String.class).getStatusCode());
		ResponseEntity<ReadableProductList> defaultProducts = testRestTemplate.getForEntity(
				"/api/v1/products?store=DEFAULT&count=10", ReadableProductList.class);
		assertEquals(HttpStatus.OK, defaultProducts.getStatusCode());
		assertNotNull(defaultProducts.getBody());

		String storeCode = "BRW" + suffix.substring(0, 8).toUpperCase();
		createStore(storeCode);
		String sku = createProduct(storeCode, "browse" + suffix);
		ResponseEntity<ReadableProductList> products = testRestTemplate.getForEntity(
				"/api/v1/products?store=" + storeCode + "&count=10", ReadableProductList.class);
		assertEquals(HttpStatus.OK, products.getStatusCode());
		assertNotNull(products.getBody());
		assertTrue("Expected the created product", !products.getBody().getProducts().isEmpty());
		ReadableProduct product = products.getBody().getProducts().stream()
				.filter(item -> sku.equals(item.getSku())).findFirst().orElse(null);
		assertNotNull(product);
		assertNotNull(product.getSku());
		ResponseEntity<String> detail = testRestTemplate.getForEntity(
				"/api/v2/product/" + product.getSku() + "?store=" + storeCode, String.class);
		assertEquals(HttpStatus.OK, detail.getStatusCode());
	}

	@Test
	public void shoppingCartCreateUpdateAndRead() {
		String storeCode = "CRT" + suffix.substring(0, 8).toUpperCase();
		createStore(storeCode);
		String sku = createProduct(storeCode, "cart" + suffix);
		PersistableShoppingCartItem item = new PersistableShoppingCartItem();
		item.setProduct(sku);
		item.setQuantity(1);
		ResponseEntity<ReadableShoppingCart> created = testRestTemplate.postForEntity("/api/v1/cart?store=" + storeCode,
				new HttpEntity<>(item, jsonHeaders()), ReadableShoppingCart.class);
		assertEquals(HttpStatus.CREATED, created.getStatusCode());
		assertNotNull(created.getBody());
		assertNotNull(created.getBody().getCode());

		item.setQuantity(2);
		ResponseEntity<ReadableShoppingCart> updated = testRestTemplate.exchange(
				"/api/v1/cart/" + created.getBody().getCode() + "?store=" + storeCode, HttpMethod.PUT,
				new HttpEntity<>(item, jsonHeaders()), ReadableShoppingCart.class);
		assertEquals(HttpStatus.CREATED, updated.getStatusCode());
		ResponseEntity<ReadableShoppingCart> fetched = testRestTemplate.getForEntity(
				"/api/v1/cart/" + created.getBody().getCode() + "?store=" + storeCode, ReadableShoppingCart.class);
		assertEquals(HttpStatus.OK, fetched.getStatusCode());
		assertNotNull(fetched.getBody());
	}

	@Test
	public void authenticatedCustomerCanPlaceAndReadOrder() {
		String storeCode = "ORD" + suffix.substring(0, 8).toUpperCase();
		createStore(storeCode);
		configureMoneyOrder(storeCode);
		String sku = createProduct(storeCode, "order" + suffix);
		String email = "checkout-" + suffix + "@example.com";
		String customerToken = registerAndLoginCustomer(email, storeCode);
		PersistableShoppingCartItem item = new PersistableShoppingCartItem();
		item.setProduct(sku);
		item.setQuantity(1);
		ReadableShoppingCart cart = testRestTemplate.postForEntity("/api/v1/cart?store=" + storeCode,
				new HttpEntity<>(item, jsonHeaders()), ReadableShoppingCart.class).getBody();
		assertNotNull(cart);

		PersistableOrder order = new PersistableOrder();
		order.setCurrency("CAD");
		order.setCustomerAgreement(true);
		PersistablePayment payment = new PersistablePayment();
		payment.setPaymentType("MONEYORDER");
		payment.setPaymentModule("moneyorder");
		assertNotNull(cart.getTotal());
		payment.setAmount(cart.getTotal().toPlainString());
		payment.setTransactionType("CAPTURE");
		order.setPayment(payment);
		ResponseEntity<ReadableOrderConfirmation> confirmation = testRestTemplate.exchange(
				"/api/v1/auth/cart/" + cart.getCode() + "/checkout?store=" + storeCode, HttpMethod.POST,
				new HttpEntity<>(order, bearer(customerToken)), ReadableOrderConfirmation.class);
		assertEquals("Checkout response: " + confirmation.getBody(), HttpStatus.OK, confirmation.getStatusCode());
		assertNotNull(confirmation.getBody());
		assertNotNull(confirmation.getBody().getId());

		ResponseEntity<String> orders = testRestTemplate.exchange("/api/v1/auth/orders?store=" + storeCode, HttpMethod.GET,
				new HttpEntity<>(bearer(customerToken)), String.class);
		assertEquals(HttpStatus.OK, orders.getStatusCode());
		ResponseEntity<String> readOrder = testRestTemplate.exchange(
				"/api/v1/auth/orders/" + confirmation.getBody().getId() + "?store=" + storeCode, HttpMethod.GET,
				new HttpEntity<>(bearer(customerToken)), String.class);
		assertEquals(HttpStatus.OK, readOrder.getStatusCode());
	}

	@Test
	public void servicesBasicAuthenticationAndOperationalEndpoints() {
		HttpHeaders validBasic = new HttpHeaders();
		validBasic.setBasicAuth("admin@shopizer.com", ADMIN_PASSWORD);
		validBasic.setContentType(MediaType.APPLICATION_JSON);
		ResponseEntity<String> valid = testRestTemplate.exchange("/services/private/system/optin", HttpMethod.POST,
				new HttpEntity<>("{\"name\":\"uat\"}", validBasic), String.class);
		assertEquals(HttpStatus.OK, valid.getStatusCode());

		HttpHeaders invalidBasic = new HttpHeaders();
		invalidBasic.setBasicAuth("admin@shopizer.com", "invalid");
		ResponseEntity<String> invalid = testRestTemplate.exchange("/services/private/system/optin", HttpMethod.POST,
				new HttpEntity<>("{\"name\":\"uat\"}", invalidBasic), String.class);
		assertEquals(HttpStatus.UNAUTHORIZED, invalid.getStatusCode());

		ResponseEntity<String> health = testRestTemplate.getForEntity("/actuator/health", String.class);
		assertEquals(HttpStatus.OK, health.getStatusCode());
		assertTrue(health.getBody().contains("\"status\":\"UP\""));
		assertEquals(HttpStatus.OK, testRestTemplate.getForEntity("/v2/api-docs", String.class).getStatusCode());
	}

	@Test
	public void storeManagerCanEditOwnMarketingButCatalogueRoleCannot() {
		String storeCode = "UAT" + suffix.substring(0, 8).toUpperCase();
		createStore(storeCode);
		String manager = createBackOfficeUser("ADMIN_RETAILER", "manager", storeCode);
		String catalogueUser = createBackOfficeUser("ADMIN_CATALOGUE", "catalogue", storeCode);

		PersistableBrand brand = new PersistableBrand();
		ResponseEntity<String> saved = testRestTemplate.exchange(
				"/api/v1/private/store/" + storeCode + "/marketing", HttpMethod.POST,
				new HttpEntity<>(brand, getHeader(manager, USER_PASSWORD)), String.class);
		assertTrue(saved.getStatusCode() == HttpStatus.CREATED || saved.getStatusCode() == HttpStatus.OK);
		assertEquals(HttpStatus.OK, testRestTemplate.exchange(
				"/api/v1/private/store/" + storeCode + "/marketing", HttpMethod.GET,
				new HttpEntity<>(getHeader(manager, USER_PASSWORD)), String.class).getStatusCode());
		ResponseEntity<String> denied = testRestTemplate.exchange(
				"/api/v1/private/store/" + storeCode + "/marketing", HttpMethod.GET,
				new HttpEntity<>(getHeader(catalogueUser, USER_PASSWORD)), String.class);
		assertEquals(HttpStatus.UNAUTHORIZED, denied.getStatusCode());
	}

	private String registerAndLoginCustomer(String email) {
		return registerAndLoginCustomer(email, "DEFAULT");
	}

	private String registerAndLoginCustomer(String email, String storeCode) {
		PersistableCustomer customer = new PersistableCustomer();
		customer.setEmailAddress(email);
		customer.setPassword(USER_PASSWORD);
		customer.setGender(CustomerGender.M.name());
		customer.setLanguage("en");
		customer.setFirstName("UAT");
		customer.setLastName("Checkout");
		customer.setStoreCode(storeCode);
		Address billing = new Address();
		billing.setFirstName("UAT");
		billing.setLastName("Checkout");
		billing.setCountry("US");
		customer.setBilling(billing);
		ResponseEntity<String> registration = testRestTemplate.postForEntity("/api/v1/customer/register?store=" + storeCode,
				new HttpEntity<>(customer, jsonHeaders()), String.class);
		assertTrue(registration.getStatusCode() == HttpStatus.OK || registration.getStatusCode() == HttpStatus.CREATED);
		ResponseEntity<AuthenticationResponse> login = testRestTemplate.postForEntity("/api/v1/customer/login",
				new HttpEntity<>(new AuthenticationRequest(email, USER_PASSWORD), jsonHeaders()),
				AuthenticationResponse.class);
		assertEquals(HttpStatus.OK, login.getStatusCode());
		return login.getBody().getToken();
	}

	private void configureMoneyOrder(String storeCode) {
		IntegrationModuleConfiguration configuration = new IntegrationModuleConfiguration();
		configuration.setCode("moneyorder");
		configuration.setActive(true);
		configuration.setDefaultSelected(true);
		configuration.getIntegrationKeys().put("address", "UAT test payment address");
		ResponseEntity<String> response = testRestTemplate.exchange(
				"/api/v1/private/modules/payment?store=" + storeCode, HttpMethod.POST,
				new HttpEntity<>(configuration, getHeader()), String.class);
		assertEquals("Payment configuration response: " + response.getBody(), HttpStatus.OK,
				response.getStatusCode());
	}

	private String createProduct(String storeCode, String sku) {
		String manufacturerCode = "MFR" + sku.toUpperCase();
		PersistableManufacturer manufacturer = manufacturer(manufacturerCode);
		ResponseEntity<PersistableManufacturer> manufacturerResponse = testRestTemplate.postForEntity(
				"/api/v1/private/manufacturer?store=" + storeCode,
				new HttpEntity<>(manufacturer, getHeader()), PersistableManufacturer.class);
		assertEquals("Manufacturer creation response: " + manufacturerResponse.getBody(), HttpStatus.CREATED,
				manufacturerResponse.getStatusCode());

		PersistableCategory category = new PersistableCategory();
		category.setCode("cat-" + sku);
		category.setSortOrder(1);
		category.setVisible(true);
		category.setDepth(1);
		CategoryDescription description = new CategoryDescription();
		description.setLanguage("en");
		description.setName(sku);
		description.setFriendlyUrl(sku);
		description.setTitle(sku);
		category.setDescriptions(Arrays.asList(description));
		ResponseEntity<PersistableCategory> categoryResponse = testRestTemplate.postForEntity(
				"/api/v1/private/category?store=" + storeCode,
				new HttpEntity<>(category, getHeader()), PersistableCategory.class);
		assertEquals("Category creation response: " + categoryResponse.getBody(), HttpStatus.CREATED,
				categoryResponse.getStatusCode());

		PersistableProduct product = product(sku);
		product.setCategories(new ArrayList<Category>(Arrays.asList(categoryResponse.getBody())));
		ProductSpecification specification = new ProductSpecification();
		specification.setManufacturer(manufacturerCode);
		product.setProductSpecifications(specification);
		product.setAvailable(true);
		product.setPrice(BigDecimal.TEN);
		product.setSku(sku);
		product.setQuantity(100);
		ResponseEntity<Entity> productResponse = testRestTemplate.postForEntity(
				"/api/v1/private/product?store=" + storeCode,
				new HttpEntity<>(product, getHeader()), Entity.class);
		assertEquals("Product creation response: " + productResponse.getBody(), HttpStatus.CREATED,
				productResponse.getStatusCode());
		return sku;
	}

	private void createStore(String code) {
		PersistableAddress address = new PersistableAddress();
		address.setAddress("UAT Street");
		address.setPostalCode("12345");
		address.setCountry("US");
		address.setCity("Test City");
		address.setStateProvince("CA");
		PersistableMerchantStore store = new PersistableMerchantStore();
		store.setCode(code);
		store.setCurrency("CAD");
		store.setDefaultLanguage("en");
		store.setEmail("uat-" + code.toLowerCase() + "@example.com");
		store.setName(code);
		store.setPhone("555-0100");
		store.setSupportedLanguages(Arrays.asList("en"));
		store.setAddress(address);
		ResponseEntity<String> response = testRestTemplate.exchange("/api/v1/private/store", HttpMethod.POST,
				new HttpEntity<>(store, getHeader()), String.class);
		assertEquals(HttpStatus.OK, response.getStatusCode());
	}

	private String createBackOfficeUser(String groupName, String role, String storeCode) {
		String username = role + "-" + suffix + "@example.com";
		PersistableUser user = new PersistableUser();
		user.setDefaultLanguage("en");
		user.setEmailAddress(username);
		user.setFirstName("UAT");
		user.setLastName(role);
		user.setUserName(username);
		user.setPassword(USER_PASSWORD);
		user.setRepeatPassword(USER_PASSWORD);
		user.setStore(storeCode);
		PersistableGroup group = new PersistableGroup();
		group.setName(groupName);
		user.getGroups().add(group);
		ResponseEntity<String> created = testRestTemplate.exchange(
				"/api/v1/private/user/?store=" + storeCode, HttpMethod.POST,
				new HttpEntity<>(user, getHeader()), String.class);
		assertEquals(created.getBody(), HttpStatus.OK, created.getStatusCode());
		return username;
	}

	private HttpHeaders jsonHeaders() {
		HttpHeaders headers = new HttpHeaders();
		headers.setContentType(MediaType.APPLICATION_JSON);
		return headers;
	}

	private HttpHeaders bearer(String token) {
		HttpHeaders headers = jsonHeaders();
		headers.setBearerAuth(token);
		return headers;
	}
}
