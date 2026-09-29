package com.salesmanager.test.shop.integration.storefront;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.util.Collections;
import java.util.UUID;

import org.junit.jupiter.api.Tag;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;

import com.salesmanager.core.business.constants.Constants;
import com.salesmanager.core.model.catalog.product.manufacturer.Manufacturer;
import com.salesmanager.shop.model.catalog.category.Category;
import com.salesmanager.shop.model.catalog.category.CategoryDescription;
import com.salesmanager.shop.model.catalog.category.PersistableCategory;
import com.salesmanager.shop.model.catalog.product.PersistableProductPrice;
import com.salesmanager.shop.model.catalog.product.ProductDescription;
import com.salesmanager.shop.model.catalog.product.ReadableProduct;
import com.salesmanager.shop.model.catalog.product.product.PersistableProduct;
import com.salesmanager.shop.model.catalog.product.product.PersistableProductInventory;
import com.salesmanager.shop.model.catalog.product.product.ProductSpecification;
import com.salesmanager.shop.model.customer.address.Address;
import com.salesmanager.shop.model.entity.Entity;
import com.salesmanager.shop.model.shoppingcart.PersistableShoppingCartItem;
import com.salesmanager.shop.model.shoppingcart.ReadableShoppingCart;
import com.salesmanager.shop.model.system.IntegrationModuleConfiguration;
import com.salesmanager.test.shop.common.ServicesTestSupport;

/**
 * Shared fixtures for the storefront REST API suite (catalog browse, cart, checkout).
 *
 * <p>Catalog fixtures are created through the admin API; every storefront call is made
 * anonymously (no Authorization header) so the tests exercise what a shopper sees. All codes
 * are randomised so the suite can share the in-memory database with the rest of the tests.
 */
@Tag(StorefrontApiTestSupport.TAG)
public abstract class StorefrontApiTestSupport extends ServicesTestSupport {

  public static final String TAG = "storefront";

  protected static final String STORE = Constants.DEFAULT_STORE;
  protected static final String MONEY_ORDER = "moneyorder";
  protected static final BigDecimal UNIT_PRICE = new BigDecimal("19.99");

  protected static String unique(String prefix) {
    return prefix + "-" + UUID.randomUUID().toString().substring(0, 8);
  }

  protected HttpHeaders jsonHeaders() {
    HttpHeaders headers = new HttpHeaders();
    headers.setContentType(new MediaType(MediaType.APPLICATION_JSON, StandardCharsets.UTF_8));
    headers.setAccept(Collections.singletonList(MediaType.APPLICATION_JSON));
    return headers;
  }

  protected HttpHeaders bearer(String token) {
    HttpHeaders headers = jsonHeaders();
    headers.setBearerAuth(token);
    return headers;
  }

  protected <T> ResponseEntity<T> anonymousGet(String url, Class<T> type) {
    return testRestTemplate.exchange(url, HttpMethod.GET, new HttpEntity<>(jsonHeaders()), type);
  }

  protected <T> ResponseEntity<T> anonymousGet(String url, ParameterizedTypeReference<T> type) {
    return testRestTemplate.exchange(url, HttpMethod.GET, new HttpEntity<>(jsonHeaders()), type);
  }

  protected <T> ResponseEntity<T> anonymousExchange(
      String url, HttpMethod method, Object body, Class<T> type) {
    return testRestTemplate.exchange(url, method, new HttpEntity<>(body, jsonHeaders()), type);
  }

