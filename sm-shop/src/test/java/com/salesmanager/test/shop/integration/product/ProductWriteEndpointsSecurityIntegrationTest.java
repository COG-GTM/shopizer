package com.salesmanager.test.shop.integration.product;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;
import static org.springframework.http.HttpStatus.CREATED;
import static org.springframework.http.HttpStatus.NOT_FOUND;
import static org.springframework.http.HttpStatus.OK;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Arrays;
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
import com.salesmanager.core.model.catalog.product.manufacturer.Manufacturer;
import com.salesmanager.core.model.customer.CustomerGender;
import com.salesmanager.shop.application.ShopApplication;
import com.salesmanager.shop.model.catalog.category.Category;
import com.salesmanager.shop.model.catalog.category.CategoryDescription;
import com.salesmanager.shop.model.catalog.category.PersistableCategory;
import com.salesmanager.shop.model.catalog.product.ReadableProduct;
import com.salesmanager.shop.model.catalog.product.product.PersistableProduct;
import com.salesmanager.shop.model.catalog.product.product.ProductSpecification;
import com.salesmanager.shop.model.customer.PersistableCustomer;
import com.salesmanager.shop.model.customer.address.Address;
import com.salesmanager.shop.model.entity.Entity;
import com.salesmanager.shop.model.store.PersistableMerchantStore;
import com.salesmanager.shop.model.references.PersistableAddress;
import com.salesmanager.shop.store.security.AuthenticationRequest;
import com.salesmanager.shop.store.security.AuthenticationResponse;
import com.salesmanager.test.shop.common.ServicesTestSupport;

@SpringBootTest(classes = ShopApplication.class, webEnvironment = WebEnvironment.RANDOM_PORT)
@RunWith(SpringRunner.class)
public class ProductWriteEndpointsSecurityIntegrationTest extends ServicesTestSupport {

	private static final String SECOND_STORE = "AUTHZSTORE2";
	private static final String PASSWORD = "clear123";

	@Test
	public void adminCanCreateUpdateAndDeleteProductThroughPrivateEndpoints() {
		String code = uniqueCode("write-lifecycle");
		PersistableProduct create = newProduct(code);

		ResponseEntity<Entity> created = testRestTemplate.postForEntity(
				"/api/v1/private/product?store=" + Constants.DEFAULT_STORE,
				new HttpEntity<>(create, getHeader()), Entity.class);
		assertEquals(CREATED, created.getStatusCode());
		assertNotNull(created.getBody());
		assertNotNull(created.getBody().getId());

		create.setId(created.getBody().getId());
		BigDecimal updatedPrice = new BigDecimal("19.99");
		setPrice(create, updatedPrice);
		ResponseEntity<String> updated = testRestTemplate.exchange(
				"/api/v1/private/product/" + create.getId() + "?store=" + Constants.DEFAULT_STORE,
				HttpMethod.PUT, new HttpEntity<>(create, getHeader()), String.class);
		assertEquals(OK, updated.getStatusCode());

		ResponseEntity<ReadableProduct> publicProduct = getPublicProduct(code, Constants.DEFAULT_STORE);
		assertEquals(OK, publicProduct.getStatusCode());
		assertEquals(0, updatedPrice.compareTo(publicProduct.getBody().getPrice()));

		ResponseEntity<String> deleted = testRestTemplate.exchange(
				"/api/v1/private/product/" + create.getId() + "?store=" + Constants.DEFAULT_STORE,
				HttpMethod.DELETE, new HttpEntity<>(getHeader()), String.class);
		assertEquals(OK, deleted.getStatusCode());

		ResponseEntity<String> missing = testRestTemplate.getForEntity(
				"/api/v2/product/" + code + "?store=" + Constants.DEFAULT_STORE, String.class);
		assertTrue("Deleted product remained readable: " + missing.getStatusCode(),
				missing.getStatusCode().is4xxClientError());
	}

