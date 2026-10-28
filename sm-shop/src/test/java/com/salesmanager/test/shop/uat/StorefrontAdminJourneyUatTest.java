package com.salesmanager.test.shop.uat;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;
import static org.springframework.http.HttpMethod.GET;
import static org.springframework.http.HttpMethod.POST;
import static org.springframework.http.HttpStatus.OK;

import java.util.UUID;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.SpringBootTest.WebEnvironment;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.test.context.junit4.SpringRunner;

import com.fasterxml.jackson.databind.JsonNode;
import com.salesmanager.core.business.constants.Constants;
import com.salesmanager.core.model.customer.CustomerGender;
import com.salesmanager.shop.application.ShopApplication;
import com.salesmanager.shop.model.catalog.product.ReadableProduct;
import com.salesmanager.shop.model.customer.PersistableCustomer;
import com.salesmanager.shop.model.customer.address.Address;
import com.salesmanager.shop.model.shoppingcart.PersistableShoppingCartItem;
import com.salesmanager.shop.model.shoppingcart.ReadableShoppingCart;
import com.salesmanager.shop.store.security.AuthenticationRequest;
import com.salesmanager.shop.store.security.AuthenticationResponse;
import com.salesmanager.test.shop.common.ServicesTestSupport;

@SpringBootTest(classes = ShopApplication.class, webEnvironment = WebEnvironment.RANDOM_PORT)
@RunWith(SpringRunner.class)
public class StorefrontAdminJourneyUatTest extends ServicesTestSupport {

	@Test
	public void administratorCanAccessPrivateOrdersButAnonymousAndInvalidTokensCannot() {
		ResponseEntity<AuthenticationResponse> login = testRestTemplate.postForEntity("/api/v1/private/login",
				new HttpEntity<>(new AuthenticationRequest("admin@shopizer.com", "password")),
				AuthenticationResponse.class);
		assertEquals(OK, login.getStatusCode());
		assertNotNull(login.getBody());
		assertNotNull(login.getBody().getToken());

		HttpHeaders authorized = bearerHeaders(login.getBody().getToken());
		ResponseEntity<JsonNode> orders = testRestTemplate.exchange(
				"/api/v1/private/orders?store=DEFAULT", GET, new HttpEntity<>(authorized), JsonNode.class);
		assertEquals(OK, orders.getStatusCode());

		ResponseEntity<String> anonymous = testRestTemplate.getForEntity(
				"/api/v1/private/orders?store=DEFAULT", String.class);
		assertNotSuccessful(anonymous.getStatusCode());

		HttpHeaders invalid = bearerHeaders("not-a-valid-token");
		ResponseEntity<String> badToken = testRestTemplate.exchange(
				"/api/v1/private/orders?store=DEFAULT", GET, new HttpEntity<>(invalid), String.class);
		assertNotSuccessful(badToken.getStatusCode());
	}

	@Test
	public void customerCanRegisterLoginAndReadProfile() {
		String suffix = UUID.randomUUID().toString().replace("-", "");
		String email = "uat-" + suffix + "@example.test";
		PersistableCustomer customer = new PersistableCustomer();
		customer.setEmailAddress(email);
		customer.setPassword("Clear123!");
		customer.setGender(CustomerGender.M.name());
		customer.setLanguage("en");
		customer.setStoreCode(Constants.DEFAULT_STORE);
		Address billing = new Address();
		billing.setFirstName("Uat");
		billing.setLastName(suffix);
		billing.setCountry("US");
		customer.setBilling(billing);

		ResponseEntity<PersistableCustomer> registration = testRestTemplate.postForEntity(
				"/api/v1/customer/register", new HttpEntity<>(customer), PersistableCustomer.class);
		assertTrue(registration.getStatusCode().is2xxSuccessful());

		ResponseEntity<AuthenticationResponse> login = testRestTemplate.postForEntity(
				"/api/v1/customer/login",
				new HttpEntity<>(new AuthenticationRequest(email, "Clear123!")), AuthenticationResponse.class);
		assertEquals(OK, login.getStatusCode());
		assertNotNull(login.getBody());
		assertNotNull(login.getBody().getToken());

		ResponseEntity<JsonNode> profile = testRestTemplate.exchange(
				"/api/v1/auth/customer/profile?store=DEFAULT", GET,
				new HttpEntity<>(bearerHeaders(login.getBody().getToken())), JsonNode.class);
		assertEquals(OK, profile.getStatusCode());
		assertEquals(email, profile.getBody().path("emailAddress").asText());
	}

