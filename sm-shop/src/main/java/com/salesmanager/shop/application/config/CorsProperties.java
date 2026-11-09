package com.salesmanager.shop.application.config;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;
import org.springframework.web.cors.CorsConfiguration;

/**
 * CORS settings applied to the REST api ({@code /api/**}) and legacy services
 * ({@code /services/**}), bound from {@code shopizer.cors.*}.
 */
@Component
@ConfigurationProperties(prefix = "shopizer.cors")
public class CorsProperties {

	static final String[] PATHS = { "/api/**", "/services/**" };

	private List<String> allowedOriginPatterns = new ArrayList<>(List.of("*"));

	private List<String> allowedMethods = new ArrayList<>(
			List.of("GET", "POST", "PUT", "PATCH", "DELETE", "OPTIONS"));

	private List<String> allowedHeaders = new ArrayList<>(
			List.of("X-Auth-Token", "Content-Type", "Authorization", "Cache-Control", "X-Requested-With"));

	private boolean allowCredentials = true;

	private Duration maxAge = Duration.ofHours(1);

	public CorsConfiguration toCorsConfiguration() {
		CorsConfiguration config = new CorsConfiguration();
		config.setAllowedOriginPatterns(allowedOriginPatterns);
		config.setAllowedMethods(allowedMethods);
		config.setAllowedHeaders(allowedHeaders);
		config.setAllowCredentials(allowCredentials);
		config.setMaxAge(maxAge);
		return config;
	}

	public List<String> getAllowedOriginPatterns() {
		return allowedOriginPatterns;
	}

	public void setAllowedOriginPatterns(List<String> allowedOriginPatterns) {
		this.allowedOriginPatterns = allowedOriginPatterns;
	}

	public List<String> getAllowedMethods() {
		return allowedMethods;
	}

	public void setAllowedMethods(List<String> allowedMethods) {
		this.allowedMethods = allowedMethods;
	}

	public List<String> getAllowedHeaders() {
		return allowedHeaders;
	}

	public void setAllowedHeaders(List<String> allowedHeaders) {
		this.allowedHeaders = allowedHeaders;
	}

	public boolean isAllowCredentials() {
		return allowCredentials;
	}

	public void setAllowCredentials(boolean allowCredentials) {
		this.allowCredentials = allowCredentials;
	}

	public Duration getMaxAge() {
		return maxAge;
	}

	public void setMaxAge(Duration maxAge) {
		this.maxAge = maxAge;
	}
}
