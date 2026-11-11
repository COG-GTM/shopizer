package com.salesmanager.test.shop.integration.system;

import static org.junit.Assert.*;
import javax.annotation.Resource;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.SpringBootVersion;
import org.springframework.core.env.Environment;
import org.springframework.http.ResponseEntity;
import org.springframework.test.context.junit4.SpringRunner;
import org.springframework.web.servlet.mvc.method.annotation.RequestMappingHandlerMapping;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.salesmanager.test.shop.common.ServicesTestSupport;

@RunWith(SpringRunner.class)
public class Boot27PlatformIntegrationTest extends ServicesTestSupport {
    @Autowired
    private Environment environment;
    @Resource(name = "requestMappingHandlerMapping")
    private RequestMappingHandlerMapping mapping;

    @Test
    public void platformUsesBoot27AndAntMatching() {
        assertTrue(SpringBootVersion.getVersion().startsWith("2.7."));
        assertEquals("ant_path_matcher", environment.getProperty("spring.mvc.pathmatch.matching-strategy"));
        assertEquals("true", environment.getProperty("spring.main.allow-circular-references"));
        assertNull(mapping.getPatternParser());
    }

    @Test
    public void swaggerDocumentsApplicationButNotActuator() throws Exception {
        ResponseEntity<String> response = testRestTemplate.getForEntity("/v2/api-docs", String.class);
        assertEquals(response.getBody(), 200, response.getStatusCodeValue());
        JsonNode document = new ObjectMapper().readTree(response.getBody());
        assertEquals("2.0", document.path("swagger").asText());
        JsonNode paths = document.path("paths");
        assertTrue(paths.has("/api/v1/products"));
        assertTrue(paths.has("/api/v1/private/login"));
        paths.fieldNames().forEachRemaining(path -> assertFalse(path, path.startsWith("/actuator")));
        assertEquals(200, testRestTemplate.getForEntity("/swagger-ui.html", String.class).getStatusCodeValue());
    }

    @Test
    public void healthIsUp() throws Exception {
        ResponseEntity<String> response = testRestTemplate.getForEntity("/actuator/health", String.class);
        assertEquals(200, response.getStatusCodeValue());
        assertEquals("UP", new ObjectMapper().readTree(response.getBody()).path("status").asText());
    }
}
