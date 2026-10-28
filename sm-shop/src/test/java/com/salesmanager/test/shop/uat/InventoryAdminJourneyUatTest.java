package com.salesmanager.test.shop.uat;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.UUID;

import javax.inject.Inject;

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
import com.salesmanager.shop.model.entity.ReadableEntityList;
import com.salesmanager.shop.model.references.PersistableAddress;
import com.salesmanager.shop.model.store.PersistableMerchantStore;
import com.salesmanager.test.shop.common.ServicesTestSupport;

public class InventoryAdminJourneyUatTest extends ServicesTestSupport {

	@Inject
	private ProductService productService;

	@Inject
	private ProductAvailabilityService productAvailabilityService;

	@Inject
	private MerchantStoreService merchantStoreService;

	@Test
	public void adminCanManageInventoryAndCannotDeleteAcrossStores() throws Exception {
		String otherStore = createOtherStore();
		ReadableProduct product = sampleProduct("journey" + UUID.randomUUID().toString().replace("-", "").substring(0, 12));
		assertNotNull(product);
		assertNotNull(product.getId());
		normalizeAvailabilityStore(product.getId());

		PersistableInventory inventory = new PersistableInventory();
		inventory.setProductId(product.getId());
		inventory.setQuantity(4);
		inventory.setRegion("*");
		inventory.setAvailable(true);
		PersistableProductPrice price = new PersistableProductPrice();
		price.setDefaultPrice(true);
		price.setPrice(BigDecimal.TEN);
		inventory.setPrices(new ArrayList<>(Arrays.asList(price)));
		ResponseEntity<ReadableInventory> created = testRestTemplate.postForEntity(
				"/api/v1/private/product/" + product.getId() + "/inventory?store=" + Constants.DEFAULT_STORE,
				new HttpEntity<>(inventory, getHeader()), ReadableInventory.class);
		assertEquals(HttpStatus.CREATED, created.getStatusCode());
		assertNotNull(created.getBody());
		Long inventoryId = created.getBody().getId();
		assertNotNull(inventoryId);
		assertTrue(hasInventory(product.getId(), inventoryId));

		ResponseEntity<Void> crossStoreDelete = testRestTemplate.exchange(deleteUrl(product.getId(), inventoryId, otherStore),
				HttpMethod.DELETE, new HttpEntity<>(getHeader()), Void.class);
		assertEquals(HttpStatus.NOT_FOUND, crossStoreDelete.getStatusCode());
		assertTrue(hasInventory(product.getId(), inventoryId));

		ResponseEntity<Void> ownStoreDelete = testRestTemplate.exchange(
				deleteUrl(product.getId(), inventoryId, Constants.DEFAULT_STORE), HttpMethod.DELETE,
				new HttpEntity<>(getHeader()), Void.class);
		assertEquals(HttpStatus.OK, ownStoreDelete.getStatusCode());
		assertFalse(hasInventory(product.getId(), inventoryId));
	}

	private String createOtherStore() {
		String code = "JOURNEY" + UUID.randomUUID().toString().replace("-", "").substring(0, 10);
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
		store.setEmail("journey-" + code + "@example.com");
		store.setName(code);
		store.setPhone("444-555-6666");
		store.setSupportedLanguages(Arrays.asList("en"));
		store.setAddress(address);

		ResponseEntity<Void> response = testRestTemplate.exchange("/api/v1/private/store/", HttpMethod.POST,
				new HttpEntity<>(store, getHeader()), Void.class);
		assertEquals(HttpStatus.OK, response.getStatusCode());
		return code;
	}

	private void normalizeAvailabilityStore(Long productId) throws Exception {
		MerchantStore store = merchantStoreService.getByCode(Constants.DEFAULT_STORE);
		Product product = productService.retrieveById(productId, store)
				.orElseThrow(() -> new AssertionError("Created product was not found in DEFAULT"));
		for (ProductAvailability availability : product.getAvailabilities()) {
			if (availability.getMerchantStore() == null) {
				availability.setMerchantStore(store);
				productAvailabilityService.saveOrUpdate(availability);
			}
		}
	}

	private boolean hasInventory(Long productId, Long inventoryId) {
		ResponseEntity<ReadableEntityList<com.salesmanager.shop.model.catalog.product.inventory.ReadableInventory>> response =
				testRestTemplate.exchange(
						"/api/v1/private/product/inventory?productId=" + productId + "&store=" + Constants.DEFAULT_STORE,
						HttpMethod.GET, new HttpEntity<>(getHeader()),
						new ParameterizedTypeReference<ReadableEntityList<com.salesmanager.shop.model.catalog.product.inventory.ReadableInventory>>() {
						});
		assertEquals(HttpStatus.OK, response.getStatusCode());
		assertNotNull(response.getBody());
		return response.getBody().getItems().stream().anyMatch(item -> inventoryId.equals(item.getId()));
	}

	private String deleteUrl(Long productId, Long inventoryId, String store) {
		return "/api/v1/private/product/" + productId + "/inventory/" + inventoryId + "?store=" + store;
	}
}
