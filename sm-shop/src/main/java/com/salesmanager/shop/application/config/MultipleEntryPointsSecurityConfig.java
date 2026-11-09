package com.salesmanager.shop.application.config;

import static org.springframework.security.web.util.matcher.AntPathRequestMatcher.antMatcher;

import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpMethod;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.AuthenticationProvider;
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

import com.salesmanager.shop.admin.security.UserAuthenticationSuccessHandler;
import com.salesmanager.shop.store.controller.customer.facade.CustomerFacade;
import com.salesmanager.shop.store.security.AuthenticationTokenFilter;
import com.salesmanager.shop.store.security.ServicesAuthenticationSuccessHandler;
import com.salesmanager.shop.store.security.admin.JWTAdminAuthenticationProvider;
import com.salesmanager.shop.store.security.customer.JWTCustomerAuthenticationProvider;
import com.salesmanager.shop.store.security.services.CredentialsService;
import com.salesmanager.shop.store.security.services.CredentialsServiceImpl;

/**
 * Main entry point for security - customer - services - private (admin) api - customer api.
 *
 * Each realm is a dedicated {@link SecurityFilterChain} with its own
 * {@link AuthenticationManager}.
 */
@Configuration
@EnableWebSecurity
public class MultipleEntryPointsSecurityConfig {

