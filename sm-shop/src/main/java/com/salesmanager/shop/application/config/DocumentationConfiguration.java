package com.salesmanager.shop.application.config;

import static org.springframework.http.HttpHeaders.AUTHORIZATION;

import org.springdoc.core.GroupedOpenApi;
import org.springdoc.core.customizers.GlobalOpenApiCustomizer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Contact;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.info.License;
import io.swagger.v3.oas.models.responses.ApiResponse;
import io.swagger.v3.oas.models.responses.ApiResponses;
import io.swagger.v3.oas.models.security.SecurityRequirement;
import io.swagger.v3.oas.models.security.SecurityScheme;

/**
 * OpenAPI 3 documentation (springdoc).
 * http://localhost:8080/swagger-ui/index.html http://localhost:8080/v3/api-docs
 */
@Configuration
public class DocumentationConfiguration {

	public static final String GROUP = "default";

	private static final String SECURITY_SCHEME = "JWT";

	private static final String[] API_PACKAGES = {
			"com.salesmanager.shop.store.api.v1",
			"com.salesmanager.shop.store.api.v2" };

	@Bean
	public OpenAPI shopizerOpenAPI() {
		return new OpenAPI()
				.info(new Info()
						.title("Shopizer REST API")
						.description("API for Shopizer e-commerce. Contains public end points as well as private end points requiring basic authentication and remote authentication based on jwt bearer token. URL patterns containing /private/** use bearer token; those are authorized customer and administrators administration actions.")
						.version("1.0")
						.termsOfService("urn:tos")
						.contact(new Contact().name("Shopizer").url("https://www.shopizer.com").email(""))
						.license(new License().name("Apache 2.0").url("http://www.apache.org/licenses/LICENSE-2.0")))
				.components(new Components().addSecuritySchemes(SECURITY_SCHEME,
						new SecurityScheme()
								.name(AUTHORIZATION)
								.type(SecurityScheme.Type.HTTP)
								.scheme("bearer")
								.bearerFormat("JWT")
								.in(SecurityScheme.In.HEADER)))
				.addSecurityItem(new SecurityRequirement().addList(SECURITY_SCHEME));
	}

	@Bean
	public GroupedOpenApi shopizerApi() {
		return GroupedOpenApi.builder()
				.group(GROUP)
				.packagesToScan(API_PACKAGES)
				.build();
	}

	/**
	 * Same global GET responses the previous Swagger 2 Docket declared, applied to
	 * both the ungrouped /v3/api-docs and every group.
	 */
	@Bean
	public GlobalOpenApiCustomizer globalGetResponses() {
		return openApi -> openApi.getPaths().values().stream()
				.filter(path -> path.getGet() != null)
				.forEach(path -> {
					ApiResponses responses = path.getGet().getResponses();
					responses.computeIfAbsent("401", code -> new ApiResponse().description("Unauthorized"));
					responses.computeIfAbsent("403", code -> new ApiResponse().description("Forbidden"));
					responses.computeIfAbsent("500", code -> new ApiResponse().description("500 message"));
				});
	}

}
