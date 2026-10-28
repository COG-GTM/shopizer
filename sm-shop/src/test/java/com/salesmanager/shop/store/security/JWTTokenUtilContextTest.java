package com.salesmanager.shop.store.security;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Base64;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

class JWTTokenUtilContextTest {
    private final String validSecret = Base64.getEncoder().encodeToString(new byte[64]);

    @Test
    void invalidSecretFailsContextStartup() {
        new ApplicationContextRunner()
                .withUserConfiguration(JWTTokenUtil.class)
                .withPropertyValues("jwt.expiration=604800", "jwt.secret=aSecret")
                .run(context -> {
                    Throwable startupFailure = context.getStartupFailure();
                    assertNotNull(startupFailure);
                    assertTrue(rootCause(startupFailure) instanceof IllegalStateException);
                });
    }

    @Test
    void unresolvedJwtSecretFailsContextStartup() {
        new ApplicationContextRunner()
                .withUserConfiguration(JWTTokenUtil.class)
                .withPropertyValues("jwt.expiration=604800", "jwt.secret=${JWT_SECRET}")
                .run(context -> {
                    Throwable startupFailure = context.getStartupFailure();
                    assertNotNull(startupFailure);
                    assertTrue(causeChainContains(startupFailure, "JWT_SECRET"));
                });
    }

    @Test
    void validSecretAllowsContextStartup() {
        new ApplicationContextRunner()
                .withUserConfiguration(JWTTokenUtil.class)
                .withPropertyValues("jwt.expiration=604800", "jwt.secret=" + validSecret)
                .run(context -> {
                    assertNull(context.getStartupFailure());
                    assertNotNull(context.getBean(JWTTokenUtil.class));
                });
    }

    private Throwable rootCause(Throwable throwable) {
        Throwable cause = throwable;
        while (cause.getCause() != null) {
            cause = cause.getCause();
        }
        return cause;
    }

    private boolean causeChainContains(Throwable throwable, String text) {
        Throwable cause = throwable;
        while (cause != null) {
            if (cause.getMessage() != null && cause.getMessage().contains(text)) {
                return true;
            }
            cause = cause.getCause();
        }
        return false;
    }
}
