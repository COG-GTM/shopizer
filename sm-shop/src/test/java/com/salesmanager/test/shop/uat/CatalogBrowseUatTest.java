package com.salesmanager.test.shop.uat;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.springframework.http.HttpMethod.GET;
import static org.springframework.http.HttpStatus.OK;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.SpringBootTest.WebEnvironment;
import org.springframework.http.HttpEntity;
import org.springframework.http.ResponseEntity;
import org.springframework.test.context.junit4.SpringRunner;

import com.salesmanager.shop.application.ShopApplication;
import com.salesmanager.shop.model.catalog.product.ReadableProduct;
import com.salesmanager.test.shop.common.ServicesTestSupport;

@SpringBootTest(classes = ShopApplication.class, webEnvironment = WebEnvironment.RANDOM_PORT)
@RunWith(SpringRunner.class)
public class CatalogBrowseUatTest extends ServicesTestSupport {

    @Test
    public void visitorCanBrowseStoreCategoriesProductsAndProductDetails() {
        ResponseEntity<String> store = testRestTemplate.getForEntity(
                "/api/v1/store/DEFAULT", String.class);
        assertEquals(OK, store.getStatusCode());

        ResponseEntity<String> categories = testRestTemplate.getForEntity(
                "/api/v1/category?store=DEFAULT", String.class);
        assertEquals(OK, categories.getStatusCode());

        ResponseEntity<String> products = testRestTemplate.getForEntity(
                "/api/v1/products?store=DEFAULT", String.class);
        assertEquals(OK, products.getStatusCode());
        assertNotNull(products.getBody());

        ReadableProduct product = sampleProduct("uat-catalog-" + System.nanoTime());
        ResponseEntity<ReadableProduct> details = testRestTemplate.exchange(
                "/api/v2/product/" + product.getSku(), GET, HttpEntity.EMPTY, ReadableProduct.class);
        assertEquals(OK, details.getStatusCode());
        assertNotNull(details.getBody());
        assertEquals(product.getSku(), details.getBody().getSku());
    }
}
