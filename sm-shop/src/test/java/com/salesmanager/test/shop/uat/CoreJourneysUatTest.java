package com.salesmanager.test.shop.uat;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;
import static org.springframework.http.HttpMethod.GET;
import static org.springframework.http.HttpMethod.POST;
import static org.springframework.http.HttpMethod.PUT;
import static org.springframework.http.HttpStatus.CREATED;
import static org.springframework.http.HttpStatus.OK;
import static org.springframework.http.HttpStatus.UNAUTHORIZED;

import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.SpringBootTest.WebEnvironment;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.test.context.junit4.SpringRunner;

import com.salesmanager.core.business.constants.Constants;
import com.salesmanager.core.model.customer.CustomerGender;
import com.salesmanager.shop.application.ShopApplication;
import com.salesmanager.shop.model.catalog.product.ReadableProduct;
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
public class CoreJourneysUatTest extends ServicesTestSupport {

	private static final String STORE = Constants.DEFAULT_STORE;

	@Test
	public void adminAndCustomerAuthJourneys() {
		ResponseEntity<AuthenticationResponse> adminLogin = testRestTemplate.postForEntity("/api/v1/private/login",
				new AuthenticationRequest("admin@shopizer.com", "password"), AuthenticationResponse.class);
		assertEquals(OK, adminLogin.getStatusCode());
		HttpHeaders adminHeaders = bearerHeaders(adminLogin.getBody().getToken());

		assertEquals(OK, exchange("/api/v1/private/customers", GET, adminHeaders, String.class).getStatusCode());
		assertEquals(UNAUTHORIZED,
				exchange("/api/v1/private/customers", GET, new HttpHeaders(), String.class).getStatusCode());
		ResponseEntity<String> badToken = exchange("/api/v1/private/customers", GET,
				bearerHeaders("garbage"), String.class);
		System.out.println("UAT_BAD_BEARER_STATUS=" + badToken.getStatusCodeValue());
		assertTrue(badToken.getStatusCode().is4xxClientError() || badToken.getStatusCode().is5xxServerError());

		String email = "core-uat-" + UUID.randomUUID() + "@test.com";
		registerCustomer(email, "clear123");
		ResponseEntity<AuthenticationResponse> customerLogin = testRestTemplate.postForEntity("/api/v1/customer/login",
				new AuthenticationRequest(email, "clear123"), AuthenticationResponse.class);
		assertEquals(OK, customerLogin.getStatusCode());
		ResponseEntity<ReadableCustomer> profile = exchange("/api/v1/auth/customer/profile", GET,
				bearerHeaders(customerLogin.getBody().getToken()), ReadableCustomer.class);
		assertEquals(OK, profile.getStatusCode());
		assertEquals(email, profile.getBody().getEmailAddress());
	}

	@Test
	public void browseProductAndCartJourneys() throws Exception {
		assertEquals(OK, testRestTemplate.getForEntity("/api/v1/store/" + STORE, ReadableMerchantStore.class)
				.getStatusCode());
		assertEquals(OK, testRestTemplate.getForEntity("/api/v1/category?store=" + STORE, String.class)
				.getStatusCode());

		String sku = "core-uat-" + UUID.randomUUID();
		ReadableProduct createdProduct = sampleProduct(sku);
		ResponseEntity<String> productList = testRestTemplate.getForEntity(
				"/api/v1/products?store=" + STORE, String.class);
		assertEquals(OK, productList.getStatusCode());
		assertTrue(productList.getBody().contains(sku));
		assertEquals(OK, testRestTemplate.getForEntity("/api/v2/product/" + sku, ReadableProduct.class)
				.getStatusCode());

		PersistableShoppingCartItem item = new PersistableShoppingCartItem();
		item.setProduct(createdProduct.getSku());
		item.setQuantity(1);
		ResponseEntity<ReadableShoppingCart> createdCart = testRestTemplate.postForEntity("/api/v1/cart", item,
				ReadableShoppingCart.class);
		assertEquals(CREATED, createdCart.getStatusCode());
		assertNotNull(createdCart.getBody().getCode());

		item.setQuantity(2);
		ResponseEntity<ReadableShoppingCart> updatedCart = testRestTemplate.exchange(
				"/api/v1/cart/" + createdCart.getBody().getCode(), PUT, new HttpEntity<>(item),
				ReadableShoppingCart.class);
		assertEquals(CREATED, updatedCart.getStatusCode());
		ResponseEntity<ReadableShoppingCart> fetchedCart = testRestTemplate.getForEntity(
				"/api/v1/cart/" + createdCart.getBody().getCode(), ReadableShoppingCart.class);
		assertEquals(OK, fetchedCart.getStatusCode());
		assertEquals(2, fetchedCart.getBody().getQuantity());

	}

