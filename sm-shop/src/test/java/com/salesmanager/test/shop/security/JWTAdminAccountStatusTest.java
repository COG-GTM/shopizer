package com.salesmanager.test.shop.security;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.util.Date;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.test.util.ReflectionTestUtils;

import com.salesmanager.core.business.services.user.PermissionService;
import com.salesmanager.core.business.services.user.UserService;
import com.salesmanager.core.model.user.User;
import com.salesmanager.shop.store.security.JWTTokenUtil;
import com.salesmanager.shop.store.security.admin.JWTAdminAuthenticationManager;
import com.salesmanager.shop.store.security.admin.JWTAdminServicesImpl;
import com.salesmanager.shop.store.security.common.CustomAuthenticationException;
import com.salesmanager.shop.store.security.user.JWTUser;

public class JWTAdminAccountStatusTest {

  private static final String USERNAME = "admin@test.com";
  private static final String TOKEN_HEADER = "Authorization";

  private UserService userService;
  private JWTAdminServicesImpl detailsService;
  private JWTTokenUtil jwtTokenUtil;
  private JWTAdminAuthenticationManager authenticationManager;
  private User user;

  @Before
  public void setUp() throws Exception {
    user = new User();
    user.setId(1L);
    user.setAdminName(USERNAME);
    user.setAdminPassword("$2a$10$encodedPassword");

    userService = mock(UserService.class);
    when(userService.getByUserName(USERNAME)).thenReturn(user);

    detailsService = new JWTAdminServicesImpl();
    ReflectionTestUtils.setField(detailsService, "userService", userService);
    ReflectionTestUtils.setField(detailsService, "permissionService", mock(PermissionService.class));

    jwtTokenUtil = new JWTTokenUtil();
    ReflectionTestUtils.setField(jwtTokenUtil, "secret", "test-secret");
    ReflectionTestUtils.setField(jwtTokenUtil, "expiration", 604800L);

    authenticationManager = new JWTAdminAuthenticationManager();
    authenticationManager.setTokenHeader(TOKEN_HEADER);
    ReflectionTestUtils.setField(authenticationManager, "jwtTokenUtil", jwtTokenUtil);
    ReflectionTestUtils.setField(authenticationManager, "jwtAdminDetailsService", detailsService);

    SecurityContextHolder.clearContext();
  }

  @After
  public void tearDown() {
    SecurityContextHolder.clearContext();
  }

  @Test
  public void settingPasswordRecordsChangeDate() {
    Date first = user.getPasswordChangedDate();
    assertNotNull(first);
    assertEquals(0, first.getTime() % 1000);

    user.setPasswordChangedDate(null);
    user.setAdminPassword(user.getAdminPassword());
    assertEquals(null, user.getPasswordChangedDate());

    user.setAdminPassword("$2a$10$anotherEncodedPassword");
    Date second = user.getPasswordChangedDate();
    assertNotNull(second);

    user.setAdminPassword("$2a$10$yetAnotherEncodedPassword");
    assertTrue(user.getPasswordChangedDate().after(second));
  }

  @Test
  public void userDetailsReflectActiveFlagAndPasswordChangeDate() {
    user.setActive(false);
    JWTUser details = (JWTUser) detailsService.loadUserByUsername(USERNAME);
    assertFalse(details.isEnabled());
    assertEquals(user.getPasswordChangedDate(), details.getLastPasswordResetDate());

    user.setActive(true);
    assertTrue(detailsService.loadUserByUsername(USERNAME).isEnabled());
  }

  @Test
  public void activeUserTokenIsAccepted() throws Exception {
    String token = issueToken();
    assertNotNull(authenticationManager.attemptAuthentication(request(token), new MockHttpServletResponse()));
  }

  @Test(expected = CustomAuthenticationException.class)
  public void disabledUserTokenIsRejected() throws Exception {
    String token = issueToken();
    user.setActive(false);
    authenticationManager.attemptAuthentication(request(token), new MockHttpServletResponse());
  }

  @Test
  public void tokenIssuedBeforePasswordChangeIsRejected() throws Exception {
    String token = issueToken();
    user.setAdminPassword("$2a$10$changedEncodedPassword");

    UserDetails details = detailsService.loadUserByUsername(USERNAME);
    assertFalse(jwtTokenUtil.validateToken(token, details));
    assertFalse(jwtTokenUtil.canTokenBeRefreshedWithGrace(token, ((JWTUser) details).getLastPasswordResetDate()));
    assertEquals(null, authenticationManager.attemptAuthentication(request(token), new MockHttpServletResponse()));
  }

  @Test
  public void tokenIssuedInSameSecondAsPasswordChangeIsRejected() throws Exception {
    String token = issueToken();
    user.setAdminPassword("$2a$10$sameSecondEncodedPassword");
    assertFalse(jwtTokenUtil.validateToken(token, detailsService.loadUserByUsername(USERNAME)));
  }

  @Test
  public void tokenIssuedAfterPasswordChangeIsAccepted() throws Exception {
    user.setAdminPassword("$2a$10$newEncodedPassword");
    String token = issueToken();
    UserDetails details = detailsService.loadUserByUsername(USERNAME);
    assertTrue(jwtTokenUtil.validateToken(token, details));
    assertTrue(jwtTokenUtil.canTokenBeRefreshedWithGrace(token, ((JWTUser) details).getLastPasswordResetDate()));
    assertNotEquals(null, authenticationManager.attemptAuthentication(request(token), new MockHttpServletResponse()));
  }

  private String issueToken() {
    return jwtTokenUtil.generateToken(detailsService.loadUserByUsername(USERNAME));
  }

  private MockHttpServletRequest request(String token) {
    MockHttpServletRequest request = new MockHttpServletRequest();
    request.addHeader(TOKEN_HEADER, "Bearer " + token);
    return request;
  }
}
