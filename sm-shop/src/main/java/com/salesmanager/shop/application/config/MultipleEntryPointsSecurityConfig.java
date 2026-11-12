package com.salesmanager.shop.application.config;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpMethod;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.AuthenticationProvider;
import org.springframework.security.authentication.ProviderManager;
import org.springframework.security.authentication.ProviderNotFoundException;
import org.springframework.security.authentication.dao.DaoAuthenticationProvider;
import org.springframework.security.config.annotation.authentication.builders.AuthenticationManagerBuilder;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configuration.WebSecurityCustomizer;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.AuthenticationEntryPoint;
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

	@Autowired
	public void configureGlobalAuthentication(AuthenticationManagerBuilder auth) {
		auth.parentAuthenticationManager(authentication -> {
			throw new ProviderNotFoundException("Authentication requires a chain-specific manager");
		});
	}

	private static AuthenticationManager authenticationManager(UserDetailsService userDetailsService,
			PasswordEncoder passwordEncoder, AuthenticationProvider... additionalProviders) {
		DaoAuthenticationProvider daoProvider = new DaoAuthenticationProvider(userDetailsService);
		daoProvider.setPasswordEncoder(passwordEncoder);
		java.util.List<AuthenticationProvider> providers = new java.util.ArrayList<>(java.util.Arrays.asList(additionalProviders));
		providers.add(daoProvider);
		return new ProviderManager(providers);
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

	
	
	/**
	 * shop / customer
	 * 
	 * @author dur9213
	 *
	 */
	@Configuration
	public static class CustomerConfiguration {

		@Bean("customerAuthenticationManager")
		public AuthenticationManager customerAuthenticationManager(PasswordEncoder passwordEncoder) {
			return authenticationManager(customerDetailsService, passwordEncoder);
		}

		@Autowired
		private UserDetailsService customerDetailsService;
		
		@Bean
		public WebSecurityCustomizer publicResourcesIgnoring() {
			return web -> {

			web.ignoring().requestMatchers("/");
			web.ignoring().requestMatchers("/error");
			web.ignoring().requestMatchers("/resources/**");
			web.ignoring().requestMatchers("/static/**");
			web.ignoring().requestMatchers("/services/public/**");
			};
		}




		@Bean
		@Order(1)
		public SecurityFilterChain customerSecurityFilterChain(HttpSecurity http, PasswordEncoder passwordEncoder) throws Exception {
			http.authenticationManager(customerAuthenticationManager(passwordEncoder));
			http
			.securityMatcher("/shop/**")
			.csrf(csrf -> csrf.disable())
			.authorizeHttpRequests(authorize -> authorize
					.requestMatchers("/shop/").permitAll()
					.requestMatchers("/shop/**").permitAll()
					.requestMatchers("/shop/customer/logon*").permitAll()
					.requestMatchers("/shop/customer/registration*").permitAll()
					.requestMatchers("/shop/customer/logout*").permitAll()
					.requestMatchers("/shop/customer/customLogon*").permitAll()
					.requestMatchers("/shop/customer/denied*").permitAll()
					.requestMatchers("/shop/customer/**").hasRole("AUTH_CUSTOMER")
					.anyRequest().authenticated())
			.httpBasic(basic -> basic
					.authenticationEntryPoint(shopAuthenticationEntryPoint()))
			.logout(logout -> logout
					.logoutUrl("/shop/customer/logout")
					.logoutSuccessUrl("/shop/")
					.invalidateHttpSession(true)
					.deleteCookies("JSESSIONID")
					.invalidateHttpSession(false))
			.exceptionHandling(handling -> handling.accessDeniedPage("/shop/"));

			return http.build();

		}

		@Bean
		public AuthenticationEntryPoint shopAuthenticationEntryPoint() {
			BasicAuthenticationEntryPoint entryPoint = new BasicAuthenticationEntryPoint();
			entryPoint.setRealmName("shop-realm");
			return entryPoint;
		}

	}
	
	/**
	 * services api v0
	 * 
	 * @author dur9213
	 * @deprecated
	 *
	 */
	@Configuration
	public static class ServicesApiConfiguration {

		@Autowired
		private WebUserServices userDetailsService;

		@Autowired
		private ServicesAuthenticationSuccessHandler servicesAuthenticationSuccessHandler;

		@Bean
		public AuthenticationManager servicesAuthenticationManager(PasswordEncoder passwordEncoder) {
			return authenticationManager(userDetailsService, passwordEncoder);
		}



		@Bean
		@Order(2)
		public SecurityFilterChain servicesSecurityFilterChain(HttpSecurity http, PasswordEncoder passwordEncoder) throws Exception {
			http.authenticationManager(servicesAuthenticationManager(passwordEncoder));
			http
			.securityMatcher("/services/**")
			.csrf(csrf -> csrf.disable())
			.authorizeHttpRequests(authorize -> authorize
					.requestMatchers("/services/public/**").permitAll()
					.requestMatchers("/services/private/**").hasRole("AUTH")
					.anyRequest().authenticated())
			.httpBasic(basic -> basic.authenticationEntryPoint(servicesAuthenticationEntryPoint()))
			.formLogin(login -> login.successHandler(servicesAuthenticationSuccessHandler));

			return http.build();

		}

		@Bean
		public AuthenticationEntryPoint servicesAuthenticationEntryPoint() {
			BasicAuthenticationEntryPoint entryPoint = new BasicAuthenticationEntryPoint();
			entryPoint.setRealmName("rest-customer-realm");
			return entryPoint;
		}

	}

	/**
	 * api - private
	 * 
	 * @author dur9213
	 *
	 */
	@Configuration
	public static class UserApiConfiguration {

		@Autowired
		private AuthenticationTokenFilter authenticationTokenFilter;

		@Autowired
		JWTAdminServicesImpl jwtUserDetailsService;

		@Bean("jwtAdminAuthenticationManager")
		public AuthenticationManager jwtAdminAuthenticationManager(PasswordEncoder passwordEncoder) {
			return authenticationManager(jwtUserDetailsService, passwordEncoder, adminAuthenticationProvider());
		}
		
		


		
		@Bean
		public WebSecurityCustomizer apiDocumentationIgnoring() {
			return web -> {

			web.ignoring().requestMatchers("/swagger-ui.html", "/swagger-ui/**", "/v3/api-docs", "/v3/api-docs/**");
			};
		}

		
		/**
		 * Admin user api
		 */
		@Bean
		@Order(5)
		public SecurityFilterChain jwtAdminSecurityFilterChain(HttpSecurity http, PasswordEncoder passwordEncoder) throws Exception {
			http.authenticationManager(jwtAdminAuthenticationManager(passwordEncoder));
			http
					.securityMatcher(API_VERSION + "/private/**")
					.authorizeHttpRequests(authorize -> authorize
							.requestMatchers(API_VERSION + "/private/login*").permitAll()
							.requestMatchers(API_VERSION + "/private/refresh").permitAll()
							.requestMatchers(HttpMethod.OPTIONS, API_VERSION + "/private/**").permitAll()
							.requestMatchers(API_VERSION + "/private/**").hasRole("AUTH")
							.anyRequest().authenticated())
					.httpBasic(basic -> basic.authenticationEntryPoint(apiAdminAuthenticationEntryPoint()))
					.addFilterAfter(authenticationTokenFilter, BasicAuthenticationFilter.class)
					.csrf(csrf -> csrf.disable());

			return http.build();

		}
		
	    @Bean
	    public AuthenticationProvider adminAuthenticationProvider() {
	    	JWTAdminAuthenticationProvider provider = new JWTAdminAuthenticationProvider();
	        provider.setUserDetailsService(jwtUserDetailsService);
	        return provider;
	    }

		@Bean
		public AuthenticationEntryPoint apiAdminAuthenticationEntryPoint() {
			BasicAuthenticationEntryPoint entryPoint = new BasicAuthenticationEntryPoint();
			entryPoint.setRealmName("api-admin-realm");
			return entryPoint;
		}

	}



	/**
	 * customer api
	 * 
	 * @author dur9213
	 *
	 */
	@Configuration
	public static class CustomeApiConfiguration {

		@Autowired
		private AuthenticationTokenFilter authenticationTokenFilter;

		@Autowired
		private UserDetailsService jwtCustomerDetailsService;
		
		@Bean("jwtCustomerAuthenticationManager")
		public AuthenticationManager jwtCustomerAuthenticationManager(PasswordEncoder passwordEncoder) {
			return authenticationManager(jwtCustomerDetailsService, passwordEncoder);
		}



		@Bean
		@Order(6)
		public SecurityFilterChain jwtCustomerSecurityFilterChain(HttpSecurity http, PasswordEncoder passwordEncoder) throws Exception {
			http.authenticationManager(jwtCustomerAuthenticationManager(passwordEncoder));
			http
				.securityMatcher(API_VERSION + "/auth/**")
				.authorizeHttpRequests(authorize -> authorize
					.requestMatchers(API_VERSION + "/auth/refresh").permitAll()
					.requestMatchers(API_VERSION + "/auth/login").permitAll()
					.requestMatchers(API_VERSION + "/auth/register").permitAll()
					.requestMatchers(HttpMethod.OPTIONS, API_VERSION + "/auth/**").permitAll()
					.requestMatchers(API_VERSION + "/auth/**").hasRole("AUTH_CUSTOMER")
					.anyRequest().authenticated())
				.httpBasic(basic -> basic.authenticationEntryPoint(apiCustomerAuthenticationEntryPoint()))
				.csrf(csrf -> csrf.disable())
				.addFilterAfter(authenticationTokenFilter, BasicAuthenticationFilter.class);

			return http.build();

		}
		
	    @Bean
	    public AuthenticationProvider customerAuthenticationProvider() {
	    	JWTCustomerAuthenticationProvider provider = new JWTCustomerAuthenticationProvider();
	        provider.setUserDetailsService(jwtCustomerDetailsService);
	        return provider;
	    }

		@Bean
		public AuthenticationEntryPoint apiCustomerAuthenticationEntryPoint() {
			BasicAuthenticationEntryPoint entryPoint = new BasicAuthenticationEntryPoint();
			entryPoint.setRealmName("api-customer-realm");
			return entryPoint;
		}

	}



}
