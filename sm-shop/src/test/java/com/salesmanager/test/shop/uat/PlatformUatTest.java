package com.salesmanager.test.shop.uat;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;
import static org.springframework.http.HttpStatus.OK;

import java.nio.charset.StandardCharsets;
import java.util.Base64;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.SpringBootTest.WebEnvironment;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.test.context.junit4.SpringRunner;

import com.fasterxml.jackson.databind.JsonNode;
import com.salesmanager.shop.application.ShopApplication;
import com.salesmanager.test.shop.common.ServicesTestSupport;

@SpringBootTest(classes = ShopApplication.class, webEnvironment = WebEnvironment.RANDOM_PORT)
@RunWith(SpringRunner.class)
public class PlatformUatTest extends ServicesTestSupport {

	@Autowired
	private TestRestTemplate testRestTemplate;

	@Test
	public void healthDocumentationAndLegacyServicesAuthenticationWork() {
		ResponseEntity<JsonNode> health = testRestTemplate.getForEntity("/actuator/health", JsonNode.class);
		assertEquals(OK, health.getStatusCode());
		assertEquals("UP", health.getBody().path("status").asText());

		ResponseEntity<JsonNode> docs = testRestTemplate.getForEntity("/v2/api-docs", JsonNode.class);
		assertEquals(OK, docs.getStatusCode());

		HttpHeaders validHeaders = new HttpHeaders();
		validHeaders.setBasicAuth("admin@shopizer.com", "password", StandardCharsets.UTF_8);
		validHeaders.setContentType(MediaType.APPLICATION_JSON);
		ResponseEntity<String> valid = testRestTemplate.exchange("/services/private/system/optin",
				HttpMethod.POST, new HttpEntity<>("{}", validHeaders), String.class);
		assertTrue("Valid Basic auth should reach the private services handler, got " + valid.getStatusCodeValue(),
				valid.getStatusCodeValue() != 401 && valid.getStatusCodeValue() != 403);

		HttpHeaders invalidHeaders = new HttpHeaders();
		String encoded = Base64.getEncoder()
				.encodeToString("admin@shopizer.com:not-a-password".getBytes(StandardCharsets.UTF_8));
		invalidHeaders.set("Authorization", "Basic " + encoded);
		invalidHeaders.setContentType(MediaType.APPLICATION_JSON);
		ResponseEntity<String> invalid = testRestTemplate.exchange("/services/private/system/optin",
				HttpMethod.POST, new HttpEntity<>("{}", invalidHeaders), String.class);
		assertEquals(401, invalid.getStatusCodeValue());
	}
}
