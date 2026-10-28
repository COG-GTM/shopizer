package com.salesmanager.test.shop.uat;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;
import static org.springframework.http.HttpStatus.CREATED;
import static org.springframework.http.HttpStatus.ACCEPTED;
import static org.springframework.http.HttpStatus.OK;
import static org.springframework.http.HttpStatus.UNAUTHORIZED;

import java.util.Arrays;
import java.util.UUID;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.SpringBootTest.WebEnvironment;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.test.context.junit4.SpringRunner;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.salesmanager.core.business.constants.Constants;
import com.salesmanager.core.model.customer.CustomerGender;
import com.salesmanager.shop.application.ShopApplication;
import com.salesmanager.shop.model.catalog.product.ReadableProduct;
import com.salesmanager.shop.model.catalog.product.ReadableProductList;
import com.salesmanager.shop.model.catalog.product.product.PersistableProduct;
import com.salesmanager.shop.model.customer.PersistableCustomer;
import com.salesmanager.shop.model.customer.ReadableCustomer;
import com.salesmanager.shop.model.customer.address.Address;
import com.salesmanager.shop.model.entity.Entity;
import com.salesmanager.shop.model.shoppingcart.PersistableShoppingCartItem;
import com.salesmanager.shop.model.shoppingcart.ReadableShoppingCart;
import com.salesmanager.shop.model.store.ReadableMerchantStore;
import com.salesmanager.shop.store.security.AuthenticationRequest;
import com.salesmanager.shop.store.security.AuthenticationResponse;
import com.salesmanager.test.shop.common.ServicesTestSupport;

@SpringBootTest(classes = ShopApplication.class, webEnvironment = WebEnvironment.RANDOM_PORT)
@RunWith(SpringRunner.class)
public class StorefrontAndAdminJourneyUatTest extends ServicesTestSupport {

	@Test
	public void adminLoginThenPrivateCall() {
		ResponseEntity<AuthenticationResponse> login = testRestTemplate.postForEntity(
				"/api/v1/private/login",
				new HttpEntity<>(new AuthenticationRequest("admin@shopizer.com", "password")),
				AuthenticationResponse.class);
		assertEquals(OK, login.getStatusCode());
		assertNotNull(login.getBody().getToken());

		HttpHeaders authenticated = new HttpHeaders();
		authenticated.setBearerAuth(login.getBody().getToken());
		assertEquals(OK, testRestTemplate.exchange(
				"/api/v1/private/customers?store=" + Constants.DEFAULT_STORE,
				HttpMethod.GET, new HttpEntity<>(authenticated), String.class).getStatusCode());
		assertEquals(UNAUTHORIZED, testRestTemplate.getForEntity(
				"/api/v1/private/customers?store=" + Constants.DEFAULT_STORE, String.class).getStatusCode());

		HttpHeaders invalidToken = new HttpHeaders();
		invalidToken.setBearerAuth("not-a-jwt");
		ResponseEntity<String> badToken = testRestTemplate.exchange(
				"/api/v1/private/customers?store=" + Constants.DEFAULT_STORE,
				HttpMethod.GET, new HttpEntity<>(invalidToken), String.class);
		assertRejected(badToken);
	}

	@Test
	public void customerRegisterLoginAndProfile() {
		CustomerSession customer = registerCustomer();
		ResponseEntity<ReadableCustomer> profile = testRestTemplate.exchange(
				"/api/v1/auth/customer/profile?store=" + Constants.DEFAULT_STORE,
				HttpMethod.GET, new HttpEntity<>(customer.headers), ReadableCustomer.class);

		assertEquals(OK, profile.getStatusCode());
		assertEquals(customer.email, profile.getBody().getEmailAddress());
	}