	@Test
	public void servicesActuatorAndApiDocsJourneys() {
		HttpHeaders validBasic = basicHeaders("admin@shopizer.com", "password");
		HttpHeaders invalidBasic = basicHeaders("admin@shopizer.com", "bad-password");
		ResponseEntity<String> validServices = testRestTemplate.exchange("/services/private/system/optin", POST,
				new HttpEntity<>("{\"uat\":\"true\"}", validBasic), String.class);
		ResponseEntity<String> invalidServices = testRestTemplate.exchange("/services/private/system/optin", POST,
				new HttpEntity<>("{\"uat\":\"true\"}", invalidBasic), String.class);
		System.out.println("UAT_SERVICES_BASIC_STATUS=" + validServices.getStatusCodeValue() + ","
				+ invalidServices.getStatusCodeValue());
		assertTrue(validServices.getStatusCode().is2xxSuccessful());
		assertEquals(UNAUTHORIZED, invalidServices.getStatusCode());

		ResponseEntity<Map> health = testRestTemplate.getForEntity("/actuator/health", Map.class);
		assertEquals(OK, health.getStatusCode());
		assertEquals("UP", health.getBody().get("status"));

		ResponseEntity<String> docs = testRestTemplate.getForEntity("/v2/api-docs", String.class);
		assertEquals(OK, docs.getStatusCode());
		assertTrue(docs.getBody().contains("/user/{store}/reset/{token}"));
		assertTrue(docs.getBody().contains("/customer/{store}/reset/{token}"));
	}

	private void registerCustomer(String email, String password) {
		PersistableCustomer customer = new PersistableCustomer();
		customer.setEmailAddress(email);
		customer.setPassword(password);
		customer.setRepeatPassword(password);
		customer.setGender(CustomerGender.M.name());
		customer.setLanguage("en");
		customer.setStoreCode(STORE);
		Address billing = new Address();
		billing.setFirstName("Core");
		billing.setLastName("UAT");
		billing.setCountry("BE");
		customer.setBilling(billing);
		assertTrue(testRestTemplate.postForEntity("/api/v1/customer/register", customer, String.class)
				.getStatusCode().is2xxSuccessful());
	}

	private HttpHeaders bearerHeaders(String token) {
		HttpHeaders headers = new HttpHeaders();
		headers.setContentType(MediaType.APPLICATION_JSON);
		headers.setBearerAuth(token);
		return headers;
	}

	private HttpHeaders basicHeaders(String username, String password) {
		HttpHeaders headers = new HttpHeaders();
		String credentials = username + ":" + password;
		headers.set("Authorization", "Basic " + Base64.getEncoder()
				.encodeToString(credentials.getBytes(StandardCharsets.UTF_8)));
		headers.setContentType(MediaType.TEXT_PLAIN);
		return headers;
	}

	private <T> ResponseEntity<T> exchange(String path, org.springframework.http.HttpMethod method, HttpHeaders headers,
			Class<T> responseType) {
		return testRestTemplate.exchange(path, method, new HttpEntity<>(headers), responseType);
	}
}
