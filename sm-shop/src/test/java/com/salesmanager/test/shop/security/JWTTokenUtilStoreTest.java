package com.salesmanager.test.shop.security;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import java.util.Collections;

import org.junit.Before;
import org.junit.Test;
import org.springframework.test.util.ReflectionTestUtils;

import com.salesmanager.shop.store.security.JWTTokenUtil;
import com.salesmanager.shop.store.security.user.JWTUser;

public class JWTTokenUtilStoreTest {

	private JWTTokenUtil jwtTokenUtil;

	@Before
	public void setUp() {
		jwtTokenUtil = new JWTTokenUtil();
		ReflectionTestUtils.setField(jwtTokenUtil, "secret", "c2hvcGl6ZXJ0ZXN0c2VjcmV0");
		ReflectionTestUtils.setField(jwtTokenUtil, "expiration", 3600L);
	}

	private JWTUser user(Long id, String storeCode) {
		JWTUser user = new JWTUser(id, "alice@example.com", "Alice", "Doe", "alice@example.com", "x",
				Collections.emptyList(), true, null);
		user.setStoreCode(storeCode);
		return user;
	}

	@Test
	public void customerTokenCarriesStoreAndValidatesOnlyInThatStore() {
		String token = jwtTokenUtil.generateToken(user(1L, "ACME"));

		assertEquals("ACME", jwtTokenUtil.getStoreFromToken(token));
		assertTrue(jwtTokenUtil.validateToken(token, user(1L, "ACME")));
		assertFalse(jwtTokenUtil.validateToken(token, user(2L, "GLOBEX")));
	}

	@Test
	public void tokenWithoutStoreIsRejectedForStoreScopedCustomer() {
		String token = jwtTokenUtil.generateToken(user(1L, null));

		assertNull(jwtTokenUtil.getStoreFromToken(token));
		assertFalse(jwtTokenUtil.validateToken(token, user(1L, "ACME")));
		assertTrue(jwtTokenUtil.validateToken(token, user(1L, null)));
	}

	@Test
	public void refreshedTokenKeepsStore() {
		String token = jwtTokenUtil.refreshToken(jwtTokenUtil.generateToken(user(1L, "ACME")));

		assertTrue(jwtTokenUtil.isTokenForStore(token, user(1L, "ACME")));
		assertFalse(jwtTokenUtil.isTokenForStore(token, user(2L, "GLOBEX")));
	}
}
