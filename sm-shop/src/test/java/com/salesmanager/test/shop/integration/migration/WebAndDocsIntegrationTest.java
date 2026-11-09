package com.salesmanager.test.shop.integration.migration;

import static org.junit.jupiter.api.Assertions.*;
import org.junit.jupiter.api.Test;
import org.springframework.http.*;
import com.salesmanager.shop.model.shoppingcart.*;
import com.salesmanager.test.shop.common.MigrationTestSupport;

class WebAndDocsIntegrationTest extends MigrationTestSupport {
    @Test void trailingSlashGetMatchesCanonicalResponse() {
        for (String path : new String[]{"/api/v1/store/DEFAULT", "/api/v1/category", "/api/v1/products"}) {
            var canonical = get(path, new HttpHeaders());
            var slash = get(path + "/", new HttpHeaders());
            assertEquals(200, canonical.getStatusCode().value(), path);
            assertEquals(canonical.getStatusCode(), slash.getStatusCode(), path);
            assertEquals(canonical.getBody(), slash.getBody(), path);
        }
    }

    @Test void trailingSlashCartPostCreatesEquivalentCart() {
        var product = sampleProduct(unique("slash"));
        for (String path : new String[]{"/api/v1/cart", "/api/v1/cart/"}) {
            PersistableShoppingCartItem item = new PersistableShoppingCartItem();
            item.setProduct(product.getSku());
            item.setQuantity(1);
            var response = testRestTemplate.postForEntity(path, item, ReadableShoppingCart.class);
            assertEquals(201, response.getStatusCode().value());
            assertNotNull(response.getBody().getCode());
            assertEquals(1, response.getBody().getQuantity());
            assertEquals(product.getSku(), response.getBody().getProducts().get(0).getSku());
        }
    }

    @Test void servesOpenApiAndSwaggerUi() {
        var docs = ok("/v3/api-docs", new HttpHeaders());
        assertTrue(docs.path("openapi").asText().startsWith("3."));
        assertTrue(docs.path("paths").has("/api/v1/products"));
        var ui = testRestTemplate.getForEntity("/swagger-ui/index.html", String.class);
        assertEquals(200, ui.getStatusCode().value());
        assertTrue(ui.getBody().contains("swagger-ui"));
    }
}
