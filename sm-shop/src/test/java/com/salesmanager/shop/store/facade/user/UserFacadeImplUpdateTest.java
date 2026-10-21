package com.salesmanager.shop.store.facade.user;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Spy;
import org.mockito.junit.MockitoJUnitRunner;
import org.springframework.test.util.ReflectionTestUtils;

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
import com.salesmanager.shop.populator.user.PersistableUserPopulator;
import com.salesmanager.shop.store.api.exception.OperationNotAllowedException;
import com.salesmanager.shop.store.api.exception.ResourceNotFoundException;
import com.salesmanager.shop.store.api.exception.ServiceRuntimeException;
import com.salesmanager.shop.store.api.exception.UnauthorizedException;

@RunWith(MockitoJUnitRunner.Silent.class)
public class UserFacadeImplUpdateTest {

	@Mock
	private UserService userService;

	@Mock
	private LanguageService languageService;

	@Mock
	private PersistableUserPopulator persistableUserPopulator;

	@Mock
	private MerchantStoreService merchantStoreService;

	@Mock
	private PermissionService permissionService;

	@Spy
	@InjectMocks
	private UserFacadeImpl facade;

	private Language language;
	private MerchantStore defaultStore;

	@Before
	public void setUp() {
		language = new Language();
		language.setCode("en");
		defaultStore = new MerchantStore(1, "DEFAULT", "Default");
		ReflectionTestUtils.setField(facade, "languageService", languageService);
		ReflectionTestUtils.setField(facade, "persistableUserPopulator", persistableUserPopulator);
	}

	@Test
	public void catalogueAdminCannotUpdateAnotherCatalogueAdmin() throws Exception {
		User caller = user("ADMIN_CATALOGUE");
		User target = user("ADMIN_CATALOGUE");
		PersistableUser body = body(target);
		stubUpdate(caller, target);

		assertThrows(UnauthorizedException.class,
				() -> facade.update(target.getId(), caller.getAdminName(), defaultStore, body));

		verify(userService, never()).update(any(User.class));
	}

	@Test
	public void adminCannotUpdateSuperadmin() throws Exception {
		User caller = user("ADMIN");
		User target = user("SUPERADMIN");
		PersistableUser body = body(target);
		stubUpdate(caller, target);

		assertThrows(UnauthorizedException.class,
				() -> facade.update(target.getId(), caller.getAdminName(), defaultStore, body));

		verify(userService, never()).update(any(User.class));
	}

	@Test
	public void adminCannotUpdateWhenDefaultStoreAuthorizationIsDenied() throws Exception {
		User caller = user("ADMIN");
		User target = user("ADMIN_CATALOGUE");
		PersistableUser body = body(target);
		stubUpdate(caller, target);
		doReturn(false).when(facade).authorizedStore(caller.getAdminName(), "DEFAULT");

		assertThrows(UnauthorizedException.class,
				() -> facade.update(target.getId(), caller.getAdminName(), defaultStore, body));

		verify(userService, never()).update(any(User.class));
	}

	@Test
	public void adminCannotMoveTargetToUnauthorizedStore() throws Exception {
		User caller = user("ADMIN");
		User target = user("ADMIN_CATALOGUE");
		PersistableUser body = body(target);
		body.setStore("OTHER");
		stubUpdate(caller, target);
		doReturn(true).when(facade).authorizedStore(caller.getAdminName(), "DEFAULT");
		doReturn(false).when(facade).authorizedStore(caller.getAdminName(), "OTHER");

		assertThrows(UnauthorizedException.class,
				() -> facade.update(target.getId(), caller.getAdminName(), defaultStore, body));

		verify(userService, never()).update(any(User.class));
	}

	@Test
	public void superadminMayUpdateCatalogueUserWithoutStoreAuthorization() throws Exception {
		User caller = user("SUPERADMIN");
		User target = user("ADMIN_CATALOGUE");
		PersistableUser body = body(target);
		body.setPassword("NEW_PASSWORD");
		stubUpdate(caller, target);

		facade.update(target.getId(), caller.getAdminName(), defaultStore, body);

		ArgumentCaptor<PersistableUser> captured = ArgumentCaptor.forClass(PersistableUser.class);
		verify(persistableUserPopulator).populate(captured.capture(), eq(target), eq(defaultStore), eq(language));
		assertNull(captured.getValue().getPassword());
		assertEquals("ORIGINAL_HASH", target.getAdminPassword());
		assertEquals(Collections.singletonList("FROM_BODY"), groupNames(target.getGroups()));
		verify(facade, never()).authorizedStore(anyString(), anyString());
	}

	@Test
	public void adminUpdatePreservesGroupsActiveStateAndPassword() throws Exception {
		User caller = user("ADMIN");
		User target = user("ADMIN_CATALOGUE");
		target.setActive(true);
		PersistableUser body = body(target);
		body.setActive(false);
		body.setPassword("NEW_PASSWORD");
		stubUpdate(caller, target);
		doReturn(true).when(facade).authorizedStore(caller.getAdminName(), "DEFAULT");

		facade.update(target.getId(), caller.getAdminName(), defaultStore, body);

		assertEquals(Collections.singletonList("ADMIN_CATALOGUE"), groupNames(target.getGroups()));
		assertEquals(true, target.isActive());
		assertEquals("ORIGINAL_HASH", target.getAdminPassword());
	}

