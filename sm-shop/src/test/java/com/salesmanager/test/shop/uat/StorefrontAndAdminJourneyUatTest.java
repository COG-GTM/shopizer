package com.salesmanager.test.shop.uat;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.SpringBootTest.WebEnvironment;
import org.springframework.core.io.ClassPathResource;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.test.context.junit.jupiter.SpringExtension;
import org.springframework.util.LinkedMultiValueMap;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.salesmanager.core.business.constants.Constants;
import com.salesmanager.core.model.customer.CustomerGender;
import com.salesmanager.shop.application.ShopApplication;
import com.salesmanager.shop.model.catalog.product.ReadableProduct;
import com.salesmanager.shop.model.customer.PersistableCustomer;
import com.salesmanager.shop.model.customer.address.Address;
import com.salesmanager.shop.store.security.AuthenticationRequest;
import com.salesmanager.test.shop.common.ServicesTestSupport;

/**
 * Black-box storefront and admin journeys against the running app on a random port.
 */
@SpringBootTest(classes = ShopApplication.class, webEnvironment = WebEnvironment.RANDOM_PORT)
@ExtendWith(SpringExtension.class)
public class StorefrontAndAdminJourneyUatTest extends ServicesTestSupport {

	private static final String PASSWORD = "clear123";
	private final ObjectMapper mapper = new ObjectMapper();

	@Test
	public void adminLogsInAndCallsPrivateApi() throws Exception {
		ResponseEntity<String> login = testRestTemplate.postForEntity("/api/v1/private/login",
				new HttpEntity<>(new AuthenticationRequest("admin@shopizer.com", "password")), String.class);
		assertEquals(HttpStatus.OK, login.getStatusCode());
		String token = json(login).get("token").asText();
		assertFalse(token.isEmpty());

		ResponseEntity<String> profile = get("/api/v1/private/user/profile", bearer(token));
		assertEquals(HttpStatus.OK, profile.getStatusCode());
		assertEquals("admin@shopizer.com", json(profile).get("userName").asText());

		assertEquals(HttpStatus.UNAUTHORIZED, get("/api/v1/private/user/profile", new HttpHeaders()).getStatusCode());
		ResponseEntity<String> tampered = get("/api/v1/private/user/profile", bearer(token + "tampered"));
		assertFalse(tampered.getStatusCode().is2xxSuccessful(), tampered.toString());

		ResponseEntity<String> badLogin = testRestTemplate.postForEntity("/api/v1/private/login",
				new HttpEntity<>(new AuthenticationRequest("admin@shopizer.com", "wrong")), String.class);
		assertFalse(badLogin.getStatusCode().is2xxSuccessful(), badLogin.toString());
	}

	@Test
	public void customerRegistersLogsInAndReadsProfile() throws Exception {
		String token = registerAndLogin("uat-profile@test.com");

		ResponseEntity<String> profile = get("/api/v1/auth/customer/profile", bearer(token));
		assertEquals(HttpStatus.OK, profile.getStatusCode());
		assertEquals("uat-profile@test.com", json(profile).get("emailAddress").asText());

		assertEquals(HttpStatus.UNAUTHORIZED, get("/api/v1/auth/customer/profile", new HttpHeaders()).getStatusCode());
		assertEquals(HttpStatus.UNAUTHORIZED,
				get("/api/v1/private/user/profile", bearer(token)).getStatusCode());
	}

	@Test
	public void shopperBrowsesStoreCategoriesAndProducts() throws Exception {
		ReadableProduct product = sampleProduct("uatbrowse");

		ResponseEntity<String> store = get("/api/v1/store/" + Constants.DEFAULT_STORE, new HttpHeaders());
		assertEquals(HttpStatus.OK, store.getStatusCode());
		assertEquals(Constants.DEFAULT_STORE, json(store).get("code").asText());

		ResponseEntity<String> categories = get("/api/v1/category?store=" + Constants.DEFAULT_STORE, new HttpHeaders());
		assertEquals(HttpStatus.OK, categories.getStatusCode());
		assertTrue(json(categories).get("categories").isArray());

		ResponseEntity<String> products = get("/api/v1/products?store=" + Constants.DEFAULT_STORE + "&count=100",
				new HttpHeaders());
		assertEquals(HttpStatus.OK, products.getStatusCode());
		boolean listed = false;
		for (JsonNode p : json(products).get("products")) {
			listed |= p.get("id").asLong() == product.getId();
		}
		assertTrue(listed, products.getBody());

		ResponseEntity<String> detail = get("/api/v2/product/" + product.getSku() + "?store=" + Constants.DEFAULT_STORE,
				new HttpHeaders());
		assertEquals(HttpStatus.OK, detail.getStatusCode());
		assertEquals("uatbrowse", json(detail).get("sku").asText());
		assertEquals(product.getId().longValue(), json(detail).get("id").asLong());
	}

