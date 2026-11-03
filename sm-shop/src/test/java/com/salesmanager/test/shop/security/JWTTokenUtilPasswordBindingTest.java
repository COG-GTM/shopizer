package com.salesmanager.test.shop.security;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import java.util.Collections;
import java.util.Date;

import org.junit.Before;
import org.junit.Test;
import org.springframework.test.util.ReflectionTestUtils;

import com.salesmanager.shop.store.security.JWTTokenUtil;
import com.salesmanager.shop.store.security.user.JWTUser;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.SignatureAlgorithm;

public class JWTTokenUtilPasswordBindingTest {

  private static final String SECRET = "test-secret";
  private static final String USERNAME = "admin@example.test";
  private static final long EXPIRATION_SECONDS = 3600L;

  private JWTTokenUtil tokenUtil;

  @Before
  public void setUp() {
    tokenUtil = new JWTTokenUtil();
    ReflectionTestUtils.setField(tokenUtil, "secret", SECRET);
    ReflectionTestUtils.setField(tokenUtil, "expiration", EXPIRATION_SECONDS);
  }

  @Test
  public void generatedAdminTokenCarriesPasswordChangeDateAndCustomerTokenDoesNot() {
    Date resetDate = new Date(1700000000000L);
    String adminToken = tokenUtil.generateToken(user(USERNAME, resetDate));

    assertEquals(resetDate.getTime(), ((Number) claims(adminToken).get("pwc")).longValue());

    String customerToken = tokenUtil.generateToken(user("customer@example.test", null));
    assertNull(claims(customerToken).get("pwc"));
    assertTrue(tokenUtil.validateToken(customerToken, user("customer@example.test", null)));
  }

  @Test
  public void boundTokenIsRejectedWhenPasswordChangeDateChangesOrDisappears() {
    Date resetDate = new Date(1700000000000L);
    String token = tokenUtil.generateToken(user(USERNAME, resetDate));

    assertFalse(tokenUtil.validateToken(token, user(USERNAME, new Date(resetDate.getTime() + 1000L))));
    assertFalse(tokenUtil.validateToken(token, user(USERNAME, null)));
    assertFalse(tokenUtil.validateToken(token, user("other@example.test", resetDate)));
  }

  @Test
  public void legacyTokenUsesIssuedAtForRefreshEligibility() {
    Date issuedAt = new Date(System.currentTimeMillis() - 10000L);
    String token = legacyToken(USERNAME, issuedAt);

    assertTrue(tokenUtil.validateToken(token, user(USERNAME, null)));
    assertTrue(tokenUtil.canTokenBeRefreshedWithGrace(token, null));
    Date resetBeforeIssue = new Date(issuedAt.getTime() - 5000L);
    assertTrue(tokenUtil.validateToken(token, user(USERNAME, resetBeforeIssue)));
    assertTrue(tokenUtil.canTokenBeRefreshedWithGrace(token, resetBeforeIssue));
    assertFalse(tokenUtil.canTokenBeRefreshedWithGrace(token, new Date()));
  }

  @Test
  public void refreshPreservesPasswordChangeBinding() {
    Date resetDate = new Date(1700000000000L);
    String token = tokenUtil.generateToken(user(USERNAME, resetDate));

    String refreshedToken = tokenUtil.refreshToken(token);

    assertEquals(resetDate.getTime(), ((Number) claims(refreshedToken).get("pwc")).longValue());
    assertTrue(tokenUtil.validateToken(refreshedToken, user(USERNAME, resetDate)));
    assertFalse(tokenUtil.validateToken(refreshedToken, user(USERNAME, new Date(resetDate.getTime() + 1000L))));
  }

  private JWTUser user(String username, Date resetDate) {
    return new JWTUser(1L, username, "Test", "User", username, "encoded-password",
        Collections.emptyList(), true, resetDate);
  }

  private Claims claims(String token) {
    return Jwts.parser().setSigningKey(SECRET).parseClaimsJws(token).getBody();
  }

  private String legacyToken(String username, Date issuedAt) {
    Date expiration = new Date(System.currentTimeMillis() + 3600000L);
    return Jwts.builder()
        .setSubject(username)
        .setAudience("api")
        .setIssuedAt(issuedAt)
        .setExpiration(expiration)
        .signWith(SignatureAlgorithm.HS512, SECRET)
        .compact();
  }
}
