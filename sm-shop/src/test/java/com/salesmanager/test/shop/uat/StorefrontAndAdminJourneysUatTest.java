package com.salesmanager.test.shop.uat;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import java.math.BigDecimal;
import java.util.Collections;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
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

import com.salesmanager.core.business.constants.Constants;
import com.salesmanager.core.model.catalog.product.manufacturer.Manufacturer;
import com.salesmanager.core.model.customer.CustomerGender;
import com.salesmanager.shop.application.ShopApplication;
import com.salesmanager.shop.model.catalog.category.Category;
import com.salesmanager.shop.model.catalog.category.PersistableCategory;
import com.salesmanager.shop.model.catalog.category.ReadableCategoryList;
import com.salesmanager.shop.model.catalog.product.ReadableProduct;
import com.salesmanager.shop.model.catalog.product.ReadableProductList;
import com.salesmanager.shop.model.catalog.product.product.PersistableProduct;
import com.salesmanager.shop.model.catalog.product.product.ProductSpecification;
import com.salesmanager.shop.model.customer.PersistableCustomer;
import com.salesmanager.shop.model.customer.ReadableCustomer;
import com.salesmanager.shop.model.customer.address.Address;
import com.salesmanager.shop.model.entity.Entity;
import com.salesmanager.shop.model.shoppingcart.PersistableShoppingCartItem;
import com.salesmanager.shop.model.shoppingcart.ReadableShoppingCart;
import com.salesmanager.shop.store.security.AuthenticationRequest;
import com.salesmanager.shop.store.security.AuthenticationResponse;
import com.salesmanager.test.shop.common.ServicesTestSupport;

@SpringBootTest(classes = ShopApplication.class, webEnvironment = WebEnvironment.RANDOM_PORT)
@RunWith(SpringRunner.class)
public class StorefrontAndAdminJourneysUatTest extends ServicesTestSupport {

	private final ObjectMapper objectMapper = new ObjectMapper();

	@Test
	public void adminAuthenticationAndCustomerListJourney() {
		ResponseEntity<AuthenticationResponse> login = testRestTemplate.postForEntity(
				"/api/v1/private/login", new HttpEntity<>(new AuthenticationRequest("admin@shopizer.com", "password")),
				AuthenticationResponse.class);
		assertEquals(HttpStatus.OK, login.getStatusCode());
		assertNotNull(login.getBody());
		assertNotNull(login.getBody().getToken());

		ResponseEntity<String> authorized = testRestTemplate.exchange("/api/v1/private/customers",
				HttpMethod.GET, new HttpEntity<>(null, headersWithToken(login.getBody().getToken())), String.class);
		ResponseEntity<String> anonymous = testRestTemplate.getForEntity("/api/v1/private/customers", String.class);
		HttpHeaders invalidHeaders = new HttpHeaders();
		invalidHeaders.setBearerAuth("garbage");
		ResponseEntity<String> invalid = testRestTemplate.exchange("/api/v1/private/customers", HttpMethod.GET,
				new HttpEntity<>(null, invalidHeaders), String.class);

		assertEquals(HttpStatus.OK, authorized.getStatusCode());
		assertEquals(HttpStatus.UNAUTHORIZED, anonymous.getStatusCode());
		assertFalse(invalid.getStatusCode().is2xxSuccessful());
	}

	@Test
	public void customerRegistrationAndProfileJourney() {
		String email = "uatcustomer" + uniqueCode() + "@test.com";
		HttpHeaders customerHeaders = registerCustomer(email);

		ResponseEntity<ReadableCustomer> profile = testRestTemplate.exchange(
				"/api/v1/auth/customer/profile", HttpMethod.GET, new HttpEntity<>(customerHeaders),
				ReadableCustomer.class);

		assertEquals(HttpStatus.OK, profile.getStatusCode());
		assertNotNull(profile.getBody());
		assertEquals(email, profile.getBody().getEmailAddress());
	}

	@Test
	public void storefrontBrowseJourney() {
		ResponseEntity<String> store = testRestTemplate.getForEntity("/api/v1/store/DEFAULT", String.class);
		ReadableProduct product = sampleProduct(uniqueCode());
		ResponseEntity<ReadableCategoryList> categories = testRestTemplate.getForEntity(
				"/api/v1/category?store=DEFAULT", ReadableCategoryList.class);
		ResponseEntity<ReadableProductList> products = testRestTemplate.getForEntity(
				"/api/v1/products?store=DEFAULT&sku=" + product.getSku(), ReadableProductList.class);
		ResponseEntity<ReadableProduct> detail = testRestTemplate.getForEntity(
				"/api/v2/product/" + product.getSku() + "?store=DEFAULT", ReadableProduct.class);

		assertEquals(HttpStatus.OK, store.getStatusCode());
		assertTrue(store.getBody().contains("\"code\":\"DEFAULT\""));
		assertEquals(HttpStatus.OK, categories.getStatusCode());
		assertFalse(categories.getBody().getCategories().isEmpty());
		assertEquals(HttpStatus.OK, products.getStatusCode());
		assertTrue(products.getBody().getProducts().stream()
				.anyMatch(listedProduct -> product.getSku().equals(listedProduct.getSku())));
		assertEquals(HttpStatus.OK, detail.getStatusCode());
		assertEquals(product.getSku(), detail.getBody().getSku());
	}

