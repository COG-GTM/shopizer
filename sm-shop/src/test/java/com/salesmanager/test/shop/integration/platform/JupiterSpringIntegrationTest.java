package com.salesmanager.test.shop.integration.platform;

import static org.junit.jupiter.api.Assertions.*;

import javax.inject.Inject;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.context.ApplicationContext;
import org.springframework.http.HttpStatus;
import com.fasterxml.jackson.databind.JsonNode;
import com.salesmanager.test.shop.common.ServicesTestSupport;

public class JupiterSpringIntegrationTest extends ServicesTestSupport {
    @Inject
    private ApplicationContext context;

    @BeforeEach
    void dependenciesAreUsableBeforeEach() {
        assertNotNull(context);
        assertNotNull(testRestTemplate);
        assertTrue(((org.springframework.boot.web.servlet.context.ServletWebServerApplicationContext) context)
                .getWebServer().getPort() > 0);
        assertEquals(HttpStatus.OK, testRestTemplate.getForEntity("/actuator/health", JsonNode.class).getStatusCode());
    }

    @Test
    void bootContextAndAdminLoginAreUsable() {
        assertTrue(context instanceof org.springframework.boot.web.servlet.context.ServletWebServerApplicationContext);
        String authorization = getHeader().getFirst("Authorization");
        assertNotNull(authorization);
        assertTrue(authorization.matches("Bearer [^.]+\\.[^.]+\\.[^.]+"));
    }

    @Test
    void healthAndSpringfoxDocumentationAreAvailable() {
        assertEquals("UP", testRestTemplate.getForObject("/actuator/health", JsonNode.class).path("status").asText());
        assertEquals(HttpStatus.OK, testRestTemplate.getForEntity("/v2/api-docs", String.class).getStatusCode());
    }

    @Nested
    class NestedContext {
        @Inject
        private ApplicationContext nestedContext;

        @Test
        void sharesTheOuterSpringContext() {
            assertSame(context, nestedContext);
            assertTrue(((org.springframework.boot.web.servlet.context.ServletWebServerApplicationContext) nestedContext)
                    .getWebServer().getPort() > 0);
        }
    }
}
