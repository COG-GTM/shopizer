package com.salesmanager.test.shop.uat;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import java.util.UUID;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.SpringBootTest.WebEnvironment;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.test.context.junit4.SpringRunner;

import com.fasterxml.jackson.databind.JsonNode;
import com.salesmanager.core.business.constants.Constants;
import com.salesmanager.core.model.customer.CustomerGender;
import com.salesmanager.shop.application.ShopApplication;
import com.salesmanager.shop.model.catalog.product.ReadableProduct;
import com.salesmanager.shop.model.catalog.product.ReadableProductList;
import com.salesmanager.shop.model.customer.PersistableCustomer;
import com.salesmanager.shop.model.customer.ReadableCustomer;
import com.salesmanager.shop.model.customer.address.Address;
import com.salesmanager.shop.model.security.PersistableGroup;
import com.salesmanager.shop.model.shoppingcart.PersistableShoppingCartItem;
import com.salesmanager.shop.model.shoppingcart.ReadableShoppingCart;
import com.salesmanager.shop.model.store.ReadableMerchantStore;
import com.salesmanager.shop.model.user.PersistableUser;
import com.salesmanager.shop.model.user.ReadableUser;
import com.salesmanager.shop.store.security.AuthenticationRequest;
import com.salesmanager.shop.store.security.AuthenticationResponse;
import com.salesmanager.test.shop.common.ServicesTestSupport;

@SpringBootTest(classes = ShopApplication.class, webEnvironment = WebEnvironment.RANDOM_PORT)
@RunWith(SpringRunner.class)
public class StorefrontAndAdminJourneyUatTest extends ServicesTestSupport {

	private static final String PASSWORD = "Password1";

	@Test
	public void adminLogin() {
		ResponseEntity<AuthenticationResponse> login = testRestTemplate.postForEntity("/api/v1/private/login",
				new HttpEntity<>(new AuthenticationRequest("admin@shopizer.com", "password")),
				AuthenticationResponse.class);
		assertThatStatus(login.getStatusCode(), HttpStatus.OK);
		assertNotNull(login.getBody());

		HttpHeaders authenticated = bearer(login.getBody().getToken());
		ResponseEntity<String> withToken = testRestTemplate.exchange("/api/v1/private/users?store=DEFAULT",
				HttpMethod.GET, new HttpEntity<>(authenticated), String.class);
		assertThatStatus(withToken.getStatusCode(), HttpStatus.OK);

		ResponseEntity<String> anonymous = testRestTemplate.getForEntity("/api/v1/private/users?store=DEFAULT",
				String.class);
		assertThatStatus(anonymous.getStatusCode(), HttpStatus.UNAUTHORIZED);

	}

	@Test
	public void adminUserLifecycle() {
		String email = "uat-admin-" + UUID.randomUUID() + "@example.com";
		PersistableUser user = new PersistableUser();
		user.setDefaultLanguage("en");
		user.setEmailAddress(email);
		user.setFirstName("UAT");
		user.setLastName("Admin");
		user.setUserName(email);
		user.setPassword(PASSWORD);
		user.setRepeatPassword(PASSWORD);
		user.setActive(true);
		PersistableGroup group = new PersistableGroup();
		group.setName("ADMIN");
		user.getGroups().add(group);

		HttpHeaders superadmin = getHeader();
		ResponseEntity<ReadableUser> created = testRestTemplate.exchange(
				"/api/v1/private/user/?store=DEFAULT", HttpMethod.POST, new HttpEntity<>(user, superadmin),
				ReadableUser.class);
		assertThatStatus(created.getStatusCode(), HttpStatus.OK);
		assertNotNull(created.getBody());
		Long id = created.getBody().getId();

		assertThatStatus(setEnabled(id, false, superadmin), HttpStatus.OK);
		assertThatStatus(setEnabled(id, true, superadmin), HttpStatus.OK);
		assertThatStatus(testRestTemplate.exchange("/api/v1/private/user/" + id + "?store=DEFAULT",
				HttpMethod.DELETE, new HttpEntity<>(superadmin), String.class).getStatusCode(), HttpStatus.OK);

		ResponseEntity<String> afterDelete = testRestTemplate.exchange(
				"/api/v1/private/users/" + id + "?store=DEFAULT", HttpMethod.GET,
				new HttpEntity<>(superadmin), String.class);
		assertTrue(afterDelete.getStatusCode() != HttpStatus.OK);
	}

