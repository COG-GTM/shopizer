package com.salesmanager.test.shop.uat;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.util.Arrays;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.SpringBootTest.WebEnvironment;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.test.context.junit.jupiter.SpringExtension;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.salesmanager.core.business.constants.Constants;
import com.salesmanager.shop.application.ShopApplication;
import com.salesmanager.shop.model.catalog.product.ReadableProduct;
import com.salesmanager.shop.model.customer.PersistableCustomer;
import com.salesmanager.shop.model.customer.address.Address;
import com.salesmanager.shop.model.order.transaction.PersistablePayment;
import com.salesmanager.shop.model.order.v1.PersistableOrder;
import com.salesmanager.shop.model.references.PersistableAddress;
import com.salesmanager.shop.model.security.PersistableGroup;
import com.salesmanager.shop.model.shoppingcart.PersistableShoppingCartItem;
import com.salesmanager.shop.model.store.PersistableMerchantStore;
import com.salesmanager.shop.model.system.IntegrationModuleConfiguration;
import com.salesmanager.shop.model.user.PersistableUser;
import com.salesmanager.shop.store.security.AuthenticationRequest;
import com.salesmanager.test.shop.common.ServicesTestSupport;

/**
 * Black-box user journeys of the storefront and admin REST API (JWT + basic auth).
 */
@SpringBootTest(classes = ShopApplication.class, webEnvironment = WebEnvironment.RANDOM_PORT)
@ExtendWith(SpringExtension.class)
public class ShopizerUserJourneyUatTest extends ServicesTestSupport {

	private final ObjectMapper mapper = new ObjectMapper();

	@Test
	public void adminLoginThenAdminOnlyCall() {
		ResponseEntity<String> login = testRestTemplate.postForEntity("/api/v1/private/login",
				new HttpEntity<>(new AuthenticationRequest("admin@shopizer.com", "password")), String.class);
		assertEquals(HttpStatus.OK, login.getStatusCode());
		String token = json(login).get("token").asText();
		assertFalse(token.isEmpty());

		ResponseEntity<String> profile = get("/api/v1/private/user/profile", bearer(token));
		assertEquals(HttpStatus.OK, profile.getStatusCode());
		assertEquals("admin@shopizer.com", json(profile).get("userName").asText());
		assertEquals(HttpStatus.OK, get("/api/v1/private/customers", bearer(token)).getStatusCode());

		assertEquals(HttpStatus.UNAUTHORIZED, get("/api/v1/private/user/profile", jsonHeaders()).getStatusCode());
		// a malformed token is rejected (observed status is 500 on base and head, not 401)
		assertTrue(get("/api/v1/private/user/profile", bearer("not.a.jwt")).getStatusCode().isError());

		ResponseEntity<String> badLogin = testRestTemplate.postForEntity("/api/v1/private/login",
				new HttpEntity<>(new AuthenticationRequest("admin@shopizer.com", "wrong")), String.class);
		assertEquals(HttpStatus.UNAUTHORIZED, badLogin.getStatusCode());
	}

	@Test
	public void customerRegisterLoginAndProfile() {
		String token = registerAndLogin("uat.customer@test.com", "clear123");

		ResponseEntity<String> profile = get("/api/v1/auth/customer/profile", bearer(token));
		assertEquals(HttpStatus.OK, profile.getStatusCode());
		assertEquals("uat.customer@test.com", json(profile).get("emailAddress").asText());

		assertEquals(HttpStatus.UNAUTHORIZED, get("/api/v1/auth/customer/profile", jsonHeaders()).getStatusCode());
	}

