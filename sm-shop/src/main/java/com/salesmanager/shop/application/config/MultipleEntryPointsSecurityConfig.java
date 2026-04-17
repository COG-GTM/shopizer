package com.salesmanager.shop.application.config;

import org.springframework.beans.factory.annotation.Autowired;
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
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.AuthenticationEntryPoint;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.www.BasicAuthenticationEntryPoint;
import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.security.web.authentication.www.BasicAuthenticationFilter;

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
 * Main entry point for security - admin - customer - auth - private - services
 *
 * Modernized for Spring Security 6.x: removed WebSecurityConfigurerAdapter,
 * using component-based SecurityFilterChain approach.
 *
 * @author dur9213
 */
@Configuration
@EnableWebSecurity
public class MultipleEntryPointsSecurityConfig {

	private static final String API_VERSION = "/api/v*";

	@Autowired
	private UserDetailsService customerDetailsService;

	@Autowired
	private WebUserServices webUserDetailsService;

	@Autowired
	private JWTAdminServicesImpl jwtUserDetailsService;

	@Autowired
	private UserDetailsService jwtCustomerDetailsService;

	@Autowired
	private ServicesAuthenticationSuccessHandler servicesAuthSuccessHandler;

	@Bean
	public AuthenticationTokenFilter authenticationTokenFilter() {
		return new AuthenticationTokenFilter();
	}

