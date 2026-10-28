package com.salesmanager.test.shop.integration.security;

import static org.junit.Assert.assertEquals;

import java.util.Date;

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

import com.salesmanager.shop.application.ShopApplication;
import com.salesmanager.test.shop.common.ServicesTestSupport;

import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.SignatureAlgorithm;

@SpringBootTest(classes = ShopApplication.class, webEnvironment = WebEnvironment.RANDOM_PORT)
@RunWith(SpringRunner.class)
public class ForgedJwtIntegrationTest extends ServicesTestSupport {

	private static final String OLD_DEFAULT_SECRET = "aSecret";

	@Test
	public void issuedAdminTokenIsAccepted() {
		assertEquals(HttpStatus.OK, get("/api/v1/private/users/1", getHeader()).getStatusCode());
	}

	@Test
	public void adminTokenForgedWithOldDefaultSecretIsUnauthorized() {
		assertEquals(HttpStatus.UNAUTHORIZED,
				get("/api/v1/private/users/1", bearer(forge("admin@shopizer.com"))).getStatusCode());
	}

	@Test
	public void customerTokenForgedWithOldDefaultSecretIsUnauthorized() {
		assertEquals(HttpStatus.UNAUTHORIZED,
				get("/api/v1/auth/customer/profile", bearer(forge("test@shopizer.com"))).getStatusCode());
	}

	private ResponseEntity<String> get(String url, HttpHeaders headers) {
		return testRestTemplate.exchange(url, HttpMethod.GET, new HttpEntity<>(headers), String.class);
	}

	private static HttpHeaders bearer(String token) {
		HttpHeaders headers = new HttpHeaders();
		headers.add("Authorization", "Bearer " + token);
		return headers;
	}

	private static String forge(String subject) {
		return Jwts.builder().setSubject(subject).setAudience("api").setIssuedAt(new Date())
				.setExpiration(new Date(System.currentTimeMillis() + 3_600_000L))
				.signWith(SignatureAlgorithm.HS512, OLD_DEFAULT_SECRET).compact();
	}
}
