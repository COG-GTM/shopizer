package com.salesmanager.shop.application.config;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.Ordered;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;
import org.springframework.web.filter.CorsFilter;

/**
 * Single CORS policy for the REST api and public services. Only origins listed in
 * cors.allowedOrigins receive Access-Control-Allow-Origin; the request Origin is never reflected.
 */
@Configuration
public class CorsConfig {

	static final List<String> ALLOWED_METHODS = Arrays.asList("GET", "HEAD", "POST", "PUT", "DELETE", "PATCH", "OPTIONS");

	static final List<String> ALLOWED_HEADERS = Arrays.asList("X-Auth-Token", "Content-Type", "Authorization",
			"Cache-Control", "X-Requested-With");

	@Value("${cors.allowedOrigins:}")
	private String allowedOrigins;

	@Value("${cors.allowCredentials:false}")
	private boolean allowCredentials;

	public CorsConfig() {
	}

	public CorsConfig(String allowedOrigins, boolean allowCredentials) {
		this.allowedOrigins = allowedOrigins;
		this.allowCredentials = allowCredentials;
	}

	@Bean
	public CorsConfigurationSource corsConfigurationSource() {
		List<String> origins = parseOrigins(allowedOrigins);

		CorsConfiguration configuration = new CorsConfiguration();
		configuration.setAllowedOrigins(origins);
		configuration.setAllowedMethods(ALLOWED_METHODS);
		configuration.setAllowedHeaders(ALLOWED_HEADERS);
		configuration.setAllowCredentials(allowCredentials);
		configuration.setMaxAge(3600L);

		// legacy /services chain still uses Basic auth and sessions, so it never gets credentialed CORS
		CorsConfiguration servicesConfiguration = new CorsConfiguration(configuration);
		servicesConfiguration.setAllowCredentials(false);

		UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
		source.registerCorsConfiguration("/api/**", configuration);
		source.registerCorsConfiguration("/services/**", servicesConfiguration);
		return source;
	}

	@Bean
	public FilterRegistrationBean<CorsFilter> corsFilterRegistration(CorsConfigurationSource corsConfigurationSource) {
		FilterRegistrationBean<CorsFilter> registration = new FilterRegistrationBean<>(
				new CorsFilter(corsConfigurationSource));
		registration.addUrlPatterns("/api/*", "/services/*");
		registration.setOrder(Ordered.HIGHEST_PRECEDENCE);
		return registration;
	}

	static List<String> parseOrigins(String value) {
		if (value == null) {
			return Collections.emptyList();
		}
		List<String> origins = Arrays.stream(value.split(","))
				.map(String::trim)
				.filter(origin -> !origin.isEmpty())
				.map(origin -> origin.endsWith("/") ? origin.substring(0, origin.length() - 1) : origin)
				.collect(Collectors.toList());
		for (String origin : origins) {
			if (origin.contains("*") || "null".equalsIgnoreCase(origin)) {
				throw new IllegalStateException("cors.allowedOrigins must list explicit origins, got: " + origin);
			}
		}
		return origins;
	}
}
