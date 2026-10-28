package com.salesmanager.test.shop.uat;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.Base64;

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

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.salesmanager.core.business.constants.Constants;
import com.salesmanager.core.model.customer.CustomerGender;
import com.salesmanager.core.model.merchant.MerchantStore;
import com.salesmanager.shop.application.ShopApplication;
import com.salesmanager.shop.model.catalog.product.ReadableProduct;
import com.salesmanager.shop.model.catalog.product.product.PersistableProduct;
import com.salesmanager.shop.model.customer.PersistableCustomer;
import com.salesmanager.shop.model.customer.address.Address;
import com.salesmanager.shop.model.entity.Entity;
import com.salesmanager.shop.model.references.PersistableAddress;
import com.salesmanager.shop.model.shoppingcart.PersistableShoppingCartItem;
import com.salesmanager.shop.model.store.PersistableMerchantStore;
import com.salesmanager.shop.model.security.PersistableGroup;
import com.salesmanager.shop.model.user.PersistableUser;
import com.salesmanager.shop.store.security.AuthenticationRequest;
import com.salesmanager.shop.store.security.AuthenticationResponse;
import com.salesmanager.test.shop.common.ServicesTestSupport;

/**
 * Black-box user journeys through the public REST contract (random port, H2).
 */
@SpringBootTest(classes = ShopApplication.class, webEnvironment = WebEnvironment.RANDOM_PORT)
@RunWith(SpringRunner.class)
public class StorefrontAndAdminJourneyUatTest extends ServicesTestSupport {

	private static final ObjectMapper JSON = new ObjectMapper();

	@Test
	public void adminLoginGrantsPrivateApiAndMissingOrBadTokenIsRejected() throws Exception {
		ResponseEntity<String> ok = get("/api/v1/private/users/1", getHeader());
		assertEquals(HttpStatus.OK, ok.getStatusCode());
		assertEquals("admin@shopizer.com", JSON.readTree(ok.getBody()).get("userName").asText());

		HttpHeaders none = jsonHeaders();
		assertUnauthorized(get("/api/v1/private/users/1", none).getStatusCode());

		// a malformed bearer token is answered with 500 on base and head alike; only require that it is refused
		HttpHeaders bad = jsonHeaders();
		bad.add("Authorization", "Bearer not.a.valid.jwt");
		ResponseEntity<String> badToken = get("/api/v1/private/users/1", bad);
		assertFalse(badToken.getStatusCode().is2xxSuccessful());
		assertFalse(String.valueOf(badToken.getBody()).contains("admin@shopizer.com"));

		ResponseEntity<String> wrongPassword = testRestTemplate.postForEntity("/api/v1/private/login",
				new HttpEntity<>(new AuthenticationRequest("admin@shopizer.com", "wrong")), String.class);
		assertUnauthorized(wrongPassword.getStatusCode());
	}

	@Test
	public void customerRegistersLogsInAndReadsProfile() throws Exception {
		String email = "uat.customer.profile@test.com";
		String token = registerAndLogin(email, "clear123");
		HttpHeaders auth = bearer(token);
		ResponseEntity<String> profile = get("/api/v1/auth/customer/profile", auth);
		assertEquals(HttpStatus.OK, profile.getStatusCode());
		assertEquals(email, JSON.readTree(profile.getBody()).get("emailAddress").asText());

		assertUnauthorized(get("/api/v1/auth/customer/profile", jsonHeaders()).getStatusCode());
	}

	@Test
	public void shopperBrowsesStoreCategoriesAndProducts() throws Exception {
		ReadableProduct created = sampleProduct("uat-browse");

		ResponseEntity<String> store = get("/api/v1/store/" + Constants.DEFAULT_STORE, jsonHeaders());
		assertEquals(HttpStatus.OK, store.getStatusCode());
		assertEquals(Constants.DEFAULT_STORE, JSON.readTree(store.getBody()).get("code").asText());

		ResponseEntity<String> categories = get("/api/v1/category?store=DEFAULT&lang=en", jsonHeaders());
		assertEquals(HttpStatus.OK, categories.getStatusCode());
		assertTrue(JSON.readTree(categories.getBody()).has("categories"));

		ResponseEntity<String> products = get("/api/v1/products?store=DEFAULT&lang=en", jsonHeaders());
		assertEquals(HttpStatus.OK, products.getStatusCode());
		JsonNode list = JSON.readTree(products.getBody());
		assertTrue(list.has("products"));
		assertTrue(list.get("recordsTotal").asLong() >= 1);

		ResponseEntity<String> detail = get("/api/v2/product/" + created.getSku() + "?store=DEFAULT&lang=en",
				jsonHeaders());
		assertEquals(HttpStatus.OK, detail.getStatusCode());
		JsonNode product = JSON.readTree(detail.getBody());
		assertEquals(created.getSku(), product.get("sku").asText());
		assertNotNull(product.get("id"));
	}

