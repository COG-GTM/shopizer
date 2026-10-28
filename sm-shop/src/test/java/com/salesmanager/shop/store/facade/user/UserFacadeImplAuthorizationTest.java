package com.salesmanager.shop.store.facade.user;

import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.fail;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnitRunner;
import org.springframework.security.authentication.AnonymousAuthenticationToken;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.AuthorityUtils;
import org.springframework.security.core.context.SecurityContextHolder;

import com.salesmanager.core.business.services.merchant.MerchantStoreService;
import com.salesmanager.core.business.services.reference.language.LanguageService;
import com.salesmanager.core.business.services.user.PermissionService;
import com.salesmanager.core.business.services.user.UserService;
import com.salesmanager.core.model.merchant.MerchantStore;
import com.salesmanager.core.model.reference.language.Language;
import com.salesmanager.core.model.user.Group;
import com.salesmanager.core.model.user.Permission;
import com.salesmanager.core.model.user.User;
import com.salesmanager.shop.model.security.PersistableGroup;
import com.salesmanager.shop.model.user.PersistableUser;
import com.salesmanager.shop.model.user.ReadableUser;
import com.salesmanager.shop.populator.user.PersistableUserPopulator;
import com.salesmanager.shop.store.api.exception.UnauthorizedException;
import com.salesmanager.shop.store.controller.security.facade.SecurityFacade;

@RunWith(MockitoJUnitRunner.Silent.class)
public class UserFacadeImplAuthorizationTest {

	@Mock
	private UserService userService;

	@Mock
	private MerchantStoreService merchantStoreService;

	@Mock
	private PersistableUserPopulator persistableUserPopulator;

	@Mock
	private SecurityFacade securityFacade;

	@Mock
	private LanguageService languageService;

	@Mock
	private PermissionService permissionService;

	@InjectMocks
	private UserFacadeImpl userFacade;

	private Language language;

	@Before
	public void setUp() throws Exception {
		language = new Language();
		language.setCode("en");
		when(languageService.defaultLanguage()).thenReturn(language);
		when(securityFacade.matchRawPasswords(anyString(), anyString())).thenReturn(true);
		when(securityFacade.validateUserPassword(anyString())).thenReturn(true);
		when(securityFacade.encodePassword(anyString())).thenReturn("encoded-password");
		when(permissionService.getPermissions(anyList())).thenReturn(Collections.<Permission>emptyList());
	}

	@After
	public void clearSecurityContext() {
		SecurityContextHolder.clearContext();
	}

	@Test
	public void superadminMayTargetSuperadminGroup() throws Exception {
		User caller = user("superadmin@example.com", "DEFAULT", "SUPERADMIN");
		when(userService.getByUserName(caller.getAdminName())).thenReturn(caller);
		authenticate(caller.getAdminName());

		userFacade.authorizedGroups(caller.getAdminName(), persistableUser("SUPERADMIN"));
	}

	@Test
	public void adminCannotTargetSuperadminGroupRegardlessOfCaseOrWhitespace() throws Exception {
		User caller = user("admin@example.com", "DEFAULT", "ADMIN");
		when(userService.getByUserName(caller.getAdminName())).thenReturn(caller);
		authenticate(caller.getAdminName());

		for (String groupName : Arrays.asList("SUPERADMIN", "superadmin", " SUPERADMIN ")) {
			try {
				userFacade.authorizedGroups(caller.getAdminName(), persistableUser(groupName));
				fail("Expected SUPERADMIN group to be rejected: " + groupName);
			} catch (UnauthorizedException expected) {
				// expected
			}
		}
	}

	@Test
	public void adminMayTargetAdminGroup() throws Exception {
		User caller = user("admin@example.com", "DEFAULT", "ADMIN");
		when(userService.getByUserName(caller.getAdminName())).thenReturn(caller);
		authenticate(caller.getAdminName());

		userFacade.authorizedGroups(caller.getAdminName(), persistableUser("ADMIN"));
	}

	@Test
	public void authorizedGroupsIgnoresNullGroupsAndNullEntries() throws Exception {
		User caller = user("admin@example.com", "DEFAULT", "ADMIN");
		when(userService.getByUserName(caller.getAdminName())).thenReturn(caller);
		authenticate(caller.getAdminName());

		PersistableUser target = new PersistableUser();
		target.setGroups(null);
		userFacade.authorizedGroups(caller.getAdminName(), target);

		target.setGroups(Collections.singletonList(null));
		userFacade.authorizedGroups(caller.getAdminName(), target);
	}

	@Test
	public void missingCallerIsUnauthorized() throws Exception {
		when(userService.getByUserName("missing@example.com")).thenReturn(null);
		authenticate("missing@example.com");

		try {
			userFacade.authorizedGroups("missing@example.com", persistableUser("ADMIN"));
			fail("Expected missing caller to be rejected");
		} catch (UnauthorizedException expected) {
			// expected
		}
	}

	@Test
	public void createRejectsResolvedSuperadminGroupWithoutSaving() throws Exception {
		assertCreateRejected("SUPERADMIN", "DEFAULT", "DEFAULT", "ADMIN");
	}

	@Test
	public void createRejectsResolvedLowercaseSuperadminGroupWithoutSaving() throws Exception {
		assertCreateRejected("superadmin ", "DEFAULT", "DEFAULT", "ADMIN");
	}

