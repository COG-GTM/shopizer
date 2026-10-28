package com.salesmanager.test.shop.security;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import java.util.Collections;

import org.junit.Before;
import org.junit.Test;
import org.springframework.test.util.ReflectionTestUtils;

import com.salesmanager.shop.store.security.JWTTokenUtil;
import com.salesmanager.shop.store.security.user.JWTUser;

public class JWTTokenUtilTest {

    private JWTTokenUtil jwtTokenUtil;

    private final JWTUser user = new JWTUser(1L, "admin@shopizer.com", "first", "last",
            "admin@shopizer.com", "password", Collections.emptyList(), true, null);

    @Before
    public void setUp() {
        jwtTokenUtil = new JWTTokenUtil();
        ReflectionTestUtils.setField(jwtTokenUtil, "secret", "test-secret");
        ReflectionTestUtils.setField(jwtTokenUtil, "expiration", 3600L);
    }

    @Test
    public void adminTokenIsOnlyValidForAdmin() {
        String token = jwtTokenUtil.generateAdminToken(user);
        assertTrue(jwtTokenUtil.validateToken(token, user, JWTTokenUtil.TOKEN_TYPE_ADMIN));
        assertFalse(jwtTokenUtil.validateToken(token, user, JWTTokenUtil.TOKEN_TYPE_CUSTOMER));
    }

    @Test
    public void customerTokenIsRejectedAsAdminToken() {
        String token = jwtTokenUtil.generateCustomerToken(user);
        assertTrue(jwtTokenUtil.validateToken(token, user, JWTTokenUtil.TOKEN_TYPE_CUSTOMER));
        assertFalse(jwtTokenUtil.validateToken(token, user, JWTTokenUtil.TOKEN_TYPE_ADMIN));
    }

    @Test
    public void refreshedTokenKeepsItsType() {
        String refreshed = jwtTokenUtil.refreshToken(jwtTokenUtil.generateCustomerToken(user));
        assertTrue(jwtTokenUtil.isTokenOfType(refreshed, JWTTokenUtil.TOKEN_TYPE_CUSTOMER));
        assertFalse(jwtTokenUtil.isTokenOfType(refreshed, JWTTokenUtil.TOKEN_TYPE_ADMIN));
    }
}
