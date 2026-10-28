package com.salesmanager.test.shop.unit.security;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

import javax.servlet.FilterChain;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import org.junit.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.test.util.ReflectionTestUtils;

import com.salesmanager.shop.store.security.AuthenticationTokenFilter;
import com.salesmanager.shop.store.security.common.CustomAuthenticationManager;

public class AuthenticationTokenFilterTest {

	@Test
	public void securityApiLegacyPathUsesAdminAuthentication() throws Exception {
		assertFilterManagers("/api/v1/sec/private/groups", true, false);
	}

	@Test
	public void securityApiCanonicalPathUsesAdminAuthentication() throws Exception {
		assertFilterManagers("/api/v1/private/security/groups", true, false);
	}

	@Test
	public void customerPathUsesCustomerAuthentication() throws Exception {
		assertFilterManagers("/api/v1/auth/customer/profile", false, true);
	}

	@Test
	public void missingTokenDoesNotAuthenticateButContinuesTheChain() throws Exception {
		FilterFixture fixture = filterFor("/api/v1/sec/private/groups", null);

		fixture.filter.doFilter(fixture.request, fixture.response, fixture.chain);

		verify(fixture.adminManager, never()).authenticateRequest(fixture.request, fixture.response);
		verify(fixture.customerManager, never()).authenticateRequest(fixture.request, fixture.response);
		verify(fixture.chain, times(1)).doFilter(fixture.request, fixture.response);
	}

	@Test
	public void publicPathDoesNotAuthenticateButContinuesTheChain() throws Exception {
		assertFilterManagers("/api/v1/products", false, false);
	}

	private void assertFilterManagers(String path, boolean admin, boolean customer) throws Exception {
		FilterFixture fixture = filterFor(path, "Bearer test-token");

		fixture.filter.doFilter(fixture.request, fixture.response, fixture.chain);

		if (admin) {
			verify(fixture.adminManager, times(1)).authenticateRequest(fixture.request, fixture.response);
		} else {
			verify(fixture.adminManager, never()).authenticateRequest(fixture.request, fixture.response);
		}
		if (customer) {
			verify(fixture.customerManager, times(1)).authenticateRequest(fixture.request, fixture.response);
		} else {
			verify(fixture.customerManager, never()).authenticateRequest(fixture.request, fixture.response);
		}
		verify(fixture.chain, times(1)).doFilter(fixture.request, fixture.response);
	}

	private FilterFixture filterFor(String path, String authorization) {
		AuthenticationTokenFilter filter = new AuthenticationTokenFilter();
		CustomAuthenticationManager adminManager = mock(CustomAuthenticationManager.class);
		CustomAuthenticationManager customerManager = mock(CustomAuthenticationManager.class);
		ReflectionTestUtils.setField(filter, "tokenHeader", "Authorization");
		ReflectionTestUtils.setField(filter, "jwtCustomAdminAuthenticationManager", adminManager);
		ReflectionTestUtils.setField(filter, "jwtCustomCustomerAuthenticationManager", customerManager);

		MockHttpServletRequest request = new MockHttpServletRequest();
		request.setRequestURI(path);
		if (authorization != null) {
			request.addHeader("Authorization", authorization);
		}

		return new FilterFixture(filter, adminManager, customerManager, request, new MockHttpServletResponse(),
				mock(FilterChain.class));
	}

	private static final class FilterFixture {
		private final AuthenticationTokenFilter filter;
		private final CustomAuthenticationManager adminManager;
		private final CustomAuthenticationManager customerManager;
		private final MockHttpServletRequest request;
		private final MockHttpServletResponse response;
		private final FilterChain chain;

		private FilterFixture(AuthenticationTokenFilter filter, CustomAuthenticationManager adminManager,
				CustomAuthenticationManager customerManager, MockHttpServletRequest request,
				MockHttpServletResponse response, FilterChain chain) {
			this.filter = filter;
			this.adminManager = adminManager;
			this.customerManager = customerManager;
			this.request = request;
			this.response = response;
			this.chain = chain;
		}
	}
}
