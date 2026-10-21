package com.salesmanager.test.shop.uat;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.SpringBootTest.WebEnvironment;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.test.context.junit4.SpringRunner;

import com.salesmanager.core.business.constants.Constants;
import com.salesmanager.shop.application.ShopApplication;
import com.salesmanager.shop.model.catalog.product.ReadableProduct;
import com.salesmanager.shop.model.shoppingcart.PersistableShoppingCartItem;
import com.salesmanager.shop.model.shoppingcart.ReadableShoppingCart;
import com.salesmanager.test.shop.common.ServicesTestSupport;

@RunWith(SpringRunner.class)
@SpringBootTest(classes = ShopApplication.class, webEnvironment = WebEnvironment.RANDOM_PORT)
public class StorefrontCartUatTest extends ServicesTestSupport {

  @Test
  public void browseProductsAndUpdateCart() {
    assertEquals(HttpStatus.OK, testRestTemplate.getForEntity(
        "/api/v1/store/" + Constants.DEFAULT_STORE, String.class).getStatusCode());
    assertEquals(HttpStatus.OK, testRestTemplate.getForEntity(
        "/api/v1/category?store=" + Constants.DEFAULT_STORE, String.class).getStatusCode());

    ReadableProduct firstProduct = sampleProduct("uatJourneyOne");
    ReadableProduct secondProduct = sampleProduct("uatJourneyTwo");
    assertEquals(HttpStatus.OK, testRestTemplate.getForEntity(
        "/api/v1/products?store=" + Constants.DEFAULT_STORE, String.class).getStatusCode());
    ResponseEntity<ReadableProduct> detail = testRestTemplate.getForEntity(
        "/api/v2/product/" + firstProduct.getSku(), ReadableProduct.class);
    assertEquals(HttpStatus.OK, detail.getStatusCode());
    assertNotNull(detail.getBody());

    PersistableShoppingCartItem firstItem = new PersistableShoppingCartItem();
    firstItem.setProduct(firstProduct.getSku());
    firstItem.setQuantity(1);
    ResponseEntity<ReadableShoppingCart> created = testRestTemplate.postForEntity(
        "/api/v1/cart", new HttpEntity<>(firstItem, getHeader()), ReadableShoppingCart.class);
    assertEquals(HttpStatus.CREATED, created.getStatusCode());

    PersistableShoppingCartItem secondItem = new PersistableShoppingCartItem();
    secondItem.setProduct(secondProduct.getSku());
    secondItem.setQuantity(1);
    ResponseEntity<ReadableShoppingCart> updated = testRestTemplate.exchange(
        "/api/v1/cart/" + created.getBody().getCode(), HttpMethod.PUT,
        new HttpEntity<>(secondItem, getHeader()), ReadableShoppingCart.class);
    assertEquals(HttpStatus.CREATED, updated.getStatusCode());

    ResponseEntity<ReadableShoppingCart> cart = testRestTemplate.getForEntity(
        "/api/v1/cart/" + created.getBody().getCode(), ReadableShoppingCart.class);
    assertEquals(HttpStatus.OK, cart.getStatusCode());
    assertEquals(2, cart.getBody().getQuantity());
  }
}
