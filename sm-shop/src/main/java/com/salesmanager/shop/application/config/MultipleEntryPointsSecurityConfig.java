package com.salesmanager.shop.application.config;

import java.util.Arrays;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpMethod;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.AuthenticationProvider;
import org.springframework.security.authentication.dao.DaoAuthenticationProvider;
import org.springframework.security.config.annotation.authentication.configuration.AuthenticationConfiguration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configuration.WebSecurityCustomizer;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.www.BasicAuthenticationEntryPoint;
import org.springframework.security.web.authentication.www.BasicAuthenticationFilter;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

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
	public CorsConfigurationSource corsConfigurationSource() {
		CorsConfiguration configuration = new CorsConfiguration();
		configuration.setAllowedOrigins(Arrays.asList("http://localhost:3000", "*"));
		configuration.setAllowedMethods(Arrays.asList("GET", "POST", "PUT", "DELETE", "PATCH", "OPTIONS"));
		configuration.setAllowedHeaders(Arrays.asList("X-Auth-Token", "Content-Type", "Authorization", "Cache-Control", "X-Requested-With"));
		configuration.setAllowCredentials(false);
		UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
		source.registerCorsConfiguration("/**", configuration);
		return source;
	}

	@Bean
	public WebSecurityCustomizer webSecurityCustomizer() {
		return (web) -> web.ignoring()
				.requestMatchers("/", "/error", "/resources/**", "/static/**", "/services/public/**",
						"/swagger-ui.html", "/swagger-ui/**", "/v3/api-docs/**");
	}

	@Bean
	public AuthenticationManager authenticationManager(AuthenticationConfiguration authenticationConfiguration) throws Exception {
		return authenticationConfiguration.getAuthenticationManager();
	}

	@Bean
	@Order(1)
	public SecurityFilterChain customerSecurityFilterChain(HttpSecurity http) throws Exception {
		http
			.securityMatcher("/shop/**")
			.csrf(csrf -> csrf.disable())
			.cors(cors -> cors.configurationSource(corsConfigurationSource()))
			.authorizeHttpRequests(auth -> auth
					.requestMatchers("/shop/").permitAll()
					.requestMatchers("/shop/customer/logon*").permitAll()
					.requestMatchers("/shop/customer/registration*").permitAll()
					.requestMatchers("/shop/customer/logout*").permitAll()
					.requestMatchers("/shop/customer/customLogon*").permitAll()
					.requestMatchers("/shop/customer/denied*").permitAll()
					.requestMatchers("/shop/customer/**").hasRole("AUTH_CUSTOMER")
					.anyRequest().permitAll()
			)
			.httpBasic(basic -> {
				BasicAuthenticationEntryPoint entryPoint = new BasicAuthenticationEntryPoint();
				entryPoint.setRealmName("shop-realm");
				basic.authenticationEntryPoint(entryPoint);
			})
			.logout(logout -> logout
					.logoutUrl("/shop/customer/logout")
					.logoutSuccessUrl("/shop/")
					.invalidateHttpSession(true)
					.deleteCookies("JSESSIONID")
			)
			.exceptionHandling(ex -> ex.accessDeniedPage("/shop/"));

		return http.build();
	}

	@Bean
	@Order(2)
	public SecurityFilterChain servicesSecurityFilterChain(HttpSecurity http,
			WebUserServices userDetailsService,
			ServicesAuthenticationSuccessHandler servicesAuthenticationSuccessHandler) throws Exception {
		BasicAuthenticationEntryPoint entryPoint = new BasicAuthenticationEntryPoint();
		entryPoint.setRealmName("rest-customer-realm");

		http
			.securityMatcher("/services/**")
			.csrf(csrf -> csrf.disable())
			.cors(cors -> cors.configurationSource(corsConfigurationSource()))
			.authorizeHttpRequests(auth -> auth
					.requestMatchers("/services/public/**").permitAll()
					.requestMatchers("/services/private/**").hasRole("AUTH")
					.anyRequest().authenticated()
			)
			.httpBasic(basic -> basic.authenticationEntryPoint(entryPoint))
			.formLogin(form -> form.successHandler(servicesAuthenticationSuccessHandler));

		return http.build();
	}

	@Bean
	@Order(5)
	public SecurityFilterChain adminApiSecurityFilterChain(HttpSecurity http,
			AuthenticationTokenFilter authenticationTokenFilter,
			JWTAdminServicesImpl jwtUserDetailsService) throws Exception {
		BasicAuthenticationEntryPoint entryPoint = new BasicAuthenticationEntryPoint();
		entryPoint.setRealmName("api-admin-realm");

		JWTAdminAuthenticationProvider provider = new JWTAdminAuthenticationProvider();
		provider.setUserDetailsService(jwtUserDetailsService);

		http
			.securityMatcher(API_VERSION + "/private/**")
			.authenticationProvider(provider)
			.csrf(csrf -> csrf.disable())
			.cors(cors -> cors.configurationSource(corsConfigurationSource()))
			.authorizeHttpRequests(auth -> auth
					.requestMatchers(API_VERSION + "/private/login*").permitAll()
					.requestMatchers(API_VERSION + "/private/refresh").permitAll()
					.requestMatchers(HttpMethod.OPTIONS, API_VERSION + "/private/**").permitAll()
					.requestMatchers(API_VERSION + "/private/**").hasRole("AUTH")
					.anyRequest().authenticated()
			)
			.httpBasic(basic -> basic.authenticationEntryPoint(entryPoint))
			.addFilterAfter(authenticationTokenFilter, BasicAuthenticationFilter.class);

		return http.build();
	}

	@Bean
	@Order(6)
	public SecurityFilterChain customerApiSecurityFilterChain(HttpSecurity http,
			AuthenticationTokenFilter authenticationTokenFilter,
			UserDetailsService jwtCustomerDetailsService) throws Exception {
		BasicAuthenticationEntryPoint entryPoint = new BasicAuthenticationEntryPoint();
		entryPoint.setRealmName("api-customer-realm");

		JWTCustomerAuthenticationProvider provider = new JWTCustomerAuthenticationProvider();
		provider.setUserDetailsService(jwtCustomerDetailsService);

		http
			.securityMatcher(API_VERSION + "/auth/**")
			.authenticationProvider(provider)
			.csrf(csrf -> csrf.disable())
			.cors(cors -> cors.configurationSource(corsConfigurationSource()))
			.authorizeHttpRequests(auth -> auth
					.requestMatchers(API_VERSION + "/auth/refresh").permitAll()
					.requestMatchers(API_VERSION + "/auth/login").permitAll()
					.requestMatchers(API_VERSION + "/auth/register").permitAll()
					.requestMatchers(HttpMethod.OPTIONS, API_VERSION + "/auth/**").permitAll()
					.requestMatchers(API_VERSION + "/auth/**").hasRole("AUTH_CUSTOMER")
					.anyRequest().authenticated()
			)
			.httpBasic(basic -> basic.authenticationEntryPoint(entryPoint))
			.addFilterAfter(authenticationTokenFilter, BasicAuthenticationFilter.class);

		return http.build();
	}
}
