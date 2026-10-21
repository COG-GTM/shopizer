package com.salesmanager.test.shop.security;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import java.util.Collections;
import java.util.Date;
import java.util.HashMap;
import java.util.Map;

import org.junit.Before;
import org.junit.Test;
import org.springframework.security.core.userdetails.User;
import org.springframework.test.util.ReflectionTestUtils;

import com.salesmanager.shop.store.security.JWTTokenUtil;
import com.salesmanager.shop.store.security.user.JWTUser;

import io.jsonwebtoken.ExpiredJwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.SignatureAlgorithm;
import io.jsonwebtoken.SignatureException;

public class JWTTokenUtilRealmBindingTest {

    private static final String SECRET = "test-secret";
    private static final long EXPIRATION_SECONDS = 3600L;
    private static final String USERNAME = "admin@example.com";

    private JWTTokenUtil tokenUtil;

    @Before
    public void setUp() {
        tokenUtil = new JWTTokenUtil();
        ReflectionTestUtils.setField(tokenUtil, "secret", SECRET);
        ReflectionTestUtils.setField(tokenUtil, "expiration", EXPIRATION_SECONDS);
    }

    @Test
    public void userIdClaimRoundTripsForSmallAndLargeIds() {
        for (Long id : new Long[] { 1L, 5_000_000_000L }) {
            JWTUser user = user(id, USERNAME, null);
            String token = tokenUtil.generateToken(user, JWTTokenUtil.TOKEN_TYPE_ADMIN);

            assertEquals(id, tokenUtil.getUserIdFromToken(token));
            assertTrue(tokenUtil.isTokenBoundTo(token, user, JWTTokenUtil.TOKEN_TYPE_ADMIN));
        }
    }

    @Test
    public void blankAndNullTokenTypesAreRejected() {
        JWTUser user = user(1L, USERNAME, null);

        assertBlankTokenTypeRejected(user);
        assertNullTokenTypeRejected(user);
    }

    @Test
    public void userWithoutIdHasNoIdClaimAndIsNotBoundToEitherRealm() {
        JWTUser user = user(null, USERNAME, null);
        String token = tokenUtil.generateToken(user, JWTTokenUtil.TOKEN_TYPE_ADMIN);

        assertNull(tokenUtil.getUserIdFromToken(token));
        assertFalse(tokenUtil.isTokenBoundTo(token, user, JWTTokenUtil.TOKEN_TYPE_ADMIN));
        assertFalse(tokenUtil.isTokenBoundTo(token, user, JWTTokenUtil.TOKEN_TYPE_CUSTOMER));
    }

    @Test
    public void nonJwtUserCanGenerateTokenWithoutUserIdClaim() {
        User user = new User(USERNAME, "password", Collections.emptyList());
        String token = tokenUtil.generateToken(user, JWTTokenUtil.TOKEN_TYPE_ADMIN);

        assertEquals(USERNAME, tokenUtil.getUsernameFromToken(token));
        assertNull(tokenUtil.getUserIdFromToken(token));
    }

    @Test
    public void tokenWithDifferentSubjectIsNotBoundToUser() {
        JWTUser user = user(1L, USERNAME, null);
        String token = token(JWTTokenUtil.TOKEN_TYPE_ADMIN, 1L, "another@example.com",
                SECRET, new Date(), new Date(System.currentTimeMillis() + 60_000L));

        assertFalse(tokenUtil.isTokenBoundTo(token, user, JWTTokenUtil.TOKEN_TYPE_ADMIN));
    }

    @Test
    public void stringUserIdClaimIsNotAcceptedAsNumericId() {
        JWTUser user = user(1L, USERNAME, null);
        String token = token(JWTTokenUtil.TOKEN_TYPE_ADMIN, "1", USERNAME,
                SECRET, new Date(), new Date(System.currentTimeMillis() + 60_000L));

        assertNull(tokenUtil.getUserIdFromToken(token));
        assertFalse(tokenUtil.isTokenBoundTo(token, user, JWTTokenUtil.TOKEN_TYPE_ADMIN));
    }

