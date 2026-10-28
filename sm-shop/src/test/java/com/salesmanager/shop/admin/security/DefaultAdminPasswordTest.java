package com.salesmanager.shop.admin.security;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.HashSet;
import java.util.Set;

import org.junit.jupiter.api.Test;

class DefaultAdminPasswordTest {

	private static final String ALPHABET = "ABCDEFGHJKLMNPQRSTUVWXYZabcdefghijkmnopqrstuvwxyz23456789";

	@Test
	void enforcesConfiguredPasswordLengthBoundary() {
		assertEquals(12, DefaultAdminPassword.MIN_CONFIGURED_LENGTH);
		assertFalse(DefaultAdminPassword.isAcceptable("StrongPass1"));
		assertTrue(DefaultAdminPassword.isAcceptable("StrongPass12"));
	}

	@Test
	void rejectsWellKnownAndBlankPasswords() {
		for (String password : new String[] {
				"   password   ",
				"123456789012",
				"QWERTYUIOP12",
				"Admin@Shopizer.com",
				null,
				"",
				"   "
		}) {
			assertFalse(DefaultAdminPassword.isAcceptable(password));
		}
	}

	@Test
	void generatesUniqueAcceptablePasswordsFromTheDeclaredAlphabet() {
		Set<String> generated = new HashSet<>();
		String ambiguousCharacters = "0O1lI";

		for (int i = 0; i < 1000; i++) {
			String password = DefaultAdminPassword.generate();

			assertEquals(DefaultAdminPassword.GENERATED_LENGTH, password.length());
			assertTrue(password.chars().allMatch(character -> ALPHABET.indexOf(character) >= 0));
			assertTrue(password.chars().noneMatch(character -> ambiguousCharacters.indexOf(character) >= 0));
			assertTrue(DefaultAdminPassword.isAcceptable(password));
			assertTrue(generated.add(password));
		}
	}
}
