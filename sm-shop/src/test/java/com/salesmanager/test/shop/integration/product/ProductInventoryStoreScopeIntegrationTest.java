package com.salesmanager.test.shop.integration.product;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.UUID;

import javax.inject.Inject;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import com.salesmanager.core.business.constants.Constants;
import com.salesmanager.core.business.services.catalog.product.ProductService;
import com.salesmanager.core.business.services.catalog.product.availability.ProductAvailabilityService;
import com.salesmanager.core.business.services.merchant.MerchantStoreService;
import com.salesmanager.core.model.catalog.product.Product;
import com.salesmanager.core.model.catalog.product.availability.ProductAvailability;
import com.salesmanager.core.model.merchant.MerchantStore;
import com.salesmanager.shop.model.catalog.product.PersistableProductPrice;
import com.salesmanager.shop.model.catalog.product.inventory.PersistableInventory;
import com.salesmanager.shop.model.catalog.product.inventory.ReadableInventory;
import com.salesmanager.shop.model.catalog.product.ReadableProduct;
import com.salesmanager.shop.model.entity.Entity;
import com.salesmanager.shop.model.entity.ReadableEntityList;
import com.salesmanager.shop.model.references.PersistableAddress;
import com.salesmanager.shop.model.security.PersistableGroup;
import com.salesmanager.shop.model.store.PersistableMerchantStore;
import com.salesmanager.shop.model.user.PersistableUser;
import com.salesmanager.shop.model.user.ReadableUser;
import com.salesmanager.shop.model.catalog.product.product.PersistableProduct;
import com.salesmanager.shop.model.catalog.product.product.ProductSpecification;
import com.salesmanager.test.shop.common.ServicesTestSupport;

public class ProductInventoryStoreScopeIntegrationTest extends ServicesTestSupport {

	private static final String PASSWORD = "Password1";
	private String otherStoreCode;

	@Inject
	private ProductService productService;

	@Inject
	private ProductAvailabilityService productAvailabilityService;

	@Inject
	private MerchantStoreService merchantStoreService;

	@BeforeEach
	public void createOtherStore() {
		otherStoreCode = "OTHER" + UUID.randomUUID().toString().replace("-", "").substring(0, 12);
		PersistableAddress address = new PersistableAddress();
		address.setAddress("121212 simple address");
		address.setPostalCode("12345");
		address.setCountry("US");
		address.setCity("FT LD");
		address.setStateProvince("FL");

		PersistableMerchantStore store = new PersistableMerchantStore();
		store.setCode(otherStoreCode);
		store.setCurrency("CAD");
		store.setDefaultLanguage("en");
		store.setEmail("scope-" + otherStoreCode + "@example.com");
		store.setName(otherStoreCode);
		store.setPhone("444-555-6666");
		store.setSupportedLanguages(Arrays.asList("en"));
		store.setAddress(address);

		ResponseEntity<Void> response = testRestTemplate.exchange("/api/v1/private/store/", HttpMethod.POST,
				new HttpEntity<>(store, getHeader()), Void.class);
		assertEquals(HttpStatus.OK, response.getStatusCode());
	}

	@Test
	public void superadminCannotDeleteInventoryThroughAnotherStore() throws Exception {
		Long productId = createProduct().getId();
		Long inventoryId = addInventory(productId);

		assertEquals(HttpStatus.NOT_FOUND, delete(productId, inventoryId, otherStoreCode).getStatusCode());
		assertTrue(hasInventory(productId, inventoryId));
	}

	@Test
	public void storeAdminCannotDeleteAnotherStoresInventory() throws Exception {
		Long productId = createProduct().getId();
		Long inventoryId = addInventory(productId);
		HttpEntity<Void> otherStoreAdmin = new HttpEntity<>(createStoreAdminHeaders());

		ResponseEntity<Void> crossStoreDelete = testRestTemplate.exchange(deleteUrl(productId, inventoryId, otherStoreCode),
				HttpMethod.DELETE, otherStoreAdmin, Void.class);
		assertEquals(HttpStatus.NOT_FOUND, crossStoreDelete.getStatusCode());
		assertTrue(hasInventory(productId, inventoryId));

		ResponseEntity<Void> defaultStoreDelete = testRestTemplate.exchange(
				deleteUrl(productId, inventoryId, Constants.DEFAULT_STORE), HttpMethod.DELETE, otherStoreAdmin,
				Void.class);
		System.out.println("Other-store admin deleting DEFAULT inventory returned " + defaultStoreDelete.getStatusCode());
		assertFalse(defaultStoreDelete.getStatusCode().is2xxSuccessful());
		assertTrue(hasInventory(productId, inventoryId));
	}

	@Test
	public void wrongProductIdDoesNotDeleteInventory() throws Exception {
		Long productId = createProduct().getId();
		Long inventoryId = addInventory(productId);
		Long otherProductId = createProduct().getId();

		assertEquals(HttpStatus.NOT_FOUND, delete(otherProductId, inventoryId, Constants.DEFAULT_STORE).getStatusCode());
		assertTrue(hasInventory(productId, inventoryId));
	}

	@Test
	public void unknownInventoryIdReturnsNotFound() throws Exception {
		Long productId = createProduct().getId();

		assertEquals(HttpStatus.NOT_FOUND,
				delete(productId, 9999999L, Constants.DEFAULT_STORE).getStatusCode());
	}

	@Test
	public void inventoryCanBeDeletedFromItsOwnStore() throws Exception {
		Long productId = createProduct().getId();
		Long inventoryId = addInventory(productId);

		assertEquals(HttpStatus.OK, delete(productId, inventoryId, Constants.DEFAULT_STORE).getStatusCode());
		assertFalse(hasInventory(productId, inventoryId));
	}

