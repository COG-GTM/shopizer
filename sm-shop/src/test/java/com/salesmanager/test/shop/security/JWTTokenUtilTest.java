package com.salesmanager.test.shop.security;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import java.util.Collections;
import java.util.Date;

import org.junit.Before;
import org.junit.Test;
import org.springframework.test.util.ReflectionTestUtils;

import com.salesmanager.shop.store.security.JWTTokenUtil;
import com.salesmanager.shop.store.security.user.JWTUser;

import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.SignatureAlgorithm;

public class JWTTokenUtilTest {

    private static final String SECRET = "test-secret";
    private static final long EXPIRATION_SECONDS = 3600L;

    private JWTTokenUtil tokenUtil;

    @Before
    public void setUp() {
        tokenUtil = new JWTTokenUtil();
        ReflectionTestUtils.setField(tokenUtil, "secret", SECRET);
        ReflectionTestUtils.setField(tokenUtil, "expiration", EXPIRATION_SECONDS);
    }

    @Test
    public void adminTokenValidatesForMatchingUser() {
        JWTUser user = user(1L, "admin@example.com");
        String token = tokenUtil.generateToken(user, JWTTokenUtil.TOKEN_TYPE_ADMIN);

        assertTrue(tokenUtil.validateToken(token, user, JWTTokenUtil.TOKEN_TYPE_ADMIN));
    }

    @Test
    public void customerTokenCannotValidateAsAdminForSameIdentity() {
        JWTUser customer = user(1L, "admin@example.com");
        String token = tokenUtil.generateToken(customer, JWTTokenUtil.TOKEN_TYPE_CUSTOMER);

        assertFalse(tokenUtil.validateToken(token, customer, JWTTokenUtil.TOKEN_TYPE_ADMIN));
    }

    @Test
    public void adminTokenCannotValidateAsCustomer() {
        JWTUser user = user(1L, "admin@example.com");
        String token = tokenUtil.generateToken(user, JWTTokenUtil.TOKEN_TYPE_ADMIN);

        assertFalse(tokenUtil.validateToken(token, user, JWTTokenUtil.TOKEN_TYPE_CUSTOMER));
    }

    @Test
    public void tokenIsRejectedForDifferentUserId() {
        JWTUser originalUser = user(1L, "admin@example.com");
        JWTUser differentUser = user(2L, "admin@example.com");
        String token = tokenUtil.generateToken(originalUser, JWTTokenUtil.TOKEN_TYPE_ADMIN);

        assertFalse(tokenUtil.validateToken(token, differentUser, JWTTokenUtil.TOKEN_TYPE_ADMIN));
    }

    @Test
    public void legacyTokenWithoutRealmAndUserIdIsRejected() {
        JWTUser user = user(1L, "admin@example.com");
        String token = legacyToken(user.getUsername());

        assertFalse(tokenUtil.validateToken(token, user, JWTTokenUtil.TOKEN_TYPE_ADMIN));
    }

    @Test
    public void adminTokenCanBeRefreshedWithGrace() {
        JWTUser user = user(1L, "admin@example.com");
        String token = tokenUtil.generateToken(user, JWTTokenUtil.TOKEN_TYPE_ADMIN);

        assertTrue(tokenUtil.canTokenBeRefreshedWithGrace(token, user, JWTTokenUtil.TOKEN_TYPE_ADMIN));
    }

    @Test
    public void customerTokenCannotBeRefreshedAsAdminWithGrace() {
        JWTUser user = user(1L, "admin@example.com");
        String token = tokenUtil.generateToken(user, JWTTokenUtil.TOKEN_TYPE_CUSTOMER);

        assertFalse(tokenUtil.canTokenBeRefreshedWithGrace(token, user, JWTTokenUtil.TOKEN_TYPE_ADMIN));
    }

    @Test
    public void refreshPreservesTokenTypeAndUserId() {
        JWTUser user = user(42L, "admin@example.com");
        String token = tokenUtil.generateToken(user, JWTTokenUtil.TOKEN_TYPE_ADMIN);
        String refreshedToken = tokenUtil.refreshToken(token);

        assertEquals(JWTTokenUtil.TOKEN_TYPE_ADMIN, tokenUtil.getTokenTypeFromToken(refreshedToken));
        assertEquals(Long.valueOf(42L), tokenUtil.getUserIdFromToken(refreshedToken));
        assertTrue(tokenUtil.validateToken(refreshedToken, user, JWTTokenUtil.TOKEN_TYPE_ADMIN));
    }

    private JWTUser user(Long id, String username) {
        return new JWTUser(id, username, "First", "Last", username, "password",
                Collections.emptyList(), true, null);
    }

    private String legacyToken(String username) {
        Date created = new Date();
        Date expiration = new Date(created.getTime() + EXPIRATION_SECONDS * 1000);
        return Jwts.builder()
                .setSubject(username)
                .setAudience("api")
                .setIssuedAt(created)
                .setExpiration(expiration)
                .signWith(SignatureAlgorithm.HS512, SECRET)
                .compact();
    }
}
