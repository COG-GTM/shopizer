package com.salesmanager.test.shop.integration.product;

import static org.junit.Assert.*;

import java.math.BigDecimal;
import java.util.Collections;
import java.util.HashSet;
import java.util.Set;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseEntity;
import org.springframework.test.context.junit4.SpringRunner;
import com.fasterxml.jackson.databind.JsonNode;
import com.salesmanager.shop.model.catalog.category.PersistableCategory;
import com.salesmanager.shop.model.catalog.product.product.PersistableProduct;
import com.salesmanager.shop.model.catalog.product.product.ProductSpecification;
import com.salesmanager.shop.model.entity.Entity;
import com.salesmanager.test.shop.common.ServicesTestSupport;

@RunWith(SpringRunner.class)
public class ProductPagingApiIntegrationTest extends ServicesTestSupport {
    @Test
    public void categoryPagesPartitionCatalogInBothApiVersions() {
        String code = "page-" + java.util.UUID.randomUUID().toString();
        HttpHeaders headers = getHeader();
        PersistableCategory category = category(code);
        category.getDescriptions().get(0).setFriendlyUrl(code);
        ResponseEntity<PersistableCategory> createdCategory = testRestTemplate.postForEntity(
                "/api/v1/private/category", new HttpEntity<>(category, headers), PersistableCategory.class);
        assertEquals(201, createdCategory.getStatusCodeValue());
        category = createdCategory.getBody();
        Set<Long> expected = new HashSet<>();
        for (int i = 0; i < 7; i++) {
            PersistableProduct p = product(code + "-" + i);
            p.setSku(code + "-" + i);
            p.setCategories(Collections.singletonList(category));
            p.setAvailable(true);
            p.setSortOrder(i / 2);
            p.setPrice(BigDecimal.TEN);
            ProductSpecification specs = new ProductSpecification();
            specs.setManufacturer("DEFAULT");
            p.setProductSpecifications(specs);
            ResponseEntity<Entity> created = testRestTemplate.postForEntity("/api/v1/private/product",
                    new HttpEntity<>(p, headers), Entity.class);
            assertEquals(201, created.getStatusCodeValue());
            expected.add(created.getBody().getId());
        }
        assertPages("/api/v1/products?category=" + category.getId(), expected);
        assertPages("/api/v2/products/category/" + code + "?store=DEFAULT", expected);
    }

    private void assertPages(String url, Set<Long> expected) {
        Set<Long> seen = new HashSet<>();
        int previous = Integer.MIN_VALUE;
        for (int page = 0; page < 4; page++) {
            ResponseEntity<JsonNode> response = testRestTemplate.getForEntity(url + "&count=3&page=" + page, JsonNode.class);
            assertEquals(200, response.getStatusCodeValue());
            JsonNode body = response.getBody();
            assertEquals(7, body.path("recordsTotal").asInt());
            assertEquals(3, body.path("totalPages").asInt());
            JsonNode products = body.path("products");
            assertTrue(products.isArray());
            assertEquals(page < 2 ? 3 : page == 2 ? 1 : 0, products.size());
            for (JsonNode p : products) {
                assertTrue(seen.add(p.path("id").asLong()));
                assertTrue(p.has("sortOrder"));
                assertTrue(p.path("sortOrder").asInt() >= previous);
                previous = p.path("sortOrder").asInt();
            }
        }
        assertEquals(expected, seen);
    }
}