	@Test
	public void createRejectsUnrelatedStoreWithoutSaving() throws Exception {
		PersistableUser request = persistableUser("ADMIN");
		MerchantStore requestStore = store("DEFAULT");
		User caller = user("admin@example.com", "DEFAULT", "ADMIN");
		User target = user("new@example.com", "other", "ADMIN");
		when(merchantStoreService.getParent("other")).thenReturn(store("other"));
		configureCreate(request, requestStore, caller, target);
		authenticate(caller.getAdminName());

		try {
			userFacade.create(request, requestStore);
			fail("Expected unrelated target store to be rejected");
		} catch (UnauthorizedException expected) {
			verify(userService, never()).saveOrUpdate(any(User.class));
		}
	}

	@Test
	public void createAllowsParentStoreAdminToCreateInChildStore() throws Exception {
		PersistableUser request = persistableUser("ADMIN");
		MerchantStore requestStore = store("parent");
		User caller = user("parent-admin@example.com", "parent", "ADMIN");
		User target = user("new@example.com", "child", "ADMIN");
		when(merchantStoreService.getParent("child")).thenReturn(store("parent"));
		configureCreate(request, requestStore, caller, target);
		authenticate(caller.getAdminName());

		ReadableUser created = userFacade.create(request, requestStore);

		assertNotNull(created);
		verify(userService).saveOrUpdate(target);
	}

	@Test
	public void createAllowsAdminToCreateInOwnStore() throws Exception {
		PersistableUser request = persistableUser("ADMIN");
		MerchantStore requestStore = store("DEFAULT");
		User caller = user("admin@example.com", "DEFAULT", "ADMIN");
		User target = user("new@example.com", "DEFAULT", "ADMIN");
		configureCreate(request, requestStore, caller, target);
		authenticate(caller.getAdminName());

		ReadableUser created = userFacade.create(request, requestStore);

		assertNotNull(created);
		verify(userService).saveOrUpdate(target);
	}

	@Test
	public void superadminMayCreateSuperadminInAnotherStoreWithoutParentLookup() throws Exception {
		PersistableUser request = persistableUser("SUPERADMIN");
		MerchantStore requestStore = store("DEFAULT");
		User caller = user("superadmin@example.com", "DEFAULT", "SUPERADMIN");
		User target = user("new@example.com", "other", "SUPERADMIN");
		configureCreate(request, requestStore, caller, target);
		authenticate(caller.getAdminName());

		ReadableUser created = userFacade.create(request, requestStore);

		assertNotNull(created);
		verify(userService).saveOrUpdate(target);
		verify(merchantStoreService, never()).getParent(anyString());
	}

	@Test
	public void anonymousAuthenticationCannotCreateUser() throws Exception {
		PersistableUser request = persistableUser("ADMIN");
		MerchantStore requestStore = store("DEFAULT");
		User caller = user("admin@example.com", "DEFAULT", "ADMIN");
		User target = user("new@example.com", "DEFAULT", "ADMIN");
		configureCreate(request, requestStore, caller, target);
		SecurityContextHolder.getContext().setAuthentication(new AnonymousAuthenticationToken("key",
				"anonymousUser", AuthorityUtils.createAuthorityList("ROLE_ANONYMOUS")));

		try {
			userFacade.create(request, requestStore);
			fail("Expected anonymous caller to be rejected");
		} catch (UnauthorizedException expected) {
			verify(userService, never()).saveOrUpdate(any(User.class));
		}
	}

	private void assertCreateRejected(String groupName, String targetStoreCode, String callerStoreCode,
			String callerGroup) throws Exception {
		PersistableUser request = persistableUser(groupName);
		MerchantStore requestStore = store(callerStoreCode);
		User caller = user("admin@example.com", callerStoreCode, callerGroup);
		User target = user("new@example.com", targetStoreCode, groupName);
		configureCreate(request, requestStore, caller, target);
		authenticate(caller.getAdminName());

		try {
			userFacade.create(request, requestStore);
			fail("Expected SUPERADMIN target group to be rejected");
		} catch (UnauthorizedException expected) {
			verify(userService, never()).saveOrUpdate(any(User.class));
		}
	}

	private void configureCreate(PersistableUser request, MerchantStore requestStore, User caller, User target)
			throws Exception {
		when(userService.getByUserName(request.getUserName(), requestStore.getCode())).thenReturn(null);
		when(userService.getByUserName(caller.getAdminName())).thenReturn(caller);
		when(persistableUserPopulator.populate(any(PersistableUser.class), any(User.class),
				any(MerchantStore.class), any(Language.class))).thenReturn(target);
		when(userService.getById(target.getId())).thenReturn(target);
	}

	private void authenticate(String userName) {
		SecurityContextHolder.getContext().setAuthentication(
				new UsernamePasswordAuthenticationToken(userName, "password"));
	}

	private PersistableUser persistableUser(String groupName) {
		PersistableUser user = new PersistableUser();
		user.setUserName("new@example.com");
		user.setEmailAddress("new@example.com");
		user.setPassword("password");
		user.setRepeatPassword("password");
		List<PersistableGroup> groups = new ArrayList<>();
		PersistableGroup group = new PersistableGroup();
		group.setName(groupName);
		groups.add(group);
		user.setGroups(groups);
		return user;
	}

	private User user(String userName, String storeCode, String groupName) {
		MerchantStore merchantStore = store(storeCode);
		Group group = new Group(groupName);
		group.setId(1);
		User user = new User();
		user.setId(1L);
		user.setAdminName(userName);
		user.setAdminEmail(userName);
		user.setMerchantStore(merchantStore);
		user.setDefaultLanguage(language);
		user.setGroups(Collections.singletonList(group));
		return user;
	}

	private MerchantStore store(String code) {
		MerchantStore store = new MerchantStore();
		store.setCode(code);
		return store;
	}
}
