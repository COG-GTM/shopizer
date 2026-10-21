package com.salesmanager.test.shop.uat;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;
import static org.springframework.http.HttpStatus.CREATED;
import static org.springframework.http.HttpStatus.OK;

import java.util.UUID;

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

import com.salesmanager.core.business.constants.Constants;
import com.salesmanager.core.model.customer.CustomerGender;
import com.salesmanager.shop.application.ShopApplication;
import com.salesmanager.shop.model.catalog.product.ReadableProduct;
import com.salesmanager.shop.model.customer.PersistableCustomer;
import com.salesmanager.shop.model.customer.ReadableCustomer;
import com.salesmanager.shop.model.customer.address.Address;
import com.salesmanager.shop.model.shoppingcart.PersistableShoppingCartItem;
import com.salesmanager.shop.model.shoppingcart.ReadableShoppingCart;
import com.salesmanager.shop.store.security.AuthenticationRequest;
import com.salesmanager.shop.store.security.AuthenticationResponse;
import com.salesmanager.test.shop.common.ServicesTestSupport;

@SpringBootTest(classes = ShopApplication.class, webEnvironment = WebEnvironment.RANDOM_PORT)
@RunWith(SpringRunner.class)
public class StorefrontAdminJourneyUatTest extends ServicesTestSupport {

	private static final String PASSWORD = "clear123";

	@Test
	public void adminLoginProtectsPrivateCustomerList() {
		ResponseEntity<AuthenticationResponse> login = testRestTemplate.postForEntity(
				"/api/v1/private/login",
				new HttpEntity<>(new AuthenticationRequest("admin@shopizer.com", "password")),
				AuthenticationResponse.class);
		assertEquals(OK, login.getStatusCode());
		assertNotNull(login.getBody());
		assertNotNull(login.getBody().getToken());

		HttpHeaders authenticated = bearerHeaders(login.getBody().getToken());
		ResponseEntity<String> authorized = testRestTemplate.exchange(
				"/api/v1/private/customers", HttpMethod.GET, new HttpEntity<>(authenticated), String.class);
		ResponseEntity<String> anonymous = testRestTemplate.getForEntity(
				"/api/v1/private/customers", String.class);
		ResponseEntity<String> invalidToken = testRestTemplate.exchange(
				"/api/v1/private/customers", HttpMethod.GET,
				new HttpEntity<>(bearerHeaders("bad.token.x")), String.class);

		assertEquals(OK, authorized.getStatusCode());
		assertEquals("No-token private customers status: " + anonymous.getStatusCode(),
				org.springframework.http.HttpStatus.UNAUTHORIZED, anonymous.getStatusCode());
		assertFalse("Bad-token private customers status: " + invalidToken.getStatusCode(),
				invalidToken.getStatusCode().is2xxSuccessful());
	}

	@Test
	public void customerCanRegisterLoginAndReadProfile() {
		String email = uniqueEmail();
		PersistableCustomer customer = customer(email);
		ResponseEntity<PersistableCustomer> registration = testRestTemplate.postForEntity(
				"/api/v1/customer/register", new HttpEntity<>(customer, getHeader()), PersistableCustomer.class);
		assertTrue("Customer registration failed: " + registration.getStatusCode(),
				registration.getStatusCode().is2xxSuccessful());

		ResponseEntity<AuthenticationResponse> login = testRestTemplate.postForEntity(
				"/api/v1/customer/login",
				new HttpEntity<>(new AuthenticationRequest(email, PASSWORD)), AuthenticationResponse.class);
		assertEquals(OK, login.getStatusCode());
		assertNotNull(login.getBody());
		assertNotNull(login.getBody().getToken());

		ResponseEntity<ReadableCustomer> profile = testRestTemplate.exchange(
				"/api/v1/auth/customer/profile", HttpMethod.GET,
				new HttpEntity<>(bearerHeaders(login.getBody().getToken())), ReadableCustomer.class);
		assertEquals(OK, profile.getStatusCode());
		assertEquals(email, profile.getBody().getEmailAddress());
	}

	@Test
	public void visitorCanBrowseStoreCategoryProductsAndProduct() {
		String code = "uatbrowse" + UUID.randomUUID().toString().replace("-", "");
		ReadableProduct product = sampleProduct(code);
		assertNotNull(product);

		ResponseEntity<String> store = testRestTemplate.getForEntity(
				"/api/v1/store/" + Constants.DEFAULT_STORE, String.class);
		ResponseEntity<String> categories = testRestTemplate.getForEntity(
				"/api/v1/category?store=" + Constants.DEFAULT_STORE, String.class);
		ResponseEntity<String> products = testRestTemplate.getForEntity(
				"/api/v1/products?store=" + Constants.DEFAULT_STORE, String.class);
		ResponseEntity<String> productById = testRestTemplate.getForEntity(
				"/api/v1/product/" + product.getId() + "?store=" + Constants.DEFAULT_STORE, String.class);
		ResponseEntity<String> productBySku = testRestTemplate.getForEntity(
				"/api/v2/product/" + product.getSku() + "?store=" + Constants.DEFAULT_STORE, String.class);

		assertEquals(OK, store.getStatusCode());
		assertEquals(OK, categories.getStatusCode());
		assertEquals(OK, products.getStatusCode());
		System.out.println("UAT GET by numeric product id status: " + productById.getStatusCode());
		assertEquals(OK, productBySku.getStatusCode());
	}

