package com.salesmanager.test.shop.uat;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import java.nio.charset.StandardCharsets;
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
import com.salesmanager.shop.application.ShopApplication;
import com.salesmanager.shop.model.catalog.product.ReadableProduct;
import com.salesmanager.shop.model.customer.PersistableCustomer;
import com.salesmanager.shop.model.customer.address.Address;
import com.salesmanager.shop.model.shoppingcart.PersistableShoppingCartItem;
import com.salesmanager.shop.store.security.AuthenticationRequest;
import com.salesmanager.shop.store.security.AuthenticationResponse;
import com.salesmanager.test.shop.common.ServicesTestSupport;

/**
 * Black-box storefront / admin journeys the Angular admin and React shop rely on.
 */
@SpringBootTest(classes = ShopApplication.class, webEnvironment = WebEnvironment.RANDOM_PORT)
@RunWith(SpringRunner.class)
public class StorefrontAdminJourneyUatTest extends ServicesTestSupport {

	private final ObjectMapper mapper = new ObjectMapper();

	private HttpHeaders json() {
		HttpHeaders headers = new HttpHeaders();
		headers.setContentType(MediaType.APPLICATION_JSON);
		return headers;
	}

	private HttpHeaders bearer(String token) {
		HttpHeaders headers = json();
		headers.add("Authorization", "Bearer " + token);
		return headers;
	}

	private HttpHeaders basic(String user, String password) {
		HttpHeaders headers = json();
		headers.add("Authorization", "Basic "
				+ Base64.getEncoder().encodeToString((user + ":" + password).getBytes(StandardCharsets.UTF_8)));
		return headers;
	}

	private ResponseEntity<String> get(String url, HttpHeaders headers) {
		return testRestTemplate.exchange(url, HttpMethod.GET, new HttpEntity<>(headers), String.class);
	}

	@Test
	public void adminLoginThenPrivateApi() throws Exception {
		ResponseEntity<AuthenticationResponse> login = testRestTemplate.postForEntity("/api/v1/private/login",
				new HttpEntity<>(new AuthenticationRequest("admin@shopizer.com", "password")),
				AuthenticationResponse.class);
		assertEquals(HttpStatus.OK, login.getStatusCode());
		String token = login.getBody().getToken();
		assertNotNull(token);

		ResponseEntity<String> customers = get("/api/v1/private/customers", bearer(token));
		assertEquals(HttpStatus.OK, customers.getStatusCode());
		assertTrue(mapper.readTree(customers.getBody()).has("customers"));

		ResponseEntity<String> profile = get("/api/v1/private/user/profile", bearer(token));
		assertEquals(HttpStatus.OK, profile.getStatusCode());
		assertEquals("admin@shopizer.com", mapper.readTree(profile.getBody()).path("userName").asText());

		assertEquals(HttpStatus.UNAUTHORIZED, get("/api/v1/private/customers", json()).getStatusCode());
		// a malformed JWT currently yields 500 on base and head; only assert it is not accepted
		HttpStatus badToken = get("/api/v1/private/customers", bearer("not.a.jwt")).getStatusCode();
		assertFalse("bad token returned " + badToken, badToken.is2xxSuccessful());
	}

	@Test
	public void adminLoginWithWrongPasswordIsRejected() {
		ResponseEntity<String> login = testRestTemplate.postForEntity("/api/v1/private/login",
				new HttpEntity<>(new AuthenticationRequest("admin@shopizer.com", "wrong")), String.class);
		assertEquals(HttpStatus.UNAUTHORIZED, login.getStatusCode());
	}

	@Test
	public void customerRegisterLoginThenAuthApi() throws Exception {
		String email = "uat" + System.nanoTime() + "@test.com";
		PersistableCustomer customer = new PersistableCustomer();
		customer.setEmailAddress(email);
		customer.setPassword("clear123");
		customer.setGender(CustomerGender.F.name());
		customer.setLanguage("en");
		Address billing = new Address();
		billing.setFirstName("Uat");
		billing.setLastName("Customer");
		billing.setCountry("CA");
		customer.setBilling(billing);
		customer.setStoreCode(Constants.DEFAULT_STORE);

		ResponseEntity<String> register = testRestTemplate.postForEntity("/api/v1/customer/register",
				new HttpEntity<>(customer, json()), String.class);
		assertEquals(HttpStatus.OK, register.getStatusCode());
		assertNotNull(mapper.readTree(register.getBody()).path("token").asText(null));

		ResponseEntity<AuthenticationResponse> login = testRestTemplate.postForEntity("/api/v1/customer/login",
				new HttpEntity<>(new AuthenticationRequest(email, "clear123")), AuthenticationResponse.class);
		assertEquals(HttpStatus.OK, login.getStatusCode());
		String token = login.getBody().getToken();
		assertNotNull(token);

		ResponseEntity<String> profile = get("/api/v1/auth/customer/profile", bearer(token));
		assertEquals(HttpStatus.OK, profile.getStatusCode());
		assertEquals(email, mapper.readTree(profile.getBody()).path("emailAddress").asText());

		assertEquals(HttpStatus.UNAUTHORIZED, get("/api/v1/auth/customer/profile", json()).getStatusCode());
	}

