package com.salesmanager.test.shop.security;

import static org.junit.Assert.*;
import static org.mockito.Mockito.*;

import java.util.Collections;
import java.util.Date;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.test.util.ReflectionTestUtils;

import com.salesmanager.shop.store.security.JWTTokenUtil;
import com.salesmanager.shop.store.security.admin.JWTAdminAuthenticationManager;
import com.salesmanager.shop.store.security.customer.JWTCustomerAuthenticationManager;
import com.salesmanager.shop.store.security.user.JWTUser;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.SignatureAlgorithm;
import io.jsonwebtoken.SignatureException;

public class JWTTokenBoundaryTest {
    private static final String SECRET = "test-secret";
    private JWTTokenUtil tokens;
    private final JWTUser user = new JWTUser(1L, "admin@shopizer.com", "first", "last",
            "admin@shopizer.com", "password", Collections.emptyList(), true, null);

    @Before
    public void setUp() {
        SecurityContextHolder.clearContext();
        tokens = new JWTTokenUtil();
        ReflectionTestUtils.setField(tokens, "secret", SECRET);
        ReflectionTestUtils.setField(tokens, "expiration", 3600L);
    }

    @After
    public void clearContext() {
        SecurityContextHolder.clearContext();
    }

    @Test
    public void generatedAdminTokenHasAdminType() {
        assertEquals("admin", tokens.getTokenTypeFromToken(tokens.generateAdminToken(user)));
    }

    @Test
    public void generatedCustomerTokenHasCustomerType() {
        assertEquals("customer", tokens.getTokenTypeFromToken(tokens.generateCustomerToken(user)));
    }

    @Test
    public void legacyTokenIsRejectedForBothTypes() {
        Date now = new Date();
        String legacy = Jwts.builder().setSubject(user.getUsername()).setIssuedAt(now)
                .setExpiration(new Date(now.getTime() + 3600000L))
                .signWith(SignatureAlgorithm.HS512, SECRET).compact();
        assertFalse(tokens.validateToken(legacy, user, JWTTokenUtil.TOKEN_TYPE_ADMIN));
        assertFalse(tokens.validateToken(legacy, user, JWTTokenUtil.TOKEN_TYPE_CUSTOMER));
        assertFalse(tokens.isTokenOfType(legacy, JWTTokenUtil.TOKEN_TYPE_ADMIN));
        assertFalse(tokens.isTokenOfType(legacy, JWTTokenUtil.TOKEN_TYPE_CUSTOMER));
    }

    @Test
    public void nullExpectedTypeIsRejected() {
        assertFalse(tokens.isTokenOfType(tokens.generateAdminToken(user), null));
    }

    @Test
    public void refreshedAdminTokenRemainsAdmin() {
        String refreshed = tokens.refreshToken(tokens.generateAdminToken(user));
        assertEquals("admin", tokens.getTokenTypeFromToken(refreshed));
        assertTrue(tokens.validateToken(refreshed, user, JWTTokenUtil.TOKEN_TYPE_ADMIN));
        assertFalse(tokens.validateToken(refreshed, user, JWTTokenUtil.TOKEN_TYPE_CUSTOMER));
    }

    @Test(expected = SignatureException.class)
    public void changedTypeSignedWithAnotherSecretIsRejected() {
        Claims claims = Jwts.parser().setSigningKey(SECRET)
                .parseClaimsJws(tokens.generateCustomerToken(user)).getBody();
        claims.put("typ", "admin");
        String forged = Jwts.builder().setClaims(claims)
                .signWith(SignatureAlgorithm.HS512, "another-secret").compact();
        tokens.validateToken(forged, user, JWTTokenUtil.TOKEN_TYPE_ADMIN);
    }

    @Test
    public void adminManagerAuthenticatesOnlyAdminTokens() {
        JWTAdminAuthenticationManager manager = new JWTAdminAuthenticationManager();
        UserDetailsService users = mock(UserDetailsService.class);
        when(users.loadUserByUsername(user.getUsername())).thenReturn(user);
        ReflectionTestUtils.setField(manager, "jwtTokenUtil", tokens);
        ReflectionTestUtils.setField(manager, "jwtAdminDetailsService", users);
        manager.setTokenHeader("Authorization");
        assertNotNull(manager.attemptAuthentication(request(tokens.generateAdminToken(user)),
                mock(HttpServletResponse.class)));
        assertNull(manager.attemptAuthentication(request(tokens.generateCustomerToken(user)),
                mock(HttpServletResponse.class)));
    }

    @Test
    public void customerManagerAuthenticatesOnlyCustomerTokens() {
        JWTCustomerAuthenticationManager manager = new JWTCustomerAuthenticationManager();
        UserDetailsService users = mock(UserDetailsService.class);
        when(users.loadUserByUsername(user.getUsername())).thenReturn(user);
        ReflectionTestUtils.setField(manager, "jwtTokenUtil", tokens);
        ReflectionTestUtils.setField(manager, "jwtCustomerDetailsService", users);
        manager.setTokenHeader("Authorization");
        assertNotNull(manager.attemptAuthentication(request(tokens.generateCustomerToken(user)),
                mock(HttpServletResponse.class)));
        assertNull(manager.attemptAuthentication(request(tokens.generateAdminToken(user)),
                mock(HttpServletResponse.class)));
    }

    private HttpServletRequest request(String token) {
        HttpServletRequest request = mock(HttpServletRequest.class);
        when(request.getHeader("Authorization")).thenReturn("Bearer " + token);
        return request;
    }
}