	@Test
	public void adminManagesProductImagesThatShoppersSee() throws Exception {
		ReadableProduct product = sampleProduct("uatimage");
		String images = "/api/v1/product/" + product.getId() + "/images?store=" + Constants.DEFAULT_STORE;

		LinkedMultiValueMap<String, Object> parameters = new LinkedMultiValueMap<>();
		parameters.add("file", new ClassPathResource("image.jpg"));
		HttpHeaders multipart = getHeader();
		multipart.setContentType(MediaType.MULTIPART_FORM_DATA);
		ResponseEntity<String> upload = testRestTemplate.exchange(
				"/api/v1/private/product/" + product.getId() + "/image?store=" + Constants.DEFAULT_STORE,
				HttpMethod.POST, new HttpEntity<>(parameters, multipart), String.class);
		assertEquals(HttpStatus.CREATED, upload.getStatusCode());

		JsonNode listed = json(get(images, new HttpHeaders()));
		assertEquals(1, listed.size());
		long imageId = listed.get(0).get("id").asLong();
		assertTrue(listed.get(0).get("defaultImage").asBoolean());
		assertFalse(listed.get(0).get("imageUrl").asText().isEmpty());

		ResponseEntity<String> delete = testRestTemplate.exchange(
				"/api/v1/private/product/image/" + imageId + "?store=" + Constants.DEFAULT_STORE, HttpMethod.DELETE,
				new HttpEntity<>(getHeader()), String.class);
		assertEquals(HttpStatus.OK, delete.getStatusCode());
		assertEquals(0, json(get(images, new HttpHeaders())).size());
	}

	@Test
	public void shopperBuildsAndUpdatesCart() throws Exception {
		ReadableProduct product = sampleProduct("uatcart");

		ResponseEntity<String> created = testRestTemplate.postForEntity("/api/v1/cart",
				new HttpEntity<>(cartItem(product.getSku(), 1), jsonHeaders()), String.class);
		assertEquals(HttpStatus.CREATED, created.getStatusCode());
		String code = json(created).get("code").asText();
		assertEquals(1, json(created).get("quantity").asInt());

		ResponseEntity<String> updated = testRestTemplate.exchange("/api/v1/cart/" + code, HttpMethod.PUT,
				new HttpEntity<>(cartItem(product.getSku(), 3), jsonHeaders()), String.class);
		assertEquals(HttpStatus.CREATED, updated.getStatusCode());

		ResponseEntity<String> read = get("/api/v1/cart/" + code, new HttpHeaders());
		assertEquals(HttpStatus.OK, read.getStatusCode());
		JsonNode cart = json(read);
		assertEquals(code, cart.get("code").asText());
		assertEquals(3, cart.get("quantity").asInt());
		assertEquals(product.getSku(), cart.get("products").get(0).get("sku").asText());
	}

	@Test
	public void authenticatedCustomerChecksOutAndReadsOrderBack() throws Exception {
		ReadableProduct product = sampleProduct("uatcheckout");
		String token = registerAndLogin("uat-checkout@test.com");

		Map<String, Object> moneyOrder = new HashMap<>();
		moneyOrder.put("code", "moneyorder");
		moneyOrder.put("active", true);
		moneyOrder.put("defaultSelected", true);
		moneyOrder.put("integrationKeys", java.util.Collections.singletonMap("address", "1 main street"));
		ResponseEntity<String> configured = testRestTemplate.postForEntity(
				"/api/v1/private/modules/payment?store=" + Constants.DEFAULT_STORE, new HttpEntity<>(moneyOrder, getHeader()),
				String.class);
		assertEquals(HttpStatus.OK, configured.getStatusCode(), configured.getBody());

		ResponseEntity<String> cartResponse = testRestTemplate.postForEntity("/api/v1/cart",
				new HttpEntity<>(cartItem(product.getSku(), 1), bearer(token)), String.class);
		assertEquals(HttpStatus.CREATED, cartResponse.getStatusCode());
		JsonNode cart = json(cartResponse);

		Map<String, Object> payment = new HashMap<>();
		payment.put("paymentModule", "moneyorder");
		payment.put("paymentType", "MONEYORDER");
		payment.put("transactionType", "CAPTURE");
		payment.put("amount", cart.get("total").asText());
		Map<String, Object> order = new HashMap<>();
		order.put("payment", payment);
		order.put("currency", "USD");

		ResponseEntity<String> checkout = testRestTemplate.postForEntity(
				"/api/v1/auth/cart/" + cart.get("code").asText() + "/checkout",
				new HttpEntity<>(order, bearer(token)), String.class);
		assertEquals(HttpStatus.OK, checkout.getStatusCode(), checkout.getBody());
		long orderId = json(checkout).get("id").asLong();

		ResponseEntity<String> readBack = get("/api/v1/auth/orders/" + orderId, bearer(token));
		assertEquals(HttpStatus.OK, readBack.getStatusCode());
		assertEquals(orderId, json(readBack).get("id").asLong());
	}

