package com.salesmanager.test.shop.user;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.Arrays;
import java.util.Collections;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnitRunner;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;

import com.salesmanager.core.business.services.user.UserService;
import com.salesmanager.core.model.merchant.MerchantStore;
import com.salesmanager.core.model.user.Group;
import com.salesmanager.core.model.user.User;
import com.salesmanager.shop.constants.Constants;
import com.salesmanager.shop.model.user.PersistableUser;
import com.salesmanager.shop.store.api.exception.ResourceNotFoundException;
import com.salesmanager.shop.store.api.exception.ServiceRuntimeException;
import com.salesmanager.shop.store.api.exception.UnauthorizedException;
import com.salesmanager.shop.store.facade.user.UserFacadeImpl;

@RunWith(MockitoJUnitRunner.class)
public class UserFacadeImplTest {

	private static final Long USER_ID = 73L;

	@Mock
	private UserService userService;

	@InjectMocks
	private UserFacadeImpl userFacade;

	@Before
	public void setUp() {
		authenticateAs("ADMIN");
	}

	@After
	public void clearSecurityContext() {
		SecurityContextHolder.clearContext();
	}

	@Test
	public void refusesToDeleteSuperadmin() throws Exception {
		when(userService.findByStore(USER_ID, "authz-unit")).thenReturn(userWithGroup("SUPERADMIN"));

		org.junit.Assert.assertThrows(ServiceRuntimeException.class,
				() -> userFacade.delete(USER_ID, "authz-unit"));

		verify(userService, never()).delete(org.mockito.ArgumentMatchers.any(User.class));
	}

	@Test
	public void deletesStoreAdmin() throws Exception {
		User target = userWithGroup("ADMIN");
		when(userService.findByStore(USER_ID, "authz-unit")).thenReturn(target);

		userFacade.delete(USER_ID, "authz-unit");

		verify(userService).delete(target);
	}

	@Test
	public void refusesToDeleteUserMissingFromStore() throws Exception {
		when(userService.findByStore(USER_ID, "authz-unit")).thenReturn(null);

		org.junit.Assert.assertThrows(ServiceRuntimeException.class,
				() -> userFacade.delete(USER_ID, "authz-unit"));

		verify(userService, never()).delete(org.mockito.ArgumentMatchers.any(User.class));
	}

	@Test
	public void refusesAdminToDisableSuperadmin() throws Exception {
		when(userService.findByStore(USER_ID, "authz-unit")).thenReturn(userWithGroup("SUPERADMIN"));

		org.junit.Assert.assertThrows(UnauthorizedException.class,
				() -> userFacade.updateEnabled(store("authz-unit"), update(false)));

		verify(userService, never()).saveOrUpdate(org.mockito.ArgumentMatchers.any(User.class));
	}

	@Test
	public void allowsSuperadminToChangeEnabledFlag() throws Exception {
		User target = userWithGroup("SUPERADMIN");
		when(userService.findByStore(USER_ID, "authz-unit")).thenReturn(target);
		authenticateAs("SUPERADMIN");

		userFacade.updateEnabled(store("authz-unit"), update(false));

		assertFalse(target.isActive());
		verify(userService).saveOrUpdate(target);
	}

	@Test
	public void reportsMissingTargetWhenUpdatingEnabledFlag() throws Exception {
		when(userService.findByStore(USER_ID, "authz-unit")).thenReturn(null);

		org.junit.Assert.assertThrows(ResourceNotFoundException.class,
				() -> userFacade.updateEnabled(store("authz-unit"), update(false)));

		verify(userService, never()).saveOrUpdate(org.mockito.ArgumentMatchers.any(User.class));
	}

	private static void authenticateAs(String role) {
		SecurityContextHolder.getContext().setAuthentication(new UsernamePasswordAuthenticationToken(
				"authz-unit-user", "password",
				Collections.singletonList(new SimpleGrantedAuthority(role))));
	}

	private static User userWithGroup(String groupName) {
		User user = new User();
		user.setId(USER_ID);
		user.setGroups(Arrays.asList(new Group(groupName)));
		user.setActive(true);
		return user;
	}

	private static MerchantStore store(String code) {
		MerchantStore store = new MerchantStore();
		store.setCode(code);
		return store;
	}

	private static PersistableUser update(boolean active) {
		PersistableUser user = new PersistableUser();
		user.setId(USER_ID);
		user.setActive(active);
		return user;
	}
}
