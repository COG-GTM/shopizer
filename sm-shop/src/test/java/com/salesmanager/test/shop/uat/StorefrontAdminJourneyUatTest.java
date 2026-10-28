package com.salesmanager.test.shop.uat;

import static org.hamcrest.Matchers.anyOf;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.not;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertThat;
import static org.junit.Assert.assertTrue;

import java.util.concurrent.atomic.AtomicInteger;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.springframework.beans.factory.annotation.Autowired;
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
import com.salesmanager.shop.model.catalog.category.PersistableCategory;
import com.salesmanager.shop.model.catalog.product.ReadableProduct;
import com.salesmanager.shop.model.customer.PersistableCustomer;
import com.salesmanager.shop.model.customer.address.Address;
import com.salesmanager.shop.model.shoppingcart.PersistableShoppingCartItem;
import com.salesmanager.shop.store.security.AuthenticationRequest;
import com.salesmanager.test.shop.common.ServicesTestSupport;

/**
 * Black-box storefront and admin user journeys over HTTP against the full application (H2).
 */
@SpringBootTest(classes = ShopApplication.class, webEnvironment = WebEnvironment.RANDOM_PORT)
@RunWith(SpringRunner.class)
public class StorefrontAdminJourneyUatTest extends ServicesTestSupport {

	private static final ObjectMapper MAPPER = new ObjectMapper();
	private static final AtomicInteger SEQ = new AtomicInteger();

	@Autowired
	private TestRestTemplate testRestTemplate;

	@Test
	public void adminLogsInAndCallsPrivateApiAndIsRejectedWithoutValidToken() throws Exception {
		ResponseEntity<String> login = testRestTemplate.postForEntity("/api/v1/private/login",
				new HttpEntity<>(new AuthenticationRequest("admin@shopizer.com", "password")), String.class);
		assertThat(login.getStatusCode(), is(HttpStatus.OK));
		String token = MAPPER.readTree(login.getBody()).get("token").asText();
		assertFalse(token.isEmpty());

		HttpHeaders bearer = new HttpHeaders();
		bearer.set("Authorization", "Bearer " + token);
		ResponseEntity<String> users = testRestTemplate.exchange("/api/v1/private/customers", HttpMethod.GET,
				new HttpEntity<>(bearer), String.class);
		assertThat(users.getStatusCode(), is(HttpStatus.OK));

		ResponseEntity<String> anonymous = testRestTemplate.getForEntity("/api/v1/private/customers", String.class);
		assertThat(anonymous.getStatusCode(), anyOf(is(HttpStatus.UNAUTHORIZED), is(HttpStatus.FORBIDDEN)));

		HttpHeaders bad = new HttpHeaders();
		bad.set("Authorization", "Bearer not.a.valid.token");
		ResponseEntity<String> badToken = testRestTemplate.exchange("/api/v1/private/customers", HttpMethod.GET,
				new HttpEntity<>(bad), String.class);
		assertFalse("malformed token must not grant access", badToken.getStatusCode().is2xxSuccessful());

		ResponseEntity<String> wrongPassword = testRestTemplate.postForEntity("/api/v1/private/login",
				new HttpEntity<>(new AuthenticationRequest("admin@shopizer.com", "wrong")), String.class);
		assertThat(wrongPassword.getStatusCode(), anyOf(is(HttpStatus.UNAUTHORIZED), is(HttpStatus.FORBIDDEN)));
	}

	@Test
	public void customerRegistersLogsInAndReadsProfile() throws Exception {
		String email = "uat" + SEQ.incrementAndGet() + "-" + System.nanoTime() % 100000 + "@test.com";
		HttpHeaders customer = registerAndLogin(email, "clear123");

		ResponseEntity<String> profile = testRestTemplate.exchange("/api/v1/auth/customer/profile", HttpMethod.GET,
				new HttpEntity<>(customer), String.class);
		assertThat(profile.getBody(), profile.getStatusCode(), is(HttpStatus.OK));
		assertEquals(email, MAPPER.readTree(profile.getBody()).get("emailAddress").asText());

		ResponseEntity<String> anonymous = testRestTemplate.getForEntity("/api/v1/auth/customer/profile", String.class);
		assertThat(anonymous.getStatusCode(), anyOf(is(HttpStatus.UNAUTHORIZED), is(HttpStatus.FORBIDDEN)));
	}

