package com.salesmanager.test.shop.uat;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.springframework.http.HttpMethod.GET;
import static org.springframework.http.HttpStatus.OK;

import org.junit.jupiter.api.Test;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseEntity;

import com.salesmanager.shop.model.catalog.product.ReadableProduct;
import com.salesmanager.shop.model.catalog.product.ReadableProductList;
import com.salesmanager.shop.model.catalog.category.ReadableCategoryList;
import com.salesmanager.shop.model.store.ReadableMerchantStore;
import com.salesmanager.test.shop.common.ServicesTestSupport;

class BrowseJourneyUatTest extends ServicesTestSupport {

	@Test
	void visitorCanBrowseStoreCategoriesProductsAndProductDetail() {
		HttpHeaders headers = getHeader();
		HttpEntity<Void> entity = new HttpEntity<>(headers);

		ResponseEntity<ReadableMerchantStore> store = testRestTemplate.exchange(
				"/api/v1/store/DEFAULT", GET, entity, ReadableMerchantStore.class);
		assertEquals(OK, store.getStatusCode());
		assertNotNull(store.getBody());

		ResponseEntity<ReadableCategoryList> categories = testRestTemplate.exchange(
				"/api/v1/category?store=DEFAULT", GET, entity, ReadableCategoryList.class);
		assertEquals(OK, categories.getStatusCode());

		ResponseEntity<ReadableProductList> response = testRestTemplate.exchange(
				"/api/v1/products?store=DEFAULT", GET, entity, ReadableProductList.class);
		assertEquals(OK, response.getStatusCode());
		if (response.getBody().getProducts().isEmpty()) {
			sampleProduct("browse" + System.nanoTime());
			response = testRestTemplate.exchange("/api/v1/products?store=DEFAULT", GET,
					entity, ReadableProductList.class);
		}
		assertFalse(response.getBody().getProducts().isEmpty());

		String sku = response.getBody().getProducts().get(0).getSku();
		ResponseEntity<ReadableProduct> detail = testRestTemplate.exchange(
				"/api/v2/product/" + sku + "?store=DEFAULT", GET, entity, ReadableProduct.class);
		assertEquals(OK, detail.getStatusCode());
		assertEquals(sku, detail.getBody().getSku());
	}
}
