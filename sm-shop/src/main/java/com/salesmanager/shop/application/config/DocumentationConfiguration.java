package com.salesmanager.shop.application.config;

import org.springdoc.core.models.GroupedOpenApi;
import org.springdoc.core.utils.SpringDocUtils;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpHeaders;

import com.salesmanager.core.model.merchant.MerchantStore;
import com.salesmanager.core.model.reference.language.Language;

import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Contact;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.info.License;
import io.swagger.v3.oas.models.security.SecurityRequirement;
import io.swagger.v3.oas.models.security.SecurityScheme;

/**
 * OpenAPI 3 documentation served by springdoc.
 * UI: /swagger-ui/index.html (/swagger-ui.html redirects) - spec: /v3/api-docs
 */
@Configuration
public class DocumentationConfiguration {

	private static final String JWT = "JWT";

	static {
		SpringDocUtils.getConfig().addRequestWrapperToIgnore(MerchantStore.class, Language.class);
	}

	@Bean
	public OpenAPI shopizerOpenAPI() {
		return new OpenAPI()
				.info(new Info().title("Shopizer REST API")
						.description("API for Shopizer e-commerce. Contains public end points as well as private end points "
								+ "requiring basic authentication and remote authentication based on jwt bearer token. "
								+ "URL patterns containing /private/** use bearer token; those are authorized customer "
								+ "and administrators administration actions.")
						.version("1.0")
						.termsOfService("urn:tos")
						.contact(new Contact().name("Shopizer").url("https://www.shopizer.com"))
						.license(new License().name("Apache 2.0").url("http://www.apache.org/licenses/LICENSE-2.0")))
				.components(new Components().addSecuritySchemes(JWT,
						new SecurityScheme().type(SecurityScheme.Type.APIKEY).in(SecurityScheme.In.HEADER)
								.name(HttpHeaders.AUTHORIZATION)))
				.addSecurityItem(new SecurityRequirement().addList(JWT));
	}

	@Bean
	public GroupedOpenApi shopizerApi() {
		return GroupedOpenApi.builder()
				.group("shopizer")
				.packagesToScan("com.salesmanager.shop.store.api.v1", "com.salesmanager.shop.store.api.v2")
				.build();
	}

}