	@Test
	public void browseStoreCategoriesAndProducts() {
		ReadableProduct product = sampleProduct("uatbrowse");
		assertNotNull(product);

		ResponseEntity<String> store = get("/api/v1/store/" + Constants.DEFAULT_STORE, jsonHeaders());
		assertEquals(HttpStatus.OK, store.getStatusCode());
		assertEquals(Constants.DEFAULT_STORE, json(store).get("code").asText());

		ResponseEntity<String> categories = get("/api/v1/category?store=" + Constants.DEFAULT_STORE, jsonHeaders());
		assertEquals(HttpStatus.OK, categories.getStatusCode());
		assertTrue(json(categories).has("categories"));

		ResponseEntity<String> products = get("/api/v1/products?store=" + Constants.DEFAULT_STORE, jsonHeaders());
		assertEquals(HttpStatus.OK, products.getStatusCode());
		assertTrue(json(products).get("products").isArray());
		assertTrue(json(products).get("products").size() > 0);

		ResponseEntity<String> detail = get("/api/v2/product/uatbrowse?store=" + Constants.DEFAULT_STORE, jsonHeaders());
		assertEquals(HttpStatus.OK, detail.getStatusCode());
		assertEquals(product.getId().longValue(), json(detail).get("id").asLong());
		assertEquals("uatbrowse", json(detail).get("sku").asText());
	}

	@Test
	public void cartCreateAddUpdateAndRead() {
		ReadableProduct first = sampleProduct("uatcart1");
		ReadableProduct second = sampleProduct("uatcart2");

		ResponseEntity<String> created = testRestTemplate.postForEntity("/api/v1/cart/",
				new HttpEntity<>(item(first.getSku(), 1), jsonHeaders()), String.class);
		assertEquals(HttpStatus.CREATED, created.getStatusCode());
		String code = json(created).get("code").asText();
		assertEquals(1, json(created).get("quantity").asInt());

		ResponseEntity<String> added = testRestTemplate.exchange("/api/v1/cart/" + code, HttpMethod.PUT,
				new HttpEntity<>(item(second.getSku(), 1), jsonHeaders()), String.class);
		assertEquals(HttpStatus.CREATED, added.getStatusCode());
		assertEquals(2, json(added).get("quantity").asInt());

		ResponseEntity<String> updated = testRestTemplate.exchange("/api/v1/cart/" + code, HttpMethod.PUT,
				new HttpEntity<>(item(second.getSku(), 3), jsonHeaders()), String.class);
		assertEquals(HttpStatus.CREATED, updated.getStatusCode());
		assertEquals(4, json(updated).get("quantity").asInt());

		ResponseEntity<String> read = get("/api/v1/cart/" + code, jsonHeaders());
		assertEquals(HttpStatus.OK, read.getStatusCode());
		assertEquals(code, json(read).get("code").asText());
		assertEquals(4, json(read).get("quantity").asInt());
		assertEquals(2, json(read).get("products").size());
	}

	@Test
	public void authenticatedCustomerCheckoutAndOrderReadBack() {
		IntegrationModuleConfiguration moneyorder = new IntegrationModuleConfiguration();
		moneyorder.setCode("moneyorder");
		moneyorder.setActive(true);
		moneyorder.setDefaultSelected(true);
		moneyorder.getIntegrationKeys().put("address", "1 UAT street");
		assertEquals(HttpStatus.OK, testRestTemplate.exchange("/api/v1/private/modules/payment", HttpMethod.POST,
				new HttpEntity<>(moneyorder, getHeader()), String.class).getStatusCode());

		String token = registerAndLogin("uat.buyer@test.com", "clear123");
		ReadableProduct product = sampleProduct("uatcheckout");
		ResponseEntity<String> cart = testRestTemplate.postForEntity("/api/v1/cart/",
				new HttpEntity<>(item(product.getSku(), 1), jsonHeaders()), String.class);
		assertEquals(HttpStatus.CREATED, cart.getStatusCode());
		String cartCode = json(cart).get("code").asText();
		String total = json(cart).get("total").asText();

		PersistablePayment payment = new PersistablePayment();
		payment.setPaymentModule("moneyorder");
		payment.setPaymentType("MONEYORDER");
		payment.setTransactionType("CAPTURE");
		payment.setAmount(total);
		PersistableOrder order = new PersistableOrder();
		order.setPayment(payment);
		order.setCurrency("CAD");
		order.setCustomerAgreement(true);

		ResponseEntity<String> confirmation = testRestTemplate.exchange("/api/v1/auth/cart/" + cartCode + "/checkout",
				HttpMethod.POST, new HttpEntity<>(order, bearer(token)), String.class);
		assertEquals(HttpStatus.OK, confirmation.getStatusCode(), confirmation.getBody());
		long orderId = json(confirmation).get("id").asLong();
		assertTrue(orderId > 0);

		ResponseEntity<String> readBack = get("/api/v1/auth/orders/" + orderId, bearer(token));
		assertEquals(HttpStatus.OK, readBack.getStatusCode(), readBack.getBody());
		assertEquals(orderId, json(readBack).get("id").asLong());

		ResponseEntity<String> mine = get("/api/v1/auth/orders", bearer(token));
		assertEquals(HttpStatus.OK, mine.getStatusCode());
		assertTrue(mine.getBody().contains(String.valueOf(orderId)));
	}

