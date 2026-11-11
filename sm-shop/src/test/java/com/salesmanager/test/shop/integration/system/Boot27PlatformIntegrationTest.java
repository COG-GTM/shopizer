package com.salesmanager.test.shop.integration.system;

import static org.junit.jupiter.api.Assertions.*;
import javax.annotation.Resource;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.SpringBootVersion;
import org.springframework.core.env.Environment;
import org.springframework.http.ResponseEntity;
import org.springframework.test.context.junit.jupiter.SpringExtension;
import org.springframework.web.servlet.mvc.method.annotation.RequestMappingHandlerMapping;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.salesmanager.test.shop.common.ServicesTestSupport;

@ExtendWith(SpringExtension.class)
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
        assertEquals(200, response.getStatusCodeValue(), response.getBody());
        JsonNode document = new ObjectMapper().readTree(response.getBody());
        assertEquals("2.0", document.path("swagger").asText());
        JsonNode paths = document.path("paths");
        assertTrue(paths.has("/api/v1/products"));
        assertTrue(paths.has("/api/v1/private/login"));
        paths.fieldNames().forEachRemaining(path -> assertFalse(path.startsWith("/actuator"), path));
        assertEquals(200, testRestTemplate.getForEntity("/swagger-ui.html", String.class).getStatusCodeValue());
    }

    @Test
    public void healthIsUp() throws Exception {
        ResponseEntity<String> response = testRestTemplate.getForEntity("/actuator/health", String.class);
        assertEquals(200, response.getStatusCodeValue());
        assertEquals("UP", new ObjectMapper().readTree(response.getBody()).path("status").asText());
    }
}
