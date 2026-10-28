package com.salesmanager.test.shop.security;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.Collections;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.DisabledException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.util.ReflectionTestUtils;

import com.salesmanager.shop.store.api.v1.customer.AuthenticateCustomerApi;
import com.salesmanager.shop.store.api.v1.user.AuthenticateUserApi;
import com.salesmanager.shop.store.security.AuthenticationRequest;
import com.salesmanager.shop.store.security.JWTTokenUtil;
import com.salesmanager.shop.store.security.admin.JWTAdminAuthenticationProvider;
import com.salesmanager.shop.store.security.customer.JWTCustomerAuthenticationManager;
import com.salesmanager.shop.store.security.customer.JWTCustomerAuthenticationProvider;
import com.salesmanager.shop.store.security.user.JWTUser;

/**
 * Complements DisabledAdminUserAuthenticationTest: customer-side checks, check ordering
 * (password before enabled flag) and the login response body for disabled accounts.
 */
public class DisabledAccountAuthenticationEdgeCasesTest {

	private static final String HEADER = "Authorization";
	private static final String TOKEN = "header.payload.signature";

	private UserDetailsService detailsService;
	private JWTTokenUtil jwtTokenUtil;

	@Before
	public void setUp() {
		detailsService = mock(UserDetailsService.class);
		when(detailsService.loadUserByUsername("active@shop.com")).thenReturn(jwtUser(1L, "active@shop.com", true));
		when(detailsService.loadUserByUsername("disabled@shop.com")).thenReturn(jwtUser(2L, "disabled@shop.com", false));
		jwtTokenUtil = mock(JWTTokenUtil.class);
		when(jwtTokenUtil.validateToken(anyString(), any(UserDetails.class))).thenReturn(true);
		when(jwtTokenUtil.canTokenBeRefreshed(anyString(), any())).thenReturn(true);
		when(jwtTokenUtil.canTokenBeRefreshedWithGrace(anyString(), any())).thenReturn(true);
		when(jwtTokenUtil.refreshToken(anyString())).thenReturn("refreshed");
		SecurityContextHolder.clearContext();
	}

	@After
	public void tearDown() {
		SecurityContextHolder.clearContext();
	}

	@Test
	public void customerTokenOfActiveUserIsAccepted() throws Exception {
		when(jwtTokenUtil.getUsernameFromToken(TOKEN)).thenReturn("active@shop.com");

		Authentication authentication = customerTokenManager().attemptAuthentication(bearerRequest(), new MockHttpServletResponse());

		assertNotNull(authentication);
		assertEquals("active@shop.com", ((UserDetails) authentication.getPrincipal()).getUsername());
	}

	@Test(expected = DisabledException.class)
	public void customerTokenOfDisabledUserIsRejected() throws Exception {
		when(jwtTokenUtil.getUsernameFromToken(TOKEN)).thenReturn("disabled@shop.com");

		customerTokenManager().attemptAuthentication(bearerRequest(), new MockHttpServletResponse());
	}

	@Test
	public void customerTokenOfDisabledUserLeavesSecurityContextEmpty() throws Exception {
		when(jwtTokenUtil.getUsernameFromToken(TOKEN)).thenReturn("disabled@shop.com");

		customerTokenManager().authenticateRequest(bearerRequest(), new MockHttpServletResponse());

		assertNull(SecurityContextHolder.getContext().getAuthentication());
	}

	@Test(expected = DisabledException.class)
	public void customerProviderRejectsDisabledUserWithValidPassword() {
		customerProvider(true).authenticate(new UsernamePasswordAuthenticationToken("disabled@shop.com", "password"));
	}

	@Test(expected = BadCredentialsException.class)
	public void customerProviderChecksPasswordBeforeEnabledFlag() {
		customerProvider(false).authenticate(new UsernamePasswordAuthenticationToken("disabled@shop.com", "wrong"));
	}

	@Test
	public void customerProviderAcceptsActiveUserWithValidPassword() {
		Authentication authentication = customerProvider(true)
				.authenticate(new UsernamePasswordAuthenticationToken("active@shop.com", "password"));

		assertNotNull(authentication);
		assertEquals("active@shop.com", ((UserDetails) authentication.getPrincipal()).getUsername());
	}

	@Test(expected = BadCredentialsException.class)
	public void adminProviderChecksPasswordBeforeEnabledFlag() {
		JWTAdminAuthenticationProvider provider = new JWTAdminAuthenticationProvider();
		provider.setJwtAdminDetailsService(detailsService);
		ReflectionTestUtils.setField(provider, "passwordEncoder", encoder(false));

		provider.authenticate(new UsernamePasswordAuthenticationToken("disabled@shop.com", "wrong"));
	}

	@Test
	public void customerLoginOfDisabledUserReturnsSameBodyAsBadCredentials() throws Exception {
		ResponseEntity<?> disabled = customerApi(managerThrowing(new DisabledException("disabled"))).authenticate(request("disabled@shop.com"));
		ResponseEntity<?> badCredentials = customerApi(managerThrowing(new BadCredentialsException("bad"))).authenticate(request("disabled@shop.com"));

		assertEquals(HttpStatus.UNAUTHORIZED, disabled.getStatusCode());
		assertEquals(badCredentials.getStatusCode(), disabled.getStatusCode());
		assertEquals(badCredentials.getBody(), disabled.getBody());
		verify(jwtTokenUtil, never()).generateToken(any(UserDetails.class));
	}

