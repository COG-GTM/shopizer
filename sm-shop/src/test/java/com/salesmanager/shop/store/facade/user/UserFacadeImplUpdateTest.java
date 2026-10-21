package com.salesmanager.shop.store.facade.user;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.fail;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

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

import com.salesmanager.core.business.services.merchant.MerchantStoreService;
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
import com.salesmanager.shop.store.api.exception.ResourceNotFoundException;
import com.salesmanager.shop.store.api.exception.UnauthorizedException;

@RunWith(MockitoJUnitRunner.Silent.class)
public class UserFacadeImplUpdateTest {

	private static final String CALLER = "caller@example.com";
	private static final String TARGET = "target@example.com";
	private static final MerchantStore DEFAULT_STORE = new MerchantStore(1, "DEFAULT", "Default");
	private static final MerchantStore OTHER_STORE = new MerchantStore(2, "OTHER", "Other");

	@Spy
	@InjectMocks
	private UserFacadeImpl facade;

	@Mock
	private UserService userService;

	@Mock
	private LanguageService languageService;

	@Mock
	private PersistableUserPopulator persistableUserPopulator;

	@Mock
	private PermissionService permissionService;

	@Mock
	private MerchantStoreService merchantStoreService;

	private final Language language = new Language("en");

	@Before
	public void setUp() throws Exception {
		language.setId(1);
		when(languageService.defaultLanguage()).thenReturn(language);
		when(permissionService.getPermissions(anyList())).thenReturn(Collections.emptyList());
		doAnswer(invocation -> {
			PersistableUser source = invocation.getArgument(0);
			User target = invocation.getArgument(1);
			target.setFirstName(source.getFirstName());
			target.setLastName(source.getLastName());
			target.setAdminEmail(source.getEmailAddress());
			target.setAdminName(source.getUserName());
			if (source.getPassword() != null && !source.getPassword().trim().isEmpty()) {
				target.setAdminPassword("ENCODED:" + source.getPassword());
			}
			target.setGroups(source.getGroups().stream().map(group -> group(group.getName()))
					.collect(Collectors.toList()));
			target.setActive(source.isActive());
			if (source.getStore() != null && !source.getStore().trim().isEmpty()) {
				target.setMerchantStore(new MerchantStore(1, source.getStore(), source.getStore()));
			}
			return target;
		}).when(persistableUserPopulator).populate(any(PersistableUser.class), any(User.class),
				any(MerchantStore.class), any(Language.class));
	}

	@Test
	public void unknownCallerRejected() throws Exception {
		when(userService.getByUserName("unknown@example.com")).thenReturn(null);

		try {
			facade.update(2L, "unknown@example.com", DEFAULT_STORE, body(TARGET));
			fail("Expected UnauthorizedException");
		} catch (UnauthorizedException expected) {
			verify(userService, never()).update(any(User.class));
		}
	}

	@Test
	public void missingTargetIsNotFound() throws Exception {
		stubCaller(CALLER, 1L, "ADMIN");
		when(userService.getById(2L)).thenReturn(null);

		try {
			facade.update(2L, CALLER, DEFAULT_STORE, body(TARGET));
			fail("Expected ResourceNotFoundException");
		} catch (ResourceNotFoundException expected) {
			verify(userService, never()).update(any(User.class));
		}
	}

	@Test
	public void nonAdminCallerCannotEditOtherUser() throws Exception {
		stubCaller(CALLER, 1L, "ADMIN_CATALOGUE");
		stubTarget(2L, TARGET, "ADMIN_CATALOGUE");

		try {
			facade.update(2L, CALLER, DEFAULT_STORE, body(TARGET));
			fail("Expected UnauthorizedException");
		} catch (UnauthorizedException expected) {
			verify(userService, never()).update(any(User.class));
		}
	}

	@Test
	public void adminCannotEditSuperadmin() throws Exception {
		stubCaller(CALLER, 1L, "ADMIN");
		stubTarget(2L, TARGET, "SUPERADMIN");

		try {
			facade.update(2L, CALLER, DEFAULT_STORE, body(TARGET));
			fail("Expected UnauthorizedException");
		} catch (UnauthorizedException expected) {
			verify(userService, never()).update(any(User.class));
		}
	}

	@Test
	public void adminRetailCannotEditUserOutsideStore() throws Exception {
		stubCaller(CALLER, 1L, "ADMIN_RETAIL");
		stubTarget(2L, TARGET, "ADMIN_CATALOGUE");
		doReturn(false).when(facade).authorizedStore(CALLER, "DEFAULT");

		try {
			facade.update(2L, CALLER, DEFAULT_STORE, body(TARGET));
			fail("Expected UnauthorizedException");
		} catch (UnauthorizedException expected) {
			verify(userService, never()).update(any(User.class));
		}
	}

	@Test
	public void adminCanEditUserInSameStore_groupsAndActiveRestored() throws Exception {
		stubCaller(CALLER, 1L, "ADMIN");
		User target = stubTarget(2L, TARGET, "ADMIN_CATALOGUE");
		target.setActive(true);
		doReturn(true).when(facade).authorizedStore(CALLER, "DEFAULT");
		PersistableUser update = body(TARGET, "ADMIN");
		update.setActive(false);

		facade.update(2L, CALLER, DEFAULT_STORE, update);

		ArgumentCaptor<User> captor = ArgumentCaptor.forClass(User.class);
		verify(userService).update(captor.capture());
		assertEquals(Arrays.asList("ADMIN_CATALOGUE"), groupNames(captor.getValue()));
		assertEquals(true, captor.getValue().isActive());
	}