  /** Creates a visible root category whose friendly url equals its code. */
  protected PersistableCategory createCategory(String code) {
    PersistableCategory category = new PersistableCategory();
    category.setCode(code);
    category.setSortOrder(1);
    category.setVisible(true);

    CategoryDescription description = new CategoryDescription();
    description.setLanguage("en");
    description.setName("Category " + code);
    description.setTitle("Category " + code);
    description.setFriendlyUrl(code);
    category.setDescriptions(Collections.singletonList(description));

    ResponseEntity<PersistableCategory> response =
        testRestTemplate.postForEntity(
            "/api/v1/private/category?store=" + STORE,
            new HttpEntity<>(category, getHeader()),
            PersistableCategory.class);
    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CREATED);
    assertThat(response.getBody().getId()).isNotNull();
    return response.getBody();
  }

  /**
   * Creates an available, purchasable product priced at {@link #UNIT_PRICE} in the given
   * category, with a friendly url equal to its sku, and returns the storefront view of it.
   */
  protected ReadableProduct createProduct(String sku, Category category) {
    PersistableProduct product = new PersistableProduct();
    product.setSku(sku);
    product.setAvailable(true);
    product.setVisible(true);
    product.setPrice(UNIT_PRICE);
    product.setQuantity(100);
    product.setCategories(Collections.singletonList(category));

    PersistableProductPrice price = new PersistableProductPrice();
    price.setDefaultPrice(true);
    price.setPrice(UNIT_PRICE);
    PersistableProductInventory inventory = new PersistableProductInventory();
    inventory.setSku(sku);
    inventory.setQuantity(100);
    inventory.setPrice(price);
    product.setInventory(inventory);

    ProductSpecification specifications = new ProductSpecification();
    specifications.setManufacturer(Manufacturer.DEFAULT_MANUFACTURER);
    product.setProductSpecifications(specifications);

    ProductDescription description = new ProductDescription();
    description.setLanguage("en");
    description.setName("Product " + sku);
    description.setFriendlyUrl(sku);
    product.getDescriptions().add(description);

    ResponseEntity<Entity> response =
        testRestTemplate.postForEntity(
            "/api/v1/private/product?store=" + STORE,
            new HttpEntity<>(product, getHeader()),
            Entity.class);
    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CREATED);

    ResponseEntity<ReadableProduct> created =
        anonymousGet("/api/v2/product/" + sku, ReadableProduct.class);
    assertThat(created.getStatusCode()).isEqualTo(HttpStatus.OK);
    return created.getBody();
  }

  protected ReadableProduct createProduct(String sku) {
    return createProduct(sku, createCategory(unique("cat")));
  }

  protected PersistableShoppingCartItem item(String sku, int quantity) {
    PersistableShoppingCartItem item = new PersistableShoppingCartItem();
    item.setProduct(sku);
    item.setQuantity(quantity);
    return item;
  }

  /** Starts a new anonymous cart containing {@code quantity} units of {@code sku}. */
  protected ReadableShoppingCart newCart(String sku, int quantity) {
    ResponseEntity<ReadableShoppingCart> response =
        anonymousExchange(
            "/api/v1/cart", HttpMethod.POST, item(sku, quantity), ReadableShoppingCart.class);
    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CREATED);
    assertThat(response.getBody().getCode()).isNotBlank();
    return response.getBody();
  }

  protected ReadableShoppingCart getCart(String code) {
    ResponseEntity<ReadableShoppingCart> response =
        anonymousGet("/api/v1/cart/" + code, ReadableShoppingCart.class);
    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
    return response.getBody();
  }

  /** Enables the offline money order payment module for the default store (idempotent). */
  protected void enableMoneyOrderPayment() {
    IntegrationModuleConfiguration configuration = new IntegrationModuleConfiguration();
    configuration.setCode(MONEY_ORDER);
    configuration.setActive(true);
    configuration.setDefaultSelected(true);
    configuration.getIntegrationKeys().put("address", "1 Integration Test Way, Toronto, ON");

    ResponseEntity<Void> response =
        testRestTemplate.postForEntity(
            "/api/v1/private/modules/payment?store=" + STORE,
            new HttpEntity<>(configuration, getHeader()),
            Void.class);
    assertThat(response.getStatusCode().is2xxSuccessful()).isTrue();
  }

  protected Address address(String firstName, String lastName) {
    Address address = new Address();
    address.setFirstName(firstName);
    address.setLastName(lastName);
    address.setAddress("100 Queen St W");
    address.setCity("Toronto");
    address.setStateProvince("Ontario");
    address.setZone("ON");
    address.setCountry("CA");
    address.setPostalCode("M5H 2N2");
    address.setPhone("4165550100");
    return address;
  }
}
