package com.salesmanager.test.shop.integration.security;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertNotSame;
import static org.junit.Assert.assertTrue;

import java.util.Arrays;
import java.util.List;

import javax.servlet.Filter;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.SpringBootTest.WebEnvironment;
import org.springframework.context.ApplicationContext;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.AuthenticationProvider;
import org.springframework.security.web.FilterChainProxy;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.security.web.authentication.logout.LogoutFilter;
import org.springframework.security.web.authentication.www.BasicAuthenticationFilter;
import org.springframework.security.web.csrf.CsrfFilter;
import org.springframework.test.context.junit4.SpringRunner;

import com.salesmanager.shop.application.ShopApplication;
import com.salesmanager.shop.store.security.AuthenticationTokenFilter;
import com.salesmanager.shop.store.security.admin.JWTAdminAuthenticationProvider;
import com.salesmanager.shop.store.security.customer.JWTCustomerAuthenticationProvider;
import com.salesmanager.test.shop.common.ServicesTestSupport;

@RunWith(SpringRunner.class)
@SpringBootTest(classes = ShopApplication.class, webEnvironment = WebEnvironment.RANDOM_PORT)
public class SecurityFilterChainWiringIntegrationTest extends ServicesTestSupport {

	@Autowired
	private FilterChainProxy filterChainProxy;

	@Autowired
	private ApplicationContext applicationContext;

	@Test
	public void ignoredAndUnmatchedPathsHaveTheExpectedFilters() {
		for (String path : Arrays.asList("/", "/error", "/resources/a.css", "/static/a.js",
				"/services/public/x", "/swagger-ui.html")) {
			List<Filter> filters = filterChainProxy.getFilters(path);
			assertNotNull(path, filters);
			assertTrue(path, filters.isEmpty());
		}

		assertNull(filterChainProxy.getFilters("/api/v1/products"));
		assertNull(filterChainProxy.getFilters("/actuator/health"));
	}

	@Test
	public void privateApiAndCustomerApiPlaceTokenFilterAfterBasicWithoutCsrf() {
		for (String path : Arrays.asList("/api/v1/private/x", "/api/v2/private/x", "/api/v1/auth/x")) {
			List<Filter> filters = filterChainProxy.getFilters(path);
			int basicIndex = indexOf(filters, BasicAuthenticationFilter.class);
			int tokenIndex = indexOf(filters, AuthenticationTokenFilter.class);

			assertTrue(path, basicIndex >= 0);
			assertEquals(path, basicIndex + 1, tokenIndex);
			assertFalse(path, contains(filters, CsrfFilter.class));
		}
	}

	@Test
	public void shopAndServicesChainsHaveOnlyTheirExpectedAuthenticationFilters() {
		List<Filter> shopFilters = filterChainProxy.getFilters("/shop/x");
		assertTrue(contains(shopFilters, BasicAuthenticationFilter.class));
		assertTrue(contains(shopFilters, LogoutFilter.class));
		assertFalse(contains(shopFilters, AuthenticationTokenFilter.class));
		assertFalse(contains(shopFilters, CsrfFilter.class));

		List<Filter> servicesFilters = filterChainProxy.getFilters("/services/x");
		assertTrue(contains(servicesFilters, BasicAuthenticationFilter.class));
		assertTrue(contains(servicesFilters, UsernamePasswordAuthenticationFilter.class));
		assertFalse(contains(servicesFilters, AuthenticationTokenFilter.class));
		assertFalse(contains(servicesFilters, CsrfFilter.class));
	}

	@Test
	public void securityBeansUseTheExpectedProviderAndDistinctManagers() {
		assertTrue(applicationContext.getBean("authenticationProvider", AuthenticationProvider.class)
				instanceof JWTAdminAuthenticationProvider);
		assertTrue(applicationContext.getBeansOfType(JWTCustomerAuthenticationProvider.class).isEmpty());

		AuthenticationManager customer = applicationContext.getBean("customerAuthenticationManager",
				AuthenticationManager.class);
		AuthenticationManager services = applicationContext.getBean("servicesAuthenticationManager",
				AuthenticationManager.class);
		AuthenticationManager admin = applicationContext.getBean("jwtAdminAuthenticationManager",
				AuthenticationManager.class);
		AuthenticationManager jwtCustomer = applicationContext.getBean("jwtCustomerAuthenticationManager",
				AuthenticationManager.class);

		assertNotSame(customer, services);
		assertNotSame(customer, admin);
		assertNotSame(customer, jwtCustomer);
		assertNotSame(services, admin);
		assertNotSame(services, jwtCustomer);
		assertNotSame(admin, jwtCustomer);
	}

	private int indexOf(List<Filter> filters, Class<?> type) {
		for (int index = 0; index < filters.size(); index++) {
			if (type.isInstance(filters.get(index))) {
				return index;
			}
		}
		return -1;
	}

	private boolean contains(List<Filter> filters, Class<?> type) {
		return filters.stream().anyMatch(type::isInstance);
	}
}