	@Test
	public void customerRegistrationLoginAndProfile() {
		String email = "uat-customer-" + UUID.randomUUID() + "@example.com";
		PersistableCustomer customer = new PersistableCustomer();
		customer.setEmailAddress(email);
		customer.setPassword("clear123");
		customer.setRepeatPassword("clear123");
		customer.setGender(CustomerGender.M.name());
		customer.setLanguage("en");
		customer.setStoreCode(Constants.DEFAULT_STORE);
		customer.setFirstName("UAT");
		customer.setLastName("Customer");
		Address billing = new Address();
		billing.setFirstName("UAT");
		billing.setLastName("Customer");
		billing.setCountry("BE");
		customer.setBilling(billing);

		ResponseEntity<String> registration = testRestTemplate.postForEntity("/api/v1/customer/register",
				new HttpEntity<>(customer), String.class);
		assertTrue("Unexpected registration status: " + registration.getStatusCode(),
				registration.getStatusCode().is2xxSuccessful());

		ResponseEntity<AuthenticationResponse> login = testRestTemplate.postForEntity("/api/v1/customer/login",
				new HttpEntity<>(new AuthenticationRequest(email, "clear123")), AuthenticationResponse.class);
		assertThatStatus(login.getStatusCode(), HttpStatus.OK);
		assertNotNull(login.getBody());

		ResponseEntity<ReadableCustomer> profile = testRestTemplate.exchange("/api/v1/auth/customer/profile",
				HttpMethod.GET, new HttpEntity<>(bearer(login.getBody().getToken())), ReadableCustomer.class);
		assertThatStatus(profile.getStatusCode(), HttpStatus.OK);
		assertEquals(email, profile.getBody().getEmailAddress());
	}

	@Test
	public void browseStoreCategoryAndProducts() {
		ResponseEntity<ReadableMerchantStore> store = testRestTemplate.getForEntity("/api/v1/store/DEFAULT",
				ReadableMerchantStore.class);
		assertThatStatus(store.getStatusCode(), HttpStatus.OK);
		assertEquals("DEFAULT", store.getBody().getCode());

		ResponseEntity<String> category = testRestTemplate.getForEntity("/api/v1/category?store=DEFAULT",
				String.class);
		assertThatStatus(category.getStatusCode(), HttpStatus.OK);

		String code = "uatBrowse-" + UUID.randomUUID();
		ReadableProduct product = sampleProduct(code);
		ResponseEntity<ReadableProductList> products = testRestTemplate.getForEntity(
				"/api/v1/products?store=DEFAULT", ReadableProductList.class);
		assertThatStatus(products.getStatusCode(), HttpStatus.OK);
		assertTrue(products.getBody().getProducts().stream().anyMatch(item -> code.equals(item.getSku())));

		ResponseEntity<ReadableProduct> detail = testRestTemplate.getForEntity(
				"/api/v2/product/" + product.getSku(), ReadableProduct.class);
		assertThatStatus(detail.getStatusCode(), HttpStatus.OK);
	}

	@Test
	public void cartCanHoldTwoProducts() {
		String suffix = UUID.randomUUID().toString();
		ReadableProduct first = sampleProduct("uatCartA-" + suffix);
		ReadableProduct second = sampleProduct("uatCartB-" + suffix);

		PersistableShoppingCartItem firstItem = cartItem(first.getSku());
		ResponseEntity<ReadableShoppingCart> created = testRestTemplate.postForEntity("/api/v1/cart/",
				new HttpEntity<>(firstItem), ReadableShoppingCart.class);
		assertThatStatus(created.getStatusCode(), HttpStatus.CREATED);
		assertNotNull(created.getBody());

		ResponseEntity<ReadableShoppingCart> updated = testRestTemplate.exchange(
				"/api/v1/cart/" + created.getBody().getCode(), HttpMethod.PUT,
				new HttpEntity<>(cartItem(second.getSku())), ReadableShoppingCart.class);
		assertTrue("Unexpected cart update status: " + updated.getStatusCode(),
				updated.getStatusCode().is2xxSuccessful());

		ResponseEntity<ReadableShoppingCart> retrieved = testRestTemplate.getForEntity(
				"/api/v1/cart/" + created.getBody().getCode(), ReadableShoppingCart.class);
		assertThatStatus(retrieved.getStatusCode(), HttpStatus.OK);
		assertTrue(retrieved.getBody().getProducts().stream().anyMatch(item -> first.getSku().equals(item.getSku())));
		assertTrue(retrieved.getBody().getProducts().stream().anyMatch(item -> second.getSku().equals(item.getSku())));
	}

