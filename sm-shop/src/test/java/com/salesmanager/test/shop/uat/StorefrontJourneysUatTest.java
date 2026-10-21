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
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.test.context.junit4.SpringRunner;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.salesmanager.core.business.constants.Constants;
import com.salesmanager.shop.application.ShopApplication;
import com.salesmanager.shop.model.catalog.product.ReadableProduct;
import com.salesmanager.shop.model.customer.PersistableCustomer;
import com.salesmanager.shop.model.customer.ReadableCustomer;
import com.salesmanager.shop.model.customer.address.Address;
import com.salesmanager.shop.model.shoppingcart.PersistableShoppingCartItem;
import com.salesmanager.shop.model.shoppingcart.ReadableShoppingCart;
import com.salesmanager.shop.model.store.ReadableMerchantStore;
import com.salesmanager.shop.model.user.ReadableUser;
import com.salesmanager.shop.store.security.AuthenticationRequest;
import com.salesmanager.shop.store.security.AuthenticationResponse;
import com.salesmanager.test.shop.common.ServicesTestSupport;

@SpringBootTest(classes = ShopApplication.class, webEnvironment = WebEnvironment.RANDOM_PORT)
@RunWith(SpringRunner.class)
public class StorefrontJourneysUatTest extends ServicesTestSupport {

	private final ObjectMapper objectMapper = new ObjectMapper();

	@Test
	public void adminApiRequiresTokenAndAcceptsAdminJwt() {
		ResponseEntity<String> anonymous = testRestTemplate.getForEntity("/api/v1/private/user/profile", String.class);
		assertEquals(HttpStatus.UNAUTHORIZED, anonymous.getStatusCode());

		ResponseEntity<AuthenticationResponse> login = testRestTemplate.postForEntity("/api/v1/private/login",
				new HttpEntity<>(new AuthenticationRequest("admin@shopizer.com", "password")),
				AuthenticationResponse.class);
		assertEquals(HttpStatus.OK, login.getStatusCode());
		assertNotNull(login.getBody());
		assertNotNull(login.getBody().getToken());

		ResponseEntity<ReadableUser> authenticated = testRestTemplate.exchange("/api/v1/private/user/profile",
				HttpMethod.GET, new HttpEntity<>(bearerHeaders(login.getBody().getToken())), ReadableUser.class);
		assertEquals(HttpStatus.OK, authenticated.getStatusCode());
		assertEquals("admin@shopizer.com", authenticated.getBody().getUserName());
	}

	@Test
	public void registeredCustomerCanAuthenticateAndReadProfile() {
		String email = "uat-" + uniqueCode() + "@example.com";
		PersistableCustomer customer = newCustomer(email);
		ResponseEntity<AuthenticationResponse> registration = testRestTemplate.postForEntity(
				"/api/v1/customer/register", new HttpEntity<>(customer), AuthenticationResponse.class);
		assertEquals(HttpStatus.OK, registration.getStatusCode());

		AuthenticationRequest credentials = new AuthenticationRequest(email, customer.getPassword());
		ResponseEntity<AuthenticationResponse> login = testRestTemplate.postForEntity(
				"/api/v1/customer/login", new HttpEntity<>(credentials), AuthenticationResponse.class);
		assertEquals(HttpStatus.OK, login.getStatusCode());
		assertNotNull(login.getBody());
		assertNotNull(login.getBody().getToken());

		HttpHeaders headers = bearerHeaders(login.getBody().getToken());
		ResponseEntity<ReadableCustomer> profile = testRestTemplate.exchange("/api/v1/auth/customer/profile",
				HttpMethod.GET, new HttpEntity<>(headers), ReadableCustomer.class);
		assertEquals(HttpStatus.OK, profile.getStatusCode());
		assertEquals(email, profile.getBody().getUserName());
	}

	@Test
	public void storefrontCanBrowseStoreCategoriesProductsAndProductDetails() {
		String sku = "uat-" + uniqueCode();
		ReadableProduct created = sampleProduct(sku);

		ResponseEntity<ReadableMerchantStore> store = testRestTemplate.getForEntity(
				"/api/v1/store/" + Constants.DEFAULT_STORE, ReadableMerchantStore.class);
		assertEquals(HttpStatus.OK, store.getStatusCode());
		assertNotNull(store.getBody());

		ResponseEntity<String> categories = testRestTemplate.getForEntity(
				"/api/v1/category?store=" + Constants.DEFAULT_STORE, String.class);
		assertEquals(HttpStatus.OK, categories.getStatusCode());
		assertNotNull(categories.getBody());

		ResponseEntity<String> products = testRestTemplate.getForEntity(
				"/api/v1/products?store=" + Constants.DEFAULT_STORE + "&sku=" + sku, String.class);
		assertEquals(HttpStatus.OK, products.getStatusCode());
		assertTrue(products.getBody(), products.getBody().contains(sku));

		ResponseEntity<ReadableProduct> detail = testRestTemplate.getForEntity(
				"/api/v2/product/" + created.getSku() + "?store=" + Constants.DEFAULT_STORE, ReadableProduct.class);
		assertEquals(HttpStatus.OK, detail.getStatusCode());
		assertEquals(sku, detail.getBody().getSku());
	}

