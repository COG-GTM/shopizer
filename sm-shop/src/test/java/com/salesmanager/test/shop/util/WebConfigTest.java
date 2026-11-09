package com.salesmanager.test.shop.util;

import static org.junit.jupiter.api.Assertions.*;
import org.junit.jupiter.api.Test;
import org.springframework.core.Ordered;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import jakarta.servlet.http.HttpServletRequest;
import com.salesmanager.shop.application.config.WebConfig;

class WebConfigTest {
    @Test void removesTrailingSlash() throws Exception { check("/api/v1/cart/", "/api/v1/cart"); }
    @Test void preservesCanonicalPath() throws Exception { check("/api/v1/cart", "/api/v1/cart"); }
    @Test void preservesRoot() throws Exception { check("/", "/"); }
    @Test void precedesSecurity() {
        assertEquals(Ordered.HIGHEST_PRECEDENCE, new WebConfig().trailingSlashFilter().getOrder());
    }
    private void check(String path, String expected) throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest("GET", path);
        request.setServletPath(path);
        int[] calls = {0};
        new WebConfig().trailingSlashFilter().getFilter().doFilter(request,
                new MockHttpServletResponse(), (req, res) -> {
                    calls[0]++;
                    assertEquals(expected, ((HttpServletRequest) req).getRequestURI());
                });
        assertEquals(1, calls[0]);
    }
}
