package com.salesmanager.shop.store.facade.user;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.ArrayList;
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
import com.salesmanager.shop.store.api.exception.ServiceRuntimeException;
import com.salesmanager.shop.store.api.exception.TooManyRequestsException;
import com.salesmanager.shop.store.controller.security.facade.SecurityFacade;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class UserFacadeImplChangePasswordTest {

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
	private MerchantStore globex;

	@BeforeEach
	void setUp() {
		defaultStore = store("DEFAULT", null);
		globex = store("GLOBEX", null);
		when(securityFacade.matchPassword(anyString(), anyString()))
				.thenAnswer(inv -> ("encoded-" + inv.getArgument(1)).equals(inv.getArgument(0)));
		when(securityFacade.validateUserPassword(anyString())).thenReturn(true);
		when(securityFacade.encodePassword(anyString())).thenAnswer(inv -> "encoded-" + inv.getArgument(0));
	}

	@Test
	void adminOfAnotherStoreCannotChangeSuperadminPassword() throws Exception {
		User superadmin = user(1L, "admin@shopizer.com", defaultStore, Constants.GROUP_SUPERADMIN);
		User mallory = user(2L, "mallory@globex.com", globex, Constants.GROUP_ADMIN);
		register(superadmin, mallory);

		assertThrows(ForbiddenException.class,
				() -> userFacade.changePassword(1L, mallory.getAdminName(), request(CURRENT)));

		verify(securityFacade, never()).matchPassword(anyString(), anyString());
		verify(userService, never()).update(any(User.class));
		assertEquals("encoded-" + CURRENT, superadmin.getAdminPassword());
	}

	@Test
	void adminOfAnotherStoreCannotChangeAdminPassword() throws Exception {
		User alice = user(1L, "alice@shopizer.com", defaultStore, Constants.GROUP_ADMIN);
		User mallory = user(2L, "mallory@globex.com", globex, Constants.GROUP_ADMIN);
		register(alice, mallory);

		assertThrows(ForbiddenException.class,
				() -> userFacade.changePassword(1L, mallory.getAdminName(), request(CURRENT)));

		verify(userService, never()).update(any(User.class));
	}

	@Test
	void sameStoreAdminCannotChangeSuperadminPassword() throws Exception {
		User superadmin = user(1L, "admin@shopizer.com", defaultStore, Constants.GROUP_SUPERADMIN);
		User admin = user(2L, "admin2@shopizer.com", defaultStore, Constants.GROUP_ADMIN);
		register(superadmin, admin);

		assertThrows(ForbiddenException.class,
				() -> userFacade.changePassword(1L, admin.getAdminName(), request(CURRENT)));

		verify(userService, never()).update(any(User.class));
	}

	@Test
	void nonAdminCannotChangeAnotherUsersPassword() throws Exception {
		User target = user(1L, "bob@shopizer.com", defaultStore, Constants.GROUP_ADMIN_CATALOGUE);
		User caller = user(2L, "carol@shopizer.com", defaultStore, Constants.GROUP_ADMIN_ORDER);
		register(target, caller);

		assertThrows(ForbiddenException.class,
				() -> userFacade.changePassword(1L, caller.getAdminName(), request(CURRENT)));
	}

	@Test
	void userCanChangeOwnPassword() throws Exception {
		User self = user(5L, "bob@shopizer.com", defaultStore, Constants.GROUP_ADMIN_CATALOGUE);
		register(self);

		userFacade.changePassword(5L, self.getAdminName(), request(CURRENT));

		verify(userService).update(self);
		assertEquals("encoded-" + NEW_PASSWORD, self.getAdminPassword());
	}

	@Test
	void superadminCanChangeAnyPassword() throws Exception {
		User superadmin = user(1L, "admin@shopizer.com", defaultStore, Constants.GROUP_SUPERADMIN);
		User target = user(7L, "dave@globex.com", globex, Constants.GROUP_ADMIN);
		register(superadmin, target);

		userFacade.changePassword(7L, superadmin.getAdminName(), request(CURRENT));

		verify(userService).update(target);
	}

	@Test
	void adminCanChangePasswordOfUserInOwnOrChildStore() throws Exception {
		MerchantStore child = store("CHILD", defaultStore);
		User admin = user(2L, "admin2@shopizer.com", defaultStore, Constants.GROUP_ADMIN);
		User sameStore = user(3L, "erin@shopizer.com", defaultStore, Constants.GROUP_ADMIN_CATALOGUE);
		User childStore = user(4L, "frank@child.com", child, Constants.GROUP_ADMIN_ORDER);
		register(admin, sameStore, childStore);

		userFacade.changePassword(3L, admin.getAdminName(), request(CURRENT));
		userFacade.changePassword(4L, admin.getAdminName(), request(CURRENT));

		verify(userService).update(sameStore);
		verify(userService).update(childStore);
	}

	@Test
	void repeatedWrongCurrentPasswordLocksPasswordChange() throws Exception {
		User self = user(5L, "bob@shopizer.com", defaultStore, Constants.GROUP_ADMIN);
		register(self);

		for (int i = 0; i < PasswordChangeAttemptLimiter.MAX_FAILED_ATTEMPTS; i++) {
			assertThrows(ServiceRuntimeException.class,
					() -> userFacade.changePassword(5L, self.getAdminName(), request("wrong")));
		}

		assertThrows(TooManyRequestsException.class,
				() -> userFacade.changePassword(5L, self.getAdminName(), request(CURRENT)));
		verify(userService, never()).update(any(User.class));
	}

	private UserPassword request(String current) {
		UserPassword password = new UserPassword();
		password.setPassword(current);
		password.setChangePassword(NEW_PASSWORD);
		return password;
	}

	private void register(User... users) throws Exception {
		for (User u : users) {
			when(userService.getById(u.getId())).thenReturn(u);
			when(userService.getByUserName(u.getAdminName())).thenReturn(u);
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
		List<Group> list = new ArrayList<>();
		for (String g : groups) {
			list.add(new Group(g));
		}
		user.setGroups(list);
		return user;
	}
}
