package com.salesmanager.test.shop.integration.security;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.http.HttpMethod.GET;
import static org.springframework.http.HttpStatus.OK;
import static org.springframework.http.HttpStatus.UNAUTHORIZED;

import java.util.Date;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseEntity;

import com.salesmanager.shop.store.security.AuthenticationRequest;
import com.salesmanager.shop.store.security.AuthenticationResponse;
import com.salesmanager.shop.store.security.JWTTokenUtil;
import com.salesmanager.test.shop.common.ServicesTestSupport;

@ExtendWith(OutputCaptureExtension.class)
class JwtAuthenticationIntegrationTest extends ServicesTestSupport {

	@Autowired
	private JWTTokenUtil jwtTokenUtil;

	@Autowired
	private TestRestTemplate testRestTemplate;

	@Test
	void adminLoginGeneratesExpectedTokenAndAuthorizesPrivateUsers(CapturedOutput output) {
		ResponseEntity<AuthenticationResponse> login = login("admin@shopizer.com", "password");

		assertEquals(OK, login.getStatusCode());
		assertNotNull(login.getBody());
		String token = login.getBody().getToken();
		assertEquals("admin@shopizer.com", jwtTokenUtil.getUsernameFromToken(token));
		assertEquals("api", jwtTokenUtil.getAudienceFromToken(token));
		Date issuedAt = jwtTokenUtil.getIssuedAtDateFromToken(token);
		Date expiresAt = jwtTokenUtil.getExpirationDateFromToken(token);
		assertNotNull(issuedAt);
		assertNotNull(expiresAt);
		assertEquals(604800L, (expiresAt.getTime() - issuedAt.getTime()) / 1000L);
		assertFalse(output.getOut().contains("doGenerateToken"));

		ResponseEntity<String> users = privateUsers(token);
		assertEquals(OK, users.getStatusCode());
	}

	@Test
	void privateUsersRejectsMissingAuthorization() {
		ResponseEntity<String> missing = testRestTemplate.exchange("/api/v1/private/users", GET,
				HttpEntity.EMPTY, String.class);
		assertTrue(missing.getStatusCode().value() == 401 || missing.getStatusCode().value() == 403);
	}

	@Test
	void refreshKeepsAdminTokenUsableAndBadPasswordIsUnauthorized() {
		ResponseEntity<AuthenticationResponse> login = login("admin@shopizer.com", "password");
		assertEquals(OK, login.getStatusCode());

		HttpHeaders headers = new HttpHeaders();
		headers.setBearerAuth(login.getBody().getToken());
		ResponseEntity<AuthenticationResponse> refreshed = testRestTemplate.exchange("/api/v1/auth/refresh",
				GET, new HttpEntity<>(headers), AuthenticationResponse.class);
		assertEquals(OK, refreshed.getStatusCode());
		assertNotNull(refreshed.getBody());
		assertEquals(OK, privateUsers(refreshed.getBody().getToken()).getStatusCode());

		assertEquals(UNAUTHORIZED, login("admin@shopizer.com", "wrong-password").getStatusCode());
	}

	private ResponseEntity<AuthenticationResponse> login(String username, String password) {
		return testRestTemplate.postForEntity("/api/v1/private/login",
				new HttpEntity<>(new AuthenticationRequest(username, password)), AuthenticationResponse.class);
	}

	private ResponseEntity<String> privateUsers(String token) {
		HttpHeaders headers = new HttpHeaders();
		headers.setBearerAuth(token);
		return testRestTemplate.exchange("/api/v1/private/users", GET, new HttpEntity<>(headers), String.class);
	}
}
