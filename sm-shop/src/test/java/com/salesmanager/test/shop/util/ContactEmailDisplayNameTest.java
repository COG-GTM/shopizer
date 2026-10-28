package com.salesmanager.test.shop.util;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

import com.salesmanager.shop.utils.EmailTemplatesUtils;

public class ContactEmailDisplayNameTest {

	@Test
	public void stripsLineBreaksAndControlCharacters() {
		String name = EmailTemplatesUtils.toDisplayName("Acme\r\nBcc: victim@example.com\u0000\u2028x");
		assertFalse(name.contains("\r"));
		assertFalse(name.contains("\n"));
		assertFalse(name.contains("\u0000"));
		assertFalse(name.contains("\u2028"));
		assertEquals("Acme Bcc: victim@example.com x", name);
	}

	@Test
	public void capsLength() {
		StringBuilder longName = new StringBuilder();
		for (int i = 0; i < 200; i++) {
			longName.append('a');
		}
		String name = EmailTemplatesUtils.toDisplayName(longName.toString());
		assertTrue(name.length() <= 64);
	}

	@Test
	public void keepsOrdinaryNames() {
		assertEquals("Jos\u00e9 O'Brien", EmailTemplatesUtils.toDisplayName("  Jos\u00e9   O'Brien "));
		assertNull(EmailTemplatesUtils.toDisplayName(null));
	}
}
