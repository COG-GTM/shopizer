package com.salesmanager.test.shop.unit.store;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.util.ReflectionTestUtils;

import com.salesmanager.core.business.services.merchant.MerchantStoreService;
import com.salesmanager.core.business.services.reference.language.LanguageService;
import com.salesmanager.core.business.services.user.PermissionService;
import com.salesmanager.core.business.services.user.UserService;
import com.salesmanager.core.model.merchant.MerchantStore;
import com.salesmanager.core.model.reference.language.Language;
import com.salesmanager.core.model.user.Group;
import com.salesmanager.core.model.user.User;
import com.salesmanager.shop.store.facade.user.UserFacadeImpl;

/**
 * Unit tests for {@link UserFacadeImpl#authorizeStore(MerchantStore, String)}:
 * the admin of a parent store can reach its child stores, never the reverse.
 */
public class UserFacadeImplAuthorizeStoreTest {

	private static final String PRIVATE_PATH = "/api/v1/private/customers";
	private static final String USER = "admin@store.com";

	private UserFacadeImpl facade;
	private MerchantStoreService merchantStoreService;
	private UserService userService;

	@BeforeEach
	public void setUp() throws Exception {
		facade = new UserFacadeImpl();
		merchantStoreService = mock(MerchantStoreService.class);
		userService = mock(UserService.class);
		LanguageService languageService = mock(LanguageService.class);
		PermissionService permissionService = mock(PermissionService.class);
		Language en = new Language("en");
		when(languageService.defaultLanguage()).thenReturn(en);
		when(permissionService.getPermissions(org.mockito.ArgumentMatchers.anyList()))
				.thenReturn(new ArrayList<>());
		ReflectionTestUtils.setField(facade, "merchantStoreService", merchantStoreService);
		ReflectionTestUtils.setField(facade, "userService", userService);
		ReflectionTestUtils.setField(facade, "languageService", languageService);
		ReflectionTestUtils.setField(facade, "permissionService", permissionService);
		SecurityContextHolder.getContext()
				.setAuthentication(new UsernamePasswordAuthenticationToken(USER, "n/a", Collections.emptyList()));
	}

	@AfterEach
	public void tearDown() {
		SecurityContextHolder.clearContext();
	}

	@Test
	public void userOfRequestedStoreIsAuthorized() throws Exception {
		MerchantStore own = store("own", null);
		givenUser(own, "ADMIN");
		assertTrue(facade.authorizeStore(own, PRIVATE_PATH));
	}

	@Test
	public void superadminIsAuthorizedOnAnyStore() throws Exception {
		givenUser(store("home", null), "SUPERADMIN");
		assertTrue(facade.authorizeStore(store("other", null), PRIVATE_PATH));
	}

	@Test
	public void parentStoreAdminIsAuthorizedOnChildStore() throws Exception {
		MerchantStore parent = store("parent", null);
		parent.setRetailer(true);
		MerchantStore child = store("child", parent);
		givenUser(parent, "ADMIN");
		when(merchantStoreService.getParent("child")).thenReturn(parent);

		assertTrue(facade.authorizeStore(child, PRIVATE_PATH));
		verify(merchantStoreService).getParent("child");
	}

	@Test
	public void parentStoreCodeIsMatchedCaseInsensitively() throws Exception {
		MerchantStore parentAsLinked = store("PARENT", null);
		MerchantStore child = store("child", parentAsLinked);
		givenUser(store("parent", null), "ADMIN");
		when(merchantStoreService.getParent("child")).thenReturn(parentAsLinked);

		assertTrue(facade.authorizeStore(child, PRIVATE_PATH));
	}

	@Test
	public void childStoreAdminIsNotAuthorizedOnItsParentChain() throws Exception {
		// attacker store "mall" was attached under "globex", which itself has parent "parentx"
		MerchantStore parentx = store("parentx", null);
		MerchantStore globex = store("globex", parentx);
		MerchantStore mall = store("mall", globex);
		givenUser(mall, "ADMIN");
		when(merchantStoreService.getParent("mall")).thenReturn(globex);
		when(merchantStoreService.getParent("globex")).thenReturn(parentx);

		assertFalse(facade.authorizeStore(globex, PRIVATE_PATH));
	}

	@Test
	public void unrelatedAdminIsNotAuthorizedOnStoreWithParent() throws Exception {
		MerchantStore parent = store("parent", null);
		MerchantStore child = store("child", parent);
		givenUser(store("unrelated", null), "ADMIN");
		when(merchantStoreService.getParent("child")).thenReturn(parent);

		assertFalse(facade.authorizeStore(child, PRIVATE_PATH));
	}

	@Test
	public void unrelatedAdminIsNotAuthorizedOnStoreWithoutParent() throws Exception {
		givenUser(store("unrelated", null), "ADMIN");

		assertFalse(facade.authorizeStore(store("standalone", null), PRIVATE_PATH));
		verify(merchantStoreService, never()).getParent(anyString());
	}

	@Test
	public void nonPrivatePathIsAlwaysAuthorized() throws Exception {
		SecurityContextHolder.clearContext();
		assertTrue(facade.authorizeStore(store("any", null), "/api/v1/products"));
	}

	private MerchantStore store(String code, MerchantStore parent) {
		MerchantStore store = new MerchantStore();
		store.setCode(code);
		store.setParent(parent);
		return store;
	}

	private void givenUser(MerchantStore userStore, String groupName) throws Exception {
		User user = new User();
		user.setId(1L);
		user.setAdminName(USER);
		user.setMerchantStore(userStore);
		Group group = new Group(groupName);
		group.setId(1);
		List<Group> groups = new ArrayList<>();
		groups.add(group);
		user.setGroups(groups);
		when(userService.getByUserName(USER)).thenReturn(user);
	}
}
