package com.salesmanager.test.shop.unit.user;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;

import java.util.Arrays;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnitRunner;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;

import com.salesmanager.core.business.services.merchant.MerchantStoreService;
import com.salesmanager.core.business.services.reference.language.LanguageService;
import com.salesmanager.core.business.services.user.PermissionService;
import com.salesmanager.core.business.services.user.UserService;
import com.salesmanager.core.model.merchant.MerchantStore;
import com.salesmanager.core.model.user.Group;
import com.salesmanager.core.model.user.User;
import com.salesmanager.shop.constants.Constants;
import com.salesmanager.shop.store.facade.user.UserFacadeImpl;

@RunWith(MockitoJUnitRunner.Silent.class)
public class UserFacadeAuthorizeStoreTest {

	private static final String PRIVATE_PATH = "/api/v1/private/products";
	private static final String USER_NAME = "mallory@acme.com";

	@Mock
	private UserService userService;

	@Mock
	private LanguageService languageService;

	@Mock
	private PermissionService permissionService;

	@Mock
	private MerchantStoreService merchantStoreService;

	@InjectMocks
	private UserFacadeImpl userFacade;

	private MerchantStore acme;
	private MerchantStore acmeWest;
	private MerchantStore acmeWest2;
	private MerchantStore other;

	@Before
	public void setUp() throws Exception {
		acme = store("ACME", null);
		acmeWest = store("ACME-WEST", acme);
		acmeWest2 = store("ACME-WEST-2", acmeWest);
		other = store("OTHER", null);
		// mirrors MerchantStoreServiceImpl.getParent: parent of the given store, or the store itself
		when(merchantStoreService.getParent(anyString())).thenAnswer(invocation -> {
			String code = invocation.getArgument(0);
			MerchantStore s = Arrays.asList(acme, acmeWest, acmeWest2, other).stream()
					.filter(m -> m.getCode().equals(code)).findFirst().orElseThrow(IllegalStateException::new);
			return s.getParent() == null ? s : s.getParent();
		});
		SecurityContextHolder.getContext()
				.setAuthentication(new UsernamePasswordAuthenticationToken(USER_NAME, "n/a"));
	}

	@After
	public void tearDown() {
		SecurityContextHolder.clearContext();
	}

	@Test
	public void userInOwnStoreIsAuthorized() throws Exception {
		userIn(acmeWest, "ADMIN");
		assertTrue(userFacade.authorizeStore(acmeWest, PRIVATE_PATH));
	}

	@Test
	public void parentStoreUserIsAuthorizedOnChildStore() throws Exception {
		userIn(acme, "ADMIN");
		assertTrue(userFacade.authorizeStore(acmeWest, PRIVATE_PATH));
	}

	@Test
	public void childStoreUserIsNotAuthorizedOnParentStore() throws Exception {
		userIn(acmeWest2, "ADMIN");
		assertFalse(userFacade.authorizeStore(acmeWest, PRIVATE_PATH));
	}

	@Test
	public void childStoreUserIsNotAuthorizedOnRootStore() throws Exception {
		userIn(acmeWest, "ADMIN");
		assertFalse(userFacade.authorizeStore(acme, PRIVATE_PATH));
	}

	@Test
	public void siblingOrUnrelatedStoreUserIsNotAuthorized() throws Exception {
		userIn(other, "ADMIN");
		assertFalse(userFacade.authorizeStore(acmeWest, PRIVATE_PATH));
		assertFalse(userFacade.authorizeStore(acme, PRIVATE_PATH));
	}

	@Test
	public void superadminIsAuthorizedOnAnyStore() throws Exception {
		userIn(acmeWest2, Constants.GROUP_SUPERADMIN);
		assertTrue(userFacade.authorizeStore(acme, PRIVATE_PATH));
	}

	@Test
	public void publicPathIsNotChecked() throws Exception {
		assertTrue(userFacade.authorizeStore(acme, "/api/v1/products"));
	}

	private void userIn(MerchantStore store, String groupName) throws Exception {
		User user = new User(USER_NAME, "n/a", USER_NAME);
		user.setId(1L);
		user.setMerchantStore(store);
		Group group = new Group(groupName);
		group.setId(1);
		user.getGroups().add(group);
		when(userService.getByUserName(USER_NAME)).thenReturn(user);
	}

	private static MerchantStore store(String code, MerchantStore parent) {
		MerchantStore store = new MerchantStore();
		store.setCode(code);
		store.setParent(parent);
		return store;
	}
}
