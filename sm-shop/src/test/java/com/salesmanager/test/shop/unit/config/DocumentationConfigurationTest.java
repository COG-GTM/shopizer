package com.salesmanager.test.shop.unit.config;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;

import org.junit.Test;

import com.salesmanager.shop.application.config.DocumentationConfiguration;

import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.PathItem;
import io.swagger.v3.oas.models.Paths;
import io.swagger.v3.oas.models.Operation;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.responses.ApiResponse;
import io.swagger.v3.oas.models.responses.ApiResponses;
import io.swagger.v3.oas.models.security.SecurityScheme;

public class DocumentationConfigurationTest {

	@Test
	public void openApiBeanDefinesShopizerMetadataAndJwtSecurity() {
		OpenAPI openApi = new DocumentationConfiguration().shopizerOpenAPI();
		Info info = openApi.getInfo();

		assertEquals("Shopizer REST API", info.getTitle());
		assertEquals("1.0", info.getVersion());
		assertEquals("Apache 2.0", info.getLicense().getName());
		assertEquals("Shopizer", info.getContact().getName());

		SecurityScheme jwt = openApi.getComponents().getSecuritySchemes().get("JWT");
		assertNotNull(jwt);
		assertEquals(SecurityScheme.Type.HTTP, jwt.getType());
		assertEquals("bearer", jwt.getScheme());
		assertEquals("JWT", jwt.getBearerFormat());
		assertEquals("Authorization", jwt.getName());
		assertTrue(openApi.getSecurity().stream().anyMatch(requirement -> requirement.containsKey("JWT")));
	}

	@Test
	public void apiGroupScansOnlyVersionOneAndTwoPackages() {
		Set<String> expectedPackages = new HashSet<>(Arrays.asList(
				"com.salesmanager.shop.store.api.v1",
				"com.salesmanager.shop.store.api.v2"));

		assertEquals("default", new DocumentationConfiguration().shopizerApi().getGroup());
		assertEquals(expectedPackages,
				new HashSet<>(new DocumentationConfiguration().shopizerApi().getPackagesToScan()));
		assertEquals(2, new DocumentationConfiguration().shopizerApi().getPackagesToScan().size());
	}

	@Test
	public void globalGetResponsesPreservesExistingResponsesAndIgnoresNonGetOperations() {
		ApiResponse customUnauthorized = new ApiResponse().description("custom");
		ApiResponses getResponses = new ApiResponses()
				.addApiResponse("200", new ApiResponse().description("OK"))
				.addApiResponse("401", customUnauthorized);
		ApiResponses postResponses = new ApiResponses()
				.addApiResponse("201", new ApiResponse().description("Created"));
		Operation getOperation = new Operation().responses(getResponses);
		Operation postOperation = new Operation().responses(postResponses);
		Paths paths = new Paths()
				.addPathItem("/a", new PathItem().get(getOperation))
				.addPathItem("/b", new PathItem().post(postOperation));

		new DocumentationConfiguration().globalGetResponses().customise(new OpenAPI().paths(paths));

		assertTrue(getResponses.containsKey("200"));
		assertTrue(getResponses.containsKey("401"));
		assertTrue(getResponses.containsKey("403"));
		assertTrue(getResponses.containsKey("500"));
		assertEquals("custom", getResponses.get("401").getDescription());
		assertEquals("Forbidden", getResponses.get("403").getDescription());
		assertEquals("500 message", getResponses.get("500").getDescription());
		assertEquals(1, postResponses.size());
		assertTrue(postResponses.containsKey("201"));
		assertFalse(postResponses.containsKey("401"));
		assertFalse(postResponses.containsKey("403"));
		assertFalse(postResponses.containsKey("500"));
		assertTrue(paths.get("/b").getGet() == null);
	}

	@Test
	public void springfoxDocketIsNotOnTheClasspath() {
		try {
			Class.forName("springfox.documentation.spring.web.plugins.Docket");
			assertTrue("Springfox Docket should not be present", false);
		} catch (ClassNotFoundException expected) {
			assertTrue(true);
		}
	}
}
