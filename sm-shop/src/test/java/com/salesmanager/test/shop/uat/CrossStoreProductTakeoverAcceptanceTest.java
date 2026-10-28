package com.salesmanager.test.shop.uat;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;
import static org.springframework.http.HttpStatus.CREATED;
import static org.springframework.http.HttpStatus.NOT_FOUND;
import static org.springframework.http.HttpStatus.OK;

import java.math.BigDecimal;
import java.util.Arrays;
import java.util.UUID;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.test.context.junit4.SpringRunner;

import com.fasterxml.jackson.databind.JsonNode;
import com.salesmanager.core.business.services.catalog.product.ProductService;
import com.salesmanager.core.model.catalog.product.Product;
import com.salesmanager.shop.model.catalog.product.product.PersistableProduct;
import com.salesmanager.shop.model.references.PersistableAddress;
import com.salesmanager.shop.model.security.PersistableGroup;
import com.salesmanager.shop.model.store.PersistableMerchantStore;
import com.salesmanager.shop.model.user.PersistableUser;
import com.salesmanager.test.shop.common.ServicesTestSupport;

@RunWith(SpringRunner.class)
public class CrossStoreProductTakeoverAcceptanceTest extends ServicesTestSupport {
    private static final String DEFAULT_STORE = "DEFAULT";
    private static final String PRODUCT_API = "/api/v1/private/product";
    private static final String PASSWORD = "Password1";

    @Autowired
    private ProductService productService;

    @Test
    public void storeAdminCannotTakeOverDefaultProducts() {
        HttpHeaders superadminHeaders = getHeader();
        String storeCode = uniqueCode();
        Long storeUserId = null;
        Long defaultProductId = null;
        Long storeProductId = null;

        try {
            createStore(storeCode, superadminHeaders);
            PersistableUser storeAdmin = newStoreAdmin(storeCode);
            ResponseEntity<JsonNode> userResponse = testRestTemplate.postForEntity(
                    "/api/v1/private/user/?store=" + storeCode,
                    new HttpEntity<>(storeAdmin, superadminHeaders), JsonNode.class);
            assertEquals(OK, userResponse.getStatusCode());
            assertNotNull(userResponse.getBody());
            storeUserId = userResponse.getBody().path("id").asLong();
            HttpHeaders storeAdminHeaders = getHeader(storeAdmin.getUserName(), PASSWORD);

            PersistableProduct defaultProduct = newProduct("cross-takeover-default");
            defaultProduct.getDescriptions().get(0).setName("Original default product");
            defaultProductId = createProduct(defaultProduct, DEFAULT_STORE, superadminHeaders);

            PersistableProduct storeProduct = newProduct("cross-takeover-store");
            storeProductId = createProduct(storeProduct, storeCode, storeAdminHeaders);
            storeProduct.setId(storeProductId);
            storeProduct.getDescriptions().get(0).setName("Updated store product");
            ResponseEntity<String> ownUpdate = testRestTemplate.exchange(
                    PRODUCT_API + "/" + storeProductId + "?store=" + storeCode,
                    HttpMethod.PUT, new HttpEntity<>(storeProduct, storeAdminHeaders), String.class);
            assertEquals(OK, ownUpdate.getStatusCode());
            assertStoreProduct(storeProductId, storeProduct.getSku(),
                    "Updated store product", storeCode);

            defaultProduct.setId(defaultProductId);
            defaultProduct.getDescriptions().get(0).setName("Attempted takeover");
            HttpEntity<PersistableProduct> takeoverRequest =
                    new HttpEntity<>(defaultProduct, storeAdminHeaders);

            ResponseEntity<String> update = testRestTemplate.exchange(
                    PRODUCT_API + "/" + defaultProductId + "?store=" + storeCode,
                    HttpMethod.PUT, takeoverRequest, String.class);
            assertEquals(NOT_FOUND, update.getStatusCode());
            assertDefaultProductUnchanged(defaultProductId, defaultProduct.getSku());

            ResponseEntity<String> save = testRestTemplate.postForEntity(
                    PRODUCT_API + "?store=" + storeCode, takeoverRequest, String.class);
            assertEquals(NOT_FOUND, save.getStatusCode());
            assertDefaultProductUnchanged(defaultProductId, defaultProduct.getSku());

            ResponseEntity<String> inventorySave = testRestTemplate.postForEntity(
                    "/api/v2/private/product/inventory?store=" + storeCode,
                    takeoverRequest, String.class);
            assertEquals(NOT_FOUND, inventorySave.getStatusCode());
            assertDefaultProductUnchanged(defaultProductId, defaultProduct.getSku());

            ResponseEntity<String> implicitDefaultUpdate = testRestTemplate.exchange(
                    PRODUCT_API + "/" + defaultProductId, HttpMethod.PUT,
                    takeoverRequest, String.class);
            System.out.println("PUT without store returned " + implicitDefaultUpdate.getStatusCode());
            assertTrue("Expected a 4xx response without store, got "
                    + implicitDefaultUpdate.getStatusCode(),
                    implicitDefaultUpdate.getStatusCode().series() == HttpStatus.Series.CLIENT_ERROR);
            assertDefaultProductUnchanged(defaultProductId, defaultProduct.getSku());
        } finally {
            deleteProduct(defaultProductId, DEFAULT_STORE, superadminHeaders);
            deleteProduct(storeProductId, storeCode, superadminHeaders);
            deleteUser(storeUserId, storeCode, superadminHeaders);
            deleteStore(storeCode, superadminHeaders);
        }
    }

