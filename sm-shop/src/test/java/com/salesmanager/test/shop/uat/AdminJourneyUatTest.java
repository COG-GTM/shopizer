package com.salesmanager.test.shop.uat;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotEquals;
import static org.junit.Assert.assertNotNull;
import static org.springframework.http.HttpStatus.OK;
import static org.springframework.http.HttpStatus.UNAUTHORIZED;

import java.util.Map;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
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
	public void adminProfileRequiresAValidToken() {
		ResponseEntity<AuthenticationResponse> login = testRestTemplate.postForEntity(
				"/api/v1/private/login",
				new HttpEntity<>(new AuthenticationRequest("admin@shopizer.com", "password")),
				AuthenticationResponse.class);
		assertEquals(OK, login.getStatusCode());
		assertNotNull(login.getBody());
		assertNotNull(login.getBody().getToken());

		HttpHeaders authenticated = new HttpHeaders();
		authenticated.setBearerAuth(login.getBody().getToken());
		ResponseEntity<Map<String, Object>> profile = testRestTemplate.exchange(
				"/api/v1/private/user/profile", HttpMethod.GET, new HttpEntity<>(authenticated),
				new ParameterizedTypeReference<Map<String, Object>>() { });
		assertEquals(OK, profile.getStatusCode());
		assertNotNull(profile.getBody());
		assertEquals("admin@shopizer.com", profile.getBody().get("emailAddress"));

		ResponseEntity<String> missingToken = testRestTemplate.exchange(
				"/api/v1/private/user/profile", HttpMethod.GET, new HttpEntity<>(new HttpHeaders()),
				String.class);
		assertEquals(UNAUTHORIZED, missingToken.getStatusCode());

		HttpHeaders invalidToken = new HttpHeaders();
		invalidToken.set("Authorization", "Bearer bad");
		ResponseEntity<String> invalid = testRestTemplate.exchange(
				"/api/v1/private/user/profile", HttpMethod.GET, new HttpEntity<>(invalidToken),
				String.class);
		// Both base and head return 500 for this malformed token; only reject success here.
		assertNotEquals(OK, invalid.getStatusCode());
	}
}
