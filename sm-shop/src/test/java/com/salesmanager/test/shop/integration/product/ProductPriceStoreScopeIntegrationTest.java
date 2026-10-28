package com.salesmanager.test.shop.integration.product;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.SpringBootTest.WebEnvironment;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.test.context.junit.jupiter.SpringExtension;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import com.salesmanager.core.business.exception.ServiceException;
import com.salesmanager.core.business.constants.Constants;
import com.salesmanager.core.business.services.catalog.product.availability.ProductAvailabilityService;
import com.salesmanager.core.business.services.catalog.product.price.ProductPriceService;
import com.salesmanager.core.business.services.merchant.MerchantStoreService;
import com.salesmanager.core.model.catalog.product.availability.ProductAvailability;
import com.salesmanager.core.model.catalog.product.price.ProductPrice;
import com.salesmanager.core.model.merchant.MerchantStore;
import com.salesmanager.shop.application.ShopApplication;
import com.salesmanager.shop.model.entity.Entity;
import com.salesmanager.shop.model.store.PersistableMerchantStore;
import com.salesmanager.shop.model.references.PersistableAddress;
import com.salesmanager.shop.model.catalog.product.product.PersistableProduct;
import com.salesmanager.test.shop.common.ServicesTestSupport;

@SpringBootTest(classes = ShopApplication.class, webEnvironment = WebEnvironment.RANDOM_PORT)
@ExtendWith(SpringExtension.class)
class ProductPriceStoreScopeIntegrationTest extends ServicesTestSupport {

	private static final String CHILD_STORE = "XSTB190";

	@Autowired
	private ProductPriceService productPriceService;

	@Autowired
	private ProductAvailabilityService productAvailabilityService;

	@Autowired
	private MerchantStoreService merchantStoreService;

	@Autowired
	private PlatformTransactionManager transactionManager;

	@Test
	void priceQueriesAreScopedToStoreAndPreserveLegacyFallback() throws Exception {
		MerchantStore defaultStore = merchantStoreService.getByCode(Constants.DEFAULT_STORE);
		ensureChildStore();
		MerchantStore childStore = merchantStoreService.getByCode(CHILD_STORE);

		String defaultSku = sku("SCOPEDEFAULT");
		AvailabilityIds standard = createProductAndReadIds(defaultSku, defaultStore);
		assertFalse(productPriceService.findByProductSku(defaultSku, childStore).stream()
				.anyMatch(price -> price.getId().equals(standard.priceId())));
		assertEquals(standard.priceId(), productPriceService
				.findById(standard.priceId(), defaultSku, defaultStore).getId());
		assertNull(productPriceService.findById(standard.priceId(), defaultSku, childStore));
		assertTrue(productPriceService.findByInventoryId(standard.availabilityId(), defaultSku, defaultStore)
				.stream().anyMatch(price -> price.getId().equals(standard.priceId())));
		assertFalse(productPriceService.findByInventoryId(standard.availabilityId(), defaultSku, childStore)
				.stream().anyMatch(price -> price.getId().equals(standard.priceId())));

		mutateAvailability(standard.availabilityId(), defaultStore, null);
		assertTrue(productPriceService.findByProductSku(defaultSku, defaultStore).stream()
				.anyMatch(price -> price.getId().equals(standard.priceId())));
		assertFalse(productPriceService.findByProductSku(defaultSku, childStore).stream()
				.anyMatch(price -> price.getId().equals(standard.priceId())));

		String childSku = sku("SCOPECHILD");
		AvailabilityIds childInventory = createProductAndReadIds(childSku, defaultStore);
		mutateAvailability(childInventory.availabilityId(), defaultStore, childStore);
		assertTrue(productPriceService.findByProductSku(childSku, childStore).stream()
				.anyMatch(price -> price.getId().equals(childInventory.priceId())));
		assertFalse(productPriceService.findByProductSku(childSku, defaultStore).stream()
				.anyMatch(price -> price.getId().equals(childInventory.priceId())));
	}

	private String sku(String prefix) {
		return prefix + Long.toString(System.nanoTime(), 36).toUpperCase();
	}

	private AvailabilityIds createProductAndReadIds(String sku, MerchantStore store) throws Exception {
		PersistableProduct product = product(sku);
		product.setSku(sku);
		product.setAvailable(true);
		ResponseEntity<Entity> response = testRestTemplate.postForEntity(
				"/api/v1/private/product?store=" + store.getCode(),
				new HttpEntity<>(product, getHeader()), Entity.class);
		assertEquals(HttpStatus.CREATED, response.getStatusCode());

		return new TransactionTemplate(transactionManager).execute(status -> {
			List<ProductAvailability> availabilities = productAvailabilityService.getBySku(sku, 0, 100)
					.getContent();
			ProductAvailability availability = availabilities.get(0);
			ProductPrice price = availability.getPrices().iterator().next();
			return new AvailabilityIds(availability.getId(), price.getId());
		});
	}

	private void mutateAvailability(Long id, MerchantStore queryStore, MerchantStore newStore) {
		new TransactionTemplate(transactionManager).executeWithoutResult(status -> {
			Optional<ProductAvailability> found = productAvailabilityService.getById(id, queryStore);
			ProductAvailability availability = found.orElseThrow();
			availability.setMerchantStore(newStore);
			try {
				productAvailabilityService.saveOrUpdate(availability);
			} catch (ServiceException e) {
				throw new IllegalStateException(e);
			}
		});
	}

	private void ensureChildStore() {
		String unique = testRestTemplate.getForObject("/api/v1/store/unique?code=" + CHILD_STORE, String.class);
		if (unique != null && unique.contains("true")) {
			return;
		}
		PersistableMerchantStore store = new PersistableMerchantStore();
		store.setCode(CHILD_STORE);
		store.setCurrency("CAD");
		store.setDefaultLanguage("en");
		store.setEmail("xstb190@example.com");
		store.setName("XSTB190");
		store.setPhone("444-555-0190");
		store.setSupportedLanguages(List.of("en"));
		PersistableAddress address = new PersistableAddress();
		address.setAddress("1 Test Road");
		address.setPostalCode("12345");
		address.setCountry("US");
		address.setCity("Test City");
		address.setStateProvince("CA");
		store.setAddress(address);
		ResponseEntity<Void> response = testRestTemplate.postForEntity("/api/v1/private/store",
				new HttpEntity<>(store, getHeader()), Void.class);
		assertEquals(HttpStatus.OK, response.getStatusCode());
	}

	private static class AvailabilityIds {
		private final Long availabilityId;
		private final Long priceId;

		private AvailabilityIds(Long availabilityId, Long priceId) {
			this.availabilityId = availabilityId;
			this.priceId = priceId;
		}

		private Long availabilityId() {
			return availabilityId;
		}

		private Long priceId() {
			return priceId;
		}
	}
}
