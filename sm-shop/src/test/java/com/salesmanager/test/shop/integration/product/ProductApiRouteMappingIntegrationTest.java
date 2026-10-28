package com.salesmanager.test.shop.integration.product;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import java.util.HashSet;
import java.util.Set;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.SpringBootTest.WebEnvironment;
import org.springframework.test.context.junit4.SpringRunner;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.method.HandlerMethod;
import org.springframework.web.servlet.mvc.method.RequestMappingInfo;
import org.springframework.web.servlet.mvc.method.annotation.RequestMappingHandlerMapping;

import com.salesmanager.shop.application.ShopApplication;
import com.salesmanager.shop.store.api.v1.product.ProductApi;

@SpringBootTest(classes = ShopApplication.class, webEnvironment = WebEnvironment.RANDOM_PORT)
@RunWith(SpringRunner.class)
public class ProductApiRouteMappingIntegrationTest {

	@Autowired
	@Qualifier("requestMappingHandlerMapping")
	private RequestMappingHandlerMapping requestMappingHandlerMapping;

	@Test
	public void productApiExposesNoAuthWriteRoutes() {
		Set<String> routes = productApiRoutes();

		for (String route : routes) {
			assertFalse("unexpected auth write route: " + route,
					route.matches("(POST|PUT|PATCH|DELETE) /api/v1/auth/.*"));
		}
	}

	@Test
	public void productApiKeepsPrivateWriteRoutes() {
		Set<String> routes = productApiRoutes();

		assertTrue(routes.contains("POST /api/v1/private/product"));
		assertTrue(routes.contains("PUT /api/v1/private/product/{id}"));
		assertTrue(routes.contains("PATCH /api/v1/private/product/{id}"));
		assertTrue(routes.contains("DELETE /api/v1/private/product/{id}"));
	}

	private Set<String> productApiRoutes() {
		Set<String> routes = new HashSet<>();
		for (java.util.Map.Entry<RequestMappingInfo, HandlerMethod> entry
				: requestMappingHandlerMapping.getHandlerMethods().entrySet()) {
			if (entry.getValue().getBeanType() != ProductApi.class) {
				continue;
			}
			Set<String> patterns = entry.getKey().getPatternsCondition().getPatterns();
			Set<RequestMethod> methods = entry.getKey().getMethodsCondition().getMethods();
			for (String pattern : patterns) {
				for (RequestMethod method : methods) {
					routes.add(method.name() + " " + pattern);
				}
			}
		}
		return routes;
	}
}