	@Test
	public void shopperCreatesCartUpdatesItemAndReadsItBack() throws Exception {
		ReadableProduct product = sampleProduct("uat-cart");
		PersistableShoppingCartItem item = new PersistableShoppingCartItem();
		item.setProduct(product.getSku());
		item.setQuantity(1);
		ResponseEntity<String> created = testRestTemplate.postForEntity("/api/v1/cart",
				new HttpEntity<>(item, jsonHeaders()), String.class);
		assertEquals(HttpStatus.CREATED, created.getStatusCode());
		String code = JSON.readTree(created.getBody()).get("code").asText();
		assertFalse(code.isEmpty());

		item.setQuantity(3);
		ResponseEntity<String> updated = testRestTemplate.exchange("/api/v1/cart/" + code, HttpMethod.PUT,
				new HttpEntity<>(item, jsonHeaders()), String.class);
		assertTrue(updated.getStatusCode().is2xxSuccessful());

		ResponseEntity<String> read = get("/api/v1/cart/" + code, jsonHeaders());
		assertEquals(HttpStatus.OK, read.getStatusCode());
		JsonNode cart = JSON.readTree(read.getBody());
		assertEquals(code, cart.get("code").asText());
		assertEquals(3, cart.get("quantity").asInt());
	}

	@Test
	public void servicesApiRequiresValidBasicAuth() {
		// rejected credentials are answered with a redirect (302) by the /services entry point on base and head
		HttpStatus wrong = postServices(basic("admin@shopizer.com", "wrong"));
		assertFalse("bad credentials accepted: " + wrong, wrong.is2xxSuccessful());
		HttpStatus none = postServices(null);
		assertFalse("missing credentials accepted: " + none, none.is2xxSuccessful());
		HttpStatus status = postServices(basic("admin@shopizer.com", "password"));
		assertTrue("valid credentials must pass authentication, got " + status, status.is2xxSuccessful());

		ResponseEntity<String> publicStore = get("/services/public/" + Constants.DEFAULT_STORE, new HttpHeaders());
		assertTrue(publicStore.getStatusCode().is2xxSuccessful());
	}

	private HttpStatus postServices(String authorization) {
		HttpHeaders headers = jsonHeaders();
		if (authorization != null) {
			headers.add("Authorization", authorization);
		}
		return testRestTemplate.exchange("/services/private/system/optin", HttpMethod.POST,
				new HttpEntity<>("{}", headers), String.class).getStatusCode();
	}

	@Test
	public void actuatorHealthIsUpAndApiDocsListInventoryEndpoint() throws Exception {
		ResponseEntity<String> health = get("/actuator/health", jsonHeaders());
		assertEquals(HttpStatus.OK, health.getStatusCode());
		assertEquals("UP", JSON.readTree(health.getBody()).get("status").asText());

		ResponseEntity<String> docs = get("/v2/api-docs", jsonHeaders());
		assertEquals(HttpStatus.OK, docs.getStatusCode());
		assertTrue(JSON.readTree(docs.getBody()).get("paths").has("/api/v1/private/product/{sku}/inventory"));
	}

	@Test
	public void storeAdminCannotReadAnotherStoresInventoryBySku() throws Exception {
		String storeCode = "UATINV";
		createStore(storeCode);
		String defaultOnlySku = "UAT-INV-DEFAULT-ONLY";
		createProduct(MerchantStore.DEFAULT_STORE, defaultOnlySku, 9);

		String user = "uat.store.admin@test.com";
		createStoreAdmin(storeCode, user, "Password1");
		HttpHeaders storeAdmin = getHeader(user, "Password1");

		ResponseEntity<String> own = get("/api/v1/private/product/" + defaultOnlySku + "/inventory?store=" + storeCode,
				storeAdmin);
		assertEquals(HttpStatus.OK, own.getStatusCode());
		JsonNode body = JSON.readTree(own.getBody());
		assertEquals(0, body.get("recordsTotal").asLong());
		assertEquals(0, body.get("items").size());

		ResponseEntity<String> foreign = get(
				"/api/v1/private/product/" + defaultOnlySku + "/inventory?store=" + MerchantStore.DEFAULT_STORE,
				storeAdmin);
		assertUnauthorized(foreign.getStatusCode());

		ResponseEntity<String> superAdmin = get(
				"/api/v1/private/product/" + defaultOnlySku + "/inventory?store=" + MerchantStore.DEFAULT_STORE,
				getHeader());
		assertEquals(HttpStatus.OK, superAdmin.getStatusCode());
		JsonNode items = JSON.readTree(superAdmin.getBody()).get("items");
		assertEquals(1, items.size());
		assertEquals(9, items.get(0).get("quantity").asInt());
	}

