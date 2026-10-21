package com.salesmanager.test.shop.integration.product;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import java.math.BigDecimal;
import java.util.Collections;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.SpringBootTest.WebEnvironment;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.beans.factory.annotation.Autowired;
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
import com.salesmanager.shop.model.catalog.product.ReadableProduct;
import com.salesmanager.shop.model.catalog.product.ReadableProductList;
import com.salesmanager.shop.model.catalog.product.product.PersistableProduct;
import com.salesmanager.shop.model.catalog.product.product.ProductSpecification;
import com.salesmanager.shop.model.customer.PersistableCustomer;
import com.salesmanager.shop.model.customer.address.Address;
import com.salesmanager.shop.model.entity.Entity;
import com.salesmanager.shop.model.references.PersistableAddress;
import com.salesmanager.shop.model.store.PersistableMerchantStore;
import com.salesmanager.shop.model.store.ReadableMerchantStore;
import com.salesmanager.shop.store.security.AuthenticationRequest;
import com.salesmanager.shop.store.security.AuthenticationResponse;
import com.salesmanager.test.shop.common.ServicesTestSupport;

@SpringBootTest(classes = ShopApplication.class, webEnvironment = WebEnvironment.RANDOM_PORT)
@RunWith(SpringRunner.class)
public class ProductWriteAuthorizationIntegrationTest extends ServicesTestSupport {

	@Autowired
	private TestRestTemplate testRestTemplate;

	@Test
	public void customerCannotCreateViaAuthAlias() {
		String sku = uniqueCode();
		PersistableProduct product = productWithCategory(sku);

		ResponseEntity<String> response = testRestTemplate.postForEntity(
				"/api/v1/auth/products?store=DEFAULT", new HttpEntity<>(product, customerHeaders()), String.class);

		assertFalse("Auth alias response: " + response.getStatusCode() + " " + response.getBody(),
				response.getStatusCode().is2xxSuccessful());
		assertTrue(readProductsBySku(sku).getBody().getProducts().isEmpty());
	}

	@Test
	public void customerCannotUpdateDeleteOrReorderViaAuthAlias() {
		ReadableProduct existing = createProduct(uniqueCode());
		String originalPrice = existing.getFinalPrice();
		HttpHeaders customerHeaders = customerHeaders();
		PersistableProduct update = product(existing.getSku());
		update.setId(existing.getId());
		update.setSku(existing.getSku());
		update.setPrice(new BigDecimal("999.00"));
		HttpEntity<PersistableProduct> updateRequest = new HttpEntity<>(update, customerHeaders);
		String endpoint = "/api/v1/auth/product/" + existing.getId() + "?store=DEFAULT";

		ResponseEntity<Void> updateResponse = testRestTemplate.exchange(endpoint, HttpMethod.PUT, updateRequest,
				Void.class);
		ResponseEntity<Void> deleteResponse = testRestTemplate.exchange(endpoint, HttpMethod.DELETE,
				new HttpEntity<>(null, customerHeaders), Void.class);
		ResponseEntity<Void> reorderResponse = testRestTemplate.exchange(endpoint + "&order=7", HttpMethod.PATCH,
				new HttpEntity<>(null, customerHeaders), Void.class);

		assertEquals(HttpStatus.NOT_FOUND, updateResponse.getStatusCode());
		assertEquals(HttpStatus.NOT_FOUND, deleteResponse.getStatusCode());
		assertEquals(HttpStatus.NOT_FOUND, reorderResponse.getStatusCode());
		ReadableProduct after = readProduct(existing.getSku()).getBody();
		assertNotNull(after);
		assertEquals(existing.getSku(), after.getSku());
		assertEquals(originalPrice, after.getFinalPrice());
	}

	@Test
	public void customerTokenAndMissingTokenAreRejectedOnPrivateRoutes() {
		ReadableProduct existing = createProduct(uniqueCode());
		String endpoint = "/api/v1/private/product/" + existing.getId() + "?store=DEFAULT";
		ResponseEntity<Void> customerDelete = testRestTemplate.exchange(endpoint, HttpMethod.DELETE,
				new HttpEntity<>(null, customerHeaders()), Void.class);
		ResponseEntity<Entity> missingTokenCreate = testRestTemplate.postForEntity(
				"/api/v1/private/product?store=DEFAULT", new HttpEntity<>(productWithCategory(uniqueCode())),
				Entity.class);

		assertEquals(HttpStatus.UNAUTHORIZED, customerDelete.getStatusCode());
		assertEquals(HttpStatus.UNAUTHORIZED, missingTokenCreate.getStatusCode());
		assertEquals(HttpStatus.OK, readProduct(existing.getSku()).getStatusCode());
	}

