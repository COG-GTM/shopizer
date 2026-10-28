package com.salesmanager.test.shop.security;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.ArrayList;
import java.util.Collections;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.DisabledException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.util.ReflectionTestUtils;

import com.salesmanager.core.business.services.user.GroupService;
import com.salesmanager.core.business.services.user.PermissionService;
import com.salesmanager.core.business.services.user.UserService;
import com.salesmanager.core.model.user.User;
import com.salesmanager.shop.store.api.v1.user.AuthenticateUserApi;
import com.salesmanager.shop.store.security.AuthenticationRequest;
import com.salesmanager.shop.store.security.JWTTokenUtil;
import com.salesmanager.shop.store.security.admin.JWTAdminAuthenticationManager;
import com.salesmanager.shop.store.security.admin.JWTAdminAuthenticationProvider;
import com.salesmanager.shop.store.security.admin.JWTAdminServicesImpl;
import com.salesmanager.shop.store.security.user.JWTUser;

public class DisabledAdminUserAuthenticationTest {

	private static final String HEADER = "Authorization";
	private static final String TOKEN = "header.payload.signature";

	private UserService userService;
	private JWTAdminServicesImpl detailsService;
	private JWTTokenUtil jwtTokenUtil;

	@Before
	public void setUp() throws Exception {
		userService = mock(UserService.class);
		detailsService = new JWTAdminServicesImpl();
		ReflectionTestUtils.setField(detailsService, "userService", userService);
		ReflectionTestUtils.setField(detailsService, "permissionService", mock(PermissionService.class));
		ReflectionTestUtils.setField(detailsService, "groupService", mock(GroupService.class));

		when(userService.getByUserName("active@shop.com")).thenReturn(user("active@shop.com", true));
		when(userService.getByUserName("disabled@shop.com")).thenReturn(user("disabled@shop.com", false));

		jwtTokenUtil = mock(JWTTokenUtil.class);
		when(jwtTokenUtil.validateToken(anyString(), any(UserDetails.class))).thenReturn(true);
		SecurityContextHolder.clearContext();
	}

	@After
	public void tearDown() {
		SecurityContextHolder.clearContext();
	}

	@Test
	public void userDetailsReflectActiveFlag() {
		assertTrue(detailsService.loadUserByUsername("active@shop.com").isEnabled());
		assertFalse(detailsService.loadUserByUsername("disabled@shop.com").isEnabled());
	}

	@Test
	public void tokenOfActiveUserIsAccepted() throws Exception {
		when(jwtTokenUtil.getUsernameFromToken(TOKEN)).thenReturn("active@shop.com");

		Authentication authentication = tokenManager().attemptAuthentication(bearerRequest(), new MockHttpServletResponse());

		assertNotNull(authentication);
		assertEquals("active@shop.com", ((UserDetails) authentication.getPrincipal()).getUsername());
	}

	@Test(expected = DisabledException.class)
	public void tokenOfDisabledUserIsRejected() throws Exception {
		when(jwtTokenUtil.getUsernameFromToken(TOKEN)).thenReturn("disabled@shop.com");

		tokenManager().attemptAuthentication(bearerRequest(), new MockHttpServletResponse());
	}

	@Test
	public void tokenOfDisabledUserLeavesSecurityContextEmpty() throws Exception {
		when(jwtTokenUtil.getUsernameFromToken(TOKEN)).thenReturn("disabled@shop.com");

		tokenManager().authenticateRequest(bearerRequest(), new MockHttpServletResponse());

		assertNull(SecurityContextHolder.getContext().getAuthentication());
	}

	@Test(expected = DisabledException.class)
	public void providerRejectsDisabledUserWithValidPassword() {
		JWTAdminAuthenticationProvider provider = new JWTAdminAuthenticationProvider();
		PasswordEncoder encoder = mock(PasswordEncoder.class);
		when(encoder.matches(anyString(), anyString())).thenReturn(true);
		provider.setJwtAdminDetailsService(detailsService);
		ReflectionTestUtils.setField(provider, "passwordEncoder", encoder);

		provider.authenticate(new UsernamePasswordAuthenticationToken("disabled@shop.com", "password"));
	}

	@Test
	public void loginOfDisabledUserReturnsUnauthorizedWithoutToken() throws Exception {
		AuthenticationManager authenticationManager = mock(AuthenticationManager.class);
		when(authenticationManager.authenticate(any(Authentication.class)))
				.thenThrow(new DisabledException("User is disabled"));
		AuthenticateUserApi api = authenticateUserApi(authenticationManager);

		AuthenticationRequest request = new AuthenticationRequest();
		request.setUsername("disabled@shop.com");
		request.setPassword("password");
		ResponseEntity<?> response = api.authenticate(request);

		assertEquals(HttpStatus.UNAUTHORIZED, response.getStatusCode());
		verify(jwtTokenUtil, never()).generateToken(any(UserDetails.class));
	}

	@Test
	public void refreshOfDisabledUserReturnsUnauthorized() {
		when(jwtTokenUtil.getUsernameFromToken(TOKEN)).thenReturn("disabled@shop.com");
		AuthenticateUserApi api = authenticateUserApi(mock(AuthenticationManager.class));

		ResponseEntity<?> response = api.refreshAndGetAuthenticationToken(bearerRequest());

		assertEquals(HttpStatus.UNAUTHORIZED, response.getStatusCode());
		verify(jwtTokenUtil, never()).refreshToken(eq(TOKEN));
	}

	private JWTAdminAuthenticationManager tokenManager() {
		JWTAdminAuthenticationManager manager = new JWTAdminAuthenticationManager();
		manager.setTokenHeader(HEADER);
		ReflectionTestUtils.setField(manager, "jwtTokenUtil", jwtTokenUtil);
		ReflectionTestUtils.setField(manager, "jwtAdminDetailsService", (UserDetailsService) detailsService);
		return manager;
	}

	private AuthenticateUserApi authenticateUserApi(AuthenticationManager authenticationManager) {
		AuthenticateUserApi api = new AuthenticateUserApi();
		ReflectionTestUtils.setField(api, "tokenHeader", HEADER);
		ReflectionTestUtils.setField(api, "jwtAdminAuthenticationManager", authenticationManager);
		ReflectionTestUtils.setField(api, "jwtAdminDetailsService", (UserDetailsService) detailsService);
		ReflectionTestUtils.setField(api, "jwtTokenUtil", jwtTokenUtil);
		return api;
	}

	private MockHttpServletRequest bearerRequest() {
		MockHttpServletRequest request = new MockHttpServletRequest();
		request.addHeader(HEADER, "Bearer " + TOKEN);
		return request;
	}

	private static User user(String userName, boolean active) {
		User user = new User(userName, "{bcrypt}hash", userName);
		user.setId(1L);
		user.setFirstName("First");
		user.setLastName("Last");
		user.setActive(active);
		user.setGroups(new ArrayList<>(Collections.emptyList()));
		return user;
	}
}
