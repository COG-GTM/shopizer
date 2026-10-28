package com.salesmanager.shop.store.facade.user;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Spy;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import com.salesmanager.core.business.services.user.UserService;
import com.salesmanager.core.model.merchant.MerchantStore;
import com.salesmanager.core.model.user.Group;
import com.salesmanager.core.model.user.User;
import com.salesmanager.shop.constants.Constants;
import com.salesmanager.shop.model.user.UserPassword;
import com.salesmanager.shop.store.api.exception.ForbiddenException;
import com.salesmanager.shop.store.api.exception.ResourceNotFoundException;
import com.salesmanager.shop.store.api.exception.ServiceRuntimeException;
import com.salesmanager.shop.store.api.exception.TooManyRequestsException;
import com.salesmanager.shop.store.api.exception.UnauthorizedException;
import com.salesmanager.shop.store.controller.security.facade.SecurityFacade;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class UserFacadeImplChangePasswordAuthorizationTest {

	private static final String CURRENT = "current";
	private static final String NEW_PASSWORD = "NewPassword1";

	@Mock
	private UserService userService;

	@Mock
	private SecurityFacade securityFacade;

	@Spy
	private PasswordChangeAttemptLimiter passwordChangeAttemptLimiter = new PasswordChangeAttemptLimiter();

	@InjectMocks
	private UserFacadeImpl userFacade;

	private MerchantStore defaultStore;
	private MerchantStore otherStore;

	@BeforeEach
	void setUp() {
		defaultStore = store("DEFAULT", null);
		otherStore = store("GLOBEX", null);
		when(securityFacade.matchPassword(anyString(), anyString()))
				.thenAnswer(inv -> ("encoded-" + inv.getArgument(1)).equals(inv.getArgument(0)));
		when(securityFacade.validateUserPassword(anyString())).thenReturn(true);
		when(securityFacade.encodePassword(anyString())).thenAnswer(inv -> "encoded-" + inv.getArgument(0));
	}

	@Test
	void adminRetailCanChangePasswordInTheSameStore() throws Exception {
		User caller = user(2L, "retail@shopizer.com", defaultStore, Constants.GROUP_ADMIN_RETAIL);
		User target = user(3L, "catalogue@shopizer.com", defaultStore, Constants.GROUP_ADMIN_CATALOGUE);
		register(caller, target);

		userFacade.changePassword(target.getId(), caller.getAdminName(), request(CURRENT));

		verify(userService).update(target);
	}

	@Test
	void adminOfChildStoreCannotChangePasswordInParentStore() throws Exception {
		MerchantStore childStore = store("CHILD", defaultStore);
		User caller = user(2L, "child-admin@shopizer.com", childStore, Constants.GROUP_ADMIN);
		User target = user(3L, "parent-user@shopizer.com", defaultStore, Constants.GROUP_ADMIN_CATALOGUE);
		register(caller, target);

		assertThrows(ForbiddenException.class,
				() -> userFacade.changePassword(target.getId(), caller.getAdminName(), request(CURRENT)));

		verify(userService, never()).update(any(User.class));
	}

	@Test
	void forbiddenAttemptsDoNotTouchLimiterAndTargetCanChangeOwnPassword() throws Exception {
		User target = user(3L, "target@shopizer.com", defaultStore, Constants.GROUP_ADMIN);
		User caller = user(4L, "other@globex.com", otherStore, Constants.GROUP_ADMIN);
		register(target, caller);

		for (int i = 0; i < 10; i++) {
			assertThrows(ForbiddenException.class,
					() -> userFacade.changePassword(target.getId(), caller.getAdminName(), request(CURRENT)));
		}

		verify(passwordChangeAttemptLimiter, never()).checkAllowed(target.getId());
		userFacade.changePassword(target.getId(), target.getAdminName(), request(CURRENT));

		verify(userService).update(target);
	}

	@Test
	void unknownTargetIsNotFoundBeforeLimiterOrUpdate() throws Exception {
		User caller = user(2L, "admin@shopizer.com", defaultStore, Constants.GROUP_SUPERADMIN);
		when(userService.getByUserName(caller.getAdminName())).thenReturn(caller);
		when(userService.getById(999L)).thenReturn(null);

		assertThrows(ResourceNotFoundException.class,
				() -> userFacade.changePassword(999L, caller.getAdminName(), request(CURRENT)));

		verify(passwordChangeAttemptLimiter, never()).checkAllowed(any());
		verify(userService, never()).update(any(User.class));
	}

	@Test
	void unknownAuthenticatedUserIsUnauthorized() throws Exception {
		when(userService.getByUserName("missing@shopizer.com")).thenReturn(null);

		assertThrows(UnauthorizedException.class,
				() -> userFacade.changePassword(3L, "missing@shopizer.com", request(CURRENT)));

		verify(userService, never()).getById(any());
		verify(passwordChangeAttemptLimiter, never()).checkAllowed(any());
	}

	@Test
	void callerWithoutGroupsCannotChangeAnotherUsersPassword() throws Exception {
		User caller = user(2L, "no-groups@shopizer.com", defaultStore);
		caller.setGroups(Collections.emptyList());
		User target = user(3L, "target@shopizer.com", defaultStore, Constants.GROUP_ADMIN);
		register(caller, target);

		assertThrows(ForbiddenException.class,
				() -> userFacade.changePassword(target.getId(), caller.getAdminName(), request(CURRENT)));

		verify(userService, never()).update(any(User.class));
	}

	@Test
	void superadminTargetingAnotherUserCanBeLockedOut() throws Exception {
		User superadmin = user(1L, "admin@shopizer.com", defaultStore, Constants.GROUP_SUPERADMIN);
		User target = user(7L, "target@globex.com", otherStore, Constants.GROUP_ADMIN);
		register(superadmin, target);

		for (int i = 0; i < PasswordChangeAttemptLimiter.MAX_FAILED_ATTEMPTS; i++) {
			assertThrows(ServiceRuntimeException.class,
					() -> userFacade.changePassword(target.getId(), superadmin.getAdminName(), request("wrong")));
		}

		assertThrows(TooManyRequestsException.class,
				() -> userFacade.changePassword(target.getId(), superadmin.getAdminName(), request(CURRENT)));

		verify(userService, never()).update(any(User.class));
	}

	@Test
	void wrongCurrentPasswordDoesNotUpdateAnAuthorizedUser() throws Exception {
		User self = user(5L, "self@shopizer.com", defaultStore, Constants.GROUP_ADMIN);
		register(self);

		assertThrows(ServiceRuntimeException.class,
				() -> userFacade.changePassword(self.getId(), self.getAdminName(), request("wrong")));

		verify(userService, never()).update(any(User.class));
	}

	private UserPassword request(String current) {
		UserPassword password = new UserPassword();
		password.setPassword(current);
		password.setChangePassword(NEW_PASSWORD);
		return password;
	}

	private void register(User... users) throws Exception {
		for (User user : users) {
			when(userService.getById(user.getId())).thenReturn(user);
			when(userService.getByUserName(user.getAdminName())).thenReturn(user);
		}
	}

	private static MerchantStore store(String code, MerchantStore parent) {
		MerchantStore store = new MerchantStore();
		store.setCode(code);
		store.setParent(parent);
		return store;
	}

	private static User user(Long id, String name, MerchantStore store, String... groups) {
		User user = new User(name, "encoded-" + CURRENT, name);
		user.setId(id);
		user.setMerchantStore(store);
		List<Group> userGroups = new ArrayList<>();
		for (String group : groups) {
			userGroups.add(new Group(group));
		}
		user.setGroups(userGroups);
		return user;
	}
}
