package com.salesmanager.test.shop.integration.product;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;
import static org.springframework.http.HttpMethod.PUT;
import static org.springframework.http.HttpStatus.CREATED;
import static org.springframework.http.HttpStatus.NOT_FOUND;
import static org.springframework.http.HttpStatus.OK;

import java.math.BigDecimal;
import java.util.Arrays;
import java.util.UUID;

import org.junit.Before;
import org.junit.Test;
import org.junit.Assume;
import org.junit.runner.RunWith;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.SpringBootTest.WebEnvironment;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.ResponseEntity;
import org.springframework.test.context.junit4.SpringRunner;

import com.salesmanager.core.business.constants.Constants;
import com.salesmanager.shop.application.ShopApplication;
import com.salesmanager.shop.model.catalog.manufacturer.PersistableManufacturer;
import com.salesmanager.shop.model.catalog.product.ReadableProduct;
import com.salesmanager.shop.model.catalog.product.ReadableProductList;
import com.salesmanager.shop.model.catalog.product.product.PersistableProduct;
import com.salesmanager.shop.model.catalog.product.product.ProductSpecification;
import com.salesmanager.shop.model.entity.Entity;
import com.salesmanager.shop.model.references.PersistableAddress;
import com.salesmanager.shop.model.store.PersistableMerchantStore;
import com.salesmanager.shop.model.store.ReadableMerchantStore;
import com.salesmanager.shop.model.user.PersistableUser;
import com.salesmanager.shop.model.user.ReadableUser;
import com.salesmanager.shop.model.security.PersistableGroup;
import com.salesmanager.test.shop.common.ServicesTestSupport;

@SpringBootTest(classes = ShopApplication.class, webEnvironment = WebEnvironment.RANDOM_PORT)
@RunWith(SpringRunner.class)
public class ProductStoreOwnershipIntegrationTest extends ServicesTestSupport {

	private String sku;
	private String originalName;
	private Long productId;
	private String storeB;
	private String storeBManufacturer;
	private HttpHeaders storeBAdmin;
	private String childStore;
	private HttpHeaders childStoreAdmin;
	private boolean childStoreReady;
	private String childStoreFailure;

	@Before
	public void setUpOwnershipFixture() {
		String suffix = UUID.randomUUID().toString().replace("-", "").substring(0, 10);
		sku = "OWNERSHIP-" + suffix;
		originalName = "Ownership " + suffix;
		storeB = "OWNB" + suffix;

		PersistableProduct product = product(sku);
		product.setSku(sku);
		product.setPrice(BigDecimal.TEN);
		product.setAvailable(true);
		product.setProductSpecifications(defaultManufacturer());
		product.getDescriptions().get(0).setName(originalName);
		ResponseEntity<Entity> response = testRestTemplate.postForEntity(
				"/api/v1/private/product?store=" + Constants.DEFAULT_STORE,
				new HttpEntity<>(product, getHeader()), Entity.class);
		assertEquals(CREATED, response.getStatusCode());
		assertNotNull(response.getBody());
		productId = response.getBody().getId();

		assertEquals(OK, createStore(storeB, null).getStatusCode());
		storeBAdmin = createAdmin(storeB, suffix);
		storeBManufacturer = "OWNM" + suffix;
		ResponseEntity<PersistableManufacturer> manufacturerResponse = testRestTemplate.postForEntity(
				"/api/v1/private/manufacturer?store=" + storeB,
				new HttpEntity<>(manufacturer(storeBManufacturer), storeBAdmin), PersistableManufacturer.class);
		assertEquals(CREATED, manufacturerResponse.getStatusCode());

		childStore = "OWNC" + suffix;
		try {
			ResponseEntity<Void> createChildResponse = createStore(childStore, Constants.DEFAULT_STORE);
			if (createChildResponse.getStatusCode() == OK) {
				ResponseEntity<ReadableMerchantStore> responseStore = testRestTemplate.getForEntity(
						"/api/v1/store/" + childStore, ReadableMerchantStore.class);
				childStoreReady = responseStore.getStatusCode() == OK && responseStore.getBody() != null
						&& responseStore.getBody().getParent() != null
						&& Constants.DEFAULT_STORE.equals(responseStore.getBody().getParent().getCode());
				if (!childStoreReady) {
					childStoreFailure = "Child store response did not show DEFAULT as its parent: "
							+ responseStore.getStatusCode();
				}
			} else {
				childStoreFailure = "Child store creation returned HTTP "
						+ createChildResponse.getStatusCodeValue();
			}
			if (childStoreReady) {
				childStoreAdmin = createAdmin(childStore, suffix + "C");
			}
		} catch (RuntimeException | AssertionError e) {
			childStoreReady = false;
			childStoreFailure = e.getMessage();
		}
	}