	@Test
	public void cartCanAddUpdateAndReadProductQuantities() {
		ReadableProduct firstProduct = sampleProduct("uat-cart-a-" + uniqueCode());
		ReadableProduct secondProduct = sampleProduct("uat-cart-b-" + uniqueCode());

		PersistableShoppingCartItem firstItem = cartItem(firstProduct.getSku(), 1);
		ResponseEntity<ReadableShoppingCart> created = testRestTemplate.postForEntity(
				"/api/v1/cart?store=" + Constants.DEFAULT_STORE,
				new HttpEntity<>(firstItem, getHeader()), ReadableShoppingCart.class);
		assertEquals(HttpStatus.CREATED, created.getStatusCode());
		assertNotNull(created.getBody());

		PersistableShoppingCartItem secondItem = cartItem(secondProduct.getSku(), 1);
		ResponseEntity<ReadableShoppingCart> updated = testRestTemplate.exchange(
				"/api/v1/cart/" + created.getBody().getCode() + "?store=" + Constants.DEFAULT_STORE,
				HttpMethod.PUT, new HttpEntity<>(secondItem, getHeader()), ReadableShoppingCart.class);
		assertEquals(HttpStatus.CREATED, updated.getStatusCode());

		firstItem.setQuantity(2);
		ResponseEntity<ReadableShoppingCart> quantityUpdated = testRestTemplate.exchange(
				"/api/v1/cart/" + created.getBody().getCode() + "?store=" + Constants.DEFAULT_STORE,
				HttpMethod.PUT, new HttpEntity<>(firstItem, getHeader()), ReadableShoppingCart.class);
		assertEquals(HttpStatus.CREATED, quantityUpdated.getStatusCode());

		ResponseEntity<ReadableShoppingCart> retrieved = testRestTemplate.getForEntity(
				"/api/v1/cart/" + created.getBody().getCode() + "?store=" + Constants.DEFAULT_STORE,
				ReadableShoppingCart.class);
		assertEquals(HttpStatus.OK, retrieved.getStatusCode());
		assertEquals(3, retrieved.getBody().getQuantity());
		assertTrue(retrieved.getBody().getProducts().size() >= 2);
	}

	@Test
	public void servicesApiUsesValidAdminBasicAuthentication() {
		HttpHeaders validHeaders = new HttpHeaders();
		validHeaders.setBasicAuth("admin@shopizer.com", "password");
		validHeaders.setContentType(MediaType.TEXT_PLAIN);
		ResponseEntity<String> valid = testRestTemplate.exchange("/services/private/system/optin", HttpMethod.POST,
				new HttpEntity<>("{}", validHeaders), String.class);
		assertEquals(HttpStatus.OK, valid.getStatusCode());

		HttpHeaders invalidHeaders = new HttpHeaders();
		invalidHeaders.setBasicAuth("admin@shopizer.com", "wrong-password");
		invalidHeaders.setContentType(MediaType.TEXT_PLAIN);
		ResponseEntity<String> invalid = testRestTemplate.exchange("/services/private/system/optin",
				HttpMethod.POST, new HttpEntity<>("{}", invalidHeaders), String.class);
		assertEquals(HttpStatus.UNAUTHORIZED, invalid.getStatusCode());
	}

	@Test
	public void actuatorHealthReportsUp() throws Exception {
		ResponseEntity<String> response = testRestTemplate.getForEntity("/actuator/health", String.class);
		assertEquals(HttpStatus.OK, response.getStatusCode());
		JsonNode health = objectMapper.readTree(response.getBody());
		assertEquals("UP", health.path("status").asText());
	}

	private PersistableCustomer newCustomer(String email) {
		PersistableCustomer customer = new PersistableCustomer();
		customer.setEmailAddress(email);
		customer.setPassword("clear123");
		customer.setGender("M");
		customer.setLanguage("en");
		customer.setStoreCode(Constants.DEFAULT_STORE);
		Address billing = new Address();
		billing.setFirstName("UAT");
		billing.setLastName("Customer");
		billing.setCountry("US");
		customer.setBilling(billing);
		return customer;
	}

	private PersistableShoppingCartItem cartItem(String sku, int quantity) {
		PersistableShoppingCartItem item = new PersistableShoppingCartItem();
		item.setProduct(sku);
		item.setQuantity(quantity);
		return item;
	}

	private HttpHeaders bearerHeaders(String token) {
		HttpHeaders headers = new HttpHeaders();
		headers.setBearerAuth(token);
		headers.setContentType(MediaType.APPLICATION_JSON);
		return headers;
	}

	private String uniqueCode() {
		return UUID.randomUUID().toString().replace("-", "");
	}
}
