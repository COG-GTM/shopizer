package com.salesmanager.shop.application.config;

import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.context.annotation.Primary;
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
import org.springframework.security.web.authentication.www.BasicAuthenticationFilter;
import org.springframework.security.web.servlet.util.matcher.PathPatternRequestMatcher;

import com.salesmanager.shop.admin.security.UserAuthenticationSuccessHandler;
import com.salesmanager.shop.admin.security.WebUserServices;
import com.salesmanager.shop.store.controller.customer.facade.CustomerFacade;
import com.salesmanager.shop.store.security.AuthenticationTokenFilter;
import com.salesmanager.shop.store.security.ServicesAuthenticationSuccessHandler;
import com.salesmanager.shop.store.security.admin.JWTAdminAuthenticationProvider;
import com.salesmanager.shop.store.security.customer.JWTCustomerAuthenticationProvider;
import com.salesmanager.shop.store.security.services.CredentialsService;
import com.salesmanager.shop.store.security.services.CredentialsServiceImpl;

@Configuration
@EnableWebSecurity
public class MultipleEntryPointsSecurityConfig {

	private static final String API_VERSION = "/api/v*";

	private static PathPatternRequestMatcher m(String pattern) {
		return PathPatternRequestMatcher.withDefaults().matcher(pattern);
	}

	private static PathPatternRequestMatcher m(HttpMethod method, String pattern) {
		return PathPatternRequestMatcher.withDefaults().matcher(method, pattern);
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
		return web -> web.ignoring().requestMatchers(
				m("/"), m("/error"), m("/resources/**"), m("/static/**"),
				m("/services/public/**"), m("/swagger-ui.html"));
	}

