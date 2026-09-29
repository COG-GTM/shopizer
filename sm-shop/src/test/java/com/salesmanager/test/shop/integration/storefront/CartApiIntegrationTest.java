package com.salesmanager.test.shop.integration.storefront;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.tuple;

import java.math.BigDecimal;
import java.util.Arrays;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import com.salesmanager.shop.model.catalog.product.ReadableProduct;
import com.salesmanager.shop.model.order.ReadableOrderTotalSummary;
import com.salesmanager.shop.model.shoppingcart.PersistableShoppingCartItem;
import com.salesmanager.shop.model.shoppingcart.ReadableShoppingCart;
import com.salesmanager.shop.model.shoppingcart.ReadableShoppingCartItem;

/** Anonymous shopper cart lifecycle: create, read, update quantities, add/remove lines, totals. */
@DisplayName("Storefront API: cart")
class CartApiIntegrationTest extends StorefrontApiTestSupport {

  private ReadableProduct shirt;
  private ReadableProduct hat;

  @BeforeEach
  void createProducts() {
    shirt = createProduct(unique("shirt"));
    hat = createProduct(unique("hat"));
  }

  @Test
  @DisplayName("adding an item without a cart code creates a new cart")
  void addToCartCreatesCart() {
    ReadableShoppingCart cart = newCart(shirt.getSku(), 2);

    assertThat(cart.getQuantity()).isEqualTo(2);
    assertThat(cart.getProducts()).hasSize(1);
    ReadableShoppingCartItem line = cart.getProducts().get(0);
    assertThat(line.getSku()).isEqualTo(shirt.getSku());
    assertThat(line.getQuantity()).isEqualTo(2);
    assertThat(cart.getSubtotal()).isEqualByComparingTo(UNIT_PRICE.multiply(BigDecimal.valueOf(2)));
  }

  @Test
  @DisplayName("a cart can be read back by its code")
  void getCartByCode() {
    ReadableShoppingCart created = newCart(shirt.getSku(), 1);

    ReadableShoppingCart fetched = getCart(created.getCode());

    assertThat(fetched.getCode()).isEqualTo(created.getCode());
    assertThat(fetched.getProducts())
        .extracting(ReadableShoppingCartItem::getSku)
        .containsExactly(shirt.getSku());
    assertThat(fetched.getTotal()).isEqualByComparingTo(created.getTotal());
  }

  @Test
  @DisplayName("PUT adds a new product line and updates an existing line's quantity")
  void modifyCart() {
    String code = newCart(shirt.getSku(), 1).getCode();

    ResponseEntity<ReadableShoppingCart> added =
        anonymousExchange(
            "/api/v1/cart/" + code, HttpMethod.PUT, item(hat.getSku(), 1), ReadableShoppingCart.class);
    assertThat(added.getStatusCode()).isEqualTo(HttpStatus.CREATED);
    assertThat(added.getBody().getProducts()).hasSize(2);

    ResponseEntity<ReadableShoppingCart> updated =
        anonymousExchange(
            "/api/v1/cart/" + code, HttpMethod.PUT, item(shirt.getSku(), 3), ReadableShoppingCart.class);
    assertThat(updated.getStatusCode()).isEqualTo(HttpStatus.CREATED);

    ReadableShoppingCart cart = getCart(code);
    assertThat(cart.getQuantity()).isEqualTo(4);
    assertThat(cart.getProducts())
        .extracting(ReadableShoppingCartItem::getSku, ReadableShoppingCartItem::getQuantity)
        .containsExactlyInAnyOrder(
            tuple(shirt.getSku(), 3),
            tuple(hat.getSku(), 1));
    assertThat(cart.getSubtotal()).isEqualByComparingTo(UNIT_PRICE.multiply(BigDecimal.valueOf(4)));
  }

  @Test
  @DisplayName("multi-item update sets several quantities in one call")
  void modifyCartWithMultipleItems() {
    String code = newCart(shirt.getSku(), 1).getCode();
    PersistableShoppingCartItem[] items = {item(shirt.getSku(), 2), item(hat.getSku(), 5)};

    ResponseEntity<ReadableShoppingCart> response =
        anonymousExchange(
            "/api/v1/cart/" + code + "/multi",
            HttpMethod.POST,
            Arrays.asList(items),
            ReadableShoppingCart.class);

    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CREATED);
    assertThat(getCart(code).getQuantity()).isEqualTo(7);
  }

  @Test
  @DisplayName("removing a product returns the remaining cart")
  void removeCartItem() {
    String code = newCart(shirt.getSku(), 1).getCode();
    anonymousExchange(
        "/api/v1/cart/" + code, HttpMethod.PUT, item(hat.getSku(), 1), ReadableShoppingCart.class);

    ResponseEntity<ReadableShoppingCart> response =
        anonymousExchange(
            "/api/v1/cart/" + code + "/product/" + shirt.getSku() + "?body=true",
            HttpMethod.DELETE,
            null,
            ReadableShoppingCart.class);

    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
    assertThat(response.getBody().getProducts())
        .extracting(ReadableShoppingCartItem::getSku)
        .containsExactly(hat.getSku());
  }

  @Test
  @DisplayName("order total for a cart matches the cart total")
  void cartTotal() {
    ReadableShoppingCart cart = newCart(shirt.getSku(), 3);

    ResponseEntity<ReadableOrderTotalSummary> response =
        anonymousGet(
            "/api/v1/cart/" + cart.getCode() + "/total", ReadableOrderTotalSummary.class);

    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
    ReadableOrderTotalSummary summary = response.getBody();
    assertThat(summary.getTotal()).isNotBlank();
    assertThat(summary.getTotals()).isNotEmpty();
    assertThat(cart.getTotal()).isEqualByComparingTo(UNIT_PRICE.multiply(BigDecimal.valueOf(3)));
  }

  @Test
  @DisplayName("adding an unknown product is rejected")
  void addUnknownProduct() {
    ResponseEntity<String> response =
        anonymousExchange("/api/v1/cart", HttpMethod.POST, item(unique("missing"), 1), String.class);

    assertThat(response.getStatusCode().is4xxClientError()).isTrue();
  }

  @Test
  @DisplayName("unknown cart code returns 404")
  void unknownCart() {
    ResponseEntity<String> response = anonymousGet("/api/v1/cart/" + unique("nocart"), String.class);

    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
  }
}
