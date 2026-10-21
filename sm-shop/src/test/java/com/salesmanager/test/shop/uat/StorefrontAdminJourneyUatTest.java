package com.salesmanager.test.shop.uat;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import java.util.Arrays;
import java.util.Base64;
import java.util.concurrent.atomic.AtomicLong;

import org.junit.Test;
import org.junit.runner.RunWith;
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
import com.salesmanager.core.business.constants.Constants;
import com.salesmanager.core.model.customer.CustomerGender;
import com.salesmanager.shop.model.catalog.product.ReadableProduct;
import com.salesmanager.shop.model.catalog.product.ReadableProductList;
import com.salesmanager.shop.model.catalog.product.group.ProductGroup;
import com.salesmanager.shop.model.customer.PersistableCustomer;
import com.salesmanager.shop.model.customer.ReadableCustomer;
import com.salesmanager.shop.model.customer.address.Address;
import com.salesmanager.shop.model.security.PersistableGroup;
import com.salesmanager.shop.model.shoppingcart.PersistableShoppingCartItem;
import com.salesmanager.shop.model.shoppingcart.ReadableShoppingCart;
import com.salesmanager.shop.model.store.ReadableMerchantStore;
import com.salesmanager.shop.model.user.PersistableUser;
import com.salesmanager.shop.store.security.AuthenticationRequest;
import com.salesmanager.shop.store.security.AuthenticationResponse;
import com.salesmanager.test.shop.common.ServicesTestSupport;

@SpringBootTest(classes = com.salesmanager.shop.application.ShopApplication.class,
		webEnvironment = WebEnvironment.RANDOM_PORT)
@RunWith(SpringRunner.class)
public class StorefrontAdminJourneyUatTest extends ServicesTestSupport {

	private static final AtomicLong IDS = new AtomicLong();

	@Test
	public void adminLoginAndPrivateAuthorizationJourney() {
		ResponseEntity<AuthenticationResponse> login = testRestTemplate.postForEntity("/api/v1/private/login",
				new HttpEntity<>(new AuthenticationRequest("admin@shopizer.com", "password")),
				AuthenticationResponse.class);
		assertEquals(HttpStatus.OK, login.getStatusCode());
		assertNotNull(login.getBody());
		HttpHeaders adminHeaders = bearer(login.getBody().getToken());
		assertEquals(HttpStatus.OK, get("/api/v1/private/users/1", adminHeaders, String.class).getStatusCode());
		assertDenied(get("/api/v1/private/users/1", null, String.class));
	}

	@Test
	public void customerRegistrationAndAuthenticatedProfileJourney() {
		HttpHeaders customerHeaders = registerAndLoginCustomer();
		ResponseEntity<ReadableCustomer> profile = get("/api/v1/auth/customer/profile", customerHeaders,
				ReadableCustomer.class);
		assertEquals(HttpStatus.OK, profile.getStatusCode());
		assertNotNull(profile.getBody());
	}

	@Test
	public void browseStoreCategoriesProductsAndProductDetailJourney() {
		ResponseEntity<ReadableMerchantStore> store = get("/api/v1/store/DEFAULT", null, ReadableMerchantStore.class);
		assertEquals(HttpStatus.OK, store.getStatusCode());
		assertNotNull(store.getBody());
		assertEquals(HttpStatus.OK, get("/api/v1/category?store=DEFAULT", null, String.class).getStatusCode());
		ResponseEntity<ReadableProductList> products = get("/api/v1/products?store=DEFAULT", null,
				ReadableProductList.class);
		assertEquals(HttpStatus.OK, products.getStatusCode());
		assertNotNull(products.getBody());
		ReadableProduct product = sampleProduct(unique("browse"));
		assertNotNull(product.getId());
		ResponseEntity<ReadableProduct> detail = get("/api/v2/product/" + product.getSku(), null,
				ReadableProduct.class);
		assertEquals(HttpStatus.OK, detail.getStatusCode());
		assertNotNull(detail.getBody());
	}

	@Test
	public void addUpdateAndReadCartJourney() {
		ReadableProduct product = sampleProduct(unique("cart"));
		PersistableShoppingCartItem item = cartItem(product.getSku(), 1);
		ResponseEntity<ReadableShoppingCart> created = testRestTemplate.postForEntity("/api/v1/cart",
				new HttpEntity<>(item), ReadableShoppingCart.class);
		assertEquals(HttpStatus.CREATED, created.getStatusCode());
		assertNotNull(created.getBody());
		assertNotNull(created.getBody().getCode());

		ResponseEntity<ReadableShoppingCart> updated = testRestTemplate.postForEntity(
				"/api/v1/cart/" + created.getBody().getCode() + "/multi",
				new HttpEntity<>(new PersistableShoppingCartItem[] { cartItem(product.getSku(), 2) }),
				ReadableShoppingCart.class);
		assertEquals(HttpStatus.CREATED, updated.getStatusCode());
		ResponseEntity<ReadableShoppingCart> retrieved = get("/api/v1/cart/" + created.getBody().getCode(), null,
				ReadableShoppingCart.class);
		assertEquals(HttpStatus.OK, retrieved.getStatusCode());
		assertNotNull(retrieved.getBody());
		assertEquals(2, retrieved.getBody().getQuantity());
	}

	@Test
	public void legacyServicesBasicAuthenticationJourney() {
		HttpHeaders valid = basic("admin@shopizer.com", "password");
		ResponseEntity<String> accepted = testRestTemplate.postForEntity("/services/private/system/optin",
				new HttpEntity<>("{}", valid), String.class);
		assertEquals(HttpStatus.OK, accepted.getStatusCode());
		ResponseEntity<String> rejected = testRestTemplate.postForEntity("/services/private/system/optin",
				new HttpEntity<>("{}", basic("admin@shopizer.com", "invalid")), String.class);
		assertEquals(HttpStatus.UNAUTHORIZED, rejected.getStatusCode());
	}

