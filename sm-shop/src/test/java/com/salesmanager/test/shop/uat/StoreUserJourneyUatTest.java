package com.salesmanager.test.shop.uat;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.util.UUID;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.SpringBootTest.WebEnvironment;
import org.springframework.core.io.ClassPathResource;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.test.context.junit4.SpringRunner;
import org.springframework.util.LinkedMultiValueMap;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.salesmanager.core.business.constants.Constants;
import com.salesmanager.core.model.catalog.product.manufacturer.Manufacturer;
import com.salesmanager.core.model.customer.CustomerGender;
import com.salesmanager.shop.application.ShopApplication;
import com.salesmanager.shop.model.catalog.product.product.PersistableProduct;
import com.salesmanager.shop.model.catalog.product.product.ProductSpecification;
import com.salesmanager.shop.model.customer.PersistableCustomer;
import com.salesmanager.shop.model.customer.address.Address;
import com.salesmanager.shop.model.shoppingcart.PersistableShoppingCartItem;
import com.salesmanager.shop.store.security.AuthenticationRequest;
import com.salesmanager.test.shop.common.ServicesTestSupport;

/**
 * Black-box user journeys (admin, customer, catalogue browse, cart, services, actuator, API docs),
 * including the product image management journey changed by PR #137.
 */
@SpringBootTest(classes = ShopApplication.class, webEnvironment = WebEnvironment.RANDOM_PORT)
@RunWith(SpringRunner.class)
public class StoreUserJourneyUatTest extends ServicesTestSupport {

	private static final ObjectMapper JSON = new ObjectMapper();

	@Test
	public void adminLoginThenPrivateCallAndTokenRejections() throws Exception {
		ResponseEntity<String> login = testRestTemplate.postForEntity("/api/v1/private/login",
				new HttpEntity<>(new AuthenticationRequest("admin@shopizer.com", "password"), json()), String.class);
		assertEquals(HttpStatus.OK, login.getStatusCode());
		String token = JSON.readTree(login.getBody()).get("token").asText();
		assertFalse(token.isEmpty());

		HttpHeaders bearer = json();
		bearer.add("Authorization", "Bearer " + token);
		ResponseEntity<String> store = get("/api/v1/private/store/DEFAULT", bearer);
		assertEquals(HttpStatus.OK, store.getStatusCode());
		assertEquals("DEFAULT", JSON.readTree(store.getBody()).get("code").asText());

		assertEquals(HttpStatus.UNAUTHORIZED, get("/api/v1/private/store/DEFAULT", json()).getStatusCode());
		HttpHeaders bad = json();
		bad.add("Authorization", "Bearer not-a-valid-token");
		HttpStatus badStatus = get("/api/v1/private/store/DEFAULT", bad).getStatusCode();
		// base and head both answer a malformed bearer token with an error (500, not 401/403)
		assertFalse("bad token got " + badStatus, badStatus.is2xxSuccessful());

		ResponseEntity<String> wrongPassword = testRestTemplate.postForEntity("/api/v1/private/login",
				new HttpEntity<>(new AuthenticationRequest("admin@shopizer.com", "wrong"), json()), String.class);
		assertEquals(HttpStatus.UNAUTHORIZED, wrongPassword.getStatusCode());
	}

	@Test
	public void customerRegisterLoginThenAuthCall() throws Exception {
		String email = "uat" + UUID.randomUUID().toString().substring(0, 8) + "@test.com";
		HttpHeaders customer = registerAndLogin(email);

		ResponseEntity<String> profile = get("/api/v1/auth/customer/profile", customer);
		assertEquals(HttpStatus.OK, profile.getStatusCode());
		assertEquals(email, JSON.readTree(profile.getBody()).get("emailAddress").asText());

		assertEquals(HttpStatus.UNAUTHORIZED, get("/api/v1/auth/customer/profile", json()).getStatusCode());
	}

