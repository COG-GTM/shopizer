package com.salesmanager.test.shop.security;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertNotNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.util.Collections;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.test.util.ReflectionTestUtils;

import com.salesmanager.shop.store.security.JWTTokenUtil;
import com.salesmanager.shop.store.security.admin.JWTAdminAuthenticationManager;
import com.salesmanager.shop.store.security.customer.JWTCustomerAuthenticationManager;
import com.salesmanager.shop.store.security.user.JWTUser;

public class JWTAuthenticationManagerRealmTest {

    private static final String SECRET = "manager-test-secret";
    private static final String USERNAME = "admin@shopizer.com";

    private JWTTokenUtil tokenUtil;
    private JWTUser user;
    private UserDetailsService adminDetailsService;
    private UserDetailsService customerDetailsService;

    @Before
    public void setUp() {
        SecurityContextHolder.clearContext();
        tokenUtil = new JWTTokenUtil();
        ReflectionTestUtils.setField(tokenUtil, "secret", SECRET);
        ReflectionTestUtils.setField(tokenUtil, "expiration", 3600L);
        user = new JWTUser(1L, USERNAME, "Admin", "User", USERNAME, "password",
                Collections.emptyList(), true, null);

        adminDetailsService = mock(UserDetailsService.class);
        customerDetailsService = mock(UserDetailsService.class);
        when(adminDetailsService.loadUserByUsername(USERNAME)).thenReturn(user);
        when(customerDetailsService.loadUserByUsername(USERNAME)).thenReturn(user);
    }

    @After
    public void tearDown() {
        SecurityContextHolder.clearContext();
    }

    @Test
    public void adminManagerAcceptsAdminTokenAndRejectsCustomerToken() throws Exception {
        JWTAdminAuthenticationManager manager = adminManager();

        Authentication adminAuthentication = manager.attemptAuthentication(
                request(token(JWTTokenUtil.TOKEN_TYPE_ADMIN, 1L)), new MockHttpServletResponse());
        Authentication customerAuthentication = manager.attemptAuthentication(
                request(token(JWTTokenUtil.TOKEN_TYPE_CUSTOMER, 1L)), new MockHttpServletResponse());

        assertNotNull(adminAuthentication);
        assertEquals(user, adminAuthentication.getPrincipal());
        assertNull(customerAuthentication);
    }

    @Test
    public void customerManagerAcceptsCustomerTokenAndRejectsAdminToken() throws Exception {
        JWTCustomerAuthenticationManager manager = customerManager();

        Authentication customerAuthentication = manager.attemptAuthentication(
                request(token(JWTTokenUtil.TOKEN_TYPE_CUSTOMER, 1L)), new MockHttpServletResponse());
        Authentication adminAuthentication = manager.attemptAuthentication(
                request(token(JWTTokenUtil.TOKEN_TYPE_ADMIN, 1L)), new MockHttpServletResponse());

        assertNotNull(customerAuthentication);
        assertEquals(user, customerAuthentication.getPrincipal());
        assertNull(adminAuthentication);
    }

    @Test
    public void adminManagerRejectsTokenForDifferentUserId() throws Exception {
        Authentication authentication = adminManager().attemptAuthentication(
                request(token(JWTTokenUtil.TOKEN_TYPE_ADMIN, 2L)), new MockHttpServletResponse());

        assertNull(authentication);
    }

    @Test
    public void customerTokenOnAdminManagerDoesNotSetSecurityContext() throws Exception {
        adminManager().authenticateRequest(
                request(token(JWTTokenUtil.TOKEN_TYPE_CUSTOMER, 1L)), new MockHttpServletResponse());

        assertNull(SecurityContextHolder.getContext().getAuthentication());
    }

    private JWTAdminAuthenticationManager adminManager() {
        JWTAdminAuthenticationManager manager = new JWTAdminAuthenticationManager();
        ReflectionTestUtils.setField(manager, "jwtTokenUtil", tokenUtil);
        ReflectionTestUtils.setField(manager, "jwtAdminDetailsService", adminDetailsService);
        manager.setTokenHeader("Authorization");
        return manager;
    }

    private JWTCustomerAuthenticationManager customerManager() {
        JWTCustomerAuthenticationManager manager = new JWTCustomerAuthenticationManager();
        ReflectionTestUtils.setField(manager, "jwtTokenUtil", tokenUtil);
        ReflectionTestUtils.setField(manager, "jwtCustomerDetailsService", customerDetailsService);
        manager.setTokenHeader("Authorization");
        return manager;
    }

    private MockHttpServletRequest request(String token) {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.addHeader("Authorization", "Bearer " + token);
        return request;
    }

    private String token(String tokenType, Long id) {
        return tokenUtil.generateToken(user(id, USERNAME), tokenType);
    }

    private JWTUser user(Long id, String username) {
        return new JWTUser(id, username, "Admin", "User", username, "password",
                Collections.emptyList(), true, null);
    }
}