	@Test
	public void browseStoreCategoriesProducts() {
		ResponseEntity<ReadableMerchantStore> store = testRestTemplate.getForEntity(
				"/api/v1/store/" + Constants.DEFAULT_STORE, ReadableMerchantStore.class);
		ResponseEntity<String> categories = testRestTemplate.getForEntity(
				"/api/v1/category?store=" + Constants.DEFAULT_STORE, String.class);
		ReadableProduct sample = sampleProduct(uniqueCode("browse"));
		ResponseEntity<ReadableProductList> products = testRestTemplate.getForEntity(
				"/api/v1/products?store=" + Constants.DEFAULT_STORE, ReadableProductList.class);
		ResponseEntity<ReadableProduct> product = testRestTemplate.getForEntity(
				"/api/v2/product/" + sample.getSku() + "?store=" + Constants.DEFAULT_STORE,
				ReadableProduct.class);

		assertEquals(OK, store.getStatusCode());
		assertEquals(Constants.DEFAULT_STORE, store.getBody().getCode());
		assertEquals(OK, categories.getStatusCode());
		assertEquals(OK, products.getStatusCode());
		assertTrue(products.getBody().getProducts().stream()
				.anyMatch(item -> sample.getSku().equals(item.getSku())));
		assertEquals(OK, product.getStatusCode());
		assertEquals(sample.getSku(), product.getBody().getSku());
	}

	@Test
	public void cartCreateUpdateRead() {
		ReadableProduct product = sampleProduct(uniqueCode("cartjourney"));
		PersistableShoppingCartItem item = cartItem(product.getSku(), 1);
		ResponseEntity<ReadableShoppingCart> created = testRestTemplate.postForEntity(
				"/api/v1/cart?store=" + Constants.DEFAULT_STORE,
				new HttpEntity<>(item, jsonHeaders()), ReadableShoppingCart.class);
		assertEquals(CREATED, created.getStatusCode());
		assertNotNull(created.getBody().getCode());

		ResponseEntity<ReadableShoppingCart> updated = testRestTemplate.exchange(
				"/api/v1/cart/" + created.getBody().getCode() + "?store=" + Constants.DEFAULT_STORE,
				HttpMethod.PUT,
				new HttpEntity<>(cartItem(product.getSku(), 3), jsonHeaders()),
				ReadableShoppingCart.class);
		assertEquals(CREATED, updated.getStatusCode());
		assertEquals(3, updated.getBody().getQuantity());

		ResponseEntity<ReadableShoppingCart> read = testRestTemplate.getForEntity(
				"/api/v1/cart/" + created.getBody().getCode() + "?store=" + Constants.DEFAULT_STORE,
				ReadableShoppingCart.class);
		assertEquals(OK, read.getStatusCode());
		assertEquals(3, read.getBody().getQuantity());
	}

	@Test
	public void servicesBasicAuth() {
		ResponseEntity<String> publicService = testRestTemplate.getForEntity(
				"/services/public/DEFAULT", String.class);
		assertEquals(ACCEPTED, publicService.getStatusCode());

		HttpHeaders invalidCredentials = new HttpHeaders();
		invalidCredentials.setBasicAuth("admin@shopizer.com", "wrong-password");
		ResponseEntity<String> denied = testRestTemplate.exchange(
				"/services/private/system/module", HttpMethod.GET,
				new HttpEntity<>(invalidCredentials), String.class);
		assertEquals(UNAUTHORIZED, denied.getStatusCode());

		HttpHeaders credentials = new HttpHeaders();
		credentials.setBasicAuth("admin@shopizer.com", "password");
		ResponseEntity<String> allowed = testRestTemplate.exchange(
				"/services/private/system/module", HttpMethod.GET,
				new HttpEntity<>(credentials), String.class);
		assertFalse("valid service credentials returned " + allowed.getStatusCode(),
				allowed.getStatusCode() == UNAUTHORIZED);
	}

	@Test
	public void actuatorAndApiDocs() throws Exception {
		ResponseEntity<String> health = testRestTemplate.getForEntity("/actuator/health", String.class);
		assertEquals(OK, health.getStatusCode());
		JsonNode healthJson = new ObjectMapper().readTree(health.getBody());
		assertEquals("UP", healthJson.path("status").asText());

		ResponseEntity<String> docs = testRestTemplate.getForEntity("/v2/api-docs", String.class);
		assertEquals(OK, docs.getStatusCode());
		JsonNode docsJson = new ObjectMapper().readTree(docs.getBody());
		assertTrue(docsJson.path("paths").has("/api/v1/private/product"));
		assertFalse(docsJson.path("paths").has("/api/v1/auth/products"));
	}

