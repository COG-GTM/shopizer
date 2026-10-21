package com.salesmanager.shop.store.facade.user;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Spy;
import org.mockito.junit.MockitoJUnitRunner;

import com.salesmanager.core.business.services.reference.language.LanguageService;
import com.salesmanager.core.business.services.user.PermissionService;
import com.salesmanager.core.business.services.user.UserService;
import com.salesmanager.core.model.merchant.MerchantStore;
import com.salesmanager.core.model.reference.language.Language;
import com.salesmanager.core.model.user.Group;
import com.salesmanager.core.model.user.User;
import com.salesmanager.shop.model.security.PersistableGroup;
import com.salesmanager.shop.model.user.PersistableUser;
import com.salesmanager.shop.model.user.ReadableUser;
import com.salesmanager.shop.populator.user.PersistableUserPopulator;
import com.salesmanager.shop.store.api.exception.OperationNotAllowedException;
import com.salesmanager.shop.store.api.exception.ServiceRuntimeException;
import com.salesmanager.shop.store.api.exception.UnauthorizedException;

/**
 * Authorization matrix of {@link UserFacadeImpl#update} (PUT /api/v1/private/user/{id}).
 * Collaborators are mocked; {@code authorizedStore} is stubbed per scenario.
 */
@RunWith(MockitoJUnitRunner.Silent.class)
public class UserFacadeImplUpdateAuthorizationTest {

	private static final String DEFAULT = "DEFAULT";
	private static final String OTHER = "OTHER";

	@Mock
	private UserService userService;
	@Mock
	private LanguageService languageService;
	@Mock
	private PermissionService permissionService;
	@Mock
	private PersistableUserPopulator persistableUserPopulator;

	@Spy
	@InjectMocks
	private UserFacadeImpl facade;

	private MerchantStore defaultStore;
	private MerchantStore otherStore;
	private int groupIds = 1;

	@Before
	public void setUp() throws Exception {
		defaultStore = store(DEFAULT);
		otherStore = store(OTHER);
		Language en = new Language("en");
		when(languageService.defaultLanguage()).thenReturn(en);
		when(permissionService.getPermissions(anyList())).thenReturn(Collections.emptyList());
		when(persistableUserPopulator.populate(any(PersistableUser.class), any(User.class), any(MerchantStore.class),
				any())).thenAnswer(inv -> {
					PersistableUser source = inv.getArgument(0);
					User target = inv.getArgument(1);
					MerchantStore requestStore = inv.getArgument(2);
					target.setFirstName(source.getFirstName());
					target.setAdminEmail(source.getEmailAddress());
					target.setAdminName(source.getUserName());
					if (source.getPassword() != null) {
						target.setAdminPassword("encoded:" + source.getPassword());
					}
					target.setMerchantStore(OTHER.equals(source.getStore()) ? otherStore
							: DEFAULT.equals(source.getStore()) ? defaultStore : requestStore);
					target.setActive(source.isActive());
					target.setGroups(source.getGroups().stream().map(g -> group(g.getName()))
							.collect(Collectors.toList()));
					return target;
				});
		doReturn(false).when(facade).authorizedStore(anyString(), anyString());
	}

	@Test
	public void lowPrivilegeCallerCannotUpdateAnotherUser() throws Exception {
		User caller = user(10L, "catalogue@test.com", defaultStore, "ADMIN_CATALOGUE");
		User target = user(11L, "target@test.com", defaultStore, "ADMIN_CATALOGUE");
		register(caller, target);
		allowStores(caller, DEFAULT);

		expectUnauthorized(target, caller, request(target, DEFAULT, "ADMIN_CATALOGUE"));
	}

	@Test
	public void lowPrivilegeCallerCannotTakeOverSuperadmin() throws Exception {
		User caller = user(10L, "catalogue@test.com", defaultStore, "ADMIN_CATALOGUE");
		User superadmin = user(1L, "admin@shopizer.com", defaultStore, "SUPERADMIN");
		register(caller, superadmin);
		allowStores(caller, DEFAULT);

		PersistableUser takeover = request(superadmin, DEFAULT, "ADMIN");
		takeover.setEmailAddress("attacker@test.com");
		takeover.setPassword("Attacker1!");
		expectUnauthorized(superadmin, caller, takeover);
	}

	@Test
	public void storeAdminCannotUpdateSuperadmin() throws Exception {
		User caller = user(10L, "storeadmin@test.com", defaultStore, "ADMIN");
		User superadmin = user(1L, "admin@shopizer.com", defaultStore, "SUPERADMIN");
		register(caller, superadmin);
		allowStores(caller, DEFAULT);

		expectUnauthorized(superadmin, caller, request(superadmin, DEFAULT, "SUPERADMIN"));
	}

