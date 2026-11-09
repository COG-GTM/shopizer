package com.salesmanager.test.shop.integration.cms;

import static org.junit.jupiter.api.Assertions.*;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.util.Arrays;
import javax.imageio.ImageIO;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.http.*;
import org.springframework.util.*;
import com.fasterxml.jackson.databind.JsonNode;
import com.salesmanager.core.business.modules.cms.impl.CacheManager;
import com.salesmanager.shop.model.catalog.product.ReadableProduct;
import com.salesmanager.test.shop.common.Pr350IntegrationSupport;

class CmsRestStoreIntegrationTest extends CmsRestTestSupport {
    @Test
    void deletingProductAlsoRemovesItsStoredImageBytes() throws Exception {
        ReadableProduct product = sampleProduct("cms-delete-product");
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        ImageIO.write(new BufferedImage(2, 2, BufferedImage.TYPE_INT_RGB), "png", output);
        byte[] original = output.toByteArray();
        assertEquals(201, upload("/api/v1/private/product/" + product.getId() + "/image",
                "product.png", original, "image/png").getStatusCode().value());
        ResponseEntity<String> deleted = testRestTemplate.exchange("/api/v1/private/product/" + product.getId(),
                HttpMethod.DELETE, new HttpEntity<>(getHeader()), String.class);
        assertTrue(deleted.getStatusCode().is2xxSuccessful(), deleted.getBody());
        assertEquals(400, testRestTemplate.getForEntity("/api/v2/product/" + product.getSku(), String.class)
                .getStatusCode().value());
        for (String size : new String[] {"SMALL", "LARGE"}) {
            byte[] fetched = testRestTemplate.getForObject("/static/products/DEFAULT/" + product.getSku()
                    + "/" + size + "/product.png", byte[].class);
            assertFalse(Arrays.equals(original, fetched), size + " still serves deleted product bytes");
        }
    }
    @Test
    void productImageUploadRoundTripsSmallBytesAndStoresOriginalLargeBytes() throws Exception {
        ReadableProduct product = sampleProduct("cms-upload-roundtrip");
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        ImageIO.write(new BufferedImage(2, 2, BufferedImage.TYPE_INT_RGB), "png", output);
        byte[] original = output.toByteArray();
        assertEquals(201, upload("/api/v1/private/product/" + product.getId() + "/image",
                "roundtrip.png", original, "image/png").getStatusCode().value());
        String node = cache.getRootName() + "DEFAULT/" + product.getSku() + "/";
        assertArrayEquals(original, cache.getTreeCache().getOrCreateNode(node + "LARGE").get("roundtrip.png"));
        byte[] small = cache.getTreeCache().getOrCreateNode(node + "SMALL").get("roundtrip.png");
        assertNotNull(small);
        ResponseEntity<byte[]> fetched = testRestTemplate.getForEntity(
                "/static/products/DEFAULT/" + product.getSku() + "/SMALL/roundtrip.png", byte[].class);
        assertEquals(200, fetched.getStatusCode().value());
        assertArrayEquals(small, fetched.getBody());
    }

    @Test
    void staticFileRoundTripAndDeletionPreserveOtherFile() {
        byte[] content = "body { color: red; }".getBytes(java.nio.charset.StandardCharsets.UTF_8);
        for (String name : new String[] {"pr350.css", "pr350-other.css"}) {
            ResponseEntity<String> uploaded = upload("/api/v1/private/file", name, content, "text/css");
            assertEquals(201, uploaded.getStatusCode().value(), uploaded.getBody());
            ResponseEntity<byte[]> fetched = testRestTemplate.getForEntity("/static/files/DEFAULT/" + name, byte[].class);
            assertEquals(200, fetched.getStatusCode().value());
            assertArrayEquals(content, fetched.getBody());
        }
        ResponseEntity<String> deleted = testRestTemplate.exchange(
                "/api/v1/private/content?name=pr350.css&contentType=STATIC_FILE",
                HttpMethod.DELETE, new HttpEntity<>(getHeader()), String.class);
        assertTrue(deleted.getStatusCode().is2xxSuccessful(), deleted.getBody());
        assertEquals(404, testRestTemplate.getForEntity("/static/files/DEFAULT/pr350.css", String.class)
                .getStatusCode().value());
        assertArrayEquals(content, testRestTemplate.getForObject("/static/files/DEFAULT/pr350-other.css", byte[].class));
    }
}
