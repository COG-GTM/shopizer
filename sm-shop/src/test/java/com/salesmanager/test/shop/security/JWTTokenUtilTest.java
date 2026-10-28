package com.salesmanager.test.shop.security;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import java.util.Collections;
import java.util.Date;
import java.util.HashMap;
import java.util.Map;

import org.junit.Before;
import org.junit.Test;
import org.springframework.test.util.ReflectionTestUtils;

import com.salesmanager.shop.store.security.JWTTokenUtil;
import com.salesmanager.shop.store.security.user.JWTUser;

import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.SignatureAlgorithm;

public class JWTTokenUtilTest {

  private static final String SECRET = "a2tra2tra2tra2tra2tra2tra2tra2tra2tra2tra2tra2tra2tra2tra2tra2tra2tra2tra2tra2tra2tra2tra2tra2tr";
  private static final String USERNAME = "admin@shopizer.com";

  private JWTTokenUtil jwtTokenUtil;
  private JWTUser principal;

  @Before
  public void setUp() {
    jwtTokenUtil = new JWTTokenUtil();
    ReflectionTestUtils.setField(jwtTokenUtil, "secret", SECRET);
    ReflectionTestUtils.setField(jwtTokenUtil, "expiration", 3600L);
    principal = new JWTUser(1L, USERNAME, "first", "last", USERNAME, "pwd",
        Collections.emptyList(), true, null);
  }

  @Test
  public void adminTokenIsAcceptedOnlyAsAdmin() {
    String token = jwtTokenUtil.generateAdminToken(principal);

    assertEquals(JWTTokenUtil.TOKEN_TYPE_ADMIN, jwtTokenUtil.getTokenTypeFromToken(token));
    assertTrue(jwtTokenUtil.validateToken(token, principal, JWTTokenUtil.TOKEN_TYPE_ADMIN));
    assertFalse(jwtTokenUtil.validateToken(token, principal, JWTTokenUtil.TOKEN_TYPE_CUSTOMER));
  }

  @Test
  public void customerTokenWithAdminUsernameIsRejectedAsAdmin() {
    String token = jwtTokenUtil.generateCustomerToken(principal);

    assertEquals(JWTTokenUtil.TOKEN_TYPE_CUSTOMER, jwtTokenUtil.getTokenTypeFromToken(token));
    assertTrue(jwtTokenUtil.validateToken(token, principal, JWTTokenUtil.TOKEN_TYPE_CUSTOMER));
    assertFalse(jwtTokenUtil.validateToken(token, principal, JWTTokenUtil.TOKEN_TYPE_ADMIN));
  }

  @Test
  public void untypedLegacyTokenIsRejected() {
    Date now = new Date();
    String token = Jwts.builder()
        .setSubject(USERNAME)
        .setAudience("api")
        .setIssuedAt(now)
        .setExpiration(new Date(now.getTime() + 3600_000L))
        .signWith(SignatureAlgorithm.HS512, SECRET)
        .compact();

    assertFalse(jwtTokenUtil.validateToken(token, principal, JWTTokenUtil.TOKEN_TYPE_ADMIN));
    assertFalse(jwtTokenUtil.validateToken(token, principal, JWTTokenUtil.TOKEN_TYPE_CUSTOMER));
  }

  @Test
  public void refreshPreservesTokenType() {
    String refreshed = jwtTokenUtil.refreshToken(jwtTokenUtil.generateCustomerToken(principal));

    assertTrue(jwtTokenUtil.isTokenOfType(refreshed, JWTTokenUtil.TOKEN_TYPE_CUSTOMER));
    assertFalse(jwtTokenUtil.validateToken(refreshed, principal, JWTTokenUtil.TOKEN_TYPE_ADMIN));
  }

  @Test
  public void forgedTypeClaimWithWrongKeyIsRejected() {
    Map<String, Object> claims = new HashMap<>();
    claims.put(JWTTokenUtil.CLAIM_KEY_TOKEN_TYPE, JWTTokenUtil.TOKEN_TYPE_ADMIN);
    Date now = new Date();
    String token = Jwts.builder()
        .setClaims(claims)
        .setSubject(USERNAME)
        .setIssuedAt(now)
        .setExpiration(new Date(now.getTime() + 3600_000L))
        .signWith(SignatureAlgorithm.HS512, "enp6enp6enp6enp6enp6enp6enp6enp6enp6enp6enp6enp6enp6enp6enp6enp6enp6enp6enp6enp6enp6enp6enp6enp6")
        .compact();

    try {
      jwtTokenUtil.validateToken(token, principal, JWTTokenUtil.TOKEN_TYPE_ADMIN);
      org.junit.Assert.fail("token signed with another key must not validate");
    } catch (io.jsonwebtoken.SignatureException expected) {
      // expected
    }
  }
}
