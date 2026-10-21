package com.salesmanager.shop.store.security;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import java.util.Collections;
import java.util.Date;

import org.junit.Before;
import org.junit.Test;
import org.springframework.test.util.ReflectionTestUtils;

import com.salesmanager.shop.store.security.user.JWTUser;

import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.SignatureAlgorithm;
import io.jsonwebtoken.SignatureException;

public class JWTTokenUtilTest {

    private static final String SECRET = "test-secret-for-jwt-token-util";

    private JWTTokenUtil jwtTokenUtil;
    private JWTUser admin;
    private JWTUser customer;

    @Before
    public void setUp() {
        jwtTokenUtil = new JWTTokenUtil();
        ReflectionTestUtils.setField(jwtTokenUtil, "secret", SECRET);
        ReflectionTestUtils.setField(jwtTokenUtil, "expiration", 3600L);
        admin = user("admin@example.com");
        customer = user("customer@example.com");
    }

    @Test
    public void generatedTokensExposeThePrincipalTypeClaim() {
        String adminToken = jwtTokenUtil.generateToken(admin, JWTTokenUtil.PRINCIPAL_TYPE_ADMIN);
        String customerToken = jwtTokenUtil.generateToken(customer, JWTTokenUtil.PRINCIPAL_TYPE_CUSTOMER);

        assertEquals(JWTTokenUtil.PRINCIPAL_TYPE_ADMIN, jwtTokenUtil.getPrincipalTypeFromToken(adminToken));
        assertEquals(JWTTokenUtil.PRINCIPAL_TYPE_ADMIN,
                Jwts.parser().setSigningKey(SECRET).parseClaimsJws(adminToken).getBody().get("principal_type"));
        assertEquals(JWTTokenUtil.PRINCIPAL_TYPE_CUSTOMER, jwtTokenUtil.getPrincipalTypeFromToken(customerToken));
        assertEquals(JWTTokenUtil.PRINCIPAL_TYPE_CUSTOMER,
                Jwts.parser().setSigningKey(SECRET).parseClaimsJws(customerToken).getBody().get("principal_type"));
    }

    @Test
    public void tokenPrincipalTypeMustMatchTheExpectedType() {
        String adminToken = jwtTokenUtil.generateToken(admin, JWTTokenUtil.PRINCIPAL_TYPE_ADMIN);

        assertTrue(jwtTokenUtil.isTokenOfPrincipalType(adminToken, JWTTokenUtil.PRINCIPAL_TYPE_ADMIN));
        assertFalse(jwtTokenUtil.isTokenOfPrincipalType(adminToken, JWTTokenUtil.PRINCIPAL_TYPE_CUSTOMER));
        assertFalse(jwtTokenUtil.isTokenOfPrincipalType(adminToken, null));
    }

    @Test
    public void validationRequiresTheMatchingPrincipalTypeAndUser() {
        String adminToken = jwtTokenUtil.generateToken(admin, JWTTokenUtil.PRINCIPAL_TYPE_ADMIN);
        String customerToken = jwtTokenUtil.generateToken(customer, JWTTokenUtil.PRINCIPAL_TYPE_CUSTOMER);

        assertTrue(jwtTokenUtil.validateToken(adminToken, admin, JWTTokenUtil.PRINCIPAL_TYPE_ADMIN));
        assertFalse(jwtTokenUtil.validateToken(adminToken, admin, JWTTokenUtil.PRINCIPAL_TYPE_CUSTOMER));
        assertFalse(jwtTokenUtil.validateToken(customerToken, customer, JWTTokenUtil.PRINCIPAL_TYPE_ADMIN));
        assertTrue(jwtTokenUtil.validateToken(customerToken, customer, JWTTokenUtil.PRINCIPAL_TYPE_CUSTOMER));
    }

    @Test
    public void legacyTokensWithoutPrincipalTypeAreRejected() {
        String token = legacyToken("admin@example.com", SECRET);

        assertEquals(null, jwtTokenUtil.getPrincipalTypeFromToken(token));
        assertFalse(jwtTokenUtil.validateToken(token, admin, JWTTokenUtil.PRINCIPAL_TYPE_ADMIN));
        assertFalse(jwtTokenUtil.validateToken(token, admin, JWTTokenUtil.PRINCIPAL_TYPE_CUSTOMER));
    }

    @Test(expected = SignatureException.class)
    public void tokensSignedWithAnotherSecretAreRejected() {
        String token = Jwts.builder()
                .setSubject(admin.getUsername())
                .claim("principal_type", JWTTokenUtil.PRINCIPAL_TYPE_ADMIN)
                .setIssuedAt(new Date())
                .setExpiration(new Date(System.currentTimeMillis() + 3600000L))
                .signWith(SignatureAlgorithm.HS512, "different-secret")
                .compact();

        jwtTokenUtil.validateToken(token, admin, JWTTokenUtil.PRINCIPAL_TYPE_ADMIN);
    }

    @Test
    public void refreshingAnAdminTokenPreservesItsPrincipalType() {
        String token = jwtTokenUtil.generateToken(admin, JWTTokenUtil.PRINCIPAL_TYPE_ADMIN);
        String refreshedToken = jwtTokenUtil.refreshToken(token);

        assertEquals(admin.getUsername(), jwtTokenUtil.getUsernameFromToken(refreshedToken));
        assertEquals(JWTTokenUtil.PRINCIPAL_TYPE_ADMIN, jwtTokenUtil.getPrincipalTypeFromToken(refreshedToken));
        assertTrue(jwtTokenUtil.validateToken(refreshedToken, admin, JWTTokenUtil.PRINCIPAL_TYPE_ADMIN));
        assertFalse(jwtTokenUtil.validateToken(refreshedToken, admin, JWTTokenUtil.PRINCIPAL_TYPE_CUSTOMER));
    }

    @Test
    public void validationRejectsAnotherUsernameAndTokensIssuedBeforePasswordReset() {
        String token = jwtTokenUtil.generateToken(admin, JWTTokenUtil.PRINCIPAL_TYPE_ADMIN);
        JWTUser differentUser = user("different@example.com");
        Date resetAfterIssuance = new Date(jwtTokenUtil.getIssuedAtDateFromToken(token).getTime() + 1000L);
        JWTUser resetUser = new JWTUser(1L, admin.getUsername(), "Admin", "User", admin.getEmail(),
                admin.getPassword(), Collections.emptyList(), true, resetAfterIssuance);

        assertFalse(jwtTokenUtil.validateToken(token, differentUser, JWTTokenUtil.PRINCIPAL_TYPE_ADMIN));
        assertFalse(jwtTokenUtil.validateToken(token, resetUser, JWTTokenUtil.PRINCIPAL_TYPE_ADMIN));
    }

    private JWTUser user(String username) {
        return new JWTUser(1L, username, "Test", "User", username, "password",
                Collections.emptyList(), true, null);
    }

    private String legacyToken(String username, String secret) {
        long now = System.currentTimeMillis();
        return Jwts.builder()
                .setSubject(username)
                .setAudience("api")
                .setIssuedAt(new Date(now))
                .setExpiration(new Date(now + 3600000L))
                .signWith(SignatureAlgorithm.HS512, secret)
                .compact();
    }
}
