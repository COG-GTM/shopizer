package com.salesmanager.shop.application.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Contact;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.info.License;
import io.swagger.v3.oas.models.security.SecurityRequirement;
import io.swagger.v3.oas.models.security.SecurityScheme;

/**
 * OpenAPI documentation: /v3/api-docs (JSON) and /swagger-ui.html (UI).
 */
@Configuration
public class DocumentationConfiguration {

	private static final String JWT = "JWT";

	@Bean
	public OpenAPI shopizerOpenAPI() {
		return new OpenAPI()
				.info(new Info()
						.title("Shopizer REST API")
						.description("API for Shopizer e-commerce. Contains public end points as well as private end points requiring basic authentication and remote authentication based on jwt bearer token. URL patterns containing /private/** use bearer token; those are authorized customer and administrators administration actions.")
						.version("1.0")
						.termsOfService("urn:tos")
						.contact(new Contact().name("Shopizer").url("https://www.shopizer.com"))
						.license(new License().name("Apache 2.0").url("http://www.apache.org/licenses/LICENSE-2.0")))
				.components(new Components().addSecuritySchemes(JWT,
						new SecurityScheme().type(SecurityScheme.Type.HTTP).scheme("bearer").bearerFormat(JWT)))
				.addSecurityItem(new SecurityRequirement().addList(JWT));
	}

}
