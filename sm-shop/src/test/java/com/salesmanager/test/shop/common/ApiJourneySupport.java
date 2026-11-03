package com.salesmanager.test.shop.common;

import static org.junit.jupiter.api.Assertions.*;

import java.math.BigDecimal;
import java.util.UUID;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.ResponseEntity;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.salesmanager.shop.model.catalog.product.product.PersistableProduct;
import com.salesmanager.shop.model.catalog.product.product.ProductSpecification;

public class ApiJourneySupport extends ServicesTestSupport {
    protected final ObjectMapper json = new ObjectMapper();

    protected String unique(String prefix) {
        return prefix + UUID.randomUUID().toString().replace("-", "").substring(0, 12);
    }

    protected ResponseEntity<String> request(HttpMethod method, String path, Object body, HttpHeaders headers) {
        return testRestTemplate.exchange(path, method, new HttpEntity<>(body, headers), String.class);
    }

    protected JsonNode expect(int status, HttpMethod method, String path, Object body, HttpHeaders headers) {
        ResponseEntity<String> response = request(method, path, body, headers);
        assertEquals(status, response.getStatusCodeValue(), () -> method + " " + path + ": " + response);
        try {
            return response.getBody() == null ? json.nullNode() : json.readTree(response.getBody());
        } catch (Exception e) {
            throw new AssertionError(response.getBody(), e);
        }
    }

    protected ObjectNode object(String value) {
        try {
            return (ObjectNode) json.readTree(value);
        } catch (Exception e) {
            throw new AssertionError(e);
        }
    }

    protected long createProduct(String store, String sku, HttpHeaders headers) {
        PersistableProduct product = product(sku);
        product.setSku(sku);
        product.setAvailable(true);
        product.setQuantity(100);
        product.setPrice(BigDecimal.TEN);
        ProductSpecification specification = new ProductSpecification();
        if ("DEFAULT".equals(store)) specification.setManufacturer("DEFAULT");
        product.setProductSpecifications(specification);
        return expect(201, HttpMethod.POST, "/api/v1/private/product?store=" + store, product, headers).path("id").asLong();
    }

    protected long createOption(String store, String code, HttpHeaders headers) {
        ObjectNode body = object("{\"type\":\"Radio\",\"descriptions\":[{\"language\":\"en\",\"name\":\"Journey color\"}]}");
        body.put("code", code);
        return expect(201, HttpMethod.POST, "/api/v1/private/product/option?store=" + store, body, headers).path("id").asLong();
    }

    protected long createValue(String store, String code, HttpHeaders headers) {
        ObjectNode body = object("{\"descriptions\":[{\"language\":\"en\",\"name\":\"Journey red\"}]}");
        body.put("code", code);
        return expect(201, HttpMethod.POST, "/api/v1/private/product/option/value?store=" + store, body, headers).path("id").asLong();
    }

    protected ObjectNode attribute(long product, long option, long value, int price) {
        ObjectNode body = json.createObjectNode();
        body.put("productId", product);
        body.putObject("option").put("id", option);
        body.putObject("optionValue").put("id", value);
        body.put("productAttributePrice", price);
        body.put("productAttributeWeight", 0);
        return body;
    }

    protected String attributes(long product, String store) {
        return "/api/v1/private/product/" + product + "/attributes?store=" + store + "&count=100";
    }

    protected String attributePath(long product, String store) {
        return "/api/v1/private/product/" + product + "/attribute?store=" + store;
    }

    protected String attributePath(long product, long attribute, String store) {
        return "/api/v1/private/product/" + product + "/attribute/" + attribute + "?store=" + store;
    }
}
