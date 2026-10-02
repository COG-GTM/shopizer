package com.salesmanager.shop.application.config;

import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpMethod;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.ProviderManager;
import org.springframework.security.authentication.dao.DaoAuthenticationProvider;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configuration.WebSecurityCustomizer;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.AuthenticationEntryPoint;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.www.BasicAuthenticationEntryPoint;
import org.springframework.security.web.authentication.www.BasicAuthenticationFilter;
import org.springframework.security.web.servlet.util.matcher.PathPatternRequestMatcher;
import org.springframework.security.web.util.matcher.RequestMatcher;

import com.salesmanager.shop.admin.security.UserAuthenticationSuccessHandler;
import com.salesmanager.shop.admin.security.WebUserServices;
import com.salesmanager.shop.store.controller.customer.facade.CustomerFacade;
import com.salesmanager.shop.store.security.AuthenticationTokenFilter;
import com.salesmanager.shop.store.security.ServicesAuthenticationSuccessHandler;
import com.salesmanager.shop.store.security.admin.JWTAdminAuthenticationProvider;
import com.salesmanager.shop.store.security.admin.JWTAdminServicesImpl;
import com.salesmanager.shop.store.security.customer.JWTCustomerAuthenticationProvider;
import com.salesmanager.shop.store.security.services.CredentialsService;
import com.salesmanager.shop.store.security.services.CredentialsServiceImpl;

/**
 * Main entry point for security - customer - auth - private - services
 *
 * @author dur9213
 *
 */
@Configuration
@EnableWebSecurity
public class MultipleEntryPointsSecurityConfig {

	private static final String API_VERSION = "/api/v*";

	private static RequestMatcher path(String pattern) {
		return PathPatternRequestMatcher.withDefaults().matcher(pattern);
	}

	private static RequestMatcher path(HttpMethod method, String pattern) {
		return PathPatternRequestMatcher.withDefaults().matcher(method, pattern);
	}

	private static DaoAuthenticationProvider daoProvider(UserDetailsService userDetailsService,
			PasswordEncoder passwordEncoder) {
		DaoAuthenticationProvider provider = new DaoAuthenticationProvider(passwordEncoder);
		provider.setUserDetailsService(userDetailsService);
		return provider;
	}

	@Bean
	public AuthenticationTokenFilter authenticationTokenFilter() {
		return new AuthenticationTokenFilter();
	}

	@Bean
	public CredentialsService credentialsService() {
		return new CredentialsServiceImpl();
	}

	@Bean
	public PasswordEncoder passwordEncoder() {
		return new BCryptPasswordEncoder();
	}

	@Bean
	public UserAuthenticationSuccessHandler userAuthenticationSuccessHandler() {
		return new UserAuthenticationSuccessHandler();
	}

	@Bean
	public ServicesAuthenticationSuccessHandler servicesAuthenticationSuccessHandler() {
		return new ServicesAuthenticationSuccessHandler();
	}

	@Bean
	public CustomerFacade customerFacade() {
		return new com.salesmanager.shop.store.controller.customer.facade.CustomerFacadeImpl();
	}

	@Bean
	public WebSecurityCustomizer webSecurityCustomizer() {
		return web -> web.ignoring().requestMatchers(path("/"), path("/error"), path("/resources/**"),
				path("/static/**"), path("/services/public/**"), path("/swagger-ui.html"),
				path("/swagger-ui/**"), path("/v3/api-docs/**"));
	}

	/**
	 * shop / customer
	 */
	@Bean("customerAuthenticationManager")
	@Primary
	public AuthenticationManager customerAuthenticationManager(
			@Qualifier("customerDetailsService") UserDetailsService customerDetailsService,
			PasswordEncoder passwordEncoder) {
		return new ProviderManager(daoProvider(customerDetailsService, passwordEncoder));
	}

