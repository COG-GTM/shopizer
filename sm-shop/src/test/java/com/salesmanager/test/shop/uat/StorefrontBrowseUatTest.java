package com.salesmanager.test.shop.uat;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.springframework.http.HttpStatus.OK;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.SpringBootTest.WebEnvironment;
import org.springframework.http.ResponseEntity;
import org.springframework.test.context.junit4.SpringRunner;

import com.salesmanager.shop.application.ShopApplication;
import com.salesmanager.shop.model.catalog.product.ReadableProduct;
import com.salesmanager.shop.model.store.ReadableMerchantStore;
import com.salesmanager.test.shop.common.ServicesTestSupport;

@RunWith(SpringRunner.class)
@SpringBootTest(classes = ShopApplication.class, webEnvironment = WebEnvironment.RANDOM_PORT)
public class StorefrontBrowseUatTest extends ServicesTestSupport {

	@Test
	public void shopperCanBrowseStoreCategoriesAndProducts() {
		ResponseEntity<ReadableMerchantStore> store = testRestTemplate.getForEntity(
				"/api/v1/store/DEFAULT", ReadableMerchantStore.class);
		ResponseEntity<String> categories = testRestTemplate.getForEntity(
				"/api/v1/category?store=DEFAULT", String.class);
		ResponseEntity<String> products = testRestTemplate.getForEntity(
				"/api/v1/products?store=DEFAULT", String.class);

		assertEquals(OK, store.getStatusCode());
		assertNotNull(store.getBody());
		assertEquals("DEFAULT", store.getBody().getCode());
		assertEquals(OK, categories.getStatusCode());
		assertEquals(OK, products.getStatusCode());

		String sku = "uat-browse-" + System.nanoTime();
		ReadableProduct created = sampleProduct(sku);
		assertNotNull(created.getId());

		ResponseEntity<ReadableProduct> detail = testRestTemplate.getForEntity(
				"/api/v2/product/" + created.getSku() + "?store=DEFAULT", ReadableProduct.class);
		assertEquals(OK, detail.getStatusCode());
		assertNotNull(detail.getBody());
		assertEquals(created.getSku(), detail.getBody().getSku());
	}
}
