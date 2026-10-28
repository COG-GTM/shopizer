package com.salesmanager.test.shop.uat;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.anyOf;
import static org.hamcrest.Matchers.is;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import java.util.List;
import java.util.Map;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.SpringBootTest.WebEnvironment;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.test.context.junit4.SpringRunner;

import com.salesmanager.shop.application.ShopApplication;
import com.salesmanager.shop.model.catalog.product.ReadableProduct;
import com.salesmanager.shop.model.customer.PersistableCustomer;
import com.salesmanager.shop.model.customer.address.Address;
import com.salesmanager.shop.model.shoppingcart.PersistableShoppingCartItem;
import com.salesmanager.shop.store.security.AuthenticationRequest;
import com.salesmanager.test.shop.common.ServicesTestSupport;

/**
 * Black-box storefront/admin journeys over HTTP (random port, H2) that the admin and shop frontends rely on.
 */
@SpringBootTest(classes = ShopApplication.class, webEnvironment = WebEnvironment.RANDOM_PORT)
@RunWith(SpringRunner.class)
public class UpgradeUserJourneyUatTest extends ServicesTestSupport {

	private static final ParameterizedTypeReference<Map<String, Object>> MAP = new ParameterizedTypeReference<Map<String, Object>>() {
	};

	private ResponseEntity<Map<String, Object>> get(String url, HttpHeaders headers) {
		return testRestTemplate.exchange(url, HttpMethod.GET, new HttpEntity<>(headers), MAP);
	}

	private static HttpHeaders json() {
		HttpHeaders headers = new HttpHeaders();
		headers.setContentType(MediaType.APPLICATION_JSON);
		return headers;
	}

	@Test
	public void adminLoginGrantsPrivateApiAndMissingOrBadTokenIsRejected() {
		ResponseEntity<Map<String, Object>> withToken = get("/api/v1/private/stores?store=DEFAULT", getHeader());
		assertThat(withToken.getStatusCode(), is(HttpStatus.OK));

		ResponseEntity<String> noToken = testRestTemplate.exchange("/api/v1/private/stores?store=DEFAULT",
				HttpMethod.GET, new HttpEntity<>(json()), String.class);
		assertThat(noToken.getStatusCode(), anyOf(is(HttpStatus.UNAUTHORIZED), is(HttpStatus.FORBIDDEN)));

		HttpHeaders bad = json();
		bad.add("Authorization", "Bearer not.a.valid.token");
		ResponseEntity<String> badToken = testRestTemplate.exchange("/api/v1/private/stores?store=DEFAULT",
				HttpMethod.GET, new HttpEntity<>(bad), String.class);
		// a malformed JWT is answered with 500 on both 3.2.7 and this PR; only assert it is rejected
		assertFalse(badToken.getStatusCode().is2xxSuccessful());

		ResponseEntity<String> badPassword = testRestTemplate.postForEntity("/api/v1/private/login",
				new HttpEntity<>(new AuthenticationRequest("admin@shopizer.com", "wrong-password")), String.class);
		assertThat(badPassword.getStatusCode(), anyOf(is(HttpStatus.UNAUTHORIZED), is(HttpStatus.FORBIDDEN)));
	}

	@Test
	public void customerRegistersLogsInAndReadsProfile() {
		String email = "uat" + System.nanoTime() + "@shopizer.com";
		PersistableCustomer customer = new PersistableCustomer();
		customer.setEmailAddress(email);
		customer.setPassword("clear123");
		customer.setGender("M");
		customer.setLanguage("en");
		Address billing = new Address();
		billing.setFirstName("uat");
		billing.setLastName("customer");
		billing.setCountry("BE");
		customer.setBilling(billing);
		customer.setStoreCode("DEFAULT");

		ResponseEntity<Map<String, Object>> register = testRestTemplate.exchange("/api/v1/customer/register",
				HttpMethod.POST, new HttpEntity<>(customer, json()), MAP);
		assertThat(register.getStatusCode(), is(HttpStatus.OK));
		assertNotNull(register.getBody().get("token"));

		ResponseEntity<Map<String, Object>> login = testRestTemplate.exchange("/api/v1/customer/login",
				HttpMethod.POST, new HttpEntity<>(new AuthenticationRequest(email, "clear123"), json()), MAP);
		assertThat(login.getStatusCode(), is(HttpStatus.OK));
		String token = (String) login.getBody().get("token");
		assertNotNull(token);

		HttpHeaders auth = json();
		auth.add("Authorization", "Bearer " + token);
		ResponseEntity<Map<String, Object>> profile = get("/api/v1/auth/customer/profile", auth);
		assertThat(profile.getStatusCode(), is(HttpStatus.OK));
		assertEquals(email, profile.getBody().get("emailAddress"));

		ResponseEntity<String> anonymous = testRestTemplate.exchange("/api/v1/auth/customer/profile", HttpMethod.GET,
				new HttpEntity<>(json()), String.class);
		assertThat(anonymous.getStatusCode(), anyOf(is(HttpStatus.UNAUTHORIZED), is(HttpStatus.FORBIDDEN)));
	}

