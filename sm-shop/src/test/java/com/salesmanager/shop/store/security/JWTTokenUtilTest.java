package com.salesmanager.shop.store.security;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

import java.nio.charset.StandardCharsets;
import java.util.Collections;
import java.util.Date;

import javax.crypto.spec.SecretKeySpec;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.userdetails.User;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.test.util.ReflectionTestUtils;

import com.salesmanager.shop.store.security.admin.JWTAdminAuthenticationManager;
import com.salesmanager.shop.store.security.customer.JWTCustomerAuthenticationManager;

import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.SignatureAlgorithm;
import io.jsonwebtoken.SignatureException;
import io.jsonwebtoken.UnsupportedJwtException;

public class JWTTokenUtilTest {

	static final String OLD_DEFAULT_SECRET = "aSecret";
	static final String STRONG_SECRET = "0123456789abcdefghijklmnopqrstuvwxyzABCDEFGHIJKLMNOPQRSTUVWXYZ-_";
	static final String ADMIN = "admin@shopizer.com";

	@AfterEach
	void clearContext() {
		SecurityContextHolder.clearContext();
	}

	@Test
	void missingSecretFailsStartup() {
		assertThrows(IllegalStateException.class, () -> tokenUtil(null));
		assertThrows(IllegalStateException.class, () -> tokenUtil(""));
		assertThrows(IllegalStateException.class, () -> tokenUtil("   "));
	}

	@Test
	void shortSecretFailsStartup() {
		assertThrows(IllegalStateException.class, () -> tokenUtil(OLD_DEFAULT_SECRET));
		assertThrows(IllegalStateException.class, () -> tokenUtil(STRONG_SECRET.substring(1)));
	}

	@Test
	void issuedTokenRoundTrips() {
		JWTTokenUtil util = tokenUtil(STRONG_SECRET);
		String token = util.generateToken(new User(ADMIN, "x", Collections.emptyList()));
		assertEquals(ADMIN, util.getUsernameFromToken(token));
	}

	@Test
	void tokenForgedWithOldDefaultSecretIsRejected() {
		JWTTokenUtil util = tokenUtil(STRONG_SECRET);
		assertThrows(SignatureException.class, () -> util.getUsernameFromToken(forge(OLD_DEFAULT_SECRET)));
		String rawKeyForgery = Jwts.builder().setSubject(ADMIN).setIssuedAt(new Date())
				.setExpiration(new Date(System.currentTimeMillis() + 3_600_000L))
				.signWith(SignatureAlgorithm.HS512,
						new SecretKeySpec(OLD_DEFAULT_SECRET.getBytes(StandardCharsets.UTF_8), "HmacSHA512"))
				.compact();
		assertThrows(SignatureException.class, () -> util.getUsernameFromToken(rawKeyForgery));
	}

	@Test
	void tokenSignedWithAnotherKeyIsRejected() {
		JWTTokenUtil util = tokenUtil(STRONG_SECRET);
		String other = tokenUtil(new StringBuilder(STRONG_SECRET).reverse().toString())
				.generateToken(new User(ADMIN, "x", Collections.emptyList()));
		assertThrows(SignatureException.class, () -> util.getUsernameFromToken(other));
	}

	@Test
	void unsignedTokenIsRejected() {
		JWTTokenUtil util = tokenUtil(STRONG_SECRET);
		String unsigned = Jwts.builder().setSubject(ADMIN)
				.setExpiration(new Date(System.currentTimeMillis() + 3_600_000L)).compact();
		assertThrows(UnsupportedJwtException.class, () -> util.getUsernameFromToken(unsigned));
	}

	@Test
	void adminManagerDoesNotAuthenticateForgedToken() throws Exception {
		UserDetailsService users = mock(UserDetailsService.class);
		JWTAdminAuthenticationManager manager = new JWTAdminAuthenticationManager();
		ReflectionTestUtils.setField(manager, "jwtTokenUtil", tokenUtil(STRONG_SECRET));
		ReflectionTestUtils.setField(manager, "jwtAdminDetailsService", users);
		manager.setTokenHeader("Authorization");

		assertNull(manager.attemptAuthentication(bearer(forge(OLD_DEFAULT_SECRET)), new MockHttpServletResponse()));
		verify(users, never()).loadUserByUsername(anyString());
	}

	@Test
	void customerManagerDoesNotAuthenticateForgedToken() throws Exception {
		UserDetailsService users = mock(UserDetailsService.class);
		JWTCustomerAuthenticationManager manager = new JWTCustomerAuthenticationManager();
		ReflectionTestUtils.setField(manager, "jwtTokenUtil", tokenUtil(STRONG_SECRET));
		ReflectionTestUtils.setField(manager, "jwtCustomerDetailsService", users);
		manager.setTokenHeader("Authorization");

		assertNull(manager.attemptAuthentication(bearer(forge(OLD_DEFAULT_SECRET)), new MockHttpServletResponse()));
		verify(users, never()).loadUserByUsername(anyString());
	}

	static String forge(String secret) {
		return Jwts.builder().setSubject(ADMIN).setAudience("api").setIssuedAt(new Date())
				.setExpiration(new Date(System.currentTimeMillis() + 3_600_000L))
				.signWith(SignatureAlgorithm.HS512, secret).compact();
	}

	private static MockHttpServletRequest bearer(String token) {
		MockHttpServletRequest request = new MockHttpServletRequest();
		request.addHeader("Authorization", "Bearer " + token);
		return request;
	}

	private static JWTTokenUtil tokenUtil(String secret) {
		JWTTokenUtil util = new JWTTokenUtil();
		ReflectionTestUtils.setField(util, "secret", secret);
		ReflectionTestUtils.setField(util, "expiration", 604800L);
		util.initSigningKey();
		return util;
	}
}
