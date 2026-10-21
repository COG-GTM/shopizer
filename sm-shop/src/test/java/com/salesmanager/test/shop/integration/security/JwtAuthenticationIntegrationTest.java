package com.salesmanager.test.shop.integration.security;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import java.nio.charset.StandardCharsets;
import java.security.SecureRandom;
import java.util.Base64;
import java.util.Map;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.springframework.beans.factory.annotation.Value;
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
import com.salesmanager.shop.store.security.AuthenticationRequest;
import com.salesmanager.shop.store.security.AuthenticationResponse;
import com.salesmanager.test.shop.common.ServicesTestSupport;

/**
 * JWT issuance and verification through the real security filter chain after the JJWT 0.12 upgrade:
 * tokens must keep the JJWT 0.8 wire format, and any HS512 token signed with the configured jwt.secret
 * (as issued before the upgrade) must still be accepted.
 */
@SpringBootTest(classes = ShopApplication.class, webEnvironment = WebEnvironment.RANDOM_PORT)
@RunWith(SpringRunner.class)
public class JwtAuthenticationIntegrationTest extends ServicesTestSupport {

	private static final String ADMIN = "admin@shopizer.com";
	private static final String ADMIN_PROFILE = "/api/v1/private/user/profile";
	private static final Base64.Encoder B64URL = Base64.getUrlEncoder().withoutPadding();

	@Value("${jwt.secret}")
	private String jwtSecret;

	@Test
	public void adminLoginIssuesHs512TokenWithStringAudience() {
		String token = adminToken();
		String[] parts = token.split("\\.");
		assertEquals(3, parts.length);
		assertEquals("{\"alg\":\"HS512\"}", decode(parts[0]));
		String payload = decode(parts[1]);
		assertTrue(payload, payload.contains("\"sub\":\"" + ADMIN + "\""));
		assertTrue(payload, payload.contains("\"aud\":\"api\""));
		assertEquals(HttpStatus.OK, get(ADMIN_PROFILE, token).getStatusCode());
	}

	@Test
	public void independentlySignedHs512TokenWithConfiguredSecretIsAccepted() throws Exception {
		String token = hs512(Base64.getDecoder().decode(jwtSecret.trim()), claims(ADMIN, 3600));
		ResponseEntity<Map> response = get(ADMIN_PROFILE, token);
		assertEquals(HttpStatus.OK, response.getStatusCode());
		assertEquals(ADMIN, response.getBody().get("userName"));
	}

	@Test
	public void tokenSignedWithAnotherKeyIsNotAccepted() throws Exception {
		byte[] otherKey = new byte[64];
		new SecureRandom().nextBytes(otherKey);
		assertNotAccepted(get(ADMIN_PROFILE, hs512(otherKey, claims(ADMIN, 3600))));
	}

	@Test
	public void expiredTokenIsNotAccepted() throws Exception {
		assertNotAccepted(get(ADMIN_PROFILE, hs512(Base64.getDecoder().decode(jwtSecret.trim()), claims(ADMIN, -3600))));
	}

	@Test
	public void unsignedTokenIsNotAccepted() {
		String unsigned = B64URL.encodeToString("{\"alg\":\"none\"}".getBytes(StandardCharsets.UTF_8)) + "."
				+ B64URL.encodeToString(claims(ADMIN, 3600).getBytes(StandardCharsets.UTF_8)) + ".";
		assertNotAccepted(get(ADMIN_PROFILE, unsigned));
	}

	@Test
	public void adminTokenIsRejectedOnCustomerApi() {
		assertEquals(HttpStatus.UNAUTHORIZED, get("/api/v1/auth/customer/profile", adminToken()).getStatusCode());
	}

	@Test
	public void refreshIssuesWorkingTokenWithStringAudience() {
		ResponseEntity<Map> refresh = get("/api/v1/auth/refresh", adminToken());
		assertEquals(HttpStatus.OK, refresh.getStatusCode());
		String refreshed = (String) refresh.getBody().get("token");
		assertNotNull(refreshed);
		String payload = decode(refreshed.split("\\.")[1]);
		assertTrue(payload, payload.contains("\"aud\":\"api\""));
		assertTrue(payload, payload.contains("\"sub\":\"" + ADMIN + "\""));
		assertEquals(HttpStatus.OK, get(ADMIN_PROFILE, refreshed).getStatusCode());
	}

	private String adminToken() {
		ResponseEntity<AuthenticationResponse> response = testRestTemplate.postForEntity("/api/v1/private/login",
				new AuthenticationRequest(ADMIN, "password"), AuthenticationResponse.class);
		assertEquals(HttpStatus.OK, response.getStatusCode());
		return response.getBody().getToken();
	}

	private ResponseEntity<Map> get(String path, String token) {
		HttpHeaders headers = new HttpHeaders();
		headers.setContentType(MediaType.APPLICATION_JSON);
		headers.add("Authorization", "Bearer " + token);
		return testRestTemplate.exchange(path, HttpMethod.GET, new HttpEntity<>(headers), Map.class);
	}

	private static void assertNotAccepted(ResponseEntity<?> response) {
		assertFalse("expected rejection but got " + response.getStatusCode(),
				response.getStatusCode().is2xxSuccessful());
	}

	private static String claims(String subject, long expiresInSeconds) {
		long now = System.currentTimeMillis() / 1000;
		return "{\"sub\":\"" + subject + "\",\"aud\":\"api\",\"iat\":" + now + ",\"exp\":" + (now + expiresInSeconds) + "}";
	}

	private static String hs512(byte[] key, String payloadJson) throws Exception {
		String signingInput = B64URL.encodeToString("{\"alg\":\"HS512\"}".getBytes(StandardCharsets.UTF_8)) + "."
				+ B64URL.encodeToString(payloadJson.getBytes(StandardCharsets.UTF_8));
		Mac mac = Mac.getInstance("HmacSHA512");
		mac.init(new SecretKeySpec(key, "HmacSHA512"));
		return signingInput + "." + B64URL.encodeToString(mac.doFinal(signingInput.getBytes(StandardCharsets.UTF_8)));
	}

	private static String decode(String part) {
		return new String(Base64.getUrlDecoder().decode(part), StandardCharsets.UTF_8);
	}
}