	/**
	 * Prevent Spring Boot from auto-registering the AuthenticationTokenFilter
	 * as a servlet filter. It is only used within specific SecurityFilterChains.
	 */
	@Bean
	public FilterRegistrationBean<AuthenticationTokenFilter> authenticationTokenFilterRegistration(
			AuthenticationTokenFilter filter) {
		FilterRegistrationBean<AuthenticationTokenFilter> registration =
				new FilterRegistrationBean<>(filter);
		registration.setEnabled(false);
		return registration;
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

	/**
	 * WebSecurityCustomizer replaces WebSecurity.ignoring() patterns
	 */
	@Bean
	public WebSecurityCustomizer webSecurityCustomizer() {
		return (web) -> web.ignoring().requestMatchers(
				"/",
				"/error",
				"/resources/**",
				"/static/**",
				"/services/public/**",
				"/swagger-ui.html",
				"/swagger-ui/**",
				"/v3/api-docs/**"
		);
	}

	// --- Authentication Managers ---

	@Bean("customerAuthenticationManager")
	public AuthenticationManager customerAuthenticationManager() {
		DaoAuthenticationProvider provider = new DaoAuthenticationProvider();
		provider.setUserDetailsService(customerDetailsService);
		provider.setPasswordEncoder(passwordEncoder());
		return new ProviderManager(provider);
	}

	@Bean("servicesAuthenticationManager")
	public AuthenticationManager servicesAuthenticationManager() {
		DaoAuthenticationProvider provider = new DaoAuthenticationProvider();
		provider.setUserDetailsService(webUserDetailsService);
		provider.setPasswordEncoder(passwordEncoder());
		return new ProviderManager(provider);
	}

	@Bean("jwtAdminAuthenticationManager")
	public AuthenticationManager jwtAdminAuthenticationManager() {
		DaoAuthenticationProvider daoProvider = new DaoAuthenticationProvider();
		daoProvider.setUserDetailsService(jwtUserDetailsService);
		daoProvider.setPasswordEncoder(passwordEncoder());
		return new ProviderManager(jwtAdminAuthenticationProvider(), daoProvider);
	}

	@Bean("jwtCustomerAuthenticationManager")
	public AuthenticationManager jwtCustomerAuthenticationManager() {
		DaoAuthenticationProvider daoProvider = new DaoAuthenticationProvider();
		daoProvider.setUserDetailsService(jwtCustomerDetailsService);
		daoProvider.setPasswordEncoder(passwordEncoder());
		return new ProviderManager(jwtCustomerAuthenticationProvider(), daoProvider);
	}

	// --- Authentication Providers ---

	@Bean
	public AuthenticationProvider jwtAdminAuthenticationProvider() {
		JWTAdminAuthenticationProvider provider = new JWTAdminAuthenticationProvider();
		provider.setUserDetailsService(jwtUserDetailsService);
		return provider;
	}

	@Bean
	public AuthenticationProvider jwtCustomerAuthenticationProvider() {
		JWTCustomerAuthenticationProvider provider = new JWTCustomerAuthenticationProvider();
		provider.setUserDetailsService(jwtCustomerDetailsService);
		return provider;
	}

	// --- Authentication Entry Points ---

	@Bean
	public AuthenticationEntryPoint shopAuthenticationEntryPoint() {
		BasicAuthenticationEntryPoint entryPoint = new BasicAuthenticationEntryPoint();
		entryPoint.setRealmName("shop-realm");
		return entryPoint;
	}

	@Bean
	public AuthenticationEntryPoint servicesAuthenticationEntryPoint() {
		BasicAuthenticationEntryPoint entryPoint = new BasicAuthenticationEntryPoint();
		entryPoint.setRealmName("rest-customer-realm");
		return entryPoint;
	}

	@Bean
	public AuthenticationEntryPoint apiAdminAuthenticationEntryPoint() {
		BasicAuthenticationEntryPoint entryPoint = new BasicAuthenticationEntryPoint();
		entryPoint.setRealmName("api-admin-realm");
		return entryPoint;
	}

	@Bean
	public AuthenticationEntryPoint apiCustomerAuthenticationEntryPoint() {
		BasicAuthenticationEntryPoint entryPoint = new BasicAuthenticationEntryPoint();
		entryPoint.setRealmName("api-customer-realm");
		return entryPoint;
	}

	// --- Security Filter Chains ---

	/**
	 * shop / customer (Order 1)
	 */
	@Bean
	@Order(1)
	public SecurityFilterChain customerFilterChain(HttpSecurity http) throws Exception {
		http
			.securityMatcher("/shop/**")
			.authenticationManager(customerAuthenticationManager())
			.csrf(csrf -> csrf.disable())
			.authorizeHttpRequests(auth -> auth
				.requestMatchers("/shop/customer/logon*").permitAll()
				.requestMatchers("/shop/customer/registration*").permitAll()
				.requestMatchers("/shop/customer/logout*").permitAll()
				.requestMatchers("/shop/customer/customLogon*").permitAll()
				.requestMatchers("/shop/customer/denied*").permitAll()
				.requestMatchers("/shop/customer/**").hasRole("AUTH_CUSTOMER")
				.requestMatchers("/shop/").permitAll()
				.requestMatchers("/shop/**").permitAll()
				.anyRequest().authenticated()
			)
			.httpBasic(basic -> basic
				.authenticationEntryPoint(shopAuthenticationEntryPoint())
			)
			.logout(logout -> logout
				.logoutUrl("/shop/customer/logout")
				.logoutSuccessUrl("/shop/")
				.invalidateHttpSession(true)
				.deleteCookies("JSESSIONID")
			)
			.exceptionHandling(ex -> ex
				.accessDeniedPage("/shop/")
			);

		return http.build();
	}

	/**
	 * services api v0 (Order 2)
	 * @deprecated
	 */
	@Bean
	@Order(2)
	public SecurityFilterChain servicesFilterChain(HttpSecurity http) throws Exception {
		http
			.securityMatcher("/services/**")
			.authenticationManager(servicesAuthenticationManager())
			.csrf(csrf -> csrf.disable())
			.authorizeHttpRequests(auth -> auth
				.requestMatchers("/services/public/**").permitAll()
				.requestMatchers("/services/private/**").hasRole("AUTH")
				.anyRequest().authenticated()
			)
			.httpBasic(basic -> basic
				.authenticationEntryPoint(servicesAuthenticationEntryPoint())
			)
			.formLogin(form -> form
				.successHandler(servicesAuthSuccessHandler)
			);

		return http.build();
	}

	/**
	 * Admin ConfigurationAdapter (Order 3) - commented out in original code
	 * Preserved as comment for reference.
	 */
	// AdminConfigurationAdapter was commented out in the original code

	/**
	 * api - private / admin user api (Order 5)
	 */
	@Bean
	@Order(5)
	public SecurityFilterChain userApiFilterChain(HttpSecurity http) throws Exception {
		http
			.securityMatcher(API_VERSION + "/private/**")
			.authenticationManager(jwtAdminAuthenticationManager())
			.authorizeHttpRequests(auth -> auth
				.requestMatchers(API_VERSION + "/private/login*").permitAll()
				.requestMatchers(API_VERSION + "/private/refresh").permitAll()
				.requestMatchers(HttpMethod.OPTIONS, API_VERSION + "/private/**").permitAll()
				.requestMatchers(API_VERSION + "/private/**").hasRole("AUTH")
				.anyRequest().authenticated()
			)
			.httpBasic(basic -> basic
				.authenticationEntryPoint(apiAdminAuthenticationEntryPoint())
			)
			.addFilterAfter(authenticationTokenFilter(), BasicAuthenticationFilter.class)
			.csrf(csrf -> csrf.disable());

		return http.build();
	}

	/**
	 * customer api (Order 6)
	 */
	@Bean
	@Order(6)
	public SecurityFilterChain customerApiFilterChain(HttpSecurity http) throws Exception {
		http
			.securityMatcher(API_VERSION + "/auth/**")
			.authenticationManager(jwtCustomerAuthenticationManager())
			.authorizeHttpRequests(auth -> auth
				.requestMatchers(API_VERSION + "/auth/refresh").permitAll()
				.requestMatchers(API_VERSION + "/auth/login").permitAll()
				.requestMatchers(API_VERSION + "/auth/register").permitAll()
				.requestMatchers(HttpMethod.OPTIONS, API_VERSION + "/auth/**").permitAll()
				.requestMatchers(API_VERSION + "/auth/**").hasRole("AUTH_CUSTOMER")
				.anyRequest().authenticated()
			)
			.httpBasic(basic -> basic
				.authenticationEntryPoint(apiCustomerAuthenticationEntryPoint())
			)
			.csrf(csrf -> csrf.disable())
			.addFilterAfter(authenticationTokenFilter(), BasicAuthenticationFilter.class);

		return http.build();
	}

}
