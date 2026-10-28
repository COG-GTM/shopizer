package com.salesmanager.shop.store.security;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.security.SecureRandom;
import java.util.Base64;
import java.util.Collections;
import java.util.Date;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import com.salesmanager.shop.store.security.user.JWTUser;

import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.SignatureAlgorithm;
import io.jsonwebtoken.SignatureException;

class JWTTokenUtilTest {
    private static final String USERNAME = "admin@shopizer.com";
    private final String validSecret = Base64.getEncoder().encodeToString(new byte[64]);
    private JWTTokenUtil jwtTokenUtil;

    @BeforeEach
    void setUp() {
        jwtTokenUtil = new JWTTokenUtil();
        setSecret(validSecret);
        ReflectionTestUtils.setField(jwtTokenUtil, "expiration", 3600L);
    }

    @Test
    void rejectsMissingPlaceholderAndWeakSecrets() {
        SecureRandom random = new SecureRandom();
        byte[] bytes32 = new byte[32];
        byte[] bytes63 = new byte[63];
        random.nextBytes(bytes32);
        random.nextBytes(bytes63);
        String key32 = Base64.getEncoder().encodeToString(bytes32);
        String key63 = Base64.getEncoder().encodeToString(bytes63);

        assertThrows(IllegalStateException.class, () -> validate(null));
        assertThrows(IllegalStateException.class, () -> validate(""));
        assertThrows(IllegalStateException.class, () -> validate("   "));
        assertThrows(IllegalStateException.class, () -> validate("${JWT_SECRET}"));
        assertThrows(IllegalStateException.class, () -> validate("aSecret"));
        assertThrows(IllegalStateException.class, () -> validate("not*base64!"));
        assertThrows(IllegalStateException.class, () -> validate(key32));
        assertThrows(IllegalStateException.class, () -> validate(key63));
    }

    @Test
    void acceptsAtLeast64DecodedBytesAndSurroundingWhitespace() {
        assertDoesNotThrow(() -> validate(validSecret));
        assertDoesNotThrow(() -> validate("  " + validSecret + "  "));
    }

    @Test
    void generatesValidatesAndRefreshesTokens() {
        JWTUser user = new JWTUser(1L, USERNAME, "Admin", "Shopizer", USERNAME, "password",
                Collections.emptyList(), true, null);

        String token = jwtTokenUtil.generateToken(user);

        assertEquals(USERNAME, jwtTokenUtil.getUsernameFromToken(token));
        assertEquals("api", jwtTokenUtil.getAudienceFromToken(token));
        assertTrue(jwtTokenUtil.validateToken(token, user));
        assertEquals(USERNAME, jwtTokenUtil.getUsernameFromToken(jwtTokenUtil.refreshToken(token)));
    }

    @Test
    void rejectsTokensSignedWithShortOrDifferentSecrets() {
        Date now = new Date();
        String shortSecretToken = Jwts.builder()
                .setSubject(USERNAME)
                .setAudience("api")
                .setIssuedAt(now)
                .setExpiration(new Date(now.getTime() + 3_600_000))
                .signWith(SignatureAlgorithm.HS512, "aSecret")
                .compact();
        byte[] randomKey = new byte[64];
        new SecureRandom().nextBytes(randomKey);
        String otherSecret = Base64.getEncoder().encodeToString(randomKey);
        String otherSecretToken = Jwts.builder()
                .setSubject(USERNAME)
                .setAudience("api")
                .setIssuedAt(now)
                .setExpiration(new Date(now.getTime() + 3_600_000))
                .signWith(SignatureAlgorithm.HS512, otherSecret)
                .compact();

        assertThrows(SignatureException.class, () -> jwtTokenUtil.getUsernameFromToken(shortSecretToken));
        assertThrows(SignatureException.class, () -> jwtTokenUtil.getUsernameFromToken(otherSecretToken));
    }

    private void validate(String secret) {
        setSecret(secret);
        jwtTokenUtil.validateSecret();
    }

    private void setSecret(String secret) {
        ReflectionTestUtils.setField(jwtTokenUtil, "secret", secret);
    }
}
