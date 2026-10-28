package com.salesmanager.test.shop.integration.product;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.is;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import java.math.BigDecimal;
import java.util.Arrays;
import java.util.List;
import java.util.Map;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.SpringBootTest.WebEnvironment;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.test.context.junit4.SpringRunner;

import com.salesmanager.shop.application.ShopApplication;
import com.salesmanager.shop.model.catalog.product.PersistableProductPrice;
import com.salesmanager.shop.model.catalog.product.ReadableProduct;
import com.salesmanager.shop.model.references.PersistableAddress;
import com.salesmanager.shop.model.store.PersistableMerchantStore;
import com.salesmanager.test.shop.common.ServicesTestSupport;

/**
 * Drives ProductPriceApi through the real Spring context with a store that does not own the product:
 * cross-store list / create / overwrite / delete must not see or touch the owner's prices (404).
 */
@SpringBootTest(classes = ShopApplication.class, webEnvironment = WebEnvironment.RANDOM_PORT)
@RunWith(SpringRunner.class)
public class ProductPriceStoreScopeIntegrationTest extends ServicesTestSupport {

	private static final String OWNER = "DEFAULT";
	private static final String OTHER = "PRICEIDOR";
	private static final ParameterizedTypeReference<Map<String, Object>> MAP = new ParameterizedTypeReference<Map<String, Object>>() {
	};

	private static int counter = 0;

	private String sku;
	private Long inventoryId;
	private Long priceId;
	private List<Map<String, Object>> ownerPricesBefore;

	@Before
	public void createOwnerProductAndOtherStore() {
		createOtherStore();
		sku = "PRICEIDOR" + System.nanoTime() + (counter++);
		ReadableProduct product = sampleProduct(sku);
		assertNotNull(product);
		ResponseEntity<Map<String, Object>> inventories = inventoriesOf(OWNER);
		assertThat(inventories.getStatusCode(), is(HttpStatus.OK));
		List<?> items = (List<?>) inventories.getBody().get("items");
		assertEquals(1, items.size());
		inventoryId = ((Number) ((Map<?, ?>) items.get(0)).get("id")).longValue();

		List<Map<String, Object>> prices = ownerPrices();
		assertFalse("owner inventory has a price", prices.isEmpty());
		priceId = ((Number) prices.get(0).get("id")).longValue();
		ownerPricesBefore = prices;
	}

	@Test
	public void otherStoreCannotListOwnerPrices() {
		assertThat(listBySku(OTHER).getStatusCode(), is(HttpStatus.NOT_FOUND));
		assertThat(listByInventory(OTHER).getStatusCode(), is(HttpStatus.NOT_FOUND));
	}

	@Test
	public void otherStoreCannotAddPriceToOwnerInventory() {
		ResponseEntity<String> response = testRestTemplate.exchange(
				"/api/v1/private/product/" + sku + "/inventory/" + inventoryId + "/price?store=" + OTHER, HttpMethod.POST,
				new HttpEntity<>(price("0.02"), getHeader()), String.class);
		assertThat(response.getStatusCode(), is(HttpStatus.NOT_FOUND));
		assertOwnerPricesUnchanged();
	}

	@Test
	public void otherStoreCannotOverwriteOwnerPrice() {
		ResponseEntity<String> response = testRestTemplate.exchange(
				"/api/v1/private/product/" + sku + "/inventory/" + inventoryId + "/price/" + priceId + "?store=" + OTHER,
				HttpMethod.PUT, new HttpEntity<>(price("0.01"), getHeader()), String.class);
		assertThat(response.getStatusCode(), is(HttpStatus.NOT_FOUND));
		assertOwnerPricesUnchanged();
	}

	@Test
	public void otherStoreCannotDeleteOwnerPrice() {
		ResponseEntity<String> response = testRestTemplate.exchange(
				"/api/v1/private/product/" + sku + "/price/" + priceId + "?store=" + OTHER, HttpMethod.DELETE,
				new HttpEntity<>(getHeader()), String.class);
		assertThat(response.getStatusCode(), is(HttpStatus.NOT_FOUND));
		assertOwnerPricesUnchanged();
	}

	@Test
	public void ownerUpdateThroughAnotherInventoryIsNotFound() {
		ResponseEntity<String> response = testRestTemplate.exchange(
				"/api/v1/private/product/" + sku + "/inventory/" + (inventoryId + 100000) + "/price/" + priceId + "?store="
						+ OWNER,
				HttpMethod.PUT, new HttpEntity<>(price("0.03"), getHeader()), String.class);
		assertThat(response.getStatusCode(), is(HttpStatus.NOT_FOUND));
		assertOwnerPricesUnchanged();
	}

