package com.salesmanager.test.shop.integration.cart;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.springframework.http.HttpStatus.BAD_REQUEST;
import static org.springframework.http.HttpStatus.CREATED;
import static org.springframework.http.HttpStatus.OK;

import java.util.Arrays;
import java.util.UUID;
import java.util.stream.Collectors;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.SpringBootTest.WebEnvironment;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpMethod;
import org.springframework.http.ResponseEntity;
import org.springframework.test.context.junit.jupiter.SpringExtension;

import com.salesmanager.shop.application.ShopApplication;
import com.salesmanager.shop.model.catalog.product.ReadableProduct;
import com.salesmanager.shop.model.shoppingcart.PersistableShoppingCartItem;
import com.salesmanager.shop.model.shoppingcart.ReadableShoppingCart;
import com.salesmanager.test.shop.common.ServicesTestSupport;

@SpringBootTest(classes = ShopApplication.class, webEnvironment = WebEnvironment.RANDOM_PORT)
@ExtendWith(SpringExtension.class)
class CartQuantityValidationIntegrationTest extends ServicesTestSupport {

    @Test
    void postRejectsZeroQuantity() {
        ReadableProduct product = sampleProduct(uniqueSku("post-zero"));
        ResponseEntity<String> response = testRestTemplate.postForEntity("/api/v1/cart",
                new HttpEntity<>(item(product.getSku(), 0), getHeader()), String.class);

        assertEquals(BAD_REQUEST, response.getStatusCode());
    }

    @Test
    void negativeMultiUpdateIsRejectedWithoutChangingCart() {
        ReadableProduct productA = sampleProduct(uniqueSku("multi-a"));
        ReadableProduct productB = sampleProduct(uniqueSku("multi-b"));
        ReadableShoppingCart created = createCart(productA.getSku(), 1);

        PersistableShoppingCartItem[] update = { item(productB.getSku(), 2), item(productA.getSku(), -1) };
        ResponseEntity<String> response = testRestTemplate.exchange("/api/v1/cart/" + created.getCode() + "/multi",
                HttpMethod.POST, new HttpEntity<>(update, getHeader()), String.class);

        assertEquals(BAD_REQUEST, response.getStatusCode());
        ReadableShoppingCart unchanged = getCart(created.getCode());
        assertEquals(1, unchanged.getQuantity());
        assertEquals(Arrays.asList(productA.getSku()), skus(unchanged));
    }

    @Test
    void zeroOnExistingLineRemovesOnlyThatLine() {
        ReadableProduct productA = sampleProduct(uniqueSku("remove-a"));
        ReadableProduct productB = sampleProduct(uniqueSku("remove-b"));
        ReadableShoppingCart created = createCart(productA.getSku(), 1);
        ResponseEntity<ReadableShoppingCart> added = testRestTemplate.exchange(
                "/api/v1/cart/" + created.getCode(), HttpMethod.PUT,
                new HttpEntity<>(item(productB.getSku(), 1), getHeader()), ReadableShoppingCart.class);
        assertEquals(CREATED, added.getStatusCode());

        ResponseEntity<ReadableShoppingCart> removed = testRestTemplate.exchange(
                "/api/v1/cart/" + created.getCode(), HttpMethod.PUT,
                new HttpEntity<>(item(productA.getSku(), 0), getHeader()), ReadableShoppingCart.class);

        assertEquals(CREATED, removed.getStatusCode());
        assertNotNull(removed.getBody());
        assertEquals(1, removed.getBody().getQuantity());
        assertEquals(Arrays.asList(productB.getSku()), skus(removed.getBody()));
    }

    @Test
    void zeroQuantityDoesNotAddANewLine() {
        ReadableProduct productA = sampleProduct(uniqueSku("zero-existing"));
        ReadableProduct productC = sampleProduct(uniqueSku("zero-new"));
        ReadableShoppingCart created = createCart(productA.getSku(), 1);

        ResponseEntity<ReadableShoppingCart> response = testRestTemplate.exchange(
                "/api/v1/cart/" + created.getCode(), HttpMethod.PUT,
                new HttpEntity<>(item(productC.getSku(), 0), getHeader()), ReadableShoppingCart.class);

        assertEquals(CREATED, response.getStatusCode());
        assertNotNull(response.getBody());
        assertEquals(1, response.getBody().getQuantity());
        assertEquals(Arrays.asList(productA.getSku()), skus(response.getBody()));
        assertFalse(skus(response.getBody()).contains(productC.getSku()));
    }

    private ReadableShoppingCart createCart(String sku, int quantity) {
        ResponseEntity<ReadableShoppingCart> response = testRestTemplate.postForEntity("/api/v1/cart",
                new HttpEntity<>(item(sku, quantity), getHeader()), ReadableShoppingCart.class);
        assertEquals(CREATED, response.getStatusCode());
        assertNotNull(response.getBody());
        return response.getBody();
    }

    private ReadableShoppingCart getCart(String code) {
        ResponseEntity<ReadableShoppingCart> response = testRestTemplate.getForEntity(
                "/api/v1/cart/" + code, ReadableShoppingCart.class);
        assertEquals(OK, response.getStatusCode());
        return response.getBody();
    }

    private static PersistableShoppingCartItem item(String sku, int quantity) {
        PersistableShoppingCartItem item = new PersistableShoppingCartItem();
        item.setProduct(sku);
        item.setQuantity(quantity);
        return item;
    }

    private static String uniqueSku(String prefix) {
        return "qty-" + prefix + "-" + UUID.randomUUID().toString().replace("-", "");
    }

    private static java.util.List<String> skus(ReadableShoppingCart cart) {
        return cart.getProducts().stream().map(product -> product.getSku()).collect(Collectors.toList());
    }
}
