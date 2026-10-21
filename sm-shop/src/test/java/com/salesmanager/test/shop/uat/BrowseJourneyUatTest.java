package com.salesmanager.test.shop.uat;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.test.context.junit4.SpringRunner;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.salesmanager.shop.application.ShopApplication;
import com.salesmanager.shop.model.catalog.product.ReadableProduct;
import com.salesmanager.test.shop.common.ServicesTestSupport;

@RunWith(SpringRunner.class)
@SpringBootTest(classes = ShopApplication.class, webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
public class BrowseJourneyUatTest extends ServicesTestSupport {

	@Test
	public void visitorCanBrowseStoreCategoriesAndProducts() throws Exception {
		ResponseEntity<String> store = testRestTemplate.getForEntity("/api/v1/store/DEFAULT", String.class);
		assertEquals(HttpStatus.OK, store.getStatusCode());
		JsonNode storeJson = new ObjectMapper().readTree(store.getBody());
		assertEquals("DEFAULT", storeJson.path("code").asText());

		ResponseEntity<String> categories = testRestTemplate.getForEntity("/api/v1/category?store=DEFAULT",
				String.class);
		assertEquals(HttpStatus.OK, categories.getStatusCode());

		String productCode = "ab534browse";
		sampleProduct(productCode);
		ResponseEntity<String> productList = testRestTemplate.getForEntity("/api/v1/products?store=DEFAULT",
				String.class);
		assertEquals(HttpStatus.OK, productList.getStatusCode());

		ResponseEntity<ReadableProduct> product = testRestTemplate
				.getForEntity("/api/v2/product/" + productCode + "?store=DEFAULT", ReadableProduct.class);
		assertEquals(HttpStatus.OK, product.getStatusCode());
		assertNotNull(product.getBody());
		assertEquals(productCode, product.getBody().getSku());
	}
}