	@Test
	public void adminCanCreateUpdateReorderAndDeleteViaPrivateRoutes() {
		ReadableProduct created = createProduct(uniqueCode());
		PersistableProduct update = product(created.getSku());
		update.setId(created.getId());
		update.setSku(created.getSku());
		update.getInventory().getPrice().setPrice(new BigDecimal("35.25"));
		update.getInventory().getPrice().setDiscountedPrice(null);

		ResponseEntity<Void> updateResponse = testRestTemplate.exchange(
				"/api/v1/private/product/" + created.getId() + "?store=DEFAULT", HttpMethod.PUT,
				new HttpEntity<>(update, getHeader()), Void.class);
		ReadableProduct afterUpdate = readProduct(created.getSku()).getBody();
		HttpHeaders sortOrderHeaders = getHeader();
		sortOrderHeaders.setAccept(Collections.singletonList(MediaType.TEXT_PLAIN));
		ResponseEntity<Void> reorderResponse = testRestTemplate.exchange(
				"/api/v1/private/product/" + created.getId() + "?store=DEFAULT&order=3", HttpMethod.PATCH,
				new HttpEntity<>(null, sortOrderHeaders), Void.class);
		ResponseEntity<Void> deleteResponse = testRestTemplate.exchange(
				"/api/v1/private/product/" + created.getId() + "?store=DEFAULT", HttpMethod.DELETE,
				new HttpEntity<>(null, getHeader()), Void.class);

		assertEquals(HttpStatus.OK, updateResponse.getStatusCode());
		assertNotNull(afterUpdate);
		assertEquals(new BigDecimal("35.25"), afterUpdate.getPrice());
		assertEquals(HttpStatus.OK, reorderResponse.getStatusCode());
		assertEquals(HttpStatus.OK, deleteResponse.getStatusCode());
		assertTrue(readProductsBySku(created.getSku()).getBody().getProducts().isEmpty());
	}

	@Test
	public void mismatchedPutIdDoesNotModifyTheBodyProduct() {
		ReadableProduct pathProduct = createProduct(uniqueCode());
		ReadableProduct bodyProduct = createProduct(uniqueCode());
		BigDecimal originalPrice = bodyProduct.getPrice();
		PersistableProduct update = product(bodyProduct.getSku());
		update.setId(bodyProduct.getId());
		update.setSku(bodyProduct.getSku());
		update.setPrice(new BigDecimal("777.00"));

		ResponseEntity<Void> response = testRestTemplate.exchange(
				"/api/v1/private/product/" + pathProduct.getId() + "?store=DEFAULT", HttpMethod.PUT,
				new HttpEntity<>(update, getHeader()), Void.class);
		ReadableProduct after = readProduct(bodyProduct.getSku()).getBody();

		assertEquals(HttpStatus.BAD_REQUEST, response.getStatusCode());
		assertNotNull(after);
		assertEquals(originalPrice, after.getPrice());
	}

	@Test
	public void crossStoreProductWritesAreRejected() {
		ReadableProduct existing = createProduct(uniqueCode());
		BigDecimal originalPrice = existing.getPrice();
		String otherStore = createStore();
		PersistableProduct update = product(existing.getSku());
		update.setId(existing.getId());
		update.setSku(existing.getSku());
		update.setPrice(new BigDecimal("888.00"));

		ResponseEntity<Void> updateResponse = testRestTemplate.exchange(
				"/api/v1/private/product/" + existing.getId() + "?store=" + otherStore, HttpMethod.PUT,
				new HttpEntity<>(update, getHeader()), Void.class);
		ResponseEntity<Entity> createResponse = testRestTemplate.postForEntity(
				"/api/v1/private/product?store=" + otherStore, new HttpEntity<>(update, getHeader()), Entity.class);
		ResponseEntity<ReadableProduct> otherStoreRead = testRestTemplate.getForEntity(
				"/api/v2/product/" + existing.getSku() + "?store=" + otherStore, ReadableProduct.class);
		ReadableProduct defaultStoreRead = readProduct(existing.getSku()).getBody();

		assertEquals(HttpStatus.SERVICE_UNAVAILABLE, updateResponse.getStatusCode());
		assertEquals(HttpStatus.NOT_FOUND, createResponse.getStatusCode());
		assertEquals(HttpStatus.NOT_FOUND, otherStoreRead.getStatusCode());
		assertNotNull(defaultStoreRead);
		assertEquals(originalPrice, defaultStoreRead.getPrice());
	}

