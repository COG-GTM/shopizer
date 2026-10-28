package com.salesmanager.shop.admin.security;

import java.security.SecureRandom;
import java.util.Arrays;
import java.util.HashSet;
import java.util.Locale;
import java.util.Set;

import org.apache.commons.lang3.StringUtils;

/**
 * Initial password of the bootstrap super admin account created on an empty database.
 */
public final class DefaultAdminPassword {

	public static final int MIN_CONFIGURED_LENGTH = 12;

	static final int GENERATED_LENGTH = 24;

	private static final char[] ALPHABET =
			"ABCDEFGHJKLMNPQRSTUVWXYZabcdefghijkmnopqrstuvwxyz23456789".toCharArray();

	private static final Set<String> WELL_KNOWN = new HashSet<String>(Arrays.asList(
			"password", "password1", "password123", "admin", "administrator", "changeme",
			"shopizer", "admin@shopizer.com", "123456789012", "qwertyuiop12"));

	private static final SecureRandom RANDOM = new SecureRandom();

	private DefaultAdminPassword() {
	}

	public static boolean isAcceptable(String password) {
		return !StringUtils.isBlank(password)
				&& password.length() >= MIN_CONFIGURED_LENGTH
				&& !WELL_KNOWN.contains(password.trim().toLowerCase(Locale.ROOT));
	}

	public static String generate() {
		char[] chars = new char[GENERATED_LENGTH];
		for (int i = 0; i < chars.length; i++) {
			chars[i] = ALPHABET[RANDOM.nextInt(ALPHABET.length)];
		}
		return new String(chars);
	}

}
