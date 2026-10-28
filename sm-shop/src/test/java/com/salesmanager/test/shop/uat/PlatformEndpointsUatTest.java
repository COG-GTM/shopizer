package com.salesmanager.test.shop.uat;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotEquals;
import static org.junit.Assert.assertTrue;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.SpringBootTest.WebEnvironment;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.test.context.junit4.SpringRunner;

import com.salesmanager.shop.application.ShopApplication;
import com.salesmanager.test.shop.common.ServicesTestSupport;

@SpringBootTest(classes = ShopApplication.class, webEnvironment = WebEnvironment.RANDOM_PORT)
@RunWith(SpringRunner.class)
public class PlatformEndpointsUatTest extends ServicesTestSupport {

	@Test
	public void healthSwaggerAndBasicAuthenticatedOptinAreAvailable() {
		ResponseEntity<String> health = testRestTemplate.getForEntity("/actuator/health/ping/", String.class);
		ResponseEntity<String> swagger = testRestTemplate.getForEntity("/v2/api-docs", String.class);
		ResponseEntity<String> validCredentials = postOptin("admin@shopizer.com", "password");
		ResponseEntity<String> invalidCredentials = postOptin("admin@shopizer.com", "wrong-password");

		assertEquals(HttpStatus.OK, health.getStatusCode());
		assertEquals(HttpStatus.OK, swagger.getStatusCode());
		assertTrue(swagger.getBody().contains("swagger"));
		assertNotEquals(HttpStatus.UNAUTHORIZED, validCredentials.getStatusCode());
		assertEquals(HttpStatus.UNAUTHORIZED, invalidCredentials.getStatusCode());
	}

	private ResponseEntity<String> postOptin(String username, String password) {
		HttpHeaders headers = new HttpHeaders();
		headers.setBasicAuth(username, password);
		headers.setContentType(MediaType.APPLICATION_JSON);
		return testRestTemplate.postForEntity("/services/private/system/optin",
				new HttpEntity<>("{}", headers), String.class);
	}
}
