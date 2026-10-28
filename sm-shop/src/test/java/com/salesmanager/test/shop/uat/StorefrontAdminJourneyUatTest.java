package com.salesmanager.test.shop.uat;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import java.util.Map;
import java.util.UUID;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.SpringBootTest.WebEnvironment;
import org.springframework.boot.test.web.client.TestRestTemplate;
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
import com.salesmanager.shop.model.catalog.product.ReadableProductList;
import com.salesmanager.shop.model.customer.PersistableCustomer;
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

	private static final String STORE = Constants.DEFAULT_STORE;

	@Test
	public void adminCustomerStoreCartCheckoutAndServiceJourney() throws Exception {
		ResponseEntity<AuthenticationResponse> adminLogin = testRestTemplate.postForEntity("/api/v1/private/login",
				new HttpEntity<AuthenticationRequest>(new AuthenticationRequest("admin@shopizer.com", "password")),
				AuthenticationResponse.class);
		assertEquals(HttpStatus.OK, adminLogin.getStatusCode());
		assertNotNull(adminLogin.getBody().getToken());

		HttpHeaders adminHeaders = bearerHeaders(adminLogin.getBody().getToken());
		ResponseEntity<String> privateWithToken = testRestTemplate.exchange("/api/v1/private/stores?store=" + STORE,
				HttpMethod.GET, new HttpEntity<String>(null, adminHeaders), String.class);
		assertEquals(HttpStatus.OK, privateWithToken.getStatusCode());

		ResponseEntity<String> privateWithoutToken = testRestTemplate.exchange("/api/v1/private/stores?store=" + STORE,
				HttpMethod.GET, new HttpEntity<String>(null, new HttpHeaders()), String.class);
		assertUnauthorized(privateWithoutToken.getStatusCode());

		HttpHeaders badTokenHeaders = bearerHeaders("not-a-valid-token");
		ResponseEntity<String> privateWithBadToken = testRestTemplate.exchange(
				"/api/v1/private/stores?store=" + STORE, HttpMethod.GET,
				new HttpEntity<String>(null, badTokenHeaders), String.class);
		assertEquals(HttpStatus.INTERNAL_SERVER_ERROR, privateWithBadToken.getStatusCode());

		String customerEmail = "journey-" + UUID.randomUUID().toString() + "@example.com";
		PersistableCustomer customer = customer(customerEmail);
		ResponseEntity<AuthenticationResponse> registration = testRestTemplate.postForEntity(
				"/api/v1/customer/register?store=" + STORE, new HttpEntity<PersistableCustomer>(customer),
				AuthenticationResponse.class);
		assertEquals(HttpStatus.OK, registration.getStatusCode());

		ResponseEntity<AuthenticationResponse> customerLogin = testRestTemplate.postForEntity(
				"/api/v1/customer/login", new HttpEntity<AuthenticationRequest>(
						new AuthenticationRequest(customerEmail, customer.getPassword())),
				AuthenticationResponse.class);
		assertEquals(HttpStatus.OK, customerLogin.getStatusCode());
		assertNotNull(customerLogin.getBody().getToken());
		HttpHeaders customerHeaders = bearerHeaders(customerLogin.getBody().getToken());

		ResponseEntity<String> authenticatedCustomer =
				testRestTemplate.exchange("/api/v1/auth/customer/profile?store=" + STORE, HttpMethod.GET,
						new HttpEntity<String>(customerHeaders), String.class);
		assertEquals(HttpStatus.OK, authenticatedCustomer.getStatusCode());

		ResponseEntity<ReadableMerchantStore> store = testRestTemplate.getForEntity(
				"/api/v1/store/" + STORE + "?lang=en", ReadableMerchantStore.class);
		assertEquals(HttpStatus.OK, store.getStatusCode());
		ResponseEntity<String> categories = testRestTemplate.getForEntity(
				"/api/v1/category?store=" + STORE + "&lang=en", String.class);
		assertEquals(HttpStatus.OK, categories.getStatusCode());

		ResponseEntity<ReadableProductList> productsResponse = testRestTemplate.getForEntity(
				"/api/v2/products?store=" + STORE + "&lang=en", ReadableProductList.class);
		assertEquals(HttpStatus.OK, productsResponse.getStatusCode());
		ReadableProduct product = productsResponse.getBody().getProducts().isEmpty()
				? sampleProduct("uat-" + UUID.randomUUID().toString())
				: productsResponse.getBody().getProducts().get(0);
		assertNotNull(product);
		ResponseEntity<ReadableProduct> productDetails = testRestTemplate.getForEntity(
				"/api/v2/product/" + product.getSku() + "?store=" + STORE + "&lang=en", ReadableProduct.class);
		assertEquals(HttpStatus.OK, productDetails.getStatusCode());

		PersistableShoppingCartItem cartItem = new PersistableShoppingCartItem();
		cartItem.setProduct(product.getSku());
		cartItem.setQuantity(1);
		ResponseEntity<ReadableShoppingCart> newCart = testRestTemplate.postForEntity(
				"/api/v1/cart?store=" + STORE, new HttpEntity<PersistableShoppingCartItem>(cartItem),
				ReadableShoppingCart.class);
		assertEquals(HttpStatus.CREATED, newCart.getStatusCode());
		assertNotNull(newCart.getBody().getCode());

		cartItem.setQuantity(2);
		ResponseEntity<ReadableShoppingCart> updatedCart = testRestTemplate.exchange(
				"/api/v1/cart/" + newCart.getBody().getCode() + "?store=" + STORE, HttpMethod.PUT,
				new HttpEntity<PersistableShoppingCartItem>(cartItem), ReadableShoppingCart.class);
		assertEquals(HttpStatus.CREATED, updatedCart.getStatusCode());
		ResponseEntity<ReadableShoppingCart> readCart = testRestTemplate.getForEntity(
				"/api/v1/cart/" + newCart.getBody().getCode() + "?store=" + STORE, ReadableShoppingCart.class);
		assertEquals(HttpStatus.OK, readCart.getStatusCode());
		assertEquals(2, readCart.getBody().getQuantity());

		HttpEntity<String> optin = new HttpEntity<String>("{}", basicHeaders("admin@shopizer.com", "password"));
		assertEquals(HttpStatus.OK, testRestTemplate.exchange("/services/private/system/optin",
				HttpMethod.POST, optin, String.class).getStatusCode());
		HttpEntity<String> invalidOptin = new HttpEntity<String>("{}",
				basicHeaders("admin@shopizer.com", "wrong-password"));
		assertEquals(HttpStatus.UNAUTHORIZED, testRestTemplate.exchange("/services/private/system/optin",
				HttpMethod.POST, invalidOptin, String.class).getStatusCode());

		ResponseEntity<Map> health = testRestTemplate.getForEntity("/actuator/health", Map.class);
		assertEquals(HttpStatus.OK, health.getStatusCode());
		assertEquals("UP", health.getBody().get("status"));
		assertEquals(HttpStatus.OK, testRestTemplate.getForEntity("/v2/api-docs", String.class).getStatusCode());
	}

	private PersistableCustomer customer(String email) {
		PersistableCustomer customer = new PersistableCustomer();
		customer.setEmailAddress(email);
		customer.setPassword("clear123");
		customer.setRepeatPassword("clear123");
		customer.setGender(CustomerGender.M.name());
		customer.setLanguage("en");
		customer.setStoreCode(STORE);
		Address billing = new Address();
		billing.setFirstName("Journey");
		billing.setLastName("Customer");
		billing.setCountry("BE");
		customer.setBilling(billing);
		return customer;
	}

	private HttpHeaders bearerHeaders(String token) {
		HttpHeaders headers = new HttpHeaders();
		headers.setContentType(MediaType.APPLICATION_JSON);
		headers.setBearerAuth(token);
		return headers;
	}

	private HttpHeaders basicHeaders(String username, String password) {
		HttpHeaders headers = new HttpHeaders();
		headers.setContentType(new MediaType(MediaType.TEXT_PLAIN, java.nio.charset.StandardCharsets.UTF_8));
		headers.setBasicAuth(username, password);
		return headers;
	}

	private void assertUnauthorized(HttpStatus status) {
		assertTrue(status == HttpStatus.UNAUTHORIZED || status == HttpStatus.FORBIDDEN);
	}
}
