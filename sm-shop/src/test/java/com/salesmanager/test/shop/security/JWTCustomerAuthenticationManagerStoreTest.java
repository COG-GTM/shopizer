package com.salesmanager.test.shop.security;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.Collections;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.util.ReflectionTestUtils;

import com.salesmanager.shop.store.security.JWTTokenUtil;
import com.salesmanager.shop.store.security.customer.JWTCustomerAuthenticationManager;
import com.salesmanager.shop.store.security.customer.JWTCustomerServicesImpl;
import com.salesmanager.shop.store.security.user.JWTUser;

public class JWTCustomerAuthenticationManagerStoreTest {

	private static final String USERNAME = "alice@example.com";

	private JWTTokenUtil jwtTokenUtil;
	private JWTCustomerServicesImpl customerDetailsService;
	private JWTCustomerAuthenticationManager authenticationManager;

	@Before
	public void setUp() {
		SecurityContextHolder.clearContext();
		jwtTokenUtil = new JWTTokenUtil();
		ReflectionTestUtils.setField(jwtTokenUtil, "secret", "c2hvcGl6ZXJ0ZXN0c2VjcmV0");
		ReflectionTestUtils.setField(jwtTokenUtil, "expiration", 3600L);

		customerDetailsService = mock(JWTCustomerServicesImpl.class);
		authenticationManager = new JWTCustomerAuthenticationManager();
		authenticationManager.setTokenHeader("Authorization");
		ReflectionTestUtils.setField(authenticationManager, "jwtTokenUtil", jwtTokenUtil);
		ReflectionTestUtils.setField(authenticationManager, "jwtCustomerDetailsService", customerDetailsService);
	}

	@After
	public void tearDown() {
		SecurityContextHolder.clearContext();
	}

	@Test
	public void authenticatesWithCustomerDetailsResolvedInRequestStore() throws Exception {
		JWTUser tokenUser = user(1L, "GLOBEX");
		JWTUser storeUser = user(1L, "GLOBEX");
		when(customerDetailsService.loadUserByUsername(USERNAME, "GLOBEX")).thenReturn(storeUser);

		MockHttpServletRequest request = request(jwtTokenUtil.generateToken(tokenUser));
		request.setParameter("store", "GLOBEX");

		Authentication authentication =
				authenticationManager.attemptAuthentication(request, new MockHttpServletResponse());

		assertNotNull(authentication);
		assertEquals(storeUser, authentication.getPrincipal());
		verify(customerDetailsService).loadUserByUsername(USERNAME, "GLOBEX");
		verify(customerDetailsService, never()).loadUserByUsername(USERNAME);
	}

	@Test
	public void rejectsTokenWhenRequestDefaultsToAnotherStore() throws Exception {
		JWTUser tokenUser = user(1L, "GLOBEX");
		JWTUser defaultStoreUser = user(2L, "DEFAULT");
		when(customerDetailsService.loadUserByUsername(USERNAME, "DEFAULT")).thenReturn(defaultStoreUser);

		Authentication authentication = authenticationManager.attemptAuthentication(
				request(jwtTokenUtil.generateToken(tokenUser)), new MockHttpServletResponse());

		assertNull(authentication);
		verify(customerDetailsService).loadUserByUsername(USERNAME, "DEFAULT");
		verify(customerDetailsService, never()).loadUserByUsername(USERNAME);
	}

	private MockHttpServletRequest request(String token) {
		MockHttpServletRequest request = new MockHttpServletRequest();
		request.addHeader("Authorization", "Bearer " + token);
		return request;
	}

	private JWTUser user(Long id, String storeCode) {
		JWTUser user = new JWTUser(id, USERNAME, "Alice", "Doe", USERNAME, "x",
				Collections.emptyList(), true, null);
		user.setStoreCode(storeCode);
		return user;
	}
}