	@Test
	public void shopperBrowsesStoreCategoriesAndProducts() throws Exception {
		ReadableProduct product = sampleProduct(unique("uatbrowse"));

		JsonNode store = getJson("/api/v1/store/DEFAULT");
		assertEquals("DEFAULT", store.get("code").asText());

		JsonNode categories = getJson("/api/v1/category?store=DEFAULT&lang=en");
		assertTrue(categories.get("categories").isArray());
		assertTrue(categories.get("categories").size() > 0);

		JsonNode products = getJson("/api/v1/products?store=DEFAULT&lang=en&count=100");
		assertTrue(products.get("products").isArray());
		assertTrue(products.get("recordsTotal").asInt() > 0);

		JsonNode detail = getJson("/api/v2/product/" + product.getSku() + "?store=DEFAULT&lang=en");
		assertEquals(product.getId().longValue(), detail.get("id").asLong());
		assertEquals(product.getSku(), detail.get("sku").asText());
	}

	@Test
	public void shopperBuildsCartAndReadsItBack() throws Exception {
		ReadableProduct first = sampleProduct(unique("uatcart1"));
		ReadableProduct second = sampleProduct(unique("uatcart2"));

		ResponseEntity<String> created = testRestTemplate.postForEntity("/api/v1/cart/",
				new HttpEntity<>(item(first.getSku(), 1)), String.class);
		assertThat(created.getBody(), created.getStatusCode(), is(HttpStatus.CREATED));
		String code = MAPPER.readTree(created.getBody()).get("code").asText();

		ResponseEntity<String> added = testRestTemplate.exchange("/api/v1/cart/" + code, HttpMethod.PUT,
				new HttpEntity<>(item(second.getSku(), 2)), String.class);
		assertThat(added.getBody(), added.getStatusCode(), is(HttpStatus.CREATED));
		assertEquals(3, MAPPER.readTree(added.getBody()).get("quantity").asInt());

		JsonNode cart = getJson("/api/v1/cart/" + code);
		assertEquals(code, cart.get("code").asText());
		assertEquals(2, cart.get("products").size());
	}

	@Test
	public void adminManagesCategoryLifecycle() throws Exception {
		String code = unique("uatcat");
		PersistableCategory request = category(code, code);
		request.setId(12345L);
		ResponseEntity<String> created = testRestTemplate.postForEntity("/api/v1/private/category?store=DEFAULT",
				new HttpEntity<>(request, getHeader()), String.class);
		assertThat(created.getBody(), created.getStatusCode(), is(HttpStatus.CREATED));
		long id = MAPPER.readTree(created.getBody()).get("id").asLong();
		assertThat(id, not(is(0L)));

		String renamed = unique("uatcat-renamed");
		ResponseEntity<String> updated = testRestTemplate.exchange("/api/v1/private/category/" + id + "?store=DEFAULT",
				HttpMethod.PUT, new HttpEntity<>(category(renamed, renamed), getHeader()), String.class);
		assertThat(updated.getBody(), updated.getStatusCode(), is(HttpStatus.OK));
		assertTrue(categoryCodeExists(renamed));
		assertFalse(categoryCodeExists(code));

		PersistableCategory hide = new PersistableCategory();
		hide.setVisible(false);
		ResponseEntity<String> hidden = testRestTemplate.exchange(
				"/api/v1/private/category/" + id + "/visible?store=DEFAULT", HttpMethod.PATCH,
				new HttpEntity<>(hide, getHeader()), String.class);
		assertThat(hidden.getBody(), hidden.getStatusCode(), is(HttpStatus.OK));

		ResponseEntity<String> deleted = testRestTemplate.exchange("/api/v1/private/category/" + id + "?store=DEFAULT",
				HttpMethod.DELETE, new HttpEntity<>(getHeader()), String.class);
		assertThat(deleted.getBody(), deleted.getStatusCode(), is(HttpStatus.OK));
		assertFalse(categoryCodeExists(renamed));

		ResponseEntity<String> missing = testRestTemplate.exchange("/api/v1/private/category/" + id + "?store=DEFAULT",
				HttpMethod.DELETE, new HttpEntity<>(getHeader()), String.class);
		assertThat(missing.getBody(), missing.getStatusCode(), is(HttpStatus.NOT_FOUND));
	}

