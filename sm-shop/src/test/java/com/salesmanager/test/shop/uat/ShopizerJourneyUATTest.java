package com.salesmanager.test.shop.uat;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;
import static org.junit.Assume.assumeTrue;

import java.util.UUID;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.salesmanager.core.business.constants.Constants;
import com.salesmanager.core.business.services.user.UserService;
import com.salesmanager.core.model.common.CredentialsReset;
import com.salesmanager.core.model.user.User;
import com.salesmanager.shop.application.ShopApplication;
import com.salesmanager.shop.model.customer.PersistableCustomer;
import com.salesmanager.shop.model.customer.address.Address;
import com.salesmanager.shop.model.shoppingcart.PersistableShoppingCartItem;
import com.salesmanager.shop.model.shoppingcart.ReadableShoppingCart;
import com.salesmanager.shop.store.security.AuthenticationRequest;
import com.salesmanager.shop.store.security.AuthenticationResponse;
import com.salesmanager.shop.store.security.PasswordRequest;
import com.salesmanager.shop.store.security.ResetPasswordRequest;
import com.salesmanager.test.shop.common.ServicesTestSupport;
import javax.inject.Inject;
import org.junit.After;
import org.junit.Before;
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

@SpringBootTest(classes = ShopApplication.class, webEnvironment = WebEnvironment.RANDOM_PORT)
@RunWith(SpringRunner.class)
public class ShopizerJourneyUATTest extends ServicesTestSupport {

	private static final String ADMIN_USERNAME = "admin@shopizer.com";
	private static final String RESET_REQUEST_PATH = "/api/v1/user/password/reset/request?store=DEFAULT";
	private static final String STORE_RETURN_URL = "http://localhost:8080/admin";
	private static final String NEW_PASSWORD = "UatResetPassw0rd!";

	@Inject
	private UserService userService;

	@Before
	public void clearAdminResetTokenBeforeTest() throws Exception {
		clearAdminResetToken();
	}

	@After
	public void clearAdminResetTokenAfterTest() throws Exception {
		clearAdminResetToken();
	}

	@Test
	public void adminLoginProtectsPrivateEndpoint() {
		ResponseEntity<AuthenticationResponse> loginResponse = testRestTemplate.postForEntity(
				"/api/v1/private/login", new AuthenticationRequest("admin@shopizer.com", "password"),
				AuthenticationResponse.class);
		assertEquals(200, loginResponse.getStatusCodeValue());
		assertNotNull(loginResponse.getBody());

		String privatePath = "/api/v1/private/customers?store=DEFAULT";
		HttpHeaders tokenHeaders = new HttpHeaders();
		tokenHeaders.setBearerAuth(loginResponse.getBody().getToken());
		ResponseEntity<String> authorized = testRestTemplate.exchange(privatePath, HttpMethod.GET,
				new HttpEntity<>(tokenHeaders), String.class);
		assertEquals(200, authorized.getStatusCodeValue());

		ResponseEntity<String> noToken = testRestTemplate.getForEntity(privatePath, String.class);
		assertTrue(noToken.getStatusCode().is4xxClientError());
	}

	@Test
	public void passwordResetRejectsAttackerAndRestoresAfterLegitimateChange() throws Exception {
		ResponseEntity<String> attackerReset = requestReset("https://attacker.example/steal");
		assertEquals(400, attackerReset.getStatusCodeValue());
		JsonNode attackerError = new ObjectMapper().readTree(attackerReset.getBody());
		assertEquals("400", attackerError.path("errorCode").asText());
		assertTrue(attackerError.path("message").asText().contains("is not allowed"));
		assertNoStoredResetToken();
		assertTrue(login("password").getStatusCode().is2xxSuccessful());

		boolean passwordChanged = false;
		try {
			String token = requestResetAndReadToken(STORE_RETURN_URL);
			ResponseEntity<Void> verified = testRestTemplate.getForEntity(
					"/api/v1/user/DEFAULT/reset/" + token, Void.class);
			assertEquals(200, verified.getStatusCodeValue());

			ResponseEntity<Void> changed = testRestTemplate.postForEntity(
					"/api/v1/user/DEFAULT/password/" + token, passwordRequest(NEW_PASSWORD), Void.class);
			passwordChanged = changed.getStatusCode().is2xxSuccessful();
			assertEquals(200, changed.getStatusCodeValue());

			assertTrue(login(NEW_PASSWORD).getStatusCode().is2xxSuccessful());
			assertFalse(login("password").getStatusCode().is2xxSuccessful());

			ResponseEntity<Void> reusedVerify = testRestTemplate.getForEntity(
					"/api/v1/user/DEFAULT/reset/" + token, Void.class);
			assertFalse(reusedVerify.getStatusCode().is2xxSuccessful());
			ResponseEntity<Void> reusedChange = testRestTemplate.postForEntity(
					"/api/v1/user/DEFAULT/password/" + token,
					passwordRequest("AnotherUatPassw0rd!"), Void.class);
			assertFalse(reusedChange.getStatusCode().is2xxSuccessful());
		} finally {
			if (passwordChanged) {
				String restoreToken = requestResetAndReadToken(STORE_RETURN_URL);
				ResponseEntity<Void> restored = testRestTemplate.postForEntity(
						"/api/v1/user/DEFAULT/password/" + restoreToken, passwordRequest("password"), Void.class);
				assertTrue(restored.getStatusCode().is2xxSuccessful());
			}
		}
		assertTrue(login("password").getStatusCode().is2xxSuccessful());
	}

