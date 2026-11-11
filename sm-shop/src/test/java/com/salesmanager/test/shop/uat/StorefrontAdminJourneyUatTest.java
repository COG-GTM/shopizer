package com.salesmanager.test.shop.uat;

import static org.junit.jupiter.api.Assertions.*;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.http.*;
import com.salesmanager.test.shop.common.SecurityJourneySupport;
import com.salesmanager.shop.model.catalog.product.ReadableProduct;
import com.salesmanager.shop.model.shoppingcart.PersistableShoppingCartItem;
import com.salesmanager.shop.model.shoppingcart.ReadableShoppingCart;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

class StorefrontAdminJourneyUatTest extends SecurityJourneySupport {
    @Test
    void adminJourneyRequiresAuthenticationAndListsStores() throws Exception {
        String token = adminToken();
        assertEquals(HttpStatus.OK, get(ADMIN_PROFILE, token).getStatusCode());
        assertDenied(get(ADMIN_PROFILE, null));
        assertDenied(get(ADMIN_PROFILE, com.salesmanager.test.shop.common.TokenContractSupport.expiredAdminToken()));
        ResponseEntity<String> stores = get("/api/v1/private/stores?code=DEFAULT", token);
        assertEquals(HttpStatus.OK, stores.getStatusCode(), stores.getBody());
        assertTrue(new ObjectMapper().readTree(stores.getBody()).get("recordsTotal").asInt() > 0);
    }

    @Test
    void customerJourneyRegistersLogsInAndReadsProfile() {
        assertEquals(HttpStatus.OK, get("/api/v1/auth/customer/profile", registerAndLoginCustomer()).getStatusCode());
    }

    @Test
    void storefrontJourneyBrowsesAndCreatesUpdatesAndReadsCart() throws Exception {
        ReadableProduct product = sampleProduct("uat-" + UUID.randomUUID());
        assertEquals(HttpStatus.OK, get("/api/v1/store/DEFAULT", null).getStatusCode());
        assertEquals(HttpStatus.OK, get("/api/v1/category", null).getStatusCode());
        ResponseEntity<String> products = get("/api/v1/products", null);
        assertEquals(HttpStatus.OK, products.getStatusCode());
        JsonNode list = new ObjectMapper().readTree(products.getBody()).get("products");
        assertNotNull(list);
        assertTrue(list.size() > 0);
        assertEquals(HttpStatus.OK, get("/api/v2/product/" + product.getSku(), null).getStatusCode());

        PersistableShoppingCartItem item = new PersistableShoppingCartItem();
        item.setProduct(product.getSku());
        item.setQuantity(1);
        ResponseEntity<ReadableShoppingCart> created = testRestTemplate.postForEntity("/api/v1/cart", item, ReadableShoppingCart.class);
        assertEquals(HttpStatus.CREATED, created.getStatusCode());
        assertNotNull(created.getBody());
        String path = "/api/v1/cart/" + created.getBody().getCode();
        assertEquals(1, created.getBody().getQuantity());
        item.setQuantity(2);
        ResponseEntity<ReadableShoppingCart> updated = testRestTemplate.exchange(path, HttpMethod.PUT,
                new HttpEntity<>(item), ReadableShoppingCart.class);
        assertEquals(HttpStatus.CREATED, updated.getStatusCode());
        assertEquals(2, updated.getBody().getQuantity());
        ResponseEntity<ReadableShoppingCart> fetched = testRestTemplate.getForEntity(path, ReadableShoppingCart.class);
        assertEquals(HttpStatus.OK, fetched.getStatusCode());
        assertEquals(2, fetched.getBody().getQuantity());
        assertEquals(product.getSku(), fetched.getBody().getProducts().get(0).getSku());
    }

    @Test
    void basicAuthServicesJourney() {
        ResponseEntity<String> valid = testRestTemplate.withBasicAuth("admin@shopizer.com", "password")
                .postForEntity("/services/private/system/optin", "{}", String.class);
        assertEquals(HttpStatus.OK, valid.getStatusCode(), valid.getBody());
        assertDenied(testRestTemplate.withBasicAuth("admin@shopizer.com", "wrong-password")
                .postForEntity("/services/private/system/optin", "{}", String.class));
    }

    @Test
    void operationalEndpointsAreAvailable() throws Exception {
        ResponseEntity<String> health = get("/actuator/health", null);
        assertEquals(HttpStatus.OK, health.getStatusCode());
        assertEquals("UP", new ObjectMapper().readTree(health.getBody()).get("status").asText());
        assertEquals(HttpStatus.OK, get("/v2/api-docs", null).getStatusCode());
    }
}
