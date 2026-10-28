package com.salesmanager.test.shop.integration.product;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.UUID;
import java.util.stream.Stream;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.SpringBootTest.WebEnvironment;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.annotation.IfProfileValue;
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

import com.salesmanager.core.business.modules.cms.product.local.CmsImageFileManagerImpl;
import com.salesmanager.shop.application.ShopApplication;
import com.salesmanager.shop.model.catalog.product.ReadableProduct;
import com.salesmanager.test.shop.common.ServicesTestSupport;

@SpringBootTest(classes = ShopApplication.class, webEnvironment = WebEnvironment.RANDOM_PORT, properties = {
        "config.cms.method=httpd", "config.cms.http.path.location=target/cms-it" })
@RunWith(SpringRunner.class)
@ActiveProfiles("local")
@IfProfileValue(name = "local.storage.integration", value = "true")
public class ProductImageUploadLocalStorageIntegrationTest extends ServicesTestSupport {

    @Value("${config.cms.method}")
    private String cmsMethod;

    private Path cmsRoot;

    @Before
    public void setUp() {
        cmsRoot = Paths.get("target", "cms-it", UUID.randomUUID().toString()).toAbsolutePath().normalize();
        CmsImageFileManagerImpl.getInstance().setRootName(cmsRoot.toString());
    }

    @After
    public void tearDown() throws IOException {
        if (Files.exists(cmsRoot)) {
            try (java.util.stream.Stream<Path> paths = Files.walk(cmsRoot)) {
                paths.sorted(java.util.Comparator.reverseOrder()).forEach(path -> path.toFile().delete());
            }
        }
    }

    @Test
    public void storesSafeUploadLocallyAndListsIt() throws Exception {
        assertEquals("httpd", cmsMethod);
        ReadableProduct product = sampleProduct("local" + UUID.randomUUID().toString().replace("-", "").substring(0, 8));
        ResponseEntity<String> upload = upload(product.getId(), "image.jpg");

        assertEquals(HttpStatus.CREATED, upload.getStatusCode());
        assertTrue(Files.exists(cmsRoot.resolve("products/DEFAULT").resolve(product.getSku())
                .resolve("LARGE/image.jpg")));
        assertTrue(Files.exists(cmsRoot.resolve("products/DEFAULT").resolve(product.getSku())
                .resolve("SMALL/image.jpg")));

        ResponseEntity<String> images = testRestTemplate.getForEntity(
                "/api/v1/product/" + product.getId() + "/images?store=DEFAULT", String.class);
        assertEquals(HttpStatus.OK, images.getStatusCode());
        assertNotNull(images.getBody());
        assertTrue(images.getBody().contains("image.jpg"));
    }

    @Test
    public void uploadWithoutAuthorizationIsRejected() throws Exception {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.MULTIPART_FORM_DATA);
        ResponseEntity<String> response = testRestTemplate.postForEntity(
                "/api/v1/private/product/" + Long.MAX_VALUE + "/image?store=DEFAULT",
                multipartRequest("image.jpg", headers), String.class);

        assertTrue(response.getStatusCode() == HttpStatus.UNAUTHORIZED
                || response.getStatusCode() == HttpStatus.FORBIDDEN);
    }

    @Test
    public void rejectsTraversalNamesWithoutWritingOrListingThem() throws Exception {
        ReadableProduct product = sampleProduct(
                "local-traversal-" + UUID.randomUUID().toString().replace("-", "").substring(0, 8));

        for (String filename : new String[] { "../../../../evil.jpg", "..\\..\\evil.jpg" }) {
            ResponseEntity<String> upload = upload(product.getId(), filename);
            assertFalse(upload.getStatusCode().is2xxSuccessful());
            assertNoEvilFilesUnderCmsRoot();

            ResponseEntity<String> images = testRestTemplate.getForEntity(
                    "/api/v1/product/" + product.getId() + "/images?store=DEFAULT", String.class);
            assertEquals(HttpStatus.OK, images.getStatusCode());
            assertNotNull(images.getBody());
            assertFalse(images.getBody().contains("evil"));
        }
    }

    private void assertNoEvilFilesUnderCmsRoot() throws IOException {
        Path cmsItRoot = Paths.get("target", "cms-it").toAbsolutePath().normalize();
        if (!Files.exists(cmsItRoot)) {
            return;
        }
        try (Stream<Path> paths = Files.walk(cmsItRoot)) {
            assertFalse(paths.filter(Files::isRegularFile)
                    .anyMatch(path -> path.getFileName().toString().contains("evil")));
        }
    }

    private ResponseEntity<String> upload(Long productId, String filename) throws IOException {
        HttpHeaders headers = getHeader();
        headers.setContentType(MediaType.MULTIPART_FORM_DATA);
        HttpEntity<MultiValueMap<String, Object>> request = multipartRequest(filename, headers);
        return testRestTemplate.postForEntity(
                "/api/v1/private/product/" + productId + "/image?store=DEFAULT", request, String.class);
    }

    private HttpEntity<MultiValueMap<String, Object>> multipartRequest(String filename, HttpHeaders headers)
            throws IOException {
        byte[] image;
        try (InputStream stream = new ClassPathResource("image.jpg").getInputStream()) {
            image = FileCopyUtils.copyToByteArray(stream);
        }
        MultiValueMap<String, Object> body = new LinkedMultiValueMap<>();
        body.add("file", new ByteArrayResource(image) {
            @Override
            public String getFilename() {
                return filename;
            }
        });
        return new HttpEntity<>(body, headers);
    }
}
