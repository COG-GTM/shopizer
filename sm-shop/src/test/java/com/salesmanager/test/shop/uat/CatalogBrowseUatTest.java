package com.salesmanager.test.shop.uat;

import static org.junit.jupiter.api.Assertions.assertEquals;

import com.salesmanager.shop.model.catalog.product.ReadableProduct;
import com.salesmanager.test.shop.common.ServicesTestSupport;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

class CatalogBrowseUatTest extends ServicesTestSupport {

	@Test
	void customerCanBrowseStoreCategoriesProductsAndProductDetail() {
		String sku = "uat-catalog-" + UUID.randomUUID().toString().replace("-", "");
		ReadableProduct product = sampleProduct(sku);

		ResponseEntity<String> store = testRestTemplate.getForEntity(
				"/api/v1/store/DEFAULT", String.class);
		ResponseEntity<String> categories = testRestTemplate.getForEntity(
				"/api/v1/category", String.class);
		ResponseEntity<String> products = testRestTemplate.getForEntity(
				"/api/v1/products", String.class);
		ResponseEntity<ReadableProduct> detail = testRestTemplate.getForEntity(
				"/api/v2/product/" + product.getSku(), ReadableProduct.class);

		assertEquals(HttpStatus.OK, store.getStatusCode());
		assertEquals(HttpStatus.OK, categories.getStatusCode());
		assertEquals(HttpStatus.OK, products.getStatusCode());
		assertEquals(HttpStatus.OK, detail.getStatusCode());
		assertEquals(sku, detail.getBody().getSku());
	}
}
