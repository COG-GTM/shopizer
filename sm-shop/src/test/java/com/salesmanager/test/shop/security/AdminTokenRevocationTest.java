package com.salesmanager.test.shop.security;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.util.Collections;
import java.util.Date;

import javax.servlet.http.HttpServletResponse;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.test.util.ReflectionTestUtils;

import com.salesmanager.core.business.services.user.PermissionService;
import com.salesmanager.core.business.services.user.UserService;
import com.salesmanager.core.model.user.User;
import com.salesmanager.shop.store.security.JWTTokenUtil;
import com.salesmanager.shop.store.security.admin.JWTAdminAuthenticationManager;
import com.salesmanager.shop.store.security.admin.JWTAdminServicesImpl;
import com.salesmanager.shop.store.security.user.JWTUser;

public class AdminTokenRevocationTest {

	private static final String USERNAME = "mallory@acme.com";

	private JWTTokenUtil tokenUtil;

	@BeforeEach
	public void setUp() {
		tokenUtil = new JWTTokenUtil();
		ReflectionTestUtils.setField(tokenUtil, "secret", "dGVzdC1zZWNyZXQtZm9yLWFkbWluLXRva2VuLXJldm9jYXRpb24tdGVzdHM=");
		ReflectionTestUtils.setField(tokenUtil, "expiration", 3600L);
		SecurityContextHolder.clearContext();
	}

	@AfterEach
	public void tearDown() {
		SecurityContextHolder.clearContext();
	}

	private static JWTUser jwtUser(boolean enabled, Date lastPasswordReset) {
		return new JWTUser(1L, USERNAME, "Mallory", "M", USERNAME, "hash", Collections.emptyList(), enabled,
				lastPasswordReset);
	}

	private static User user(boolean active) {
		User user = new User(USERNAME, "hash", USERNAME);
		user.setId(1L);
		user.setActive(active);
		return user;
	}

	private JWTAdminServicesImpl servicesFor(User user) throws Exception {
		UserService userService = mock(UserService.class);
		when(userService.getByUserName(USERNAME)).thenReturn(user);
		JWTAdminServicesImpl services = new JWTAdminServicesImpl();
		ReflectionTestUtils.setField(services, "userService", userService);
		ReflectionTestUtils.setField(services, "permissionService", mock(PermissionService.class));
		return services;
	}

	private Authentication authenticate(String token, UserDetailsService services) throws Exception {
		JWTAdminAuthenticationManager manager = new JWTAdminAuthenticationManager();
		ReflectionTestUtils.setField(manager, "jwtTokenUtil", tokenUtil);
		ReflectionTestUtils.setField(manager, "jwtAdminDetailsService", services);
		manager.setTokenHeader("Authorization");
		MockHttpServletRequest request = new MockHttpServletRequest();
		request.addHeader("Authorization", "Bearer " + token);
		HttpServletResponse response = new MockHttpServletResponse();
		manager.authenticateRequest(request, response);
		return SecurityContextHolder.getContext().getAuthentication();
	}

	@Test
	public void userDetailsReflectActiveFlagAndPasswordChange() throws Exception {
		User active = user(true);
		active.setAdminPassword("new-hash");
		UserDetails details = servicesFor(active).loadUserByUsername(USERNAME);
		assertTrue(details.isEnabled());
		assertEquals(active.getPasswordChangedDate(), ((JWTUser) details).getLastPasswordResetDate());

		assertFalse(servicesFor(user(false)).loadUserByUsername(USERNAME).isEnabled());
	}

	@Test
	public void passwordChangedDateOnlyMovesWhenPasswordChanges() {
		User user = user(true);
		assertNull(user.getPasswordChangedDate());
		user.setAdminPassword("hash");
		assertNull(user.getPasswordChangedDate());
		user.setAdminPassword("other-hash");
		assertNotNull(user.getPasswordChangedDate());
		assertEquals(0, user.getPasswordChangedDate().getTime() % 1000);
	}

	@Test
	public void validateTokenRejectsDisabledUser() {
		String token = tokenUtil.generateToken(jwtUser(true, null));
		assertTrue(tokenUtil.validateToken(token, jwtUser(true, null)));
		assertFalse(tokenUtil.validateToken(token, jwtUser(false, null)));
	}

	@Test
	public void validateTokenRejectsTokenIssuedUpToPasswordChange() {
		String token = tokenUtil.generateToken(jwtUser(true, null));
		Date issuedAt = tokenUtil.getIssuedAtDateFromToken(token);
		Date later = new Date(issuedAt.getTime() + 1000);
		assertFalse(tokenUtil.validateToken(token, jwtUser(true, later)));
		assertTrue(tokenUtil.isIssuedBeforeLastPasswordReset(token, later));
		assertFalse(tokenUtil.validateToken(token, jwtUser(true, issuedAt)));
		assertTrue(tokenUtil.isIssuedBeforeLastPasswordReset(token, issuedAt));
		Date earlier = new Date(issuedAt.getTime() - 1000);
		assertTrue(tokenUtil.validateToken(token, jwtUser(true, earlier)));
		assertFalse(tokenUtil.isIssuedBeforeLastPasswordReset(token, earlier));
	}

	@Test
	public void adminAuthenticationManagerRejectsDisabledUser() throws Exception {
		String token = tokenUtil.generateToken(jwtUser(true, null));
		assertNotNull(authenticate(token, servicesFor(user(true))));

		SecurityContextHolder.clearContext();
		assertNull(authenticate(token, servicesFor(user(false))));
	}

	@Test
	public void adminAuthenticationManagerRevalidatesExistingContext() throws Exception {
		String token = tokenUtil.generateToken(jwtUser(true, null));
		Authentication existing = authenticate(token, servicesFor(user(true)));
		assertNotNull(existing);

		SecurityContextHolder.getContext().setAuthentication(existing);
		assertNull(authenticate(token, servicesFor(user(false))));
	}

	@Test
	public void adminAuthenticationManagerRejectsTokenAfterPasswordChange() throws Exception {
		String token = tokenUtil.generateToken(jwtUser(true, null));
		User user = user(true);
		user.setPasswordChangedDate(new Date(tokenUtil.getIssuedAtDateFromToken(token).getTime() + 1000));
		assertNull(authenticate(token, servicesFor(user)));
	}
}
