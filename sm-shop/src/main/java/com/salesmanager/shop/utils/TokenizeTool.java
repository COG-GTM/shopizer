package com.salesmanager.shop.utils;

import java.security.SecureRandom;
import java.util.Base64;
import javax.crypto.Cipher;
import javax.crypto.KeyGenerator;
import javax.crypto.SecretKey;
import javax.crypto.spec.GCMParameterSpec;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class TokenizeTool {

    private final static String CIPHER = "AES/GCM/NoPadding";
    private static final int GCM_IV_LENGTH = 12; // 96 bits recommended for GCM
    private static final int GCM_TAG_LENGTH = 128; // 128-bit auth tag

    private static final Logger LOGGER = LoggerFactory.getLogger(TokenizeTool.class);
    private static final SecureRandom SECURE_RANDOM = new SecureRandom();

    private TokenizeTool() {}

    private static SecretKey key = null;

    static {
        try {
            KeyGenerator keygen = KeyGenerator.getInstance("AES");
            keygen.init(256); // AES-256
            key = keygen.generateKey();
        } catch (Exception e) {
            LOGGER.error("Cannot generate key", e);
        }
    }

    public static String tokenizeString(String token) throws Exception {
        // Generate random IV for each operation
        byte[] iv = new byte[GCM_IV_LENGTH];
        SECURE_RANDOM.nextBytes(iv);

        Cipher cipher = Cipher.getInstance(CIPHER);
        GCMParameterSpec gcmSpec = new GCMParameterSpec(GCM_TAG_LENGTH, iv);
        cipher.init(Cipher.ENCRYPT_MODE, key, gcmSpec);
        byte[] ciphertext = cipher.doFinal(token.getBytes(java.nio.charset.StandardCharsets.UTF_8));

        // Prepend IV to ciphertext
        byte[] combined = new byte[GCM_IV_LENGTH + ciphertext.length];
        System.arraycopy(iv, 0, combined, 0, GCM_IV_LENGTH);
        System.arraycopy(ciphertext, 0, combined, GCM_IV_LENGTH, ciphertext.length);

        return Base64.getEncoder().encodeToString(combined);
    }
}
