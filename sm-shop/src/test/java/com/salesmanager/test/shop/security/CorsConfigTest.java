package com.salesmanager.test.shop.security;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockFilterChain;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.web.filter.CorsFilter;

import com.salesmanager.shop.application.config.CorsConfig;

public class CorsConfigTest {

	private static final String ALLOWED = "https://admin.acme.example";
	private static final String EVIL = "https://evil.example";

	@Test
	public void disallowedOriginIsNotReflected() throws Exception {
		MockHttpServletRequest request = request("GET", "/api/v1/private/users", EVIL);
		MockHttpServletResponse response = new MockHttpServletResponse();
		MockFilterChain chain = new MockFilterChain();

		filter(ALLOWED, true).doFilter(request, response, chain);

		assertEquals(403, response.getStatus());
		assertNull(response.getHeader("Access-Control-Allow-Origin"));
		assertNull(response.getHeader("Access-Control-Allow-Credentials"));
		assertNull(chain.getRequest());
	}

	@Test
	public void disallowedOriginPreflightIsRejected() throws Exception {
		MockHttpServletRequest request = preflight("/api/v1/private/users", EVIL);
		MockHttpServletResponse response = new MockHttpServletResponse();

		filter(ALLOWED, false).doFilter(request, response, new MockFilterChain());

		assertEquals(403, response.getStatus());
		assertNull(response.getHeader("Access-Control-Allow-Origin"));
	}

	@Test
	public void allowedOriginWithoutCredentialsByDefault() throws Exception {
		MockHttpServletRequest request = preflight("/api/v1/auth/customers/profile", ALLOWED);
		MockHttpServletResponse response = new MockHttpServletResponse();

		filter(" " + ALLOWED + "/ , http://localhost:3000", false).doFilter(request, response, new MockFilterChain());

		assertEquals(200, response.getStatus());
		assertEquals(ALLOWED, response.getHeader("Access-Control-Allow-Origin"));
		assertNull(response.getHeader("Access-Control-Allow-Credentials"));
		assertNotNull(response.getHeader("Access-Control-Allow-Methods"));
	}

	@Test
	public void allowedOriginWithCredentialsWhenEnabled() throws Exception {
		MockHttpServletRequest request = request("GET", "/services/public/store", ALLOWED);
		MockHttpServletResponse response = new MockHttpServletResponse();
		MockFilterChain chain = new MockFilterChain();

		filter(ALLOWED, true).doFilter(request, response, chain);

		assertEquals(ALLOWED, response.getHeader("Access-Control-Allow-Origin"));
		assertEquals("true", response.getHeader("Access-Control-Allow-Credentials"));
		assertNotNull(chain.getRequest());
	}

	@Test
	public void noOriginsConfiguredRejectsCrossOrigin() throws Exception {
		MockHttpServletRequest request = request("GET", "/api/v1/products", EVIL);
		MockHttpServletResponse response = new MockHttpServletResponse();

		filter("", false).doFilter(request, response, new MockFilterChain());

		assertEquals(403, response.getStatus());
		assertNull(response.getHeader("Access-Control-Allow-Origin"));
	}

	@Test
	public void requestWithoutOriginPassesThrough() throws Exception {
		MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/v1/products");
		MockHttpServletResponse response = new MockHttpServletResponse();
		MockFilterChain chain = new MockFilterChain();

		filter(ALLOWED, false).doFilter(request, response, chain);

		assertNotNull(chain.getRequest());
		assertNull(response.getHeader("Access-Control-Allow-Origin"));
	}

	@Test
	public void wildcardAndNullOriginsAreRejected() {
		assertThrows(IllegalStateException.class, () -> new CorsConfig("*", false).corsConfigurationSource());
		assertThrows(IllegalStateException.class,
				() -> new CorsConfig("https://*.acme.example", false).corsConfigurationSource());
		assertThrows(IllegalStateException.class, () -> new CorsConfig("null", false).corsConfigurationSource());
	}

	@Test
	public void shopPathsAreNotCovered() {
		assertNull(new CorsConfig(ALLOWED, false).corsConfigurationSource()
				.getCorsConfiguration(request("GET", "/shop/home", EVIL)));
	}

	private CorsFilter filter(String origins, boolean allowCredentials) {
		return new CorsFilter(new CorsConfig(origins, allowCredentials).corsConfigurationSource());
	}

	private MockHttpServletRequest request(String method, String uri, String origin) {
		MockHttpServletRequest request = new MockHttpServletRequest(method, uri);
		request.setServerName("api.acme.example");
		request.setScheme("https");
		request.setServerPort(443);
		request.addHeader("Origin", origin);
		return request;
	}

	private MockHttpServletRequest preflight(String uri, String origin) {
		MockHttpServletRequest request = request("OPTIONS", uri, origin);
		request.addHeader("Access-Control-Request-Method", "GET");
		request.addHeader("Access-Control-Request-Headers", "Authorization");
		return request;
	}
}
