package com.salesmanager.shop.store.facade.user;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.util.Collections;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Spy;
import org.mockito.junit.MockitoJUnitRunner;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;

import com.salesmanager.core.business.exception.ServiceException;
import com.salesmanager.core.business.services.merchant.MerchantStoreService;
import com.salesmanager.core.business.services.reference.language.LanguageService;
import com.salesmanager.core.business.services.user.PermissionService;
import com.salesmanager.core.business.services.user.UserService;
import com.salesmanager.core.model.merchant.MerchantStore;
import com.salesmanager.core.model.reference.language.Language;
import com.salesmanager.core.model.user.Group;
import com.salesmanager.core.model.user.User;
import com.salesmanager.shop.constants.Constants;
import com.salesmanager.shop.store.api.exception.UnauthorizedException;

@RunWith(MockitoJUnitRunner.class)
public class UserFacadeImplAuthorizeStoreTest {

	private static final String USER_NAME = "store-admin@example.com";

	@Mock
	private UserService userService;

	@Mock
	private MerchantStoreService merchantStoreService;

	@Mock
	private LanguageService languageService;

	@Mock
	private PermissionService permissionService;

	@Spy
	@InjectMocks
	private UserFacadeImpl facade;

	private User user;

	@Before
	public void setUp() throws Exception {
		user = user("OTHER");
		when(languageService.defaultLanguage()).thenReturn(new Language());
		when(userService.getByUserName(USER_NAME)).thenReturn(user);
		when(permissionService.getPermissions(anyList())).thenReturn(Collections.emptyList());
		SecurityContextHolder.getContext()
				.setAuthentication(new UsernamePasswordAuthenticationToken(USER_NAME, "password"));
	}

	@After
	public void clearSecurityContext() {
		SecurityContextHolder.clearContext();
	}

	@Test
	public void nonPrivatePathIsAuthorizedWithoutStoreLookup() {
		assertTrue(facade.authorizeStore(store("requested"), "/api/v1/products"));
		verifyNoInteractions(merchantStoreService);
	}

	@Test
	public void userStoreMatchesRequestedStoreIgnoringCase() {
		user.setMerchantStore(store("same-store"));

		assertTrue(facade.authorizeStore(store("SAME-STORE"), "/api/v1/private/customers"));
	}

	@Test
	public void superadminCanAccessAnyRequestedStore() {
		user.setGroups(Collections.singletonList(group(Constants.GROUP_SUPERADMIN)));

		assertTrue(facade.authorizeStore(store("requested"), "/api/v1/private/customers"));
	}

	@Test
	public void unrelatedUserCannotAccessStoreWithoutParent() {
		user.setMerchantStore(store("OTHER"));

		assertFalse(facade.authorizeStore(store("requested"), "/api/v1/private/customers"));
	}

	@Test
	public void parentUserCanAccessRequestedChildStore() throws Exception {
		MerchantStore child = store("child");
		child.setParent(store("parent"));
		user.setMerchantStore(store("PARENT"));
		when(merchantStoreService.getParent("child")).thenReturn(store("parent"));

		assertTrue(facade.authorizeStore(child, "/api/v1/private/customers"));
	}

	@Test
	public void childUserCannotAccessItsParentStore() {
		user.setMerchantStore(store("child"));

		assertFalse(facade.authorizeStore(store("parent"), "/api/v1/private/customers"));
	}

	@Test
	public void siblingStoreUserCannotAccessRequestedChild() throws Exception {
		MerchantStore sibling = store("child-b");
		sibling.setParent(store("parent"));
		user.setMerchantStore(store("child-a"));
		when(merchantStoreService.getParent("child-b")).thenReturn(store("parent"));

		assertFalse(facade.authorizeStore(sibling, "/api/v1/private/customers"));
	}

	@Test
	public void storeCannotAuthorizeItsOwnParentLink() throws Exception {
		MerchantStore requested = store("retailer");
		requested.setParent(requested);
		user.setMerchantStore(store("OTHER"));
		when(merchantStoreService.getParent("retailer")).thenReturn(requested);

		assertFalse(facade.authorizeStore(requested, "/api/v1/private/customers"));
	}

	@Test(expected = UnauthorizedException.class)
	public void missingUserIsRejected() throws Exception {
		when(userService.getByUserName(USER_NAME)).thenReturn(null);

		facade.authorizeStore(store("requested"), "/api/v1/private/customers");
	}

	@Test(expected = UnauthorizedException.class)
	public void parentLookupFailureIsUnauthorized() throws Exception {
		MerchantStore child = store("child");
		child.setParent(store("parent"));
		when(merchantStoreService.getParent("child")).thenThrow(new ServiceException("failure"));

		facade.authorizeStore(child, "/api/v1/private/customers");
	}

	private static MerchantStore store(String code) {
		MerchantStore store = new MerchantStore();
		store.setCode(code);
		return store;
	}

	private static User user(String merchant) {
		User user = new User();
		user.setMerchantStore(store(merchant));
		return user;
	}

	private static Group group(String name) {
		Group group = new Group();
		group.setId(1);
		group.setGroupName(name);
		return group;
	}
}
