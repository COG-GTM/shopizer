package com.salesmanager.test.shop.uat;

import static org.junit.Assert.assertEquals;

import java.util.Collections;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.test.context.junit4.SpringRunner;

import com.salesmanager.shop.application.ShopApplication;
import com.salesmanager.test.shop.common.ServicesTestSupport;

@RunWith(SpringRunner.class)
@SpringBootTest(classes = ShopApplication.class, webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
public class ServicesBasicAuthUatTest extends ServicesTestSupport {

	@Test
	public void servicesEndpointsEnforceBasicAuthentication() {
		ResponseEntity<String> publicService = testRestTemplate.getForEntity("/services/public/DEFAULT", String.class);
		assertEquals(HttpStatus.ACCEPTED, publicService.getStatusCode());

		HttpHeaders anonymousHeaders = jsonHeaders();
		ResponseEntity<String> anonymous = testRestTemplate.postForEntity(
				"/services/private/system/optin", new HttpEntity<>("{}", anonymousHeaders), String.class);
		assertEquals("Unexpected anonymous response " + anonymous.getHeaders() + ": " + anonymous.getBody(),
				HttpStatus.UNAUTHORIZED, anonymous.getStatusCode());

		HttpHeaders invalidCredentials = jsonHeaders();
		invalidCredentials.setBasicAuth("admin@shopizer.com", "wrong-password");
		ResponseEntity<String> invalid = testRestTemplate.postForEntity(
				"/services/private/system/optin", new HttpEntity<>("{}", invalidCredentials), String.class);
		assertEquals(HttpStatus.UNAUTHORIZED, invalid.getStatusCode());

		HttpHeaders administratorCredentials = jsonHeaders();
		administratorCredentials.setBasicAuth("admin@shopizer.com", "password");
		ResponseEntity<String> authorized = testRestTemplate.postForEntity(
				"/services/private/system/optin", new HttpEntity<>("{}", administratorCredentials), String.class);
		assertEquals(HttpStatus.OK, authorized.getStatusCode());
	}

	private static HttpHeaders jsonHeaders() {
		HttpHeaders headers = new HttpHeaders();
		headers.setContentType(MediaType.APPLICATION_JSON);
		headers.setAccept(Collections.singletonList(MediaType.APPLICATION_JSON));
		return headers;
	}
}
