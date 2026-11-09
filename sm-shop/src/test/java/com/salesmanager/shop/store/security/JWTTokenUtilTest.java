package com.salesmanager.shop.store.security;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.charset.StandardCharsets;
import java.util.Collections;
import java.util.Date;

import javax.crypto.SecretKey;

import org.junit.jupiter.api.Test;

import com.salesmanager.shop.store.security.user.JWTUser;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.ExpiredJwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import io.jsonwebtoken.security.SignatureException;

class JWTTokenUtilTest {

	private static final String SECRET = "unit-test-jwt-signing-key-that-is-definitely-longer-than-64-bytes-0123456789";
	private static final String OTHER_SECRET = "another-unit-test-jwt-signing-key-also-longer-than-sixty-four-bytes-9876543210";

	private final JWTTokenUtil util = new JWTTokenUtil(SECRET, 3600L);

	private static JWTUser user(String username, Date lastPasswordReset) {
		return new JWTUser(1L, username, "first", "last", username, "pwd", Collections.emptyList(), true,
				lastPasswordReset);
	}

	@Test
	void generatedTokenIsHs512AndRoundTrips() {
		String token = util.generateToken(user("admin@shopizer.com", null));

		assertEquals("admin@shopizer.com", util.getUsernameFromToken(token));
		assertEquals(JWTTokenUtil.AUDIENCE_API, util.getAudienceFromToken(token));
		assertTrue(util.getExpirationDateFromToken(token).after(util.getIssuedAtDateFromToken(token)));
		assertEquals("HS512", Jwts.parser().verifyWith(key(SECRET)).build().parseSignedClaims(token).getHeader()
				.getAlgorithm());
		assertTrue(util.validateToken(token, user("admin@shopizer.com", null)));
		assertFalse(util.validateToken(token, user("someone@else.com", null)));
	}

	@Test
	void tokenIssuedBeforePasswordResetIsRejected() {
		String token = util.generateToken(user("admin@shopizer.com", null));
		Date future = new Date(System.currentTimeMillis() + 60_000);

		assertFalse(util.validateToken(token, user("admin@shopizer.com", future)));
		assertFalse(util.canTokenBeRefreshed(token, future));
		assertTrue(util.canTokenBeRefreshed(token, null));
	}

	@Test
	void tokenSignedWithAnotherKeyIsRejected() {
		String forged = new JWTTokenUtil(OTHER_SECRET, 3600L).generateToken(user("admin@shopizer.com", null));

		assertThrows(SignatureException.class, () -> util.getUsernameFromToken(forged));
	}

	@Test
	void tamperedTokenIsRejected() {
		String token = util.generateToken(user("admin@shopizer.com", null));
		String[] parts = token.split("\\.");
		String otherPayload = new JWTTokenUtil(SECRET, 3600L).generateToken(user("root@shopizer.com", null))
				.split("\\.")[1];
		String tampered = parts[0] + "." + otherPayload + "." + parts[2];

		assertThrows(SignatureException.class, () -> util.getUsernameFromToken(tampered));
	}

	@Test
	void expiredTokenIsRejected() {
		String expired = Jwts.builder().subject("admin@shopizer.com")
				.issuedAt(new Date(System.currentTimeMillis() - 10_000))
				.expiration(new Date(System.currentTimeMillis() - 5_000))
				.signWith(key(SECRET), Jwts.SIG.HS512).compact();

		assertThrows(ExpiredJwtException.class, () -> util.getUsernameFromToken(expired));
	}

	@Test
	void refreshKeepsClaimsAndExtendsExpiration() throws InterruptedException {
		JWTTokenUtil shortLived = new JWTTokenUtil(SECRET, 60L);
		String token = shortLived.generateToken(user("admin@shopizer.com", null));
		Thread.sleep(1100);

		String refreshed = util.refreshToken(token);

		assertNotEquals(token, refreshed);
		Claims claims = Jwts.parser().verifyWith(key(SECRET)).build().parseSignedClaims(refreshed).getPayload();
		assertEquals("admin@shopizer.com", claims.getSubject());
		assertEquals(Collections.singleton(JWTTokenUtil.AUDIENCE_API), claims.getAudience());
		assertTrue(claims.getExpiration().after(shortLived.getExpirationDateFromToken(token)));
		assertTrue(util.canTokenBeRefreshedWithGrace(refreshed, null));
	}

	@Test
	void secretShorterThanHs512KeySizeFailsFast() {
		IllegalStateException e = assertThrows(IllegalStateException.class, () -> new JWTTokenUtil("aSecret", 3600L));
		assertTrue(e.getMessage().contains("at least 64 bytes"));
		assertThrows(IllegalStateException.class, () -> new JWTTokenUtil("", 3600L));
	}

	private static SecretKey key(String secret) {
		return Keys.hmacShaKeyFor(secret.getBytes(StandardCharsets.UTF_8));
	}
}
