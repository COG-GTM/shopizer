package com.salesmanager.test.shop.integration.inventory;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import java.math.BigDecimal;
import java.util.Arrays;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.SpringBootTest.WebEnvironment;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.test.context.junit4.SpringRunner;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.salesmanager.core.model.merchant.MerchantStore;
import com.salesmanager.shop.application.ShopApplication;
import com.salesmanager.shop.model.catalog.product.product.PersistableProduct;
import com.salesmanager.shop.model.entity.Entity;
import com.salesmanager.shop.model.references.PersistableAddress;
import com.salesmanager.shop.model.store.PersistableMerchantStore;
import com.salesmanager.test.shop.common.ServicesTestSupport;

/**
 * GET /api/v1/private/product/{sku}/inventory?store=X must only return inventory of store X.
 */
@SpringBootTest(classes = ShopApplication.class, webEnvironment = WebEnvironment.RANDOM_PORT)
@RunWith(SpringRunner.class)
public class ProductInventoryBySkuStoreScopeIntegrationTest extends ServicesTestSupport {

	private static final ObjectMapper JSON = new ObjectMapper();

	@Test
	public void skuThatOnlyExistsInAnotherStoreIsNotReturned() throws Exception {
		String other = "ITINVB";
		createStore(other);
		String sku = "IT-INV-ONLY-OTHER";
		createProduct(other, sku, 42);

		JsonNode fromDefault = inventory(sku, MerchantStore.DEFAULT_STORE);
		assertEquals(0, fromDefault.get("recordsTotal").asLong());
		assertEquals(0, fromDefault.get("items").size());

		JsonNode fromOwner = inventory(sku, other);
		assertEquals(1, fromOwner.get("items").size());
		assertEquals(42, fromOwner.get("items").get(0).get("quantity").asInt());
	}

	@Test
	public void inventoryPagingKeepsContract() throws Exception {
		String sku = "IT-INV-PAGING";
		createProduct(MerchantStore.DEFAULT_STORE, sku, 3);
		JsonNode page = inventory(sku + "?page=0&count=1", MerchantStore.DEFAULT_STORE);
		for (String field : Arrays.asList("items", "recordsTotal", "totalPages", "number")) {
			assertTrue("missing " + field, page.has(field));
		}
		JsonNode item = page.get("items").get(0);
		for (String field : Arrays.asList("id", "quantity", "region", "store", "sku", "prices", "price")) {
			assertTrue("missing " + field, item.has(field));
		}
	}

	private JsonNode inventory(String skuAndQuery, String store) throws Exception {
		String sep = skuAndQuery.contains("?") ? "&" : "?";
		String sku = skuAndQuery.contains("?") ? skuAndQuery.substring(0, skuAndQuery.indexOf('?')) : skuAndQuery;
		String query = skuAndQuery.contains("?") ? skuAndQuery.substring(skuAndQuery.indexOf('?')) : "";
		ResponseEntity<String> response = testRestTemplate.exchange(
				"/api/v1/private/product/" + sku + "/inventory" + query + sep + "store=" + store, HttpMethod.GET,
				new HttpEntity<>(getHeader()), String.class);
		assertEquals(response.getBody(), HttpStatus.OK, response.getStatusCode());
		return JSON.readTree(response.getBody());
	}

	protected void createStore(String code) {
		ResponseEntity<String> existing = testRestTemplate.exchange("/api/v1/store/" + code, HttpMethod.GET,
				new HttpEntity<>(getHeader()), String.class);
		if (existing.getStatusCode() == HttpStatus.OK) {
			return;
		}
		PersistableAddress address = new PersistableAddress();
		address.setAddress("1 Scope street");
		address.setPostalCode("12345");
		address.setCountry("US");
		address.setCity("Miami");
		address.setStateProvince("FL");
		PersistableMerchantStore store = new PersistableMerchantStore();
		store.setCode(code);
		store.setCurrency("USD");
		store.setDefaultLanguage("en");
		store.setEmail(code.toLowerCase() + "@test.com");
		store.setName(code);
		store.setPhone("444-555-6666");
		store.setSupportedLanguages(Arrays.asList("en"));
		store.setAddress(address);
		ResponseEntity<Void> response = testRestTemplate.exchange("/api/v1/private/store/", HttpMethod.POST,
				new HttpEntity<>(store, getHeader()), Void.class);
		assertEquals(HttpStatus.OK, response.getStatusCode());
	}

	protected void createProduct(String store, String sku, int quantity) {
		PersistableProduct product = product(sku);
		product.setSku(sku);
		product.setAvailable(true);
		product.setPrice(BigDecimal.TEN);
		product.setQuantity(quantity);
		product.getInventory().setQuantity(quantity);
		ResponseEntity<Entity> response = testRestTemplate.postForEntity("/api/v1/private/product?store=" + store,
				new HttpEntity<>(product, getHeader()), Entity.class);
		assertEquals(HttpStatus.CREATED, response.getStatusCode());
	}
}
