package com.salesmanager.test.shop.facade.user;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.assertThrows;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;

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
import com.salesmanager.shop.model.user.PersistableUser;
import com.salesmanager.shop.store.api.exception.OperationNotAllowedException;
import com.salesmanager.shop.store.api.exception.ResourceNotFoundException;
import com.salesmanager.shop.store.api.exception.UnauthorizedException;
import com.salesmanager.shop.store.facade.user.UserFacadeImpl;

@RunWith(MockitoJUnitRunner.class)
public class UserFacadeImplStoreScopingTest {

	@Mock
	private UserService userService;

	@InjectMocks
	private UserFacadeImpl userFacade;

	private MerchantStore store;

	@Before
	public void setUp() {
		store = new MerchantStore();
		store.setCode("DEFAULT");
		authenticate("bob", "AUTH", "ADMIN");
	}

	@After
	public void clearSecurityContext() {
		SecurityContextHolder.clearContext();
	}

	@Test
	public void deleteRejectsUserOutsideStore() throws Exception {
		when(userService.findByStore(5L, "DEFAULT")).thenReturn(null);

		assertThrows(ResourceNotFoundException.class, () -> userFacade.delete(5L, "DEFAULT"));

		verify(userService, never()).delete(org.mockito.ArgumentMatchers.any(User.class));
	}

	@Test
	public void deleteRejectsSuperadmin() throws Exception {
		User superadmin = userWithGroup("SUPERADMIN");
		when(userService.findByStore(5L, "DEFAULT")).thenReturn(superadmin);

		assertThrows(OperationNotAllowedException.class, () -> userFacade.delete(5L, "DEFAULT"));

		verify(userService, never()).delete(org.mockito.ArgumentMatchers.any(User.class));
	}

	@Test
	public void deleteRemovesAdminInRequestedStore() throws Exception {
		User admin = userWithGroup("ADMIN");
		when(userService.findByStore(5L, "DEFAULT")).thenReturn(admin);

		userFacade.delete(5L, "DEFAULT");

		verify(userService).findByStore(5L, "DEFAULT");
		verify(userService).delete(admin);
	}

	@Test
	public void deleteHandlesTargetWithNullGroups() throws Exception {
		User user = new User();
		user.setId(5L);
		user.setGroups(null);
		when(userService.findByStore(5L, "DEFAULT")).thenReturn(user);

		userFacade.delete(5L, "DEFAULT");

		verify(userService).delete(user);
	}

	@Test
	public void updateEnabledRejectsUserOutsideStore() throws Exception {
		when(userService.findByStore(5L, "DEFAULT")).thenReturn(null);

		assertThrows(ResourceNotFoundException.class,
				() -> userFacade.updateEnabled(store, persistableUser(5L, false)));
	}

	@Test
	public void updateEnabledDoesNotAllowAdminToDisableSuperadmin() throws Exception {
		User superadmin = userWithGroup("SUPERADMIN");
		when(userService.findByStore(5L, "DEFAULT")).thenReturn(superadmin);

		assertThrows(UnauthorizedException.class,
				() -> userFacade.updateEnabled(store, persistableUser(5L, false)));

		assertTrue(superadmin.isActive());
		verify(userService, never()).saveOrUpdate(superadmin);
	}

	@Test
	public void updateEnabledAllowsSuperadminToDisableSuperadmin() throws Exception {
		authenticate("root", "AUTH", "SUPERADMIN");
		User superadmin = userWithGroup("SUPERADMIN");
		when(userService.findByStore(5L, "DEFAULT")).thenReturn(superadmin);

		userFacade.updateEnabled(store, persistableUser(5L, false));

		assertFalse(superadmin.isActive());
		verify(userService).saveOrUpdate(superadmin);
	}

	@Test
	public void updateEnabledAllowsAdminToDisableAdmin() throws Exception {
		User admin = userWithGroup("ADMIN");
		when(userService.findByStore(5L, "DEFAULT")).thenReturn(admin);

		userFacade.updateEnabled(store, persistableUser(5L, false));

		assertFalse(admin.isActive());
		verify(userService).saveOrUpdate(admin);
	}

	private static User userWithGroup(String groupName) {
		User user = new User();
		user.setId(5L);
		user.setGroups(Collections.singletonList(new Group(groupName)));
		user.setActive(true);
		return user;
	}

	private static PersistableUser persistableUser(Long id, boolean active) {
		PersistableUser user = new PersistableUser();
		user.setId(id);
		user.setActive(active);
		return user;
	}

	private static void authenticate(String username, String... authorities) {
		List<SimpleGrantedAuthority> grantedAuthorities = Arrays.stream(authorities)
				.map(SimpleGrantedAuthority::new).collect(Collectors.toList());
		SecurityContextHolder.getContext().setAuthentication(
				new UsernamePasswordAuthenticationToken(username, null, grantedAuthorities));
	}
}