	@Test
	public void servicesApiRequiresValidBasicAuth() {
		// anonymous requests are redirected to the form login of the services chain
		ResponseEntity<String> anonymous = get("/services/private/system/optin/x/customer", jsonHeaders());
		assertEquals(HttpStatus.FOUND, anonymous.getStatusCode());

		ResponseEntity<String> bad = testRestTemplate.withBasicAuth("admin@shopizer.com", "wrong")
				.exchange("/services/private/system/optin/x/customer", HttpMethod.GET, new HttpEntity<>(jsonHeaders()), String.class);
		assertEquals(HttpStatus.UNAUTHORIZED, bad.getStatusCode());

		ResponseEntity<String> good = testRestTemplate.withBasicAuth("admin@shopizer.com", "password")
				.exchange("/services/private/system/optin/x/customer", HttpMethod.GET, new HttpEntity<>(jsonHeaders()), String.class);
		assertNotEquals(HttpStatus.UNAUTHORIZED, good.getStatusCode());
		assertNotEquals(HttpStatus.FORBIDDEN, good.getStatusCode());
		assertFalse(good.getStatusCode().is3xxRedirection());
	}

	@Test
	public void actuatorHealthAndApiDocs() {
		ResponseEntity<String> health = get("/actuator/health", jsonHeaders());
		assertEquals(HttpStatus.OK, health.getStatusCode());
		assertEquals("UP", json(health).get("status").asText());

		ResponseEntity<String> docs = get("/v2/api-docs", jsonHeaders());
		assertEquals(HttpStatus.OK, docs.getStatusCode());
		assertEquals("2.0", json(docs).get("swagger").asText());
		assertTrue(json(docs).get("paths").has("/api/v1/private/store/{code}"));
	}

	@Test
	public void retailerAdminManagesChildStoreButChildAdminCannotReachParent() {
		HttpHeaders superadmin = getHeader();
		PersistableMerchantStore retailer = store("uatretail", null);
		retailer.setRetailer(true);
		assertEquals(HttpStatus.OK, send("/api/v1/private/store/", HttpMethod.POST, retailer, superadmin).getStatusCode());
		assertEquals(HttpStatus.OK, send("/api/v1/private/store/", HttpMethod.POST, store("uatbranch", "uatretail"), superadmin).getStatusCode());
		createAdmin("uatretail", "boss@uatretail.com", superadmin);
		createAdmin("uatbranch", "clerk@uatbranch.com", superadmin);

		HttpHeaders boss = getHeader("boss@uatretail.com", "Password1");
		HttpHeaders clerk = getHeader("clerk@uatbranch.com", "Password1");

		ResponseEntity<String> children = send("/api/v1/private/merchant/uatretail/children", HttpMethod.GET, null, boss);
		assertEquals(HttpStatus.OK, children.getStatusCode());
		assertTrue(children.getBody().contains("uatbranch"));
		assertEquals(HttpStatus.OK, send("/api/v1/private/customers?store=uatbranch", HttpMethod.GET, null, boss).getStatusCode());
		assertEquals(HttpStatus.OK, send("/api/v1/private/store/uatbranch", HttpMethod.PUT, store("uatbranch", "uatretail"), clerk).getStatusCode());

		assertEquals(HttpStatus.UNAUTHORIZED, send("/api/v1/private/customers?store=uatretail", HttpMethod.GET, null, clerk).getStatusCode());
		assertEquals(HttpStatus.UNAUTHORIZED, send("/api/v1/private/store/uatbranch", HttpMethod.PUT, store("uatbranch", Constants.DEFAULT_STORE), clerk).getStatusCode());
		assertEquals(HttpStatus.UNAUTHORIZED, send("/api/v1/private/customers?store=" + Constants.DEFAULT_STORE, HttpMethod.GET, null, clerk).getStatusCode());
	}

