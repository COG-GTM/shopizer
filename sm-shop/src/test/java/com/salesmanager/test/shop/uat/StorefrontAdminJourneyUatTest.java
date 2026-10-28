package com.salesmanager.test.shop.uat;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.charset.StandardCharsets;
import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;

import com.salesmanager.core.business.constants.Constants;
import com.salesmanager.shop.model.catalog.product.ReadableProduct;
import com.salesmanager.shop.model.customer.PersistableCustomer;
import com.salesmanager.shop.model.customer.address.Address;
import com.salesmanager.shop.model.shoppingcart.PersistableShoppingCartItem;
import com.salesmanager.shop.model.shoppingcart.ReadableShoppingCart;
import com.salesmanager.shop.store.security.AuthenticationRequest;
import com.salesmanager.shop.store.security.AuthenticationResponse;
import com.salesmanager.test.shop.common.ServicesTestSupport;

public class StorefrontAdminJourneyUatTest extends ServicesTestSupport {

	private static final String CUSTOMER_PASSWORD = "CustomerPassword1!";

	@Test
	public void adminAndCustomerAuthenticationEnforceTheirApiScopes() {
		HttpHeaders adminHeaders = getHeader();
		assertEquals(HttpStatus.OK, testRestTemplate.exchange("/api/v1/private/customers", HttpMethod.GET,
				new HttpEntity<>(adminHeaders), String.class).getStatusCode());

		ResponseEntity<String> noToken = testRestTemplate.getForEntity("/api/v1/private/customers", String.class);
		assertTrue(isUnauthorized(noToken.getStatusCode()));
		HttpHeaders badTokenHeaders = new HttpHeaders();
		badTokenHeaders.setBearerAuth("not-a-valid-token");
		ResponseEntity<String> badToken = testRestTemplate.exchange("/api/v1/private/customers", HttpMethod.GET,
				new HttpEntity<>(badTokenHeaders), String.class);
		assertFalse(badToken.getStatusCode().is2xxSuccessful());

		PersistableCustomer customer = newCustomer();
		ResponseEntity<AuthenticationResponse> registration = testRestTemplate.postForEntity(
				"/api/v1/customer/register?store=" + Constants.DEFAULT_STORE,
				new HttpEntity<>(customer), AuthenticationResponse.class);
		assertTrue(registration.getStatusCode().is2xxSuccessful());
		assertNotNull(registration.getBody());
		assertNotNull(registration.getBody().getToken());

		ResponseEntity<AuthenticationResponse> login = testRestTemplate.postForEntity("/api/v1/customer/login",
				new HttpEntity<>(new AuthenticationRequest(customer.getEmailAddress(), CUSTOMER_PASSWORD)),
				AuthenticationResponse.class);
		assertEquals(HttpStatus.OK, login.getStatusCode());
		assertNotNull(login.getBody());
		assertNotNull(login.getBody().getToken());

		HttpHeaders customerHeaders = bearerHeaders(login.getBody().getToken());
		ResponseEntity<String> profile = testRestTemplate.exchange("/api/v1/auth/customer/profile", HttpMethod.GET,
				new HttpEntity<>(customerHeaders), String.class);
		assertEquals(HttpStatus.OK, profile.getStatusCode());
	}

