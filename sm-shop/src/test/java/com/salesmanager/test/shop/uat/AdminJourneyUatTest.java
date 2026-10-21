package com.salesmanager.test.shop.uat;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.test.context.junit4.SpringRunner;

import com.salesmanager.shop.application.ShopApplication;
import com.salesmanager.shop.store.security.AuthenticationRequest;
import com.salesmanager.shop.store.security.AuthenticationResponse;
import com.salesmanager.test.shop.common.ServicesTestSupport;

@RunWith(SpringRunner.class)
@SpringBootTest(classes = ShopApplication.class, webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
public class AdminJourneyUatTest extends ServicesTestSupport {

	@Test
	public void administratorCanLogInAndAccessPrivateResources() {
		ResponseEntity<AuthenticationResponse> login = testRestTemplate.postForEntity("/api/v1/private/login",
				new HttpEntity<>(new AuthenticationRequest("admin@shopizer.com", "password"), jsonHeaders()),
				AuthenticationResponse.class);
		assertEquals(HttpStatus.OK, login.getStatusCode());
		assertNotNull(login.getBody());
		assertNotNull(login.getBody().getToken());
		assertFalse(login.getBody().getToken().isEmpty());

		HttpEntity<Void> authenticated = new HttpEntity<>(bearerHeaders(login.getBody().getToken()));
		ResponseEntity<String> customers = testRestTemplate.exchange("/api/v1/private/customers", HttpMethod.GET,
				authenticated, String.class);
		assertEquals(HttpStatus.OK, customers.getStatusCode());

		ResponseEntity<String> anonymousCustomers = testRestTemplate.getForEntity("/api/v1/private/customers",
				String.class);
		assertEquals(HttpStatus.UNAUTHORIZED, anonymousCustomers.getStatusCode());

		ResponseEntity<String> badPassword = testRestTemplate.postForEntity("/api/v1/private/login",
				new HttpEntity<>(new AuthenticationRequest("admin@shopizer.com", "wrong-password"), jsonHeaders()),
				String.class);
		assertEquals(HttpStatus.UNAUTHORIZED, badPassword.getStatusCode());

		ResponseEntity<String> profile = testRestTemplate.exchange("/api/v1/private/user/profile", HttpMethod.GET,
				authenticated, String.class);
		assertEquals(HttpStatus.OK, profile.getStatusCode());
	}

	private static HttpHeaders jsonHeaders() {
		HttpHeaders headers = new HttpHeaders();
		headers.setContentType(MediaType.APPLICATION_JSON);
		return headers;
	}

	private static HttpHeaders bearerHeaders(String token) {
		HttpHeaders headers = jsonHeaders();
		headers.setBearerAuth(token);
		return headers;
	}
}
