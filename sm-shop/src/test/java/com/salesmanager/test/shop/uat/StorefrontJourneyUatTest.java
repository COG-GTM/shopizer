package com.salesmanager.test.shop.uat;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import java.util.UUID;

import javax.inject.Inject;

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

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.salesmanager.core.model.customer.CustomerGender;
import com.salesmanager.shop.application.ShopApplication;
import com.salesmanager.shop.model.catalog.product.ReadableProduct;
import com.salesmanager.shop.model.customer.PersistableCustomer;
import com.salesmanager.shop.model.customer.ReadableCustomer;
import com.salesmanager.shop.model.customer.address.Address;
import com.salesmanager.shop.model.shoppingcart.PersistableShoppingCartItem;
import com.salesmanager.shop.model.shoppingcart.ReadableShoppingCart;
import com.salesmanager.shop.store.security.AuthenticationRequest;
import com.salesmanager.shop.store.security.AuthenticationResponse;
import com.salesmanager.test.shop.common.ServicesTestSupport;

@SpringBootTest(classes = ShopApplication.class, webEnvironment = WebEnvironment.RANDOM_PORT)
@RunWith(SpringRunner.class)
public class StorefrontJourneyUatTest extends ServicesTestSupport {

	@Inject
	private TestRestTemplate testRestTemplate;

	@Test
	public void adminLoginAndProtectedUsersList() {
		ResponseEntity<AuthenticationResponse> login = adminLogin();
		assertEquals(HttpStatus.OK, login.getStatusCode());
		assertNotNull(login.getBody());
		HttpHeaders headers = bearer(login.getBody().getToken());

		ResponseEntity<String> users = testRestTemplate.exchange("/api/v1/private/users?store=DEFAULT",
				HttpMethod.GET, new HttpEntity<>(headers), String.class);
		assertEquals(HttpStatus.OK, users.getStatusCode());

		ResponseEntity<String> anonymous = testRestTemplate.getForEntity("/api/v1/private/users?store=DEFAULT",
				String.class);
		assertEquals(HttpStatus.UNAUTHORIZED, anonymous.getStatusCode());

		HttpHeaders invalidHeaders = bearer("not.a.valid.token");
		ResponseEntity<String> invalid = testRestTemplate.exchange("/api/v1/private/users?store=DEFAULT",
				HttpMethod.GET, new HttpEntity<>(invalidHeaders), String.class);
		assertEquals(HttpStatus.INTERNAL_SERVER_ERROR, invalid.getStatusCode());
	}

	@Test
	public void customerRegistrationLoginAndProfile() {
		String email = "u-" + UUID.randomUUID() + "@shopizer.com";
		String password = "Customer9";
		AuthenticationResponse registration = registerCustomer(email, password);
		assertNotNull(registration.getToken());

		ResponseEntity<AuthenticationResponse> login = testRestTemplate.postForEntity("/api/v1/customer/login",
				new HttpEntity<>(new AuthenticationRequest(email, password)), AuthenticationResponse.class);
		assertEquals(HttpStatus.OK, login.getStatusCode());
		assertNotNull(login.getBody());

		ResponseEntity<ReadableCustomer> profile = testRestTemplate.exchange("/api/v1/auth/customer/profile",
				HttpMethod.GET, new HttpEntity<>(bearer(login.getBody().getToken())), ReadableCustomer.class);
		assertEquals(HttpStatus.OK, profile.getStatusCode());
		assertEquals(email, profile.getBody().getEmailAddress());

		ResponseEntity<String> anonymous = testRestTemplate.getForEntity("/api/v1/auth/customer/profile",
				String.class);
		assertEquals(HttpStatus.UNAUTHORIZED, anonymous.getStatusCode());
	}

	@Test
	public void browseCategoriesAndProductListingAndDetail() throws Exception {
		String sku = "uat-" + UUID.randomUUID().toString().replace("-", "").substring(0, 12);
		ReadableProduct product = sampleProduct(sku);

		ResponseEntity<String> store = testRestTemplate.getForEntity("/api/v1/store/DEFAULT", String.class);
		assertEquals(HttpStatus.OK, store.getStatusCode());

		ResponseEntity<String> categories = testRestTemplate.getForEntity("/api/v1/category?store=DEFAULT",
				String.class);
		assertEquals(HttpStatus.OK, categories.getStatusCode());

		ResponseEntity<String> products = testRestTemplate.getForEntity("/api/v1/products?store=DEFAULT",
				String.class);
		assertEquals(HttpStatus.OK, products.getStatusCode());
		assertTrue(products.getBody().contains(sku));

		ResponseEntity<ReadableProduct> detail = testRestTemplate.exchange(
				"/api/v2/product/" + sku, HttpMethod.GET,
				new HttpEntity<>(getHeader()), ReadableProduct.class);
		assertEquals(HttpStatus.OK, detail.getStatusCode());
		assertEquals(sku, detail.getBody().getSku());
		assertNotNull(product);
	}

