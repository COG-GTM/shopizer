package com.salesmanager.test.shop.uat;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;
import static org.springframework.http.HttpStatus.UNAUTHORIZED;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.SpringBootTest.WebEnvironment;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.test.context.junit4.SpringRunner;

import com.salesmanager.shop.application.ShopApplication;
import com.salesmanager.test.shop.common.ServicesTestSupport;

@RunWith(SpringRunner.class)
@SpringBootTest(classes = ShopApplication.class, webEnvironment = WebEnvironment.RANDOM_PORT)
public class ServicesBasicAuthUatTest extends ServicesTestSupport {

	@Test
	public void publicServicesAndPrivateOptinUseExpectedAuthentication() {
		ResponseEntity<String> publicStore = testRestTemplate.getForEntity(
				"/services/public/DEFAULT", String.class);
		assertTrue(publicStore.getStatusCode().is2xxSuccessful());

		HttpHeaders validHeaders = new HttpHeaders();
		validHeaders.setBasicAuth("admin@shopizer.com", "password");
		validHeaders.setContentType(MediaType.TEXT_PLAIN);
		ResponseEntity<String> authorized = testRestTemplate.postForEntity(
				"/services/private/system/optin",
				new HttpEntity<>("{\"type\":\"NEWSLETTER\"}", validHeaders), String.class);
		assertTrue(authorized.getStatusCode().is2xxSuccessful());
		assertTrue(authorized.getStatusCode() != UNAUTHORIZED);

		HttpHeaders invalidHeaders = new HttpHeaders();
		invalidHeaders.setBasicAuth("admin@shopizer.com", "wrong-password");
		invalidHeaders.setContentType(MediaType.TEXT_PLAIN);
		ResponseEntity<String> invalid = testRestTemplate.postForEntity(
				"/services/private/system/optin",
				new HttpEntity<>("{\"type\":\"NEWSLETTER\"}", invalidHeaders), String.class);
		assertEquals(UNAUTHORIZED, invalid.getStatusCode());
	}
}
