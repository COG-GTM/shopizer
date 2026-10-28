package com.salesmanager.test.shop.util;

import static org.junit.Assert.assertEquals;

import org.junit.Test;

import com.salesmanager.shop.utils.EmailTemplatesUtils;

public class ContactEmailDisplayNameEdgeCasesTest {

	@Test
	public void leavesExactly64CharactersUnchanged() {
		String name = repeated('a', 64);
		assertEquals(name, EmailTemplatesUtils.toDisplayName(name));
	}

	@Test
	public void abbreviates65CharactersTo64EndingInEllipsis() {
		String result = EmailTemplatesUtils.toDisplayName(repeated('a', 65));
		assertEquals(64, result.length());
		assertEquals("...", result.substring(result.length() - 3));
	}

	@Test
	public void normalizesEmptyAndWhitespaceOnlyValuesToEmpty() {
		assertEquals("", EmailTemplatesUtils.toDisplayName(""));
		assertEquals("", EmailTemplatesUtils.toDisplayName("  \r\n\t "));
	}

	@Test
	public void collapsesTabsControlsAndUnicodeSeparatorsToSingleSpaces() {
		assertEquals("one two three four five",
				EmailTemplatesUtils.toDisplayName("one\ttwo\u000bthree\ffour\u2029five"));
	}

	private String repeated(char value, int count) {
		StringBuilder result = new StringBuilder();
		for (int i = 0; i < count; i++) {
			result.append(value);
		}
		return result.toString();
	}
}