	@Test
	public void cartItemQuantityCanBeUpdatedAndReadBack() throws Exception {
		String sku = "uat-cart-" + UUID.randomUUID().toString().replace("-", "").substring(0, 10);
		sampleProduct(sku);
		PersistableShoppingCartItem item = new PersistableShoppingCartItem();
		item.setProduct(sku);
		item.setQuantity(1);

		ResponseEntity<ReadableShoppingCart> created = testRestTemplate.postForEntity("/api/v1/cart",
				new HttpEntity<>(item, getHeader()), ReadableShoppingCart.class);
		assertEquals(HttpStatus.CREATED, created.getStatusCode());
		assertNotNull(created.getBody());

		item.setQuantity(2);
		ResponseEntity<ReadableShoppingCart> updated = testRestTemplate.exchange(
				"/api/v1/cart/" + created.getBody().getCode(), HttpMethod.PUT,
				new HttpEntity<>(item, getHeader()), ReadableShoppingCart.class);
		assertEquals(HttpStatus.CREATED, updated.getStatusCode());

		ResponseEntity<ReadableShoppingCart> readBack = testRestTemplate.exchange(
				"/api/v1/cart/" + created.getBody().getCode(), HttpMethod.GET,
				new HttpEntity<>(getHeader()), ReadableShoppingCart.class);
		assertEquals(HttpStatus.OK, readBack.getStatusCode());
		assertEquals(2, readBack.getBody().getProducts().get(0).getQuantity());
	}

	@Test
	public void legacyOptinRequiresValidBasicAuthentication() {
		HttpHeaders validHeaders = new HttpHeaders();
		validHeaders.setBasicAuth("admin@shopizer.com", "password");
		validHeaders.setContentType(MediaType.APPLICATION_JSON);
		ResponseEntity<String> valid = testRestTemplate.exchange("/services/private/system/optin",
				HttpMethod.POST, new HttpEntity<>("{}", validHeaders), String.class);
		assertTrue(valid.getStatusCode() != HttpStatus.UNAUTHORIZED);

		HttpHeaders invalidHeaders = new HttpHeaders();
		invalidHeaders.setBasicAuth("admin@shopizer.com", "wrong-password");
		invalidHeaders.setContentType(MediaType.APPLICATION_JSON);
		ResponseEntity<String> invalid = testRestTemplate.exchange("/services/private/system/optin",
				HttpMethod.POST, new HttpEntity<>("{}", invalidHeaders), String.class);
		assertEquals(HttpStatus.UNAUTHORIZED, invalid.getStatusCode());
	}

	@Test
	public void healthAndApiDocumentationAreAvailable() throws Exception {
		ResponseEntity<String> health = testRestTemplate.getForEntity("/actuator/health", String.class);
		assertEquals(HttpStatus.OK, health.getStatusCode());
		JsonNode healthJson = new ObjectMapper().readTree(health.getBody());
		assertEquals("UP", healthJson.get("status").asText());

		ResponseEntity<String> apiDocs = testRestTemplate.getForEntity("/v2/api-docs", String.class);
		assertEquals(HttpStatus.OK, apiDocs.getStatusCode());
	}

	private ResponseEntity<AuthenticationResponse> adminLogin() {
		return testRestTemplate.postForEntity("/api/v1/private/login",
				new HttpEntity<>(new AuthenticationRequest("admin@shopizer.com", "password")),
				AuthenticationResponse.class);
	}

	private AuthenticationResponse registerCustomer(String email, String password) {
		PersistableCustomer customer = new PersistableCustomer();
		customer.setEmailAddress(email);
		customer.setPassword(password);
		customer.setGender(CustomerGender.M.name());
		customer.setLanguage("en");
		customer.setStoreCode("DEFAULT");
		Address billing = new Address();
		billing.setFirstName("UAT");
		billing.setLastName("Customer");
		billing.setCountry("US");
		customer.setBilling(billing);

		ResponseEntity<AuthenticationResponse> response = testRestTemplate.postForEntity(
				"/api/v1/customer/register", new HttpEntity<>(customer), AuthenticationResponse.class);
		assertEquals(HttpStatus.OK, response.getStatusCode());
		assertNotNull(response.getBody());
		return response.getBody();
	}

	private HttpHeaders bearer(String token) {
		HttpHeaders headers = new HttpHeaders();
		headers.setContentType(MediaType.APPLICATION_JSON);
		headers.setBearerAuth(token);
		return headers;
	}
}
