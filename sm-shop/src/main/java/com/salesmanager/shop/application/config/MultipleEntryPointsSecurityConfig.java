package com.salesmanager.shop.application.config;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpMethod;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.AuthenticationProvider;
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
	@Order(1)
	public static class CustomerConfigurationAdapter {

		@Autowired
		private UserDetailsService customerDetailsService;

		@Bean("customerAuthenticationManager")
		public AuthenticationManager customerAuthenticationManager(HttpSecurity http) throws Exception {
			AuthenticationManagerBuilder builder = http.getSharedObject(AuthenticationManagerBuilder.class);
			builder.userDetailsService(customerDetailsService);
			return builder.build();
		}

		@Bean
		public WebSecurityCustomizer customerWebSecurityCustomizer() {
			return (web) -> {
				web.ignoring().antMatchers("/");
				web.ignoring().antMatchers("/error");
				web.ignoring().antMatchers("/resources/**");
				web.ignoring().antMatchers("/static/**");
				web.ignoring().antMatchers("/services/public/**");
			};
		}

		@Bean
		@Order(1)
		public SecurityFilterChain customerFilterChain(HttpSecurity http) throws Exception {
			http
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
	@Order(2)
	public static class ServicesApiConfigurationAdapter {

		@Autowired
		private WebUserServices userDetailsService;

		@Autowired
		private ServicesAuthenticationSuccessHandler servicesAuthenticationSuccessHandler;

		@Bean
		@Order(2)
		public SecurityFilterChain servicesFilterChain(HttpSecurity http) throws Exception {
			AuthenticationManagerBuilder builder = http.getSharedObject(AuthenticationManagerBuilder.class);
			builder.userDetailsService(userDetailsService);

			http
			.antMatcher("/services/**")
			.csrf().disable()
					.authorizeRequests()
					.antMatchers("/services/public/**").permitAll()
					.antMatchers("/services/private/**").hasRole("AUTH")
					.anyRequest().authenticated()
					.and().httpBasic().authenticationEntryPoint(servicesAuthenticationEntryPoint())
					.and().formLogin()
					.successHandler(servicesAuthenticationSuccessHandler);

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
	 * admin
	 * 
	 * @author dur9213
	 *
	 */
	/*
	@Configuration
	@Order(3)
	public static class AdminConfigurationAdapter {

		@Autowired
		private WebUserServices userDetailsService;

		@Autowired
		private UserAuthenticationSuccessHandler userAuthenticationSuccessHandler;

		@Bean
		@Order(3)
		public SecurityFilterChain adminFilterChain(HttpSecurity http) throws Exception {
			http
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
					.anyRequest()
					.authenticated()
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

	}
	*/

	/**
	 * api - private
	 * 
	 * @author dur9213
	 *
	 */
	@Configuration
	@Order(5)
	public static class UserApiConfigurationAdapter {

		@Autowired
		private AuthenticationTokenFilter authenticationTokenFilter;

		@Autowired
		JWTAdminServicesImpl jwtUserDetailsService;

		@Bean("jwtAdminAuthenticationManager")
		public AuthenticationManager jwtAdminAuthenticationManager(HttpSecurity http) throws Exception {
			AuthenticationManagerBuilder builder = http.getSharedObject(AuthenticationManagerBuilder.class);
			builder.userDetailsService(jwtUserDetailsService)
				.and()
				.authenticationProvider(authenticationProvider());
			return builder.build();
		}

		@Bean
		public WebSecurityCustomizer adminApiWebSecurityCustomizer() {
			return (web) -> web.ignoring().antMatchers("/swagger-ui.html");
		}

		/**
		 * Admin user api
		 */
		@Bean
		@Order(5)
		public SecurityFilterChain adminApiFilterChain(HttpSecurity http) throws Exception {
			http
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
	    public AuthenticationProvider authenticationProvider() {
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
	@Order(6)
	public static class CustomeApiConfigurationAdapter {

		@Autowired
		private AuthenticationTokenFilter authenticationTokenFilter;

		@Autowired
		private UserDetailsService jwtCustomerDetailsService;

		@Bean("jwtCustomerAuthenticationManager")
		public AuthenticationManager jwtCustomerAuthenticationManager(HttpSecurity http) throws Exception {
			AuthenticationManagerBuilder builder = http.getSharedObject(AuthenticationManagerBuilder.class);
			builder.userDetailsService(jwtCustomerDetailsService);
			return builder.build();
		}

		@Bean
		@Order(6)
		public SecurityFilterChain customerApiFilterChain(HttpSecurity http) throws Exception {
			http
			
				.antMatcher(API_VERSION + "/auth/**")
				.authorizeRequests()
					.antMatchers(API_VERSION + "/auth/refresh").permitAll()
					.antMatchers(API_VERSION + "/auth/login").permitAll()
					.antMatchers(API_VERSION + "/auth/register").permitAll()
					.antMatchers(HttpMethod.OPTIONS, API_VERSION + "/auth/**").permitAll()
					.antMatchers(API_VERSION + "/auth/**")
					.hasRole("AUTH_CUSTOMER").anyRequest().authenticated()
					.and()
					.httpBasic()
					.authenticationEntryPoint(apiCustomerAuthenticationEntryPoint()).and().csrf().disable()
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
