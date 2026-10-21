package com.salesmanager.shop.store.security.admin;

import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.Collections;
import java.util.Date;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.test.util.ReflectionTestUtils;

import com.salesmanager.shop.store.security.JWTTokenUtil;
import com.salesmanager.shop.store.security.user.JWTUser;

import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.SignatureAlgorithm;

public class JWTAdminAuthenticationManagerTest {

	private static final String SECRET =
			"MDEyMzQ1Njc4OWFiY2RlZjAxMjM0NTY3ODlhYmNkZWYwMTIzNDU2Nzg5YWJjZGVmMDEyMzQ1Njc4OWFiY2RlZg==";
	private static final String USERNAME = "admin@example.com";

	private JWTAdminAuthenticationManager manager;
	private UserDetailsService userDetailsService;

	@Before
	public void setUp() {
		SecurityContextHolder.clearContext();
		manager = new JWTAdminAuthenticationManager();
		manager.setTokenHeader("Authorization");
		JWTTokenUtil tokenUtil = new JWTTokenUtil();
		ReflectionTestUtils.setField(tokenUtil, "secret", SECRET);
		ReflectionTestUtils.setField(tokenUtil, "expiration", 604800L);
		ReflectionTestUtils.setField(manager, "jwtTokenUtil", tokenUtil);
		userDetailsService = org.mockito.Mockito.mock(UserDetailsService.class);
		ReflectionTestUtils.setField(manager, "jwtAdminDetailsService", userDetailsService);
	}

	@After
	public void clearSecurityContext() {
		SecurityContextHolder.clearContext();
	}

	@Test
	public void forgedTokenReturnsNullWithoutLoadingUser() throws Exception {
		MockHttpServletRequest request = request(tokenWithSecret("aSecret"));

		assertNull(manager.attemptAuthentication(request, new MockHttpServletResponse()));
		verify(userDetailsService, never()).loadUserByUsername(USERNAME);
	}

	@Test
	public void authenticateRequestIgnoresForgedToken() throws Exception {
		manager.authenticateRequest(request(tokenWithSecret("aSecret")), new MockHttpServletResponse());

		assertNull(SecurityContextHolder.getContext().getAuthentication());
		verify(userDetailsService, never()).loadUserByUsername(USERNAME);
	}

	@Test
	public void authenticatesMatchingUserWithValidToken() throws Exception {
		JWTUser user = jwtUser();
		when(userDetailsService.loadUserByUsername(USERNAME)).thenReturn(user);
		JWTTokenUtil tokenUtil = (JWTTokenUtil) ReflectionTestUtils.getField(manager, "jwtTokenUtil");

		assertNotNull(manager.attemptAuthentication(request(tokenUtil.generateToken(user)),
				new MockHttpServletResponse()));
	}

	private MockHttpServletRequest request(String token) {
		MockHttpServletRequest request = new MockHttpServletRequest();
		request.addHeader("Authorization", "Bearer " + token);
		return request;
	}

	private String tokenWithSecret(String secret) {
		Date now = new Date();
		return Jwts.builder()
				.setSubject(USERNAME)
				.setAudience("api")
				.setIssuedAt(now)
				.setExpiration(new Date(now.getTime() + 86400000L))
				.signWith(SignatureAlgorithm.HS512, secret)
				.compact();
	}

	private JWTUser jwtUser() {
		return new JWTUser(1L, USERNAME, "Admin", "User", USERNAME, "password",
				Collections.emptyList(), true, null);
	}
}
