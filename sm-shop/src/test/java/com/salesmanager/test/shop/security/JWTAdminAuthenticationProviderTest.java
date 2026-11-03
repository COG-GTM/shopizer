package com.salesmanager.test.shop.security;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.util.Collections;

import org.junit.Before;
import org.junit.Test;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.DisabledException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.test.util.ReflectionTestUtils;

import com.salesmanager.shop.store.security.admin.JWTAdminAuthenticationProvider;
import com.salesmanager.shop.store.security.user.JWTUser;

public class JWTAdminAuthenticationProviderTest {

  private static final String USERNAME = "admin@example.test";

  private JWTAdminAuthenticationProvider provider;
  private UserDetailsService detailsService;

  @Before
  public void setUp() {
    provider = new JWTAdminAuthenticationProvider();
    detailsService = mock(UserDetailsService.class);
    ReflectionTestUtils.setField(provider, "jwtAdminDetailsService", detailsService);
    ReflectionTestUtils.setField(provider, "passwordEncoder", new BCryptPasswordEncoder());
  }

  @Test(expected = DisabledException.class)
  public void disabledUserIsRejectedBeforePasswordCheck() {
    when(detailsService.loadUserByUsername(USERNAME)).thenReturn(user(false));

    provider.authenticate(new UsernamePasswordAuthenticationToken(USERNAME, "any-password"));
  }

  @Test(expected = BadCredentialsException.class)
  public void enabledUserWithWrongPasswordGetsBadCredentials() {
    when(detailsService.loadUserByUsername(USERNAME)).thenReturn(user(true));

    provider.authenticate(new UsernamePasswordAuthenticationToken(USERNAME, "wrong-password"));
  }

  @Test(expected = BadCredentialsException.class)
  public void unknownUserGetsBadCredentials() {
    when(detailsService.loadUserByUsername(USERNAME)).thenReturn(null);

    provider.authenticate(new UsernamePasswordAuthenticationToken(USERNAME, "any-password"));
  }

  private JWTUser user(boolean enabled) {
    return new JWTUser(1L, USERNAME, "Test", "User", USERNAME, "encoded-password",
        Collections.emptyList(), enabled, null);
  }
}
