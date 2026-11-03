package com.salesmanager.test.shop.uat;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.springframework.http.HttpStatus.BAD_REQUEST;
import static org.springframework.http.HttpStatus.CREATED;
import static org.springframework.http.HttpStatus.OK;

import java.math.BigDecimal;
import java.util.UUID;

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
class CartPriceManipulationUatTest extends ServicesTestSupport {

    @Test
    void negativeQuantityRequestsCannotReduceCartTotal() {
        ReadableProduct product = sampleProduct(uniqueSku("price"));
        PersistableShoppingCartItem positive = item(product.getSku(), 2);
        ResponseEntity<ReadableShoppingCart> created = testRestTemplate.postForEntity("/api/v1/cart",
                new HttpEntity<>(positive, getHeader()), ReadableShoppingCart.class);
        assertEquals(CREATED, created.getStatusCode());
        assertNotNull(created.getBody());
        String code = created.getBody().getCode();
        BigDecimal originalTotal = created.getBody().getTotal();

        ResponseEntity<String> negativePost = testRestTemplate.postForEntity("/api/v1/cart",
                new HttpEntity<>(item(product.getSku(), -1), getHeader()), String.class);
        assertEquals(BAD_REQUEST, negativePost.getStatusCode());

        ResponseEntity<String> negativePut = testRestTemplate.exchange("/api/v1/cart/" + code, HttpMethod.PUT,
                new HttpEntity<>(item(product.getSku(), -1), getHeader()), String.class);
        assertEquals(BAD_REQUEST, negativePut.getStatusCode());

        ResponseEntity<String> negativeMulti = testRestTemplate.exchange(
                "/api/v1/cart/" + code + "/multi", HttpMethod.POST,
                new HttpEntity<>(new PersistableShoppingCartItem[] { item(product.getSku(), -1) }, getHeader()),
                String.class);
        assertEquals(BAD_REQUEST, negativeMulti.getStatusCode());

        ResponseEntity<ReadableShoppingCart> current = testRestTemplate.getForEntity(
                "/api/v1/cart/" + code, ReadableShoppingCart.class);
        assertEquals(OK, current.getStatusCode());
        assertNotNull(current.getBody());
        assertEquals(2, current.getBody().getQuantity());
        assertEquals(0, originalTotal.compareTo(current.getBody().getTotal()));
    }

    private static PersistableShoppingCartItem item(String sku, int quantity) {
        PersistableShoppingCartItem item = new PersistableShoppingCartItem();
        item.setProduct(sku);
        item.setQuantity(quantity);
        return item;
    }

    private static String uniqueSku(String prefix) {
        return "uat-" + prefix + "-" + UUID.randomUUID().toString().replace("-", "");
    }
}