	@Test
	public void storefrontBrowseAndCartCanBeCompleted() {
		assertEquals(HttpStatus.OK, testRestTemplate.getForEntity("/api/v1/store/DEFAULT", String.class).getStatusCode());
		assertEquals(HttpStatus.OK,
				testRestTemplate.getForEntity("/api/v1/category?store=DEFAULT", String.class).getStatusCode());
		assertEquals(HttpStatus.OK,
				testRestTemplate.getForEntity("/api/v1/products?store=DEFAULT", String.class).getStatusCode());

		String sku = "journey" + UUID.randomUUID().toString().replace("-", "").substring(0, 12);
		ReadableProduct product = sampleProduct(sku);
		assertNotNull(product);
		ResponseEntity<ReadableProduct> productDetails = testRestTemplate.getForEntity("/api/v2/product/" + sku,
				ReadableProduct.class);
		assertEquals(HttpStatus.OK, productDetails.getStatusCode());
		assertNotNull(productDetails.getBody());

		PersistableShoppingCartItem item = new PersistableShoppingCartItem();
		item.setProduct(sku);
		item.setQuantity(1);
		ResponseEntity<ReadableShoppingCart> created = testRestTemplate.postForEntity("/api/v1/cart",
				new HttpEntity<>(item), ReadableShoppingCart.class);
		assertEquals(HttpStatus.CREATED, created.getStatusCode());
		assertNotNull(created.getBody());
		assertNotNull(created.getBody().getCode());
		assertEquals(1, created.getBody().getQuantity());

		item.setQuantity(2);
		ResponseEntity<ReadableShoppingCart> updated = testRestTemplate.exchange(
				"/api/v1/cart/" + created.getBody().getCode(), HttpMethod.PUT, new HttpEntity<>(item),
				ReadableShoppingCart.class);
		assertEquals(HttpStatus.CREATED, updated.getStatusCode());
		assertEquals(2, updated.getBody().getQuantity());

		ResponseEntity<ReadableShoppingCart> readBack = testRestTemplate.getForEntity(
				"/api/v1/cart/" + created.getBody().getCode(), ReadableShoppingCart.class);
		assertEquals(HttpStatus.OK, readBack.getStatusCode());
		assertEquals(2, readBack.getBody().getQuantity());
	}

	@Test
	public void servicesRequireValidBasicAuthentication() {
		HttpHeaders validCredentials = new HttpHeaders();
		validCredentials.setBasicAuth("admin@shopizer.com", "password", StandardCharsets.UTF_8);
		validCredentials.setContentType(MediaType.TEXT_PLAIN);
		ResponseEntity<String> valid = testRestTemplate.exchange("/services/private/system/optin", HttpMethod.POST,
				new HttpEntity<>("{}", validCredentials), String.class);
		assertEquals(HttpStatus.OK, valid.getStatusCode());

		HttpHeaders invalidCredentials = new HttpHeaders();
		invalidCredentials.setBasicAuth("admin@shopizer.com", "wrong", StandardCharsets.UTF_8);
		invalidCredentials.setContentType(MediaType.TEXT_PLAIN);
		ResponseEntity<String> invalid = testRestTemplate.exchange("/services/private/system/optin", HttpMethod.POST,
				new HttpEntity<>("{}", invalidCredentials), String.class);
		assertTrue(isUnauthorized(invalid.getStatusCode()));
	}

	@Test
	public void healthAndApiDocumentationAreAvailable() {
		ResponseEntity<String> health = testRestTemplate.getForEntity("/actuator/health", String.class);
		assertEquals(HttpStatus.OK, health.getStatusCode());
		assertTrue(health.getBody().contains("\"status\":\"UP\""));

		assertEquals(HttpStatus.OK, testRestTemplate.getForEntity("/v2/api-docs", String.class).getStatusCode());
	}

	private PersistableCustomer newCustomer() {
		String email = "j-" + UUID.randomUUID().toString().replace("-", "").substring(0, 16) + "@example.com";
		Address billing = new Address();
		billing.setFirstName("Journey");
		billing.setLastName("Customer");
		billing.setAddress("121212 simple address");
		billing.setCity("FT LD");
		billing.setStateProvince("FL");
		billing.setPostalCode("12345");
		billing.setCountry("US");
		billing.setPhone("444-555-6666");
		billing.setBillingAddress(true);

		PersistableCustomer customer = new PersistableCustomer();
		customer.setEmailAddress(email);
		customer.setFirstName("Journey");
		customer.setLastName("Customer");
		customer.setPassword(CUSTOMER_PASSWORD);
		customer.setRepeatPassword(CUSTOMER_PASSWORD);
		customer.setStoreCode(Constants.DEFAULT_STORE);
		customer.setLanguage("en");
		customer.setBilling(billing);
		return customer;
	}

	private HttpHeaders bearerHeaders(String token) {
		HttpHeaders headers = new HttpHeaders();
		headers.setContentType(new MediaType("application", "json", StandardCharsets.UTF_8));
		headers.setBearerAuth(token);
		return headers;
	}

	private boolean isUnauthorized(HttpStatus status) {
		return status == HttpStatus.UNAUTHORIZED || status == HttpStatus.FORBIDDEN;
	}
}