	@Test
	public void storeBAdminCannotPostDefaultProduct() {
		ResponseEntity<String> response = testRestTemplate.postForEntity(
				"/api/v1/private/product?store=" + storeB,
				new HttpEntity<>(productBody(productId, sku, "Changed by B", storeBManufacturer), storeBAdmin),
				String.class);

		assertEquals(NOT_FOUND, response.getStatusCode());
		assertProductUnchanged();
	}

	@Test
	public void storeBAdminCannotPutDefaultProduct() {
		ResponseEntity<String> response = testRestTemplate.exchange(
				"/api/v1/private/product/" + productId + "?store=" + storeB, PUT,
				new HttpEntity<>(productBody(productId, sku, "Changed by B", storeBManufacturer), storeBAdmin),
				String.class);

		assertEquals(NOT_FOUND, response.getStatusCode());
		assertProductUnchanged();
	}

	@Test
	public void storeBAdminCannotCreateV2InventoryForDefaultProduct() {
		ResponseEntity<String> response = testRestTemplate.postForEntity(
				"/api/v2/private/product/inventory?store=" + storeB,
				new HttpEntity<>(productBody(productId, sku, "Changed by B", storeBManufacturer), storeBAdmin),
				String.class);

		assertEquals(NOT_FOUND, response.getStatusCode());
		assertProductUnchanged();
	}

	@Test
	public void childStoreAdminCannotPostDefaultProduct() {
		Assume.assumeTrue(childStoreFailure, childStoreReady);
		ResponseEntity<String> response = testRestTemplate.postForEntity(
				"/api/v1/private/product?store=" + childStore,
				new HttpEntity<>(productBody(productId, sku, "Changed by C"), childStoreAdmin), String.class);

		assertEquals(NOT_FOUND, response.getStatusCode());
		assertProductUnchanged();
	}

	@Test
	public void childStoreAdminCannotPutDefaultProduct() {
		Assume.assumeTrue(childStoreFailure, childStoreReady);
		ResponseEntity<String> response = testRestTemplate.exchange(
				"/api/v1/private/product/" + productId + "?store=" + childStore, PUT,
				new HttpEntity<>(productBody(productId, sku, "Changed by C"), childStoreAdmin), String.class);

		assertEquals(NOT_FOUND, response.getStatusCode());
		assertProductUnchanged();
	}

	@Test
	public void superadminCannotPostDefaultProductIntoStoreB() {
		ResponseEntity<String> response = testRestTemplate.postForEntity(
				"/api/v1/private/product?store=" + storeB,
				new HttpEntity<>(
						productBody(productId, sku, "Changed by superadmin", storeBManufacturer), getHeader()),
				String.class);

		assertEquals(NOT_FOUND, response.getStatusCode());
		assertProductUnchanged();
	}

	@Test
	public void putOfUnknownProductReturnsNotFound() {
		long unknownId = 987654L;
		ResponseEntity<String> response = testRestTemplate.exchange(
				"/api/v1/private/product/" + unknownId + "?store=" + storeB, PUT,
				new HttpEntity<>(productBody(unknownId, sku, "Unknown product", storeBManufacturer), storeBAdmin),
				String.class);

		assertEquals(NOT_FOUND, response.getStatusCode());
		assertProductUnchanged();
	}

	@Test
	public void putWithMismatchedIdsReturnsBadRequestWithoutChangingProduct() {
		PersistableProduct body = productBody(productId + 1, sku, "Mismatched update");
		ResponseEntity<String> response = testRestTemplate.exchange(
				"/api/v1/private/product/" + productId + "?store=" + Constants.DEFAULT_STORE, PUT,
				new HttpEntity<>(body, getHeader()), String.class);

		assertEquals(400, response.getStatusCodeValue());
		assertProductUnchanged();
	}

	@Test
	public void sameStoreAdminCanUpdateProduct() {
		String updatedName = "Updated " + originalName;
		ResponseEntity<String> response = testRestTemplate.exchange(
				"/api/v1/private/product/" + productId + "?store=" + Constants.DEFAULT_STORE, PUT,
				new HttpEntity<>(productBody(productId, sku, updatedName), getHeader()), String.class);

		assertEquals(OK, response.getStatusCode());
		assertEquals(updatedName, readProduct().getDescription().getName());
	}

