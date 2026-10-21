package com.salesmanager.test.shop.uat;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;
import static org.springframework.http.HttpStatus.OK;

import java.util.Map;

import org.junit.Assume;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpMethod;
import org.springframework.http.ResponseEntity;
import org.springframework.test.context.junit4.SpringRunner;

import com.salesmanager.shop.application.ShopApplication;
import com.salesmanager.test.shop.common.ServicesTestSupport;

@RunWith(SpringRunner.class)
@SpringBootTest(classes = ShopApplication.class, webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
public class PlatformUatTest extends ServicesTestSupport {

	@Test
	public void healthAndApiDocsAreAvailable() {
		ResponseEntity<Map<String, Object>> health = testRestTemplate.exchange(
				"/actuator/health", HttpMethod.GET, HttpEntity.EMPTY,
				new ParameterizedTypeReference<Map<String, Object>>() { });
		assertEquals(OK, health.getStatusCode());
		assertNotNull(health.getBody());
		assertEquals("UP", health.getBody().get("status"));

		ResponseEntity<Map<String, Object>> docs = testRestTemplate.exchange(
				"/v2/api-docs", HttpMethod.GET, HttpEntity.EMPTY,
				new ParameterizedTypeReference<Map<String, Object>>() { });
		assertEquals(OK, docs.getStatusCode());
		assertNotNull(docs.getBody());
		assertTrue(docs.getBody().get("paths") instanceof Map);
		assertTrue(!((Map<?, ?>) docs.getBody().get("paths")).isEmpty());
	}

	@Test
	public void seededLogoIsAvailableWhenPresentInTestRuntime() {
		ResponseEntity<byte[]> logo = testRestTemplate.getForEntity(
				"/static/files/DEFAULT/LOGO/image.jpg", byte[].class);
		Assume.assumeTrue("Seeded logo is not available in the sm-shop test runtime",
				logo.getStatusCode() == OK);
		assertNotNull(logo.getBody());
		assertTrue(logo.getBody().length > 0);
	}
}
