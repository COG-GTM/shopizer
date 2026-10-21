package com.salesmanager.test.shop.unit.config;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import java.util.Arrays;

import org.junit.Test;
import org.springdoc.core.GroupedOpenApi;
import org.springdoc.core.customizers.GlobalOpenApiCustomizer;

import com.salesmanager.shop.application.config.DocumentationConfiguration;

import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.PathItem;
import io.swagger.v3.oas.models.Paths;
import io.swagger.v3.oas.models.Operation;
import io.swagger.v3.oas.models.responses.ApiResponse;
import io.swagger.v3.oas.models.responses.ApiResponses;
import io.swagger.v3.oas.models.security.SecurityScheme;

public class DocumentationConfigurationTest {

	@Test
	public void shopizerOpenApiDefinesJwtSecurityAndApiMetadata() {
		OpenAPI openAPI = new DocumentationConfiguration().shopizerOpenAPI();

		assertEquals("Shopizer REST API", openAPI.getInfo().getTitle());
		assertEquals("1.0", openAPI.getInfo().getVersion());
		assertEquals("Apache 2.0", openAPI.getInfo().getLicense().getName());

		SecurityScheme jwt = openAPI.getComponents().getSecuritySchemes().get("JWT");
		assertEquals(SecurityScheme.Type.HTTP, jwt.getType());
		assertEquals("bearer", jwt.getScheme());
		assertEquals("JWT", jwt.getBearerFormat());
		assertEquals("Authorization", jwt.getName());
		assertEquals(SecurityScheme.In.HEADER, jwt.getIn());
		assertTrue(openAPI.getSecurity().stream().anyMatch(requirement -> requirement.containsKey("JWT")));
	}

	@Test
	public void shopizerApiScansOnlyTheVersionedApiPackages() {
		GroupedOpenApi api = new DocumentationConfiguration().shopizerApi();

		assertEquals("default", api.getGroup());
		assertEquals(Arrays.asList("com.salesmanager.shop.store.api.v1", "com.salesmanager.shop.store.api.v2"),
				api.getPackagesToScan());
	}

	@Test
	public void globalGetResponsesAddsMissingCodesAndPreservesExistingResponses() {
		ApiResponses emptyResponses = new ApiResponses();
		ApiResponses existingResponses = new ApiResponses()
				.addApiResponse("200", new ApiResponse().description("OK"))
				.addApiResponse("401", new ApiResponse().description("Custom unauthorized"));
		ApiResponses postResponses = new ApiResponses()
				.addApiResponse("201", new ApiResponse().description("Created"));

		Paths paths = new Paths()
				.addPathItem("/empty", new PathItem().get(new Operation().responses(emptyResponses)))
				.addPathItem("/existing", new PathItem().get(new Operation().responses(existingResponses)))
				.addPathItem("/post-only", new PathItem().post(new Operation().responses(postResponses)));
		OpenAPI openAPI = new OpenAPI().paths(paths);
		GlobalOpenApiCustomizer customizer = new DocumentationConfiguration().globalGetResponses();

		customizer.customise(openAPI);

		assertEquals("Unauthorized", emptyResponses.get("401").getDescription());
		assertEquals("Forbidden", emptyResponses.get("403").getDescription());
		assertEquals("500 message", emptyResponses.get("500").getDescription());
		assertEquals("OK", existingResponses.get("200").getDescription());
		assertEquals("Custom unauthorized", existingResponses.get("401").getDescription());
		assertEquals("Forbidden", existingResponses.get("403").getDescription());
		assertEquals("500 message", existingResponses.get("500").getDescription());
		assertArrayEquals(new String[] { "201" }, postResponses.keySet().toArray(new String[0]));
	}
}
