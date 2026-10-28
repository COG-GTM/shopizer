package com.salesmanager.test.shop.uat;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.http.HttpMethod.GET;
import static org.springframework.http.HttpMethod.POST;
import static org.springframework.http.HttpMethod.PUT;
import static org.springframework.http.HttpStatus.BAD_REQUEST;
import static org.springframework.http.HttpStatus.CREATED;
import static org.springframework.http.HttpStatus.OK;
import static org.springframework.http.HttpStatus.UNAUTHORIZED;

import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.SpringBootTest.WebEnvironment;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.ResponseEntity;
import org.springframework.test.context.junit.jupiter.SpringExtension;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.salesmanager.core.business.constants.Constants;
import com.salesmanager.core.model.customer.CustomerGender;
import com.salesmanager.shop.application.ShopApplication;
import com.salesmanager.shop.model.customer.PersistableCustomer;
import com.salesmanager.shop.model.customer.address.Address;
import com.salesmanager.shop.model.shoppingcart.PersistableShoppingCartItem;
import com.salesmanager.shop.model.shoppingcart.ReadableShoppingCart;
import com.salesmanager.shop.store.security.AuthenticationRequest;
import com.salesmanager.shop.store.security.AuthenticationResponse;
import com.salesmanager.test.shop.common.ServicesTestSupport;

@SpringBootTest(classes = ShopApplication.class, webEnvironment = WebEnvironment.RANDOM_PORT)
@ExtendWith(SpringExtension.class)
public class ShopizerJourneysUATTest extends ServicesTestSupport {

	private final ObjectMapper objectMapper = new ObjectMapper();

	@Test
	public void adminLoginProtectsPrivateCustomerList() throws Exception {
		ResponseEntity<AuthenticationResponse> login = testRestTemplate.postForEntity(
				"/api/v1/private/login",
				new HttpEntity<>(new AuthenticationRequest("admin@shopizer.com", "password")),
				AuthenticationResponse.class);

		assertEquals(OK, login.getStatusCode());
		assertNotNull(login.getBody());
		assertNotNull(login.getBody().getToken());

		ResponseEntity<String> authorized = testRestTemplate.exchange(
				"/api/v1/private/customers", GET, new HttpEntity<>(bearer(login.getBody().getToken())),
				String.class);
		ResponseEntity<String> missingToken = testRestTemplate.getForEntity(
				"/api/v1/private/customers", String.class);

		assertEquals(OK, authorized.getStatusCode());
		assertUnauthorizedOrForbidden(missingToken.getStatusCodeValue());
	}

	@Test
	public void customerCanRegisterLoginAndReadProfile() throws Exception {
		String email = "pr198-" + UUID.randomUUID().toString().replace("-", "") + "@example.com";
		PersistableCustomer customer = customer(email);
		ResponseEntity<AuthenticationResponse> registration = testRestTemplate.postForEntity(
				"/api/v1/customer/register",
				new HttpEntity<>(customer, getHeader()), AuthenticationResponse.class);

		assertTrue(registration.getStatusCode().is2xxSuccessful());

		ResponseEntity<AuthenticationResponse> login = testRestTemplate.postForEntity(
				"/api/v1/customer/login",
				new HttpEntity<>(new AuthenticationRequest(email, customer.getPassword())),
				AuthenticationResponse.class);

		assertEquals(OK, login.getStatusCode());
		assertNotNull(login.getBody());
		ResponseEntity<String> profile = testRestTemplate.exchange(
				"/api/v1/auth/customer/profile", GET,
				new HttpEntity<>(bearer(login.getBody().getToken())), String.class);

		assertEquals(OK, profile.getStatusCode());
		assertTrue(profile.getBody().contains(email));
	}

	@Test
	public void browseAndCartJourneyRejectsNegativePostWithoutChangingExistingCart() throws Exception {
		sampleProduct("pr198-uat-browse-" + UUID.randomUUID().toString().replace("-", ""));
		assertEquals(OK, testRestTemplate.getForEntity("/api/v1/store/DEFAULT", String.class).getStatusCode());
		assertEquals(OK, testRestTemplate.getForEntity("/api/v1/category?store=DEFAULT", String.class).getStatusCode());

		ResponseEntity<String> products = testRestTemplate.getForEntity(
				"/api/v1/products?store=DEFAULT", String.class);
		assertEquals(OK, products.getStatusCode());
		String sku = findSku(objectMapper.readTree(products.getBody()));
		assertNotNull(sku, "Product browse response did not contain a SKU");
		ResponseEntity<String> detail = testRestTemplate.getForEntity(
				"/api/v2/product/" + sku, String.class);
		assertEquals(OK, detail.getStatusCode());

		ResponseEntity<ReadableShoppingCart> created = testRestTemplate.postForEntity(
				"/api/v1/cart", new HttpEntity<>(cartItem(sku, 1), getHeader()),
				ReadableShoppingCart.class);
		assertEquals(CREATED, created.getStatusCode());
		assertNotNull(created.getBody());
		ResponseEntity<ReadableShoppingCart> updated = testRestTemplate.exchange(
				"/api/v1/cart/" + created.getBody().getCode(), PUT,
				new HttpEntity<>(cartItem(sku, 2), getHeader()), ReadableShoppingCart.class);
		assertEquals(CREATED, updated.getStatusCode());

		ResponseEntity<String> rejected = testRestTemplate.postForEntity(
				"/api/v1/cart", new HttpEntity<>(cartItem(sku, -1), getHeader()), String.class);
		ResponseEntity<ReadableShoppingCart> readback = testRestTemplate.getForEntity(
				"/api/v1/cart/" + created.getBody().getCode(), ReadableShoppingCart.class);

		assertEquals(BAD_REQUEST, rejected.getStatusCode());
		assertTrue(rejected.getBody().contains("Invalid quantity -1"));
		assertEquals(2, readback.getBody().getQuantity());
		assertEquals(1, readback.getBody().getProducts().size());
	}

