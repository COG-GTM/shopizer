package com.salesmanager.test.shop.uat;

import static org.junit.Assert.assertEquals;
import static org.springframework.http.HttpStatus.OK;
import static org.springframework.http.HttpStatus.UNAUTHORIZED;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.test.context.junit4.SpringRunner;

import com.salesmanager.shop.application.ShopApplication;
import com.salesmanager.test.shop.common.ServicesTestSupport;

@RunWith(SpringRunner.class)
@SpringBootTest(classes = ShopApplication.class, webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
public class ServicesBasicAuthUatTest extends ServicesTestSupport {

	@Test
	public void privateSystemOptinRequiresValidBasicCredentials() {
		HttpHeaders validHeaders = basicHeaders("password");
		ResponseEntity<String> valid = testRestTemplate.exchange(
				"/services/private/system/optin", HttpMethod.POST,
				new HttpEntity<>("{}", validHeaders), String.class);
		assertEquals(OK, valid.getStatusCode());

		HttpHeaders invalidHeaders = basicHeaders("wrong-password");
		ResponseEntity<String> invalid = testRestTemplate.exchange(
				"/services/private/system/optin", HttpMethod.POST,
				new HttpEntity<>("{}", invalidHeaders), String.class);
		assertEquals(UNAUTHORIZED, invalid.getStatusCode());
	}

	private static HttpHeaders basicHeaders(String password) {
		HttpHeaders headers = new HttpHeaders();
		headers.setBasicAuth("admin@shopizer.com", password);
		headers.setContentType(MediaType.APPLICATION_JSON);
		return headers;
	}
}