	@Test
	public void browseStoreCategoriesProductsAndDetail() throws Exception {
		String sku = sku();
		Long productId = createProduct(sku);

		ResponseEntity<String> store = get("/api/v1/store/DEFAULT", json());
		assertEquals(HttpStatus.OK, store.getStatusCode());
		assertEquals("DEFAULT", JSON.readTree(store.getBody()).get("code").asText());

		ResponseEntity<String> categories = get("/api/v1/category?store=DEFAULT&lang=en", json());
		assertEquals(HttpStatus.OK, categories.getStatusCode());
		assertTrue(JSON.readTree(categories.getBody()).has("categories"));

		ResponseEntity<String> products = get("/api/v1/products?store=DEFAULT&lang=en", json());
		assertEquals(HttpStatus.OK, products.getStatusCode());
		JsonNode list = JSON.readTree(products.getBody());
		assertTrue(list.has("products"));
		assertTrue(list.get("recordsTotal").asLong() >= 1);

		ResponseEntity<String> detail = get("/api/v2/product/" + sku + "?store=DEFAULT&lang=en", json());
		assertEquals(HttpStatus.OK, detail.getStatusCode());
		JsonNode readable = JSON.readTree(detail.getBody());
		assertEquals(productId.longValue(), readable.get("id").asLong());
		assertEquals(sku, readable.get("sku").asText());
	}

	@Test
	public void cartCreateAddUpdateAndReadBack() throws Exception {
		String skuA = sku();
		String skuB = sku();
		createProduct(skuA);
		createProduct(skuB);

		PersistableShoppingCartItem item = new PersistableShoppingCartItem();
		item.setProduct(skuA);
		item.setQuantity(1);
		ResponseEntity<String> created = testRestTemplate.postForEntity("/api/v1/cart/", new HttpEntity<>(item, json()),
				String.class);
		assertEquals(HttpStatus.CREATED, created.getStatusCode());
		String code = JSON.readTree(created.getBody()).get("code").asText();

		PersistableShoppingCartItem second = new PersistableShoppingCartItem();
		second.setProduct(skuB);
		second.setQuantity(2);
		ResponseEntity<String> updated = testRestTemplate.exchange("/api/v1/cart/" + code, HttpMethod.PUT,
				new HttpEntity<>(second, json()), String.class);
		assertEquals(HttpStatus.CREATED, updated.getStatusCode());
		assertEquals(3, JSON.readTree(updated.getBody()).get("quantity").asInt());

		ResponseEntity<String> read = get("/api/v1/cart/" + code, json());
		assertEquals(HttpStatus.OK, read.getStatusCode());
		JsonNode cart = JSON.readTree(read.getBody());
		assertEquals(code, cart.get("code").asText());
		assertEquals(2, cart.get("products").size());
	}

	@Test
	public void servicesBasicAuthValidAndInvalid() {
		HttpHeaders valid = new HttpHeaders();
		valid.setBasicAuth("admin@shopizer.com", "password");
		valid.setContentType(MediaType.APPLICATION_JSON);
		ResponseEntity<String> ok = testRestTemplate.exchange("/services/private/system/optin", HttpMethod.POST,
				new HttpEntity<>("{}", valid), String.class);
		assertEquals(HttpStatus.OK, ok.getStatusCode());

		HttpHeaders invalid = new HttpHeaders();
		invalid.setBasicAuth("admin@shopizer.com", "wrong");
		invalid.setContentType(MediaType.APPLICATION_JSON);
		ResponseEntity<String> ko = testRestTemplate.exchange("/services/private/system/optin", HttpMethod.POST,
				new HttpEntity<>("{}", invalid), String.class);
		assertEquals(HttpStatus.UNAUTHORIZED, ko.getStatusCode());
	}

	@Test
	public void actuatorHealthUpAndApiDocs() throws Exception {
		ResponseEntity<String> health = get("/actuator/health", json());
		assertEquals(HttpStatus.OK, health.getStatusCode());
		assertEquals("UP", JSON.readTree(health.getBody()).get("status").asText());

		ResponseEntity<String> docs = get("/v2/api-docs", json());
		assertEquals(HttpStatus.OK, docs.getStatusCode());
		JsonNode paths = JSON.readTree(docs.getBody()).get("paths");
		assertNotNull(paths);
		assertTrue(paths.has("/api/v1/private/product/image/{id}"));
		assertFalse("customer image delete route must not be documented",
				paths.has("/api/v1/auth/product/images/{id}"));
	}