	@Bean
	@Order(1)
	public SecurityFilterChain customerFilterChain(
			HttpSecurity http,
			@Qualifier("customerAuthenticationManager") AuthenticationManager authenticationManager)
			throws Exception {
		http
			.securityMatcher(m("/shop/**"))
			.authenticationManager(authenticationManager)
			.csrf(csrf -> csrf.disable())
			.authorizeHttpRequests(auth -> auth
				.requestMatchers(m("/shop/")).permitAll()
				.requestMatchers(m("/shop/**")).permitAll()
				.requestMatchers(m("/shop/customer/logon*")).permitAll()
				.requestMatchers(m("/shop/customer/registration*")).permitAll()
				.requestMatchers(m("/shop/customer/logout*")).permitAll()
				.requestMatchers(m("/shop/customer/customLogon*")).permitAll()
				.requestMatchers(m("/shop/customer/denied*")).permitAll()
				.requestMatchers(m("/shop/customer/**")).hasRole("AUTH_CUSTOMER")
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

	@Bean
	public AuthenticationEntryPoint shopAuthenticationEntryPoint() {
		BasicAuthenticationEntryPoint entryPoint = new BasicAuthenticationEntryPoint();
		entryPoint.setRealmName("shop-realm");
		return entryPoint;
	}

	@Bean("customerAuthenticationManager")
	@Primary
	public AuthenticationManager customerAuthenticationManager(
			@Qualifier("customerDetailsService") UserDetailsService customerDetailsService) {
		return new ProviderManager(dao(customerDetailsService));
	}

	@Bean
	@Order(2)
	public SecurityFilterChain servicesApiFilterChain(
			HttpSecurity http,
			WebUserServices webUserServices,
			ServicesAuthenticationSuccessHandler successHandler) throws Exception {
		http
			.securityMatcher(m("/services/**"))
			.authenticationManager(new ProviderManager(dao(webUserServices)))
			.csrf(csrf -> csrf.disable())
			.authorizeHttpRequests(auth -> auth
				.requestMatchers(m("/services/public/**")).permitAll()
				.requestMatchers(m("/services/private/**")).hasRole("AUTH")
				.anyRequest().authenticated())
			.httpBasic(basic -> basic.authenticationEntryPoint(servicesAuthenticationEntryPoint()))
			.formLogin(form -> form.successHandler(successHandler));
		return http.build();
	}

	@Bean
	public AuthenticationEntryPoint servicesAuthenticationEntryPoint() {
		BasicAuthenticationEntryPoint entryPoint = new BasicAuthenticationEntryPoint();
		entryPoint.setRealmName("rest-customer-realm");
		return entryPoint;
	}

	@Bean
	@Order(5)
	public SecurityFilterChain userApiFilterChain(
			HttpSecurity http,
			@Qualifier("jwtAdminAuthenticationManager") AuthenticationManager authenticationManager,
			AuthenticationTokenFilter authenticationTokenFilter) throws Exception {
		http
			.securityMatcher(m(API_VERSION + "/private/**"))
			.authenticationManager(authenticationManager)
			.csrf(csrf -> csrf.disable())
			.authorizeHttpRequests(auth -> auth
				.requestMatchers(m(API_VERSION + "/private/login*")).permitAll()
				.requestMatchers(m(API_VERSION + "/private/refresh")).permitAll()
				.requestMatchers(m(HttpMethod.OPTIONS, API_VERSION + "/private/**")).permitAll()
				.requestMatchers(m(API_VERSION + "/private/**")).hasRole("AUTH")
				.anyRequest().authenticated())
			.httpBasic(basic -> basic.authenticationEntryPoint(apiAdminAuthenticationEntryPoint()))
			.addFilterAfter(authenticationTokenFilter, BasicAuthenticationFilter.class);
		return http.build();
	}

	@Bean("jwtAdminAuthenticationProvider")
	public AuthenticationProvider jwtAdminAuthenticationProvider(
			@Qualifier("jwtAdminDetailsService") UserDetailsService jwtUserDetailsService) {
		JWTAdminAuthenticationProvider provider = new JWTAdminAuthenticationProvider();
		provider.setUserDetailsService(jwtUserDetailsService);
		return provider;
	}

	@Bean("jwtAdminAuthenticationManager")
	public AuthenticationManager jwtAdminAuthenticationManager(
			@Qualifier("jwtAdminDetailsService") UserDetailsService jwtUserDetailsService,
			@Qualifier("jwtAdminAuthenticationProvider") AuthenticationProvider provider) {
		return new ProviderManager(dao(jwtUserDetailsService), provider);
	}

	@Bean
	public AuthenticationEntryPoint apiAdminAuthenticationEntryPoint() {
		BasicAuthenticationEntryPoint entryPoint = new BasicAuthenticationEntryPoint();
		entryPoint.setRealmName("api-admin-realm");
		return entryPoint;
	}

	@Bean
	@Order(6)
	public SecurityFilterChain customerApiFilterChain(
			HttpSecurity http,
			@Qualifier("jwtCustomerAuthenticationManager") AuthenticationManager authenticationManager,
			AuthenticationTokenFilter authenticationTokenFilter) throws Exception {
		http
			.securityMatcher(m(API_VERSION + "/auth/**"))
			.authenticationManager(authenticationManager)
			.csrf(csrf -> csrf.disable())
			.authorizeHttpRequests(auth -> auth
				.requestMatchers(m(API_VERSION + "/auth/refresh")).permitAll()
				.requestMatchers(m(API_VERSION + "/auth/login")).permitAll()
				.requestMatchers(m(API_VERSION + "/auth/register")).permitAll()
				.requestMatchers(m(HttpMethod.OPTIONS, API_VERSION + "/auth/**")).permitAll()
				.requestMatchers(m(API_VERSION + "/auth/**")).hasRole("AUTH_CUSTOMER")
				.anyRequest().authenticated())
			.httpBasic(basic -> basic.authenticationEntryPoint(apiCustomerAuthenticationEntryPoint()))
			.addFilterAfter(authenticationTokenFilter, BasicAuthenticationFilter.class);
		return http.build();
	}

	@Bean("jwtCustomerAuthenticationProvider")
	public AuthenticationProvider jwtCustomerAuthenticationProvider(
			@Qualifier("jwtCustomerDetailsService") UserDetailsService jwtCustomerDetailsService) {
		JWTCustomerAuthenticationProvider provider = new JWTCustomerAuthenticationProvider();
		provider.setUserDetailsService(jwtCustomerDetailsService);
		return provider;
	}

	@Bean("jwtCustomerAuthenticationManager")
	public AuthenticationManager jwtCustomerAuthenticationManager(
			@Qualifier("jwtCustomerDetailsService") UserDetailsService jwtCustomerDetailsService,
			@Qualifier("jwtCustomerAuthenticationProvider") AuthenticationProvider provider) {
		return new ProviderManager(dao(jwtCustomerDetailsService), provider);
	}

	@Bean
	public AuthenticationEntryPoint apiCustomerAuthenticationEntryPoint() {
		BasicAuthenticationEntryPoint entryPoint = new BasicAuthenticationEntryPoint();
		entryPoint.setRealmName("api-customer-realm");
		return entryPoint;
	}

	private DaoAuthenticationProvider dao(UserDetailsService userDetailsService) {
		DaoAuthenticationProvider provider = new DaoAuthenticationProvider(userDetailsService);
		provider.setPasswordEncoder(passwordEncoder());
		return provider;
	}
}
