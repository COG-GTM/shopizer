package com.salesmanager.shop.store.security;

import java.io.IOException;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.web.AuthenticationEntryPoint;

/**
 * Entry point for the JWT protected REST api: answers with a JSON 401 and a
 * {@code WWW-Authenticate: Bearer} challenge instead of a Basic auth prompt.
 */
public class RestAuthenticationEntryPoint implements AuthenticationEntryPoint {

	private final String realmName;

	public RestAuthenticationEntryPoint(String realmName) {
		this.realmName = realmName;
	}

	@Override
	public void commence(HttpServletRequest request, HttpServletResponse response,
			AuthenticationException authException) throws IOException {
		response.setStatus(HttpStatus.UNAUTHORIZED.value());
		response.setHeader(HttpHeaders.WWW_AUTHENTICATE, "Bearer realm=\"" + realmName + "\"");
		response.setContentType(MediaType.APPLICATION_JSON_VALUE);
		response.getWriter().write("{\"errorCode\":\"401\",\"message\":\"Unauthorized\"}");
	}

	public String getRealmName() {
		return realmName;
	}
}
