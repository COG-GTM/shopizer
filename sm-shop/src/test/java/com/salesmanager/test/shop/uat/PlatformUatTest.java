package com.salesmanager.test.shop.uat;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import java.util.Map;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.SpringBootTest.WebEnvironment;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.test.context.junit4.SpringRunner;

import com.salesmanager.shop.application.ShopApplication;
import com.salesmanager.test.shop.common.ServicesTestSupport;

@SpringBootTest(classes = ShopApplication.class, webEnvironment = WebEnvironment.RANDOM_PORT)
@RunWith(SpringRunner.class)
public class PlatformUatTest extends ServicesTestSupport {

	@Test
	public void healthSwaggerAndServicesBasicAuthenticationWork() {
		ResponseEntity<String> health = testRestTemplate.getForEntity("/actuator/health", String.class);
		assertEquals(HttpStatus.OK, health.getStatusCode());
		assertTrue(health.getBody().contains("\"status\":\"UP\""));

		ResponseEntity<Map> swagger = testRestTemplate.getForEntity("/v2/api-docs", Map.class);
		assertEquals(HttpStatus.OK, swagger.getStatusCode());
		assertTrue(((Map<?, ?>) swagger.getBody().get("paths")).containsKey("/api/v1/private/content/page"));

		HttpHeaders noCredentials = new HttpHeaders();
		noCredentials.setContentType(MediaType.TEXT_PLAIN);
		ResponseEntity<String> anonymous = testRestTemplate.exchange("/services/private/system/optin",
				HttpMethod.POST, new HttpEntity<>("{}", noCredentials), String.class);
		assertEquals(HttpStatus.FOUND, anonymous.getStatusCode());

		HttpHeaders invalidCredentials = new HttpHeaders();
		invalidCredentials.setContentType(MediaType.TEXT_PLAIN);
		invalidCredentials.setBasicAuth("invalid@example.test", "incorrect");
		ResponseEntity<String> invalid = testRestTemplate.exchange("/services/private/system/optin",
				HttpMethod.POST, new HttpEntity<>("{}", invalidCredentials), String.class);
		assertEquals(HttpStatus.UNAUTHORIZED, invalid.getStatusCode());

		HttpHeaders validCredentials = new HttpHeaders();
		validCredentials.setContentType(MediaType.TEXT_PLAIN);
		validCredentials.setBasicAuth("admin@shopizer.com", "password");
		ResponseEntity<String> authorized = testRestTemplate.exchange("/services/private/system/optin",
				HttpMethod.POST, new HttpEntity<>("{}", validCredentials), String.class);
		assertTrue(authorized.getStatusCode() != HttpStatus.UNAUTHORIZED);
	}
}