	@Test
	public void browseStoreCategoriesAndProducts() throws Exception {
		ReadableProduct created = sampleProduct("uatBrowse");

		ResponseEntity<String> store = get("/api/v1/store/" + Constants.DEFAULT_STORE, json());
		assertEquals(HttpStatus.OK, store.getStatusCode());
		assertEquals(Constants.DEFAULT_STORE, mapper.readTree(store.getBody()).path("code").asText());

		ResponseEntity<String> categories = get("/api/v1/category?store=DEFAULT&lang=en", json());
		assertEquals(HttpStatus.OK, categories.getStatusCode());
		assertTrue(mapper.readTree(categories.getBody()).path("categories").isArray());

		ResponseEntity<String> products = get("/api/v1/products?store=DEFAULT&lang=en", json());
		assertEquals(HttpStatus.OK, products.getStatusCode());
		JsonNode list = mapper.readTree(products.getBody()).path("products");
		assertTrue(list.isArray());
		assertTrue(list.size() > 0);

		ResponseEntity<String> detail = get("/api/v2/product/" + created.getSku() + "?store=DEFAULT&lang=en", json());
		assertEquals(HttpStatus.OK, detail.getStatusCode());
		JsonNode product = mapper.readTree(detail.getBody());
		assertEquals(created.getId().longValue(), product.path("id").asLong());
		assertEquals("uatBrowse", product.path("sku").asText());
	}

	@Test
	public void cartCreateUpdateAndReadBack() throws Exception {
		ReadableProduct product = sampleProduct("uatCart");

		PersistableShoppingCartItem item = new PersistableShoppingCartItem();
		item.setProduct(product.getSku());
		item.setQuantity(1);
		ResponseEntity<String> created = testRestTemplate.postForEntity("/api/v1/cart",
				new HttpEntity<>(item, json()), String.class);
		assertEquals(HttpStatus.CREATED, created.getStatusCode());
		JsonNode cart = mapper.readTree(created.getBody());
		String code = cart.path("code").asText();
		assertFalse(code.isEmpty());
		assertEquals(1, cart.path("quantity").asInt());

		item.setQuantity(3);
		ResponseEntity<String> updated = testRestTemplate.exchange("/api/v1/cart/" + code, HttpMethod.PUT,
				new HttpEntity<>(item, json()), String.class);
		assertEquals(HttpStatus.CREATED, updated.getStatusCode());
		assertEquals(3, mapper.readTree(updated.getBody()).path("quantity").asInt());

		ResponseEntity<String> read = get("/api/v1/cart/" + code, json());
		assertEquals(HttpStatus.OK, read.getStatusCode());
		JsonNode readCart = mapper.readTree(read.getBody());
		assertEquals(code, readCart.path("code").asText());
		assertEquals(3, readCart.path("quantity").asInt());
		assertEquals(1, readCart.path("products").size());
	}

	@Test
	public void servicesApiBasicAuth() {
		assertEquals(HttpStatus.UNAUTHORIZED,
				get("/services/private/uat", basic("admin@shopizer.com", "wrong")).getStatusCode());
		assertFalse(get("/services/private/uat", json()).getStatusCode().is2xxSuccessful());
	}

	@Test
	public void healthUpAndApiDocsAvailable() throws Exception {
		ResponseEntity<String> health = get("/actuator/health", json());
		assertEquals(HttpStatus.OK, health.getStatusCode());
		assertEquals("UP", mapper.readTree(health.getBody()).path("status").asText());

		ResponseEntity<String> docs = get("/v2/api-docs", json());
		assertEquals(HttpStatus.OK, docs.getStatusCode());
		JsonNode api = mapper.readTree(docs.getBody());
		assertEquals("2.0", api.path("swagger").asText());
		assertTrue(api.path("paths").has("/api/v1/private/login"));
	}

}
