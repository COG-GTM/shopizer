package com.salesmanager.test.shop.util;

import static org.junit.jupiter.api.Assertions.*;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.salesmanager.shop.store.security.JWTTokenUtil;
import com.salesmanager.shop.store.security.user.JWTUser;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.io.Decoders;
import io.jsonwebtoken.security.Keys;
import java.util.Collections;
import java.util.Date;
import java.util.Properties;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

class JwtBuilderCompatibilityTest {
    private final JWTTokenUtil tokens = new JWTTokenUtil();
    private final JWTUser user = new JWTUser(1L, "admin@shopizer.com", "Admin", "User",
            "admin@shopizer.com", "password", Collections.emptyList(), true, null);
    private javax.crypto.SecretKey key;

    @BeforeEach
    void configure() throws Exception {
        Properties properties = new Properties();
        try (var stream = getClass().getResourceAsStream("/authentication.properties")) {
            properties.load(stream);
        }
        String secret = properties.getProperty("jwt.secret");
        key = Keys.hmacShaKeyFor(Decoders.BASE64.decode(secret));
        ReflectionTestUtils.setField(tokens, "secret", secret);
        ReflectionTestUtils.setField(tokens, "expiration", 3600L);
        tokens.initializeKey();
    }

    @Test
    void refreshPreservesSubjectCustomClaimsAndSignatureAlgorithm() throws Exception {
        String original = Jwts.builder().subject(user.getUsername()).claim("aud", "api")
                .claim("store", "DEFAULT").claim("customerId", 123)
                .issuedAt(new Date(System.currentTimeMillis() - 60000))
                .expiration(new Date(System.currentTimeMillis() + 60000))
                .signWith(key, Jwts.SIG.HS512).compact();
        String refreshed = tokens.refreshToken(original);
        var jwt = Jwts.parser().verifyWith(key).build().parseSignedClaims(refreshed);
        assertEquals("HS512", jwt.getHeader().getAlgorithm());
        assertEquals(user.getUsername(), jwt.getPayload().getSubject());
        assertEquals("DEFAULT", jwt.getPayload().get("store"));
        assertEquals(123, jwt.getPayload().get("customerId", Integer.class));
        assertEquals("api", tokens.getAudienceFromToken(refreshed));
        assertEquals(3600000, jwt.getPayload().getExpiration().getTime()
                - jwt.getPayload().getIssuedAt().getTime());
        assertTrue(tokens.validateToken(refreshed, user));
    }

    @Test
    void generatedClaimNamesAndJsonTypesStayStable() throws Exception {
        JsonNode claims = new ObjectMapper().readTree(Decoders.BASE64URL.decode(
                tokens.generateToken(user).split("\\.")[1]));
        assertEquals(4, claims.size());
        assertTrue(claims.path("sub").isTextual());
        assertTrue(claims.path("aud").isTextual());
        assertEquals("api", claims.path("aud").asText());
        assertTrue(claims.path("iat").isIntegralNumber());
        assertTrue(claims.path("exp").isIntegralNumber());
        assertEquals(3600, claims.path("exp").asLong() - claims.path("iat").asLong());
    }
}
