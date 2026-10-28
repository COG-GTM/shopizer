package com.salesmanager.test.shop.uat;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import java.util.UUID;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.test.context.junit4.SpringRunner;

import com.fasterxml.jackson.databind.JsonNode;
import com.salesmanager.shop.model.catalog.product.ReadableProduct;
import com.salesmanager.shop.model.customer.PersistableCustomer;
import com.salesmanager.shop.model.customer.address.Address;
import com.salesmanager.shop.model.shoppingcart.PersistableShoppingCartItem;
import com.salesmanager.shop.model.shoppingcart.ReadableShoppingCart;
import com.salesmanager.shop.store.security.AuthenticationRequest;
import com.salesmanager.shop.store.security.AuthenticationResponse;
import com.salesmanager.test.shop.common.ServicesTestSupport;

@RunWith(SpringRunner.class)
public class StorefrontSmokeJourneyTest extends ServicesTestSupport {

	@Test
	public void customerRegistersLogsInAndReadsAuthenticatedProfile() {
		String email = "uat-" + uniqueCode() + "@example.test";
		PersistableCustomer customer = new PersistableCustomer();
		customer.setEmailAddress(email);
		customer.setPassword("clear123");
		customer.setGender("M");
		customer.setLanguage("en");
		customer.setStoreCode("DEFAULT");
		Address billing = new Address();
		billing.setFirstName("Acceptance");
		billing.setLastName("Customer");
		billing.setCountry("BE");
		customer.setBilling(billing);
		assertEquals(HttpStatus.OK, testRestTemplate.postForEntity("/api/v1/customer/register",
				new HttpEntity<>(customer), JsonNode.class).getStatusCode());
		ResponseEntity<AuthenticationResponse> login = testRestTemplate.postForEntity("/api/v1/customer/login",
				new AuthenticationRequest(email, "clear123"), AuthenticationResponse.class);
		assertEquals(HttpStatus.OK, login.getStatusCode());
		assertNotNull(login.getBody().getToken());
		HttpHeaders headers = new HttpHeaders();
		headers.setBearerAuth(login.getBody().getToken());
		ResponseEntity<JsonNode> profile = testRestTemplate.exchange("/api/v1/auth/customer/profile",
				HttpMethod.GET, new HttpEntity<>(headers), JsonNode.class);
		assertEquals(HttpStatus.OK, profile.getStatusCode());
		assertEquals(email, profile.getBody().path("emailAddress").asText());
	}

	@Test
	public void shopperBrowsesStoreCategoriesProductsAndProductDetail() {
		ReadableProduct product = sampleProduct(uniqueCode());
		for (String url : new String[] {"/api/v1/store/DEFAULT", "/api/v1/category?store=DEFAULT",
				"/api/v1/products?store=DEFAULT", "/api/v2/product/" + product.getSku()}) {
			ResponseEntity<JsonNode> response = testRestTemplate.getForEntity(url, JsonNode.class);
			assertEquals(url, HttpStatus.OK, response.getStatusCode());
			assertNotNull(response.getBody());
		}
	}

	@Test
	public void shopperCreatesUpdatesAndReadsCart() {
		ReadableProduct product = sampleProduct(uniqueCode());
		PersistableShoppingCartItem item = new PersistableShoppingCartItem();
		item.setProduct(product.getSku());
		item.setQuantity(1);
		ResponseEntity<ReadableShoppingCart> created = testRestTemplate.postForEntity("/api/v1/cart",
				item, ReadableShoppingCart.class);
		assertEquals(HttpStatus.CREATED, created.getStatusCode());
		assertEquals(1, created.getBody().getQuantity());
		String url = "/api/v1/cart/" + created.getBody().getCode();
		item.setQuantity(2);
		ResponseEntity<ReadableShoppingCart> updated = testRestTemplate.exchange(url, HttpMethod.PUT,
				new HttpEntity<>(item), ReadableShoppingCart.class);
		assertEquals(HttpStatus.CREATED, updated.getStatusCode());
		assertEquals(2, updated.getBody().getQuantity());
		ResponseEntity<ReadableShoppingCart> read = testRestTemplate.getForEntity(url, ReadableShoppingCart.class);
		assertEquals(HttpStatus.OK, read.getStatusCode());
		assertEquals(2, read.getBody().getQuantity());
		assertEquals(created.getBody().getCode(), read.getBody().getCode());
		assertEquals(1, read.getBody().getProducts().size());
	}

	@Test
	public void legacyServicesAcceptValidBasicAuthAndRejectInvalidCredentials() {
		String url = "/services/private/system/optin";
		ResponseEntity<JsonNode> valid = testRestTemplate.withBasicAuth("admin@shopizer.com", "password")
				.postForEntity(url, "{}", JsonNode.class);
		assertEquals(HttpStatus.OK, valid.getStatusCode());
		assertEquals(200, valid.getBody().path("status").asInt());
		ResponseEntity<String> invalid = testRestTemplate.withBasicAuth("admin@shopizer.com", "invalid")
				.postForEntity(url, "{}", String.class);
		assertTrue(invalid.getStatusCode() == HttpStatus.UNAUTHORIZED || invalid.getStatusCode() == HttpStatus.FORBIDDEN);
	}

	@Test
	public void healthAndSwaggerContractRemainAvailable() {
		ResponseEntity<JsonNode> health = testRestTemplate.getForEntity("/actuator/health", JsonNode.class);
		assertEquals(HttpStatus.OK, health.getStatusCode());
		assertEquals("UP", health.getBody().path("status").asText());
		ResponseEntity<JsonNode> docs = testRestTemplate.getForEntity("/v2/api-docs", JsonNode.class);
		assertEquals(HttpStatus.OK, docs.getStatusCode());
		assertEquals("2.0", docs.getBody().path("swagger").asText());
		assertTrue(docs.getBody().path("paths").has("/api/v1/private/tax/rate"));
	}

	private String uniqueCode() {
		return "uat" + UUID.randomUUID().toString().replace("-", "").substring(0, 12);
	}
}
