package com.salesmanager.shop.store.security;

import static org.junit.jupiter.api.Assertions.*;

import java.nio.charset.StandardCharsets;
import java.util.Date;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.junit.jupiter.api.Test;
import org.springframework.security.core.userdetails.User;

class JWTTokenUtilBoundaryTest {
    private static final String SECRET = "x".repeat(64);
    private final JWTTokenUtil util = new JWTTokenUtil(SECRET, 3600L);

    @Test
    void graceRefreshRejectsPasswordResetAfterIssueTime() {
        String token = util.generateToken(User.withUsername("user").password("pwd").authorities("ROLE_AUTH").build());
        assertTrue(util.canTokenBeRefreshedWithGrace(token, null));
        assertFalse(util.canTokenBeRefreshedWithGrace(token, new Date(util.getIssuedAtDateFromToken(token).getTime() + 1000)));
    }

    @Test
    void secretBoundaryAndMissingConfigurationFailFast() {
        assertThrows(IllegalStateException.class, () -> new JWTTokenUtil(null, 3600L));
        assertThrows(IllegalStateException.class, () -> new JWTTokenUtil("   ", 3600L));
        assertThrows(IllegalStateException.class, () -> new JWTTokenUtil("x".repeat(63), 3600L));
        assertDoesNotThrow(() -> new JWTTokenUtil(SECRET, 3600L));
    }

    @Test
    void malformedAndUnsignedTokensAreRejected() {
        assertThrows(JwtException.class, () -> util.getUsernameFromToken("garbage"));
        assertThrows(JwtException.class, () -> util.getUsernameFromToken("abc.def.ghi"));
        String unsigned = Jwts.builder().subject("user").compact();
        assertThrows(JwtException.class, () -> util.getUsernameFromToken(unsigned));
    }

    @Test
    void observeHs256TokenSignedWithTheConfiguredKey() {
        String token = Jwts.builder().subject("user").signWith(
                Keys.hmacShaKeyFor(SECRET.getBytes(StandardCharsets.UTF_8)), Jwts.SIG.HS256).compact();
        try {
            System.out.println("JWT algorithm observation: same-key HS256 accepted, subject=" + util.getUsernameFromToken(token));
        } catch (JwtException exception) {
            System.out.println("JWT algorithm observation: same-key HS256 rejected: " + exception.getClass().getName());
        }
    }
}