	@Test
	public void adminLoginOfDisabledUserReturnsSameBodyAsBadCredentials() throws Exception {
		ResponseEntity<?> disabled = adminApi(managerThrowing(new DisabledException("disabled"))).authenticate(request("disabled@shop.com"));
		ResponseEntity<?> badCredentials = adminApi(managerThrowing(new BadCredentialsException("bad"))).authenticate(request("disabled@shop.com"));

		assertEquals(HttpStatus.UNAUTHORIZED, disabled.getStatusCode());
		assertEquals(badCredentials.getBody(), disabled.getBody());
		assertEquals("{\"message\":\"Bad credentials\"}", disabled.getBody());
	}

	@Test
	public void customerRefreshOfDisabledUserReturnsUnauthorized() {
		when(jwtTokenUtil.getUsernameFromToken(anyString())).thenReturn("disabled@shop.com");

		ResponseEntity<?> response = customerApi(mock(AuthenticationManager.class)).refreshToken(bearerRequest());

		assertEquals(HttpStatus.UNAUTHORIZED, response.getStatusCode());
		verify(jwtTokenUtil, never()).refreshToken(anyString());
	}

	@Test
	public void customerRefreshOfActiveUserStillIssuesToken() {
		when(jwtTokenUtil.getUsernameFromToken(anyString())).thenReturn("active@shop.com");

		ResponseEntity<?> response = customerApi(mock(AuthenticationManager.class)).refreshToken(bearerRequest());

		assertEquals(HttpStatus.OK, response.getStatusCode());
		verify(jwtTokenUtil).refreshToken(anyString());
	}

	@Test
	public void adminRefreshOfActiveUserStillIssuesToken() {
		when(jwtTokenUtil.getUsernameFromToken(TOKEN)).thenReturn("active@shop.com");

		ResponseEntity<?> response = adminApi(mock(AuthenticationManager.class)).refreshAndGetAuthenticationToken(bearerRequest());

		assertEquals(HttpStatus.OK, response.getStatusCode());
		verify(jwtTokenUtil).refreshToken(TOKEN);
	}

	private JWTCustomerAuthenticationManager customerTokenManager() {
		JWTCustomerAuthenticationManager manager = new JWTCustomerAuthenticationManager();
		manager.setTokenHeader(HEADER);
		ReflectionTestUtils.setField(manager, "jwtTokenUtil", jwtTokenUtil);
		ReflectionTestUtils.setField(manager, "jwtCustomerDetailsService", detailsService);
		return manager;
	}

	private JWTCustomerAuthenticationProvider customerProvider(boolean passwordMatches) {
		JWTCustomerAuthenticationProvider provider = new JWTCustomerAuthenticationProvider();
		provider.setJwtCustomerDetailsService(detailsService);
		ReflectionTestUtils.setField(provider, "passwordEncoder", encoder(passwordMatches));
		return provider;
	}

	private static PasswordEncoder encoder(boolean matches) {
		PasswordEncoder encoder = mock(PasswordEncoder.class);
		when(encoder.matches(anyString(), anyString())).thenReturn(matches);
		return encoder;
	}

	private static AuthenticationManager managerThrowing(RuntimeException e) {
		AuthenticationManager manager = mock(AuthenticationManager.class);
		when(manager.authenticate(any(Authentication.class))).thenThrow(e);
		return manager;
	}

	private AuthenticateCustomerApi customerApi(AuthenticationManager authenticationManager) {
		AuthenticateCustomerApi api = new AuthenticateCustomerApi();
		ReflectionTestUtils.setField(api, "tokenHeader", HEADER);
		ReflectionTestUtils.setField(api, "jwtCustomerAuthenticationManager", authenticationManager);
		ReflectionTestUtils.setField(api, "jwtCustomerDetailsService", detailsService);
		ReflectionTestUtils.setField(api, "jwtTokenUtil", jwtTokenUtil);
		return api;
	}

	private AuthenticateUserApi adminApi(AuthenticationManager authenticationManager) {
		AuthenticateUserApi api = new AuthenticateUserApi();
		ReflectionTestUtils.setField(api, "tokenHeader", HEADER);
		ReflectionTestUtils.setField(api, "jwtAdminAuthenticationManager", authenticationManager);
		ReflectionTestUtils.setField(api, "jwtAdminDetailsService", detailsService);
		ReflectionTestUtils.setField(api, "jwtTokenUtil", jwtTokenUtil);
		return api;
	}

	private static AuthenticationRequest request(String username) {
		AuthenticationRequest request = new AuthenticationRequest();
		request.setUsername(username);
		request.setPassword("password");
		return request;
	}

	private MockHttpServletRequest bearerRequest() {
		MockHttpServletRequest request = new MockHttpServletRequest();
		request.addHeader(HEADER, "Bearer " + TOKEN);
		return request;
	}

	private static JWTUser jwtUser(Long id, String username, boolean enabled) {
		return new JWTUser(id, username, "First", "Last", username, "{bcrypt}hash",
				Collections.emptyList(), enabled, null);
	}
}