	@Test
	public void shoppingCartCreateUpdateAndReadJourney() {
		ReadableProduct product = sampleProduct(uniqueCode());
		PersistableShoppingCartItem item = cartItem(product.getSku(), 1);
		ResponseEntity<ReadableShoppingCart> created = testRestTemplate.postForEntity(
				"/api/v1/cart?store=DEFAULT", new HttpEntity<>(item, getHeader()), ReadableShoppingCart.class);
		assertEquals(HttpStatus.CREATED, created.getStatusCode());
		assertNotNull(created.getBody());
		assertNotNull(created.getBody().getCode());

		ResponseEntity<ReadableShoppingCart> updated = testRestTemplate.exchange(
				"/api/v1/cart/" + created.getBody().getCode() + "?store=DEFAULT", HttpMethod.PUT,
				new HttpEntity<>(cartItem(product.getSku(), 3), getHeader()), ReadableShoppingCart.class);
		ResponseEntity<ReadableShoppingCart> read = testRestTemplate.getForEntity(
				"/api/v1/cart/" + created.getBody().getCode() + "?store=DEFAULT", ReadableShoppingCart.class);

		assertEquals(HttpStatus.CREATED, updated.getStatusCode());
		assertEquals(HttpStatus.OK, read.getStatusCode());
		assertEquals(3, read.getBody().getQuantity());
	}

	@Test
	public void privateProductAdminAndRemovedAliasJourney() {
		String sku = uniqueCode();
		ReadableProduct created = createProduct(sku);
		PersistableProduct update = product(sku);
		update.setId(created.getId());
		update.setSku(sku);
		update.getInventory().getPrice().setPrice(new BigDecimal("45.00"));
		update.getInventory().getPrice().setDiscountedPrice(null);
		ResponseEntity<Void> updated = testRestTemplate.exchange(
				"/api/v1/private/product/" + created.getId() + "?store=DEFAULT", HttpMethod.PUT,
				new HttpEntity<>(update, getHeader()), Void.class);
		assertEquals(HttpStatus.OK, updated.getStatusCode());

		ReadableProduct afterUpdate = testRestTemplate.getForObject(
				"/api/v2/product/" + sku + "?store=DEFAULT", ReadableProduct.class);
		assertNotNull(afterUpdate);
		assertEquals(sku, afterUpdate.getSku());
		HttpHeaders sortOrderHeaders = getHeader();
		sortOrderHeaders.setAccept(Collections.singletonList(MediaType.TEXT_PLAIN));
		assertEquals(HttpStatus.OK, testRestTemplate.exchange(
				"/api/v1/private/product/" + created.getId() + "?store=DEFAULT&order=3", HttpMethod.PATCH,
				new HttpEntity<>(null, sortOrderHeaders), Void.class).getStatusCode());
		assertEquals(HttpStatus.OK, testRestTemplate.exchange(
				"/api/v1/private/product/" + created.getId() + "?store=DEFAULT", HttpMethod.DELETE,
				new HttpEntity<>(null, getHeader()), Void.class).getStatusCode());

		String blockedSku = uniqueCode();
		PersistableProduct blockedProduct = productWithCategory(blockedSku);
		ResponseEntity<String> alias = testRestTemplate.postForEntity(
				"/api/v1/auth/products?store=DEFAULT",
				new HttpEntity<>(blockedProduct, registerCustomer("alias" + uniqueCode() + "@test.com")), String.class);
		assertFalse(alias.getStatusCode().is2xxSuccessful());
		assertTrue(testRestTemplate.getForObject(
				"/api/v1/products?store=DEFAULT&sku=" + blockedSku, ReadableProductList.class)
				.getProducts().isEmpty());
	}

