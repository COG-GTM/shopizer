package com.salesmanager.shop.application.config;

import org.springdoc.core.customizers.OpenApiCustomizer;
import org.springdoc.core.utils.SpringDocUtils;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import com.salesmanager.core.model.merchant.MerchantStore;
import com.salesmanager.core.model.reference.language.Language;

import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Contact;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.info.License;
import io.swagger.v3.oas.models.responses.ApiResponse;
import io.swagger.v3.oas.models.security.SecurityRequirement;
import io.swagger.v3.oas.models.security.SecurityScheme;

/**
 * OpenAPI 3 (springdoc) documentation.
 * http://localhost:8080/swagger-ui.html http://localhost:8080/v3/api-docs
 * Scanned packages are set with springdoc.packages-to-scan (api v1 and v2).
 */
@Configuration
public class DocumentationConfiguration {

	public static final String SECURITY_SCHEME = "JWT";

	static {
		// resolved from the store / lang request parameters by argument resolvers
		SpringDocUtils.getConfig().addRequestWrapperToIgnore(MerchantStore.class, Language.class);
	}

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
				.components(new Components().addSecuritySchemes(SECURITY_SCHEME,
						new SecurityScheme().type(SecurityScheme.Type.HTTP).scheme("bearer").bearerFormat("JWT")))
				.addSecurityItem(new SecurityRequirement().addList(SECURITY_SCHEME));
	}

	/**
	 * Global GET responses previously declared on the springfox Docket.
	 */
	@Bean
	public OpenApiCustomizer globalGetResponsesCustomizer() {
		return openApi -> {
			if (openApi.getPaths() == null) {
				return;
			}
			openApi.getPaths().values().stream()
					.filter(item -> item.getGet() != null)
					.forEach(item -> {
						item.getGet().getResponses().putIfAbsent("401", new ApiResponse().description("Unauthorized"));
						item.getGet().getResponses().putIfAbsent("403", new ApiResponse().description("Forbidden"));
						item.getGet().getResponses().putIfAbsent("500", new ApiResponse().description("500 message"));
					});
		};
	}

}
