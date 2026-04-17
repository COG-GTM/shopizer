package com.salesmanager.core.business.modules.utils;

import java.security.SecureRandom;
import javax.crypto.Cipher;
import javax.crypto.spec.IvParameterSpec;
import javax.crypto.spec.SecretKeySpec;

import org.apache.commons.lang3.StringUtils;

import com.salesmanager.core.modules.utils.Encryption;

public final class EncryptionImpl implements Encryption {

	private static final int IV_LENGTH = 16;
	private static final String LEGACY_IV = "fedcba9876543210";
	private final static String KEY_SPEC = "AES";
	private final static String CYPHER_SPEC = "AES/CBC/PKCS5Padding";
	private static final SecureRandom SECURE_RANDOM = new SecureRandom();

    private String  secretKey;

	@Override
	public String encrypt(String value) throws Exception {

		// Generate a random IV for each encryption operation
		byte[] iv = new byte[IV_LENGTH];
		SECURE_RANDOM.nextBytes(iv);
		IvParameterSpec ivSpec = new IvParameterSpec(iv);

		Cipher cipher = Cipher.getInstance(CYPHER_SPEC);
		SecretKeySpec keySpec = new SecretKeySpec(secretKey.getBytes(), KEY_SPEC);
		cipher.init(Cipher.ENCRYPT_MODE, keySpec, ivSpec);
		byte[] encrypted = cipher.doFinal(value.getBytes());

		// Prepend IV to ciphertext
		byte[] combined = new byte[IV_LENGTH + encrypted.length];
		System.arraycopy(iv, 0, combined, 0, IV_LENGTH);
		System.arraycopy(encrypted, 0, combined, IV_LENGTH, encrypted.length);

		return bytesToHex(combined);
	}

	@Override
	public String decrypt(String value) throws Exception {

		if (StringUtils.isBlank(value))
			throw new Exception("Nothing to decrypt");

		byte[] combined = hexToBytes(value);

		if (combined == null || combined.length <= IV_LENGTH) {
			throw new Exception("Invalid encrypted data: too short");
		}

		// Try new format first: IV (16 bytes) prepended to ciphertext
		byte[] iv = new byte[IV_LENGTH];
		System.arraycopy(combined, 0, iv, 0, IV_LENGTH);
		byte[] ciphertext = new byte[combined.length - IV_LENGTH];
		System.arraycopy(combined, IV_LENGTH, ciphertext, 0, ciphertext.length);

		try {
			Cipher cipher = Cipher.getInstance(CYPHER_SPEC);
			SecretKeySpec keySpec = new SecretKeySpec(secretKey.getBytes(), KEY_SPEC);
			IvParameterSpec ivSpec = new IvParameterSpec(iv);
			cipher.init(Cipher.DECRYPT_MODE, keySpec, ivSpec);
			byte[] outText = cipher.doFinal(ciphertext);
			return new String(outText);
		} catch (Exception e) {
			// Fall back to legacy format: static IV, no prepended IV in ciphertext
			Cipher cipher = Cipher.getInstance(CYPHER_SPEC);
			SecretKeySpec keySpec = new SecretKeySpec(secretKey.getBytes(), KEY_SPEC);
			IvParameterSpec ivSpec = new IvParameterSpec(LEGACY_IV.getBytes());
			cipher.init(Cipher.DECRYPT_MODE, keySpec, ivSpec);
			byte[] outText = cipher.doFinal(combined);
			return new String(outText);
		}
	}


	private String bytesToHex(byte[] data) {
		if (data == null) {
			return null;
		} else {
			int len = data.length;
			String str = "";
			for (byte datum : data) {
				if ((datum & 0xFF) < 16) {
					str = str + "0"
							+ Integer.toHexString(datum & 0xFF);
				} else {
					str = str + Integer.toHexString(datum & 0xFF);
				}

			}
			return str;
		}
	}

	private static byte[] hexToBytes(String str) {
		if (str == null) {
			return null;
		} else if (str.length() < 2) {
			return null;
		} else {
			int len = str.length() / 2;
			byte[] buffer = new byte[len];
			for (int i = 0; i < len; i++) {
				buffer[i] = (byte) Integer.parseInt(str.substring(i * 2,
						i * 2 + 2), 16);
			}
			return buffer;
		}
	}

	public String getSecretKey() {
		return secretKey;
	}

	public void setSecretKey(String secretKey) {
		this.secretKey = secretKey;
	}

}