	@Test
	public void basicAuthActuatorAndApiDocsContracts() throws Exception {
		ResponseEntity<String> badCredentials = testRestTemplate.exchange(
				"/services/private/customers", GET, new HttpEntity<>(basicAuth("invalid", "invalid")),
				String.class);
		ResponseEntity<String> validCredentials = testRestTemplate.exchange(
				"/services/private/customers", GET,
				new HttpEntity<>(basicAuth("admin@shopizer.com", "password")), String.class);

		assertEquals(UNAUTHORIZED, badCredentials.getStatusCode());
		assertNotEquals(UNAUTHORIZED, validCredentials.getStatusCode());

		ResponseEntity<String> health = testRestTemplate.getForEntity("/actuator/health", String.class);
		assertEquals(OK, health.getStatusCode());
		JsonNode healthBody = objectMapper.readTree(health.getBody());
		if (!"UP".equals(healthBody.path("status").asText())) {
			ResponseEntity<String> ping = testRestTemplate.getForEntity(
					"/actuator/health/ping/", String.class);
			assertEquals(OK, ping.getStatusCode());
		}

		ResponseEntity<String> docs = testRestTemplate.getForEntity("/v2/api-docs", String.class);
		assertEquals(OK, docs.getStatusCode());
		assertTrue(docs.getBody().contains("\"/api/v1/cart\""));
	}

	private PersistableCustomer customer(String email) {
		PersistableCustomer customer = new PersistableCustomer();
		customer.setEmailAddress(email);
		customer.setPassword("clear123");
		customer.setRepeatPassword("clear123");
		customer.setGender(CustomerGender.M.name());
		customer.setLanguage("en");
		customer.setStoreCode(Constants.DEFAULT_STORE);
		Address billing = new Address();
		billing.setFirstName("PR198");
		billing.setLastName("Checkout");
		billing.setCountry("BE");
		customer.setBilling(billing);
		return customer;
	}

	private static HttpHeaders bearer(String token) {
		HttpHeaders headers = new HttpHeaders();
		headers.set("Authorization", "Bearer " + token);
		return headers;
	}

	private static HttpHeaders basicAuth(String username, String password) {
		HttpHeaders headers = new HttpHeaders();
		String value = username + ":" + password;
		headers.set("Authorization", "Basic " + Base64.getEncoder()
				.encodeToString(value.getBytes(StandardCharsets.UTF_8)));
		return headers;
	}

	private static void assertUnauthorizedOrForbidden(int status) {
		assertTrue(status == 401 || status == 403, "Expected 401/403 but received " + status);
	}

	private static PersistableShoppingCartItem cartItem(String sku, int quantity) {
		PersistableShoppingCartItem item = new PersistableShoppingCartItem();
		item.setProduct(sku);
		item.setQuantity(quantity);
		return item;
	}

	private static String findSku(JsonNode node) {
		if (node.isObject()) {
			JsonNode sku = node.get("sku");
			if (sku != null && sku.isTextual() && !sku.asText().isEmpty()) {
				return sku.asText();
			}
			for (JsonNode child : node) {
				String found = findSku(child);
				if (found != null) {
					return found;
				}
			}
		} else if (node.isArray()) {
			for (JsonNode child : node) {
				String found = findSku(child);
				if (found != null) {
					return found;
				}
			}
		}
		return null;
	}

	private static JsonNode findField(JsonNode node, String fieldName) {
		if (node.isObject()) {
			JsonNode found = node.get(fieldName);
			if (found != null && !found.isNull()) {
				return found;
			}
			for (JsonNode child : node) {
				found = findField(child, fieldName);
				if (found != null) {
					return found;
				}
			}
		} else if (node.isArray()) {
			for (JsonNode child : node) {
				JsonNode found = findField(child, fieldName);
				if (found != null) {
					return found;
				}
			}
		}
		return null;
	}
}
