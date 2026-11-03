package com.salesmanager.test.shop.integration.system;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import java.util.List;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.test.context.junit4.SpringRunner;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.web.servlet.mvc.method.RequestMappingInfoHandlerMapping;
import org.springframework.web.servlet.mvc.method.annotation.RequestMappingHandlerMapping;

import com.fasterxml.jackson.databind.JsonNode;
import com.salesmanager.test.shop.common.ServicesTestSupport;

import springfox.documentation.spring.web.plugins.WebMvcRequestHandlerProvider;

@RunWith(SpringRunner.class)
public class DocumentationCompatibilityIntegrationTest extends ServicesTestSupport {

    @Autowired
    private WebMvcRequestHandlerProvider handlerProvider;

    @Autowired
    @Qualifier("requestMappingHandlerMapping")
    private RequestMappingHandlerMapping mvcMapping;

    @Test
    public void swaggerStillDescribesPublicPrivateAndCustomerRoutes() {
        ResponseEntity<JsonNode> response = testRestTemplate.getForEntity("/v2/api-docs", JsonNode.class);
        assertEquals(HttpStatus.OK, response.getStatusCode());
        JsonNode document = response.getBody();
        assertNotNull(document);
        assertEquals("2.0", document.path("swagger").asText());
        JsonNode paths = document.path("paths");
        assertTrue(paths.has("/api/v1/products"));
        assertTrue(paths.has("/api/v1/private/login"));
        assertTrue(paths.has("/api/v1/customer/login"));
        assertTrue(paths.has("/api/v1/auth/customer/profile"));
        assertTrue(paths.has("/api/v1/auth/cart/{code}/checkout"));
        assertFalse(paths.has("/actuator/health"));
        assertEquals("apiKey", document.path("securityDefinitions").path("JWT").path("type").asText());
        assertEquals("Authorization", document.path("securityDefinitions").path("JWT").path("name").asText());
        assertEquals(HttpStatus.OK, testRestTemplate.getForEntity("/swagger-ui.html", String.class).getStatusCode());
    }

    @Test
    @SuppressWarnings("unchecked")
    public void springfoxUsesLegacyMappingsWithoutRemovingRealMvcRoutes() {
        assertNull(mvcMapping.getPatternParser());
        List<RequestMappingInfoHandlerMapping> mappings = (List<RequestMappingInfoHandlerMapping>)
                ReflectionTestUtils.getField(handlerProvider, "handlerMappings");
        assertNotNull(mappings);
        assertTrue(mappings.contains(mvcMapping));
        for (RequestMappingInfoHandlerMapping mapping : mappings) {
            assertNull(mapping.getPatternParser());
        }
        assertFalse(mvcMapping.getHandlerMethods().isEmpty());
    }

    @Test
    public void actuatorRemainsReachableAfterSpringfoxFiltering() {
        ResponseEntity<JsonNode> response = testRestTemplate.getForEntity("/actuator/health", JsonNode.class);
        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertNotNull(response.getBody());
        assertEquals("UP", response.getBody().path("status").asText());
        assertEquals(HttpStatus.OK,
                testRestTemplate.getForEntity("/actuator/health/ping/", JsonNode.class).getStatusCode());
    }
}