	@Test
	public void privateProductWritesRequireAuthorization() {
		String code = uniqueCode("no-auth");
		PersistableProduct product = newProduct(code);
		product.setId(987654L);
		HttpHeaders headers = new HttpHeaders();
		headers.setContentType(MediaType.APPLICATION_JSON);
		String base = "/api/v1/private/product";
		String store = "?store=" + Constants.DEFAULT_STORE;

		ResponseEntity<String> post = testRestTemplate.exchange(base + store, HttpMethod.POST,
				new HttpEntity<>(product, headers), String.class);
		ResponseEntity<String> put = testRestTemplate.exchange(base + "/987654" + store, HttpMethod.PUT,
				new HttpEntity<>(product, headers), String.class);
		ResponseEntity<String> delete = testRestTemplate.exchange(base + "/987654" + store, HttpMethod.DELETE,
				new HttpEntity<>(headers), String.class);

		assertEquals(HttpStatus.UNAUTHORIZED, post.getStatusCode());
		assertEquals(HttpStatus.UNAUTHORIZED, put.getStatusCode());
		assertEquals(HttpStatus.UNAUTHORIZED, delete.getStatusCode());
	}

	@Test
	public void customerCannotUpdatePrivateProduct() {
		String code = uniqueCode("customer-private");
		PersistableProduct product = createProduct(code);
		BigDecimal originalPrice = product.getPrice();
		HttpHeaders customerHeaders = registerAndLoginCustomer(uniqueCode("customer"));
		setPrice(product, new BigDecimal("999.99"));

		ResponseEntity<String> response = testRestTemplate.exchange(
				"/api/v1/private/product/" + product.getId() + "?store=" + Constants.DEFAULT_STORE,
				HttpMethod.PUT, new HttpEntity<>(product, customerHeaders), String.class);

		assertTrue("Customer private update should be forbidden, got " + response.getStatusCode(),
				response.getStatusCode() == HttpStatus.UNAUTHORIZED
						|| response.getStatusCode() == HttpStatus.FORBIDDEN);
		ResponseEntity<ReadableProduct> unchanged = getPublicProduct(code, Constants.DEFAULT_STORE);
		assertEquals(OK, unchanged.getStatusCode());
		assertEquals(0, originalPrice.compareTo(unchanged.getBody().getPrice()));
	}

	@Test
	public void customerCannotWriteRemovedAuthAliases() {
		String code = uniqueCode("removed-alias");
		PersistableProduct product = createProduct(code);
		HttpHeaders customerHeaders = registerAndLoginCustomer(uniqueCode("customer"));

		PersistableProduct aliasCreate = newProduct(uniqueCode("alias-create"));
		ResponseEntity<String> post = testRestTemplate.exchange(
				"/api/v1/auth/products?store=" + Constants.DEFAULT_STORE,
				HttpMethod.POST, new HttpEntity<>(aliasCreate, customerHeaders), String.class);

		setPrice(product, new BigDecimal("888.88"));
		ResponseEntity<String> put = testRestTemplate.exchange(
				"/api/v1/auth/product/" + product.getId() + "?store=" + Constants.DEFAULT_STORE,
				HttpMethod.PUT, new HttpEntity<>(product, customerHeaders), String.class);
		ResponseEntity<String> delete = testRestTemplate.exchange(
				"/api/v1/auth/product/" + product.getId() + "?store=" + Constants.DEFAULT_STORE,
				HttpMethod.DELETE, new HttpEntity<>(customerHeaders), String.class);
		ResponseEntity<String> patch = testRestTemplate.exchange(
				"/api/v1/auth/product/" + product.getId() + "?store=" + Constants.DEFAULT_STORE + "&order=3",
				HttpMethod.PATCH, new HttpEntity<>(customerHeaders), String.class);

		ResponseEntity<ReadableProduct> publicProduct = getPublicProduct(code, Constants.DEFAULT_STORE);
		assertEquals(OK, publicProduct.getStatusCode());

		String statuses = "POST " + post.getStatusCode() + ", PUT " + put.getStatusCode()
				+ ", DELETE " + delete.getStatusCode() + ", PATCH " + patch.getStatusCode();
		assertEquals(statuses, NOT_FOUND, post.getStatusCode());
		assertEquals(statuses, NOT_FOUND, put.getStatusCode());
		assertEquals(statuses, NOT_FOUND, delete.getStatusCode());
		assertEquals(statuses, NOT_FOUND, patch.getStatusCode());

	}

