package com.salesmanager.test.shop.security;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertThrows;
import static org.junit.Assert.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.util.Collections;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.DisabledException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.util.ReflectionTestUtils;

import com.salesmanager.shop.store.api.v1.user.AuthenticateUserApi;
import com.salesmanager.shop.store.security.AuthenticationRequest;
import com.salesmanager.shop.store.security.JWTTokenUtil;
import com.salesmanager.shop.store.security.admin.JWTAdminAuthenticationProvider;
import com.salesmanager.shop.store.security.user.JWTUser;

public class AdminAuthenticationContractTest {

    private static final String USERNAME = "provider-test@example.com";
    private UserDetailsService users;
    private PasswordEncoder encoder;
    private JWTAdminAuthenticationProvider provider;

    @Before
    public void setUp() {
        SecurityContextHolder.clearContext();
        users = mock(UserDetailsService.class);
        encoder = mock(PasswordEncoder.class);
        provider = new JWTAdminAuthenticationProvider();
        provider.setJwtAdminDetailsService(users);
        ReflectionTestUtils.setField(provider, "passwordEncoder", encoder);
    }

    @After
    public void clearContext() {
        SecurityContextHolder.clearContext();
    }

    private JWTUser user(boolean enabled) {
        return new JWTUser(9L, USERNAME, "Test", "Admin", USERNAME, "hash",
                Collections.emptyList(), enabled, null);
    }

    private Authentication credentials() {
        return new UsernamePasswordAuthenticationToken(USERNAME, "password");
    }

    @Test
    public void disabledAccountIsRejectedBeforeCheckingPassword() {
        when(users.loadUserByUsername(USERNAME)).thenReturn(user(false));
        assertThrows(DisabledException.class, () -> provider.authenticate(credentials()));
        verifyNoInteractions(encoder);
    }

    @Test
    public void activeAccountStillAuthenticates() {
        JWTUser user = user(true);
        when(users.loadUserByUsername(USERNAME)).thenReturn(user);
        when(encoder.matches("password", USERNAME)).thenReturn(true);
        Authentication result = provider.authenticate(credentials());
        assertTrue(result.isAuthenticated());
        assertSame(user, result.getPrincipal());
        assertEquals(user.getAuthorities(), result.getAuthorities());
    }

    @Test
    public void activeAccountWithBadPasswordIsRejected() {
        when(users.loadUserByUsername(USERNAME)).thenReturn(user(true));
        when(encoder.matches("password", USERNAME)).thenReturn(false);
        assertThrows(BadCredentialsException.class, () -> provider.authenticate(credentials()));
    }

    @Test
    public void missingAccountIsRejectedWithoutCheckingPassword() {
        assertThrows(BadCredentialsException.class, () -> provider.authenticate(credentials()));
        verifyNoInteractions(encoder);
    }

    @Test
    public void disabledLoginHasSameResponseAsBadCredentials() throws Exception {
        AuthenticateUserApi api = new AuthenticateUserApi();
        AuthenticationManager manager = mock(AuthenticationManager.class);
        JWTTokenUtil tokens = mock(JWTTokenUtil.class);
        ReflectionTestUtils.setField(api, "jwtAdminAuthenticationManager", manager);
        ReflectionTestUtils.setField(api, "jwtAdminDetailsService", users);
        ReflectionTestUtils.setField(api, "jwtTokenUtil", tokens);
        when(manager.authenticate(org.mockito.ArgumentMatchers.any()))
                .thenThrow(new DisabledException("disabled"))
                .thenThrow(new BadCredentialsException("incorrect"));

        ResponseEntity<?> disabled = api.authenticate(new AuthenticationRequest(USERNAME, "password"));
        ResponseEntity<?> incorrect = api.authenticate(new AuthenticationRequest(USERNAME, "password"));
        assertEquals(HttpStatus.UNAUTHORIZED, disabled.getStatusCode());
        assertEquals(incorrect.getStatusCode(), disabled.getStatusCode());
        assertEquals("{\"message\":\"Bad credentials\"}", disabled.getBody());
        assertEquals(incorrect.getBody(), disabled.getBody());
        verifyNoInteractions(tokens, users);
    }
}