	@Test
	public void adminManagesProductImagesOnlyThroughPrivateApi() throws Exception {
		Long productId = createProduct();
		LinkedMultiValueMap<String, Object> parameters = new LinkedMultiValueMap<>();
		parameters.add("file", new ClassPathResource("image.jpg"));
		HttpHeaders multipart = getHeader();
		multipart.setContentType(MediaType.MULTIPART_FORM_DATA);
		ResponseEntity<String> upload = testRestTemplate.exchange(
				"/api/v1/private/product/" + productId + "/image?store=DEFAULT", HttpMethod.POST,
				new HttpEntity<>(parameters, multipart), String.class);
		assertEquals(HttpStatus.CREATED, upload.getStatusCode());

		JsonNode images = JSON.readTree(get("/api/v1/product/" + productId + "/images?store=DEFAULT", json()).getBody());
		assertEquals(1, images.size());
		long imageId = images.get(0).get("id").asLong();

		String email = "uatimg" + UUID.randomUUID().toString().substring(0, 8) + "@test.com";
		HttpHeaders customer = registerAndLogin(email);
		ResponseEntity<String> customerDelete = testRestTemplate.exchange("/api/v1/auth/product/images/" + imageId,
				HttpMethod.DELETE, new HttpEntity<>(customer), String.class);
		assertFalse(customerDelete.getStatusCode().is2xxSuccessful());
		assertEquals(1, JSON.readTree(get("/api/v1/product/" + productId + "/images?store=DEFAULT", json()).getBody())
				.size());

		ResponseEntity<String> adminDelete = testRestTemplate.exchange(
				"/api/v1/private/product/image/" + imageId + "?store=DEFAULT", HttpMethod.DELETE,
				new HttpEntity<>(getHeader()), String.class);
		assertEquals(HttpStatus.OK, adminDelete.getStatusCode());
		assertEquals(0, JSON.readTree(get("/api/v1/product/" + productId + "/images?store=DEFAULT", json()).getBody())
				.size());
	}

	private HttpHeaders registerAndLogin(String email) throws Exception {
		PersistableCustomer customer = new PersistableCustomer();
		customer.setEmailAddress(email);
		customer.setPassword("clear123");
		customer.setGender(CustomerGender.M.name());
		customer.setLanguage("en");
		Address billing = new Address();
		billing.setFirstName("Uat");
		billing.setLastName("Customer");
		billing.setCountry("CA");
		customer.setBilling(billing);
		customer.setStoreCode(Constants.DEFAULT_STORE);
		ResponseEntity<String> register = testRestTemplate.postForEntity("/api/v1/customer/register",
				new HttpEntity<>(customer, json()), String.class);
		assertEquals(register.toString(), HttpStatus.OK, register.getStatusCode());

		ResponseEntity<String> login = testRestTemplate.postForEntity("/api/v1/customer/login",
				new HttpEntity<>(new AuthenticationRequest(email, "clear123"), json()), String.class);
		assertEquals(HttpStatus.OK, login.getStatusCode());
		HttpHeaders headers = json();
		headers.add("Authorization", "Bearer " + JSON.readTree(login.getBody()).get("token").asText());
		return headers;
	}

	private ResponseEntity<String> get(String url, HttpHeaders headers) {
		return testRestTemplate.exchange(url, HttpMethod.GET, new HttpEntity<>(headers), String.class);
	}

	private static HttpHeaders json() {
		HttpHeaders headers = new HttpHeaders();
		headers.setContentType(new MediaType("application", "json", StandardCharsets.UTF_8));
		return headers;
	}

	private static String sku() {
		return "uat" + UUID.randomUUID().toString().replace("-", "").substring(0, 10);
	}

	private Long createProduct() throws Exception {
		return createProduct(sku());
	}

	private Long createProduct(String sku) throws Exception {
		PersistableProduct product = product(sku);
		ProductSpecification specifications = new ProductSpecification();
		specifications.setManufacturer(Manufacturer.DEFAULT_MANUFACTURER);
		product.setProductSpecifications(specifications);
		product.setPrice(BigDecimal.TEN);
		product.setSku(sku);
		product.setQuantity(100);
		ResponseEntity<String> response = testRestTemplate.postForEntity("/api/v1/private/product?store=DEFAULT",
				new HttpEntity<>(product, getHeader()), String.class);
		assertEquals(response.toString(), HttpStatus.CREATED, response.getStatusCode());
		return JSON.readTree(response.getBody()).get("id").asLong();
	}
}