	@Test
	public void servicesApiRequiresValidBasicAuth() {
		ResponseEntity<String> valid = testRestTemplate.withBasicAuth("admin@shopizer.com", "password")
				.postForEntity("/services/private/system/optin", new HttpEntity<>("{}", jsonHeaders()), String.class);
		assertEquals(HttpStatus.OK, valid.getStatusCode());

		ResponseEntity<String> invalid = testRestTemplate.withBasicAuth("admin@shopizer.com", "wrong")
				.postForEntity("/services/private/system/optin", new HttpEntity<>("{}", jsonHeaders()), String.class);
		assertEquals(HttpStatus.UNAUTHORIZED, invalid.getStatusCode());

		ResponseEntity<String> none = testRestTemplate.postForEntity("/services/private/system/optin",
				new HttpEntity<>("{}", jsonHeaders()), String.class);
		assertFalse(none.getStatusCode().is2xxSuccessful(), none.toString());
	}

	@Test
	public void actuatorIsUpAndApiDocsDescribeOnlyPrivateImageWrites() throws Exception {
		ResponseEntity<String> health = get("/actuator/health", new HttpHeaders());
		assertEquals(HttpStatus.OK, health.getStatusCode());
		assertEquals("UP", json(health).get("status").asText());

		ResponseEntity<String> docs = get("/v2/api-docs", new HttpHeaders());
		assertEquals(HttpStatus.OK, docs.getStatusCode());
		JsonNode paths = json(docs).get("paths");
		assertTrue(paths.has("/api/v1/private/product/image/{id}"));
		assertTrue(paths.get("/api/v1/private/product/image/{id}").has("delete"));
		assertTrue(paths.has("/api/v1/product/{productId}/images"));
		assertFalse(paths.has("/api/v1/auth/product/images/{id}"));
		assertFalse(paths.has("/api/v1/auth/product/{id}/image"));
	}

	private String registerAndLogin(String email) throws Exception {
		PersistableCustomer customer = new PersistableCustomer();
		customer.setEmailAddress(email);
		customer.setPassword(PASSWORD);
		customer.setGender(CustomerGender.M.name());
		customer.setLanguage("en");
		Address billing = new Address();
		billing.setFirstName("uat");
		billing.setLastName("shopper");
		billing.setAddress("1 main street");
		billing.setCity("Toronto");
		billing.setPostalCode("M5V 2T6");
		billing.setStateProvince("ON");
		billing.setCountry("CA");
		customer.setBilling(billing);
		customer.setStoreCode(Constants.DEFAULT_STORE);
		ResponseEntity<String> register = testRestTemplate.postForEntity("/api/v1/customer/register",
				new HttpEntity<>(customer, jsonHeaders()), String.class);
		assertEquals(HttpStatus.OK, register.getStatusCode(), register.getBody());

		ResponseEntity<String> login = testRestTemplate.postForEntity("/api/v1/customer/login",
				new HttpEntity<>(new AuthenticationRequest(email, PASSWORD)), String.class);
		assertEquals(HttpStatus.OK, login.getStatusCode());
		String token = json(login).get("token").asText();
		assertNotEquals("", token);
		return token;
	}

	private static Map<String, Object> cartItem(String sku, int quantity) {
		Map<String, Object> item = new HashMap<>();
		item.put("product", sku);
		item.put("quantity", quantity);
		return item;
	}

	private ResponseEntity<String> get(String url, HttpHeaders headers) {
		return testRestTemplate.exchange(url, HttpMethod.GET, new HttpEntity<>(headers), String.class);
	}

	private JsonNode json(ResponseEntity<String> response) throws Exception {
		return mapper.readTree(response.getBody());
	}

	private static HttpHeaders jsonHeaders() {
		HttpHeaders headers = new HttpHeaders();
		headers.setContentType(new MediaType("application", "json", StandardCharsets.UTF_8));
		return headers;
	}

	private static HttpHeaders bearer(String token) {
		HttpHeaders headers = jsonHeaders();
		headers.add("Authorization", "Bearer " + token);
		return headers;
	}
}
