package com.salesmanager.shop.filter;

import java.util.Arrays;
import java.util.Collections;
import java.util.Set;
import java.util.stream.Collectors;

import javax.annotation.PostConstruct;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import org.apache.commons.lang3.StringUtils;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.handler.HandlerInterceptorAdapter;

@Component
public class CorsFilter extends HandlerInterceptorAdapter {

		@Value("${cors.allowed.origins:}")
		private String allowedOriginsConfig;

		private Set<String> allowedOrigins = Collections.emptySet();

		public CorsFilter() {
			
		}

		@PostConstruct
		public void init() {
			if (!StringUtils.isBlank(allowedOriginsConfig)) {
				allowedOrigins = Arrays.stream(allowedOriginsConfig.split(","))
						.map(String::trim)
						.filter(s -> !s.isEmpty())
						.collect(Collectors.toSet());
			}
		}

		/**
		 * Allows public web services to work from allowed origins
		 */
	   public boolean preHandle(
	            HttpServletRequest request,
	            HttpServletResponse response,
	            Object handler) throws Exception {

        	response.setHeader("Access-Control-Allow-Methods", "POST, GET, PUT, OPTIONS, DELETE, PATCH");
        	response.setHeader("Access-Control-Allow-Headers", "X-Auth-Token, Content-Type, Authorization, Cache-Control, X-Requested-With");

        	String requestOrigin = request.getHeader("origin");
        	if (!StringUtils.isBlank(requestOrigin) && !allowedOrigins.isEmpty() && allowedOrigins.contains(requestOrigin)) {
        		response.setHeader("Access-Control-Allow-Origin", requestOrigin);
        	}
        	// If allowlist is empty or origin is not in allowlist, do not set Access-Control-Allow-Origin (secure by default)

        	return true;
			
		}
}
