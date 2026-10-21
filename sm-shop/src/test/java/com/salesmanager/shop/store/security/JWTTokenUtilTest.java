package com.salesmanager.shop.store.security;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotEquals;
import static org.junit.Assert.assertThrows;
import static org.junit.Assert.assertTrue;

import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.Collections;
import java.util.Date;

import org.junit.Before;
import org.junit.Test;
import org.springframework.test.util.ReflectionTestUtils;

import com.salesmanager.shop.store.security.user.JWTUser;

import io.jsonwebtoken.ExpiredJwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import io.jsonwebtoken.security.SignatureException;

public class JWTTokenUtilTest {

	/** Default jwt.secret from sm-core authentication.properties. */
	private static final String SECRET =
			"c2hvcGl6ZXItbG9jYWwtZGV2ZWxvcG1lbnQtand0LXNpZ25pbmcta2V5LUhTNTEyLWNoYW5nZS1tZS1pbi1wcm9kdWN0aW9uIQ==";

	/**
	 * Issued by the pre-AB-538 implementation (JJWT 0.8.0,
	 * {@code signWith(SignatureAlgorithm.HS512, SECRET)}) for admin@shopizer.com,
	 * aud=api, iat=2026-10-21T00:00:00Z, exp=2100-01-01T00:00:00Z.
	 */
	private static final String LEGACY_JJWT_08_TOKEN =
			"eyJhbGciOiJIUzUxMiJ9.eyJzdWIiOiJhZG1pbkBzaG9waXplci5jb20iLCJhdWQiOiJhcGkiLCJleHAiOjQxMDI0NDQ4MDAsImlhdCI6MTc5MjU0MDgwMH0"
			+ ".t33vAxukoIoJKOOI0mpAgdVpmql147njmzxMdDPXFXozJOkAIKZBKCdqmTONPld95GFB1DTnhHtEOE5Ahl3yYQ";

	private static final String USERNAME = "admin@shopizer.com";

	private JWTTokenUtil util;

	@Before
	public void setUp() {
		util = newUtil(SECRET, 604800L);
	}

	private static JWTTokenUtil newUtil(String secret, long expirationSeconds) {
		JWTTokenUtil u = new JWTTokenUtil();
		ReflectionTestUtils.setField(u, "secret", secret);
		ReflectionTestUtils.setField(u, "expiration", expirationSeconds);
		u.init();
		return u;
	}

	private static JWTUser user(String username, Date lastPasswordReset) {
		return new JWTUser(1L, username, "Admin", "User", username, "pwd", Collections.emptyList(), true,
				lastPasswordReset);
	}

	private static String decodePart(String token, int index) {
		return new String(Base64.getUrlDecoder().decode(token.split("\\.")[index]), StandardCharsets.UTF_8);
	}

	@Test
	public void generateThenValidate() {
		String token = util.generateToken(user(USERNAME, null));

		assertTrue(util.validateToken(token, user(USERNAME, null)));
		assertEquals(USERNAME, util.getUsernameFromToken(token));
		assertEquals(JWTTokenUtil.AUDIENCE_API, util.getAudienceFromToken(token));
		assertTrue(util.getExpirationDateFromToken(token).after(new Date()));
	}

	@Test
	public void generatedTokenKeepsLegacyHeaderAndClaimShapes() {
		String token = util.generateToken(user(USERNAME, null));

		assertEquals("{\"alg\":\"HS512\"}", decodePart(token, 0));
		String payload = decodePart(token, 1);
		assertTrue(payload, payload.contains("\"sub\":\"" + USERNAME + "\""));
		assertTrue("aud must stay a single string, not an array: " + payload, payload.contains("\"aud\":\"api\""));
		assertTrue(payload, payload.contains("\"iat\":"));
		assertTrue(payload, payload.contains("\"exp\":"));
	}

	@Test
	public void validateRejectsOtherUser() {
		String token = util.generateToken(user(USERNAME, null));

		assertFalse(util.validateToken(token, user("someone@else.com", null)));
	}

