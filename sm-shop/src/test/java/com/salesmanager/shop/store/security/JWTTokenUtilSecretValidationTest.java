package com.salesmanager.shop.store.security;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.springframework.test.util.ReflectionTestUtils;

public class JWTTokenUtilSecretValidationTest {

	private String originalJwtSecret;

	@Before
	public void saveJwtSecretProperty() {
		originalJwtSecret = System.getProperty("jwt.secret");
	}

	@After
	public void restoreJwtSecretProperty() {
		if (originalJwtSecret == null) {
			System.clearProperty("jwt.secret");
		} else {
			System.setProperty("jwt.secret", originalJwtSecret);
		}
	}

	@Test
	public void rejects63ByteSecret() {
		assertInvalidSecret("a".repeat(63));
	}

	@Test
	public void acceptsExactly64ByteSecret() {
		JWTTokenUtil tokenUtil = tokenUtil("a".repeat(64));

		tokenUtil.validateSecret();
	}

	@Test
	public void countsMultibyteSecretAsUtf8Bytes() {
		assertInvalidSecret("€".repeat(21));

		JWTTokenUtil tokenUtil = tokenUtil("€".repeat(22));
		tokenUtil.validateSecret();
	}

	@Test
	public void usesSystemPropertyWhenFieldIsNull() {
		String systemSecret = "system-property-secret-" + "s".repeat(64);
		System.setProperty("jwt.secret", systemSecret);
		JWTTokenUtil tokenUtil = tokenUtil(null);

		tokenUtil.validateSecret();

		assertEquals(systemSecret, ReflectionTestUtils.getField(tokenUtil, "secret"));
	}

	@Test
	public void usesSystemPropertyWhenFieldIsBlank() {
		String systemSecret = "system-property-secret-" + "s".repeat(64);
		System.setProperty("jwt.secret", systemSecret);
		JWTTokenUtil tokenUtil = tokenUtil("  ");

		tokenUtil.validateSecret();

		assertEquals(systemSecret, ReflectionTestUtils.getField(tokenUtil, "secret"));
	}

	@Test
	public void rejectsShortSystemPropertyFallback() {
		System.setProperty("jwt.secret", "short");

		assertInvalidSecret(" ");
	}

	@Test
	public void validFieldTakesPrecedenceOverShortSystemProperty() {
		String fieldSecret = "f".repeat(64);
		System.setProperty("jwt.secret", "short");
		JWTTokenUtil tokenUtil = tokenUtil(fieldSecret);

		tokenUtil.validateSecret();

		assertEquals(fieldSecret, ReflectionTestUtils.getField(tokenUtil, "secret"));
	}

	private JWTTokenUtil tokenUtil(String secret) {
		JWTTokenUtil tokenUtil = new JWTTokenUtil();
		ReflectionTestUtils.setField(tokenUtil, "secret", secret);
		return tokenUtil;
	}

	private void assertInvalidSecret(String secret) {
		JWTTokenUtil tokenUtil = tokenUtil(secret);
		try {
			tokenUtil.validateSecret();
			fail("Expected invalid JWT secret to be rejected");
		} catch (IllegalStateException exception) {
			assertTrue(exception.getMessage().contains("JWT signing secret"));
		}
	}
}
