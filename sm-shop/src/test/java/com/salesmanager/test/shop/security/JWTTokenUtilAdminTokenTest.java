package com.salesmanager.test.shop.security;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import java.util.Collections;

import org.junit.Before;
import org.junit.Test;
import org.springframework.test.util.ReflectionTestUtils;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.salesmanager.shop.store.security.JWTTokenUtil;
import com.salesmanager.shop.store.security.user.JWTUser;

public class JWTTokenUtilAdminTokenTest {

	private JWTTokenUtil jwtTokenUtil;

	@Before
	public void setUp() {
		jwtTokenUtil = new JWTTokenUtil();
		ReflectionTestUtils.setField(jwtTokenUtil, "secret", "c2hvcGl6ZXJ0ZXN0c2VjcmV0");
		ReflectionTestUtils.setField(jwtTokenUtil, "expiration", 3600L);
	}

	@Test
	public void adminTokenHasNoStoreClaimAndRefreshPreservesIt() {
		JWTUser admin = user(null);
		String token = jwtTokenUtil.generateToken(admin);

		assertNull(jwtTokenUtil.getStoreFromToken(token));
		assertTrue(jwtTokenUtil.validateToken(token, admin));
		assertTrue(jwtTokenUtil.isTokenForStore(token, admin));

		String refreshedToken = jwtTokenUtil.refreshToken(token);
		assertNull(jwtTokenUtil.getStoreFromToken(refreshedToken));
		assertTrue(jwtTokenUtil.validateToken(refreshedToken, admin));
	}

	@Test
	public void storeCodeIsIgnoredWhenSerializingJwtUser() throws Exception {
		String json = new ObjectMapper().writeValueAsString(user("GLOBEX"));

		assertFalse(json.contains("storeCode"));
	}

	private JWTUser user(String storeCode) {
		JWTUser user = new JWTUser(1L, "admin@example.com", "Admin", "User", "admin@example.com", "x",
				Collections.emptyList(), true, null);
		user.setStoreCode(storeCode);
		return user;
	}
}
