package com.salesmanager.test.shop.uat;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import java.math.BigDecimal;
import java.util.UUID;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.test.context.junit4.SpringRunner;

import com.fasterxml.jackson.databind.JsonNode;
import com.salesmanager.shop.model.catalog.product.product.PersistableProduct;
import com.salesmanager.test.shop.common.ServicesTestSupport;

@RunWith(SpringRunner.class)
public class ProductManagementAcceptanceTest extends ServicesTestSupport {
    private static final String PRODUCT_API = "/api/v1/private/product";

    @Test
    public void adminCanCreateUpdateAndReadOwnProduct() {
        HttpHeaders headers = getHeader();
        PersistableProduct body = newProduct();
        Long id = create(body, headers);
        body.setId(id);
        body.getDescriptions().get(0).setName("Updated acceptance product");
        try {
            ResponseEntity<String> update = testRestTemplate.exchange(PRODUCT_API + "/" + id,
                    HttpMethod.PUT, new HttpEntity<>(body, headers), String.class);
            assertEquals(HttpStatus.OK, update.getStatusCode());
            JsonNode read = read(body.getSku());
            assertEquals(id.longValue(), read.path("id").asLong());
            assertEquals(body.getSku(), read.path("sku").asText());
            assertEquals("Updated acceptance product", read.path("description").path("name").asText());
        } finally {
            delete(id, headers);
        }
    }

    @Test
    public void invalidPathIdCannotMutateTheBodyProduct() {
        HttpHeaders headers = getHeader();
        PersistableProduct body = newProduct();
        Long id = create(body, headers);
        body.setId(id);
        try {
            String originalName = read(body.getSku()).path("description").path("name").asText();
            body.getDescriptions().get(0).setName("Must not be saved");
            ResponseEntity<String> response = testRestTemplate.exchange(PRODUCT_API + "/" + Long.MAX_VALUE,
                    HttpMethod.PUT, new HttpEntity<>(body, headers), String.class);
            assertEquals(HttpStatus.BAD_REQUEST, response.getStatusCode());
            assertEquals(originalName, read(body.getSku()).path("description").path("name").asText());
        } finally {
            delete(id, headers);
        }
    }

    @Test
    public void unknownProductReturns404ForAllSharedSaveRoutes() {
        PersistableProduct body = newProduct();
        body.setId(Long.MAX_VALUE);
        HttpEntity<PersistableProduct> request = new HttpEntity<>(body, getHeader());
        assertEquals(HttpStatus.NOT_FOUND,
                testRestTemplate.postForEntity(PRODUCT_API, request, String.class).getStatusCode());
        assertEquals(HttpStatus.NOT_FOUND, testRestTemplate.exchange(PRODUCT_API + "/" + Long.MAX_VALUE,
                HttpMethod.PUT, request, String.class).getStatusCode());
        assertEquals(HttpStatus.NOT_FOUND, testRestTemplate.postForEntity(
                "/api/v2/private/product/inventory", request, String.class).getStatusCode());
    }

    @Test
    public void anonymousProductWritesAreDenied() {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        assertDenied(testRestTemplate.postForEntity(PRODUCT_API,
                new HttpEntity<>(newProduct(), headers), String.class));
    }

    private PersistableProduct newProduct() {
        String sku = "uat-" + UUID.randomUUID();
        PersistableProduct body = product(sku);
        body.setSku(sku);
        body.setPrice(BigDecimal.TEN);
        body.setVisible(true);
        return body;
    }

    private Long create(PersistableProduct body, HttpHeaders headers) {
        ResponseEntity<JsonNode> response = testRestTemplate.postForEntity(PRODUCT_API,
                new HttpEntity<>(body, headers), JsonNode.class);
        assertEquals(HttpStatus.CREATED, response.getStatusCode());
        assertNotNull(response.getBody());
        assertTrue(response.getBody().path("id").isIntegralNumber());
        return response.getBody().path("id").asLong();
    }

    private JsonNode read(String sku) {
        ResponseEntity<JsonNode> response = testRestTemplate.getForEntity("/api/v2/product/" + sku, JsonNode.class);
        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertNotNull(response.getBody());
        return response.getBody();
    }

    private void delete(Long id, HttpHeaders headers) {
        testRestTemplate.exchange(PRODUCT_API + "/" + id, HttpMethod.DELETE,
                new HttpEntity<>(headers), String.class);
    }

    private void assertDenied(ResponseEntity<String> response) {
        assertTrue(response.getStatusCode() == HttpStatus.UNAUTHORIZED
                || response.getStatusCode() == HttpStatus.FORBIDDEN);
    }
}
