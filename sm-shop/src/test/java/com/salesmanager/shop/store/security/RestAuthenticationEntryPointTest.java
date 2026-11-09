package com.salesmanager.shop.store.security;

import static org.junit.jupiter.api.Assertions.*;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.authentication.BadCredentialsException;

class RestAuthenticationEntryPointTest {
    @Test
    void unauthorizedResponseContainsBearerChallengeAndJson() throws Exception {
        MockHttpServletResponse response = new MockHttpServletResponse();
        new RestAuthenticationEntryPoint("test-realm").commence(new MockHttpServletRequest(), response,
                new BadCredentialsException("not exposed"));
        assertEquals(401, response.getStatus());
        assertEquals("Bearer realm=\"test-realm\"", response.getHeader("WWW-Authenticate"));
        assertEquals("application/json", response.getContentType());
        JsonNode body = new ObjectMapper().readTree(response.getContentAsString());
        assertEquals("401", body.path("errorCode").asText());
        assertEquals("Unauthorized", body.path("message").asText());
    }
}