	@Test
	public void storefrontCatalogAndCartJourneysWork() {
		assertEquals(OK, testRestTemplate.getForEntity(
				"/api/v1/store/DEFAULT?lang=en", String.class).getStatusCode());
		assertEquals(OK, testRestTemplate.getForEntity(
				"/api/v1/category?store=DEFAULT&lang=en", String.class).getStatusCode());

		ReadableProduct product = sampleProduct("uat" + UUID.randomUUID().toString().replace("-", ""));
		ResponseEntity<String> products = testRestTemplate.getForEntity(
				"/api/v2/products?store=DEFAULT&lang=en", String.class);
		assertEquals(OK, products.getStatusCode());
		assertTrue(products.getBody().contains(product.getSku()));

		ResponseEntity<ReadableProduct> detail = testRestTemplate.getForEntity(
				"/api/v2/product/" + product.getSku() + "?store=DEFAULT&lang=en", ReadableProduct.class);
		assertEquals(OK, detail.getStatusCode());
		assertEquals(product.getSku(), detail.getBody().getSku());

		PersistableShoppingCartItem item = new PersistableShoppingCartItem();
		item.setProduct(product.getSku());
		item.setQuantity(1);
		ResponseEntity<ReadableShoppingCart> created = testRestTemplate.postForEntity(
				"/api/v1/cart/?store=DEFAULT", new HttpEntity<>(item), ReadableShoppingCart.class);
		assertEquals(HttpStatus.CREATED, created.getStatusCode());
		assertNotNull(created.getBody());
		ResponseEntity<ReadableShoppingCart> read = testRestTemplate.getForEntity(
				"/api/v1/cart/" + created.getBody().getCode() + "?store=DEFAULT", ReadableShoppingCart.class);
		assertEquals(OK, read.getStatusCode());
	}

	@Test
	public void actuatorSwaggerAndServicesSecurityAreAvailable() {
		ResponseEntity<JsonNode> health = testRestTemplate.getForEntity("/actuator/health", JsonNode.class);
		assertEquals(OK, health.getStatusCode());
		assertEquals("UP", health.getBody().path("status").asText());

		ResponseEntity<String> apiDocs = testRestTemplate.getForEntity("/v2/api-docs", String.class);
		assertEquals(OK, apiDocs.getStatusCode());
		assertTrue(apiDocs.getBody().contains("swagger"));

		HttpHeaders validBasicAuth = new HttpHeaders();
		validBasicAuth.setBasicAuth("admin@shopizer.com", "password");
		validBasicAuth.setContentType(MediaType.TEXT_PLAIN);
		ResponseEntity<String> authorized = testRestTemplate.exchange(
				"/services/private/system/optin", POST,
				new HttpEntity<>("{}", validBasicAuth), String.class);
		assertEquals(OK, authorized.getStatusCode());

		HttpHeaders invalidBasicAuth = new HttpHeaders();
		invalidBasicAuth.setBasicAuth("invalid", "invalid");
		invalidBasicAuth.setContentType(MediaType.TEXT_PLAIN);
		ResponseEntity<String> rejected = testRestTemplate.exchange(
				"/services/private/system/optin", POST,
				new HttpEntity<>("{}", invalidBasicAuth), String.class);
		assertAuthenticationRejected(rejected.getStatusCode());
	}

	private static HttpHeaders bearerHeaders(String token) {
		HttpHeaders headers = new HttpHeaders();
		headers.setBearerAuth(token);
		return headers;
	}

	private static void assertAuthenticationRejected(HttpStatus status) {
		assertTrue(status.toString(), status == HttpStatus.UNAUTHORIZED || status == HttpStatus.FORBIDDEN);
	}

	private static void assertNotSuccessful(HttpStatus status) {
		assertTrue(status.toString(), !status.is2xxSuccessful());
	}

}
