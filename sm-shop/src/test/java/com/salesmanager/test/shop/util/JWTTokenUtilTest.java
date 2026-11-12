package com.salesmanager.test.shop.util;

import static org.junit.jupiter.api.Assertions.*;

import java.nio.charset.StandardCharsets;
import java.util.Collections;
import java.util.Date;
import java.util.Properties;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import com.salesmanager.shop.store.security.JWTTokenUtil;
import com.salesmanager.shop.store.security.user.JWTUser;

import io.jsonwebtoken.ExpiredJwtException;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.io.Decoders;
import io.jsonwebtoken.security.Keys;

class JWTTokenUtilTest {
    private JWTTokenUtil util;
    private String secret;
    private final JWTUser user = new JWTUser(1L, "admin@shopizer.com", "Admin", "User",
            "admin@shopizer.com", "password", Collections.emptyList(), true, null);

    @BeforeEach
    void setUp() throws Exception {
        Properties properties = new Properties();
        try (java.io.InputStream in = getClass().getResourceAsStream("/authentication.properties")) {
            properties.load(in);
        }
        secret = properties.getProperty("jwt.secret");
        util = new JWTTokenUtil();
        ReflectionTestUtils.setField(util, "secret", secret);
        ReflectionTestUtils.setField(util, "expiration", 3600L);
        util.initializeKey();
    }

    @Test
    void generatesValidHs512TokenAndRefreshesIt() {
        String token = util.generateToken(user);
        assertTrue(util.validateToken(token, user));
        assertEquals("api", util.getAudienceFromToken(token));
        String payload = new String(Decoders.BASE64URL.decode(token.split("\\.")[1]),
                StandardCharsets.UTF_8);
        assertTrue(payload.contains("\"aud\":\"api\""), "Audience must remain a JSON string");
        assertEquals("HS512", Jwts.parser().verifyWith(Keys.hmacShaKeyFor(Decoders.BASE64.decode(secret)))
                .build().parseSignedClaims(token).getHeader().getAlgorithm());
        assertTrue(util.validateToken(util.refreshToken(token), user));
        JWTUser other = new JWTUser(2L, "other", "", "", "", "", Collections.emptyList(), true, null);
        assertFalse(util.validateToken(token, other));
    }

    @Test
    void returnsNullWhenAudienceIsAbsent() {
        String token = Jwts.builder().subject(user.getUsername())
                .signWith(Keys.hmacShaKeyFor(Decoders.BASE64.decode(secret)), Jwts.SIG.HS512)
                .compact();
        assertNull(util.getAudienceFromToken(token));
    }

    @Test
    void rejectsExpiredToken() {
        ReflectionTestUtils.setField(util, "expiration", -3600L);
        assertThrows(ExpiredJwtException.class, () -> util.validateToken(util.generateToken(user), user));
    }

    @Test
    void rejectsTamperedAndEmptyTokens() {
        String token = util.generateToken(user);
        String[] parts = token.split("\\.");
        String payload = java.util.Base64.getUrlEncoder().withoutPadding().encodeToString(
                "{\"sub\":\"attacker\"}".getBytes(StandardCharsets.UTF_8));
        assertThrows(JwtException.class, () -> util.validateToken(parts[0] + "." + payload + "." + parts[2], user));
        String signature = (parts[2].startsWith("A") ? "B" : "A") + parts[2].substring(1);
        assertThrows(JwtException.class, () -> util.validateToken(parts[0] + "." + parts[1] + "." + signature, user));
        assertThrows(IllegalArgumentException.class, () -> util.validateToken("", user));
    }

    @Test
    void validatesLegacyFixtureWithIdenticalClaims() throws Exception {
        String token;
        try (java.io.InputStream in = getClass().getResourceAsStream("/legacy-jjwt-0.8.token")) {
            token = new String(in.readAllBytes(), StandardCharsets.UTF_8).trim();
        }
        assertTrue(util.validateToken(token, user));
        assertEquals(user.getUsername(), util.getUsernameFromToken(token));
        assertEquals("api", util.getAudienceFromToken(token));
        assertEquals(new Date(1767225600000L), util.getIssuedAtDateFromToken(token));
        assertEquals(new Date(4070908800000L), util.getExpirationDateFromToken(token));
    }

    @Test
    void rejectsWeakEmptyAndMalformedSecretsAtInitialization() {
        for (String invalid : new String[] {"aSecret", "", "%%%"}) {
            ReflectionTestUtils.setField(util, "secret", invalid);
            IllegalStateException error = assertThrows(IllegalStateException.class, util::initializeKey);
            assertTrue(error.getMessage().contains("64 bytes"));
        }
    }
}
