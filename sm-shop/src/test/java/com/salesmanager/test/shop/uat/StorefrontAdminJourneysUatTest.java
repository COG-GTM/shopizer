package com.salesmanager.test.shop.uat;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

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

import com.salesmanager.core.business.constants.Constants;
import com.salesmanager.core.model.customer.CustomerGender;
import com.salesmanager.shop.application.ShopApplication;
import com.salesmanager.shop.model.catalog.product.ReadableProduct;
import com.salesmanager.shop.model.customer.PersistableCustomer;
import com.salesmanager.shop.model.customer.address.Address;
import com.salesmanager.shop.model.security.PersistableGroup;
import com.salesmanager.shop.model.shoppingcart.PersistableShoppingCartItem;
import com.salesmanager.shop.model.shoppingcart.ReadableShoppingCart;
import com.salesmanager.shop.model.user.PersistableUser;
import com.salesmanager.shop.model.user.ReadableUser;
import com.salesmanager.shop.store.security.AuthenticationRequest;
import com.salesmanager.shop.store.security.AuthenticationResponse;
import com.salesmanager.test.shop.common.ServicesTestSupport;

/**
 * Black-box journeys used by the Angular admin and React shop frontends, including the
 * disabled-admin journey changed by PR #166.
 */
@SpringBootTest(classes = ShopApplication.class, webEnvironment = WebEnvironment.RANDOM_PORT)
@RunWith(SpringRunner.class)
public class StorefrontAdminJourneysUatTest extends ServicesTestSupport {

	private static final String PASSWORD = "Password123";

	@Test
	public void adminLoginThenAdminOnlyCallAndRejectionWithoutToken() {
		ResponseEntity<AuthenticationResponse> login = testRestTemplate.postForEntity("/api/v1/private/login",
				new HttpEntity<>(new AuthenticationRequest("admin@shopizer.com", "password")), AuthenticationResponse.class);
		assertEquals(HttpStatus.OK, login.getStatusCode());
		assertNotNull(login.getBody().getToken());

		ResponseEntity<String> users = testRestTemplate.exchange("/api/v1/private/users?count=5", HttpMethod.GET,
				new HttpEntity<>(bearer(login.getBody().getToken())), String.class);
		assertEquals(HttpStatus.OK, users.getStatusCode());
		assertTrue(users.getBody(), users.getBody().contains("admin@shopizer.com"));

		ResponseEntity<String> anonymous = testRestTemplate.exchange("/api/v1/private/users?count=5", HttpMethod.GET,
				new HttpEntity<>(json()), String.class);
		assertEquals(HttpStatus.UNAUTHORIZED, anonymous.getStatusCode());
	}

	@Test
	public void adminDisablesColleagueWhoLosesAccessUntilReenabled() {
		String username = "uat.colleague." + System.nanoTime() + "@shopizer.com";
		PersistableUser user = new PersistableUser();
		user.setDefaultLanguage("en");
		user.setEmailAddress(username);
		user.setUserName(username);
		user.setFirstName("Uat");
		user.setLastName("Colleague");
		user.setPassword(PASSWORD);
		user.setRepeatPassword(PASSWORD);
		user.setStore(Constants.DEFAULT_STORE);
		user.setActive(true);
		PersistableGroup group = new PersistableGroup();
		group.setName("ADMIN");
		user.setGroups(Collections.singletonList(group));
		ResponseEntity<ReadableUser> created = testRestTemplate.postForEntity("/api/v1/private/user/",
				new HttpEntity<>(user, getHeader()), ReadableUser.class);
		assertEquals(HttpStatus.OK, created.getStatusCode());
		Long id = created.getBody().getId();

		String colleagueToken = adminLogin(username).getBody().getToken();
		assertEquals(HttpStatus.OK, adminProfile(colleagueToken).getStatusCode());

		assertEquals(HttpStatus.OK, setEnabled(id, false).getStatusCode());
		assertEquals(HttpStatus.UNAUTHORIZED, adminProfile(colleagueToken).getStatusCode());
		assertEquals(HttpStatus.UNAUTHORIZED, adminLogin(username).getStatusCode());
		assertEquals(HttpStatus.UNAUTHORIZED, testRestTemplate.exchange("/api/v1/auth/refresh", HttpMethod.GET,
				new HttpEntity<>(bearer(colleagueToken)), String.class).getStatusCode());

		assertEquals(HttpStatus.OK, setEnabled(id, true).getStatusCode());
		assertEquals(HttpStatus.OK, adminLogin(username).getStatusCode());
	}

	@Test
	public void customerRegistersLogsInAndReadsProfile() {
		String email = "uat.customer." + System.nanoTime() + "@test.com";
		String token = registerAndLogin(email);

		ResponseEntity<String> profile = testRestTemplate.exchange("/api/v1/auth/customer/profile", HttpMethod.GET,
				new HttpEntity<>(bearer(token)), String.class);
		assertEquals(HttpStatus.OK, profile.getStatusCode());
		assertTrue(profile.getBody(), profile.getBody().contains(email));

		ResponseEntity<String> badLogin = testRestTemplate.postForEntity("/api/v1/customer/login",
				new HttpEntity<>(new AuthenticationRequest(email, "wrong")), String.class);
		assertEquals(HttpStatus.UNAUTHORIZED, badLogin.getStatusCode());
	}