	@Test
	public void shopperCannotWriteCatalogButAdminCan() {
		CustomerSession shopper = registerCustomer();
		String sku = uniqueCode("shopperwrite");
		ResponseEntity<String> shopperPost = testRestTemplate.postForEntity(
				"/api/v1/auth/products?store=" + Constants.DEFAULT_STORE,
				new HttpEntity<>(writableProduct(sku), shopper.headers), String.class);
		assertFalse("shopper POST returned " + shopperPost.getStatusCode(),
				shopperPost.getStatusCode().is2xxSuccessful());

		ResponseEntity<Entity> adminPost = testRestTemplate.postForEntity(
				"/api/v1/private/product?store=" + Constants.DEFAULT_STORE,
				new HttpEntity<>(writableProduct(sku), getHeader()), Entity.class);
		assertEquals(CREATED, adminPost.getStatusCode());
		ResponseEntity<ReadableProduct> saved = testRestTemplate.getForEntity(
				"/api/v2/product/" + sku + "?store=" + Constants.DEFAULT_STORE, ReadableProduct.class);
		assertEquals(OK, saved.getStatusCode());
		assertEquals(sku, saved.getBody().getSku());
	}

	private CustomerSession registerCustomer() {
		String email = uniqueCode("shopper") + "@test.com";
		PersistableCustomer customer = new PersistableCustomer();
		customer.setEmailAddress(email);
		customer.setPassword("clear123");
		customer.setGender(CustomerGender.F.name());
		customer.setLanguage("en");
		customer.setStoreCode(Constants.DEFAULT_STORE);
		Address billing = new Address();
		billing.setFirstName("Mallory");
		billing.setLastName("Shopper");
		billing.setCountry("BE");
		customer.setBilling(billing);
		ResponseEntity<String> registration = testRestTemplate.postForEntity(
				"/api/v1/customer/register",
				new HttpEntity<>(customer, jsonHeaders()), String.class);
		assertTrue("registration returned " + registration.getStatusCode(),
				registration.getStatusCode().is2xxSuccessful());

		ResponseEntity<AuthenticationResponse> login = testRestTemplate.postForEntity(
				"/api/v1/customer/login",
				new HttpEntity<>(new AuthenticationRequest(email, "clear123")),
				AuthenticationResponse.class);
		assertEquals(OK, login.getStatusCode());
		HttpHeaders headers = jsonHeaders();
		headers.setBearerAuth(login.getBody().getToken());
		return new CustomerSession(email, headers);
	}

	private PersistableProduct writableProduct(String sku) {
		PersistableProduct product = product(sku);
		com.salesmanager.shop.model.catalog.product.product.ProductSpecification specification
				= new com.salesmanager.shop.model.catalog.product.product.ProductSpecification();
		specification.setManufacturer(
				com.salesmanager.core.model.catalog.product.manufacturer.Manufacturer.DEFAULT_MANUFACTURER);
		product.setProductSpecifications(specification);
		product.setAvailable(true);
		product.setSku(sku);
		product.setQuantity(100);
		product.setPrice(java.math.BigDecimal.TEN);
		return product;
	}

	private static PersistableShoppingCartItem cartItem(String sku, int quantity) {
		PersistableShoppingCartItem item = new PersistableShoppingCartItem();
		item.setProduct(sku);
		item.setQuantity(quantity);
		return item;
	}

	private static HttpHeaders jsonHeaders() {
		HttpHeaders headers = new HttpHeaders();
		headers.setContentType(MediaType.APPLICATION_JSON);
		headers.setAccept(Arrays.asList(MediaType.APPLICATION_JSON));
		return headers;
	}

	private static String uniqueCode(String prefix) {
		return prefix + UUID.randomUUID().toString().replace("-", "").substring(0, 12);
	}

	private static void assertRejected(ResponseEntity<?> response) {
		assertFalse("unexpected successful status " + response.getStatusCode(),
				response.getStatusCode().is2xxSuccessful());
	}

	private static final class CustomerSession {
		private final String email;
		private final HttpHeaders headers;

		private CustomerSession(String email, HttpHeaders headers) {
			this.email = email;
			this.headers = headers;
		}
	}
}