    @Test
    public void tokenSignedWithDifferentSecretThrowsSignatureException() {
        String token = token(JWTTokenUtil.TOKEN_TYPE_ADMIN, 1L, USERNAME,
                "different-secret", new Date(), new Date(System.currentTimeMillis() + 60_000L));

        try {
            tokenUtil.validateToken(token, user(1L, USERNAME, null), JWTTokenUtil.TOKEN_TYPE_ADMIN);
            fail("Expected a signature exception");
        } catch (SignatureException expected) {
            // expected
        }
    }

    @Test
    public void expiredTokenCannotBeValidatedOrRefreshedWithGrace() {
        JWTUser user = user(1L, USERNAME, null);
        Date issuedAt = new Date(System.currentTimeMillis() - 120_000L);
        Date expiration = new Date(System.currentTimeMillis() - 60_000L);
        String token = token(JWTTokenUtil.TOKEN_TYPE_ADMIN, 1L, USERNAME, SECRET, issuedAt, expiration);

        assertThrowsExpired(() -> tokenUtil.validateToken(token, user, JWTTokenUtil.TOKEN_TYPE_ADMIN));
        assertThrowsExpired(() -> tokenUtil.canTokenBeRefreshedWithGrace(
                token, user, JWTTokenUtil.TOKEN_TYPE_ADMIN));
    }

    @Test
    public void tokenCreatedBeforePasswordResetIsBoundButInvalidAndNotRefreshable() {
        Date passwordReset = new Date(System.currentTimeMillis() + 60_000L);
        JWTUser user = user(1L, USERNAME, passwordReset);
        String token = tokenUtil.generateToken(user, JWTTokenUtil.TOKEN_TYPE_ADMIN);

        assertTrue(tokenUtil.isTokenBoundTo(token, user, JWTTokenUtil.TOKEN_TYPE_ADMIN));
        assertFalse(tokenUtil.validateToken(token, user, JWTTokenUtil.TOKEN_TYPE_ADMIN));
        assertFalse(tokenUtil.canTokenBeRefreshed(token, user, JWTTokenUtil.TOKEN_TYPE_ADMIN));
    }

    @Test
    public void customerTokenValidatesAndRefreshPreservesRealmAndUserId() {
        JWTUser user = user(42L, USERNAME, null);
        String token = tokenUtil.generateToken(user, JWTTokenUtil.TOKEN_TYPE_CUSTOMER);

        assertTrue(tokenUtil.validateToken(token, user, JWTTokenUtil.TOKEN_TYPE_CUSTOMER));
        assertTrue(tokenUtil.canTokenBeRefreshed(token, user, JWTTokenUtil.TOKEN_TYPE_CUSTOMER));

        String refreshedToken = tokenUtil.refreshToken(token);
        assertEquals(JWTTokenUtil.TOKEN_TYPE_CUSTOMER, tokenUtil.getTokenTypeFromToken(refreshedToken));
        assertEquals(Long.valueOf(42L), tokenUtil.getUserIdFromToken(refreshedToken));
    }

    private void assertBlankTokenTypeRejected(JWTUser user) {
        try {
            tokenUtil.generateToken(user, "");
            fail("Expected an IllegalArgumentException");
        } catch (IllegalArgumentException expected) {
            // expected
        }
    }

    private void assertNullTokenTypeRejected(JWTUser user) {
        try {
            tokenUtil.generateToken(user, null);
            fail("Expected a NullPointerException");
        } catch (NullPointerException expected) {
            // expected
        }
    }

    private void assertThrowsExpired(Runnable action) {
        try {
            action.run();
            fail("Expected an ExpiredJwtException");
        } catch (ExpiredJwtException expected) {
            // expected
        }
    }

    private JWTUser user(Long id, String username, Date lastPasswordReset) {
        return new JWTUser(id, username, "First", "Last", username, "password",
                Collections.emptyList(), true, lastPasswordReset);
    }

    private String token(String tokenType, Object userId, String subject, String secret,
            Date issuedAt, Date expiration) {
        Map<String, Object> claims = new HashMap<>();
        claims.put(JWTTokenUtil.CLAIM_KEY_TOKEN_TYPE, tokenType);
        claims.put(JWTTokenUtil.CLAIM_KEY_USER_ID, userId);
        return Jwts.builder()
                .setClaims(claims)
                .setSubject(subject)
                .setAudience("api")
                .setIssuedAt(issuedAt)
                .setExpiration(expiration)
                .signWith(SignatureAlgorithm.HS512, secret)
                .compact();
    }
}
