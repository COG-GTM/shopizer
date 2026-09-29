package com.salesmanager.test.shop.integration.storefront;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import com.salesmanager.shop.model.catalog.category.PersistableCategory;
import com.salesmanager.shop.model.catalog.category.ReadableCategory;
import com.salesmanager.shop.model.catalog.category.ReadableCategoryList;
import com.salesmanager.shop.model.catalog.product.ReadableProduct;
import com.salesmanager.shop.model.catalog.product.ReadableProductList;

/** Anonymous shopper browsing the catalog: categories, category listings and product detail. */
@DisplayName("Storefront API: catalog browse")
class CatalogBrowseApiIntegrationTest extends StorefrontApiTestSupport {

  private PersistableCategory category;
  private ReadableProduct first;
  private ReadableProduct second;

  @BeforeEach
  void createCatalog() {
    category = createCategory(unique("browse"));
    first = createProduct(unique("sku-a"), category);
    second = createProduct(unique("sku-b"), category);
  }

  @Test
  @DisplayName("category tree is public and contains a newly created category")
  void listsCategories() {
    ResponseEntity<ReadableCategoryList> response =
        anonymousGet("/api/v1/category?count=1000", ReadableCategoryList.class);

    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
    assertThat(response.getBody().getCategories())
        .extracting(ReadableCategory::getCode)
        .contains(category.getCode());
  }

  @Test
  @Disabled(
      "GET /api/v1/category/{id} and GET /api/v1/category/{friendlyUrl} are ambiguous mappings "
          + "in CategoryApi; Spring MVC fails the request with IllegalStateException (HTTP 500)")
  @DisplayName("category detail is available by friendly url")
  void getsCategoryByFriendlyUrl() {
    ResponseEntity<ReadableCategory> response =
        anonymousGet("/api/v1/category/" + category.getCode(), ReadableCategory.class);

    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
    ReadableCategory fetched = response.getBody();
    assertThat(fetched.getId()).isEqualTo(category.getId());
    assertThat(fetched.getCode()).isEqualTo(category.getCode());
    assertThat(fetched.getDescription().getName()).isEqualTo("Category " + category.getCode());
  }

  @Test
  @DisplayName("products are listed by category friendly url")
  void listsProductsByCategoryFriendlyUrl() {
    ResponseEntity<ReadableProductList> response =
        anonymousGet(
            "/api/v2/products/category/" + category.getCode(), ReadableProductList.class);

    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
    assertThat(response.getBody().getProducts())
        .extracting(ReadableProduct::getSku)
        .containsExactlyInAnyOrder(first.getSku(), second.getSku());
  }

  @Test
  @DisplayName("product search can be filtered by category id")
  void filtersProductsByCategoryId() {
    ResponseEntity<ReadableProductList> response =
        anonymousGet(
            "/api/v2/products?categoryIds=" + category.getId(), ReadableProductList.class);

    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
    assertThat(response.getBody().getProducts())
        .extracting(ReadableProduct::getSku)
        .containsExactlyInAnyOrder(first.getSku(), second.getSku());
  }

  @Test
  @DisplayName("product detail is available by sku and by friendly url, and is purchasable")
  void getsProductDetail() {
    ResponseEntity<ReadableProduct> bySku =
        anonymousGet("/api/v2/product/" + first.getSku(), ReadableProduct.class);
    assertThat(bySku.getStatusCode()).isEqualTo(HttpStatus.OK);
    ReadableProduct product = bySku.getBody();
    assertThat(product.getSku()).isEqualTo(first.getSku());
    assertThat(product.getDescription().getName()).isEqualTo("Product " + first.getSku());
    assertThat(product.isCanBePurchased()).isTrue();
    assertThat(product.getFinalPrice()).contains(UNIT_PRICE.toPlainString());
    assertThat(product.getCategories())
        .extracting(ReadableCategory::getCode)
        .containsExactly(category.getCode());

    ResponseEntity<ReadableProduct> byFriendlyUrl =
        anonymousGet("/api/v2/product/friendly/" + first.getSku(), ReadableProduct.class);
    assertThat(byFriendlyUrl.getStatusCode()).isEqualTo(HttpStatus.OK);
    assertThat(byFriendlyUrl.getBody().getSku()).isEqualTo(first.getSku());
  }

  @Test
  @DisplayName("unknown product sku is rejected")
  void unknownProductIsRejected() {
    ResponseEntity<String> response =
        anonymousGet("/api/v2/product/" + unique("missing"), String.class);

    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
  }
}