	@Test
	public void retailAdminCannotUpdateSuperadmin() throws Exception {
		User caller = user(10L, "retail@test.com", defaultStore, "ADMIN_RETAIL");
		User superadmin = user(1L, "admin@shopizer.com", defaultStore, "SUPERADMIN");
		register(caller, superadmin);
		allowStores(caller, DEFAULT);

		expectUnauthorized(superadmin, caller, request(superadmin, DEFAULT, "SUPERADMIN"));
	}

	@Test
	public void storeAdminCannotUpdateUserOfForeignStore() throws Exception {
		User caller = user(10L, "storeadmin@test.com", defaultStore, "ADMIN");
		User target = user(11L, "foreign@test.com", otherStore, "ADMIN_CATALOGUE");
		register(caller, target);
		allowStores(caller, DEFAULT);

		expectUnauthorized(target, caller, request(target, OTHER, "ADMIN_CATALOGUE"));
	}

	@Test
	public void storeAdminCannotMoveSameStoreUserIntoForeignStore() throws Exception {
		User caller = user(10L, "storeadmin@test.com", defaultStore, "ADMIN");
		User target = user(11L, "target@test.com", defaultStore, "ADMIN_CATALOGUE");
		register(caller, target);
		allowStores(caller, DEFAULT);

		expectUnauthorized(target, caller, request(target, OTHER, "ADMIN_CATALOGUE"));
	}

	@Test
	public void storeAdminCanUpdateSameStoreUserButNotItsGroupsOrActiveFlag() throws Exception {
		User caller = user(10L, "storeadmin@test.com", defaultStore, "ADMIN");
		User target = user(11L, "target@test.com", defaultStore, "ADMIN_CATALOGUE");
		target.setActive(true);
		register(caller, target);
		allowStores(caller, DEFAULT);

		PersistableUser update = request(target, DEFAULT, "ADMIN");
		update.setFirstName("Renamed");
		update.setActive(false);

		ReadableUser result = facade.update(target.getId(), caller.getAdminName(), defaultStore, update);

		User saved = savedUser();
		assertEquals("Renamed", saved.getFirstName());
		assertEquals(Arrays.asList("ADMIN_CATALOGUE"), groupNames(saved));
		assertTrue("non-superadmin caller must not change active flag", saved.isActive());
		assertEquals("Renamed", result.getFirstName());
	}

	@Test
	public void retailAdminCanUpdateUserInChildStore() throws Exception {
		User caller = user(10L, "retail@test.com", defaultStore, "ADMIN_RETAIL");
		User target = user(11L, "child@test.com", otherStore, "ADMIN_CATALOGUE");
		register(caller, target);
		allowStores(caller, DEFAULT, OTHER);

		facade.update(target.getId(), caller.getAdminName(), otherStore, request(target, OTHER, "ADMIN_CATALOGUE"));

		assertEquals(OTHER, savedUser().getMerchantStore().getCode());
	}

	@Test
	public void userCanUpdateOwnProfileButNotEscalateGroupsOrActiveFlag() throws Exception {
		User self = user(10L, "catalogue@test.com", defaultStore, "ADMIN_CATALOGUE");
		self.setActive(true);
		register(self);

		PersistableUser update = request(self, DEFAULT, "ADMIN");
		update.setFirstName("Me");
		update.setActive(false);
		update.setPassword("NewPassword1");

		facade.update(self.getId(), self.getAdminName(), defaultStore, update);

		User saved = savedUser();
		assertEquals("Me", saved.getFirstName());
		assertEquals("encoded:NewPassword1", saved.getAdminPassword());
		assertEquals(Arrays.asList("ADMIN_CATALOGUE"), groupNames(saved));
		assertTrue(saved.isActive());
	}

	@Test
	public void userCannotChangeOwnStoreThroughBody() throws Exception {
		User self = user(10L, "storeadmin@test.com", defaultStore, "ADMIN");
		register(self);

		try {
			facade.update(self.getId(), self.getAdminName(), defaultStore, request(self, OTHER, "ADMIN"));
			fail("expected OperationNotAllowedException");
		} catch (OperationNotAllowedException expected) {
			// expected
		}
		verify(userService, never()).update(any(User.class));
	}

