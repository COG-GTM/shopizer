package com.salesmanager.test.shop.uat;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.SpringBootTest.WebEnvironment;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.test.context.junit4.SpringRunner;

import com.salesmanager.shop.application.ShopApplication;
import com.salesmanager.shop.model.catalog.product.ReadableProduct;
import com.salesmanager.shop.model.catalog.product.ReadableProductList;
import com.salesmanager.shop.model.store.ReadableMerchantStore;
import com.salesmanager.test.shop.common.ServicesTestSupport;

@SpringBootTest(classes = ShopApplication.class, webEnvironment = WebEnvironment.RANDOM_PORT)
@RunWith(SpringRunner.class)
public class StorefrontBrowseUatTest extends ServicesTestSupport {

	@Test
	public void customerCanBrowseStoreCategoriesProductsAndProductDetails() {
		ReadableProduct product = sampleProduct("storefront-uat-" + System.nanoTime());
		assertNotNull(product);

		ResponseEntity<ReadableMerchantStore> storeResponse = testRestTemplate.getForEntity(
				"/api/v1/store/DEFAULT", ReadableMerchantStore.class);
		assertEquals(HttpStatus.OK, storeResponse.getStatusCode());
		assertNotNull(storeResponse.getBody());

		ResponseEntity<String> categoriesResponse = testRestTemplate.getForEntity(
				"/api/v1/category?store=DEFAULT", String.class);
		assertEquals(HttpStatus.OK, categoriesResponse.getStatusCode());

		ResponseEntity<ReadableProductList> productsResponse = testRestTemplate.getForEntity(
				"/api/v1/products?store=DEFAULT", ReadableProductList.class);
		assertEquals(HttpStatus.OK, productsResponse.getStatusCode());
		assertNotNull(productsResponse.getBody());
		assertTrue(productsResponse.getBody().getProducts().stream()
				.anyMatch(listedProduct -> product.getSku().equals(listedProduct.getSku())));

		ResponseEntity<ReadableProduct> productResponse = testRestTemplate.exchange(
				"/api/v2/product/" + product.getSku() + "?store=DEFAULT", HttpMethod.GET,
				new HttpEntity<>(getHeader()), ReadableProduct.class);
		assertEquals(HttpStatus.OK, productResponse.getStatusCode());
		assertNotNull(productResponse.getBody());
		assertEquals(product.getId(), productResponse.getBody().getId());
	}
}