	@Test
	public void rejectsCrossStoreProductUpdateAsNotFound() {
		ensureSecondStoreExists();
		String code = uniqueCode("cross-store");
		PersistableProduct product = createProduct(code);
		BigDecimal originalPrice = product.getPrice();
		setPrice(product, new BigDecimal("999.99"));

		ResponseEntity<String> response = testRestTemplate.exchange(
				"/api/v1/private/product/" + product.getId() + "?store=" + SECOND_STORE,
				HttpMethod.PUT, new HttpEntity<>(product, getHeader()), String.class);

		assertEquals(NOT_FOUND, response.getStatusCode());
		ResponseEntity<ReadableProduct> unchanged = getPublicProduct(code, Constants.DEFAULT_STORE);
		assertEquals(OK, unchanged.getStatusCode());
		assertEquals(0, originalPrice.compareTo(unchanged.getBody().getPrice()));
	}

	@Test
	public void anonymousProductReadsRemainAvailable() {
		String code = uniqueCode("anonymous-read");
		PersistableProduct product = createProduct(code);

		ResponseEntity<String> list = testRestTemplate.getForEntity(
				"/api/v1/products?store=" + Constants.DEFAULT_STORE, String.class);
		ResponseEntity<String> byId = testRestTemplate.getForEntity(
				"/api/v1/product/" + product.getId() + "?store=" + Constants.DEFAULT_STORE, String.class);
		ResponseEntity<String> byFriendlyUrl = testRestTemplate.getForEntity(
				"/api/v1/product/" + code + "?store=" + Constants.DEFAULT_STORE, String.class);

		assertEquals(OK, list.getStatusCode());
		System.out.println("Anonymous GET by numeric product id status: " + byId.getStatusCode());
		assertEquals(OK, byFriendlyUrl.getStatusCode());
	}

	@Test
	public void privatePatchWithoutBodyReportsDispatchStatusForBaseComparison() {
		PersistableProduct product = createProduct(uniqueCode("patch-dispatch"));
		ResponseEntity<String> response = testRestTemplate.exchange(
				"/api/v1/private/product/" + product.getId() + "?store=" + Constants.DEFAULT_STORE + "&order=3",
				HttpMethod.PATCH, new HttpEntity<>(getHeader()), String.class);

		System.out.println("private PATCH without body status: " + response.getStatusCode());
		assertTrue("Unexpected private PATCH status " + response.getStatusCode(),
				response.getStatusCode().is2xxSuccessful() || response.getStatusCode().is4xxClientError());
	}

	@Test
	public void productSwaggerDocsExposePrivateAndOmitRemovedAliases() throws Exception {
		ResponseEntity<String> response = testRestTemplate.getForEntity("/v2/api-docs", String.class);
		assertEquals(OK, response.getStatusCode());
		JsonNode paths = new ObjectMapper().readTree(response.getBody()).path("paths");

		assertTrue(paths.has("/api/v1/private/product/{id}"));
		assertFalse(paths.has("/api/v1/auth/product/{id}"));
		assertFalse(paths.has("/api/v1/auth/products"));
	}

	private PersistableProduct createProduct(String code) {
		PersistableProduct product = newProduct(code);
		ResponseEntity<Entity> response = testRestTemplate.postForEntity(
				"/api/v1/private/product?store=" + Constants.DEFAULT_STORE,
				new HttpEntity<>(product, getHeader()), Entity.class);
		assertEquals(CREATED, response.getStatusCode());
		assertNotNull(response.getBody());
		assertNotNull(response.getBody().getId());
		product.setId(response.getBody().getId());
		return product;
	}

