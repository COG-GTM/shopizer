package com.salesmanager.test.shop.uat;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;
import static org.springframework.http.HttpStatus.CREATED;
import static org.springframework.http.HttpStatus.OK;

import java.util.Base64;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.springframework.boot.test.context.SpringBootTest;
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
import com.salesmanager.test.shop.common.ServicesTestSupport;

@RunWith(SpringRunner.class)
@SpringBootTest(classes = ShopApplication.class, webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
public class CmsContentUatTest extends ServicesTestSupport {

	private static final byte[] PNG_BYTES = Base64.getDecoder().decode(
			"iVBORw0KGgoAAAANSUhEUgAAAAEAAAABCAQAAAC1HAwCAAAAC0lEQVR42mP8/x8AAwMCAO+jx+QAAAAASUVORK5CYII=");

	@Test
	public void contentImageCanBeUploadedFetchedAndRemoved() {
		String fileName = "uat-" + UUID.randomUUID() + ".png";
		HttpHeaders adminHeaders = getHeader();

		try {
			MultiValueMap<String, Object> parts = new LinkedMultiValueMap<>();
			parts.add("qqfile", filePart(PNG_BYTES, fileName));
			parts.add("qquuid", UUID.randomUUID().toString());
			parts.add("qqfilename", fileName);
			ResponseEntity<String> upload = testRestTemplate.exchange(
					"/api/v1/private/content/images/add?store=DEFAULT&lang=en", HttpMethod.POST,
					new HttpEntity<>(parts, multipartHeaders(adminHeaders)), String.class);
			assertEquals(CREATED, upload.getStatusCode());
			assertTrue(listContentFiles(adminHeaders).stream()
					.anyMatch(file -> fileName.equals(file.get("name"))));

			ResponseEntity<byte[]> fetched = testRestTemplate.getForEntity(
					"/static/files/DEFAULT/IMAGE/" + fileName, byte[].class);
			assertEquals(OK, fetched.getStatusCode());
			assertArrayEquals(PNG_BYTES, fetched.getBody());

			ResponseEntity<String> removed = testRestTemplate.exchange(
					"/api/v1/private/content/images/remove?store=DEFAULT&lang=en&path=/" + fileName,
					HttpMethod.DELETE, new HttpEntity<>(adminHeaders), String.class);
			assertEquals(OK, removed.getStatusCode());
			assertTrue(listContentFiles(adminHeaders).stream()
					.noneMatch(file -> fileName.equals(file.get("name"))));
		} finally {
			testRestTemplate.exchange(
					"/api/v1/private/content/images/remove?store=DEFAULT&lang=en&path=/" + fileName,
					HttpMethod.DELETE, new HttpEntity<>(adminHeaders), String.class);
		}
	}

	private List<Map<String, Object>> listContentFiles(HttpHeaders headers) {
		ResponseEntity<List<Map<String, Object>>> response = testRestTemplate.exchange(
				"/api/v1/private/content/list?store=DEFAULT&lang=en", HttpMethod.GET,
				new HttpEntity<>(headers),
				new ParameterizedTypeReference<List<Map<String, Object>>>() { });
		assertEquals(OK, response.getStatusCode());
		assertNotNull(response.getBody());
		return response.getBody();
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