	@Test
	public void healthAndApiDocumentationJourney() throws Exception {
		ResponseEntity<JsonNode> health = get("/actuator/health", null, JsonNode.class);
		assertEquals(HttpStatus.OK, health.getStatusCode());
		assertEquals("UP", health.getBody().path("status").asText());
		ResponseEntity<JsonNode> docs = get("/v2/api-docs", null, JsonNode.class);
		assertEquals(HttpStatus.OK, docs.getStatusCode());
		assertTrue(docs.getBody().path("paths").isObject());
	}

	@Test
	public void productGroupAdminDeletionJourney() {
		String code = unique("UATGROUP");
		ProductGroup group = new ProductGroup();
		group.setCode(code);
		group.setActive(true);
		HttpHeaders adminHeaders = getHeader();
		ResponseEntity<ProductGroup> created = testRestTemplate.postForEntity("/api/v1/private/products/group",
				new HttpEntity<>(group, adminHeaders), ProductGroup.class);
		assertEquals(HttpStatus.OK, created.getStatusCode());

		ReadableProduct product = sampleProduct(unique("groupitem"));
		ResponseEntity<ReadableProductList> attached = testRestTemplate.postForEntity(
				"/api/v1/private/products/" + product.getId() + "/group/" + code,
				new HttpEntity<>(null, adminHeaders), ReadableProductList.class);
		assertEquals(HttpStatus.CREATED, attached.getStatusCode());
		ResponseEntity<ReadableProductList> publicItems = get("/api/v1/products/group/" + code, null,
				ReadableProductList.class);
		assertEquals(HttpStatus.OK, publicItems.getStatusCode());
		assertTrue(publicItems.getBody().getProducts().stream()
				.anyMatch(item -> product.getSku().equals(item.getSku())));

		assertEquals(HttpStatus.METHOD_NOT_ALLOWED,
				testRestTemplate.exchange("/api/v1/products/group/" + code, HttpMethod.DELETE, null, String.class)
						.getStatusCode());
		assertDenied(testRestTemplate.exchange("/api/v1/private/products/group/" + code, HttpMethod.DELETE, null,
				String.class));
		assertGroupExists(code, true, adminHeaders);

		assertEquals(HttpStatus.OK, testRestTemplate.exchange("/api/v1/private/products/group/" + code,
				HttpMethod.DELETE, new HttpEntity<>(adminHeaders), String.class).getStatusCode());
		assertGroupExists(code, false, adminHeaders);
	}

	private HttpHeaders registerAndLoginCustomer() {
		String email = unique("uatcustomer").toLowerCase() + "@test.com";
		PersistableCustomer customer = new PersistableCustomer();
		customer.setEmailAddress(email);
		customer.setPassword("clear123");
		customer.setGender(CustomerGender.M.name());
		customer.setLanguage("en");
		customer.setStoreCode(Constants.DEFAULT_STORE);
		Address billing = new Address();
		billing.setFirstName("UAT");
		billing.setLastName("Customer");
		billing.setCountry("BE");
		customer.setBilling(billing);
		ResponseEntity<?> registration = testRestTemplate.postForEntity("/api/v1/customer/register",
				new HttpEntity<>(customer, getHeader()), Object.class);
		assertEquals(HttpStatus.OK, registration.getStatusCode());
		ResponseEntity<AuthenticationResponse> login = testRestTemplate.postForEntity("/api/v1/customer/login",
				new HttpEntity<>(new AuthenticationRequest(email, "clear123")), AuthenticationResponse.class);
		assertEquals(HttpStatus.OK, login.getStatusCode());
		assertNotNull(login.getBody());
		return bearer(login.getBody().getToken());
	}

	private HttpHeaders bearer(String token) {
		HttpHeaders headers = new HttpHeaders();
		headers.setBearerAuth(token);
		return headers;
	}

	private HttpHeaders basic(String username, String password) {
		HttpHeaders headers = new HttpHeaders();
		headers.set("Authorization", "Basic " + Base64.getEncoder()
				.encodeToString((username + ":" + password).getBytes(java.nio.charset.StandardCharsets.UTF_8)));
		headers.setContentType(MediaType.TEXT_PLAIN);
		return headers;
	}

	private PersistableShoppingCartItem cartItem(String sku, int quantity) {
		PersistableShoppingCartItem item = new PersistableShoppingCartItem();
		item.setProduct(sku);
		item.setQuantity(quantity);
		return item;
	}

	private <T> ResponseEntity<T> get(String path, HttpHeaders headers, Class<T> responseType) {
		return testRestTemplate.exchange(path, HttpMethod.GET, new HttpEntity<>(headers), responseType);
	}

	private void assertDenied(ResponseEntity<?> response) {
		assertTrue("Expected 401 or 403 but received " + response.getStatusCode(),
				response.getStatusCode() == HttpStatus.UNAUTHORIZED || response.getStatusCode() == HttpStatus.FORBIDDEN);
	}

	private void assertGroupExists(String code, boolean exists, HttpHeaders headers) {
		ResponseEntity<ProductGroup[]> response = get("/api/v1/private/product/groups", headers, ProductGroup[].class);
		assertEquals(HttpStatus.OK, response.getStatusCode());
		assertNotNull(response.getBody());
		boolean found = Arrays.stream(response.getBody()).anyMatch(item -> code.equals(item.getCode()));
		assertEquals(exists, found);
	}

	private static String unique(String prefix) {
		return prefix + Long.toString(System.currentTimeMillis(), 36).toUpperCase() + IDS.incrementAndGet();
	}
}
