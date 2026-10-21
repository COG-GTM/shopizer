package com.salesmanager.shop.store.security;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import java.util.Collections;
import java.util.Date;

import org.junit.Test;
import org.springframework.test.util.ReflectionTestUtils;

import com.salesmanager.shop.store.security.user.JWTUser;

import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.SignatureAlgorithm;

public class JWTTokenUtilTokenTest {

	private static final String SECRET =
			"MDEyMzQ1Njc4OWFiY2RlZjAxMjM0NTY3ODlhYmNkZWYwMTIzNDU2Nzg5YWJjZGVmMDEyMzQ1Njc4OWFiY2RlZg==";
	private static final String USERNAME = "jwt-user@example.com";

	@Test
	public void generatedTokenRoundTripsAndValidates() {
		JWTTokenUtil tokenUtil = tokenUtil(SECRET);
		JWTUser user = jwtUser(USERNAME);

		String token = tokenUtil.generateToken(user);

		assertEquals(USERNAME, tokenUtil.getUsernameFromToken(token));
		assertTrue(tokenUtil.validateToken(token, user));
	}

	@Test
	public void rejectsTokenSignedWithOldDefaultSecret() {
		assertInvalidSignature(tokenWithSecret("aSecret", USERNAME));
	}

	@Test
	public void rejectsTokenSignedWithDifferentStrongSecret() {
		String otherSecret = "c".repeat(64);
		assertInvalidSignature(tokenWithSecret(otherSecret, USERNAME));
	}

	private void assertInvalidSignature(String token) {
		JWTTokenUtil tokenUtil = tokenUtil(SECRET);
		try {
			tokenUtil.getUsernameFromToken(token);
			fail("Expected invalid JWT signature to be rejected");
		} catch (JwtException expected) {
			assertTrue(expected.getMessage() != null);
		}
	}

	private String tokenWithSecret(String secret, String username) {
		Date now = new Date();
		return Jwts.builder()
				.setSubject(username)
				.setAudience("api")
				.setIssuedAt(now)
				.setExpiration(new Date(now.getTime() + 86400000L))
				.signWith(SignatureAlgorithm.HS512, secret)
				.compact();
	}

	private JWTTokenUtil tokenUtil(String secret) {
		JWTTokenUtil tokenUtil = new JWTTokenUtil();
		ReflectionTestUtils.setField(tokenUtil, "secret", secret);
		ReflectionTestUtils.setField(tokenUtil, "expiration", 604800L);
		return tokenUtil;
	}

	private JWTUser jwtUser(String username) {
		return new JWTUser(1L, username, "Test", "User", username, "password",
				Collections.emptyList(), true, null);
	}
}