	@Test
	public void userCannotChangeOwnStoreThroughRequestStoreWhenBodyStoreBlank() throws Exception {
		User self = user(10L, "storeadmin@test.com", defaultStore, "ADMIN");
		register(self);

		try {
			facade.update(self.getId(), self.getAdminName(), otherStore, request(self, null, "ADMIN"));
			fail("expected OperationNotAllowedException");
		} catch (OperationNotAllowedException expected) {
			// expected
		}
		verify(userService, never()).update(any(User.class));
	}

	@Test
	public void superadminCanUpdateAnyUserIncludingGroupsAndActiveFlag() throws Exception {
		User caller = user(1L, "admin@shopizer.com", defaultStore, "SUPERADMIN");
		User target = user(11L, "foreign@test.com", otherStore, "ADMIN_CATALOGUE");
		target.setActive(true);
		register(caller, target);

		PersistableUser update = request(target, OTHER, "ADMIN_RETAIL");
		update.setActive(false);

		facade.update(target.getId(), caller.getAdminName(), defaultStore, update);

		User saved = savedUser();
		assertEquals(Arrays.asList("ADMIN_RETAIL"), groupNames(saved));
		assertFalse(saved.isActive());
	}

	@Test
	public void superadminEditingSelfKeepsSuperadminGroup() throws Exception {
		User self = user(1L, "admin@shopizer.com", defaultStore, "SUPERADMIN");
		register(self);

		facade.update(self.getId(), self.getAdminName(), defaultStore, request(self, DEFAULT, "ADMIN"));

		assertEquals(Arrays.asList("SUPERADMIN"), groupNames(savedUser()));
	}

	@Test
	public void bodyUserNameOfAnotherUserIsRejected() throws Exception {
		User caller = user(1L, "admin@shopizer.com", defaultStore, "SUPERADMIN");
		User target = user(11L, "target@test.com", defaultStore, "ADMIN_CATALOGUE");
		User other = user(12L, "other@test.com", defaultStore, "ADMIN_CATALOGUE");
		register(caller, target, other);

		PersistableUser update = request(target, DEFAULT, "ADMIN_CATALOGUE");
		update.setUserName(other.getAdminName());
		try {
			facade.update(target.getId(), caller.getAdminName(), defaultStore, update);
			fail("expected ServiceRuntimeException");
		} catch (ServiceRuntimeException expected) {
			// expected
		}
		verify(userService, never()).update(any(User.class));
	}

	private void expectUnauthorized(User target, User caller, PersistableUser update) throws Exception {
		try {
			facade.update(target.getId(), caller.getAdminName(), defaultStore, update);
			fail("expected UnauthorizedException");
		} catch (UnauthorizedException expected) {
			// expected
		}
		verify(userService, never()).update(any(User.class));
	}

	private void allowStores(User caller, String... codes) {
		for (String code : codes) {
			doReturn(true).when(facade).authorizedStore(eq(caller.getAdminName()), eq(code));
		}
	}

	private void register(User... users) throws Exception {
		for (User u : users) {
			when(userService.getById(u.getId())).thenReturn(u);
			when(userService.getByUserName(u.getAdminName())).thenReturn(u);
		}
	}

	private User savedUser() throws Exception {
		ArgumentCaptor<User> captor = ArgumentCaptor.forClass(User.class);
		verify(userService).update(captor.capture());
		return captor.getValue();
	}

	private List<String> groupNames(User u) {
		return u.getGroups().stream().map(Group::getGroupName).collect(Collectors.toList());
	}

	private PersistableUser request(User target, String storeCode, String groupName) {
		PersistableUser p = new PersistableUser();
		p.setUserName(target.getAdminName());
		p.setEmailAddress(target.getAdminEmail());
		p.setFirstName(target.getFirstName());
		p.setLastName("User");
		p.setDefaultLanguage("en");
		p.setStore(storeCode);
		PersistableGroup g = new PersistableGroup();
		g.setName(groupName);
		p.getGroups().add(g);
		return p;
	}

	private User user(Long id, String name, MerchantStore store, String... groups) {
		User u = new User(name, "secret", name);
		u.setId(id);
		u.setFirstName("First");
		u.setMerchantStore(store);
		u.setGroups(new ArrayList<>(Arrays.stream(groups).map(this::group).collect(Collectors.toList())));
		return u;
	}

	private Group group(String name) {
		Group g = new Group(name);
		g.setId(groupIds++);
		return g;
	}

	private MerchantStore store(String code) {
		MerchantStore s = new MerchantStore();
		s.setCode(code);
		return s;
	}
}