    private PersistableUser newStoreAdmin(String storeCode) {
        String username = "store-admin-" + UUID.randomUUID() + "@example.com";
        PersistableUser user = new PersistableUser();
        user.setDefaultLanguage("en");
        user.setEmailAddress(username);
        user.setFirstName("Store");
        user.setLastName("Admin");
        user.setUserName(username);
        user.setPassword(PASSWORD);
        user.setRepeatPassword(PASSWORD);
        user.setActive(true);
        user.setStore(storeCode);

        PersistableGroup group = new PersistableGroup();
        group.setName("ADMIN");
        user.getGroups().add(group);
        return user;
    }

    private void createStore(String code, HttpHeaders headers) {
        PersistableAddress address = new PersistableAddress();
        address.setAddress("12 test street");
        address.setPostalCode("12345");
        address.setCountry("US");
        address.setCity("Test City");
        address.setStateProvince("CA");

        PersistableMerchantStore store = new PersistableMerchantStore();
        store.setCode(code);
        store.setCurrency("CAD");
        store.setDefaultLanguage("en");
        store.setEmail(code + "@example.com");
        store.setName(code);
        store.setPhone("444-555-6666");
        store.setSupportedLanguages(Arrays.asList("en"));
        store.setAddress(address);

        ResponseEntity<Void> response = testRestTemplate.postForEntity(
                "/api/v1/private/store", new HttpEntity<>(store, headers), Void.class);
        assertEquals(OK, response.getStatusCode());
    }

    private Long createProduct(PersistableProduct product, String storeCode, HttpHeaders headers) {
        ResponseEntity<JsonNode> response = testRestTemplate.postForEntity(
                PRODUCT_API + "?store=" + storeCode,
                new HttpEntity<>(product, headers), JsonNode.class);
        assertEquals(CREATED, response.getStatusCode());
        assertNotNull(response.getBody());
        return response.getBody().path("id").asLong();
    }

    private void assertDefaultProductUnchanged(Long id, String sku) {
        assertStoreProduct(id, sku, "Original default product", DEFAULT_STORE);
    }

    private void assertStoreProduct(Long id, String sku, String name, String storeCode) {
        Product product = productService.getProductWithOnlyMerchantStoreById(id);
        assertNotNull(product);
        assertEquals(storeCode, product.getMerchantStore().getCode());

        ResponseEntity<JsonNode> read = testRestTemplate.getForEntity(
                "/api/v2/product/" + sku + "?store=" + storeCode, JsonNode.class);
        assertEquals(OK, read.getStatusCode());
        assertNotNull(read.getBody());
        assertEquals(name, read.getBody().path("description").path("name").asText());
    }

    private PersistableProduct newProduct(String prefix) {
        String sku = prefix + "-" + UUID.randomUUID().toString().replace("-", "");
        PersistableProduct product = product(sku);
        product.setSku(sku);
        product.setPrice(BigDecimal.TEN);
        product.setVisible(true);
        return product;
    }

    private String uniqueCode() {
        return "s" + UUID.randomUUID().toString().replace("-", "").substring(0, 8);
    }

    private void deleteProduct(Long id, String storeCode, HttpHeaders headers) {
        if (id != null) {
            try {
                testRestTemplate.exchange(PRODUCT_API + "/" + id + "?store=" + storeCode,
                        HttpMethod.DELETE, new HttpEntity<>(headers), String.class);
            } catch (RuntimeException ignored) {
            }
        }
    }

    private void deleteUser(Long id, String storeCode, HttpHeaders headers) {
        if (id != null) {
            try {
                testRestTemplate.exchange("/api/v1/private/user/" + id + "?store=" + storeCode,
                        HttpMethod.DELETE, new HttpEntity<>(headers), String.class);
            } catch (RuntimeException ignored) {
            }
        }
    }

    private void deleteStore(String code, HttpHeaders headers) {
        try {
            testRestTemplate.exchange("/api/v1/private/store/" + code,
                    HttpMethod.DELETE, new HttpEntity<>(headers), String.class);
        } catch (RuntimeException ignored) {
        }
    }
}
