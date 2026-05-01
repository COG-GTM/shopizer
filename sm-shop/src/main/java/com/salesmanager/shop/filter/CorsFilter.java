package com.salesmanager.shop.filter;

import org.apache.commons.lang3.StringUtils;
import org.springframework.web.servlet.HandlerInterceptor;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

public class CorsFilter implements HandlerInterceptor {

		public CorsFilter() {
			
		}

		@Override
	   public boolean preHandle(
	            HttpServletRequest request,
	            HttpServletResponse response,
	            Object handler) throws Exception {
		   
        	HttpServletResponse httpResponse = (HttpServletResponse) response;
        	
        	String origin = "*";
        	if(!StringUtils.isBlank(request.getHeader("origin"))) {
        		origin = request.getHeader("origin");
        	}
	
	        httpResponse.setHeader("Access-Control-Allow-Methods", "POST, GET, PUT, OPTIONS, DELETE, PATCH");
        	httpResponse.setHeader("Access-Control-Allow-Headers", "X-Auth-Token, Content-Type, Authorization, Cache-Control, X-Requested-With");
        	httpResponse.setHeader("Access-Control-Allow-Origin", origin);
	        
        	return true;
			
		}
}