	@Test
	public void passwordInBodyIgnored() throws Exception {
		stubCaller(CALLER, 1L, "SUPERADMIN");
		User target = stubTarget(2L, TARGET, "ADMIN_CATALOGUE");
		target.setAdminPassword("ORIGINAL_HASH");
		PersistableUser update = body(TARGET, "ADMIN_CATALOGUE");
		update.setPassword("NewPass1");

		facade.update(2L, CALLER, DEFAULT_STORE, update);

		ArgumentCaptor<User> targetCaptor = ArgumentCaptor.forClass(User.class);
		verify(userService).update(targetCaptor.capture());
		assertEquals("ORIGINAL_HASH", targetCaptor.getValue().getAdminPassword());
		ArgumentCaptor<PersistableUser> bodyCaptor = ArgumentCaptor.forClass(PersistableUser.class);
		verify(persistableUserPopulator).populate(bodyCaptor.capture(), any(User.class), any(MerchantStore.class),
				any(Language.class));
		assertEquals(null, bodyCaptor.getValue().getPassword());
	}

	@Test
	public void blankBodyStoreKeepsTargetStore() throws Exception {
		stubCaller(CALLER, 1L, "SUPERADMIN");
		stubTarget(2L, TARGET, "ADMIN_CATALOGUE");
		PersistableUser update = body(TARGET);
		update.setStore(null);

		facade.update(2L, CALLER, OTHER_STORE, update);

		ArgumentCaptor<PersistableUser> bodyCaptor = ArgumentCaptor.forClass(PersistableUser.class);
		verify(persistableUserPopulator).populate(bodyCaptor.capture(), any(User.class), any(MerchantStore.class),
				any(Language.class));
		assertEquals("DEFAULT", bodyCaptor.getValue().getStore());
	}

	@Test
	public void selfEditCannotChangeOwningStore() throws Exception {
		stubCaller(CALLER, 2L, "ADMIN_CATALOGUE");
		stubTarget(2L, CALLER, "ADMIN_CATALOGUE");
		PersistableUser update = body(CALLER);
		update.setStore("OTHER");

		try {
			facade.update(2L, CALLER, OTHER_STORE, update);
			fail("Expected OperationNotAllowedException");
		} catch (OperationNotAllowedException expected) {
			verify(userService, never()).update(any(User.class));
		}
	}

	@Test
	public void selfEditProfileAllowed() throws Exception {
		stubCaller(CALLER, 2L, "ADMIN_CATALOGUE");
		stubTarget(2L, CALLER, "ADMIN_CATALOGUE");
		PersistableUser update = body(CALLER);
		update.setFirstName("Updated");

		ReadableUser result = facade.update(2L, CALLER, DEFAULT_STORE, update);

		assertNotNull(result);
		verify(userService).update(any(User.class));
	}

	@Test
	public void superadminCallerMayChangeGroups() throws Exception {
		stubCaller(CALLER, 1L, "SUPERADMIN");
		stubTarget(2L, TARGET, "ADMIN_CATALOGUE");

		facade.update(2L, CALLER, DEFAULT_STORE, body(TARGET, "ADMIN"));

		ArgumentCaptor<User> captor = ArgumentCaptor.forClass(User.class);
		verify(userService).update(captor.capture());
		assertEquals(Arrays.asList("ADMIN"), groupNames(captor.getValue()));
	}

	private User stubCaller(String username, long id, String... groupNames) throws Exception {
		User caller = user(id, username, groupNames);
		when(userService.getByUserName(username)).thenReturn(caller);
		return caller;
	}

	private User stubTarget(long id, String username, String... groupNames) throws Exception {
		User target = user(id, username, groupNames);
		when(userService.getById(id)).thenReturn(target);
		when(userService.getByUserName(username)).thenReturn(target);
		return target;
	}

	private User user(long id, String username, String... groupNames) {
		User user = new User();
		user.setId(id);
		user.setAdminName(username);
		user.setAdminEmail(username);
		user.setAdminPassword("HASH");
		user.setMerchantStore(DEFAULT_STORE);
		user.setDefaultLanguage(language);
		user.setActive(true);
		user.setGroups(Arrays.stream(groupNames).map(UserFacadeImplUpdateTest::group).collect(Collectors.toList()));
		return user;
	}

	private static Group group(String name) {
		Group group = new Group(name);
		group.setId(Math.abs(name.hashCode()) + 1);
		return group;
	}

	private PersistableUser body(String username, String... groupNames) {
		PersistableUser user = new PersistableUser();
		user.setUserName(username);
		user.setEmailAddress(username);
		user.setFirstName("Changed");
		user.setLastName("User");
		user.setDefaultLanguage("en");
		user.setGroups(Arrays.stream(groupNames).map(PersistableGroup::new).collect(Collectors.toList()));
		return user;
	}

	private List<String> groupNames(User user) {
		return user.getGroups().stream().map(Group::getGroupName).collect(Collectors.toList());
	}
}