	@Test
	public void selfEditWithBlankStoreKeepsOwningStoreAndGroups() throws Exception {
		User caller = user("ADMIN_CATALOGUE");
		PersistableUser body = body(caller);
		body.setStore(" ");
		stubUpdate(caller, caller);

		facade.update(caller.getId(), caller.getAdminName(), defaultStore, body);

		assertEquals("DEFAULT", caller.getMerchantStore().getCode());
		assertEquals(Collections.singletonList("ADMIN_CATALOGUE"), groupNames(caller.getGroups()));
	}

	@Test
	public void selfEditCannotChangeOwningStore() throws Exception {
		User caller = user("ADMIN_CATALOGUE");
		PersistableUser body = body(caller);
		body.setStore("OTHER");
		stubUpdate(caller, caller);

		assertThrows(OperationNotAllowedException.class,
				() -> facade.update(caller.getId(), caller.getAdminName(), defaultStore, body));

		verify(userService, never()).update(any(User.class));
	}

	@Test
	public void unknownCallerDoesNotLookUpTarget() throws Exception {
		User target = user("ADMIN_CATALOGUE");
		PersistableUser body = body(target);
		when(languageService.defaultLanguage()).thenReturn(language);
		when(userService.getByUserName("missing@shopizer.com")).thenReturn(null);

		assertThrows(UnauthorizedException.class,
				() -> facade.update(target.getId(), "missing@shopizer.com", defaultStore, body));

		verify(userService, never()).getById(target.getId());
		verify(userService, never()).update(any(User.class));
	}

	@Test
	public void unknownTargetRaisesNotFound() throws Exception {
		User caller = user("SUPERADMIN");
		PersistableUser body = body(user("ADMIN"));
		when(languageService.defaultLanguage()).thenReturn(language);
		when(userService.getByUserName(caller.getAdminName())).thenReturn(caller);
		when(userService.getById(42L)).thenReturn(null);

		assertThrows(ResourceNotFoundException.class,
				() -> facade.update(42L, caller.getAdminName(), defaultStore, body));

		verify(userService, never()).update(any(User.class));
	}

	@Test
	public void bodyUsernameBelongingToAnotherUserIsRejected() throws Exception {
		User caller = user("ADMIN");
		User target = user("ADMIN_CATALOGUE");
		User other = user("ADMIN_ORDER");
		PersistableUser body = body(target);
		body.setUserName(other.getAdminName());
		stubUpdate(caller, target);
		when(userService.getByUserName(other.getAdminName())).thenReturn(other);
		doReturn(true).when(facade).authorizedStore(caller.getAdminName(), "DEFAULT");

		assertThrows(ServiceRuntimeException.class,
				() -> facade.update(target.getId(), caller.getAdminName(), defaultStore, body));

		verify(userService, never()).update(any(User.class));
	}

	@Test
	public void superadminUpdatePreservesTargetsSuperadminGroup() throws Exception {
		User caller = user("SUPERADMIN");
		User target = user("SUPERADMIN");
		PersistableUser body = body(target);
		stubUpdate(caller, target);

		facade.update(target.getId(), caller.getAdminName(), defaultStore, body);

		assertEquals(Collections.singletonList("SUPERADMIN"), groupNames(target.getGroups()));
		verify(userService).update(target);
	}

	private void stubUpdate(User caller, User target) throws Exception {
		when(languageService.defaultLanguage()).thenReturn(language);
		when(userService.getByUserName(caller.getAdminName())).thenReturn(caller);
		when(userService.getById(target.getId())).thenReturn(target);
		when(userService.getByUserName(target.getAdminName())).thenReturn(target);
		doReturn(Collections.emptyList()).when(facade).findPermissionsByGroups(anyList());
		doAnswer(invocation -> {
			PersistableUser input = invocation.getArgument(0);
			User destination = invocation.getArgument(1);
			MerchantStore store = invocation.getArgument(2);
			destination.setFirstName(input.getFirstName());
			if (input.getPassword() != null && !input.getPassword().trim().isEmpty()) {
				destination.setAdminPassword(input.getPassword());
			}
			if (input.getStore() != null && !input.getStore().trim().isEmpty()) {
				MerchantStore bodyStore = new MerchantStore();
				bodyStore.setCode(input.getStore());
				destination.setMerchantStore(bodyStore);
			} else {
				destination.setMerchantStore(store);
			}
			destination.setActive(input.isActive());
			destination.setGroups(new ArrayList<>(Collections.singletonList(group("FROM_BODY"))));
			return destination;
		}).when(persistableUserPopulator).populate(any(PersistableUser.class), any(User.class),
				any(MerchantStore.class), any(Language.class));
	}

	private User user(String groupName) {
		String username = "u-" + UUID.randomUUID() + "@shopizer.com";
		User user = new User();
		user.setId(Math.abs(UUID.randomUUID().getMostSignificantBits()));
		user.setAdminName(username);
		user.setAdminEmail(username);
		user.setFirstName("Original");
		user.setLastName("User");
		user.setAdminPassword("ORIGINAL_HASH");
		user.setMerchantStore(defaultStore);
		user.setGroups(new ArrayList<>(Collections.singletonList(group(groupName))));
		user.setActive(true);
		return user;
	}

	private PersistableUser body(User target) {
		PersistableUser body = new PersistableUser();
		body.setUserName(target.getAdminName());
		body.setEmailAddress(target.getAdminEmail());
		body.setFirstName("Updated");
		body.setLastName(target.getLastName());
		body.setStore("DEFAULT");
		body.setActive(true);
		return body;
	}

	private Group group(String name) {
		Group group = new Group();
		group.setGroupName(name);
		group.setId(Math.abs(UUID.randomUUID().hashCode()));
		return group;
	}

	private List<String> groupNames(List<Group> groups) {
		return groups.stream().map(Group::getGroupName).collect(Collectors.toList());
	}
}
