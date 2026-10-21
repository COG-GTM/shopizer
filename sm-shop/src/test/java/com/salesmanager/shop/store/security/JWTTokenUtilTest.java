package com.salesmanager.shop.store.security;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import java.util.Collections;
import java.util.Date;

import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import com.salesmanager.shop.store.security.user.JWTUser;

import io.jsonwebtoken.ExpiredJwtException;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jws;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.SignatureException;

class JWTTokenUtilTest {

	private static final String SECRET = "aSecret";
	private static final long EXPIRATION_SECONDS = 604800L;

	@Test
	void generateTokenWritesNothingToStandardOutput() {
		JWTTokenUtil tokenUtil = tokenUtil(SECRET, EXPIRATION_SECONDS);
		ByteArrayOutputStream output = new ByteArrayOutputStream();
		PrintStream original = System.out;

		try (PrintStream captured = new PrintStream(output, true, StandardCharsets.UTF_8)) {
			System.setOut(captured);
			tokenUtil.generateToken(user("admin@example.com", null));
		} finally {
			System.setOut(original);
		}

		String text = output.toString(StandardCharsets.UTF_8);
		assertFalse(text.contains("doGenerateToken"));
		assertEquals("", text);
	}

	@Test
	void generateTokenContainsExpectedClaimsAndSignatureAlgorithm() {
		JWTTokenUtil tokenUtil = tokenUtil(SECRET, EXPIRATION_SECONDS);
		String token = tokenUtil.generateToken(user("admin@example.com", null));
		Jws<Claims> claims = Jwts.parser().setSigningKey(SECRET).parseClaimsJws(token);

		assertEquals("admin@example.com", claims.getBody().getSubject());
		assertEquals(JWTTokenUtil.AUDIENCE_API, claims.getBody().getAudience());
		assertTrue(claims.getBody().getIssuedAt() != null);
		assertTrue(claims.getBody().getExpiration() != null);
		assertEquals(EXPIRATION_SECONDS,
				(claims.getBody().getExpiration().getTime() - claims.getBody().getIssuedAt().getTime()) / 1000L);
		assertEquals("HS512", claims.getHeader().getAlgorithm());
	}

	@Test
	void validateTokenChecksUsernameExpirationAndPasswordResetDate() {
		JWTTokenUtil tokenUtil = tokenUtil(SECRET, EXPIRATION_SECONDS);
		String token = tokenUtil.generateToken(user("admin@example.com", null));
		Date issuedAt = tokenUtil.getIssuedAtDateFromToken(token);

		assertTrue(tokenUtil.validateToken(token, user("admin@example.com", null)));
		assertFalse(tokenUtil.validateToken(token, user("other@example.com", null)));
		assertFalse(tokenUtil.validateToken(token,
				user("admin@example.com", new Date(issuedAt.getTime() + 3_600_000L))));
	}

	@Test
	void canTokenBeRefreshedRejectsTokensIssuedBeforePasswordReset() {
		JWTTokenUtil tokenUtil = tokenUtil(SECRET, EXPIRATION_SECONDS);
		String token = tokenUtil.generateToken(user("admin@example.com", null));
		Date issuedAt = tokenUtil.getIssuedAtDateFromToken(token);

		assertTrue(tokenUtil.canTokenBeRefreshed(token, null));
		assertFalse(tokenUtil.canTokenBeRefreshed(token,
				new Date(issuedAt.getTime() + 3_600_000L)));
	}

	@Test
	void refreshTokenPreservesSubjectAndAudienceAndRemainsValid() {
		JWTTokenUtil tokenUtil = tokenUtil(SECRET, EXPIRATION_SECONDS);
		JWTUser user = user("admin@example.com", null);
		String token = tokenUtil.generateToken(user);
		Date originalExpiration = tokenUtil.getExpirationDateFromToken(token);

		String refreshed = tokenUtil.refreshToken(token);

		assertEquals("admin@example.com", tokenUtil.getUsernameFromToken(refreshed));
		assertEquals(JWTTokenUtil.AUDIENCE_API, tokenUtil.getAudienceFromToken(refreshed));
		assertTrue(tokenUtil.validateToken(refreshed, user));
		assertTrue(tokenUtil.getExpirationDateFromToken(refreshed).compareTo(originalExpiration) >= 0);
	}

	@Test
	void parsingRejectsInvalidSignatureAndExpiredTokens() {
		JWTTokenUtil tokenUtil = tokenUtil(SECRET, EXPIRATION_SECONDS);
		String otherSecretToken = tokenUtil("anotherSecret", EXPIRATION_SECONDS)
				.generateToken(user("admin@example.com", null));
		assertThrows(SignatureException.class, () -> tokenUtil.getUsernameFromToken(otherSecretToken));

		JWTTokenUtil expiredTokenUtil = tokenUtil(SECRET, -10L);
		String expiredToken = expiredTokenUtil.generateToken(user("admin@example.com", null));
		assertThrows(ExpiredJwtException.class, () -> expiredTokenUtil.getUsernameFromToken(expiredToken));
	}

	private static JWTTokenUtil tokenUtil(String secret, long expiration) {
		JWTTokenUtil tokenUtil = new JWTTokenUtil();
		ReflectionTestUtils.setField(tokenUtil, "secret", secret);
		ReflectionTestUtils.setField(tokenUtil, "expiration", expiration);
		return tokenUtil;
	}

	private static JWTUser user(String username, Date lastPasswordResetDate) {
		return new JWTUser(1L, username, "Admin", "User", username, "password",
				Collections.emptyList(), true, lastPasswordResetDate);
	}
}
