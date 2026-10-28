package com.salesmanager.test.shop.uat;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import java.util.HashMap;
import java.util.Map;

import org.junit.Before;
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
import com.salesmanager.core.business.constants.Constants;
import com.salesmanager.shop.application.ShopApplication;
import com.salesmanager.shop.model.catalog.category.Category;
import com.salesmanager.shop.model.catalog.product.ReadableProduct;
import com.salesmanager.shop.model.catalog.product.ReadableProductList;
import com.salesmanager.shop.model.catalog.product.product.ProductSpecification;
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

	private String suffix;
	private ReadableProduct product;

	@Before
	public void setUp() {
		suffix = Long.toString(System.nanoTime());
		product = sampleProduct("uat-" + suffix);
	}

	@Test
	public void adminCustomerBrowseAndCartJourneyWorks() {
		ResponseEntity<AuthenticationResponse> adminLogin = testRestTemplate.postForEntity(
				"/api/v1/private/login",
				new HttpEntity<>(new AuthenticationRequest("admin@shopizer.com", "password")),
				AuthenticationResponse.class);
		assertEquals(HttpStatus.OK, adminLogin.getStatusCode());
		assertNotNull(adminLogin.getBody());
		HttpHeaders adminHeaders = bearer(adminLogin.getBody().getToken());

		ResponseEntity<Object> customers = testRestTemplate.exchange("/api/v1/private/customers", HttpMethod.GET,
				new HttpEntity<>(adminHeaders), Object.class);
		assertEquals(HttpStatus.OK, customers.getStatusCode());
		ResponseEntity<String> noToken = testRestTemplate.getForEntity("/api/v1/private/customers", String.class);
		assertEquals(HttpStatus.UNAUTHORIZED, noToken.getStatusCode());
		ResponseEntity<String> badToken = testRestTemplate.exchange("/api/v1/private/customers", HttpMethod.GET,
				new HttpEntity<>(bearer("abc.def.ghi")), String.class);
		System.out.println("UAT_BAD_TOKEN_STATUS=" + badToken.getStatusCodeValue());
		assertFalse(badToken.getStatusCode().is2xxSuccessful());

		Shopper shopper = registerShopper("journey-" + suffix);
		ResponseEntity<ReadableCustomer> profile = testRestTemplate.exchange(
				"/api/v1/auth/customer/profile", HttpMethod.GET, new HttpEntity<>(shopper.headers),
				ReadableCustomer.class);
		assertEquals(HttpStatus.OK, profile.getStatusCode());
		assertEquals(shopper.email, profile.getBody().getEmailAddress());

		assertEquals(HttpStatus.OK,
				testRestTemplate.getForEntity("/api/v1/store/DEFAULT", Object.class).getStatusCode());
		assertEquals(HttpStatus.OK,
				testRestTemplate.getForEntity("/api/v1/category?store=DEFAULT", Object.class).getStatusCode());
		ResponseEntity<ReadableProductList> productList = testRestTemplate.getForEntity(
				"/api/v1/products?store=DEFAULT", ReadableProductList.class);
		assertEquals(HttpStatus.OK, productList.getStatusCode());
		assertTrue(productList.getBody().getProducts().stream()
				.anyMatch(readable -> product.getSku().equals(readable.getSku())));
		ResponseEntity<ReadableProduct> productDetail = testRestTemplate.getForEntity(
				"/api/v2/product/" + product.getSku() + "?store=DEFAULT", ReadableProduct.class);
		assertEquals(HttpStatus.OK, productDetail.getStatusCode());

		ReadableProduct secondProduct = sampleProduct("uat-cart-second-" + suffix);
		PersistableShoppingCartItem firstItem = cartItem(product.getSku(), 1);
		ResponseEntity<ReadableShoppingCart> cartCreate = testRestTemplate.postForEntity("/api/v1/cart",
				new HttpEntity<>(firstItem), ReadableShoppingCart.class);
		assertEquals(HttpStatus.CREATED, cartCreate.getStatusCode());
		assertNotNull(cartCreate.getBody());
		String cartCode = cartCreate.getBody().getCode();

		ResponseEntity<ReadableShoppingCart> quantityUpdate = testRestTemplate.exchange(
				"/api/v1/cart/" + cartCode, HttpMethod.PUT,
				new HttpEntity<>(cartItem(product.getSku(), 2)), ReadableShoppingCart.class);
		assertEquals(HttpStatus.CREATED, quantityUpdate.getStatusCode());
		ResponseEntity<ReadableShoppingCart> addSecondItem = testRestTemplate.exchange(
				"/api/v1/cart/" + cartCode, HttpMethod.PUT,
				new HttpEntity<>(cartItem(secondProduct.getSku(), 1)), ReadableShoppingCart.class);
		assertEquals(HttpStatus.CREATED, addSecondItem.getStatusCode());
		ResponseEntity<ReadableShoppingCart> cartRead = testRestTemplate.getForEntity(
				"/api/v1/cart/" + cartCode, ReadableShoppingCart.class);
		assertEquals(HttpStatus.OK, cartRead.getStatusCode());
		assertEquals(3, cartRead.getBody().getQuantity());
		Map<String, Integer> quantities = new HashMap<>();
		cartRead.getBody().getProducts().forEach(item -> quantities.put(item.getSku(), item.getQuantity()));
		assertEquals(Integer.valueOf(2), quantities.get(product.getSku()));
		assertEquals(Integer.valueOf(1), quantities.get(secondProduct.getSku()));
	}

	@Test
	public void servicesBasicAuthHealthDocsAndShopperWriteContractWork() {
		HttpHeaders basicHeaders = new HttpHeaders();
		basicHeaders.setBasicAuth("admin@shopizer.com", "password");
		basicHeaders.setContentType(MediaType.TEXT_PLAIN);
		ResponseEntity<String> services = testRestTemplate.postForEntity(
				"/services/private/system/optin", new HttpEntity<>("{}", basicHeaders), String.class);
		assertTrue(services.getStatusCode().is2xxSuccessful());

		HttpHeaders invalidBasicHeaders = new HttpHeaders();
		invalidBasicHeaders.setBasicAuth("admin@shopizer.com", "wrong-password");
		invalidBasicHeaders.setContentType(MediaType.TEXT_PLAIN);
		ResponseEntity<String> invalidServices = testRestTemplate.postForEntity(
				"/services/private/system/optin", new HttpEntity<>("{}", invalidBasicHeaders), String.class);
		assertEquals(HttpStatus.UNAUTHORIZED, invalidServices.getStatusCode());

		ResponseEntity<JsonNode> health = testRestTemplate.getForEntity("/actuator/health", JsonNode.class);
		assertEquals(HttpStatus.OK, health.getStatusCode());
		assertEquals("UP", health.getBody().path("status").asText());

		ResponseEntity<JsonNode> docs = testRestTemplate.getForEntity("/v2/api-docs", JsonNode.class);
		assertEquals(HttpStatus.OK, docs.getStatusCode());
		assertTrue(docs.getBody().path("paths").has("/api/v1/private/product"));
		assertFalse(docs.getBody().path("paths").has("/api/v1/auth/products"));

		Shopper shopper = registerShopper("blocked-write-" + suffix);
		String blockedSku = "uat-blocked-" + suffix;
		ResponseEntity<String> blocked = testRestTemplate.postForEntity(
				"/api/v1/auth/products?store=" + Constants.DEFAULT_STORE,
				new HttpEntity<>(productPayload(blockedSku), shopper.headers), String.class);
		System.out.println("UAT_SHOPPER_PRODUCT_CREATE_STATUS=" + blocked.getStatusCodeValue());
		assertFalse(blocked.getStatusCode().is2xxSuccessful());
		ResponseEntity<String> blockedProduct = testRestTemplate.getForEntity(
				"/api/v2/product/" + blockedSku, String.class);
		assertFalse(blockedProduct.getStatusCode().is2xxSuccessful());
	}

	private Shopper registerShopper(String identifier) {
		String email = identifier + "-" + suffix + "@example.com";
		PersistableCustomer customer = new PersistableCustomer();
		customer.setEmailAddress(email);
		customer.setPassword("clear123");
		customer.setGender("M");
		customer.setLanguage("en");
		customer.setStoreCode(Constants.DEFAULT_STORE);
		Address billing = new Address();
		billing.setFirstName("UAT");
		billing.setLastName("Customer");
		billing.setCountry("BE");
		customer.setBilling(billing);
		ResponseEntity<PersistableCustomer> registration = testRestTemplate.postForEntity(
				"/api/v1/customer/register", new HttpEntity<>(customer), PersistableCustomer.class);
		assertEquals(HttpStatus.OK, registration.getStatusCode());

		ResponseEntity<AuthenticationResponse> login = testRestTemplate.postForEntity("/api/v1/customer/login",
				new HttpEntity<>(new AuthenticationRequest(email, "clear123")), AuthenticationResponse.class);
		assertEquals(HttpStatus.OK, login.getStatusCode());
		assertNotNull(login.getBody());
		ResponseEntity<ReadableCustomer> profile = testRestTemplate.exchange(
				"/api/v1/auth/customer/profile", HttpMethod.GET,
				new HttpEntity<>(bearer(login.getBody().getToken())), ReadableCustomer.class);
		assertEquals(HttpStatus.OK, profile.getStatusCode());
		return new Shopper(email, bearer(login.getBody().getToken()));
	}

	private PersistableShoppingCartItem cartItem(String sku, int quantity) {
		PersistableShoppingCartItem item = new PersistableShoppingCartItem();
		item.setProduct(sku);
		item.setQuantity(quantity);
		return item;
	}

	private com.salesmanager.shop.model.catalog.product.product.PersistableProduct productPayload(String sku) {
		com.salesmanager.shop.model.catalog.product.product.PersistableProduct payload = product(sku);
		payload.setSku(sku);
		payload.setAvailable(true);
		payload.setPrice(java.math.BigDecimal.TEN);
		payload.setQuantity(100);
		ProductSpecification specifications = new ProductSpecification();
		specifications.setManufacturer(
				com.salesmanager.core.model.catalog.product.manufacturer.Manufacturer.DEFAULT_MANUFACTURER);
		payload.setProductSpecifications(specifications);
		if (!product.getCategories().isEmpty()) {
			Category category = new Category();
			category.setId(product.getCategories().get(0).getId());
			payload.setCategories(java.util.Collections.singletonList(category));
		}
		return payload;
	}

	private HttpHeaders bearer(String token) {
		HttpHeaders headers = new HttpHeaders();
		headers.setContentType(MediaType.APPLICATION_JSON);
		headers.setBearerAuth(token);
		return headers;
	}

	private static class Shopper {
		private final String email;
		private final HttpHeaders headers;

		private Shopper(String email, HttpHeaders headers) {
			this.email = email;
			this.headers = headers;
		}
	}
}
