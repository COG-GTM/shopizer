package com.salesmanager.test.shop.util;

import static org.junit.jupiter.api.Assertions.*;
import java.util.List;
import org.junit.jupiter.api.Test;
import io.swagger.v3.oas.models.security.SecurityScheme;
import com.salesmanager.shop.application.config.DocumentationConfiguration;

class DocumentationConfigurationTest {
    @Test void declaresApiMetadataAndAuthorizationHeader() {
        var api = new DocumentationConfiguration().shopizerOpenAPI();
        assertEquals("Shopizer REST API", api.getInfo().getTitle());
        assertEquals("1.0", api.getInfo().getVersion());
        assertEquals("Apache 2.0", api.getInfo().getLicense().getName());
        var jwt = api.getComponents().getSecuritySchemes().get("JWT");
        assertEquals(SecurityScheme.Type.APIKEY, jwt.getType());
        assertEquals(SecurityScheme.In.HEADER, jwt.getIn());
        assertEquals("Authorization", jwt.getName());
        assertTrue(api.getSecurity().get(0).containsKey("JWT"));
    }
    @Test void scansBothApiVersionsWithoutPathRestriction() {
        var group = new DocumentationConfiguration().shopizerApi();
        assertEquals("shopizer", group.getGroup());
        assertEquals(List.of("com.salesmanager.shop.store.api.v1", "com.salesmanager.shop.store.api.v2"), group.getPackagesToScan());
        assertNull(group.getPathsToMatch());
        assertNull(group.getPathsToExclude());
    }
}
