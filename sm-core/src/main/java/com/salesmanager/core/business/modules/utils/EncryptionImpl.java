package com.salesmanager.core.business.modules.utils;

import java.nio.charset.StandardCharsets;

import javax.crypto.Cipher;
import javax.crypto.spec.IvParameterSpec;
import javax.crypto.spec.SecretKeySpec;

import org.apache.commons.lang3.StringUtils;

import com.salesmanager.core.modules.utils.Encryption;

public final class EncryptionImpl implements Encryption {
	
	private final static String IV_P = "fedcba9876543210";
	private final static String KEY_SPEC = "AES";
	private final static String CYPHER_SPEC = "AES/CBC/PKCS5Padding";
	private static final String LEGACY_DEFAULT_KEY = "7070200000000007";
	private static final int[] VALID_KEY_LENGTHS = {16, 24, 32};
	


    private String  secretKey;



	@Override
	public String encrypt(String value) throws Exception {

		
		// value = StringUtils.rightPad(value, 16,"*");
		// Cipher cipher = Cipher.getInstance("AES/CBC/NoPadding");
		// NEED TO UNDERSTAND WHY PKCS5Padding DOES NOT WORK
		Cipher cipher = Cipher.getInstance(CYPHER_SPEC);
		SecretKeySpec keySpec = keySpec();
		IvParameterSpec ivSpec = new IvParameterSpec(IV_P
				.getBytes(StandardCharsets.UTF_8));
		cipher.init(Cipher.ENCRYPT_MODE, keySpec, ivSpec);
		byte[] inpbytes = value.getBytes(StandardCharsets.UTF_8);
		byte[] encrypted = cipher.doFinal(inpbytes);
		return bytesToHex(encrypted);
		
		
	}

	@Override
	public String decrypt(String value) throws Exception {

		
		if (StringUtils.isBlank(value))
			throw new Exception("Nothing to encrypt");

		// NEED TO UNDERSTAND WHY PKCS5Padding DOES NOT WORK
		// Cipher cipher = Cipher.getInstance("AES/CBC/NoPadding");
		Cipher cipher = Cipher.getInstance(CYPHER_SPEC);
		SecretKeySpec keySpec = keySpec();
		IvParameterSpec ivSpec = new IvParameterSpec(IV_P
				.getBytes(StandardCharsets.UTF_8));
		cipher.init(Cipher.DECRYPT_MODE, keySpec, ivSpec);
		byte[] outText;
		outText = cipher.doFinal(hexToBytes(value));
		return new String(outText, StandardCharsets.UTF_8);
		
		
	}

	private SecretKeySpec keySpec() {
		if (secretKey == null) {
			throw new IllegalStateException("Encryption secretKey is not configured");
		}
		return new SecretKeySpec(secretKey.getBytes(StandardCharsets.UTF_8), KEY_SPEC);
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
		if (StringUtils.isBlank(secretKey)) {
			throw new IllegalArgumentException("secretKey must not be blank");
		}
		if (LEGACY_DEFAULT_KEY.equals(secretKey)) {
			throw new IllegalArgumentException(
					"secretKey must not be the publicly known default value; set SECRET_KEY to a unique key");
		}
		int keyLength = secretKey.getBytes(StandardCharsets.UTF_8).length;
		boolean validLength = false;
		for (int validKeyLength : VALID_KEY_LENGTHS) {
			if (keyLength == validKeyLength) {
				validLength = true;
				break;
			}
		}
		if (!validLength) {
			throw new IllegalArgumentException("secretKey must be exactly 16, 24 or 32 UTF-8 bytes");
		}
		this.secretKey = secretKey;
	}

}
