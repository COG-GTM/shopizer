package com.salesmanager.shop.application.config;

import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpMethod;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.AuthenticationProvider;
import org.springframework.security.authentication.ProviderManager;
import org.springframework.security.authentication.dao.DaoAuthenticationProvider;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configuration.WebSecurityCustomizer;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.AuthenticationEntryPoint;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.www.BasicAuthenticationEntryPoint;
import org.springframework.security.web.authentication.www.BasicAuthenticationFilter;
import org.springframework.security.web.servlet.util.matcher.PathPatternRequestMatcher;
import org.springframework.security.web.util.matcher.RequestMatcher;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

import com.salesmanager.shop.admin.security.UserAuthenticationSuccessHandler;
import com.salesmanager.shop.store.controller.customer.facade.CustomerFacade;
import com.salesmanager.shop.store.security.AuthenticationTokenFilter;
import com.salesmanager.shop.store.security.RestAuthenticationEntryPoint;
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

	/**
	 * Stored hashes are raw (unprefixed) bcrypt in 60 character columns, so a
	 * {@code DelegatingPasswordEncoder} ({bcrypt} prefix) cannot be used without
	 * widening ADMIN_PASSWORD / CUSTOMER_PASSWORD first.
	 */
	@Bean
	public PasswordEncoder passwordEncoder(
			@Value("${shopizer.security.password.bcrypt-strength:10}") int bcryptStrength) {
		return new BCryptPasswordEncoder(BCryptPasswordEncoder.BCryptVersion.$2A, bcryptStrength);
	}

	@Bean
	public CorsConfigurationSource corsConfigurationSource(CorsProperties corsProperties) {
		CorsConfiguration config = corsProperties.toCorsConfiguration();
		UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
		for (String path : CorsProperties.PATHS) {
			source.registerCorsConfiguration(path, config);
		}
		return source;
	}

	private static RequestMatcher path(String pattern) {
		return PathPatternRequestMatcher.withDefaults().matcher(pattern);
	}

	private static RequestMatcher path(HttpMethod method, String pattern) {
		return PathPatternRequestMatcher.withDefaults().matcher(method, pattern);
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

	private static DaoAuthenticationProvider daoProvider(UserDetailsService userDetailsService,
			PasswordEncoder passwordEncoder) {
		DaoAuthenticationProvider provider = new DaoAuthenticationProvider(passwordEncoder);
		provider.setUserDetailsService(userDetailsService);
		return provider;
	}

	/**
	 * shop / customer
	 */
	// Every filter chain sets its own manager; @Primary only satisfies HttpSecurity's single-manager lookup
	@Primary
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
			.securityMatcher(path("/services/**"))
			.authenticationManager(new ProviderManager(daoProvider(userDetailsService, passwordEncoder)))
			.csrf(AbstractHttpConfigurer::disable)
			.cors(Customizer.withDefaults())
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
			.securityMatcher(path(API_VERSION + "/private/**"))
			.authenticationManager(jwtAdminAuthenticationManager)
			.authorizeHttpRequests(auth -> auth
				.requestMatchers(path(API_VERSION + "/private/login*")).permitAll()
				.requestMatchers(path(API_VERSION + "/private/refresh")).permitAll()
				.requestMatchers(path(HttpMethod.OPTIONS, API_VERSION + "/private/**")).permitAll()
				.requestMatchers(path(API_VERSION + "/private/**")).hasRole("AUTH")
				.anyRequest().authenticated())
			.httpBasic(basic -> basic.authenticationEntryPoint(apiAdminAuthenticationEntryPoint()))
			.exceptionHandling(ex -> ex.authenticationEntryPoint(new RestAuthenticationEntryPoint("api-admin-realm")))
			.sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
			.cors(Customizer.withDefaults())
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
			.exceptionHandling(ex -> ex.authenticationEntryPoint(new RestAuthenticationEntryPoint("api-customer-realm")))
			.sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
			.cors(Customizer.withDefaults())
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
