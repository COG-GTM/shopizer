package com.salesmanager.test.shop.uat;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

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

import com.fasterxml.jackson.databind.JsonNode;
import com.salesmanager.core.business.constants.Constants;
import com.salesmanager.core.model.customer.CustomerGender;
import com.salesmanager.shop.application.ShopApplication;
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

	@Test
	public void adminCanLoginAndAccessProtectedUserResource() throws Exception {
		ResponseEntity<AuthenticationResponse> login = testRestTemplate.postForEntity(
				"/api/v1/private/login",
				new HttpEntity<AuthenticationRequest>(
						new AuthenticationRequest("admin@shopizer.com", "password")),
				AuthenticationResponse.class);
		assertEquals(HttpStatus.OK, login.getStatusCode());
		assertNotNull(login.getBody());
		assertNotNull(login.getBody().getToken());

		HttpEntity<String> authenticated = new HttpEntity<String>(headers(login.getBody().getToken()));
		ResponseEntity<String> user = testRestTemplate.exchange(
				"/api/v1/private/users/1", HttpMethod.GET, authenticated, String.class);
		assertEquals(HttpStatus.OK, user.getStatusCode());

		ResponseEntity<String> anonymous = testRestTemplate.getForEntity(
				"/api/v1/private/users/1", String.class);
		assertEquals(HttpStatus.UNAUTHORIZED, anonymous.getStatusCode());

		HttpHeaders invalidTokenHeaders = headers("bad.token.value");
		ResponseEntity<String> invalidToken = testRestTemplate.exchange(
				"/api/v1/private/users/1", HttpMethod.GET,
				new HttpEntity<String>(invalidTokenHeaders), String.class);
		// Malformed tokens currently return 500 on both revisions; ensure no user data leaks.
		assertFalse(invalidToken.getStatusCode().is2xxSuccessful());
		assertNotNull(invalidToken.getBody());
		assertFalse(invalidToken.getBody().contains("userName"));
		assertFalse(invalidToken.getBody().contains("emailAddress"));
	}

	@Test
	public void customerCanRegisterLoginAndReadProfile() throws Exception {
		String email = "pr188.customer." + System.currentTimeMillis() + "@example.com";
		PersistableCustomer customer = createCustomer(email);
		ResponseEntity<PersistableCustomer> registration = testRestTemplate.postForEntity(
				"/api/v1/customer/register",
				new HttpEntity<PersistableCustomer>(customer, getHeader()),
				PersistableCustomer.class);
		assertTrue(registration.getStatusCode().is2xxSuccessful());

		ResponseEntity<AuthenticationResponse> login = testRestTemplate.postForEntity(
				"/api/v1/customer/login",
				new HttpEntity<AuthenticationRequest>(
						new AuthenticationRequest(email, customer.getPassword())),
				AuthenticationResponse.class);
		assertEquals(HttpStatus.OK, login.getStatusCode());
		assertNotNull(login.getBody());
		assertNotNull(login.getBody().getToken());

		ResponseEntity<ReadableCustomer> profile = testRestTemplate.exchange(
				"/api/v1/auth/customer/profile", HttpMethod.GET,
				new HttpEntity<String>(headers(login.getBody().getToken())), ReadableCustomer.class);
		assertEquals(HttpStatus.OK, profile.getStatusCode());
		assertNotNull(profile.getBody());
		assertEquals(email, profile.getBody().getEmailAddress());

		ResponseEntity<String> anonymous = testRestTemplate.getForEntity(
				"/api/v1/auth/customer/profile", String.class);
		assertTrue(Arrays.asList(HttpStatus.UNAUTHORIZED, HttpStatus.FORBIDDEN)
				.contains(anonymous.getStatusCode()));
	}

	@Test
	public void visitorCanBrowseStoreCategoriesProductsAndProductDetails() throws Exception {
		ResponseEntity<ReadableMerchantStore> store = testRestTemplate.getForEntity(
				"/api/v1/store/" + Constants.DEFAULT_STORE, ReadableMerchantStore.class);
		assertEquals(HttpStatus.OK, store.getStatusCode());
		assertNotNull(store.getBody());
		assertEquals(Constants.DEFAULT_STORE, store.getBody().getCode());

		ResponseEntity<String> categories = testRestTemplate.getForEntity(
				"/api/v1/category?store=" + Constants.DEFAULT_STORE, String.class);
		assertEquals(HttpStatus.OK, categories.getStatusCode());

		String sku = "pr188-browse-" + System.currentTimeMillis();
		ReadableProduct createdProduct = sampleProduct(sku);
		ResponseEntity<ReadableProductList> products = testRestTemplate.getForEntity(
				"/api/v1/products?store=" + Constants.DEFAULT_STORE + "&sku=" + sku,
				ReadableProductList.class);
		assertEquals(HttpStatus.OK, products.getStatusCode());
		assertNotNull(products.getBody());
		assertTrue(products.getBody().getProducts().stream()
				.anyMatch(product -> sku.equals(product.getSku())));

		ResponseEntity<ReadableProduct> details = testRestTemplate.getForEntity(
				"/api/v2/product/" + createdProduct.getSku() + "?store=" + Constants.DEFAULT_STORE,
				ReadableProduct.class);
		assertEquals(HttpStatus.OK, details.getStatusCode());
		assertNotNull(details.getBody());
		assertEquals(sku, details.getBody().getSku());
	}

	@Test
	public void visitorCanCreateUpdateAndReadCart() throws Exception {
		ReadableProduct firstProduct = sampleProduct("pr188-cart-a-" + System.currentTimeMillis());
		ReadableProduct secondProduct = sampleProduct("pr188-cart-b-" + System.currentTimeMillis());
		PersistableShoppingCartItem firstItem = cartItem(firstProduct.getSku());
		ResponseEntity<ReadableShoppingCart> created = testRestTemplate.postForEntity(
				"/api/v1/cart/", new HttpEntity<PersistableShoppingCartItem>(firstItem, getHeader()),
				ReadableShoppingCart.class);
		assertEquals(HttpStatus.CREATED, created.getStatusCode());
		assertNotNull(created.getBody());
		assertNotNull(created.getBody().getCode());

		ResponseEntity<ReadableShoppingCart> updated = testRestTemplate.exchange(
				"/api/v1/cart/" + created.getBody().getCode(), HttpMethod.PUT,
				new HttpEntity<PersistableShoppingCartItem>(cartItem(secondProduct.getSku()), getHeader()),
				ReadableShoppingCart.class);
		assertEquals(HttpStatus.CREATED, updated.getStatusCode());
		assertNotNull(updated.getBody());
		assertEquals(2, updated.getBody().getQuantity());

		ResponseEntity<ReadableShoppingCart> readback = testRestTemplate.getForEntity(
				"/api/v1/cart/" + created.getBody().getCode(), ReadableShoppingCart.class);
		assertEquals(HttpStatus.OK, readback.getStatusCode());
		assertNotNull(readback.getBody());
		assertEquals(2, readback.getBody().getQuantity());
	}

	@Test
	public void servicesRespectBasicAuthAndPublicServiceIsAvailable() {
		HttpHeaders headers = new HttpHeaders();
		headers.setContentType(MediaType.TEXT_PLAIN);
		HttpEntity<String> body = new HttpEntity<String>("{}", headers);
		ResponseEntity<String> authenticated = testRestTemplate.withBasicAuth(
				"admin@shopizer.com", "password").postForEntity(
						"/services/private/system/optin", body, String.class);
		assertTrue(authenticated.getStatusCode().is2xxSuccessful());

		ResponseEntity<String> invalidCredentials = testRestTemplate.withBasicAuth(
				"admin@shopizer.com", "wrong-password").postForEntity(
						"/services/private/system/optin", body, String.class);
		assertEquals(HttpStatus.UNAUTHORIZED, invalidCredentials.getStatusCode());

		ResponseEntity<String> publicService = testRestTemplate.getForEntity(
				"/services/public/" + Constants.DEFAULT_STORE, String.class);
		assertEquals(HttpStatus.ACCEPTED, publicService.getStatusCode());
	}

	@Test
	public void actuatorHealthAndSwaggerDocsAreAvailable() throws Exception {
		ResponseEntity<JsonNode> health = testRestTemplate.getForEntity(
				"/actuator/health", JsonNode.class);
		assertEquals(HttpStatus.OK, health.getStatusCode());
		assertNotNull(health.getBody());
		assertEquals("UP", health.getBody().path("status").asText());

		ResponseEntity<JsonNode> apiDocs = testRestTemplate.getForEntity(
				"/v2/api-docs", JsonNode.class);
		assertEquals(HttpStatus.OK, apiDocs.getStatusCode());
		assertNotNull(apiDocs.getBody());
		assertTrue(apiDocs.getBody().has("swagger"));
	}

	private PersistableCustomer createCustomer(String email) {
		PersistableCustomer customer = new PersistableCustomer();
		customer.setEmailAddress(email);
		customer.setUserName(email);
		customer.setPassword("clear123");
		customer.setRepeatPassword("clear123");
		customer.setGender(CustomerGender.M.name());
		customer.setLanguage("en");
		customer.setStoreCode(Constants.DEFAULT_STORE);
		customer.setFirstName("PR188");
		customer.setLastName("Customer");
		Address billing = new Address();
		billing.setFirstName("PR188");
		billing.setLastName("Customer");
		billing.setCountry("BE");
		billing.setAddress("1 Test Road");
		billing.setCity("Brussels");
		customer.setBilling(billing);
		return customer;
	}

	private PersistableShoppingCartItem cartItem(String sku) {
		PersistableShoppingCartItem item = new PersistableShoppingCartItem();
		item.setProduct(sku);
		item.setQuantity(1);
		return item;
	}

	private HttpHeaders headers(String token) {
		HttpHeaders headers = new HttpHeaders();
		headers.setContentType(MediaType.APPLICATION_JSON);
		headers.setAccept(Collections.singletonList(MediaType.APPLICATION_JSON));
		headers.set("Authorization", "Bearer " + token);
		return headers;
	}

}