	@Test
	public void shopperBrowsesStoreCategoriesAndProducts() {
		ReadableProduct product = sampleProduct("uatBrowse");

		assertEquals(HttpStatus.OK, get("/api/v1/store/DEFAULT").getStatusCode());
		assertEquals(HttpStatus.OK, get("/api/v1/category?store=DEFAULT&lang=en").getStatusCode());
		ResponseEntity<String> products = get("/api/v1/products?store=DEFAULT&lang=en");
		assertEquals(HttpStatus.OK, products.getStatusCode());
		assertTrue(products.getBody(), products.getBody().contains(product.getSku()));
		ResponseEntity<String> detail = get("/api/v2/product/" + product.getSku() + "?store=DEFAULT&lang=en");
		assertEquals(HttpStatus.OK, detail.getStatusCode());
		assertTrue(detail.getBody(), detail.getBody().contains(product.getSku()));
	}

	@Test
	public void shopperCreatesCartUpdatesItAndReadsItBack() {
		ReadableProduct product = sampleProduct("uatCart");
		ReadableShoppingCart cart = addToCart(null, product.getSku(), 1);
		assertNotNull(cart.getCode());

		ReadableShoppingCart updated = addToCart(cart.getCode(), product.getSku(), 3);
		assertEquals(3, updated.getQuantity());

		ResponseEntity<ReadableShoppingCart> readBack = testRestTemplate.exchange("/api/v1/cart/" + cart.getCode(),
				HttpMethod.GET, new HttpEntity<>(json()), ReadableShoppingCart.class);
		assertEquals(HttpStatus.OK, readBack.getStatusCode());
		assertEquals(cart.getCode(), readBack.getBody().getCode());
		assertEquals(3, readBack.getBody().getQuantity());
	}

	@Test
	public void servicesBasicAuthAcceptsValidAndRejectsInvalidCredentials() {
		ResponseEntity<String> valid = testRestTemplate.withBasicAuth("admin@shopizer.com", "password")
				.getForEntity("/services/private/uat-unknown", String.class);
		ResponseEntity<String> invalid = testRestTemplate.withBasicAuth("admin@shopizer.com", "wrong")
				.getForEntity("/services/private/uat-unknown", String.class);

		assertEquals(HttpStatus.NOT_FOUND, valid.getStatusCode());
		assertEquals(HttpStatus.UNAUTHORIZED, invalid.getStatusCode());
	}

	@Test
	public void actuatorHealthIsUpAndApiDocsRespond() {
		ResponseEntity<String> health = get("/actuator/health");
		assertEquals(HttpStatus.OK, health.getStatusCode());
		assertTrue(health.getBody(), health.getBody().contains("\"status\":\"UP\""));

		ResponseEntity<String> docs = get("/v2/api-docs");
		assertEquals(HttpStatus.OK, docs.getStatusCode());
		assertTrue(docs.getBody().contains("/api/v1/private/login"));
	}

	private String registerAndLogin(String email) {
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
		assertEquals(HttpStatus.OK, testRestTemplate.postForEntity("/api/v1/customer/register",
				new HttpEntity<>(customer, json()), String.class).getStatusCode());

		ResponseEntity<AuthenticationResponse> login = testRestTemplate.postForEntity("/api/v1/customer/login",
				new HttpEntity<>(new AuthenticationRequest(email, "clear123")), AuthenticationResponse.class);
		assertEquals(HttpStatus.OK, login.getStatusCode());
		return login.getBody().getToken();
	}

	private ReadableShoppingCart addToCart(String cartCode, String sku, int quantity) {
		PersistableShoppingCartItem item = new PersistableShoppingCartItem();
		item.setProduct(sku);
		item.setQuantity(quantity);
		ResponseEntity<ReadableShoppingCart> response = cartCode == null
				? testRestTemplate.postForEntity("/api/v1/cart/", new HttpEntity<>(item, json()), ReadableShoppingCart.class)
				: testRestTemplate.exchange("/api/v1/cart/" + cartCode, HttpMethod.PUT, new HttpEntity<>(item, json()),
						ReadableShoppingCart.class);
		assertEquals(HttpStatus.CREATED, response.getStatusCode());
		return response.getBody();
	}

	private ResponseEntity<AuthenticationResponse> adminLogin(String username) {
		return testRestTemplate.postForEntity("/api/v1/private/login",
				new HttpEntity<>(new AuthenticationRequest(username, PASSWORD)), AuthenticationResponse.class);
	}

	private ResponseEntity<String> adminProfile(String token) {
		return testRestTemplate.exchange("/api/v1/private/user/profile", HttpMethod.GET, new HttpEntity<>(bearer(token)),
				String.class);
	}

	private ResponseEntity<Void> setEnabled(Long id, boolean active) {
		PersistableUser user = new PersistableUser();
		user.setActive(active);
		return testRestTemplate.exchange("/api/v1/private/user/" + id + "/enabled", HttpMethod.PATCH,
				new HttpEntity<>(user, getHeader()), Void.class);
	}

	private ResponseEntity<String> get(String path) {
		return testRestTemplate.exchange(path, HttpMethod.GET, new HttpEntity<>(json()), String.class);
	}

	private static HttpHeaders json() {
		HttpHeaders headers = new HttpHeaders();
		headers.setContentType(MediaType.APPLICATION_JSON);
		return headers;
	}

	private static HttpHeaders bearer(String token) {
		HttpHeaders headers = json();
		headers.add("Authorization", "Bearer " + token);
		return headers;
	}
}