	private String registerAndLogin(String email, String password) {
		PersistableCustomer customer = new PersistableCustomer();
		customer.setEmailAddress(email);
		customer.setPassword(password);
		customer.setGender("M");
		customer.setLanguage("en");
		Address billing = new Address();
		billing.setFirstName("Uat");
		billing.setLastName("Customer");
		billing.setAddress("1 UAT street");
		billing.setCity("Toronto");
		billing.setPostalCode("M5V2T6");
		billing.setStateProvince("ON");
		billing.setCountry("CA");
		customer.setBilling(billing);
		customer.setDelivery(billing);
		customer.setStoreCode(Constants.DEFAULT_STORE);
		ResponseEntity<String> registered = testRestTemplate.postForEntity("/api/v1/customer/register",
				new HttpEntity<>(customer, jsonHeaders()), String.class);
		assertEquals(HttpStatus.OK, registered.getStatusCode(), registered.getBody());

		ResponseEntity<String> login = testRestTemplate.postForEntity("/api/v1/customer/login",
				new HttpEntity<>(new AuthenticationRequest(email, password)), String.class);
		assertEquals(HttpStatus.OK, login.getStatusCode());
		String token = json(login).get("token").asText();
		assertFalse(token.isEmpty());
		return token;
	}

	private void createAdmin(String storeCode, String userName, HttpHeaders superadmin) {
		PersistableUser user = new PersistableUser();
		user.setDefaultLanguage("en");
		user.setEmailAddress(userName);
		user.setFirstName("Uat");
		user.setLastName("Admin");
		user.setUserName(userName);
		user.setPassword("Password1");
		user.setRepeatPassword("Password1");
		user.setActive(true);
		PersistableGroup group = new PersistableGroup();
		group.setName("ADMIN");
		user.getGroups().add(group);
		ResponseEntity<String> response = send("/api/v1/private/user/?store=" + storeCode, HttpMethod.POST, user, superadmin);
		assertEquals(HttpStatus.OK, response.getStatusCode(), response.getBody());
	}

	private PersistableMerchantStore store(String code, String retailerStore) {
		PersistableAddress address = new PersistableAddress();
		address.setAddress("1 UAT street");
		address.setPostalCode("12345");
		address.setCountry("US");
		address.setCity("FT LD");
		address.setStateProvince("FL");
		PersistableMerchantStore store = new PersistableMerchantStore();
		store.setCode(code);
		store.setCurrency("CAD");
		store.setDefaultLanguage("en");
		store.setEmail(code + "@test.com");
		store.setName(code);
		store.setPhone("444-555-6666");
		store.setSupportedLanguages(Arrays.asList("en"));
		store.setAddress(address);
		store.setRetailerStore(retailerStore);
		return store;
	}

	private PersistableShoppingCartItem item(String sku, int quantity) {
		PersistableShoppingCartItem item = new PersistableShoppingCartItem();
		item.setProduct(sku);
		item.setQuantity(quantity);
		return item;
	}

	private ResponseEntity<String> get(String url, HttpHeaders headers) {
		return testRestTemplate.exchange(url, HttpMethod.GET, new HttpEntity<>(headers), String.class);
	}

	private ResponseEntity<String> send(String url, HttpMethod method, Object body, HttpHeaders headers) {
		return testRestTemplate.exchange(url, method, new HttpEntity<>(body, headers), String.class);
	}

	private HttpHeaders jsonHeaders() {
		HttpHeaders headers = new HttpHeaders();
		headers.setContentType(MediaType.APPLICATION_JSON);
		return headers;
	}

	private HttpHeaders bearer(String token) {
		HttpHeaders headers = jsonHeaders();
		headers.add("Authorization", "Bearer " + token);
		return headers;
	}

	private JsonNode json(ResponseEntity<String> response) {
		try {
			return mapper.readTree(response.getBody());
		} catch (IOException e) {
			throw new IllegalStateException("Not JSON: " + response.getBody(), e);
		}
	}
}
