package com.salesmanager.test.shop.unit.security;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.util.Arrays;
import java.util.List;

import org.junit.Test;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.support.DefaultListableBeanFactory;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.authentication.AuthenticationEventPublisher;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.ProviderManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.authentication.dao.DaoAuthenticationProvider;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.core.userdetails.User;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.AuthenticationEntryPoint;
import org.springframework.test.util.ReflectionTestUtils;

import com.salesmanager.shop.admin.security.WebUserServices;
import com.salesmanager.shop.application.config.MultipleEntryPointsSecurityConfig;
import com.salesmanager.shop.store.security.admin.JWTAdminAuthenticationProvider;
import com.salesmanager.shop.store.security.admin.JWTAdminServicesImpl;

public class MultipleEntryPointsSecurityConfigTest {

	private final MultipleEntryPointsSecurityConfig config = new MultipleEntryPointsSecurityConfig();
	private final PasswordEncoder passwordEncoder = new BCryptPasswordEncoder();

	@Test
	public void authenticationProviderBeanUsesTheAdminDetailsServiceArgument() {
		List<Field> injectedFields = Arrays.stream(MultipleEntryPointsSecurityConfig.class.getDeclaredFields())
				.filter(field -> !Modifier.isStatic(field.getModifiers()))
				.collect(java.util.stream.Collectors.toList());
		assertTrue(injectedFields.isEmpty());

		JWTAdminServicesImpl service = mock(JWTAdminServicesImpl.class);
		JWTAdminAuthenticationProvider provider =
				(JWTAdminAuthenticationProvider) config.authenticationProvider(service);

		assertSame(service, ReflectionTestUtils.invokeMethod(provider, "getUserDetailsService"));
	}

	@Test
	public void customerAuthenticationManagerUsesOneDaoProviderAndHidesUnknownUsers() {
		UserDetailsService service = userDetailsService();
		AuthenticationManager manager =
				config.customerAuthenticationManager(service, passwordEncoder, emptyEventPublisher());

		assertDaoAuthenticationManager(manager);
	}

	@Test
	public void servicesAuthenticationManagerUsesOneDaoProviderAndHidesUnknownUsers() {
		WebUserServices service = mock(WebUserServices.class);
		stubUserDetails(service);
		AuthenticationManager manager =
				config.servicesAuthenticationManager(service, passwordEncoder, emptyEventPublisher());

		assertDaoAuthenticationManager(manager);
	}

	@Test
	public void jwtCustomerAuthenticationManagerUsesOneDaoProviderAndHidesUnknownUsers() {
		UserDetailsService service = userDetailsService();
		AuthenticationManager manager =
				config.jwtCustomerAuthenticationManager(service, passwordEncoder, emptyEventPublisher());

		assertDaoAuthenticationManager(manager);
	}

	@Test
	public void jwtAdminAuthenticationManagerPreservesProviderOrderAndIdentity() {
		org.springframework.security.authentication.AuthenticationProvider jwtProvider =
				mock(org.springframework.security.authentication.AuthenticationProvider.class);
		JWTAdminServicesImpl service = mock(JWTAdminServicesImpl.class);
		AuthenticationManager manager = config.jwtAdminAuthenticationManager(jwtProvider, service, passwordEncoder,
				emptyEventPublisher());

		assertTrue(manager instanceof ProviderManager);
		List<org.springframework.security.authentication.AuthenticationProvider> providers =
				((ProviderManager) manager).getProviders();
		assertEquals(2, providers.size());
		assertSame(jwtProvider, providers.get(0));
		assertTrue(providers.get(1) instanceof DaoAuthenticationProvider);
	}

	@Test
	public void eventPublisherReceivesSuccessAndFailureEvents() {
		AuthenticationEventPublisher publisher = mock(AuthenticationEventPublisher.class);
		DefaultListableBeanFactory beanFactory = new DefaultListableBeanFactory();
		beanFactory.registerSingleton("authenticationEventPublisher", publisher);
		UserDetailsService service = userDetailsService();
		AuthenticationManager manager = config.customerAuthenticationManager(service, passwordEncoder,
				beanFactory.getBeanProvider(AuthenticationEventPublisher.class));

		manager.authenticate(new UsernamePasswordAuthenticationToken("valid", "secret"));
		assertThrows(BadCredentialsException.class,
				() -> manager.authenticate(new UsernamePasswordAuthenticationToken("valid", "wrong")));

		verify(publisher).publishAuthenticationSuccess(any(Authentication.class));
		verify(publisher).publishAuthenticationFailure(any(AuthenticationException.class), any(Authentication.class));
	}

	@Test
	public void managerWorksWhenNoEventPublisherIsAvailable() {
		UserDetailsService service = userDetailsService();
		AuthenticationManager manager =
				config.customerAuthenticationManager(service, passwordEncoder, emptyEventPublisher());

		assertTrue(manager.authenticate(new UsernamePasswordAuthenticationToken("valid", "secret")).isAuthenticated());
	}

	@Test
	public void entryPointsReturnUnauthorizedWithTheirConfiguredRealms() throws Exception {
		assertEntryPoint(config.shopAuthenticationEntryPoint(), "shop-realm");
		assertEntryPoint(config.servicesAuthenticationEntryPoint(), "rest-customer-realm");
		assertEntryPoint(config.apiAdminAuthenticationEntryPoint(), "api-admin-realm");
		assertEntryPoint(config.apiCustomerAuthenticationEntryPoint(), "api-customer-realm");
	}

	private UserDetailsService userDetailsService() {
		UserDetailsService service = mock(UserDetailsService.class);
		stubUserDetails(service);
		return service;
	}

	private void stubUserDetails(UserDetailsService service) {
		UserDetails user = User.withUsername("valid")
				.password(passwordEncoder.encode("secret"))
				.authorities("ROLE_AUTH")
				.build();
		when(service.loadUserByUsername("valid")).thenReturn(user);
		when(service.loadUserByUsername("missing")).thenThrow(new UsernameNotFoundException("missing"));
	}

	private void assertDaoAuthenticationManager(AuthenticationManager manager) {
		assertTrue(manager instanceof ProviderManager);
		List<org.springframework.security.authentication.AuthenticationProvider> providers =
				((ProviderManager) manager).getProviders();
		assertEquals(1, providers.size());
		assertTrue(providers.get(0) instanceof DaoAuthenticationProvider);

		Authentication authenticated =
				manager.authenticate(new UsernamePasswordAuthenticationToken("valid", "secret"));
		assertTrue(authenticated.isAuthenticated());
		assertThrows(BadCredentialsException.class,
				() -> manager.authenticate(new UsernamePasswordAuthenticationToken("valid", "wrong")));
		assertThrows(BadCredentialsException.class,
				() -> manager.authenticate(new UsernamePasswordAuthenticationToken("missing", "secret")));
	}

	private ObjectProvider<AuthenticationEventPublisher> emptyEventPublisher() {
		return new DefaultListableBeanFactory().getBeanProvider(AuthenticationEventPublisher.class);
	}

	private void assertEntryPoint(AuthenticationEntryPoint entryPoint, String realm) throws Exception {
		MockHttpServletResponse response = new MockHttpServletResponse();
		entryPoint.commence(new MockHttpServletRequest(), response,
				new BadCredentialsException("bad credentials"));

		assertEquals(401, response.getStatus());
		assertEquals("Basic realm=\"" + realm + "\"", response.getHeader("WWW-Authenticate"));
	}
}
