package com.salesmanager.shop.application.config;

import static org.springframework.security.web.servlet.util.matcher.PathPatternRequestMatcher.withDefaults;

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
 * Main entry point for security - customer (shop) - services - admin api - customer api.
 * One ordered {@link SecurityFilterChain} per entry point, each with its own {@link AuthenticationManager}.
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
				path("/"),
				path("/error"),
				path("/resources/**"),
				path("/static/**"),
				path("/services/public/**"),
				path("/swagger-ui.html"));
	}

	/**
	 * shop / customer
	 */
	@Bean
	@Order(1)
	public SecurityFilterChain customerFilterChain(HttpSecurity http,
			@Qualifier("customerDetailsService") UserDetailsService customerDetailsService,
			PasswordEncoder passwordEncoder) throws Exception {
		http
			.securityMatcher(path("/shop/**"))
			.authenticationManager(authenticationManager(customerDetailsService, passwordEncoder))
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
			.httpBasic(basic -> basic.authenticationEntryPoint(entryPoint("shop-realm")))
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
	 * @deprecated
	 */
	@Deprecated
	@Bean
	@Order(2)
	public SecurityFilterChain servicesFilterChain(HttpSecurity http,
			@Qualifier("userDetailsService") WebUserServices userDetailsService,
			ServicesAuthenticationSuccessHandler servicesAuthenticationSuccessHandler,
			PasswordEncoder passwordEncoder) throws Exception {
		http
			.securityMatcher(path("/services/**"))
			.authenticationManager(authenticationManager(userDetailsService, passwordEncoder))
			.csrf(AbstractHttpConfigurer::disable)
			.authorizeHttpRequests(auth -> auth
				.requestMatchers(path("/services/public/**")).permitAll()
				.requestMatchers(path("/services/private/**")).hasRole("AUTH")
				.anyRequest().authenticated())
			.httpBasic(basic -> basic.authenticationEntryPoint(entryPoint("rest-customer-realm")))
			.formLogin(form -> form.successHandler(servicesAuthenticationSuccessHandler));
		return http.build();
	}

	/**
	 * api - private (admin user api)
	 */
	@Bean
	@Order(5)
	public SecurityFilterChain userApiFilterChain(HttpSecurity http,
			JWTAdminServicesImpl jwtUserDetailsService,
			AuthenticationTokenFilter authenticationTokenFilter,
			PasswordEncoder passwordEncoder) throws Exception {
		http
			.securityMatcher(path(API_VERSION + "/private/**"))
			.authenticationManager(authenticationManager(jwtUserDetailsService, passwordEncoder,
					jwtAdminAuthenticationProvider(jwtUserDetailsService)))
			.authorizeHttpRequests(auth -> auth
				.requestMatchers(path(API_VERSION + "/private/login*")).permitAll()
				.requestMatchers(path(API_VERSION + "/private/refresh")).permitAll()
				.requestMatchers(withDefaults().matcher(HttpMethod.OPTIONS, API_VERSION + "/private/**")).permitAll()
				.requestMatchers(path(API_VERSION + "/private/**")).hasRole("AUTH")
				.anyRequest().authenticated())
			.httpBasic(basic -> basic.authenticationEntryPoint(entryPoint("api-admin-realm")))
			.addFilterAfter(authenticationTokenFilter, BasicAuthenticationFilter.class)
			.csrf(AbstractHttpConfigurer::disable);
		return http.build();
	}

	/**
	 * customer api
	 */
	@Bean
	@Order(6)
	public SecurityFilterChain customerApiFilterChain(HttpSecurity http,
			@Qualifier("jwtCustomerDetailsService") UserDetailsService jwtCustomerDetailsService,
			AuthenticationTokenFilter authenticationTokenFilter,
			PasswordEncoder passwordEncoder) throws Exception {
		http
			.securityMatcher(path(API_VERSION + "/auth/**"))
			.authenticationManager(authenticationManager(jwtCustomerDetailsService, passwordEncoder))
			.authorizeHttpRequests(auth -> auth
				.requestMatchers(path(API_VERSION + "/auth/refresh")).permitAll()
				.requestMatchers(path(API_VERSION + "/auth/login")).permitAll()
				.requestMatchers(path(API_VERSION + "/auth/register")).permitAll()
				.requestMatchers(withDefaults().matcher(HttpMethod.OPTIONS, API_VERSION + "/auth/**")).permitAll()
				.requestMatchers(path(API_VERSION + "/auth/**")).hasRole("AUTH_CUSTOMER")
				.anyRequest().authenticated())
			.httpBasic(basic -> basic.authenticationEntryPoint(entryPoint("api-customer-realm")))
			.csrf(AbstractHttpConfigurer::disable)
			.addFilterAfter(authenticationTokenFilter, BasicAuthenticationFilter.class);
		return http.build();
	}

	@Bean
	public JWTAdminAuthenticationProvider jwtAdminAuthenticationProvider(JWTAdminServicesImpl jwtUserDetailsService) {
		JWTAdminAuthenticationProvider provider = new JWTAdminAuthenticationProvider();
		provider.setUserDetailsService(jwtUserDetailsService);
		return provider;
	}

	@Bean
	public JWTCustomerAuthenticationProvider jwtCustomerAuthenticationProvider(
			@Qualifier("jwtCustomerDetailsService") UserDetailsService jwtCustomerDetailsService) {
		JWTCustomerAuthenticationProvider provider = new JWTCustomerAuthenticationProvider();
		provider.setUserDetailsService(jwtCustomerDetailsService);
		return provider;
	}

	private static RequestMatcher path(String pattern) {
		return withDefaults().matcher(pattern);
	}

	private static AuthenticationManager authenticationManager(UserDetailsService userDetailsService,
			PasswordEncoder passwordEncoder, AuthenticationProvider... additionalProviders) {
		DaoAuthenticationProvider dao = new DaoAuthenticationProvider(passwordEncoder);
		dao.setUserDetailsService(userDetailsService);
		AuthenticationProvider[] providers = new AuthenticationProvider[additionalProviders.length + 1];
		providers[0] = dao;
		System.arraycopy(additionalProviders, 0, providers, 1, additionalProviders.length);
		return new ProviderManager(providers);
	}

	private static AuthenticationEntryPoint entryPoint(String realm) {
		BasicAuthenticationEntryPoint entryPoint = new BasicAuthenticationEntryPoint();
		entryPoint.setRealmName(realm);
		return entryPoint;
	}

}
