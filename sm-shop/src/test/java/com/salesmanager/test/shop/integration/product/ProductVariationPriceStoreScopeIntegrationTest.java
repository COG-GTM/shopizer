package com.salesmanager.test.shop.integration.product;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;

import java.math.BigDecimal;
import java.util.Arrays;
import java.util.Collections;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.SpringBootTest.WebEnvironment;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.test.context.junit4.SpringRunner;

import com.salesmanager.core.business.constants.Constants;
import com.salesmanager.shop.application.ShopApplication;
import com.salesmanager.shop.model.catalog.product.ReadableProductPrice;
import com.salesmanager.shop.model.catalog.product.attribute.ReadableProductVariantValue;
import com.salesmanager.shop.model.catalog.product.attribute.ReadableSelectedProductVariant;
import com.salesmanager.shop.model.catalog.product.product.PersistableProduct;
import com.salesmanager.shop.model.catalog.product.product.ProductSpecification;
import com.salesmanager.shop.model.entity.Entity;
import com.salesmanager.shop.model.references.PersistableAddress;
import com.salesmanager.shop.model.store.PersistableMerchantStore;
import com.salesmanager.test.shop.common.ServicesTestSupport;

/**
 * POST /api/v2/product/{id}/variation?store=X must only price products visible in store X
 * (own products or products of the parent store) and must hide unavailable products (PR #212).
 */
@SpringBootTest(classes = ShopApplication.class, webEnvironment = WebEnvironment.RANDOM_PORT)
@RunWith(SpringRunner.class)
public class ProductVariationPriceStoreScopeIntegrationTest extends ServicesTestSupport {

  private static final String VARIATION_URL = "/api/v2/product/%d/variation?store=%s";

  @Test
  public void productOfRequestedStoreIsPriced() {
    Long id = sampleProduct("pr212own").getId();

    ResponseEntity<ReadableProductPrice> response = calculate(id, Constants.DEFAULT_STORE, unmatchedSelection());

    assertEquals(HttpStatus.OK, response.getStatusCode());
    assertNotNull(response.getBody());
    assertNotNull(response.getBody().getFinalPrice());
  }

  @Test
  public void productOfAnotherStoreIsNotPriced() {
    Long id = sampleProduct("pr212foreign").getId();
    createStore("pr212oth", null);

    ResponseEntity<String> response = calculateRaw(id, "pr212oth", unmatchedSelection());

    assertEquals(HttpStatus.NOT_FOUND, response.getStatusCode());
  }

  @Test
  public void productOfParentStoreIsPricedForChildStore() {
    Long id = sampleProduct("pr212parent").getId();
    createStore("pr212chd", Constants.DEFAULT_STORE);

    ResponseEntity<ReadableProductPrice> response = calculate(id, "pr212chd", unmatchedSelection());

    assertEquals(HttpStatus.OK, response.getStatusCode());
    assertNotNull(response.getBody());
    assertNotNull(response.getBody().getFinalPrice());
  }

  @Test
  public void unavailableProductIsNotPriced() {
    Long id = createProduct("pr212unavail", false);

    ResponseEntity<String> response = calculateRaw(id, Constants.DEFAULT_STORE, unmatchedSelection());

    assertEquals(HttpStatus.NOT_FOUND, response.getStatusCode());
  }

  @Test
  public void unknownProductIsNotFound() {
    ResponseEntity<String> response = calculateRaw(987654321L, Constants.DEFAULT_STORE, unmatchedSelection());

    assertEquals(HttpStatus.NOT_FOUND, response.getStatusCode());
  }

  @Test
  public void emptyOptionsReturnEmptyBody() {
    Long id = sampleProduct("pr212empty").getId();
    ReadableSelectedProductVariant empty = new ReadableSelectedProductVariant();

    ResponseEntity<String> response = calculateRaw(id, Constants.DEFAULT_STORE, empty);

    assertEquals(HttpStatus.OK, response.getStatusCode());
    assertNull(response.getBody());
  }

  private ResponseEntity<ReadableProductPrice> calculate(Long id, String store, ReadableSelectedProductVariant body) {
    return testRestTemplate.postForEntity(String.format(VARIATION_URL, id, store), json(body),
        ReadableProductPrice.class);
  }

  private ResponseEntity<String> calculateRaw(Long id, String store, ReadableSelectedProductVariant body) {
    return testRestTemplate.postForEntity(String.format(VARIATION_URL, id, store), json(body), String.class);
  }

  private static HttpEntity<ReadableSelectedProductVariant> json(ReadableSelectedProductVariant body) {
    HttpHeaders headers = new HttpHeaders();
    headers.setContentType(MediaType.APPLICATION_JSON);
    return new HttpEntity<>(body, headers);
  }

  private static ReadableSelectedProductVariant unmatchedSelection() {
    ReadableProductVariantValue v = new ReadableProductVariantValue();
    v.setOption(999999L);
    v.setValue(999999L);
    ReadableSelectedProductVariant selection = new ReadableSelectedProductVariant();
    selection.setOptions(Collections.singletonList(v));
    return selection;
  }

  private Long createProduct(String code, boolean available) {
    PersistableProduct product = product(code);
    ProductSpecification specifications = new ProductSpecification();
    specifications.setManufacturer(
        com.salesmanager.core.model.catalog.product.manufacturer.Manufacturer.DEFAULT_MANUFACTURER);
    product.setProductSpecifications(specifications);
    product.setVisible(available);
    product.setPrice(BigDecimal.TEN);
    product.setSku(code);
    product.setQuantity(10);
    ResponseEntity<Entity> response = testRestTemplate.postForEntity(
        "/api/v1/private/product?store=" + Constants.DEFAULT_STORE, new HttpEntity<>(product, getHeader()),
        Entity.class);
    assertEquals(HttpStatus.CREATED, response.getStatusCode());
    return response.getBody().getId();
  }

  private void createStore(String code, String parent) {
    PersistableAddress address = new PersistableAddress();
    address.setAddress("1 test street");
    address.setPostalCode("12345");
    address.setCountry("US");
    address.setCity("FT LD");
    address.setStateProvince("FL");

    PersistableMerchantStore store = new PersistableMerchantStore();
    store.setCode(code);
    store.setCurrency("USD");
    store.setDefaultLanguage("en");
    store.setEmail(code + "@test.com");
    store.setName(code);
    store.setPhone("444-555-6666");
    store.setSupportedLanguages(Arrays.asList("en"));
    store.setAddress(address);
    store.setRetailerStore(parent);

    ResponseEntity<Void> response = testRestTemplate.postForEntity("/api/v1/private/store/",
        new HttpEntity<>(store, getHeader()), Void.class);
    assertEquals(HttpStatus.OK, response.getStatusCode());
  }
}
