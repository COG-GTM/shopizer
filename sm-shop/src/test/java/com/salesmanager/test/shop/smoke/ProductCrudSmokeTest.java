package com.salesmanager.test.shop.smoke;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

import java.math.BigDecimal;
import java.util.Collections;

import org.junit.jupiter.api.Test;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpMethod;
import org.springframework.http.ResponseEntity;

import com.salesmanager.shop.model.catalog.product.ReadableProduct;
import com.salesmanager.shop.model.catalog.product.product.PersistableProduct;

class ProductCrudSmokeTest extends SmokeTestSupport {

	@Test
	void createsReadsUpdatesAndDeletesProduct() {
		ProductFixture fixture = createProduct("crud");
		String readPath = "/api/v2/product/" + fixture.product.getSku();
		ResponseEntity<ReadableProduct> created = testRestTemplate.exchange(readPath, HttpMethod.GET,
				new HttpEntity<>(bearerHeaders(fixture.adminToken)), ReadableProduct.class);
		assertEquals(200, created.getStatusCodeValue());
		assertNotNull(created.getBody());

		PersistableProduct update = product(fixture.product.getSku(), "updated-" + fixture.product.getSku(),
				new BigDecimal("42.00"));
		update.setId(fixture.id);
		update.setCategories(Collections.emptyList());
		ResponseEntity<Void> updated = testRestTemplate.exchange(
				"/api/v1/private/product/" + fixture.id + "?store=DEFAULT", HttpMethod.PUT,
				new HttpEntity<>(update, bearerHeaders(fixture.adminToken)), Void.class);
		assertEquals(200, updated.getStatusCodeValue());

		ResponseEntity<ReadableProduct> reread = testRestTemplate.exchange(readPath, HttpMethod.GET,
				new HttpEntity<>(bearerHeaders(fixture.adminToken)), ReadableProduct.class);
		assertEquals(200, reread.getStatusCodeValue());
		assertEquals("updated-" + fixture.product.getSku(), reread.getBody().getDescription().getName());
		assertEquals(new BigDecimal("42.00"), new BigDecimal(reread.getBody().getPrice().toString()));

		ResponseEntity<Void> deleted = testRestTemplate.exchange(
				"/api/v1/private/product/" + fixture.id + "?store=DEFAULT", HttpMethod.DELETE,
				new HttpEntity<>(bearerHeaders(fixture.adminToken)), Void.class);
		assertEquals(200, deleted.getStatusCodeValue());
		ResponseEntity<String> afterDelete = testRestTemplate.exchange(readPath, HttpMethod.GET,
				new HttpEntity<>(bearerHeaders(fixture.adminToken)), String.class);
		// The V2 read API returns 400 after deletion.
		assertEquals(400, afterDelete.getStatusCodeValue());
	}
}