	@Test
	public void customerCanRegisterLoginAndReadProfile() {
		PersistableCustomer customer = new PersistableCustomer();
		String email = "uat-" + UUID.randomUUID() + "@example.com";
		customer.setEmailAddress(email);
		customer.setPassword("UatPassw0rd!");
		customer.setGender("M");
		customer.setLanguage("en");
		customer.setStoreCode(Constants.DEFAULT_STORE);
		Address billing = new Address();
		billing.setFirstName("UAT");
		billing.setLastName("Customer");
		billing.setCountry("BE");
		customer.setBilling(billing);

		ResponseEntity<String> registration = testRestTemplate.postForEntity(
				"/api/v1/customer/register", new HttpEntity<>(customer, getHeader()), String.class);
		assertTrue(registration.getStatusCode().is2xxSuccessful());

		ResponseEntity<AuthenticationResponse> login = testRestTemplate.postForEntity("/api/v1/customer/login",
				new AuthenticationRequest(email, "UatPassw0rd!"), AuthenticationResponse.class);
		assertEquals(200, login.getStatusCodeValue());
		assertNotNull(login.getBody());
		assertNotNull(login.getBody().getToken());

		HttpHeaders headers = new HttpHeaders();
		headers.setBearerAuth(login.getBody().getToken());
		ResponseEntity<String> profile = testRestTemplate.exchange(
				"/api/v1/auth/customer/profile?store=DEFAULT", HttpMethod.GET,
				new HttpEntity<>(headers), String.class);
		assertEquals(200, profile.getStatusCodeValue());
		assertTrue(profile.getBody().contains(email));
	}

	@Test
	public void anonymousBrowsingReachesStoreCategoriesAndProducts() {
		ResponseEntity<JsonNode> store = testRestTemplate.getForEntity("/api/v1/store/DEFAULT", JsonNode.class);
		assertEquals(200, store.getStatusCodeValue());
		assertEquals("DEFAULT", store.getBody().path("code").asText());

		ResponseEntity<JsonNode> categories = testRestTemplate.getForEntity(
				"/api/v1/category?store=DEFAULT", JsonNode.class);
		assertEquals(200, categories.getStatusCodeValue());
		assertNotNull(categories.getBody());

		ResponseEntity<JsonNode> products = testRestTemplate.getForEntity(
				"/api/v1/products?store=DEFAULT", JsonNode.class);
		assertEquals(200, products.getStatusCodeValue());
		JsonNode productList = products.getBody().path("products");
		assertTrue(productList.isArray());
	}

	@Test
	public void anonymousBrowsingReachesProductDetail() {
		ResponseEntity<JsonNode> products = testRestTemplate.getForEntity(
				"/api/v1/products?store=DEFAULT", JsonNode.class);
		assertEquals(200, products.getStatusCodeValue());
		JsonNode productList = products.getBody().path("products");
		assumeTrue("Seeded store has no product; product detail and cart were not run", !productList.isEmpty());

		JsonNode product = null;
		for (JsonNode listedProduct : productList) {
			if (listedProduct.path("description").hasNonNull("friendlyUrl")) {
				product = listedProduct;
				break;
			}
		}
		assumeTrue("Seeded products have no friendly URL; product detail was not run", product != null);
		assertTrue(product.hasNonNull("id"));
		assertTrue(product.hasNonNull("sku"));

		ResponseEntity<JsonNode> detail = testRestTemplate.getForEntity(
				"/api/v1/product/" + product.path("description").path("friendlyUrl").asText()
						+ "?store=DEFAULT", JsonNode.class);
		assertEquals(200, detail.getStatusCodeValue());
		assertTrue(detail.getBody().hasNonNull("sku"));
	}

