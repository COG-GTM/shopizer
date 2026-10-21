package com.salesmanager.test.shop.integration.cache;

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

import javax.inject.Inject;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.test.context.junit4.SpringRunner;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.salesmanager.core.business.services.content.ContentService;
import com.salesmanager.core.model.content.FileContentType;
import com.salesmanager.core.model.content.OutputContentFile;
import com.salesmanager.shop.application.ShopApplication;
import com.salesmanager.test.shop.common.ServicesTestSupport;

@RunWith(SpringRunner.class)
@org.springframework.boot.test.context.SpringBootTest(
		classes = ShopApplication.class,
		webEnvironment = org.springframework.boot.test.context.SpringBootTest.WebEnvironment.RANDOM_PORT)
public class CmsContentApiIntegrationTest extends ServicesTestSupport {

	private static final Logger LOGGER = LoggerFactory.getLogger(CmsContentApiIntegrationTest.class);
	private static final byte[] PNG_BYTES = Base64.getDecoder().decode(
			"iVBORw0KGgoAAAANSUhEUgAAAAEAAAABCAQAAAC1HAwCAAAAC0lEQVR42mP8/x8AAwMCAO+jx+QAAAAASUVORK5CYII=");

	@Inject
	private ContentService contentService;

	@Test
	public void uploadsListsFetchesAndRemovesContentImage() throws Exception {
		String fileName = "uat-" + UUID.randomUUID() + ".png";
		String url = "/api/v1/private/content/images/add?store=DEFAULT&lang=en";
		HttpHeaders adminHeaders = getHeader();
		assertSeededLogoWhenAvailable();

		try {
			MultiValueMap<String, Object> parts = new LinkedMultiValueMap<>();
			parts.add("qqfile", filePart(PNG_BYTES, fileName));
			parts.add("qquuid", UUID.randomUUID().toString());
			parts.add("qqfilename", fileName);
			ResponseEntity<String> upload = testRestTemplate.exchange(url, HttpMethod.POST,
					new HttpEntity<>(parts, multipartHeaders(adminHeaders)), String.class);
			assertEquals(CREATED, upload.getStatusCode());

			List<Map<String, Object>> files = listContentFiles(adminHeaders);
			assertTrue(files.stream().anyMatch(file -> fileName.equals(file.get("name"))));

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

	private void assertSeededLogoWhenAvailable() throws Exception {
		OutputContentFile seededLogo = contentService.getContentFile(
				"DEFAULT", FileContentType.LOGO, "image.jpg");
		if (seededLogo == null) {
			LOGGER.info("No seeded DEFAULT logo is available in the sm-shop test runtime");
			return;
		}
		ResponseEntity<byte[]> logo = testRestTemplate.getForEntity(
				"/static/files/DEFAULT/LOGO/image.jpg", byte[].class);
		assertEquals(OK, logo.getStatusCode());
		assertNotNull(logo.getBody());
		assertTrue(logo.getBody().length > 0);
	}

	private List<Map<String, Object>> listContentFiles(HttpHeaders adminHeaders) {
		ResponseEntity<List<Map<String, Object>>> response = testRestTemplate.exchange(
				"/api/v1/private/content/list?store=DEFAULT&lang=en",
				HttpMethod.GET, new HttpEntity<>(adminHeaders),
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
