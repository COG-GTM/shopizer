package com.salesmanager.test.shop.security;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.util.Collections;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.security.core.userdetails.User;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.test.util.ReflectionTestUtils;

import com.salesmanager.shop.store.security.JWTTokenUtil;

import io.jsonwebtoken.JwtException;

class JWTTokenUtilTest {

	private final UserDetails user = new User("admin@shopizer.com", "x", Collections.emptyList());

	private JWTTokenUtil tokenUtil;

	@BeforeEach
	void setUp() {
		tokenUtil = newTokenUtil("aSecret");
	}

	private JWTTokenUtil newTokenUtil(String secret) {
		JWTTokenUtil util = new JWTTokenUtil();
		ReflectionTestUtils.setField(util, "secret", secret);
		ReflectionTestUtils.setField(util, "expiration", 604800L);
		return util;
	}

	@Test
	void generatedTokenCarriesSubjectAndAudience() {
		String token = tokenUtil.generateToken(user);

		assertEquals("admin@shopizer.com", tokenUtil.getUsernameFromToken(token));
		assertEquals("api", tokenUtil.getAudienceFromToken(token));
	}

	@Test
	void refreshedTokenKeepsSubject() {
		String refreshed = tokenUtil.refreshToken(tokenUtil.generateToken(user));

		assertEquals("admin@shopizer.com", tokenUtil.getUsernameFromToken(refreshed));
		assertEquals("api", tokenUtil.getAudienceFromToken(refreshed));
	}

	@Test
	void tamperedTokenIsRejected() {
		String token = tokenUtil.generateToken(user);

		assertThrows(JwtException.class, () -> tokenUtil.getUsernameFromToken(token + "x"));
	}

	@Test
	void tokenSignedWithOtherSecretIsRejected() {
		String token = newTokenUtil("anotherSecret").generateToken(user);

		assertThrows(JwtException.class, () -> tokenUtil.getUsernameFromToken(token));
	}

	@Test
	void longSecretIsUsedDirectly() {
		JWTTokenUtil longSecretUtil = newTokenUtil("0123456789012345678901234567890123456789012345678901234567890123");
		String token = longSecretUtil.generateToken(user);

		assertEquals("admin@shopizer.com", longSecretUtil.getUsernameFromToken(token));
		assertNotEquals(token, tokenUtil.generateToken(user));
	}
}
