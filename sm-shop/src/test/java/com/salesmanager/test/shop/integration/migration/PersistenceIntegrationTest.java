package com.salesmanager.test.shop.integration.migration;

import static org.junit.jupiter.api.Assertions.*;
import org.junit.jupiter.api.Test;
import org.springframework.http.*;
import org.springframework.core.io.ClassPathResource;
import org.springframework.util.LinkedMultiValueMap;
import com.salesmanager.shop.model.catalog.manufacturer.*;
import com.salesmanager.test.shop.common.MigrationTestSupport;

class PersistenceIntegrationTest extends MigrationTestSupport {
    @Test void updatesImageThroughRenamedPrivateRoute() {
        var product = sampleProduct(unique("image"));
        var headers = getHeader();
        headers.setContentType(MediaType.MULTIPART_FORM_DATA);
        var body = new LinkedMultiValueMap<String, Object>();
        body.add("file", new ClassPathResource("image.jpg"));
        var upload = testRestTemplate.postForEntity("/api/v1/private/product/" + product.getId() + "/image",
                new HttpEntity<>(body, headers), String.class);
        assertEquals(201, upload.getStatusCode().value(), upload.getBody());
        var images = ok("/api/v1/product/" + product.getId() + "/images", new HttpHeaders());
        assertTrue(images.isArray() && !images.isEmpty(), images.toString());
        long imageId = images.get(0).path("id").asLong();
        assertTrue(imageId > 0);
        var update = testRestTemplate.exchange("/api/v1/private/product/" + product.getId() + "/image/" + imageId + "?order=2",
                HttpMethod.PATCH, new HttpEntity<>(getHeader()), String.class);
        assertEquals(200, update.getStatusCode().value(), update.getBody());
    }

    @Test void insertsZeroIdAndRoundTripsLongDescription() {
        String text = "Long description with Unicode café. ".repeat(40);
        var manufacturer = manufacturer(unique("brand"));
        manufacturer.setId(0L);
        manufacturer.getDescriptions().get(0).setDescription(text);
        var response = testRestTemplate.postForEntity("/api/v1/private/manufacturer",
                new HttpEntity<>(manufacturer, getHeader()), PersistableManufacturer.class);
        assertEquals(201, response.getStatusCode().value());
        assertNotNull(response.getBody());
        Long id = response.getBody().getId();
        assertNotNull(id);
        assertTrue(id > 0);
        var read = testRestTemplate.getForEntity("/api/v1/manufacturer/" + id, ReadableManufacturer.class);
        assertEquals(200, read.getStatusCode().value());
        assertEquals(text, read.getBody().getDescription().getDescription());
    }

    @Test void countQueriesReturnSaneTotalsIncludingEmptyFilteredResults() {
        var headers = getHeader();
        var product = sampleProduct(unique("counts"));
        for (String path : new String[]{"/api/v1/private/customers", "/api/v1/private/orders",
                "/api/v1/private/users", "/api/v1/private/stores",
                "/api/v1/products?category=" + product.getCategories().get(0).getId()
                        + "&manufacturer=" + product.getManufacturer().getId(),
                "/api/v1/products?sku=" + unique("missing")}) {
            var list = ok(path, headers);
            assertTrue(list.has("recordsTotal"), path + ": " + list);
            assertTrue(list.path("recordsTotal").asLong() >= 0, path);
            if (path.contains("?sku=")) assertEquals(0, list.path("recordsTotal").asLong());
            if (path.contains("?category=")) assertTrue(list.path("recordsTotal").asLong() > 0);
        }
    }
}
