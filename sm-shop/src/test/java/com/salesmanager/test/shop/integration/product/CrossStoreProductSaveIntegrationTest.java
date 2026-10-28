package com.salesmanager.test.shop.integration.product;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
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
import org.springframework.http.ResponseEntity;
import org.springframework.test.context.junit4.SpringRunner;

import com.fasterxml.jackson.databind.JsonNode;
import com.salesmanager.core.business.services.catalog.product.ProductService;
import com.salesmanager.core.model.catalog.product.Product;
import com.salesmanager.shop.model.catalog.product.product.PersistableProduct;
import com.salesmanager.shop.model.references.PersistableAddress;
import com.salesmanager.shop.model.store.PersistableMerchantStore;
import com.salesmanager.test.shop.common.ServicesTestSupport;

@RunWith(SpringRunner.class)
public class CrossStoreProductSaveIntegrationTest extends ServicesTestSupport {
    private static final String DEFAULT_STORE = "DEFAULT";
    private static final String PRODUCT_API = "/api/v1/private/product";

    @Autowired
    private ProductService productService;

    @Test
    public void storeCannotSaveDefaultProductByIdButCanCreateItsOwn() {
        HttpHeaders headers = getHeader();
        String storeCode = uniqueCode();
        Long defaultProductId = null;
        Long storeProductId = null;

        try {
            createStore(storeCode, headers);

            PersistableProduct defaultProduct = newProduct("cross-save-default");
            defaultProduct.getDescriptions().get(0).setName("Original default product");
            defaultProductId = createProduct(defaultProduct, DEFAULT_STORE, headers);

            defaultProduct.setId(defaultProductId);
            defaultProduct.getDescriptions().get(0).setName("Attempted cross-store update");
            HttpEntity<PersistableProduct> request = new HttpEntity<>(defaultProduct, headers);

            ResponseEntity<String> update = testRestTemplate.exchange(
                    PRODUCT_API + "/" + defaultProductId + "?store=" + storeCode,
                    HttpMethod.PUT, request, String.class);
            assertEquals(NOT_FOUND, update.getStatusCode());
            assertProductUnchanged(defaultProductId, defaultProduct.getSku(),
                    "Original default product", DEFAULT_STORE);

            ResponseEntity<String> save = testRestTemplate.postForEntity(
                    PRODUCT_API + "?store=" + storeCode, request, String.class);
            assertEquals(NOT_FOUND, save.getStatusCode());
            assertProductUnchanged(defaultProductId, defaultProduct.getSku(),
                    "Original default product", DEFAULT_STORE);

            PersistableProduct storeProduct = newProduct("cross-save-store");
            ResponseEntity<JsonNode> createResponse = testRestTemplate.postForEntity(
                    PRODUCT_API + "?store=" + storeCode,
                    new HttpEntity<>(storeProduct, headers), JsonNode.class);
            assertEquals(CREATED, createResponse.getStatusCode());
            assertNotNull(createResponse.getBody());
            storeProductId = createResponse.getBody().path("id").asLong();
            assertNotNull(productService.getProductWithOnlyMerchantStoreById(storeProductId));
            assertEquals(storeCode, productService.getProductWithOnlyMerchantStoreById(storeProductId)
                    .getMerchantStore().getCode());
        } finally {
            deleteProduct(defaultProductId, DEFAULT_STORE, headers);
            deleteProduct(storeProductId, storeCode, headers);
            deleteStore(storeCode, headers);
        }
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

    private void assertProductUnchanged(Long id, String sku, String name, String storeCode) {
        Product product = productService.getProductWithOnlyMerchantStoreById(id);
        assertNotNull(product);
        assertEquals(DEFAULT_STORE, product.getMerchantStore().getCode());

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

    private void deleteStore(String code, HttpHeaders headers) {
        try {
            testRestTemplate.exchange("/api/v1/private/store/" + code,
                    HttpMethod.DELETE, new HttpEntity<>(headers), String.class);
        } catch (RuntimeException ignored) {
        }
    }
}
