package com.salesmanager.shop.store.security;

import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import org.junit.Test;
import org.springframework.test.util.ReflectionTestUtils;

public class JWTTokenUtilTest {

	@Test
	public void rejectsNullSecret() {
		assertInvalidSecret(null);
	}

	@Test
	public void rejectsBlankSecret() {
		assertInvalidSecret("   ");
	}

	@Test
	public void rejectsShortSecret() {
		assertInvalidSecret("aSecret");
	}

	@Test
	public void accepts64ByteSecret() {
		JWTTokenUtil tokenUtil = new JWTTokenUtil();
		ReflectionTestUtils.setField(tokenUtil, "secret",
				"0123456789abcdefghijklmnopqrstuvwxyzABCDEFGHIJKLMNOPQRSTUVWXYZ!@");

		tokenUtil.validateSecret();
	}

	private void assertInvalidSecret(String secret) {
		JWTTokenUtil tokenUtil = new JWTTokenUtil();
		ReflectionTestUtils.setField(tokenUtil, "secret", secret);

		try {
			tokenUtil.validateSecret();
			fail("Expected invalid JWT secret to be rejected");
		} catch (IllegalStateException exception) {
			assertTrue(exception.getMessage().contains("JWT signing secret"));
		}
	}
}
