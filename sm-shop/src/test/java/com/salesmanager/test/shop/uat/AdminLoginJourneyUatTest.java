package com.salesmanager.test.shop.uat;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.SpringBootTest.WebEnvironment;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.test.context.junit4.SpringRunner;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.salesmanager.shop.application.ShopApplication;
import com.salesmanager.shop.store.security.AuthenticationRequest;
import com.salesmanager.shop.store.security.AuthenticationResponse;
import com.salesmanager.test.shop.common.ServicesTestSupport;

@RunWith(SpringRunner.class)
@SpringBootTest(classes = ShopApplication.class, webEnvironment = WebEnvironment.RANDOM_PORT)
public class AdminLoginJourneyUatTest extends ServicesTestSupport {

	@Test
	public void adminCanLoginAndReadProfileWithExpectedUnauthorizedResponses() throws Exception {
		ResponseEntity<AuthenticationResponse> login = testRestTemplate.postForEntity("/api/v1/private/login",
				new HttpEntity<>(new AuthenticationRequest("admin@shopizer.com", "password")),
				AuthenticationResponse.class);
		assertEquals(HttpStatus.OK, login.getStatusCode());
		assertNotNull(login.getBody());
		assertNotNull(login.getBody().getToken());

		HttpHeaders bearer = new HttpHeaders();
		bearer.setBearerAuth(login.getBody().getToken());
		ResponseEntity<String> profile = testRestTemplate.exchange("/api/v1/private/user/profile", HttpMethod.GET,
				new HttpEntity<>(bearer), String.class);
		assertEquals(HttpStatus.OK, profile.getStatusCode());
		JsonNode profileJson = new ObjectMapper().readTree(profile.getBody());
		assertEquals("admin@shopizer.com", profileJson.path("emailAddress").asText());

		ResponseEntity<String> missingToken = testRestTemplate.getForEntity("/api/v1/private/user/profile",
				String.class);
		assertEquals(HttpStatus.UNAUTHORIZED, missingToken.getStatusCode());
		assertEquals("Basic realm=\"api-admin-realm\"",
				missingToken.getHeaders().getFirst(HttpHeaders.WWW_AUTHENTICATE));

		ResponseEntity<String> badToken = testRestTemplate.exchange("/api/v1/private/user/profile", HttpMethod.GET,
				new HttpEntity<>(badTokenHeaders()), String.class);
		assertEquals(HttpStatus.INTERNAL_SERVER_ERROR, badToken.getStatusCode());

		ResponseEntity<String> wrongPassword = testRestTemplate.postForEntity("/api/v1/private/login",
				new HttpEntity<>(new AuthenticationRequest("admin@shopizer.com", "wrong")), String.class);
		assertEquals(HttpStatus.UNAUTHORIZED, wrongPassword.getStatusCode());
	}

	private HttpHeaders badTokenHeaders() {
		HttpHeaders headers = new HttpHeaders();
		headers.setBearerAuth("garbage.token.value");
		return headers;
	}
}
