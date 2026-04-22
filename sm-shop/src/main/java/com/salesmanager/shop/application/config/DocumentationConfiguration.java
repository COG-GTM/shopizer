package com.salesmanager.shop.application.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Contact;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.info.License;
import io.swagger.v3.oas.models.security.SecurityRequirement;
import io.swagger.v3.oas.models.security.SecurityScheme;
import io.swagger.v3.oas.models.Components;

@Configuration
public class DocumentationConfiguration {

	@Bean
	public OpenAPI shopizerOpenAPI() {
		return new OpenAPI()
				.info(new Info()
						.title("Shopizer")
						.description("Shopizer REST API")
						.version("3.2.7")
						.contact(new Contact()
								.name("Shopizer")
								.url("https://www.shopizer.com"))
						.license(new License()
								.name("Apache 2.0")
								.url("http://www.apache.org/licenses/LICENSE-2.0")))
				.addSecurityItem(new SecurityRequirement().addList("JWT"))
				.components(new Components()
						.addSecuritySchemes("JWT", new SecurityScheme()
								.type(SecurityScheme.Type.HTTP)
								.scheme("bearer")
								.bearerFormat("JWT")
								.in(SecurityScheme.In.HEADER)
								.name("Authorization")));
	}

}
