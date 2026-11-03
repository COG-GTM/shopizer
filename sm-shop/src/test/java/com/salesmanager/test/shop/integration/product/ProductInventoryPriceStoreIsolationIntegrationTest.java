package com.salesmanager.test.shop.integration.product;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import java.math.BigDecimal;
import java.util.Arrays;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.SpringBootTest.WebEnvironment;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.test.context.junit4.SpringRunner;

import com.salesmanager.core.business.constants.Constants;
import com.salesmanager.shop.application.ShopApplication;
import com.salesmanager.shop.model.catalog.product.PersistableProductPrice;
import com.salesmanager.shop.model.catalog.product.ReadableProduct;
import com.salesmanager.shop.model.references.PersistableAddress;
import com.salesmanager.shop.model.store.PersistableMerchantStore;
import com.salesmanager.test.shop.common.ServicesTestSupport;

@SpringBootTest(classes = ShopApplication.class, webEnvironment = WebEnvironment.RANDOM_PORT)
@RunWith(SpringRunner.class)
public class ProductInventoryPriceStoreIsolationIntegrationTest extends ServicesTestSupport {

	private static final String OTHER_STORE = "isolation";
	private static final String SKU = "isolation-sku";

	@Test
	public void inventoryAndPricesAreNotReachableFromAnotherStore() throws Exception {

		ReadableProduct product = sampleProduct(SKU);
		createOtherStore();

		JsonNode inventories = json(call(HttpMethod.GET,
				"/api/v1/private/product/" + SKU + "/inventory?store=" + Constants.DEFAULT_STORE, null)).get("items");
		assertTrue(inventories.size() > 0);
		Long inventoryId = inventories.get(0).get("id").asLong();

		JsonNode prices = inventories.get(0).get("prices");
		assertTrue(prices.size() > 0);
		Long priceId = prices.get(0).get("id").asLong();

		// read inventory by sku from another store
		ResponseEntity<String> read = call(HttpMethod.GET,
				"/api/v1/private/product/" + SKU + "/inventory?store=" + OTHER_STORE, null);
		assertTrue(read.getBody(), read.getStatusCode() != HttpStatus.OK || !read.getBody().contains("\"id\":" + inventoryId));

		// delete inventory from another store
		ResponseEntity<String> delete = call(HttpMethod.DELETE,
				"/api/v1/private/product/" + product.getId() + "/inventory/" + inventoryId + "?store=" + OTHER_STORE, null);
		assertEquals(HttpStatus.NOT_FOUND, delete.getStatusCode());

		// attach a new price to another store's inventory
		PersistableProductPrice newPrice = new PersistableProductPrice();
		newPrice.setPrice(new BigDecimal("0.01"));
		newPrice.setDefaultPrice(true);
		ResponseEntity<String> create = call(HttpMethod.POST,
				"/api/v1/private/product/" + SKU + "/inventory/" + inventoryId + "/price?store=" + OTHER_STORE, newPrice);
		assertFalse(create.getBody(), create.getStatusCode().is2xxSuccessful());

		// overwrite another store's price by id
		ResponseEntity<String> overwrite = call(HttpMethod.PUT,
				"/api/v1/private/product/" + SKU + "/inventory/" + inventoryId + "/price/" + priceId + "?store=" + OTHER_STORE, newPrice);
		assertFalse(overwrite.getBody(), overwrite.getStatusCode().is2xxSuccessful());

		// delete another store's price
		ResponseEntity<String> deletePrice = call(HttpMethod.DELETE,
				"/api/v1/private/product/" + SKU + "/price/" + priceId + "?store=" + OTHER_STORE, null);
		assertFalse(deletePrice.getBody(), deletePrice.getStatusCode().is2xxSuccessful());

		// owning store data is unchanged
		JsonNode after = json(call(HttpMethod.GET,
				"/api/v1/private/product/" + SKU + "/inventory?store=" + Constants.DEFAULT_STORE, null)).get("items");
		assertEquals(inventories, after);

		// owning store can still read its inventory by sku
		ResponseEntity<String> ownRead = call(HttpMethod.GET,
				"/api/v1/private/product/" + SKU + "/inventory?store=" + Constants.DEFAULT_STORE, null);
		assertEquals(HttpStatus.OK, ownRead.getStatusCode());
		assertTrue(ownRead.getBody(), ownRead.getBody().contains("\"id\":" + inventoryId));

		// owning store can still update its price by id
		ResponseEntity<String> ownUpdate = call(HttpMethod.PUT,
				"/api/v1/private/product/" + SKU + "/inventory/" + inventoryId + "/price/" + priceId + "?store=" + Constants.DEFAULT_STORE, newPrice);
		assertTrue(ownUpdate.getBody(), ownUpdate.getStatusCode().is2xxSuccessful());
		JsonNode updated = json(call(HttpMethod.GET,
				"/api/v1/private/product/" + SKU + "/inventory?store=" + Constants.DEFAULT_STORE, null)).get("items");
		assertEquals(priceId.longValue(), updated.get(0).get("prices").get(0).get("id").asLong());
		assertTrue(updated.toString(), updated.get(0).get("prices").get(0).get("originalPrice").asText().contains("0.01"));
	}

	private JsonNode json(ResponseEntity<String> response) throws Exception {
		assertEquals(response.getBody(), HttpStatus.OK, response.getStatusCode());
		return new ObjectMapper().readTree(response.getBody());
	}

	private ResponseEntity<String> call(HttpMethod method, String url, Object body) {
		return testRestTemplate.exchange(url, method, new HttpEntity<>(body, getHeader()), String.class);
	}

	private void createOtherStore() {
		PersistableAddress address = new PersistableAddress();
		address.setAddress("1 isolation street");
		address.setPostalCode("12345");
		address.setCountry("US");
		address.setCity("FT LD");
		address.setStateProvince("FL");

		PersistableMerchantStore store = new PersistableMerchantStore();
		store.setCode(OTHER_STORE);
		store.setCurrency("USD");
		store.setDefaultLanguage("en");
		store.setEmail("isolation@test.com");
		store.setName(OTHER_STORE);
		store.setPhone("444-555-6666");
		store.setSupportedLanguages(Arrays.asList("en"));
		store.setAddress(address);

		ResponseEntity<Void> response = testRestTemplate.exchange("/api/v1/private/store/", HttpMethod.POST,
				new HttpEntity<>(store, getHeader()), Void.class);
		assertEquals(HttpStatus.OK, response.getStatusCode());
	}
}
