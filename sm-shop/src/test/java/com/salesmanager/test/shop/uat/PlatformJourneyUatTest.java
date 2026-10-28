package com.salesmanager.test.shop.uat;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.test.context.junit.jupiter.SpringExtension;

import com.salesmanager.shop.application.ShopApplication;
import com.salesmanager.test.shop.common.ServicesTestSupport;

@SpringBootTest(classes = ShopApplication.class, webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ExtendWith(SpringExtension.class)
public class PlatformJourneyUatTest extends ServicesTestSupport {

	@Test
	public void servicesBasicAuthActuatorAndSwaggerJourneysWork() {
		HttpHeaders basicHeaders = new HttpHeaders();
		basicHeaders.setBasicAuth("admin@shopizer.com", "password");
		basicHeaders.setContentType(MediaType.TEXT_PLAIN);
		ResponseEntity<String> authenticatedService = testRestTemplate.exchange(
				"/services/private/system/optin", HttpMethod.POST,
				new HttpEntity<>("{}", basicHeaders), String.class);
		assertEquals(HttpStatus.OK, authenticatedService.getStatusCode());

		HttpHeaders invalidHeaders = new HttpHeaders();
		invalidHeaders.setBasicAuth("admin@shopizer.com", "incorrect");
		invalidHeaders.setContentType(MediaType.TEXT_PLAIN);
		ResponseEntity<String> invalidService = testRestTemplate.exchange(
				"/services/private/system/optin", HttpMethod.POST,
				new HttpEntity<>("{}", invalidHeaders), String.class);
		assertEquals(HttpStatus.UNAUTHORIZED, invalidService.getStatusCode());

		ResponseEntity<String> health = testRestTemplate.getForEntity("/actuator/health", String.class);
		assertEquals(HttpStatus.OK, health.getStatusCode());
		assertTrue(health.getBody().contains("UP"));

		ResponseEntity<String> apiDocs = testRestTemplate.getForEntity("/v2/api-docs", String.class);
		assertEquals(HttpStatus.OK, apiDocs.getStatusCode());
		assertTrue(apiDocs.getBody().contains("swagger"));
	}
}
