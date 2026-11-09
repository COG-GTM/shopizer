package com.salesmanager.shop.application.config;

import static org.junit.jupiter.api.Assertions.*;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;

class PasswordEncoderTest {
    @Test
    void defaultStrengthIsTenAndExistingBcryptHashesStillMatch() throws Exception {
        String defaultValue = MultipleEntryPointsSecurityConfig.class.getMethod("passwordEncoder", int.class)
                .getParameters()[0].getAnnotation(Value.class).value();
        assertEquals("${shopizer.security.password.bcrypt-strength:10}", defaultValue);
        PasswordEncoder encoder = new MultipleEntryPointsSecurityConfig().passwordEncoder(10);
        String hash = encoder.encode("password");
        assertTrue(hash.startsWith("$2a$10$"));
        assertTrue(encoder.matches("password", hash));
        assertFalse(encoder.matches("wrong", hash));
        assertTrue(encoder.matches("password", new BCryptPasswordEncoder().encode("password")));
    }

    @Test
    void configurableStrengthIsUsed() {
        PasswordEncoder encoder = new MultipleEntryPointsSecurityConfig().passwordEncoder(12);
        String hash = encoder.encode("password");
        assertTrue(hash.startsWith("$2a$12$"));
        assertTrue(encoder.matches("password", hash));
        assertThrows(IllegalArgumentException.class, () -> new MultipleEntryPointsSecurityConfig().passwordEncoder(3));
    }
}
