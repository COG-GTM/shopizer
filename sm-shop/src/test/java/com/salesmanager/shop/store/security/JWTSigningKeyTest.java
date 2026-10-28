package com.salesmanager.shop.store.security;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.nio.charset.StandardCharsets;
import java.util.Collections;

import javax.crypto.spec.SecretKeySpec;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.test.util.ReflectionTestUtils;

import com.salesmanager.shop.store.security.admin.JWTAdminAuthenticationManager;
import com.salesmanager.shop.store.security.customer.JWTCustomerAuthenticationManager;
import com.salesmanager.shop.store.security.user.JWTUser;

import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.SignatureException;

class JWTSigningKeyTest {

	private static final String USERNAME = "user@example.com";
	private static final String STRONG_SECRET = "ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789+/";

	@AfterEach
	void clearSecurityContext() {
		SecurityContextHolder.clearContext();
	}

	@Test
	void secretLengthIsMeasuredInUtf8Bytes() {
		assertNotNull(tokenUtil("a".repeat(64)));
		assertNotNull(tokenUtil("é".repeat(32)));
		assertThrows(IllegalStateException.class, () -> tokenUtil("é".repeat(31) + "a"));
	}

	@Test
	void signingKeyUsesRawUtf8BytesAndHs512() {
		JWTTokenUtil util = tokenUtil(STRONG_SECRET);
		String token = util.generateToken(user(USERNAME));
		SecretKeySpec key = (SecretKeySpec) ReflectionTestUtils.getField(util, "signingKey");

		assertArrayEquals(STRONG_SECRET.getBytes(StandardCharsets.UTF_8), key.getEncoded());
		assertEquals("HS512", Jwts.parser().setSigningKey(
				new SecretKeySpec(STRONG_SECRET.getBytes(StandardCharsets.UTF_8), "HmacSHA512"))
				.parseClaimsJws(token).getHeader().getAlgorithm());
		assertEquals(USERNAME, Jwts.parser().setSigningKey(
				new SecretKeySpec(STRONG_SECRET.getBytes(StandardCharsets.UTF_8), "HmacSHA512"))
				.parseClaimsJws(token).getBody().getSubject());
	}

	@Test
	void instancesSharingSecretAcceptEachOthersTokens() {
		JWTTokenUtil first = tokenUtil(STRONG_SECRET);
		JWTTokenUtil second = tokenUtil(STRONG_SECRET);
		String tokenFromFirst = first.generateToken(user(USERNAME));
		String tokenFromSecond = second.generateToken(user("other@example.com"));

		assertEquals(USERNAME, second.getUsernameFromToken(tokenFromFirst));
		assertEquals("other@example.com", first.getUsernameFromToken(tokenFromSecond));
	}

	@Test
	void refreshedTokenKeepsSubjectAndValidatesOnlyMatchingUser() {
		JWTTokenUtil util = tokenUtil(STRONG_SECRET);
		String token = util.generateToken(user(USERNAME));
		String refreshed = util.refreshToken(token);

		assertEquals(USERNAME, util.getUsernameFromToken(refreshed));
		assertTrue(util.validateToken(refreshed, user(USERNAME)));
		assertFalse(util.validateToken(refreshed, user("other@example.com")));
	}

	@Test
	void tamperedPayloadIsRejected() {
		JWTTokenUtil util = tokenUtil(STRONG_SECRET);
		String[] segments = util.generateToken(user(USERNAME)).split("\\.");
		String changedPayload = java.util.Base64.getUrlEncoder().withoutPadding()
				.encodeToString("{\"sub\":\"forged@example.com\"}".getBytes(StandardCharsets.UTF_8));

		assertThrows(SignatureException.class,
				() -> util.getUsernameFromToken(segments[0] + "." + changedPayload + "." + segments[2]));
	}

	@Test
	void adminManagerRejectsMalformedBearerWithoutLoadingUser() throws Exception {
		UserDetailsService users = mock(UserDetailsService.class);
		JWTAdminAuthenticationManager manager = new JWTAdminAuthenticationManager();
		ReflectionTestUtils.setField(manager, "jwtTokenUtil", tokenUtil(STRONG_SECRET));
		ReflectionTestUtils.setField(manager, "jwtAdminDetailsService", users);
		manager.setTokenHeader("Authorization");

		assertNull(manager.attemptAuthentication(bearer("not-a-jwt"), new MockHttpServletResponse()));
		verify(users, never()).loadUserByUsername(anyString());
	}

	@Test
	void customerManagerRejectsMalformedBearerWithoutLoadingUser() throws Exception {
		UserDetailsService users = mock(UserDetailsService.class);
		JWTCustomerAuthenticationManager manager = new JWTCustomerAuthenticationManager();
		ReflectionTestUtils.setField(manager, "jwtTokenUtil", tokenUtil(STRONG_SECRET));
		ReflectionTestUtils.setField(manager, "jwtCustomerDetailsService", users);
		manager.setTokenHeader("Authorization");

		assertNull(manager.attemptAuthentication(bearer("not-a-jwt"), new MockHttpServletResponse()));
		verify(users, never()).loadUserByUsername(anyString());
	}

	@Test
	void adminManagerAuthenticatesValidBearer() throws Exception {
		assertValidAuthentication(true);
	}

	@Test
	void customerManagerAuthenticatesValidBearer() throws Exception {
		assertValidAuthentication(false);
	}

	private static void assertValidAuthentication(boolean admin) throws Exception {
		UserDetailsService users = mock(UserDetailsService.class);
		UserDetails user = user(USERNAME);
		when(users.loadUserByUsername(USERNAME)).thenReturn(user);
		JWTTokenUtil util = tokenUtil(STRONG_SECRET);
		String token = util.generateToken(user);

		Authentication authentication;
		if (admin) {
			JWTAdminAuthenticationManager manager = new JWTAdminAuthenticationManager();
			ReflectionTestUtils.setField(manager, "jwtTokenUtil", util);
			ReflectionTestUtils.setField(manager, "jwtAdminDetailsService", users);
			manager.setTokenHeader("Authorization");
			authentication = manager.attemptAuthentication(bearer(token), new MockHttpServletResponse());
		} else {
			JWTCustomerAuthenticationManager manager = new JWTCustomerAuthenticationManager();
			ReflectionTestUtils.setField(manager, "jwtTokenUtil", util);
			ReflectionTestUtils.setField(manager, "jwtCustomerDetailsService", users);
			manager.setTokenHeader("Authorization");
			authentication = manager.attemptAuthentication(bearer(token), new MockHttpServletResponse());
		}

		assertNotNull(authentication);
		assertEquals(USERNAME, ((UserDetails) authentication.getPrincipal()).getUsername());
		verify(users).loadUserByUsername(USERNAME);
	}

	private static JWTUser user(String username) {
		return new JWTUser(1L, username, "First", "Last", username, "password", Collections.emptyList(), true, null);
	}

	private static JWTTokenUtil tokenUtil(String secret) {
		JWTTokenUtil util = new JWTTokenUtil();
		ReflectionTestUtils.setField(util, "secret", secret);
		ReflectionTestUtils.setField(util, "expiration", 604800L);
		util.initSigningKey();
		return util;
	}

	private static MockHttpServletRequest bearer(String token) {
		MockHttpServletRequest request = new MockHttpServletRequest();
		request.addHeader("Authorization", "Bearer " + token);
		return request;
	}
}
