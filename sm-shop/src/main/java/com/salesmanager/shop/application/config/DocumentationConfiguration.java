package com.salesmanager.shop.application.config;

import static org.springframework.http.HttpHeaders.AUTHORIZATION;

import org.springdoc.core.models.GroupedOpenApi;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Contact;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.info.License;
import io.swagger.v3.oas.models.security.SecurityRequirement;
import io.swagger.v3.oas.models.security.SecurityScheme;

@Configuration
public class DocumentationConfiguration {

	private static final String SECURITY_SCHEME = "JWT";

	/**
	 * http://localhost:8080/swagger-ui.html http://localhost:8080/v3/api-docs
	 */
	@Bean
	public GroupedOpenApi api() {
		return GroupedOpenApi.builder()
				.group("shopizer")
				.packagesToScan("com.salesmanager.shop.store.api.v1", "com.salesmanager.shop.store.api.v2")
				.build();
	}

	@Bean
	public OpenAPI shopizerOpenAPI() {
		return new OpenAPI()
				.info(apiInfo())
				.components(new Components().addSecuritySchemes(SECURITY_SCHEME,
						new SecurityScheme().type(SecurityScheme.Type.APIKEY).in(SecurityScheme.In.HEADER).name(AUTHORIZATION)))
				.addSecurityItem(new SecurityRequirement().addList(SECURITY_SCHEME));
	}

	private Info apiInfo() {
		return new Info().title("Shopizer REST API")
				.description("API for Shopizer e-commerce. Contains public end points as well as private end points requiring basic authentication and remote authentication based on jwt bearer token. URL patterns containing /private/** use bearer token; those are authorized customer and administrators administration actions.")
				.version("1.0")
				.termsOfService("urn:tos")
				.contact(new Contact().name("Shopizer").url("https://www.shopizer.com"))
				.license(new License().name("Apache 2.0").url("http://www.apache.org/licenses/LICENSE-2.0"));
	}

}