	@Test
	public void visitorCanCreateUpdateAndReadCart() {
		String suffix = UUID.randomUUID().toString().replace("-", "");
		ReadableProduct first = sampleProduct("uatcartfirst" + suffix);
		ReadableProduct second = sampleProduct("uatcartsecond" + suffix);

		PersistableShoppingCartItem firstItem = cartItem(first.getSku(), 1);
		ResponseEntity<ReadableShoppingCart> created = testRestTemplate.postForEntity(
				"/api/v1/cart", new HttpEntity<>(firstItem, getHeader()), ReadableShoppingCart.class);
		assertEquals(CREATED, created.getStatusCode());
		assertNotNull(created.getBody());

		PersistableShoppingCartItem secondItem = cartItem(second.getSku(), 1);
		ResponseEntity<ReadableShoppingCart> added = testRestTemplate.exchange(
				"/api/v1/cart/" + created.getBody().getCode(), HttpMethod.PUT,
				new HttpEntity<>(secondItem, getHeader()), ReadableShoppingCart.class);
		assertEquals(CREATED, added.getStatusCode());

		ResponseEntity<ReadableShoppingCart> updated = testRestTemplate.postForEntity(
				"/api/v1/cart/" + created.getBody().getCode() + "/multi",
				new HttpEntity<>(new PersistableShoppingCartItem[] {
						cartItem(first.getSku(), 3), cartItem(second.getSku(), 2) }, getHeader()),
				ReadableShoppingCart.class);
		assertEquals(CREATED, updated.getStatusCode());

		ResponseEntity<ReadableShoppingCart> fetched = testRestTemplate.getForEntity(
				"/api/v1/cart/" + created.getBody().getCode(), ReadableShoppingCart.class);
		assertEquals(OK, fetched.getStatusCode());
		assertEquals(5, fetched.getBody().getQuantity());
		assertEquals(3, quantityForSku(fetched.getBody(), first.getSku()));
		assertEquals(2, quantityForSku(fetched.getBody(), second.getSku()));
	}

	@Test
	public void servicesEndpointsUseBasicAuthentication() {
		HttpHeaders goodCredentials = new HttpHeaders();
		goodCredentials.setBasicAuth("admin@shopizer.com", "password");
		HttpHeaders badCredentials = new HttpHeaders();
		badCredentials.setBasicAuth("bad-user", "bad-password");

		ResponseEntity<String> authorized = testRestTemplate.exchange(
				"/services/private/anything", HttpMethod.GET,
				new HttpEntity<>(goodCredentials), String.class);
		ResponseEntity<String> rejected = testRestTemplate.exchange(
				"/services/private/anything", HttpMethod.GET,
				new HttpEntity<>(badCredentials), String.class);

		assertTrue("Valid Basic credentials returned " + authorized.getStatusCode(),
				authorized.getStatusCode() != org.springframework.http.HttpStatus.UNAUTHORIZED);
		assertEquals(org.springframework.http.HttpStatus.UNAUTHORIZED, rejected.getStatusCode());
	}

	@Test
	public void actuatorHealthAndSwaggerDocsAreAvailable() throws Exception {
		ResponseEntity<String> health = testRestTemplate.getForEntity("/actuator/health", String.class);
		assertEquals(OK, health.getStatusCode());
		assertTrue(health.getBody(), health.getBody().contains("\"status\":\"UP\""));

		ResponseEntity<String> docs = testRestTemplate.getForEntity("/v2/api-docs", String.class);
		assertEquals(OK, docs.getStatusCode());
	}

	private HttpHeaders bearerHeaders(String token) {
		HttpHeaders headers = new HttpHeaders();
		headers.setContentType(MediaType.APPLICATION_JSON);
		headers.setBearerAuth(token);
		return headers;
	}

	private PersistableCustomer customer(String email) {
		PersistableCustomer customer = new PersistableCustomer();
		customer.setEmailAddress(email);
		customer.setPassword(PASSWORD);
		customer.setGender(CustomerGender.M.name());
		customer.setLanguage("en");
		customer.setStoreCode(Constants.DEFAULT_STORE);
		Address billing = new Address();
		billing.setFirstName("storefront");
		billing.setLastName("journey");
		billing.setCountry("BE");
		customer.setBilling(billing);
		return customer;
	}

	private PersistableShoppingCartItem cartItem(String product, int quantity) {
		PersistableShoppingCartItem item = new PersistableShoppingCartItem();
		item.setProduct(product);
		item.setQuantity(quantity);
		return item;
	}

	private int quantityForSku(ReadableShoppingCart cart, String sku) {
		return cart.getProducts().stream()
				.filter(item -> sku.equals(item.getSku()))
				.findFirst()
				.orElseThrow(() -> new AssertionError("Product not found in cart: " + sku))
				.getQuantity();
	}

	private String uniqueEmail() {
		return "storefront-" + UUID.randomUUID().toString().replace("-", "") + "@test.com";
	}
}
