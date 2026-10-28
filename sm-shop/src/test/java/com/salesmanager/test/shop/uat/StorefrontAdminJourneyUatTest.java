package com.salesmanager.test.shop.uat;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;
import static org.springframework.http.HttpStatus.CREATED;
import static org.springframework.http.HttpStatus.OK;
import static org.springframework.http.HttpStatus.UNAUTHORIZED;

import java.nio.charset.StandardCharsets;
import java.util.Base64;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.SpringBootTest.WebEnvironment;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.ResponseEntity;
import org.springframework.test.context.junit4.SpringRunner;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.salesmanager.core.business.constants.Constants;
import com.salesmanager.core.model.customer.CustomerGender;
import com.salesmanager.shop.application.ShopApplication;
import com.salesmanager.shop.model.catalog.category.ReadableCategoryList;
import com.salesmanager.shop.model.catalog.product.ReadableProduct;
import com.salesmanager.shop.model.catalog.product.ReadableProductList;
import com.salesmanager.shop.model.customer.PersistableCustomer;
import com.salesmanager.shop.model.customer.ReadableCustomer;
import com.salesmanager.shop.model.customer.address.Address;
import com.salesmanager.shop.model.shoppingcart.PersistableShoppingCartItem;
import com.salesmanager.shop.model.shoppingcart.ReadableShoppingCart;
import com.salesmanager.shop.model.store.ReadableMerchantStore;
import com.salesmanager.shop.store.security.AuthenticationRequest;
import com.salesmanager.shop.store.security.AuthenticationResponse;
import com.salesmanager.test.shop.common.ServicesTestSupport;

@SpringBootTest(classes = ShopApplication.class, webEnvironment = WebEnvironment.RANDOM_PORT)
@RunWith(SpringRunner.class)
public class StorefrontAdminJourneyUatTest extends ServicesTestSupport {

	private final ObjectMapper objectMapper = new ObjectMapper();

	@Test
	public void adminCanAuthenticateAndAccessPrivateUsers() {
		ResponseEntity<AuthenticationResponse> login = testRestTemplate.postForEntity("/api/v1/private/login",
				new HttpEntity<>(new AuthenticationRequest("admin@shopizer.com", "password")),
				AuthenticationResponse.class);
		assertEquals(OK, login.getStatusCode());
		assertNotNull(login.getBody().getToken());
		HttpHeaders admin = bearer(login.getBody().getToken());

		ResponseEntity<String> authenticated = testRestTemplate.exchange("/api/v1/private/users/1",
				HttpMethod.GET, new HttpEntity<>(admin), String.class);
		assertEquals(OK, authenticated.getStatusCode());
		ResponseEntity<String> anonymous = testRestTemplate.getForEntity("/api/v1/private/users/1", String.class);
		assertEquals(UNAUTHORIZED, anonymous.getStatusCode());
	}

	@Test
	public void customerCanRegisterLoginAndReadProfile() {
		String email = "uat-customer-" + System.nanoTime() + "@test.com";
		registerCustomer(email);
		ResponseEntity<AuthenticationResponse> login = testRestTemplate.postForEntity("/api/v1/customer/login",
				new HttpEntity<>(new AuthenticationRequest(email, "Password1")), AuthenticationResponse.class);
		assertEquals(OK, login.getStatusCode());

		ResponseEntity<ReadableCustomer> profile = testRestTemplate.exchange("/api/v1/auth/customer/profile",
				HttpMethod.GET, new HttpEntity<>(bearer(login.getBody().getToken())), ReadableCustomer.class);
		assertEquals(OK, profile.getStatusCode());
		assertEquals(email, profile.getBody().getEmailAddress());
	}

	@Test
	public void storefrontCanBeBrowsedAndProductCanBeAddedToCart() throws Exception {
		ResponseEntity<ReadableMerchantStore> store = testRestTemplate.getForEntity(
				"/api/v1/store/DEFAULT", ReadableMerchantStore.class);
		assertEquals(OK, store.getStatusCode());
		assertEquals("DEFAULT", store.getBody().getCode());
		ResponseEntity<ReadableCategoryList> categories = testRestTemplate.getForEntity(
				"/api/v1/category?store=DEFAULT", ReadableCategoryList.class);
		assertEquals(OK, categories.getStatusCode());

		String sku = "uat-" + System.nanoTime();
		ReadableProduct product = sampleProduct(sku);
		ResponseEntity<ReadableProductList> products = testRestTemplate.getForEntity(
				"/api/v1/products?store=DEFAULT", ReadableProductList.class);
		assertEquals(OK, products.getStatusCode());
		assertTrue(products.getBody().getProducts().stream().anyMatch(item -> sku.equals(item.getSku())));
		ResponseEntity<String> details = testRestTemplate.getForEntity(
				"/api/v2/product/" + sku, String.class);
		assertEquals(OK, details.getStatusCode());

		String secondSku = "uat-" + System.nanoTime();
		sampleProduct(secondSku);
		PersistableShoppingCartItem firstItem = cartItem(sku);
		ResponseEntity<ReadableShoppingCart> created = testRestTemplate.postForEntity("/api/v1/cart/",
				new HttpEntity<>(firstItem, getHeader()), ReadableShoppingCart.class);
		assertEquals(CREATED, created.getStatusCode());
		assertEquals(1, created.getBody().getQuantity());

		ResponseEntity<ReadableShoppingCart> updated = testRestTemplate.exchange(
				"/api/v1/cart/" + created.getBody().getCode(), HttpMethod.PUT,
				new HttpEntity<>(cartItem(secondSku), getHeader()), ReadableShoppingCart.class);
		assertEquals(CREATED, updated.getStatusCode());
		assertEquals(2, updated.getBody().getQuantity());
		ResponseEntity<ReadableShoppingCart> fetched = testRestTemplate.getForEntity(
				"/api/v1/cart/" + created.getBody().getCode(), ReadableShoppingCart.class);
		assertEquals(OK, fetched.getStatusCode());
		assertEquals(2, fetched.getBody().getQuantity());
	}

