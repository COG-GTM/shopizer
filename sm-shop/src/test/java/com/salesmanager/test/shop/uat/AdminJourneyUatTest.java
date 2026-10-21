package com.salesmanager.test.shop.uat;

import static org.junit.jupiter.api.Assertions.assertEquals;

import com.salesmanager.shop.model.user.ReadableUser;
import com.salesmanager.shop.store.security.AuthenticationRequest;
import com.salesmanager.shop.store.security.AuthenticationResponse;
import com.salesmanager.test.shop.common.ServicesTestSupport;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

class AdminJourneyUatTest extends ServicesTestSupport {

	@Test
	void adminCanAuthenticateAndAccessProfile() {
		ResponseEntity<AuthenticationResponse> login = testRestTemplate.postForEntity(
				"/api/v1/private/login",
				new HttpEntity<>(new AuthenticationRequest("admin@shopizer.com", "password")),
				AuthenticationResponse.class);
		assertEquals(HttpStatus.OK, login.getStatusCode());

		HttpHeaders tokenHeaders = new HttpHeaders();
		tokenHeaders.setBearerAuth(login.getBody().getToken());
		ResponseEntity<ReadableUser> profile = testRestTemplate.exchange(
				"/api/v1/private/user/profile", HttpMethod.GET,
				new HttpEntity<>(tokenHeaders), ReadableUser.class);
		assertEquals(HttpStatus.OK, profile.getStatusCode());
		assertEquals("admin@shopizer.com", profile.getBody().getEmailAddress());

		ResponseEntity<String> noToken = testRestTemplate.getForEntity(
				"/api/v1/private/user/profile", String.class);
		assertEquals(HttpStatus.UNAUTHORIZED, noToken.getStatusCode());
	}
}
