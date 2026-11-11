package com.salesmanager.shop.application.config;

import static org.springframework.http.HttpHeaders.AUTHORIZATION;

import java.util.Arrays;

import org.springdoc.core.GroupedOpenApi;
import org.springdoc.core.SpringDocUtils;
import org.springdoc.core.customizers.OperationCustomizer;
import org.springframework.beans.factory.config.BeanDefinition;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.ClassPathScanningCandidateComponentProvider;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.type.filter.AnnotationTypeFilter;
import org.springframework.stereotype.Controller;
import org.springframework.util.ClassUtils;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;

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
 *
 * http://localhost:8080/swagger-ui.html (redirects to /swagger-ui/index.html)
 * http://localhost:8080/v3/api-docs
 */
@Configuration
public class DocumentationConfiguration {

	public static final String GROUP = "default";

	public static final String SECURITY_SCHEME = "JWT";

	static final String[] API_PACKAGES = { "com.salesmanager.shop.store.api.v1",
			"com.salesmanager.shop.store.api.v2" };

	static {
		SpringDocUtils.getConfig().addRestControllers(apiControllers());
	}

	/**
	 * Several API classes are plain {@code @Controller}s whose handlers return {@code void} or
	 * {@code ResponseEntity} without {@code @ResponseBody}. the Swagger 2 setup documented every handler in the
	 * API packages; registering these classes as REST controllers keeps those operations documented.
	 */
	static Class<?>[] apiControllers() {
		ClassPathScanningCandidateComponentProvider scanner = new ClassPathScanningCandidateComponentProvider(false);
		scanner.addIncludeFilter(new AnnotationTypeFilter(Controller.class));
		ClassLoader classLoader = DocumentationConfiguration.class.getClassLoader();
		return Arrays.stream(API_PACKAGES)
				.flatMap(pkg -> scanner.findCandidateComponents(pkg).stream())
				.map(BeanDefinition::getBeanClassName)
				.map(name -> ClassUtils.resolveClassName(name, classLoader))
				.toArray(Class<?>[]::new);
	}

	@Bean
	public OpenAPI shopizerOpenAPI() {
		return new OpenAPI()
				.info(new Info().title("Shopizer REST API")
						.description("API for Shopizer e-commerce. Contains public end points as well as private end points requiring basic authentication and remote authentication based on jwt bearer token. URL patterns containing /private/** use bearer token; those are authorized customer and administrators administration actions.")
						.version("1.0")
						.termsOfService("urn:tos")
						.contact(new Contact().name("Shopizer").url("https://www.shopizer.com"))
						.license(new License().name("Apache 2.0").url("http://www.apache.org/licenses/LICENSE-2.0")))
				.components(new Components().addSecuritySchemes(SECURITY_SCHEME,
						new SecurityScheme().type(SecurityScheme.Type.HTTP).scheme("bearer").bearerFormat("JWT")
								.in(SecurityScheme.In.HEADER).name(AUTHORIZATION)))
				.addSecurityItem(new SecurityRequirement().addList(SECURITY_SCHEME));
	}

	@Bean
	public GroupedOpenApi shopizerApi() {
		return GroupedOpenApi.builder()
				.group(GROUP)
				.packagesToScan(API_PACKAGES)
				.addOperationCustomizer(defaultSummary())
				.addOperationCustomizer(globalGetResponses())
				.build();
	}

	/**
	 * Operations without an explicit summary fall back to the handler method name, as the Swagger 2 setup did.
	 */
	@Bean
	public OperationCustomizer defaultSummary() {
		return (operation, handlerMethod) -> {
			if (operation.getSummary() == null || operation.getSummary().isEmpty()) {
				operation.setSummary(handlerMethod.getMethod().getName());
			}
			return operation;
		};
	}

	/**
	 * Documents the 500 / 403 / 401 responses on every GET operation, as the Swagger 2 Docket did.
	 */
	@Bean
	public OperationCustomizer globalGetResponses() {
		return (operation, handlerMethod) -> {
			if (isGet(handlerMethod.getMethod().getAnnotation(GetMapping.class),
					handlerMethod.getMethod().getAnnotation(RequestMapping.class))) {
				if (operation.getResponses() == null) {
					operation.setResponses(new ApiResponses());
				}
				operation.getResponses().putIfAbsent("500", new ApiResponse().description("500 message"));
				operation.getResponses().putIfAbsent("403", new ApiResponse().description("Forbidden"));
				operation.getResponses().putIfAbsent("401", new ApiResponse().description("Unauthorized"));
			}
			return operation;
		};
	}

	private static boolean isGet(GetMapping getMapping, RequestMapping requestMapping) {
		if (getMapping != null) {
			return true;
		}
		if (requestMapping == null) {
			return false;
		}
		RequestMethod[] methods = requestMapping.method();
		if (methods.length == 0) {
			return true;
		}
		for (RequestMethod m : methods) {
			if (m == RequestMethod.GET) {
				return true;
			}
		}
		return false;
	}

}