	@Test
	public void privateServicesRequireValidBasicCredentials() {
		HttpHeaders badCredentials = basic("not-admin", "bad-password");
		HttpHeaders adminCredentials = basic("admin@shopizer.com", "password");

		ResponseEntity<String> bad = testRestTemplate.exchange("/services/private/anything",
				HttpMethod.GET, new HttpEntity<>(badCredentials), String.class);
		ResponseEntity<String> good = testRestTemplate.exchange("/services/private/anything",
				HttpMethod.GET, new HttpEntity<>(adminCredentials), String.class);

		assertEquals(UNAUTHORIZED, bad.getStatusCode());
		assertFalse(UNAUTHORIZED.equals(good.getStatusCode()));
	}

	@Test
	public void healthAndSwaggerDescribeTheCacheApiContract() {
		ResponseEntity<JsonNode> health = testRestTemplate.getForEntity("/actuator/health", JsonNode.class);
		assertEquals(OK, health.getStatusCode());
		assertEquals("UP", health.getBody().path("status").asText());

		ResponseEntity<String> apiDocs = testRestTemplate.getForEntity("/v2/api-docs", String.class);
		assertEquals(OK, apiDocs.getStatusCode());
		assertTrue(apiDocs.getBody().contains("/api/v1/private/cache/store/{storeId}/clear"));
		assertFalse(apiDocs.getBody().contains("/api/v1/auth/cache/store/{storeId}/clear"));
	}

	@Test
	public void clearingStoreCacheAllowsReferenceDataToBeLoadedAgain() throws Exception {
		ResponseEntity<String> cleared = testRestTemplate.exchange(
				"/api/v1/private/cache/store/DEFAULT/clear", HttpMethod.DELETE,
				new HttpEntity<>(getHeader()), String.class);
		assertEquals(OK, cleared.getStatusCode());

		ResponseEntity<String> countries = testRestTemplate.getForEntity(
				"/api/v1/country?store=DEFAULT&lang=en", String.class);
		assertEquals(OK, countries.getStatusCode());
		assertTrue(containsNonEmptyArray(objectMapper.readTree(countries.getBody())));
		ResponseEntity<String> categories = testRestTemplate.getForEntity(
				"/api/v1/category?store=DEFAULT", String.class);
		assertEquals(OK, categories.getStatusCode());
		assertTrue(containsNonEmptyArray(objectMapper.readTree(categories.getBody())));
	}

	private void registerCustomer(String email) {
		PersistableCustomer customer = new PersistableCustomer();
		customer.setEmailAddress(email);
		customer.setPassword("Password1");
		customer.setGender(CustomerGender.M.name());
		customer.setLanguage("en");
		customer.setStoreCode(Constants.DEFAULT_STORE);
		Address billing = new Address();
		billing.setFirstName("UAT");
		billing.setLastName("Customer");
		billing.setCountry("US");
		customer.setBilling(billing);
		ResponseEntity<PersistableCustomer> response = testRestTemplate.postForEntity(
				"/api/v1/customer/register", new HttpEntity<>(customer, getHeader()), PersistableCustomer.class);
		assertEquals(OK, response.getStatusCode());
	}

	private static PersistableShoppingCartItem cartItem(String sku) {
		PersistableShoppingCartItem item = new PersistableShoppingCartItem();
		item.setProduct(sku);
		item.setQuantity(1);
		return item;
	}

	private static HttpHeaders bearer(String token) {
		HttpHeaders headers = new HttpHeaders();
		headers.setBearerAuth(token);
		return headers;
	}

	private static HttpHeaders basic(String username, String password) {
		HttpHeaders headers = new HttpHeaders();
		String credentials = username + ":" + password;
		headers.set("Authorization", "Basic " + Base64.getEncoder().encodeToString(
				credentials.getBytes(StandardCharsets.UTF_8)));
		return headers;
	}

	private static boolean containsNonEmptyArray(JsonNode node) {
		if (node.isArray()) {
			if (!node.isEmpty()) {
				return true;
			}
		}
		if (node.isContainerNode()) {
			for (JsonNode child : node) {
				if (containsNonEmptyArray(child)) {
					return true;
				}
			}
		}
		return false;
	}
}
