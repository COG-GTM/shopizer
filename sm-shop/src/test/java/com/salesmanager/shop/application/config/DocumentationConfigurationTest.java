package com.salesmanager.shop.application.config;

import static org.junit.Assert.*;

import java.util.Arrays;
import java.util.HashSet;
import org.junit.Test;
import org.springdoc.core.GroupedOpenApi;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.method.HandlerMethod;
import com.salesmanager.shop.store.api.v1.shoppingCart.ShoppingCartApi;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.Operation;
import io.swagger.v3.oas.models.responses.ApiResponse;
import io.swagger.v3.oas.models.responses.ApiResponses;
import io.swagger.v3.oas.models.security.SecurityScheme;

public class DocumentationConfigurationTest {
    private final DocumentationConfiguration configuration = new DocumentationConfiguration();

    @Test
    public void apiControllersIncludePlainAndRestControllersWithoutDuplicates() {
        Class<?>[] controllers = DocumentationConfiguration.apiControllers();
        assertTrue(controllers.length > 0);
        assertTrue(Arrays.asList(controllers).contains(ShoppingCartApi.class));
        assertEquals(controllers.length, new HashSet<>(Arrays.asList(controllers)).size());
        assertTrue(Arrays.stream(controllers).anyMatch(c -> c.isAnnotationPresent(RestController.class)));
        for (Class<?> controller : controllers) {
            assertTrue(controller.getName(), controller.getName().startsWith("com.salesmanager.shop.store.api.v1.")
                    || controller.getName().startsWith("com.salesmanager.shop.store.api.v2."));
        }
    }

    @Test
    public void defaultSummaryHandlesNullEmptyAndExistingSummaries() throws Exception {
        for (String summary : new String[] {null, "", "Explicit summary"}) {
            Operation operation = new Operation().summary(summary);
            assertSame(operation, configuration.defaultSummary().customize(operation, handler("get")));
            assertEquals(summary == null || summary.isEmpty() ? "get" : summary, operation.getSummary());
        }
    }

    @Test
    public void globalGetResponsesCoverAllGetMappingsAndNullResponses() throws Exception {
        for (String method : new String[] {"get", "unrestricted", "explicitGet"}) {
            Operation operation = new Operation();
            assertNull(operation.getResponses());
            configuration.globalGetResponses().customize(operation, handler(method));
            assertEquals("500 message", operation.getResponses().get("500").getDescription());
            assertEquals("Forbidden", operation.getResponses().get("403").getDescription());
            assertEquals("Unauthorized", operation.getResponses().get("401").getDescription());
        }
    }

    @Test
    public void globalGetResponsesLeavePostAndUnmappedHandlersAlone() throws Exception {
        for (String method : new String[] {"post", "explicitPost", "unmapped"}) {
            Operation operation = new Operation();
            configuration.globalGetResponses().customize(operation, handler(method));
            assertNull(operation.getResponses());
        }
    }

    @Test
    public void globalGetResponsesPreserveExistingResponse() throws Exception {
        ApiResponse response = new ApiResponse().description("Custom authentication response");
        Operation operation = new Operation().responses(new ApiResponses().addApiResponse("401", response));
        configuration.globalGetResponses().customize(operation, handler("get"));
        assertSame(response, operation.getResponses().get("401"));
        assertEquals("Custom authentication response", response.getDescription());
        assertEquals(3, operation.getResponses().size());
    }

    @Test
    public void openApiMetadataAndGlobalJwtSecurityAreConfigured() {
        OpenAPI api = configuration.shopizerOpenAPI();
        assertEquals("Shopizer REST API", api.getInfo().getTitle());
        assertEquals("1.0", api.getInfo().getVersion());
        assertEquals("Apache 2.0", api.getInfo().getLicense().getName());
        assertEquals("http://www.apache.org/licenses/LICENSE-2.0", api.getInfo().getLicense().getUrl());
        SecurityScheme jwt = api.getComponents().getSecuritySchemes().get("JWT");
        assertEquals(SecurityScheme.Type.HTTP, jwt.getType());
        assertEquals("bearer", jwt.getScheme());
        assertEquals("JWT", jwt.getBearerFormat());
        assertEquals(SecurityScheme.In.HEADER, jwt.getIn());
        assertEquals("Authorization", jwt.getName());
        assertTrue(api.getSecurity().stream().anyMatch(s -> s.containsKey("JWT")));
    }

    @Test
    public void defaultGroupScansBothApiPackages() {
        GroupedOpenApi api = configuration.shopizerApi();
        assertEquals("default", api.getGroup());
        assertEquals(Arrays.asList("com.salesmanager.shop.store.api.v1", "com.salesmanager.shop.store.api.v2"),
                api.getPackagesToScan());
    }

    private HandlerMethod handler(String name) throws Exception {
        return new HandlerMethod(new Handlers(), Handlers.class.getMethod(name));
    }

    public static class Handlers {
        @GetMapping public void get() { }
        @RequestMapping public void unrestricted() { }
        @RequestMapping(method = RequestMethod.GET) public void explicitGet() { }
        @PostMapping public void post() { }
        @RequestMapping(method = RequestMethod.POST) public void explicitPost() { }
        public void unmapped() { }
    }
}
