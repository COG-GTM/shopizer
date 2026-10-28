package com.salesmanager.test.shop.security;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.Set;

import org.junit.jupiter.api.Test;
import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.core.Ordered;
import org.springframework.mock.web.MockFilterChain;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.filter.CorsFilter;

import com.salesmanager.shop.application.config.CorsConfig;

public class CorsConfigEdgeCasesTest {

	private static final String ALLOWED = "https://admin.acme.example";

	@Test
	public void emptyAndNullOriginsProduceNoAllowedOrigins() {
		assertEquals(Collections.emptyList(), allowedOrigins(null));
		assertEquals(Collections.emptyList(), allowedOrigins(""));
		assertEquals(Collections.emptyList(), allowedOrigins(" , ,"));
	}

	@Test
	public void trailingSlashIsRemovedFromConfiguredOrigin() {
		assertEquals(Arrays.asList(ALLOWED), allowedOrigins(ALLOWED + "/"));
	}

	@Test
	public void invalidConfiguredOriginsAreRejected() {
		assertThrows(IllegalStateException.class,
				() -> new CorsConfig("https://good.example,https://*.acme.example", false)
						.corsConfigurationSource());
		assertThrows(IllegalStateException.class,
				() -> new CorsConfig("https://good.example,NULL", false).corsConfigurationSource());
		assertThrows(IllegalStateException.class,
				() -> new CorsConfig("https://good.example,*", false).corsConfigurationSource());
	}

	@Test
	public void lookalikeOriginsAreRejected() throws Exception {
		for (String origin : Arrays.asList("https://admin.acme.example.evil.com",
				"https://evil.admin.acme.example", "http://admin.acme.example",
				"https://admin.acme.example:8443")) {
			MockHttpServletResponse response = new MockHttpServletResponse();
			MockFilterChain chain = new MockFilterChain();

			filter(ALLOWED).doFilter(request("GET", "/api/v1/products", origin), response, chain);

			assertEquals(403, response.getStatus(), origin);
			assertNull(response.getHeader("Access-Control-Allow-Origin"), origin);
			assertNull(chain.getRequest(), origin);
		}
	}

	@Test
	public void allowedPreflightAdvertisesMaxAgeAndSupportedMethods() throws Exception {
		MockHttpServletRequest request = preflight("GET", "Authorization");
		MockHttpServletResponse response = new MockHttpServletResponse();

		filter(ALLOWED).doFilter(request, response, new MockFilterChain());

		assertEquals(200, response.getStatus());
		assertEquals("3600", response.getHeader("Access-Control-Max-Age"));
		String methods = response.getHeader("Access-Control-Allow-Methods");
		for (String method : Arrays.asList("GET", "HEAD", "POST", "PUT", "DELETE", "PATCH")) {
			assertTrue(methods.contains(method), method);
		}
	}

	@Test
	public void disallowedPreflightMethodAndHeaderAreRejected() throws Exception {
		MockHttpServletResponse methodResponse = new MockHttpServletResponse();
		filter(ALLOWED).doFilter(preflight("TRACE", "Authorization"), methodResponse, new MockFilterChain());
		assertEquals(403, methodResponse.getStatus());

		MockHttpServletResponse headerResponse = new MockHttpServletResponse();
		filter(ALLOWED).doFilter(preflight("GET", "X-Evil-Header"), headerResponse, new MockFilterChain());
		assertEquals(403, headerResponse.getStatus());
	}

	@Test
	public void allowedPreflightEchoesRequestedAllowedHeaders() throws Exception {
		MockHttpServletResponse response = new MockHttpServletResponse();

		filter(ALLOWED).doFilter(preflight("POST", "Authorization, Content-Type"), response, new MockFilterChain());

		assertEquals(200, response.getStatus());
		String allowedHeaders = response.getHeader("Access-Control-Allow-Headers");
		assertTrue(allowedHeaders.contains("Authorization"));
		assertTrue(allowedHeaders.contains("Content-Type"));
	}

	@Test
	public void allowedOriginResponseVariesByOrigin() throws Exception {
		MockHttpServletResponse response = new MockHttpServletResponse();
		MockFilterChain chain = new MockFilterChain();

		filter(ALLOWED).doFilter(request("GET", "/api/v1/products", ALLOWED), response, chain);

		assertTrue(response.getHeaders("Vary").stream().anyMatch(value -> value.contains("Origin")));
		assertEquals(ALLOWED, response.getHeader("Access-Control-Allow-Origin"));
		assertTrue(chain.getRequest() != null);
	}

	@Test
	public void shopPathWithEvilOriginPassesThroughWithoutCorsHeaders() throws Exception {
		MockHttpServletResponse response = new MockHttpServletResponse();
		MockFilterChain chain = new MockFilterChain();

		filter(ALLOWED).doFilter(request("GET", "/shop/home.html", "https://evil.example"), response, chain);

		assertTrue(chain.getRequest() != null);
		assertNull(response.getHeader("Access-Control-Allow-Origin"));
		assertEquals(200, response.getStatus());
	}

	@Test
	public void corsFilterRegistrationUsesHighestPrecedenceAndApiPatterns() {
		CorsConfigurationSource source = new CorsConfig(ALLOWED, false).corsConfigurationSource();
		FilterRegistrationBean<CorsFilter> registration = new CorsConfig(ALLOWED, false)
				.corsFilterRegistration(source);

		assertEquals(Ordered.HIGHEST_PRECEDENCE, registration.getOrder());
		Set<String> expectedPatterns = new HashSet<>(Arrays.asList("/api/*", "/services/*"));
		assertEquals(expectedPatterns, registration.getUrlPatterns());
	}

	private java.util.List<String> allowedOrigins(String value) {
		CorsConfigurationSource source = new CorsConfig(value, false).corsConfigurationSource();
		CorsConfiguration configuration = source.getCorsConfiguration(request("GET", "/api/v1/products", null));
		return configuration.getAllowedOrigins();
	}

	private CorsFilter filter(String origins) {
		return new CorsFilter(new CorsConfig(origins, false).corsConfigurationSource());
	}

	private MockHttpServletRequest request(String method, String uri, String origin) {
		MockHttpServletRequest request = new MockHttpServletRequest(method, uri);
		request.setServerName("api.acme.example");
		request.setScheme("https");
		request.setServerPort(443);
		if (origin != null) {
			request.addHeader("Origin", origin);
		}
		return request;
	}

	private MockHttpServletRequest preflight(String method, String headers) {
		MockHttpServletRequest request = request("OPTIONS", "/api/v1/products", ALLOWED);
		request.addHeader("Access-Control-Request-Method", method);
		request.addHeader("Access-Control-Request-Headers", headers);
		return request;
	}
}