	@Test
	public void ownerStoreCanAddUpdateAndDeleteItsPrices() {
		ResponseEntity<Map<String, Object>> created = testRestTemplate.exchange(
				"/api/v1/private/product/" + sku + "/inventory/" + inventoryId + "/price?store=" + OWNER, HttpMethod.POST,
				new HttpEntity<>(price("77.00"), getHeader()), MAP);
		assertThat(created.getStatusCode(), is(HttpStatus.OK));
		long newPriceId = ((Number) created.getBody().get("id")).longValue();
		assertEquals(ownerPricesBefore.size() + 1, ownerPrices().size());

		ResponseEntity<String> updated = testRestTemplate.exchange(
				"/api/v1/private/product/" + sku + "/inventory/" + inventoryId + "/price/" + newPriceId + "?store=" + OWNER,
				HttpMethod.PUT, new HttpEntity<>(price("88.00"), getHeader()), String.class);
		assertThat(updated.getStatusCode(), is(HttpStatus.OK));
		assertTrue(ownerPrices().stream().anyMatch(p -> ((Number) p.get("id")).longValue() == newPriceId
				&& String.valueOf(p.get("originalPrice")).contains("88")));

		ResponseEntity<String> deleted = testRestTemplate.exchange(
				"/api/v1/private/product/" + sku + "/price/" + newPriceId + "?store=" + OWNER, HttpMethod.DELETE,
				new HttpEntity<>(getHeader()), String.class);
		assertThat(deleted.getStatusCode(), is(HttpStatus.OK));
		assertOwnerPricesUnchanged();

		ResponseEntity<String> deletedAgain = testRestTemplate.exchange(
				"/api/v1/private/product/" + sku + "/price/" + newPriceId + "?store=" + OWNER, HttpMethod.DELETE,
				new HttpEntity<>(getHeader()), String.class);
		assertThat(deletedAgain.getStatusCode(), is(HttpStatus.NOT_FOUND));
	}

	private void assertOwnerPricesUnchanged() {
		assertEquals(ownerPricesBefore, ownerPrices());
	}

	private ResponseEntity<Map<String, Object>> inventoriesOf(String store) {
		return testRestTemplate.exchange("/api/v1/private/product/" + sku + "/inventory?store=" + store,
				HttpMethod.GET, new HttpEntity<>(getHeader()), MAP);
	}

	@SuppressWarnings("unchecked")
	private List<Map<String, Object>> ownerPrices() {
		ResponseEntity<Map<String, Object>> inventories = inventoriesOf(OWNER);
		assertThat(inventories.getStatusCode(), is(HttpStatus.OK));
		for (Object item : (List<?>) inventories.getBody().get("items")) {
			Map<String, Object> inventory = (Map<String, Object>) item;
			if (((Number) inventory.get("id")).longValue() == inventoryId) {
				return (List<Map<String, Object>>) inventory.get("prices");
			}
		}
		throw new AssertionError("owner inventory " + inventoryId + " not listed for " + OWNER);
	}

	private ResponseEntity<String> listBySku(String store) {
		return testRestTemplate.exchange("/api/v1/private/product/" + sku + "/prices?store=" + store, HttpMethod.GET,
				new HttpEntity<>(getHeader()), String.class);
	}

	private ResponseEntity<String> listByInventory(String store) {
		return testRestTemplate.exchange(
				"/api/v1/private/product/" + sku + "/inventory/" + inventoryId + "/price?store=" + store, HttpMethod.GET,
				new HttpEntity<>(getHeader()), String.class);
	}

	private PersistableProductPrice price(String amount) {
		PersistableProductPrice price = new PersistableProductPrice();
		price.setPrice(new BigDecimal(amount));
		price.setDefaultPrice(false);
		return price;
	}

	private void createOtherStore() {
		ResponseEntity<String> existing = testRestTemplate.exchange("/api/v1/store/" + OTHER, HttpMethod.GET,
				new HttpEntity<>(getHeader()), String.class);
		if (existing.getStatusCode() == HttpStatus.OK) {
			return;
		}
		PersistableAddress address = new PersistableAddress();
		address.setAddress("1 Other street");
		address.setPostalCode("12345");
		address.setCountry("US");
		address.setCity("Other city");
		address.setStateProvince("FL");

		PersistableMerchantStore store = new PersistableMerchantStore();
		store.setCode(OTHER);
		store.setCurrency("USD");
		store.setDefaultLanguage("en");
		store.setEmail("other@priceidor.com");
		store.setName(OTHER);
		store.setPhone("444-555-6666");
		store.setSupportedLanguages(Arrays.asList("en"));
		store.setAddress(address);
		ResponseEntity<String> created = testRestTemplate.exchange("/api/v1/private/store/", HttpMethod.POST,
				new HttpEntity<>(store, getHeader()), String.class);
		assertThat(created.getStatusCode(), is(HttpStatus.OK));
	}
}