	@Bean
	public AuthenticationEntryPoint shopAuthenticationEntryPoint() {
		BasicAuthenticationEntryPoint entryPoint = new BasicAuthenticationEntryPoint();
		entryPoint.setRealmName("shop-realm");
		return entryPoint;
	}

	@Bean
	@Order(1)
	public SecurityFilterChain shopSecurityFilterChain(HttpSecurity http,
			@Qualifier("customerAuthenticationManager") AuthenticationManager customerAuthenticationManager)
			throws Exception {
		http
			.securityMatcher(path("/shop/**"))
			.authenticationManager(customerAuthenticationManager)
			.csrf(AbstractHttpConfigurer::disable)
			.authorizeHttpRequests(auth -> auth
				.requestMatchers(path("/shop/")).permitAll()
				.requestMatchers(path("/shop/**")).permitAll()
				.requestMatchers(path("/shop/customer/logon*")).permitAll()
				.requestMatchers(path("/shop/customer/registration*")).permitAll()
				.requestMatchers(path("/shop/customer/logout*")).permitAll()
				.requestMatchers(path("/shop/customer/customLogon*")).permitAll()
				.requestMatchers(path("/shop/customer/denied*")).permitAll()
				.requestMatchers(path("/shop/customer/**")).hasRole("AUTH_CUSTOMER")
				.anyRequest().authenticated())
			.httpBasic(basic -> basic.authenticationEntryPoint(shopAuthenticationEntryPoint()))
			.logout(logout -> logout
				.logoutUrl("/shop/customer/logout")
				.logoutSuccessUrl("/shop/")
				.deleteCookies("JSESSIONID")
				.invalidateHttpSession(false))
			.exceptionHandling(ex -> ex.accessDeniedPage("/shop/"));
		return http.build();
	}

	/**
	 * services api v0
	 *
	 * @deprecated
	 */
	@Deprecated
	@Bean
	public AuthenticationEntryPoint servicesAuthenticationEntryPoint() {
		BasicAuthenticationEntryPoint entryPoint = new BasicAuthenticationEntryPoint();
		entryPoint.setRealmName("rest-customer-realm");
		return entryPoint;
	}

	@Bean
	@Order(2)
	public SecurityFilterChain servicesSecurityFilterChain(HttpSecurity http,
			WebUserServices userDetailsService, PasswordEncoder passwordEncoder,
			ServicesAuthenticationSuccessHandler servicesAuthenticationSuccessHandler) throws Exception {
		http
			.securityMatcher(path("/services/**"))
			.authenticationManager(new ProviderManager(daoProvider(userDetailsService, passwordEncoder)))
			.csrf(AbstractHttpConfigurer::disable)
			.authorizeHttpRequests(auth -> auth
				.requestMatchers(path("/services/public/**")).permitAll()
				.requestMatchers(path("/services/private/**")).hasRole("AUTH")
				.anyRequest().authenticated())
			.httpBasic(basic -> basic.authenticationEntryPoint(servicesAuthenticationEntryPoint()))
			.formLogin(form -> form.successHandler(servicesAuthenticationSuccessHandler));
		return http.build();
	}

	/**
	 * api - private (admin user api)
	 */
	@Bean
	public JWTAdminAuthenticationProvider jwtAdminAuthenticationProvider(JWTAdminServicesImpl jwtUserDetailsService) {
		JWTAdminAuthenticationProvider provider = new JWTAdminAuthenticationProvider();
		provider.setUserDetailsService(jwtUserDetailsService);
		return provider;
	}

	@Bean("jwtAdminAuthenticationManager")
	public AuthenticationManager jwtAdminAuthenticationManager(JWTAdminServicesImpl jwtUserDetailsService,
			JWTAdminAuthenticationProvider jwtAdminAuthenticationProvider, PasswordEncoder passwordEncoder) {
		return new ProviderManager(daoProvider(jwtUserDetailsService, passwordEncoder),
				jwtAdminAuthenticationProvider);
	}

