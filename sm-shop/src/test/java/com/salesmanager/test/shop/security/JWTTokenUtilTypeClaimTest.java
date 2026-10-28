package com.salesmanager.test.shop.security;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.Collections;
import java.util.Date;

import org.junit.Before;
import org.junit.Test;
import org.springframework.test.util.ReflectionTestUtils;

import com.salesmanager.shop.store.security.JWTTokenUtil;
import com.salesmanager.shop.store.security.user.JWTUser;

import io.jsonwebtoken.ExpiredJwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.SignatureAlgorithm;

public class JWTTokenUtilTypeClaimTest {

  private static final String SECRET = "a2tra2tra2tra2tra2tra2tra2tra2tra2tra2tra2tra2tra2tra2tra2tra2tra2tra2tra2tra2tra2tra2tra2tr";
  private static final String USERNAME = "admin@shopizer.com";

  private JWTTokenUtil jwtTokenUtil;
  private JWTUser principal;

  @Before
  public void setUp() {
    jwtTokenUtil = new JWTTokenUtil();
    ReflectionTestUtils.setField(jwtTokenUtil, "secret", SECRET);
    ReflectionTestUtils.setField(jwtTokenUtil, "expiration", 3600L);
    principal = principal(USERNAME, null);
  }

  @Test
  public void generatedTokensCarryTheirRealmAndSubject() {
    String adminToken = jwtTokenUtil.generateAdminToken(principal);
    String customerToken = jwtTokenUtil.generateCustomerToken(principal);

    assertEquals(USERNAME, jwtTokenUtil.getUsernameFromToken(adminToken));
    assertEquals(JWTTokenUtil.TOKEN_TYPE_ADMIN, jwtTokenUtil.getTokenTypeFromToken(adminToken));
    assertEquals(JWTTokenUtil.TOKEN_TYPE_CUSTOMER, jwtTokenUtil.getTokenTypeFromToken(customerToken));
  }

  @Test
  public void untypedTokenHasNoRealmAndIsNotAccepted() {
    String token = untypedToken(USERNAME);

    assertEquals(null, jwtTokenUtil.getTokenTypeFromToken(token));
    assertFalse(jwtTokenUtil.isTokenOfType(token, JWTTokenUtil.TOKEN_TYPE_ADMIN));
    assertFalse(jwtTokenUtil.isTokenOfType(jwtTokenUtil.generateAdminToken(principal), null));
  }

  @Test
  public void tokenForAnotherUsernameIsInvalid() {
    String token = jwtTokenUtil.generateAdminToken(principal);

    assertFalse(jwtTokenUtil.validateToken(token, principal("another@example.com", null),
        JWTTokenUtil.TOKEN_TYPE_ADMIN));
  }

  @Test
  public void tokenCreatedBeforePasswordResetIsInvalid() {
    String token = jwtTokenUtil.generateAdminToken(principal);
    JWTUser resetPrincipal = principal(USERNAME, new Date(System.currentTimeMillis() + 60_000L));

    assertFalse(jwtTokenUtil.validateToken(token, resetPrincipal, JWTTokenUtil.TOKEN_TYPE_ADMIN));
  }

  @Test(expected = ExpiredJwtException.class)
  public void expiredAdminTokenThrowsExpiredJwtException() {
    ReflectionTestUtils.setField(jwtTokenUtil, "expiration", -60L);
    String token = jwtTokenUtil.generateAdminToken(principal);

    jwtTokenUtil.validateToken(token, principal, JWTTokenUtil.TOKEN_TYPE_ADMIN);
  }

  @Test(expected = io.jsonwebtoken.SignatureException.class)
  public void changingCustomerTypeToAdminInvalidatesSignature() {
    String[] parts = jwtTokenUtil.generateCustomerToken(principal).split("\\.");
    String payload = new String(Base64.getUrlDecoder().decode(parts[1]), StandardCharsets.UTF_8)
        .replace("\"typ\":\"customer\"", "\"typ\":\"admin\"");
    String tamperedPayload = Base64.getUrlEncoder().withoutPadding()
        .encodeToString(payload.getBytes(StandardCharsets.UTF_8));
    String token = parts[0] + "." + tamperedPayload + "." + parts[2];

    jwtTokenUtil.validateToken(token, principal, JWTTokenUtil.TOKEN_TYPE_ADMIN);
  }

  @Test
  public void refreshPreservesAdminRealmAndSubject() {
    String token = jwtTokenUtil.generateAdminToken(principal);
    String refreshed = jwtTokenUtil.refreshToken(token);

    assertEquals(USERNAME, jwtTokenUtil.getUsernameFromToken(refreshed));
    assertEquals(JWTTokenUtil.TOKEN_TYPE_ADMIN, jwtTokenUtil.getTokenTypeFromToken(refreshed));
    assertTrue(jwtTokenUtil.validateToken(refreshed, principal, JWTTokenUtil.TOKEN_TYPE_ADMIN));
    assertFalse(jwtTokenUtil.validateToken(refreshed, principal, JWTTokenUtil.TOKEN_TYPE_CUSTOMER));
  }

  private static JWTUser principal(String username, Date lastPasswordResetDate) {
    return new JWTUser(1L, username, "first", "last", username, "pwd", Collections.emptyList(),
        true, lastPasswordResetDate);
  }

  private String untypedToken(String username) {
    Date now = new Date();
    return Jwts.builder()
        .setSubject(username)
        .setAudience("api")
        .setIssuedAt(now)
        .setExpiration(new Date(now.getTime() + 3600_000L))
        .signWith(SignatureAlgorithm.HS512, SECRET)
        .compact();
  }
}
