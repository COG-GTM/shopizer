package com.salesmanager.shop.application.config;

import static org.springframework.security.web.servlet.util.matcher.PathPatternRequestMatcher.withDefaults;

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

import com.salesmanager.shop.admin.security.UserAuthenticationSuccessHandler;
import com.salesmanager.shop.store.controller.customer.facade.CustomerFacade;
import com.salesmanager.shop.store.security.AuthenticationTokenFilter;
import com.salesmanager.shop.store.security.ServicesAuthenticationSuccessHandler;
import com.salesmanager.shop.store.security.services.CredentialsService;
import com.salesmanager.shop.store.security.services.CredentialsServiceImpl;

/**
 * Main entry point for security - customer (shop) - services (v0) - admin api (private) - customer api (auth).
 * Each entry point is a {@link SecurityFilterChain} bean with its own {@link AuthenticationManager}.
 */
@Configuration
@EnableWebSecurity
public class MultipleEntryPointsSecurityConfig {

	private static final String API_VERSION = "/api/v*";

	private static final PathPatternRequestMatcher.Builder PATHS = withDefaults();

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
				PATHS.matcher("/"),
				PATHS.matcher("/error"),
				PATHS.matcher("/resources/**"),
				PATHS.matcher("/static/**"),
				PATHS.matcher("/services/public/**"));
	}

	private static AuthenticationManager daoAuthenticationManager(UserDetailsService userDetailsService,
			PasswordEncoder passwordEncoder) {
		DaoAuthenticationProvider provider = new DaoAuthenticationProvider(passwordEncoder);
		provider.setUserDetailsService(userDetailsService);
		ProviderManager manager = new ProviderManager(provider);
		manager.setEraseCredentialsAfterAuthentication(false);
		return manager;
	}

	private static AuthenticationEntryPoint basicEntryPoint(String realm) {
		BasicAuthenticationEntryPoint entryPoint = new BasicAuthenticationEntryPoint();
		entryPoint.setRealmName(realm);
		entryPoint.afterPropertiesSet();
		return entryPoint;
	}

	@Bean("customerAuthenticationManager")
	@Primary
	public AuthenticationManager customerAuthenticationManager(
			@Qualifier("customerDetailsService") UserDetailsService customerDetailsService,
			PasswordEncoder passwordEncoder) {
		return daoAuthenticationManager(customerDetailsService, passwordEncoder);
	}

	@Bean("servicesAuthenticationManager")
	public AuthenticationManager servicesAuthenticationManager(
			@Qualifier("userDetailsService") UserDetailsService userDetailsService,
			PasswordEncoder passwordEncoder) {
		return daoAuthenticationManager(userDetailsService, passwordEncoder);
	}

	@Bean("jwtAdminAuthenticationManager")
	public AuthenticationManager jwtAdminAuthenticationManager(
			@Qualifier("jwtAdminDetailsService") UserDetailsService jwtAdminDetailsService,
			PasswordEncoder passwordEncoder) {
		return daoAuthenticationManager(jwtAdminDetailsService, passwordEncoder);
	}

	@Bean("jwtCustomerAuthenticationManager")
	public AuthenticationManager jwtCustomerAuthenticationManager(
			@Qualifier("jwtCustomerDetailsService") UserDetailsService jwtCustomerDetailsService,
			PasswordEncoder passwordEncoder) {
		return daoAuthenticationManager(jwtCustomerDetailsService, passwordEncoder);
	}

	/**
	 * shop / customer
	 */
	@Bean
	@Order(1)
	public SecurityFilterChain customerSecurityFilterChain(HttpSecurity http,
			@Qualifier("customerAuthenticationManager") AuthenticationManager customerAuthenticationManager)
			throws Exception {
		http
			.securityMatcher(PATHS.matcher("/shop/**"))
			.authenticationManager(customerAuthenticationManager)
			.csrf(AbstractHttpConfigurer::disable)
			.authorizeHttpRequests(auth -> auth
				.requestMatchers(PATHS.matcher("/shop/")).permitAll()
				.requestMatchers(PATHS.matcher("/shop/**")).permitAll()
				.requestMatchers(PATHS.matcher("/shop/customer/logon*")).permitAll()
				.requestMatchers(PATHS.matcher("/shop/customer/registration*")).permitAll()
				.requestMatchers(PATHS.matcher("/shop/customer/logout*")).permitAll()
				.requestMatchers(PATHS.matcher("/shop/customer/customLogon*")).permitAll()
				.requestMatchers(PATHS.matcher("/shop/customer/denied*")).permitAll()
				.requestMatchers(PATHS.matcher("/shop/customer/**")).hasRole("AUTH_CUSTOMER")
				.anyRequest().authenticated())
			.httpBasic(basic -> basic.authenticationEntryPoint(basicEntryPoint("shop-realm")))
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
	@Order(2)
	public SecurityFilterChain servicesSecurityFilterChain(HttpSecurity http,
			@Qualifier("servicesAuthenticationManager") AuthenticationManager servicesAuthenticationManager,
			ServicesAuthenticationSuccessHandler servicesAuthenticationSuccessHandler) throws Exception {
		http
			.securityMatcher(PATHS.matcher("/services/**"))
			.authenticationManager(servicesAuthenticationManager)
			.csrf(AbstractHttpConfigurer::disable)
			.authorizeHttpRequests(auth -> auth
				.requestMatchers(PATHS.matcher("/services/public/**")).permitAll()
				.requestMatchers(PATHS.matcher("/services/private/**")).hasRole("AUTH")
				.anyRequest().authenticated())
			.httpBasic(basic -> basic.authenticationEntryPoint(basicEntryPoint("rest-customer-realm")))
			.formLogin(form -> form.successHandler(servicesAuthenticationSuccessHandler));
		return http.build();
	}

	/**
	 * admin user api (private)
	 */
	@Bean
	@Order(5)
	public SecurityFilterChain adminApiSecurityFilterChain(HttpSecurity http,
			@Qualifier("jwtAdminAuthenticationManager") AuthenticationManager jwtAdminAuthenticationManager,
			AuthenticationTokenFilter authenticationTokenFilter) throws Exception {
		http
			.securityMatcher(PATHS.matcher(API_VERSION + "/private/**"))
			.authenticationManager(jwtAdminAuthenticationManager)
			.authorizeHttpRequests(auth -> auth
				.requestMatchers(PATHS.matcher(API_VERSION + "/private/login*")).permitAll()
				.requestMatchers(PATHS.matcher(API_VERSION + "/private/refresh")).permitAll()
				.requestMatchers(PATHS.matcher(HttpMethod.OPTIONS, API_VERSION + "/private/**")).permitAll()
				.requestMatchers(PATHS.matcher(API_VERSION + "/private/**")).hasRole("AUTH")
				.anyRequest().authenticated())
			.httpBasic(basic -> basic.authenticationEntryPoint(basicEntryPoint("api-admin-realm")))
			.addFilterAfter(authenticationTokenFilter, BasicAuthenticationFilter.class)
			.csrf(AbstractHttpConfigurer::disable);
		return http.build();
	}

	/**
	 * customer api (auth)
	 */
	@Bean
	@Order(6)
	public SecurityFilterChain customerApiSecurityFilterChain(HttpSecurity http,
			@Qualifier("jwtCustomerAuthenticationManager") AuthenticationManager jwtCustomerAuthenticationManager,
			AuthenticationTokenFilter authenticationTokenFilter) throws Exception {
		http
			.securityMatcher(PATHS.matcher(API_VERSION + "/auth/**"))
			.authenticationManager(jwtCustomerAuthenticationManager)
			.authorizeHttpRequests(auth -> auth
				.requestMatchers(PATHS.matcher(API_VERSION + "/auth/refresh")).permitAll()
				.requestMatchers(PATHS.matcher(API_VERSION + "/auth/login")).permitAll()
				.requestMatchers(PATHS.matcher(API_VERSION + "/auth/register")).permitAll()
				.requestMatchers(PATHS.matcher(HttpMethod.OPTIONS, API_VERSION + "/auth/**")).permitAll()
				.requestMatchers(PATHS.matcher(API_VERSION + "/auth/**")).hasRole("AUTH_CUSTOMER")
				.anyRequest().authenticated())
			.httpBasic(basic -> basic.authenticationEntryPoint(basicEntryPoint("api-customer-realm")))
			.csrf(AbstractHttpConfigurer::disable)
			.addFilterAfter(authenticationTokenFilter, BasicAuthenticationFilter.class);
		return http.build();
	}

}
