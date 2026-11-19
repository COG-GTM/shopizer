package com.salesmanager.test.shop.integration.cart;

import static org.junit.Assert.*;

import java.math.BigDecimal;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpMethod;
import org.springframework.http.ResponseEntity;
import org.springframework.test.context.junit4.SpringRunner;

import com.salesmanager.shop.model.shoppingcart.PersistableShoppingCartItem;
import com.salesmanager.shop.model.shoppingcart.ReadableShoppingCart;
import com.salesmanager.shop.model.shoppingcart.ReadableShoppingCartItem;
import com.salesmanager.test.shop.common.ServicesTestSupport;

@RunWith(SpringRunner.class)
public class CartTotalsIntegrationTest extends ServicesTestSupport {
    @Test
    public void cartTotalsRemainConsistentAfterAddAndQuantityUpdate() {
        String prefix = "totals-" + UUID.randomUUID().toString().substring(0, 8);
        String first = sampleProduct(prefix + "-a").getSku();
        String second = sampleProduct(prefix + "-b").getSku();
        PersistableShoppingCartItem item = new PersistableShoppingCartItem();
        item.setProduct(first);
        item.setQuantity(1);
        ResponseEntity<ReadableShoppingCart> created = testRestTemplate.postForEntity("/api/v1/cart/", item, ReadableShoppingCart.class);
        assertEquals(201, created.getStatusCodeValue());
        String url = "/api/v1/cart/" + created.getBody().getCode();
        item.setProduct(second);
        ResponseEntity<ReadableShoppingCart> added = testRestTemplate.exchange(url, HttpMethod.PUT, new HttpEntity<>(item), ReadableShoppingCart.class);
        assertEquals(201, added.getStatusCodeValue());
        assertCart(added.getBody(), first, 1, second, 1);
        item.setProduct(first);
        item.setQuantity(3);
        ResponseEntity<ReadableShoppingCart> updated = testRestTemplate.postForEntity(url + "/multi", new PersistableShoppingCartItem[]{item}, ReadableShoppingCart.class);
        assertEquals(201, updated.getStatusCodeValue());
        assertCart(updated.getBody(), first, 3, second, 1);
        ResponseEntity<ReadableShoppingCart> read = testRestTemplate.getForEntity(url, ReadableShoppingCart.class);
        assertEquals(200, read.getStatusCodeValue());
        assertCart(read.getBody(), first, 3, second, 1);
    }

    private void assertCart(ReadableShoppingCart cart, String first, int firstQuantity, String second, int secondQuantity) {
        assertNotNull(cart);
        assertEquals(2, cart.getProducts().size());
        Map<String, Integer> quantities = new HashMap<>();
        quantities.put(first, firstQuantity);
        quantities.put(second, secondQuantity);
        BigDecimal sum = BigDecimal.ZERO;
        for (ReadableShoppingCartItem line : cart.getProducts()) {
            assertEquals(quantities.remove(line.getSku()).intValue(), line.getQuantity());
            assertNotNull(line.getPrice());
            assertTrue(line.getPrice().signum() > 0);
            BigDecimal expected = line.getPrice().multiply(BigDecimal.valueOf(line.getQuantity()));
            assertEquals(0, expected.compareTo(line.getSubTotal()));
            assertNotNull(line.getDisplaySubTotal());
            assertFalse(line.getDisplaySubTotal().trim().isEmpty());
            sum = sum.add(expected);
        }
        assertTrue(quantities.isEmpty());
        assertEquals(firstQuantity + secondQuantity, cart.getQuantity());
        assertEquals(0, sum.compareTo(cart.getSubtotal()));
        assertEquals(0, sum.compareTo(cart.getTotal()));
        assertNotNull(cart.getDisplaySubTotal());
        assertFalse(cart.getDisplaySubTotal().trim().isEmpty());
        assertNotNull(cart.getDisplayTotal());
        assertFalse(cart.getDisplayTotal().trim().isEmpty());
        assertNotNull(cart.getTotals());
        assertFalse(cart.getTotals().isEmpty());
    }
}