	@Bean
	public AuthenticationEntryPoint apiAdminAuthenticationEntryPoint() {
		BasicAuthenticationEntryPoint entryPoint = new BasicAuthenticationEntryPoint();
		entryPoint.setRealmName("api-admin-realm");
		return entryPoint;
	}

	@Bean
	@Order(5)
	public SecurityFilterChain userApiSecurityFilterChain(HttpSecurity http,
			AuthenticationTokenFilter authenticationTokenFilter,
			@Qualifier("jwtAdminAuthenticationManager") AuthenticationManager jwtAdminAuthenticationManager)
			throws Exception {
		http
			.securityMatcher(path(API_VERSION + "/private/**"))
			.authenticationManager(jwtAdminAuthenticationManager)
			.authorizeHttpRequests(auth -> auth
				.requestMatchers(path(API_VERSION + "/private/login*")).permitAll()
				.requestMatchers(path(API_VERSION + "/private/refresh")).permitAll()
				.requestMatchers(path(HttpMethod.OPTIONS, API_VERSION + "/private/**")).permitAll()
				.requestMatchers(path(API_VERSION + "/private/**")).hasRole("AUTH")
				.anyRequest().authenticated())
			.httpBasic(basic -> basic.authenticationEntryPoint(apiAdminAuthenticationEntryPoint()))
			.addFilterAfter(authenticationTokenFilter, BasicAuthenticationFilter.class)
			.csrf(AbstractHttpConfigurer::disable);
		return http.build();
	}

	/**
	 * customer api
	 */
	@Bean
	public JWTCustomerAuthenticationProvider jwtCustomerAuthenticationProvider(
			@Qualifier("jwtCustomerDetailsService") UserDetailsService jwtCustomerDetailsService) {
		JWTCustomerAuthenticationProvider provider = new JWTCustomerAuthenticationProvider();
		provider.setUserDetailsService(jwtCustomerDetailsService);
		return provider;
	}

	@Bean("jwtCustomerAuthenticationManager")
	public AuthenticationManager jwtCustomerAuthenticationManager(
			@Qualifier("jwtCustomerDetailsService") UserDetailsService jwtCustomerDetailsService,
			PasswordEncoder passwordEncoder) {
		return new ProviderManager(daoProvider(jwtCustomerDetailsService, passwordEncoder));
	}

	@Bean
	public AuthenticationEntryPoint apiCustomerAuthenticationEntryPoint() {
		BasicAuthenticationEntryPoint entryPoint = new BasicAuthenticationEntryPoint();
		entryPoint.setRealmName("api-customer-realm");
		return entryPoint;
	}

	@Bean
	@Order(6)
	public SecurityFilterChain customerApiSecurityFilterChain(HttpSecurity http,
			AuthenticationTokenFilter authenticationTokenFilter,
			@Qualifier("jwtCustomerAuthenticationManager") AuthenticationManager jwtCustomerAuthenticationManager)
			throws Exception {
		http
			.securityMatcher(path(API_VERSION + "/auth/**"))
			.authenticationManager(jwtCustomerAuthenticationManager)
			.authorizeHttpRequests(auth -> auth
				.requestMatchers(path(API_VERSION + "/auth/refresh")).permitAll()
				.requestMatchers(path(API_VERSION + "/auth/login")).permitAll()
				.requestMatchers(path(API_VERSION + "/auth/register")).permitAll()
				.requestMatchers(path(HttpMethod.OPTIONS, API_VERSION + "/auth/**")).permitAll()
				.requestMatchers(path(API_VERSION + "/auth/**")).hasRole("AUTH_CUSTOMER")
				.anyRequest().authenticated())
			.httpBasic(basic -> basic.authenticationEntryPoint(apiCustomerAuthenticationEntryPoint()))
			.csrf(AbstractHttpConfigurer::disable)
			.addFilterAfter(authenticationTokenFilter, BasicAuthenticationFilter.class);
		return http.build();
	}

}