	@Test
	public void validateRejectsTokenIssuedBeforePasswordReset() {
		String token = util.generateToken(user(USERNAME, null));
		Date resetAfterIssue = new Date(System.currentTimeMillis() + 60_000);

		assertFalse(util.validateToken(token, user(USERNAME, resetAfterIssue)));
	}

	@Test
	public void expiredTokenIsRejected() {
		String token = newUtil(SECRET, -60L).generateToken(user(USERNAME, null));

		assertThrows(ExpiredJwtException.class, () -> util.validateToken(token, user(USERNAME, null)));
		assertThrows(ExpiredJwtException.class, () -> util.getUsernameFromToken(token));
	}

	@Test
	public void tamperedPayloadIsRejected() {
		String token = util.generateToken(user(USERNAME, null));
		String[] parts = token.split("\\.");
		String forgedPayload = Base64.getUrlEncoder().withoutPadding().encodeToString(
				decodePart(token, 1).replace(USERNAME, "attacker@shopizer.com").getBytes(StandardCharsets.UTF_8));
		String tampered = parts[0] + "." + forgedPayload + "." + parts[2];

		assertThrows(SignatureException.class, () -> util.validateToken(tampered, user("attacker@shopizer.com", null)));
	}

	@Test
	public void tokenSignedWithAnotherKeyIsRejected() {
		String foreign = Jwts.builder().subject(USERNAME).audience().single("api").issuedAt(new Date())
				.expiration(new Date(System.currentTimeMillis() + 60_000))
				.signWith(Keys.hmacShaKeyFor(new byte[64]), Jwts.SIG.HS512).compact();

		assertThrows(SignatureException.class, () -> util.getUsernameFromToken(foreign));
	}

	@Test
	public void tokenIssuedByJjwt08StillValidates() {
		assertTrue(util.validateToken(LEGACY_JJWT_08_TOKEN, user(USERNAME, null)));
		assertEquals(USERNAME, util.getUsernameFromToken(LEGACY_JJWT_08_TOKEN));
		assertEquals(JWTTokenUtil.AUDIENCE_API, util.getAudienceFromToken(LEGACY_JJWT_08_TOKEN));
		assertEquals(1792540800000L, util.getIssuedAtDateFromToken(LEGACY_JJWT_08_TOKEN).getTime());
		assertEquals(4102444800000L, util.getExpirationDateFromToken(LEGACY_JJWT_08_TOKEN).getTime());
	}

	@Test
	public void refreshKeepsSubjectAndAudienceAndExtendsExpiry() {
		String refreshed = util.refreshToken(LEGACY_JJWT_08_TOKEN);

		assertNotEquals(LEGACY_JJWT_08_TOKEN, refreshed);
		assertEquals(USERNAME, util.getUsernameFromToken(refreshed));
		assertEquals(JWTTokenUtil.AUDIENCE_API, util.getAudienceFromToken(refreshed));
		assertTrue(decodePart(refreshed, 1).contains("\"aud\":\"api\""));
		assertTrue(util.getIssuedAtDateFromToken(refreshed).getTime() > 1792540800000L);
		assertTrue(util.validateToken(refreshed, user(USERNAME, null)));
	}

	@Test
	public void secretShorterThan512BitsIsRejectedAtStartup() {
		// The previous default "aSecret" Base64-decodes to only 3 bytes (24 bits).
		IllegalStateException e = assertThrows(IllegalStateException.class, () -> newUtil("aSecret", 60L));
		assertTrue(e.getMessage(), e.getMessage().contains("512"));
		assertThrows(IllegalStateException.class,
				() -> newUtil(Base64.getEncoder().encodeToString(new byte[63]), 60L));
	}

	@Test
	public void missingSecretIsRejected() {
		assertThrows(IllegalStateException.class, () -> newUtil("", 60L));
	}
}
