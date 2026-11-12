package com.salesmanager.test.shop.util;

import com.salesmanager.shop.filter.CorsFilter;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import static org.junit.jupiter.api.Assertions.*;

class CorsInterceptorUpgradeTest {
    @Test void echoesOriginAndAllowsRequest() throws Exception { check("https://store.example", "https://store.example"); }
    @Test void missingOriginUsesWildcard() throws Exception { check(null, "*"); }
    @Test void blankOriginUsesWildcard() throws Exception { check("  ", "*"); }
    private void check(String origin, String expected) throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest("OPTIONS", "/api/v1/category");
        if (origin != null) request.addHeader("origin", origin);
        MockHttpServletResponse response = new MockHttpServletResponse();
        assertTrue(new CorsFilter().preHandle(request, response, new Object()));
        assertEquals(expected, response.getHeader("Access-Control-Allow-Origin"));
        assertEquals("POST, GET, PUT, OPTIONS, DELETE, PATCH", response.getHeader("Access-Control-Allow-Methods"));
        assertEquals("X-Auth-Token, Content-Type, Authorization, Cache-Control, X-Requested-With",
                response.getHeader("Access-Control-Allow-Headers"));
    }
}
