package com.salesmanager.test.shop.security;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import org.junit.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;

import com.salesmanager.shop.application.config.CorsConfig;

public class CorsConfigTest {

    @Test
    public void allowsConfiguredOriginsAndCredentials() {
        CorsConfiguration configuration = corsConfiguration(
                "https://shop.example.com, https://admin.example.com",
                "/api/v1/products");

        assertEquals("https://shop.example.com", configuration.checkOrigin("https://shop.example.com"));
        assertNull(configuration.checkOrigin("https://evil.example"));
        assertEquals(Boolean.TRUE, configuration.getAllowCredentials());
    }

    @Test
    public void emptyOriginsRejectCrossOriginRequestsWithoutCredentials() {
        CorsConfiguration configuration = corsConfiguration("", "/api/v1/products");

        assertNull(configuration.checkOrigin("https://evil.example"));
        assertTrue(!Boolean.TRUE.equals(configuration.getAllowCredentials()));
    }

    @Test(expected = IllegalStateException.class)
    public void rejectsWildcardOrigins() {
        CorsConfig config = new CorsConfig();
        ReflectionTestUtils.setField(config, "allowedOrigins", "*");
        config.corsConfigurationSource();
    }

    @Test
    public void doesNotConfigureShopPaths() {
        CorsConfig config = new CorsConfig();
        ReflectionTestUtils.setField(config, "allowedOrigins", "https://shop.example.com");
        CorsConfigurationSource source = config.corsConfigurationSource();

        assertNull(source.getCorsConfiguration(new MockHttpServletRequest("GET", "/shop/x")));
    }

    private CorsConfiguration corsConfiguration(String origins, String path) {
        CorsConfig config = new CorsConfig();
        ReflectionTestUtils.setField(config, "allowedOrigins", origins);
        CorsConfigurationSource source = config.corsConfigurationSource();
        return source.getCorsConfiguration(new MockHttpServletRequest("GET", path));
    }
}