	@Test
	public void storeBAdminCanCreateProductWithoutId() {
		String newSku = sku + "-NEW";
		PersistableProduct newProduct = productBody(null, newSku, "New " + originalName, storeBManufacturer);
		ResponseEntity<String> response = testRestTemplate.postForEntity(
				"/api/v1/private/product?store=" + storeB,
				new HttpEntity<>(newProduct, storeBAdmin), String.class);

		assertEquals(String.valueOf(response.getBody()), CREATED, response.getStatusCode());
		assertNotNull(response.getBody());
		assertNotNull(readProduct(storeB, newSku));
	}

	private void assertProductUnchanged() {
		assertEquals(originalName, readProduct().getDescription().getName());
		assertEquals(sku, readProduct().getSku());
	}

	private ReadableProduct readProduct() {
		return readProduct(Constants.DEFAULT_STORE, sku);
	}

	private ReadableProduct readProduct(String store, String productSku) {
		ResponseEntity<ReadableProductList> response = testRestTemplate.exchange(
				"/api/v1/products?store=" + store + "&sku=" + productSku, HttpMethod.GET,
				new HttpEntity<>(getHeader()), ReadableProductList.class);
		assertEquals(OK, response.getStatusCode());
		assertNotNull(response.getBody());
		assertTrue("Expected the product to be listed", !response.getBody().getProducts().isEmpty());
		return response.getBody().getProducts().get(0);
	}

	private ResponseEntity<Void> createStore(String code, String retailerStore) {
		PersistableMerchantStore store = new PersistableMerchantStore();
		store.setCode(code);
		store.setCurrency("CAD");
		store.setDefaultLanguage("en");
		store.setEmail(code.toLowerCase() + "@test.com");
		store.setName(code);
		store.setPhone("444-555-6666");
		store.setSupportedLanguages(Arrays.asList("en"));
		store.setRetailerStore(retailerStore);
		PersistableAddress address = new PersistableAddress();
		address.setAddress("121212 simple address");
		address.setPostalCode("12345");
		address.setCountry("US");
		address.setCity("FT LD");
		address.setStateProvince("FL");
		store.setAddress(address);
		return testRestTemplate.exchange("/api/v1/private/store",
				HttpMethod.POST, new HttpEntity<>(store, getHeader()), Void.class);
	}

	private HttpHeaders createAdmin(String storeCode, String suffix) {
		String email = "ownership-" + suffix + "@test.com";
		String password = "Password1";
		PersistableUser user = new PersistableUser();
		user.setDefaultLanguage("en");
		user.setEmailAddress(email);
		user.setUserName(email);
		user.setFirstName("Ownership");
		user.setLastName("Admin");
		user.setPassword(password);
		user.setRepeatPassword(password);
		user.setActive(true);
		user.setStore(storeCode);
		PersistableGroup group = new PersistableGroup();
		group.setName("ADMIN");
		user.getGroups().add(group);
		ResponseEntity<ReadableUser> response = testRestTemplate.exchange(
				"/api/v1/private/user/?store=" + storeCode, HttpMethod.POST,
				new HttpEntity<>(user, getHeader()), ReadableUser.class);
		assertEquals(OK, response.getStatusCode());
		return getHeader(email, password);
	}

	private PersistableProduct productBody(Long id, String productSku, String name) {
		return productBody(id, productSku, name,
				com.salesmanager.core.model.catalog.product.manufacturer.Manufacturer.DEFAULT_MANUFACTURER);
	}

	private PersistableProduct productBody(Long id, String productSku, String name, String manufacturerCode) {
		PersistableProduct product = product(productSku);
		product.setId(id);
		product.setSku(productSku);
		product.setPrice(BigDecimal.TEN);
		product.setAvailable(true);
		ProductSpecification specification = new ProductSpecification();
		specification.setManufacturer(manufacturerCode);
		product.setProductSpecifications(specification);
		product.getDescriptions().get(0).setName(name);
		return product;
	}

	private ProductSpecification defaultManufacturer() {
		ProductSpecification specifications = new ProductSpecification();
		specifications.setManufacturer(
				com.salesmanager.core.model.catalog.product.manufacturer.Manufacturer.DEFAULT_MANUFACTURER);
		return specifications;
	}
}