	private static final String API_VERSION = "/api/v*";

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
		return web -> web.ignoring().requestMatchers(
				antMatcher("/"),
				antMatcher("/error"),
				antMatcher("/resources/**"),
				antMatcher("/static/**"),
				antMatcher("/services/public/**"),
				antMatcher("/swagger-ui.html"));
	}

	private static DaoAuthenticationProvider daoProvider(UserDetailsService userDetailsService,
			PasswordEncoder passwordEncoder) {
		DaoAuthenticationProvider provider = new DaoAuthenticationProvider(passwordEncoder);
		provider.setUserDetailsService(userDetailsService);
		return provider;
	}

	/**
	 * shop / customer
	 */
	@Bean("customerAuthenticationManager")
	public AuthenticationManager customerAuthenticationManager(
			@Qualifier("customerDetailsService") UserDetailsService customerDetailsService,
			PasswordEncoder passwordEncoder) {
		return new ProviderManager(daoProvider(customerDetailsService, passwordEncoder));
	}

	@Bean
	public AuthenticationEntryPoint shopAuthenticationEntryPoint() {
		return basicEntryPoint("shop-realm");
	}

	@Bean
	@Order(1)
	public SecurityFilterChain customerSecurityFilterChain(HttpSecurity http,
			@Qualifier("customerAuthenticationManager") AuthenticationManager customerAuthenticationManager)
			throws Exception {
		http
			.securityMatcher(antMatcher("/shop/**"))
			.authenticationManager(customerAuthenticationManager)
			.csrf(AbstractHttpConfigurer::disable)
			.authorizeHttpRequests(auth -> auth
				.requestMatchers(antMatcher("/shop/")).permitAll()
				.requestMatchers(antMatcher("/shop/**")).permitAll()
				.requestMatchers(antMatcher("/shop/customer/logon*")).permitAll()
				.requestMatchers(antMatcher("/shop/customer/registration*")).permitAll()
				.requestMatchers(antMatcher("/shop/customer/logout*")).permitAll()
				.requestMatchers(antMatcher("/shop/customer/customLogon*")).permitAll()
				.requestMatchers(antMatcher("/shop/customer/denied*")).permitAll()
				.requestMatchers(antMatcher("/shop/customer/**")).hasRole("AUTH_CUSTOMER")
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
		return basicEntryPoint("rest-customer-realm");
	}

	@Deprecated
	@Bean
	@Order(2)
	public SecurityFilterChain servicesSecurityFilterChain(HttpSecurity http,
			@Qualifier("userDetailsService") UserDetailsService userDetailsService,
			PasswordEncoder passwordEncoder,
			ServicesAuthenticationSuccessHandler servicesAuthenticationSuccessHandler) throws Exception {
		http
			.securityMatcher(antMatcher("/services/**"))
			.authenticationManager(new ProviderManager(daoProvider(userDetailsService, passwordEncoder)))
			.csrf(AbstractHttpConfigurer::disable)
			.authorizeHttpRequests(auth -> auth
				.requestMatchers(antMatcher("/services/public/**")).permitAll()
				.requestMatchers(antMatcher("/services/private/**")).hasRole("AUTH")
				.anyRequest().authenticated())
			.httpBasic(basic -> basic.authenticationEntryPoint(servicesAuthenticationEntryPoint()))
			.formLogin(form -> form.successHandler(servicesAuthenticationSuccessHandler));
		return http.build();
	}

	/**
	 * api - private (admin user api)
	 */
	@Bean
	public JWTAdminAuthenticationProvider jwtAdminAuthenticationProvider(
			@Qualifier("jwtAdminDetailsService") UserDetailsService jwtAdminDetailsService) {
		JWTAdminAuthenticationProvider provider = new JWTAdminAuthenticationProvider();
		provider.setUserDetailsService(jwtAdminDetailsService);
		return provider;
	}

	@Bean("jwtAdminAuthenticationManager")
	public AuthenticationManager jwtAdminAuthenticationManager(
			@Qualifier("jwtAdminDetailsService") UserDetailsService jwtAdminDetailsService,
			PasswordEncoder passwordEncoder,
			@Qualifier("jwtAdminAuthenticationProvider") AuthenticationProvider jwtAdminAuthenticationProvider) {
		return new ProviderManager(daoProvider(jwtAdminDetailsService, passwordEncoder),
				jwtAdminAuthenticationProvider);
	}

	@Bean
	public AuthenticationEntryPoint apiAdminAuthenticationEntryPoint() {
		return basicEntryPoint("api-admin-realm");
	}

	@Bean
	@Order(5)
	public SecurityFilterChain userApiSecurityFilterChain(HttpSecurity http,
			@Qualifier("jwtAdminAuthenticationManager") AuthenticationManager jwtAdminAuthenticationManager,
			AuthenticationTokenFilter authenticationTokenFilter) throws Exception {
		http
			.securityMatcher(antMatcher(API_VERSION + "/private/**"))
			.authenticationManager(jwtAdminAuthenticationManager)
			.authorizeHttpRequests(auth -> auth
				.requestMatchers(antMatcher(API_VERSION + "/private/login*")).permitAll()
				.requestMatchers(antMatcher(API_VERSION + "/private/refresh")).permitAll()
				.requestMatchers(antMatcher(HttpMethod.OPTIONS, API_VERSION + "/private/**")).permitAll()
				.requestMatchers(antMatcher(API_VERSION + "/private/**")).hasRole("AUTH")
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
		return basicEntryPoint("api-customer-realm");
	}

	@Bean
	@Order(6)
	public SecurityFilterChain customerApiSecurityFilterChain(HttpSecurity http,
			@Qualifier("jwtCustomerAuthenticationManager") AuthenticationManager jwtCustomerAuthenticationManager,
			AuthenticationTokenFilter authenticationTokenFilter) throws Exception {
		http
			.securityMatcher(antMatcher(API_VERSION + "/auth/**"))
			.authenticationManager(jwtCustomerAuthenticationManager)
			.authorizeHttpRequests(auth -> auth
				.requestMatchers(antMatcher(API_VERSION + "/auth/refresh")).permitAll()
				.requestMatchers(antMatcher(API_VERSION + "/auth/login")).permitAll()
				.requestMatchers(antMatcher(API_VERSION + "/auth/register")).permitAll()
				.requestMatchers(antMatcher(HttpMethod.OPTIONS, API_VERSION + "/auth/**")).permitAll()
				.requestMatchers(antMatcher(API_VERSION + "/auth/**")).hasRole("AUTH_CUSTOMER")
				.anyRequest().authenticated())
			.httpBasic(basic -> basic.authenticationEntryPoint(apiCustomerAuthenticationEntryPoint()))
			.csrf(AbstractHttpConfigurer::disable)
			.addFilterAfter(authenticationTokenFilter, BasicAuthenticationFilter.class);
		return http.build();
	}

	private static AuthenticationEntryPoint basicEntryPoint(String realm) {
		BasicAuthenticationEntryPoint entryPoint = new BasicAuthenticationEntryPoint();
		entryPoint.setRealmName(realm);
		return entryPoint;
	}
}