	@Test
	public void servicesRequireBasicAuthenticationForPrivatePaths() {
		ResponseEntity<String> publicService = testRestTemplate.getForEntity("/services/public/DEFAULT", String.class);
		assertTrue("Unexpected public-service status: " + publicService.getStatusCode(),
				publicService.getStatusCode().is2xxSuccessful());

		String privatePath = "/services/private/system/optin";
		HttpHeaders privateHeaders = new HttpHeaders();
		ResponseEntity<String> anonymous = testRestTemplate.exchange(privatePath, HttpMethod.GET,
				new HttpEntity<>(privateHeaders), String.class);
		assertTrue("Unexpected anonymous-service status: " + anonymous.getStatusCode(),
				anonymous.getStatusCode() == HttpStatus.UNAUTHORIZED || anonymous.getStatusCode() == HttpStatus.FOUND);

		HttpHeaders invalidCredentials = new HttpHeaders();
		invalidCredentials.setBasicAuth("invalid", "invalid");
		ResponseEntity<String> invalid = testRestTemplate.exchange(privatePath, HttpMethod.GET,
				new HttpEntity<>(invalidCredentials), String.class);
		assertTrue("Unexpected invalid-credentials status: " + invalid.getStatusCode(),
				invalid.getStatusCode() == HttpStatus.UNAUTHORIZED || invalid.getStatusCode() == HttpStatus.FOUND);

		HttpHeaders adminCredentials = new HttpHeaders();
		adminCredentials.setBasicAuth("admin@shopizer.com", "password");
		ResponseEntity<String> admin = testRestTemplate.exchange(privatePath, HttpMethod.GET,
				new HttpEntity<>(adminCredentials), String.class);
		assertTrue("Admin was rejected by services endpoint: " + admin.getStatusCode(),
				admin.getStatusCode() != HttpStatus.UNAUTHORIZED);
	}

	@Test
	public void platformHealthAndSwaggerAreAvailable() {
		ResponseEntity<JsonNode> health = testRestTemplate.getForEntity("/actuator/health", JsonNode.class);
		assertThatStatus(health.getStatusCode(), HttpStatus.OK);
		assertEquals("UP", health.getBody().path("status").asText());

		ResponseEntity<JsonNode> swagger = testRestTemplate.getForEntity("/v2/api-docs", JsonNode.class);
		assertThatStatus(swagger.getStatusCode(), HttpStatus.OK);
		assertEquals("2.0", swagger.getBody().path("swagger").asText());
		assertTrue(swagger.getBody().path("paths").has("/api/v1/private/user/{id}"));
	}

	private HttpStatus setEnabled(Long id, boolean active, HttpHeaders headers) {
		PersistableUser user = new PersistableUser();
		user.setActive(active);
		return testRestTemplate.exchange("/api/v1/private/user/" + id + "/enabled?store=DEFAULT",
				HttpMethod.PATCH, new HttpEntity<>(user, headers), String.class).getStatusCode();
	}

	private static PersistableShoppingCartItem cartItem(String sku) {
		PersistableShoppingCartItem item = new PersistableShoppingCartItem();
		item.setProduct(sku);
		item.setQuantity(1);
		return item;
	}

	private static HttpHeaders bearer(String token) {
		HttpHeaders headers = new HttpHeaders();
		headers.add(HttpHeaders.AUTHORIZATION, "Bearer " + token);
		return headers;
	}

	private static void assertThatStatus(HttpStatus actual, HttpStatus expected) {
		assertEquals("Unexpected response status", expected, actual);
	}
}