	@Test
	public void shopperBrowsesStoreCategoriesAndProducts() {
		ReadableProduct product = sampleProduct("uatBrowse" + System.nanoTime());

		ResponseEntity<Map<String, Object>> store = get("/api/v1/store/DEFAULT", json());
		assertThat(store.getStatusCode(), is(HttpStatus.OK));
		assertEquals("DEFAULT", store.getBody().get("code"));

		ResponseEntity<Map<String, Object>> categories = get("/api/v1/category?store=DEFAULT&lang=en", json());
		assertThat(categories.getStatusCode(), is(HttpStatus.OK));
		assertFalse(((List<?>) categories.getBody().get("categories")).isEmpty());

		ResponseEntity<Map<String, Object>> products = get("/api/v1/products?store=DEFAULT&lang=en", json());
		assertThat(products.getStatusCode(), is(HttpStatus.OK));
		assertTrue(((Number) products.getBody().get("recordsTotal")).intValue() > 0);

		ResponseEntity<Map<String, Object>> detail = get("/api/v2/product/" + product.getSku() + "?store=DEFAULT&lang=en", json());
		assertThat(detail.getStatusCode(), is(HttpStatus.OK));
		assertEquals(product.getSku(), detail.getBody().get("sku"));
	}

	@Test
	public void shopperCreatesCartAddsAndUpdatesItemsAndReadsItBack() {
		ReadableProduct first = sampleProduct("uatCartA" + System.nanoTime());
		ReadableProduct second = sampleProduct("uatCartB" + System.nanoTime());

		PersistableShoppingCartItem item = new PersistableShoppingCartItem();
		item.setProduct(first.getSku());
		item.setQuantity(1);
		ResponseEntity<Map<String, Object>> created = testRestTemplate.exchange("/api/v1/cart/", HttpMethod.POST,
				new HttpEntity<>(item, json()), MAP);
		assertThat(created.getStatusCode(), is(HttpStatus.CREATED));
		String code = (String) created.getBody().get("code");
		assertNotNull(code);

		PersistableShoppingCartItem other = new PersistableShoppingCartItem();
		other.setProduct(second.getSku());
		other.setQuantity(2);
		ResponseEntity<Map<String, Object>> updated = testRestTemplate.exchange("/api/v1/cart/" + code, HttpMethod.PUT,
				new HttpEntity<>(other, json()), MAP);
		assertThat(updated.getStatusCode(), is(HttpStatus.CREATED));
		assertEquals(3, ((Number) updated.getBody().get("quantity")).intValue());

		ResponseEntity<Map<String, Object>> read = get("/api/v1/cart/" + code, json());
		assertThat(read.getStatusCode(), is(HttpStatus.OK));
		assertEquals(code, read.getBody().get("code"));
		assertEquals(2, ((List<?>) read.getBody().get("products")).size());
	}

	@Test
	public void servicesRequireValidBasicAuth() {
		ResponseEntity<String> invalid = testRestTemplate.withBasicAuth("admin@shopizer.com", "wrong-password")
				.getForEntity("/services/private/ping", String.class);
		assertThat(invalid.getStatusCode(), is(HttpStatus.UNAUTHORIZED));

		ResponseEntity<String> none = testRestTemplate.getForEntity("/services/private/ping", String.class);
		assertThat(none.getStatusCode(), anyOf(is(HttpStatus.UNAUTHORIZED), is(HttpStatus.FOUND)));

		ResponseEntity<String> valid = testRestTemplate.withBasicAuth("admin@shopizer.com", "password")
				.getForEntity("/services/private/ping", String.class);
		assertThat(valid.getStatusCode(), is(HttpStatus.NOT_FOUND));
	}

	@Test
	public void actuatorHealthIsUpAndApiDocsRespond() {
		ResponseEntity<Map<String, Object>> health = get("/actuator/health", json());
		assertThat(health.getStatusCode(), is(HttpStatus.OK));
		assertEquals("UP", health.getBody().get("status"));

		ResponseEntity<Map<String, Object>> docs = get("/v2/api-docs", json());
		assertThat(docs.getStatusCode(), is(HttpStatus.OK));
		Map<?, ?> paths = (Map<?, ?>) docs.getBody().get("paths");
		assertTrue(paths.containsKey("/api/v1/private/product/{sku}/prices"));
		assertTrue(paths.containsKey("/api/v1/private/product/{sku}/inventory/{inventoryId}/price/{priceId}"));
	}
}
