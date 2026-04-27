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
 * @author dur9213
 *
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

	@Autowired
	private UserDetailsService customerDetailsService;

	@Autowired
	private WebUserServices userDetailsService;

	@Autowired
	private ServicesAuthenticationSuccessHandler servicesAuthSuccessHandler;

	@Autowired
	private JWTAdminServicesImpl jwtUserDetailsService;

	@Autowired
	private UserDetailsService jwtCustomerDetailsService;

	// ---------------------------------------------------------------
	// WebSecurityCustomizer — replaces configure(WebSecurity)
	// ---------------------------------------------------------------

	@Bean
	public WebSecurityCustomizer webSecurityCustomizer() {
		return (web) -> web.ignoring()
				.requestMatchers("/")
				.requestMatchers("/error")
				.requestMatchers("/resources/**")
				.requestMatchers("/static/**")
				.requestMatchers("/services/public/**")
				.requestMatchers("/swagger-ui.html");
	}

	// ---------------------------------------------------------------
	// Authentication entry points
	// ---------------------------------------------------------------

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

	// ---------------------------------------------------------------
	// Authentication providers
	// ---------------------------------------------------------------

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

	// ---------------------------------------------------------------
	// Named AuthenticationManager beans (consumed by other components)
	// ---------------------------------------------------------------

	@Bean("customerAuthenticationManager")
	public AuthenticationManager customerAuthenticationManager() {
		DaoAuthenticationProvider provider = new DaoAuthenticationProvider();
		provider.setUserDetailsService(customerDetailsService);
		return new ProviderManager(provider);
	}

	@Bean("jwtAdminAuthenticationManager")
	public AuthenticationManager jwtAdminAuthenticationManager() {
		return new ProviderManager(jwtAdminAuthenticationProvider());
	}

	@Bean("jwtCustomerAuthenticationManager")
	public AuthenticationManager jwtCustomerAuthenticationManager() {
		return new ProviderManager(jwtCustomerAuthenticationProvider());
	}

	// ---------------------------------------------------------------
	// SecurityFilterChain beans — replace inner WebSecurityConfigurerAdapter classes
	// ---------------------------------------------------------------

	/**
	 * shop / customer
	 */
	@Bean
	@Order(1)
	public SecurityFilterChain customerFilterChain(HttpSecurity http) throws Exception {
		http
			.securityMatcher("/shop/**")
			.csrf(csrf -> csrf.disable())
			.authorizeHttpRequests(auth -> auth
				.requestMatchers("/shop/").permitAll()
				.requestMatchers("/shop/**").permitAll()
				.requestMatchers("/shop/customer/logon*").permitAll()
				.requestMatchers("/shop/customer/registration*").permitAll()
				.requestMatchers("/shop/customer/logout*").permitAll()
				.requestMatchers("/shop/customer/customLogon*").permitAll()
				.requestMatchers("/shop/customer/denied*").permitAll()
				.requestMatchers("/shop/customer/**").hasRole("AUTH_CUSTOMER")
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
				.invalidateHttpSession(false)
			)
			.exceptionHandling(ex -> ex
				.accessDeniedPage("/shop/")
			);
		return http.build();
	}

	/**
	 * services api v0
	 *
	 * @deprecated
	 */
	@Bean
	@Order(2)
	public SecurityFilterChain servicesFilterChain(HttpSecurity http) throws Exception {
		http
			.securityMatcher("/services/**")
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
	 * admin — commented out in original configuration
	 */
	/*
	@Bean
	@Order(3)
	public SecurityFilterChain adminFilterChain(HttpSecurity http) throws Exception {
		http
			.securityMatcher("/admin/**")
			.authorizeHttpRequests(auth -> auth
				.requestMatchers("/admin/logon*").permitAll()
				.requestMatchers("/admin/resources/**").permitAll()
				.requestMatchers("/admin/layout/**").permitAll()
				.requestMatchers("/admin/denied*").permitAll()
				.requestMatchers("/admin/unauthorized*").permitAll()
				.requestMatchers("/admin/users/resetPassword*").permitAll()
				.requestMatchers("/admin/").hasRole("AUTH")
				.requestMatchers("/admin/**").hasRole("AUTH")
				.requestMatchers("/admin/users/resetPasswordSecurityQtn*").permitAll()
				.anyRequest().authenticated()
			)
			.httpBasic(basic -> basic
				.authenticationEntryPoint(adminAuthenticationEntryPoint())
			)
			.formLogin(form -> form
				.usernameParameter("username")
				.passwordParameter("password")
				.loginPage("/admin/logon.html")
				.loginProcessingUrl("/admin/performUserLogin")
				.successHandler(userAuthenticationSuccessHandler())
				.failureUrl("/admin/logon.html?login_error=true")
			)
			.csrf(csrf -> csrf.disable())
			.logout(logout -> logout
				.logoutUrl("/admin/logout")
				.logoutSuccessUrl("/admin/home.html")
				.invalidateHttpSession(true)
			)
			.exceptionHandling(ex -> ex
				.accessDeniedPage("/admin/denied.html")
			);
		return http.build();
	}

	@Bean
	public AuthenticationEntryPoint adminAuthenticationEntryPoint() {
		BasicAuthenticationEntryPoint entryPoint = new BasicAuthenticationEntryPoint();
		entryPoint.setRealmName("admin-realm");
		return entryPoint;
	}
	*/

	/**
	 * api - private (admin)
	 */
	@Bean
	@Order(5)
	public SecurityFilterChain adminApiFilterChain(HttpSecurity http) throws Exception {
		http
			.securityMatcher(API_VERSION + "/private/**")
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
			.addFilterBefore(authenticationTokenFilter(), BasicAuthenticationFilter.class)
			.csrf(csrf -> csrf.disable());
		return http.build();
	}

	/**
	 * customer api
	 */
	@Bean
	@Order(6)
	public SecurityFilterChain customerApiFilterChain(HttpSecurity http) throws Exception {
		http
			.securityMatcher(API_VERSION + "/auth/**")
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
			.addFilterBefore(authenticationTokenFilter(), BasicAuthenticationFilter.class);
		return http.build();
	}

}
