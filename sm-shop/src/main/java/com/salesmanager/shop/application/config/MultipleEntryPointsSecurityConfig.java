package com.salesmanager.shop.application.config;

import org.springframework.beans.factory.annotation.Autowired;
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
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.AuthenticationEntryPoint;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.www.BasicAuthenticationEntryPoint;
import org.springframework.security.web.authentication.www.BasicAuthenticationFilter;

import static org.springframework.security.web.util.matcher.AntPathRequestMatcher.antMatcher;

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

	
	
	private static DaoAuthenticationProvider daoProvider(UserDetailsService userDetailsService, PasswordEncoder passwordEncoder) {
		DaoAuthenticationProvider provider = new DaoAuthenticationProvider();
		provider.setUserDetailsService(userDetailsService);
		provider.setPasswordEncoder(passwordEncoder);
		return provider;
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

	/**
	 * shop / customer
	 * 
	 * @author dur9213
	 *
	 */
	@Configuration
	public static class CustomerConfigurationAdapter {

		@Autowired
		private UserDetailsService customerDetailsService;

		@Autowired
		private PasswordEncoder passwordEncoder;

		@Bean("customerAuthenticationManager")
		@Primary
		public AuthenticationManager customerAuthenticationManager() {
			return new ProviderManager(daoProvider(customerDetailsService, passwordEncoder));
		}

		@Bean
		@Order(1)
		public SecurityFilterChain shopSecurityFilterChain(HttpSecurity http) throws Exception {
			http
			.securityMatcher(antMatcher("/shop/**"))
			.authenticationManager(customerAuthenticationManager())
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
					.invalidateHttpSession(true)
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

	}
	
	/**
	 * services api v0
	 * 
	 * @author dur9213
	 * @deprecated
	 *
	 */
	@Configuration
	public static class ServicesApiConfigurationAdapter {

		@Autowired
		private WebUserServices userDetailsService;

		@Autowired
		private PasswordEncoder passwordEncoder;

		@Autowired
		private ServicesAuthenticationSuccessHandler servicesAuthenticationSuccessHandler;

		@Bean
		@Order(2)
		public SecurityFilterChain servicesSecurityFilterChain(HttpSecurity http) throws Exception {
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
	/**
	@Configuration
	@Order(3)
	public static class AdminConfigurationAdapter extends WebSecurityConfigurerAdapter {

		@Autowired
		private WebUserServices userDetailsService;

		@Autowired
		private UserAuthenticationSuccessHandler userAuthenticationSuccessHandler;

		public AdminConfigurationAdapter() {
			super();
		}

		@Override
		public void configure(AuthenticationManagerBuilder auth) throws Exception {
			auth.userDetailsService(userDetailsService);
		}
		
		@Override
		public void configure(WebSecurity web) {
		}

		@Override
		protected void configure(HttpSecurity http) throws Exception {
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
			

		}

		@Bean
		public AuthenticationEntryPoint adminAuthenticationEntryPoint() {
			BasicAuthenticationEntryPoint entryPoint = new BasicAuthenticationEntryPoint();
			entryPoint.setRealmName("admin-realm");
			return entryPoint;
		}

	}
	**/

	/**
	 * api - private
	 * 
	 * @author dur9213
	 *
	 */
	@Configuration
	public static class UserApiConfigurationAdapter {

		@Autowired
		private AuthenticationTokenFilter authenticationTokenFilter;

		@Autowired
		JWTAdminServicesImpl jwtUserDetailsService;

		@Autowired
		private PasswordEncoder passwordEncoder;

		@Bean("jwtAdminAuthenticationManager")
		public AuthenticationManager jwtAdminAuthenticationManager() {
			return new ProviderManager(daoProvider(jwtUserDetailsService, passwordEncoder), jwtAdminAuthenticationProvider());
		}

		/**
		 * Admin user api
		 */
		@Bean
		@Order(5)
		public SecurityFilterChain adminApiSecurityFilterChain(HttpSecurity http) throws Exception {
			http
			.securityMatcher(antMatcher(API_VERSION + "/private/**"))
			.authenticationManager(jwtAdminAuthenticationManager())
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
		
	    @Bean
	    public AuthenticationProvider jwtAdminAuthenticationProvider() {
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
	public static class CustomeApiConfigurationAdapter {

		@Autowired
		private AuthenticationTokenFilter authenticationTokenFilter;

		@Autowired
		private UserDetailsService jwtCustomerDetailsService;

		@Autowired
		private PasswordEncoder passwordEncoder;

		@Bean("jwtCustomerAuthenticationManager")
		public AuthenticationManager jwtCustomerAuthenticationManager() {
			return new ProviderManager(daoProvider(jwtCustomerDetailsService, passwordEncoder));
		}

		@Bean
		@Order(6)
		public SecurityFilterChain customerApiSecurityFilterChain(HttpSecurity http) throws Exception {
			http
			.securityMatcher(antMatcher(API_VERSION + "/auth/**"))
			.authenticationManager(jwtCustomerAuthenticationManager())
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
		
	    @Bean
	    public AuthenticationProvider jwtCustomerAuthenticationProvider() {
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
