package com.salesmanager.shop.store.security;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertThrows;
import static org.junit.Assert.assertTrue;

import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.Collections;
import java.util.Date;

import org.junit.Test;
import org.springframework.test.util.ReflectionTestUtils;

import com.salesmanager.shop.store.security.user.JWTUser;

import io.jsonwebtoken.ExpiredJwtException;
import io.jsonwebtoken.JwtException;

/**
 * Claim accessors and refresh policy of JWTTokenUtil across the JJWT 0.8 -> 0.12 upgrade.
 * Only uses API that exists before and after AB-538 so the same test runs on both.
 */
public class JWTTokenUtilClaimsAndRefreshPolicyTest {

	/** Default jwt.secret from sm-core authentication.properties (AB-538). */
	private static final String SECRET =
			"c2hvcGl6ZXItbG9jYWwtZGV2ZWxvcG1lbnQtand0LXNpZ25pbmcta2V5LUhTNTEyLWNoYW5nZS1tZS1pbi1wcm9kdWN0aW9uIQ==";

	/** Issued by JJWT 0.8.0 with SECRET: sub=admin@shopizer.com, aud=api, iat=1792540800, exp=4102444800. */
	private static final String LEGACY_JJWT_08_TOKEN =
			"eyJhbGciOiJIUzUxMiJ9.eyJzdWIiOiJhZG1pbkBzaG9waXplci5jb20iLCJhdWQiOiJhcGkiLCJleHAiOjQxMDI0NDQ4MDAsImlhdCI6MTc5MjU0MDgwMH0"
			+ ".t33vAxukoIoJKOOI0mpAgdVpmql147njmzxMdDPXFXozJOkAIKZBKCdqmTONPld95GFB1DTnhHtEOE5Ahl3yYQ";

	private static final String USERNAME = "admin@shopizer.com";

	private static JWTTokenUtil util(long expirationSeconds) {
		JWTTokenUtil u = new JWTTokenUtil();
		ReflectionTestUtils.setField(u, "secret", SECRET);
		ReflectionTestUtils.setField(u, "expiration", expirationSeconds);
		return u;
	}

	private static JWTUser user(Date lastPasswordReset) {
		return new JWTUser(1L, USERNAME, "Admin", "User", USERNAME, "pwd", Collections.emptyList(), true,
				lastPasswordReset);
	}

	private static String payload(String token) {
		return new String(Base64.getUrlDecoder().decode(token.split("\\.")[1]), StandardCharsets.UTF_8);
	}

	@Test
	public void accessorsReadLegacyTokenClaims() {
		JWTTokenUtil u = util(604800L);
		assertEquals(USERNAME, u.getUsernameFromToken(LEGACY_JJWT_08_TOKEN));
		assertEquals("api", u.getAudienceFromToken(LEGACY_JJWT_08_TOKEN));
		assertEquals(1792540800000L, u.getIssuedAtDateFromToken(LEGACY_JJWT_08_TOKEN).getTime());
		assertEquals(4102444800000L, u.getExpirationDateFromToken(LEGACY_JJWT_08_TOKEN).getTime());
	}

	@Test
	public void generatedTokenLifetimeFollowsConfiguredExpiration() {
		JWTTokenUtil u = util(3600L);
		String token = u.generateToken(user(null));
		long lifetime = u.getExpirationDateFromToken(token).getTime() - u.getIssuedAtDateFromToken(token).getTime();
		assertEquals(3600_000L, lifetime);
	}

	@Test
	public void canTokenBeRefreshedHonoursPasswordReset() {
		JWTTokenUtil u = util(604800L);
		String token = u.generateToken(user(null));
		Date issuedAt = u.getIssuedAtDateFromToken(token);
		assertTrue(u.canTokenBeRefreshed(token, null));
		assertTrue(u.canTokenBeRefreshed(token, new Date(issuedAt.getTime() - 60_000L)));
		assertFalse(u.canTokenBeRefreshed(token, new Date(issuedAt.getTime() + 60_000L)));
	}

	@Test
	public void expiredTokenCannotBeRefreshed() {
		JWTTokenUtil u = util(-60L);
		String token = u.generateToken(user(null));
		assertThrows(ExpiredJwtException.class, () -> u.canTokenBeRefreshed(token, null));
		assertThrows(ExpiredJwtException.class, () -> u.refreshToken(token));
	}

	@Test
	public void unsignedTokenIsRejected() {
		String header = Base64.getUrlEncoder().withoutPadding()
				.encodeToString("{\"alg\":\"none\"}".getBytes(StandardCharsets.UTF_8));
		String unsigned = header + "." + LEGACY_JJWT_08_TOKEN.split("\\.")[1] + ".";
		assertThrows(JwtException.class, () -> util(604800L).validateToken(unsigned, user(null)));
	}

	@Test
	public void refreshedLegacyTokenValidatesAndKeepsStringAudience() {
		JWTTokenUtil u = util(604800L);
		String refreshed = u.refreshToken(LEGACY_JJWT_08_TOKEN);
		assertTrue(u.validateToken(refreshed, user(null)));
		assertEquals(USERNAME, u.getUsernameFromToken(refreshed));
		assertTrue(payload(refreshed).contains("\"aud\":\"api\""));
		assertTrue(u.getIssuedAtDateFromToken(refreshed).getTime() > 1792540800000L);
	}
}