	@Test
	public void servicesEndpointsRequireValidBasicAuth() {
		ResponseEntity<String> invalid = testRestTemplate.withBasicAuth("admin@shopizer.com", "wrong")
				.getForEntity("/services/private/system/optin", String.class);
		assertThat(invalid.getStatusCode(), is(HttpStatus.UNAUTHORIZED));

		ResponseEntity<String> anonymous = testRestTemplate.getForEntity("/services/private/system/optin", String.class);
		assertFalse(anonymous.getStatusCode().is2xxSuccessful());

		ResponseEntity<String> valid = testRestTemplate.withBasicAuth("admin@shopizer.com", "password")
				.getForEntity("/services/private/system/optin", String.class);
		assertThat(valid.getStatusCode(), not(is(HttpStatus.UNAUTHORIZED)));
		assertFalse(valid.getStatusCode().is3xxRedirection());
	}

	@Test
	public void actuatorHealthIsUpAndApiDocsAreServed() throws Exception {
		ResponseEntity<String> health = testRestTemplate.getForEntity("/actuator/health", String.class);
		assertThat(health.getStatusCode(), is(HttpStatus.OK));
		assertEquals("UP", MAPPER.readTree(health.getBody()).get("status").asText());

		ResponseEntity<String> docs = testRestTemplate.getForEntity("/v2/api-docs", String.class);
		assertThat(docs.getStatusCode(), is(HttpStatus.OK));
		assertThat(docs.getBody(), containsString("/api/v1/private/category/{id}"));
	}

	private HttpHeaders registerAndLogin(String email, String password) throws Exception {
		PersistableCustomer customer = new PersistableCustomer();
		customer.setEmailAddress(email);
		customer.setPassword(password);
		customer.setGender(CustomerGender.M.name());
		customer.setLanguage("en");
		Address billing = new Address();
		billing.setFirstName("Uat");
		billing.setLastName("Shopper");
		billing.setCountry("BE");
		customer.setBilling(billing);
		customer.setStoreCode("DEFAULT");
		ResponseEntity<String> register = testRestTemplate.postForEntity("/api/v1/customer/register",
				new HttpEntity<>(customer), String.class);
		assertThat(register.getBody(), register.getStatusCode(), is(HttpStatus.OK));

		ResponseEntity<String> login = testRestTemplate.postForEntity("/api/v1/customer/login",
				new HttpEntity<>(new AuthenticationRequest(email, password)), String.class);
		assertThat(login.getBody(), login.getStatusCode(), is(HttpStatus.OK));
		String token = MAPPER.readTree(login.getBody()).get("token").asText();
		assertNotNull(token);
		HttpHeaders headers = new HttpHeaders();
		headers.setContentType(MediaType.APPLICATION_JSON);
		headers.set("Authorization", "Bearer " + token);
		return headers;
	}

	private boolean categoryCodeExists(String code) throws Exception {
		ResponseEntity<String> response = testRestTemplate.exchange(
				"/api/v1/private/category/unique?store=DEFAULT&code=" + code, HttpMethod.GET,
				new HttpEntity<>(getHeader()), String.class);
		assertThat(response.getBody(), response.getStatusCode(), is(HttpStatus.OK));
		return MAPPER.readTree(response.getBody()).get("exists").asBoolean();
	}

	private JsonNode getJson(String url) throws Exception {
		ResponseEntity<String> response = testRestTemplate.getForEntity(url, String.class);
		assertThat(response.getBody(), response.getStatusCode(), is(HttpStatus.OK));
		return MAPPER.readTree(response.getBody());
	}

	private static PersistableShoppingCartItem item(String sku, int quantity) {
		PersistableShoppingCartItem item = new PersistableShoppingCartItem();
		item.setProduct(sku);
		item.setQuantity(quantity);
		return item;
	}

	private static String unique(String prefix) {
		return prefix + SEQ.incrementAndGet() + (System.nanoTime() % 100000);
	}
}