	@Test
	public void productIdAbove127CanBeDeletedFromItsOwnStore() throws Exception {
		Long productId = null;
		for (int attempt = 0; attempt < 400 && (productId == null || productId <= 127); attempt++) {
			String sku = "scope" + UUID.randomUUID().toString().replace("-", "").substring(0, 16);
			PersistableProduct product = product(sku);
			product.setProductSpecifications(new ProductSpecification());
			product.getProductSpecifications().setManufacturer(
					com.salesmanager.core.model.catalog.product.manufacturer.Manufacturer.DEFAULT_MANUFACTURER);
			product.setAvailable(true);
			product.setPrice(BigDecimal.TEN);
			product.setSku(sku);
			product.setQuantity(100);

			ResponseEntity<Entity> response = testRestTemplate.postForEntity(
					"/api/v1/private/product?store=" + Constants.DEFAULT_STORE,
					new HttpEntity<>(product, getHeader()), Entity.class);
			assertEquals(HttpStatus.CREATED, response.getStatusCode());
			assertNotNull(response.getBody());
			productId = response.getBody().getId();
			normalizeAvailabilityStore(productId);
		}
		assertNotNull(productId, "No product was created within the 400-attempt cap");
		assertTrue(productId > 127, "Product id did not exceed 127 within the 400-attempt cap");

		Long inventoryId = addInventory(productId);
		assertEquals(HttpStatus.OK, delete(productId, inventoryId, Constants.DEFAULT_STORE).getStatusCode());
		assertFalse(hasInventory(productId, inventoryId));
	}

	private ReadableProduct createProduct() {
		String sku = "scope" + UUID.randomUUID().toString().replace("-", "").substring(0, 16);
		ReadableProduct product = sampleProduct(sku);
		assertNotNull(product);
		assertNotNull(product.getId());
		normalizeAvailabilityStore(product.getId());
		return product;
	}

	private void normalizeAvailabilityStore(Long productId) {
		try {
			MerchantStore store = merchantStoreService.getByCode(Constants.DEFAULT_STORE);
			Product product = productService.retrieveById(productId, store).orElseThrow(
					() -> new AssertionError("Created product was not found in DEFAULT"));
			for (ProductAvailability availability : product.getAvailabilities()) {
				if (availability.getMerchantStore() == null) {
					availability.setMerchantStore(store);
					productAvailabilityService.saveOrUpdate(availability);
				}
			}
		} catch (Exception e) {
			throw new AssertionError("Could not prepare product availability for inventory API", e);
		}
	}

	private Long addInventory(Long productId) {
		PersistableInventory inventory = new PersistableInventory();
		inventory.setProductId(productId);
		inventory.setQuantity(5);
		inventory.setRegion("*");
		inventory.setAvailable(true);
		PersistableProductPrice price = new PersistableProductPrice();
		price.setDefaultPrice(true);
		price.setPrice(BigDecimal.TEN);
		inventory.setPrices(new ArrayList<>(Arrays.asList(price)));

		ResponseEntity<ReadableInventory> response = testRestTemplate.postForEntity(
				"/api/v1/private/product/" + productId + "/inventory?store=" + Constants.DEFAULT_STORE,
				new HttpEntity<>(inventory, getHeader()), ReadableInventory.class);
		assertEquals(HttpStatus.CREATED, response.getStatusCode());
		assertNotNull(response.getBody());
		assertNotNull(response.getBody().getId());
		return response.getBody().getId();
	}

	private boolean hasInventory(Long productId, Long inventoryId) {
		ResponseEntity<ReadableEntityList<ReadableInventory>> response = testRestTemplate.exchange(
				"/api/v1/private/product/inventory?productId=" + productId + "&store=" + Constants.DEFAULT_STORE,
				HttpMethod.GET, new HttpEntity<>(getHeader()),
				new ParameterizedTypeReference<ReadableEntityList<ReadableInventory>>() {
				});
		assertEquals(HttpStatus.OK, response.getStatusCode());
		assertNotNull(response.getBody());
		return response.getBody().getItems().stream().anyMatch(item -> inventoryId.equals(item.getId()));
	}

	private ResponseEntity<Void> delete(Long productId, Long inventoryId, String storeCode) {
		return testRestTemplate.exchange(deleteUrl(productId, inventoryId, storeCode), HttpMethod.DELETE,
				new HttpEntity<>(getHeader()), Void.class);
	}

	private String deleteUrl(Long productId, Long inventoryId, String storeCode) {
		return "/api/v1/private/product/" + productId + "/inventory/" + inventoryId + "?store=" + storeCode;
	}

	private org.springframework.http.HttpHeaders createStoreAdminHeaders() {
		String email = "scope-admin-" + UUID.randomUUID().toString().replace("-", "") + "@example.com";
		PersistableUser user = new PersistableUser();
		user.setDefaultLanguage("en");
		user.setEmailAddress(email);
		user.setFirstName("Scope");
		user.setLastName("Admin");
		user.setUserName(email);
		user.setPassword(PASSWORD);
		user.setRepeatPassword(PASSWORD);
		user.setStore(otherStoreCode);
		user.setActive(true);

		PersistableGroup group = new PersistableGroup();
		group.setName("ADMIN");
		user.getGroups().add(group);
		ResponseEntity<ReadableUser> created = testRestTemplate.postForEntity("/api/v1/private/user/",
				new HttpEntity<>(user, getHeader()), ReadableUser.class);
		assertEquals(HttpStatus.OK, created.getStatusCode());

		return getHeader(email, PASSWORD);
	}
}