	private String registerAndLogin(String email, String password) {
		PersistableCustomer customer = new PersistableCustomer();
		customer.setEmailAddress(email);
		customer.setPassword(password);
		customer.setGender(CustomerGender.F.name());
		customer.setLanguage("en");
		Address billing = new Address();
		billing.setFirstName("Uat");
		billing.setLastName("Customer");
		billing.setCountry("CA");
		customer.setBilling(billing);
		customer.setStoreCode(Constants.DEFAULT_STORE);
		ResponseEntity<String> registered = testRestTemplate.postForEntity("/api/v1/customer/register",
				new HttpEntity<>(customer, jsonHeaders()), String.class);
		assertEquals(registered.getBody(), HttpStatus.OK, registered.getStatusCode());
		ResponseEntity<AuthenticationResponse> login = testRestTemplate.postForEntity("/api/v1/customer/login",
				new HttpEntity<>(new AuthenticationRequest(email, password)), AuthenticationResponse.class);
		assertEquals(HttpStatus.OK, login.getStatusCode());
		assertNotNull(login.getBody().getToken());
		return login.getBody().getToken();
	}

	private void createStore(String code) {
		if (get("/api/v1/store/" + code, getHeader()).getStatusCode() == HttpStatus.OK) {
			return;
		}
		PersistableAddress address = new PersistableAddress();
		address.setAddress("1 UAT street");
		address.setPostalCode("12345");
		address.setCountry("US");
		address.setCity("Miami");
		address.setStateProvince("FL");
		PersistableMerchantStore store = new PersistableMerchantStore();
		store.setCode(code);
		store.setCurrency("USD");
		store.setDefaultLanguage("en");
		store.setEmail(code.toLowerCase() + "@test.com");
		store.setName(code);
		store.setPhone("444-555-6666");
		store.setSupportedLanguages(Arrays.asList("en"));
		store.setAddress(address);
		ResponseEntity<String> response = testRestTemplate.exchange("/api/v1/private/store/", HttpMethod.POST,
				new HttpEntity<>(store, getHeader()), String.class);
		assertEquals(response.getBody(), HttpStatus.OK, response.getStatusCode());
	}

	private void createProduct(String store, String sku, int quantity) {
		PersistableProduct product = product(sku);
		product.setSku(sku);
		product.setAvailable(true);
		product.setPrice(BigDecimal.TEN);
		product.setQuantity(quantity);
		product.getInventory().setQuantity(quantity);
		ResponseEntity<Entity> response = testRestTemplate.postForEntity("/api/v1/private/product?store=" + store,
				new HttpEntity<>(product, getHeader()), Entity.class);
		assertEquals(HttpStatus.CREATED, response.getStatusCode());
	}

	private void createStoreAdmin(String store, String userName, String password) {
		PersistableUser user = new PersistableUser();
		user.setDefaultLanguage("en");
		user.setEmailAddress(userName);
		user.setFirstName("Store");
		user.setLastName("Admin");
		user.setUserName(userName);
		user.setPassword(password);
		user.setRepeatPassword(password);
		user.setStore(store);
		user.setActive(true);
		PersistableGroup group = new PersistableGroup();
		group.setName("ADMIN");
		user.getGroups().add(group);
		ResponseEntity<String> response = testRestTemplate.exchange("/api/v1/private/user/?store=" + store,
				HttpMethod.POST, new HttpEntity<>(user, getHeader()), String.class);
		assertEquals(response.getBody(), HttpStatus.OK, response.getStatusCode());
	}

	private ResponseEntity<String> get(String url, HttpHeaders headers) {
		return testRestTemplate.exchange(url, HttpMethod.GET, new HttpEntity<>(headers), String.class);
	}

	private static HttpHeaders jsonHeaders() {
		HttpHeaders headers = new HttpHeaders();
		headers.setContentType(MediaType.APPLICATION_JSON);
		return headers;
	}

	private static HttpHeaders bearer(String token) {
		HttpHeaders headers = jsonHeaders();
		headers.add("Authorization", "Bearer " + token);
		return headers;
	}

	private static String basic(String user, String password) {
		return "Basic " + Base64.getEncoder().encodeToString((user + ":" + password).getBytes(StandardCharsets.UTF_8));
	}

	private static void assertUnauthorized(HttpStatus status) {
		assertTrue("expected 401/403 but was " + status,
				status == HttpStatus.UNAUTHORIZED || status == HttpStatus.FORBIDDEN);
	}
}
