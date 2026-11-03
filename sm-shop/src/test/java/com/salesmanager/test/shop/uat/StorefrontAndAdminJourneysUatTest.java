package com.salesmanager.test.shop.uat;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.Collections;
import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;

import com.salesmanager.core.model.customer.CustomerGender;
import com.salesmanager.shop.model.catalog.product.ReadableProduct;
import com.salesmanager.shop.model.customer.PersistableCustomer;
import com.salesmanager.shop.model.customer.address.Address;
import com.salesmanager.shop.model.shoppingcart.PersistableShoppingCartItem;
import com.salesmanager.shop.model.shoppingcart.ReadableShoppingCart;
import com.salesmanager.shop.model.store.MerchantConfigEntity;
import com.salesmanager.shop.model.store.PersistableBrand;
import com.salesmanager.shop.model.store.ReadableBrand;
import com.salesmanager.shop.model.store.ReadableMerchantStore;
import com.salesmanager.shop.store.security.AuthenticationRequest;
import com.salesmanager.shop.store.security.AuthenticationResponse;
import com.salesmanager.test.shop.common.ServicesTestSupport;

/**
 * Black box journeys that the admin and storefront frontends rely on.
 */
public class StorefrontAndAdminJourneysUatTest extends ServicesTestSupport {

	private static final String MARKETING = "/api/v1/private/store/DEFAULT/marketing";

	private HttpHeaders json() {
		HttpHeaders headers = new HttpHeaders();
		headers.setContentType(MediaType.APPLICATION_JSON);
		headers.setAccept(Collections.singletonList(MediaType.APPLICATION_JSON));
		return headers;
	}

	private HttpHeaders bearer(String token) {
		HttpHeaders headers = json();
		headers.setBearerAuth(token);
		return headers;
	}

	private HttpHeaders basic(String user, String password) {
		HttpHeaders headers = json();
		headers.set(HttpHeaders.AUTHORIZATION, "Basic "
				+ Base64.getEncoder().encodeToString((user + ":" + password).getBytes(StandardCharsets.UTF_8)));
		return headers;
	}

	private ResponseEntity<String> get(String url, HttpHeaders headers) {
		return testRestTemplate.exchange(url, HttpMethod.GET, new HttpEntity<>(headers), String.class);
	}

	private PersistableBrand brand(String key, String value) {
		MerchantConfigEntity entity = new MerchantConfigEntity();
		entity.setKey(key);
		entity.setValue(value);
		entity.setActive(true);
		PersistableBrand brand = new PersistableBrand();
		brand.getSocialNetworks().add(entity);
		return brand;
	}

	private MerchantConfigEntity marketingEntry(String key) {
		ResponseEntity<ReadableBrand> response = testRestTemplate.exchange(MARKETING, HttpMethod.GET,
				new HttpEntity<>(getHeader()), ReadableBrand.class);
		assertEquals(HttpStatus.OK, response.getStatusCode());
		return response.getBody().getSocialNetworks().stream().filter(c -> key.equals(c.getKey())).findFirst()
				.orElse(null);
	}

	@Test
	public void adminLoginGrantsAccessToAdminApiOnlyWithValidToken() {
		ResponseEntity<AuthenticationResponse> login = testRestTemplate.postForEntity("/api/v1/private/login",
				new HttpEntity<>(new AuthenticationRequest("admin@shopizer.com", "password")),
				AuthenticationResponse.class);
		assertEquals(HttpStatus.OK, login.getStatusCode());
		String token = login.getBody().getToken();
		assertNotNull(token);
		assertFalse(token.isEmpty());

		assertEquals(HttpStatus.OK, get("/api/v1/private/customers", bearer(token)).getStatusCode());
		assertEquals(HttpStatus.UNAUTHORIZED, get("/api/v1/private/customers", json()).getStatusCode());
		// Malformed tokens answer 500 on base and head alike; only require that access is denied
		assertFalse(get("/api/v1/private/customers", bearer("not-a-jwt")).getStatusCode().is2xxSuccessful());
	}

	@Test
	public void adminManagesStoreSocialLinks() {
		String url = "https://instagram.com/" + UUID.randomUUID();
		assertEquals(HttpStatus.UNAUTHORIZED, get(MARKETING, json()).getStatusCode());

		ResponseEntity<Void> saved = testRestTemplate.exchange(MARKETING, HttpMethod.POST,
				new HttpEntity<>(brand("instagram", url), getHeader()), Void.class);
		assertEquals(HttpStatus.CREATED, saved.getStatusCode());
		MerchantConfigEntity entry = marketingEntry("instagram");
		assertNotNull(entry);
		assertEquals(url, entry.getValue());
		assertEquals("SOCIAL", entry.getType().name());

		ResponseEntity<Void> cleared = testRestTemplate.exchange(MARKETING, HttpMethod.POST,
				new HttpEntity<>(brand("instagram", ""), getHeader()), Void.class);
		assertEquals(HttpStatus.CREATED, cleared.getStatusCode());
		assertNull(marketingEntry("instagram"));

		ResponseEntity<String> reserved = testRestTemplate.exchange(MARKETING, HttpMethod.POST,
				new HttpEntity<>(brand("EMAIL_CONFIG", "{\"host\":\"evil.example\"}"), getHeader()), String.class);
		assertFalse(reserved.getStatusCode().is2xxSuccessful());
		assertNull(marketingEntry("EMAIL_CONFIG"));
	}

