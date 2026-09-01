package com.salesmanager.test.utils;

import static org.junit.Assert.assertEquals;

import org.junit.Test;

import com.salesmanager.core.business.modules.utils.EncryptionImpl;

public class EncryptionImplTest {

	private static final String TEST_KEY = "unit-test-only-aes-key-012345678";

	@Test
	public void encryptAndDecryptRoundTrip() throws Exception {
		EncryptionImpl encryption = new EncryptionImpl();
		encryption.setSecretKey(TEST_KEY);

		String value = "payment credentials: café";
		assertEquals(value, encryption.decrypt(encryption.encrypt(value)));
	}

	@Test(expected = IllegalArgumentException.class)
	public void rejectsLegacyDefaultKey() {
		new EncryptionImpl().setSecretKey("7070200000000007");
	}

	@Test(expected = IllegalArgumentException.class)
	public void rejectsBlankKey() {
		new EncryptionImpl().setSecretKey("");
	}

	@Test(expected = IllegalArgumentException.class)
	public void rejectsShortKey() {
		new EncryptionImpl().setSecretKey("1234567890");
	}

	@Test(expected = IllegalStateException.class)
	public void encryptWithoutKeyFails() throws Exception {
		new EncryptionImpl().encrypt("value");
	}
}