	@Test
	public void cartOperationsPreserveQuantities() {
		ResponseEntity<JsonNode> products = testRestTemplate.getForEntity(
				"/api/v1/products?store=DEFAULT", JsonNode.class);
		assertEquals(200, products.getStatusCodeValue());
		JsonNode productList = products.getBody().path("products");
		assumeTrue("Seeded store has no product; cart operations were not run", !productList.isEmpty());
		JsonNode product = productList.get(0);
		assertTrue(product.hasNonNull("sku"));
		PersistableShoppingCartItem cartItem = new PersistableShoppingCartItem();
		cartItem.setProduct(product.path("sku").asText());
		cartItem.setQuantity(2);
		ResponseEntity<ReadableShoppingCart> created = testRestTemplate.postForEntity(
				"/api/v1/cart?store=DEFAULT", cartItem, ReadableShoppingCart.class);
		assertEquals(201, created.getStatusCodeValue());
		assertNotNull(created.getBody());
		assertEquals(2, created.getBody().getQuantity());

		cartItem.setQuantity(3);
		ResponseEntity<ReadableShoppingCart> updated = testRestTemplate.exchange(
				"/api/v1/cart/" + created.getBody().getCode() + "?store=DEFAULT", HttpMethod.PUT,
				new HttpEntity<>(cartItem), ReadableShoppingCart.class);
		assertEquals(201, updated.getStatusCodeValue());
		assertEquals(3, updated.getBody().getQuantity());

		ResponseEntity<ReadableShoppingCart> readBack = testRestTemplate.getForEntity(
				"/api/v1/cart/" + created.getBody().getCode() + "?store=DEFAULT", ReadableShoppingCart.class);
		assertEquals(200, readBack.getStatusCodeValue());
		assertEquals(3, readBack.getBody().getQuantity());
	}

	@Test
	public void basicServicesAuthenticationActuatorAndSwaggerAreReachable() {
		HttpHeaders validBasicAuth = new HttpHeaders();
		validBasicAuth.setBasicAuth("admin@shopizer.com", "password");
		validBasicAuth.setContentType(MediaType.APPLICATION_JSON);
		ResponseEntity<String> authorized = testRestTemplate.exchange(
				"/services/private/system/optin", HttpMethod.POST,
				new HttpEntity<>("\"uat\"", validBasicAuth), String.class);
		assertEquals(200, authorized.getStatusCodeValue());

		HttpHeaders invalidBasicAuth = new HttpHeaders();
		invalidBasicAuth.setBasicAuth("admin@shopizer.com", "bad-password");
		invalidBasicAuth.setContentType(MediaType.APPLICATION_JSON);
		ResponseEntity<String> unauthorized = testRestTemplate.exchange(
				"/services/private/system/optin", HttpMethod.POST,
				new HttpEntity<>("\"uat\"", invalidBasicAuth), String.class);
		assertTrue(unauthorized.getStatusCode().is4xxClientError());

		ResponseEntity<JsonNode> health = testRestTemplate.getForEntity("/actuator/health", JsonNode.class);
		assertEquals(200, health.getStatusCodeValue());
		assertEquals("UP", health.getBody().path("status").asText());

		ResponseEntity<JsonNode> swagger = testRestTemplate.getForEntity("/v2/api-docs", JsonNode.class);
		assertEquals(200, swagger.getStatusCodeValue());
		assertTrue(swagger.getBody().hasNonNull("swagger"));
	}

	private ResponseEntity<String> requestReset(String returnUrl) {
		ResetPasswordRequest request = new ResetPasswordRequest();
		request.setUsername(ADMIN_USERNAME);
		request.setReturnUrl(returnUrl);
		return testRestTemplate.postForEntity(RESET_REQUEST_PATH, request, String.class);
	}

	private String requestResetAndReadToken(String returnUrl) throws Exception {
		String previousToken = storedResetToken();
		ResponseEntity<String> response = requestReset(returnUrl);
		String token = storedResetToken();
		assertNotNull("Expected reset token for " + returnUrl, token);
		assertFalse("Expected a new reset token for " + returnUrl, previousToken != null
				&& previousToken.equals(token));
		if (!response.getStatusCode().is2xxSuccessful()) {
			JsonNode error = new ObjectMapper().readTree(response.getBody());
			assertFalse("Expected URL validation to pass for " + returnUrl,
					error.path("message").asText().contains("is not allowed"));
		}
		return token;
	}

	private ResponseEntity<AuthenticationResponse> login(String password) {
		return testRestTemplate.postForEntity("/api/v1/private/login",
				new AuthenticationRequest(ADMIN_USERNAME, password), AuthenticationResponse.class);
	}

	private static PasswordRequest passwordRequest(String password) {
		PasswordRequest request = new PasswordRequest();
		request.setUsername(ADMIN_USERNAME);
		request.setPassword(password);
		request.setRepeatPassword(password);
		return request;
	}

	private void clearAdminResetToken() throws Exception {
		User admin = userService.getByUserName(ADMIN_USERNAME, Constants.DEFAULT_STORE);
		assertNotNull(admin);
		if (admin.getCredentialsResetRequest() != null) {
			admin.setCredentialsResetRequest(null);
			userService.saveOrUpdate(admin);
		}
		assertNoStoredResetToken();
	}

	private String storedResetToken() throws Exception {
		User admin = userService.getByUserName(ADMIN_USERNAME, Constants.DEFAULT_STORE);
		assertNotNull(admin);
		CredentialsReset credentialsReset = admin.getCredentialsResetRequest();
		return credentialsReset == null ? null : credentialsReset.getCredentialsRequest();
	}

	private void assertNoStoredResetToken() throws Exception {
		assertTrue(storedResetToken() == null);
	}
}