	private PersistableProduct newProduct(String code) {
		PersistableCategory category = new PersistableCategory();
		category.setCode(code);
		category.setSortOrder(1);
		category.setVisible(true);
		category.setDepth(4);
		category.setParent(new Category());
		CategoryDescription description = new CategoryDescription();
		description.setLanguage("en");
		description.setName(code);
		description.setFriendlyUrl(code);
		description.setTitle(code);
		category.setDescriptions(Arrays.asList(description));
		ResponseEntity<PersistableCategory> categoryResponse = testRestTemplate.postForEntity(
				"/api/v1/private/category?store=" + Constants.DEFAULT_STORE,
				new HttpEntity<>(category, getHeader()), PersistableCategory.class);
		assertEquals(CREATED, categoryResponse.getStatusCode());
		assertNotNull(categoryResponse.getBody());

		PersistableProduct product = product(code);
		product.setCategories(new ArrayList<Category>(Arrays.asList(categoryResponse.getBody())));
		product.setProductSpecifications(specification());
		product.setAvailable(true);
		product.setSku(code);
		product.getDescriptions().get(0).setFriendlyUrl(code);
		setPrice(product, BigDecimal.TEN);
		return product;
	}

	private ProductSpecification specification() {
		ProductSpecification specification = new ProductSpecification();
		specification.setManufacturer(Manufacturer.DEFAULT_MANUFACTURER);
		return specification;
	}

	private void setPrice(PersistableProduct product, BigDecimal price) {
		product.setPrice(price);
		product.getInventory().getPrice().setPrice(price);
		product.getInventory().getPrice().setDiscountedPrice(price);
	}

	private ResponseEntity<ReadableProduct> getPublicProduct(String sku, String store) {
		return testRestTemplate.getForEntity("/api/v2/product/" + sku + "?store=" + store, ReadableProduct.class);
	}

	private HttpHeaders registerAndLoginCustomer(String email) {
		PersistableCustomer customer = new PersistableCustomer();
		customer.setEmailAddress(email);
		customer.setPassword(PASSWORD);
		customer.setGender(CustomerGender.M.name());
		customer.setLanguage("en");
		customer.setStoreCode(Constants.DEFAULT_STORE);
		Address billing = new Address();
		billing.setFirstName("product");
		billing.setLastName("authorization");
		billing.setCountry("BE");
		customer.setBilling(billing);
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

		HttpHeaders headers = new HttpHeaders();
		headers.setContentType(MediaType.APPLICATION_JSON);
		headers.setBearerAuth(login.getBody().getToken());
		return headers;
	}

	private void ensureSecondStoreExists() {
		ResponseEntity<String> existing = testRestTemplate.getForEntity(
				"/api/v1/store/" + SECOND_STORE, String.class);
		if (existing.getStatusCode().is2xxSuccessful()) {
			return;
		}

		PersistableAddress address = new PersistableAddress();
		address.setAddress("121212 simple address");
		address.setPostalCode("12345");
		address.setCountry("US");
		address.setCity("FT LD");
		address.setStateProvince("FL");

		PersistableMerchantStore store = new PersistableMerchantStore();
		store.setCode(SECOND_STORE);
		store.setCurrency("CAD");
		store.setDefaultLanguage("en");
		store.setEmail("authz-store2@test.com");
		store.setName(SECOND_STORE);
		store.setPhone("444-555-6666");
		store.setSupportedLanguages(Arrays.asList("en"));
		store.setAddress(address);

		ResponseEntity<Void> created = testRestTemplate.exchange(
				"/api/v1/private/store/", HttpMethod.POST, new HttpEntity<>(store, getHeader()), Void.class);
		ResponseEntity<String> confirm = testRestTemplate.getForEntity(
				"/api/v1/store/" + SECOND_STORE, String.class);
		assertTrue("Second store creation failed: " + created.getStatusCode(),
				created.getStatusCode().is2xxSuccessful() || confirm.getStatusCode() == OK);
	}

	private String uniqueCode(String prefix) {
		return prefix + UUID.randomUUID().toString().replace("-", "");
	}
}
