package com.salesmanager.test.shop.integration.product;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import java.io.IOException;
import java.io.InputStream;
import java.util.UUID;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.SpringBootTest.WebEnvironment;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.core.io.ClassPathResource;
import org.springframework.util.FileCopyUtils;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.test.context.junit4.SpringRunner;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;

import com.salesmanager.shop.application.ShopApplication;
import com.salesmanager.shop.model.catalog.product.ReadableProduct;
import com.salesmanager.test.shop.common.ServicesTestSupport;

@SpringBootTest(classes = ShopApplication.class, webEnvironment = WebEnvironment.RANDOM_PORT)
@RunWith(SpringRunner.class)
public class ProductImageUploadDefaultStorageIntegrationTest extends ServicesTestSupport {

    @Test
    public void acceptsSafeUploadInDefaultStorageMode() throws Exception {
        ReadableProduct product = sampleProduct("default" + UUID.randomUUID().toString().replace("-", "").substring(0, 8));

        ResponseEntity<String> response = upload(product.getId(), "image.jpg");

        assertEquals(HttpStatus.CREATED, response.getStatusCode());
        ResponseEntity<String> images = testRestTemplate.getForEntity(
                "/api/v1/product/" + product.getId() + "/images?store=DEFAULT", String.class);
        assertEquals(HttpStatus.OK, images.getStatusCode());
        assertNotNull(images.getBody());
        assertTrue(images.getBody().contains("image.jpg"));
    }

    @Test
    public void rejectsTraversalNamesWithoutListingThem() throws Exception {
        ReadableProduct product = sampleProduct(
                "default-traversal-" + UUID.randomUUID().toString().replace("-", "").substring(0, 8));

        for (String filename : new String[] { "../../../../evil.jpg", "..\\..\\evil.jpg" }) {
            ResponseEntity<String> upload = upload(product.getId(), filename);
            assertFalse(upload.getStatusCode().is2xxSuccessful());

            ResponseEntity<String> images = testRestTemplate.getForEntity(
                    "/api/v1/product/" + product.getId() + "/images?store=DEFAULT", String.class);
            assertEquals(HttpStatus.OK, images.getStatusCode());
            assertNotNull(images.getBody());
            assertFalse(images.getBody().contains("evil"));
        }
    }

    private ResponseEntity<String> upload(Long productId, String filename) throws IOException {
        HttpHeaders headers = getHeader();
        headers.setContentType(MediaType.MULTIPART_FORM_DATA);
        MultiValueMap<String, Object> body = new LinkedMultiValueMap<>();
        byte[] image;
        try (InputStream stream = new ClassPathResource("image.jpg").getInputStream()) {
            image = FileCopyUtils.copyToByteArray(stream);
        }
        body.add("file", new ByteArrayResource(image) {
            @Override
            public String getFilename() {
                return filename;
            }
        });
        return testRestTemplate.postForEntity(
                "/api/v1/private/product/" + productId + "/image?store=DEFAULT",
                new HttpEntity<>(body, headers), String.class);
    }
}
