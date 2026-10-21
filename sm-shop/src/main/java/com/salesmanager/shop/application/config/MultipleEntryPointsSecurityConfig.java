package com.salesmanager.shop.application.config;

import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpMethod;
import org.springframework.security.authentication.AuthenticationEventPublisher;
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
import com.salesmanager.shop.store.security.services.CredentialsService;
import com.salesmanager.shop.store.security.services.CredentialsServiceImpl;

/**
 * Main entry point for security - admin - customer - auth - private - services
 *
 * @author dur9213
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
		return web -> web.ignoring().antMatchers("/", "/error", "/resources/**", "/static/**", "/services/public/**",
				"/swagger-ui.html");
	}

	@Bean
	public AuthenticationProvider authenticationProvider(JWTAdminServicesImpl jwtAdminDetailsService) {
		JWTAdminAuthenticationProvider provider = new JWTAdminAuthenticationProvider();
		provider.setUserDetailsService(jwtAdminDetailsService);
		return provider;
	}

	@Bean("customerAuthenticationManager")
	public AuthenticationManager customerAuthenticationManager(
			@Qualifier("customerDetailsService") UserDetailsService userDetailsService, PasswordEncoder passwordEncoder,
			ObjectProvider<AuthenticationEventPublisher> eventPublisher) {
		return providerManager(eventPublisher, daoProvider(userDetailsService, passwordEncoder));
	}

	@Bean("servicesAuthenticationManager")
	public AuthenticationManager servicesAuthenticationManager(
			@Qualifier("userDetailsService") WebUserServices userDetailsService, PasswordEncoder passwordEncoder,
			ObjectProvider<AuthenticationEventPublisher> eventPublisher) {
		return providerManager(eventPublisher, daoProvider(userDetailsService, passwordEncoder));
	}

	@Bean("jwtAdminAuthenticationManager")
	public AuthenticationManager jwtAdminAuthenticationManager(
			@Qualifier("authenticationProvider") AuthenticationProvider authenticationProvider,
			JWTAdminServicesImpl jwtAdminDetailsService, PasswordEncoder passwordEncoder,
			ObjectProvider<AuthenticationEventPublisher> eventPublisher) {
		return providerManager(eventPublisher, authenticationProvider, daoProvider(jwtAdminDetailsService, passwordEncoder));
	}

	@Bean("jwtCustomerAuthenticationManager")
	public AuthenticationManager jwtCustomerAuthenticationManager(
			@Qualifier("jwtCustomerDetailsService") UserDetailsService userDetailsService,
			PasswordEncoder passwordEncoder, ObjectProvider<AuthenticationEventPublisher> eventPublisher) {
		return providerManager(eventPublisher, daoProvider(userDetailsService, passwordEncoder));
	}

	private static DaoAuthenticationProvider daoProvider(UserDetailsService userDetailsService,
			PasswordEncoder passwordEncoder) {
		DaoAuthenticationProvider provider = new DaoAuthenticationProvider();
		provider.setUserDetailsService(userDetailsService);
		provider.setPasswordEncoder(passwordEncoder);
		return provider;
	}

	private static ProviderManager providerManager(ObjectProvider<AuthenticationEventPublisher> eventPublisher,
			AuthenticationProvider... providers) {
		ProviderManager manager = new ProviderManager(providers);
		eventPublisher.ifAvailable(manager::setAuthenticationEventPublisher);
		return manager;
	}

	@Bean
	@Order(1)
	public SecurityFilterChain shopSecurityFilterChain(HttpSecurity http,
			@Qualifier("customerAuthenticationManager") AuthenticationManager authenticationManager) throws Exception {
		http.authenticationManager(authenticationManager)
				.antMatcher("/shop/**")
				.csrf().disable()
				.authorizeRequests()
				.antMatchers("/shop/").permitAll()
				.antMatchers("/shop/**").permitAll()
				.antMatchers("/shop/customer/logon*").permitAll()
				.antMatchers("/shop/customer/registration*").permitAll()
				.antMatchers("/shop/customer/logout*").permitAll()
				.antMatchers("/shop/customer/customLogon*").permitAll()
				.antMatchers("/shop/customer/denied*").permitAll()
				.antMatchers("/shop/customer/**").hasRole("AUTH_CUSTOMER")
				.anyRequest().authenticated()
				.and()
				.httpBasic()
				.authenticationEntryPoint(shopAuthenticationEntryPoint())
				.and()
				.logout()
				.logoutUrl("/shop/customer/logout")
				.logoutSuccessUrl("/shop/")
				.invalidateHttpSession(true)
				.deleteCookies("JSESSIONID")
				.invalidateHttpSession(false)
				.and()
				.exceptionHandling().accessDeniedPage("/shop/");

		return http.build();
	}

	@Bean
	@Order(2)
	public SecurityFilterChain servicesSecurityFilterChain(HttpSecurity http,
			@Qualifier("servicesAuthenticationManager") AuthenticationManager authenticationManager,
			ServicesAuthenticationSuccessHandler servicesAuthenticationSuccessHandler) throws Exception {
		http.authenticationManager(authenticationManager)
				.antMatcher("/services/**")
				.csrf().disable()
				.authorizeRequests()
				.antMatchers("/services/public/**").permitAll()
				.antMatchers("/services/private/**").hasRole("AUTH")
				.anyRequest().authenticated()
				.and()
				.httpBasic().authenticationEntryPoint(servicesAuthenticationEntryPoint())
				.and()
				.formLogin()
				.successHandler(servicesAuthenticationSuccessHandler);

		return http.build();
	}

	// Inactive: the original /admin/** security chain is not enabled.
	/*
	@Bean
	@Order(3)
	public SecurityFilterChain adminSecurityFilterChain(HttpSecurity http,
			@Qualifier("servicesAuthenticationManager") AuthenticationManager authenticationManager,
			UserAuthenticationSuccessHandler userAuthenticationSuccessHandler) throws Exception {
		http.authenticationManager(authenticationManager)
				.antMatcher("/admin/**")
				.authorizeRequests()
				.antMatchers("/admin/logon*").permitAll()
				.antMatchers("/admin/resources/**").permitAll()
				.antMatchers("/admin/layout/**").permitAll()
				.antMatchers("/admin/denied*").permitAll()
				.antMatchers("/admin/unauthorized*").permitAll()
				.antMatchers("/admin/users/resetPassword*").permitAll()
				.antMatchers("/admin/").hasRole("AUTH")
				.antMatchers("/admin/**").hasRole("AUTH")
				.antMatchers("/admin/**").hasRole("AUTH")
				.antMatchers("/admin/users/resetPasswordSecurityQtn*").permitAll()
				.anyRequest().authenticated()
				.and()
				.httpBasic()
				.authenticationEntryPoint(adminAuthenticationEntryPoint())
				.and()
				.formLogin().usernameParameter("username").passwordParameter("password")
				.loginPage("/admin/logon.html")
				.loginProcessingUrl("/admin/performUserLogin")
				.successHandler(userAuthenticationSuccessHandler)
				.failureUrl("/admin/logon.html?login_error=true")
				.and()
				.csrf().disable()
				.logout().logoutUrl("/admin/logout").logoutSuccessUrl("/admin/home.html")
				.invalidateHttpSession(true).and().exceptionHandling().accessDeniedPage("/admin/denied.html");

		return http.build();
	}

	@Bean
	public AuthenticationEntryPoint adminAuthenticationEntryPoint() {
		BasicAuthenticationEntryPoint entryPoint = new BasicAuthenticationEntryPoint();
		entryPoint.setRealmName("admin-realm");
		return entryPoint;
	}
	*/

	@Bean
	@Order(5)
	public SecurityFilterChain privateApiSecurityFilterChain(HttpSecurity http,
			@Qualifier("jwtAdminAuthenticationManager") AuthenticationManager authenticationManager,
			AuthenticationTokenFilter authenticationTokenFilter) throws Exception {
		http.authenticationManager(authenticationManager)
				.antMatcher(API_VERSION + "/private/**")
				.authorizeRequests()
				.antMatchers(API_VERSION + "/private/login*").permitAll()
				.antMatchers(API_VERSION + "/private/refresh").permitAll()
				.antMatchers(HttpMethod.OPTIONS, API_VERSION + "/private/**").permitAll()
				.antMatchers(API_VERSION + "/private/**").hasRole("AUTH")
				.anyRequest().authenticated()
				.and()
				.httpBasic().authenticationEntryPoint(apiAdminAuthenticationEntryPoint())
				.and()
				.addFilterAfter(authenticationTokenFilter, BasicAuthenticationFilter.class)
				.csrf().disable();

		return http.build();
	}

	@Bean
	@Order(6)
	public SecurityFilterChain customerApiSecurityFilterChain(HttpSecurity http,
			@Qualifier("jwtCustomerAuthenticationManager") AuthenticationManager authenticationManager,
			AuthenticationTokenFilter authenticationTokenFilter) throws Exception {
		http.authenticationManager(authenticationManager)
				.antMatcher(API_VERSION + "/auth/**")
				.authorizeRequests()
				.antMatchers(API_VERSION + "/auth/refresh").permitAll()
				.antMatchers(API_VERSION + "/auth/login").permitAll()
				.antMatchers(API_VERSION + "/auth/register").permitAll()
				.antMatchers(HttpMethod.OPTIONS, API_VERSION + "/auth/**").permitAll()
				.antMatchers(API_VERSION + "/auth/**").hasRole("AUTH_CUSTOMER")
				.anyRequest().authenticated()
				.and()
				.httpBasic()
				.authenticationEntryPoint(apiCustomerAuthenticationEntryPoint()).and().csrf().disable()
				.addFilterAfter(authenticationTokenFilter, BasicAuthenticationFilter.class);

		return http.build();
	}

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
}
