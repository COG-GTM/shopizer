package com.salesmanager.test.shop.integration.order;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import javax.persistence.EntityManager;
import javax.persistence.PersistenceContext;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.ResponseEntity;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import com.fasterxml.jackson.databind.JsonNode;
import com.salesmanager.core.business.repositories.catalog.product.ProductRepository;
import com.salesmanager.core.model.catalog.product.Product;
import com.salesmanager.core.model.catalog.product.variant.ProductVariant;
import com.salesmanager.core.model.merchant.MerchantStore;
import com.salesmanager.shop.model.catalog.product.ReadableProduct;
import com.salesmanager.test.shop.common.InventoryJourneySupport;

class OrderInventoryIntegrationTest extends InventoryJourneySupport {

	@Autowired private ProductRepository productRepository;
	@Autowired private PlatformTransactionManager transactionManager;
	@PersistenceContext private EntityManager entityManager;

	@Test
	void skuLookupIsScopedToMerchantIncludingVariantSku() throws Exception {
		ReadableProduct product = stockedProduct("lookup", 10);
		MerchantStore store = merchantStoreService.getByCode("DEFAULT");
		MerchantStore other = new MerchantStore();
		other.setCode(unique("store"));
		other.setStorename("Other store");
		other.setStorephone("1234567890");
		other.setStorecity("Montreal");
		other.setStorepostalcode("H2H2H2");
		other.setStoreEmailAddress("other@example.com");
		other.setCountry(store.getCountry());
		other.setCurrency(store.getCurrency());
		other.setDefaultLanguage(store.getDefaultLanguage());
		other.getLanguages().add(store.getDefaultLanguage());
		merchantStoreService.create(other);
		String variantSku = unique("variant");
		new TransactionTemplate(transactionManager).execute(status -> {
			ProductVariant variant = new ProductVariant();
			variant.setProduct(entityManager.find(Product.class, product.getId()));
			variant.setSku(variantSku);
			entityManager.persist(variant);
			return null;
		});

		assertTrue(productRepository.findBySku(product.getSku(), other.getId()).isEmpty());
		assertEquals(product.getId().longValue(), ((Number) productRepository.findBySku(product.getSku(), store.getId()).get(0)).longValue());
		assertEquals(product.getId().longValue(), ((Number) productRepository.findBySku(variantSku, store.getId()).get(0)).longValue());
		assertTrue(productRepository.findBySku(variantSku, other.getId()).isEmpty());
	}

	@Test
	void authenticatedCheckoutDecrementsOnlyPurchasedProduct() throws Exception {
		HttpHeaders customer = customerHeaders();
		ReadableProduct purchased = stockedProduct("purchased", 10);
		ReadableProduct unrelated = stockedProduct("unrelated", 17);
		JsonNode cart = cart(purchased.getSku(), 3, customer);

		JsonNode confirmation = json(checkout(cart.get("code").asText(), customer), 200);

		assertEquals(7, stock(purchased.getId()));
		assertEquals(17, stock(unrelated.getId()));
		json(request("/api/v1/auth/orders/" + confirmation.get("id").asLong(), HttpMethod.GET, null, customer), 200);
	}

	@Test
	void stockReducedAfterCartCreationRejectsCheckoutWithoutCreatingOrder() throws Exception {
		HttpHeaders customer = customerHeaders();
		ReadableProduct purchased = stockedProduct("shortage", 10);
		JsonNode cart = cart(purchased.getSku(), 3, customer);
		jdbc.update("update SALESMANAGER.PRODUCT_AVAILABILITY set QUANTITY=2 where PRODUCT_ID=?", purchased.getId());
		long ordersBefore = jdbc.queryForObject("select count(*) from SALESMANAGER.ORDERS", Long.class);

		ResponseEntity<String> response = checkout(cart.get("code").asText(), customer);
		System.out.println("INSUFFICIENT_STOCK_HTTP " + response.getStatusCodeValue() + " " + response.getBody());

		assertTrue(response.getStatusCode().isError(), response.getBody());
		assertEquals(ordersBefore, jdbc.queryForObject("select count(*) from SALESMANAGER.ORDERS", Long.class));
		assertEquals(2, stock(purchased.getId()));
	}
}
