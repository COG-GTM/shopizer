package com.salesmanager.test.shop.uat;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.test.context.junit4.SpringRunner;

import com.salesmanager.shop.application.ShopApplication;
import com.salesmanager.shop.model.catalog.product.ReadableProduct;
import com.salesmanager.shop.model.store.ReadableMerchantStore;
import com.salesmanager.test.shop.common.ServicesTestSupport;

@RunWith(SpringRunner.class)
@SpringBootTest(classes = ShopApplication.class, webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
public class CatalogBrowseUatTest extends ServicesTestSupport {

  @Test
  public void storefrontCanBrowseStoreCategoriesProductsAndProductDetails() {
    String productCode = "uat-browse-" + System.nanoTime();
    ReadableProduct product = sampleProduct(productCode);

    ResponseEntity<ReadableMerchantStore> storeResponse = testRestTemplate.getForEntity(
        "/api/v1/store/DEFAULT", ReadableMerchantStore.class);
    ResponseEntity<String> categoryResponse = testRestTemplate.getForEntity(
        "/api/v1/category?store=DEFAULT&lang=en", String.class);
    ResponseEntity<String> productsResponse = testRestTemplate.getForEntity(
        "/api/v1/products?store=DEFAULT&lang=en", String.class);
    ResponseEntity<ReadableProduct> detailResponse = testRestTemplate.getForEntity(
        "/api/v2/product/" + productCode + "?store=DEFAULT&lang=en", ReadableProduct.class);

    assertEquals(HttpStatus.OK, storeResponse.getStatusCode());
    assertNotNull(storeResponse.getBody());
    assertEquals(HttpStatus.OK, categoryResponse.getStatusCode());
    assertEquals(HttpStatus.OK, productsResponse.getStatusCode());
    assertEquals(HttpStatus.OK, detailResponse.getStatusCode());
    assertNotNull(detailResponse.getBody());
    assertEquals(product.getSku(), detailResponse.getBody().getSku());
  }
}
