package com.salesmanager.shop.application.config;

import static org.junit.jupiter.api.Assertions.*;

import java.time.Duration;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.web.cors.CorsConfiguration;

class CorsPropertiesTest {
    @Test
    void defaultsAllowCredentialedRequestsFromAnyOrigin() {
        CorsProperties properties = new CorsProperties();
        assertEquals(List.of("*"), properties.getAllowedOriginPatterns());
        assertEquals(List.of("GET", "POST", "PUT", "PATCH", "DELETE", "OPTIONS"), properties.getAllowedMethods());
        assertEquals(List.of("X-Auth-Token", "Content-Type", "Authorization", "Cache-Control", "X-Requested-With"),
                properties.getAllowedHeaders());
        assertTrue(properties.isAllowCredentials());
        assertEquals(Duration.ofHours(1), properties.getMaxAge());
        CorsConfiguration config = properties.toCorsConfiguration();
        assertEquals("http://localhost:3000", config.checkOrigin("http://localhost:3000"));
        assertEquals("https://other.example", config.checkOrigin("https://other.example"));
        assertNull(config.checkOrigin(null));
        assertEquals(Boolean.TRUE, config.getAllowCredentials());
        assertEquals(3600L, config.getMaxAge());
    }

    @Test
    void everyConfiguredFieldIsMappedAndOriginsAreRestricted() {
        CorsProperties properties = new CorsProperties();
        properties.setAllowedOriginPatterns(List.of("https://shop.example.com"));
        properties.setAllowedMethods(List.of("GET"));
        properties.setAllowedHeaders(List.of("Authorization"));
        properties.setAllowCredentials(false);
        properties.setMaxAge(Duration.ofSeconds(42));
        CorsConfiguration config = properties.toCorsConfiguration();
        assertEquals(properties.getAllowedOriginPatterns(), config.getAllowedOriginPatterns());
        assertEquals(properties.getAllowedMethods(), config.getAllowedMethods());
        assertEquals(properties.getAllowedHeaders(), config.getAllowedHeaders());
        assertEquals(Boolean.FALSE, config.getAllowCredentials());
        assertEquals(42L, config.getMaxAge());
        assertEquals("https://shop.example.com", config.checkOrigin("https://shop.example.com"));
        assertNull(config.checkOrigin("http://evil.example"));
        properties.setAllowedOriginPatterns(List.of());
        assertNull(properties.toCorsConfiguration().checkOrigin("https://shop.example.com"));
    }
}