	@Test
	public void customerRegistersLogsInAndReadsProfile() {
		String email = "uat-" + UUID.randomUUID() + "@test.com";
		PersistableCustomer customer = new PersistableCustomer();
		customer.setEmailAddress(email);
		customer.setPassword("clear123");
		customer.setGender(CustomerGender.M.name());
		customer.setLanguage("en");
		Address billing = new Address();
		billing.setFirstName("uat");
		billing.setLastName("customer");
		billing.setCountry("BE");
		customer.setBilling(billing);
		customer.setStoreCode("DEFAULT");

		ResponseEntity<String> register = testRestTemplate.postForEntity("/api/v1/customer/register",
				new HttpEntity<>(customer, json()), String.class);
		assertEquals(HttpStatus.OK, register.getStatusCode());

		ResponseEntity<AuthenticationResponse> login = testRestTemplate.postForEntity("/api/v1/customer/login",
				new HttpEntity<>(new AuthenticationRequest(email, "clear123")), AuthenticationResponse.class);
		assertEquals(HttpStatus.OK, login.getStatusCode());
		String token = login.getBody().getToken();
		assertNotNull(token);

		ResponseEntity<String> profile = get("/api/v1/auth/customer/profile", bearer(token));
		assertEquals(HttpStatus.OK, profile.getStatusCode());
		assertTrue(profile.getBody().contains(email));
		assertEquals(HttpStatus.UNAUTHORIZED, get("/api/v1/auth/customer/profile", json()).getStatusCode());
	}

	@Test
	public void shopperBrowsesStoreCategoriesAndProducts() {
		ResponseEntity<ReadableMerchantStore> store = testRestTemplate.exchange("/api/v1/store/DEFAULT", HttpMethod.GET,
				new HttpEntity<>(json()), ReadableMerchantStore.class);
		assertEquals(HttpStatus.OK, store.getStatusCode());
		assertEquals("DEFAULT", store.getBody().getCode());

		assertEquals(HttpStatus.OK, get("/api/v1/category?store=DEFAULT&lang=en", json()).getStatusCode());

		String sku = "uat" + System.nanoTime();
		ReadableProduct product = sampleProduct(sku);
		assertNotNull(product);

		ResponseEntity<String> list = get("/api/v1/products?store=DEFAULT&lang=en", json());
		assertEquals(HttpStatus.OK, list.getStatusCode());
		assertTrue(list.getBody().contains("\"products\""));

		ResponseEntity<ReadableProduct> detail = testRestTemplate.exchange(
				"/api/v2/product/" + sku + "?store=DEFAULT&lang=en", HttpMethod.GET, new HttpEntity<>(json()),
				ReadableProduct.class);
		assertEquals(HttpStatus.OK, detail.getStatusCode());
		assertEquals(sku, detail.getBody().getSku());
	}

	@Test
	public void shopperCreatesCartAddsItemsAndReadsItBack() {
		String sku = "uatcart" + System.nanoTime();
		ReadableProduct product = sampleProduct(sku);

		PersistableShoppingCartItem item = new PersistableShoppingCartItem();
		item.setProduct(product.getSku());
		item.setQuantity(1);
		ResponseEntity<ReadableShoppingCart> created = testRestTemplate.postForEntity("/api/v1/cart/",
				new HttpEntity<>(item, json()), ReadableShoppingCart.class);
		assertEquals(HttpStatus.CREATED, created.getStatusCode());
		String code = created.getBody().getCode();
		assertNotNull(code);

		PersistableShoppingCartItem update = new PersistableShoppingCartItem();
		update.setProduct(product.getSku());
		update.setQuantity(3);
		ResponseEntity<ReadableShoppingCart> updated = testRestTemplate.exchange("/api/v1/cart/" + code,
				HttpMethod.PUT, new HttpEntity<>(update, json()), ReadableShoppingCart.class);
		assertEquals(HttpStatus.CREATED, updated.getStatusCode());

		ResponseEntity<ReadableShoppingCart> read = testRestTemplate.exchange("/api/v1/cart/" + code, HttpMethod.GET,
				new HttpEntity<>(json()), ReadableShoppingCart.class);
		assertEquals(HttpStatus.OK, read.getStatusCode());
		assertEquals(code, read.getBody().getCode());
		assertEquals(1, read.getBody().getProducts().size());
		assertEquals(3, read.getBody().getQuantity());
	}

	@Test
	public void servicesRequireValidBasicAuth() {
		String url = "/services/private/system/optin";
		assertEquals(HttpStatus.OK, testRestTemplate.exchange(url, HttpMethod.POST,
				new HttpEntity<>("{}", basic("admin@shopizer.com", "password")), String.class).getStatusCode());
		assertEquals(HttpStatus.UNAUTHORIZED, testRestTemplate.exchange(url, HttpMethod.POST,
				new HttpEntity<>("{}", basic("admin@shopizer.com", "wrong")), String.class).getStatusCode());
		assertEquals(HttpStatus.UNAUTHORIZED, testRestTemplate.exchange(url, HttpMethod.POST,
				new HttpEntity<>("{}", json()), String.class).getStatusCode());
	}

	@Test
	public void healthIsUpAndApiDocsAreServed() {
		ResponseEntity<String> health = get("/actuator/health", json());
		assertEquals(HttpStatus.OK, health.getStatusCode());
		assertTrue(health.getBody().contains("\"status\":\"UP\""));

		ResponseEntity<String> docs = get("/v2/api-docs", json());
		assertEquals(HttpStatus.OK, docs.getStatusCode());
		assertTrue(docs.getBody().contains("\"swagger\""));
		assertTrue(docs.getBody().contains("/api/v1/private/store/{code}/marketing"));
	}
}