	@Test
	public void servicesBasicAuthenticationAndPublicEndpointJourney() {
		HttpHeaders validBasic = new HttpHeaders();
		validBasic.setBasicAuth("admin@shopizer.com", "password");
		validBasic.setContentType(MediaType.TEXT_PLAIN);
		ResponseEntity<String> valid = testRestTemplate.postForEntity(
				"/services/private/system/optin", new HttpEntity<>("{}", validBasic), String.class);

		HttpHeaders invalidBasic = new HttpHeaders();
		invalidBasic.setBasicAuth("admin@shopizer.com", "wrong");
		invalidBasic.setContentType(MediaType.TEXT_PLAIN);
		ResponseEntity<String> invalid = testRestTemplate.postForEntity(
				"/services/private/system/optin", new HttpEntity<>("{}", invalidBasic), String.class);
		ResponseEntity<String> publicEndpoint = testRestTemplate.getForEntity("/services/public/DEFAULT", String.class);

		assertEquals(HttpStatus.OK, valid.getStatusCode());
		assertEquals(HttpStatus.UNAUTHORIZED, invalid.getStatusCode());
		assertTrue(publicEndpoint.getStatusCode() != HttpStatus.UNAUTHORIZED
				&& publicEndpoint.getStatusCode() != HttpStatus.FORBIDDEN);
	}

	@Test
	public void healthAndProductApiDocumentationJourney() throws Exception {
		ResponseEntity<String> health = testRestTemplate.getForEntity("/actuator/health", String.class);
		assertEquals(HttpStatus.OK, health.getStatusCode());
		assertEquals("UP", objectMapper.readTree(health.getBody()).path("status").asText());

		ResponseEntity<String> docs = testRestTemplate.getForEntity("/v2/api-docs", String.class);
		assertEquals(HttpStatus.OK, docs.getStatusCode());
		JsonNode paths = objectMapper.readTree(docs.getBody()).path("paths");
		assertTrue(paths.has("/api/v1/private/product"));
		assertFalse(paths.has("/api/v1/auth/products"));
		assertFalse(paths.has("/api/v1/auth/product/{id}"));
	}

	private ReadableProduct createProduct(String sku) {
		PersistableProduct product = productWithCategory(sku);
		ResponseEntity<Entity> response = testRestTemplate.postForEntity(
				"/api/v1/private/product?store=DEFAULT", new HttpEntity<>(product, getHeader()), Entity.class);
		assertEquals(HttpStatus.CREATED, response.getStatusCode());
		return testRestTemplate.getForObject("/api/v2/product/" + sku + "?store=DEFAULT", ReadableProduct.class);
	}

	private PersistableProduct productWithCategory(String sku) {
		PersistableProduct product = product(sku);
		product.setSku(sku);
		product.setAvailable(true);
		product.setPrice(new BigDecimal("14.25"));
		product.setQuantity(100);
		ProductSpecification specifications = new ProductSpecification();
		specifications.setManufacturer(Manufacturer.DEFAULT_MANUFACTURER);
		product.setProductSpecifications(specifications);

		PersistableCategory category = category(sku + "CAT");
		category.setParent(new Category());
		ResponseEntity<PersistableCategory> categoryResponse = testRestTemplate.postForEntity(
				"/api/v1/private/category?store=DEFAULT", new HttpEntity<>(category, getHeader()),
				PersistableCategory.class);
		assertEquals(HttpStatus.CREATED, categoryResponse.getStatusCode());
		assertNotNull(categoryResponse.getBody());
		product.setCategories(Collections.singletonList(categoryResponse.getBody()));
		return product;
	}

	private HttpHeaders registerCustomer(String email) {
		PersistableCustomer customer = new PersistableCustomer();
		customer.setEmailAddress(email);
		customer.setPassword("clear123");
		customer.setGender(CustomerGender.M.name());
		customer.setLanguage("en");
		Address billing = new Address();
		billing.setFirstName("journey");
		billing.setLastName("customer");
		billing.setCountry("BE");
		customer.setBilling(billing);
		customer.setStoreCode(Constants.DEFAULT_STORE);
		ResponseEntity<PersistableCustomer> registration = testRestTemplate.postForEntity(
				"/api/v1/customer/register", new HttpEntity<>(customer, getHeader()), PersistableCustomer.class);
		assertEquals(HttpStatus.OK, registration.getStatusCode());

		ResponseEntity<AuthenticationResponse> login = testRestTemplate.postForEntity(
				"/api/v1/customer/login", new HttpEntity<>(new AuthenticationRequest(email, "clear123")),
				AuthenticationResponse.class);
		assertEquals(HttpStatus.OK, login.getStatusCode());
		assertNotNull(login.getBody());
		assertNotNull(login.getBody().getToken());
		return headersWithToken(login.getBody().getToken());
	}

	private HttpHeaders headersWithToken(String token) {
		HttpHeaders headers = new HttpHeaders();
		headers.setBearerAuth(token);
		headers.setContentType(MediaType.APPLICATION_JSON);
		return headers;
	}

	private PersistableShoppingCartItem cartItem(String sku, int quantity) {
		PersistableShoppingCartItem item = new PersistableShoppingCartItem();
		item.setProduct(sku);
		item.setQuantity(quantity);
		return item;
	}

	private String uniqueCode() {
		return "U" + Long.toString(System.nanoTime(), 36).toUpperCase();
	}
}