	private ReadableProduct createProduct(String sku) {
		PersistableProduct product = productWithCategory(sku);
		ResponseEntity<Entity> response = testRestTemplate.postForEntity(
				"/api/v1/private/product?store=DEFAULT", new HttpEntity<>(product, getHeader()), Entity.class);
		assertEquals(HttpStatus.CREATED, response.getStatusCode());
		ResponseEntity<ReadableProduct> read = readProduct(sku);
		assertEquals(HttpStatus.OK, read.getStatusCode());
		assertNotNull(read.getBody());
		return read.getBody();
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

	private ResponseEntity<ReadableProduct> readProduct(String sku) {
		return testRestTemplate.getForEntity("/api/v2/product/" + sku + "?store=DEFAULT", ReadableProduct.class);
	}

	private ResponseEntity<ReadableProductList> readProductsBySku(String sku) {
		return testRestTemplate.getForEntity(
				"/api/v1/products?store=DEFAULT&sku=" + sku, ReadableProductList.class);
	}

	private String createStore() {
		String code = "STORE" + uniqueCode();
		PersistableAddress address = new PersistableAddress();
		address.setAddress("121212 simple address");
		address.setPostalCode("12345");
		address.setCountry("US");
		address.setCity("FT LD");
		address.setStateProvince("FL");

		PersistableMerchantStore store = new PersistableMerchantStore();
		store.setCode(code);
		store.setCurrency("CAD");
		store.setDefaultLanguage("en");
		store.setEmail("store" + uniqueCode() + "@test.com");
		store.setName(code);
		store.setPhone("444-555-6666");
		store.setSupportedLanguages(Collections.singletonList("en"));
		store.setAddress(address);

		ResponseEntity<ReadableMerchantStore> response = testRestTemplate.postForEntity(
				"/api/v1/private/store", new HttpEntity<>(store, getHeader()), ReadableMerchantStore.class);
		assertTrue(response.getStatusCode().is2xxSuccessful());
		return code;
	}

	private HttpHeaders customerHeaders() {
		String email = "customer" + uniqueCode() + "@test.com";
		PersistableCustomer customer = new PersistableCustomer();
		customer.setEmailAddress(email);
		customer.setPassword("clear123");
		customer.setGender(CustomerGender.M.name());
		customer.setLanguage("en");
		Address billing = new Address();
		billing.setFirstName("customer");
		billing.setLastName("test");
		billing.setCountry("BE");
		customer.setBilling(billing);
		customer.setStoreCode(Constants.DEFAULT_STORE);
		ResponseEntity<PersistableCustomer> registration = testRestTemplate.postForEntity(
				"/api/v1/customer/register", new HttpEntity<>(customer, getHeader()), PersistableCustomer.class);
		assertEquals(HttpStatus.OK, registration.getStatusCode());

		AuthenticationRequest login = new AuthenticationRequest(email, "clear123");
		ResponseEntity<AuthenticationResponse> response = testRestTemplate.postForEntity(
				"/api/v1/customer/login", new HttpEntity<>(login), AuthenticationResponse.class);
		assertEquals(HttpStatus.OK, response.getStatusCode());
		assertNotNull(response.getBody());
		assertNotNull(response.getBody().getToken());
		HttpHeaders headers = new HttpHeaders();
		headers.setBearerAuth(response.getBody().getToken());
		headers.setContentType(MediaType.APPLICATION_JSON);
		return headers;
	}

	private String uniqueCode() {
		return "T" + Long.toString(System.nanoTime(), 36).toUpperCase();
	}
}
