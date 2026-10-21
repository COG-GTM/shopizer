package com.salesmanager.test.shop.integration.cache;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;
import static org.springframework.http.HttpStatus.CREATED;
import static org.springframework.http.HttpStatus.OK;

import java.util.Base64;
import java.util.List;
import java.util.UUID;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.test.context.junit4.SpringRunner;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;

import com.salesmanager.shop.application.ShopApplication;
import com.salesmanager.shop.model.catalog.product.ReadableImage;
import com.salesmanager.shop.model.catalog.product.ReadableProduct;
import com.salesmanager.shop.model.entity.Entity;
import com.salesmanager.test.shop.common.ServicesTestSupport;

@RunWith(SpringRunner.class)
@org.springframework.boot.test.context.SpringBootTest(
		classes = ShopApplication.class,
		webEnvironment = org.springframework.boot.test.context.SpringBootTest.WebEnvironment.RANDOM_PORT)
public class ProductImageCmsIntegrationTest extends ServicesTestSupport {

	private static final byte[] PNG_BYTES = Base64.getDecoder().decode(
			"iVBORw0KGgoAAAANSUhEUgAAAAEAAAABCAQAAAC1HAwCAAAAC0lEQVR42mP8/x8AAwMCAO+jx+QAAAAASUVORK5CYII=");

	@Test
	public void productImagesAreUploadedListedAndRemovedByImageId() {
		String code = "cache-image-" + UUID.randomUUID();
		String imageName = "img-" + UUID.randomUUID() + ".png";
		HttpHeaders adminHeaders = getHeader();
		ReadableProduct product = sampleProduct(code);
		String productId = String.valueOf(product.getId());

		try {
			MultiValueMap<String, Object> parts = new LinkedMultiValueMap<>();
			parts.add("file", filePart(PNG_BYTES, imageName));
			ResponseEntity<String> upload = testRestTemplate.exchange(
					"/api/v1/private/product/" + productId + "/image?store=DEFAULT&lang=en",
					HttpMethod.POST, new HttpEntity<>(parts, multipartHeaders(adminHeaders)), String.class);
			assertEquals(CREATED, upload.getStatusCode());

			ResponseEntity<List<ReadableImage>> listed = listImages(productId, adminHeaders);
			assertEquals(OK, listed.getStatusCode());
			assertNotNull(listed.getBody());
			assertEquals(1, listed.getBody().size());
			ReadableImage image = listed.getBody().get(0);
			assertNotNull(image.getId());

			String imageBase = "/static/products/DEFAULT/" + product.getSku() + "/";
			ResponseEntity<byte[]> small = testRestTemplate.getForEntity(
					imageBase + "SMALL/" + imageName, byte[].class);
			ResponseEntity<byte[]> large = testRestTemplate.getForEntity(
					imageBase + "LARGE/" + imageName, byte[].class);
			assertEquals(OK, small.getStatusCode());
			assertEquals(OK, large.getStatusCode());
			assertNotNull(small.getBody());
			assertNotNull(large.getBody());
			assertTrue(small.getBody().length > 0);
			assertTrue(large.getBody().length > 0);

			ResponseEntity<String> deleted = testRestTemplate.exchange(
					"/api/v1/private/product/" + productId + "/image/" + image.getId()
							+ "?store=DEFAULT&lang=en",
					HttpMethod.DELETE, new HttpEntity<>(adminHeaders), String.class);
			assertEquals(OK, deleted.getStatusCode());

			ResponseEntity<List<ReadableImage>> afterDelete = listImages(productId, adminHeaders);
			assertEquals(OK, afterDelete.getStatusCode());
			assertNotNull(afterDelete.getBody());
			assertTrue(afterDelete.getBody().stream()
					.noneMatch(candidate -> image.getId().equals(candidate.getId())));
		} finally {
			testRestTemplate.exchange("/api/v1/private/product/" + productId
					+ "?store=DEFAULT&lang=en", HttpMethod.DELETE,
					new HttpEntity<>(adminHeaders), Entity.class);
		}
	}

	private ResponseEntity<List<ReadableImage>> listImages(String productId, HttpHeaders headers) {
		return testRestTemplate.exchange(
				"/api/v1/product/" + productId + "/images?store=DEFAULT&lang=en",
				HttpMethod.GET, new HttpEntity<>(headers),
				new ParameterizedTypeReference<List<ReadableImage>>() { });
	}

	private static HttpEntity<ByteArrayResource> filePart(byte[] bytes, String fileName) {
		ByteArrayResource resource = new ByteArrayResource(bytes) {
			@Override
			public String getFilename() {
				return fileName;
			}
		};
		HttpHeaders headers = new HttpHeaders();
		headers.setContentType(MediaType.IMAGE_PNG);
		return new HttpEntity<>(resource, headers);
	}

	private static HttpHeaders multipartHeaders(HttpHeaders source) {
		HttpHeaders headers = new HttpHeaders();
		headers.set("Authorization", source.getFirst("Authorization"));
		headers.setContentType(MediaType.MULTIPART_FORM_DATA);
		return headers;
	}
}
