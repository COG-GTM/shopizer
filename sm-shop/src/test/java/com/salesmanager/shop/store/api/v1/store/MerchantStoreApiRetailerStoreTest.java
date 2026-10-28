package com.salesmanager.shop.store.api.v1.store;

import static org.junit.Assert.fail;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.security.Principal;
import java.util.Arrays;

import javax.servlet.http.HttpServletRequest;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnitRunner;

import com.salesmanager.core.model.merchant.MerchantStore;
import com.salesmanager.shop.constants.Constants;
import com.salesmanager.shop.model.store.PersistableMerchantStore;
import com.salesmanager.shop.store.api.exception.UnauthorizedException;
import com.salesmanager.shop.store.controller.store.facade.StoreFacade;
import com.salesmanager.shop.store.controller.user.facade.UserFacade;

@RunWith(MockitoJUnitRunner.class)
public class MerchantStoreApiRetailerStoreTest {

	private static final String USER_NAME = "store-admin@example.com";
	private static final String STORE_CODE = "store";

	@Mock
	private StoreFacade storeFacade;

	@Mock
	private UserFacade userFacade;

	@Mock
	private HttpServletRequest request;

	@InjectMocks
	private MerchantStoreApi api;

	@Before
	public void setUp() {
		when(userFacade.authenticatedUser()).thenReturn(USER_NAME);
		when(request.getUserPrincipal()).thenReturn((Principal) () -> USER_NAME);
		when(userFacade.authorizedStore(USER_NAME, STORE_CODE)).thenReturn(true);
		when(userFacade.userInRoles(eq(USER_NAME), anyList())).thenReturn(false);
	}

	@Test
	public void updateAllowsBlankRetailerStore() {
		update(null, existingStore(null));

		verify(storeFacade).update(any(PersistableMerchantStore.class));
	}

	@Test
	public void updateAllowsSuperadminToSetArbitraryParent() {
		when(userFacade.userInRoles(USER_NAME, Arrays.asList(Constants.GROUP_SUPERADMIN))).thenReturn(true);
		when(storeFacade.getByCode(STORE_CODE)).thenReturn(existingStore(null));

		api.update(STORE_CODE, store("unrelated"), request);

		verify(storeFacade).update(any(PersistableMerchantStore.class));
	}

	@Test
	public void updateAllowsNonSuperadminToKeepExistingParent() {
		update("parent", existingStore("parent"));

		verify(storeFacade).update(any(PersistableMerchantStore.class));
	}

	@Test
	public void updateRejectsDifferentParentForNonSuperadmin() {
		when(storeFacade.getByCode(STORE_CODE)).thenReturn(existingStore("parent"));

		try {
			api.update(STORE_CODE, store("unrelated"), request);
			fail("Expected UnauthorizedException");
		} catch (UnauthorizedException expected) {
			verify(storeFacade, never()).update(any(PersistableMerchantStore.class));
		}
	}

	@Test
	public void updateRejectsParentWhenExistingStoreHasNoParent() {
		when(storeFacade.getByCode(STORE_CODE)).thenReturn(existingStore(null));

		try {
			api.update(STORE_CODE, store("parent"), request);
			fail("Expected UnauthorizedException");
		} catch (UnauthorizedException expected) {
			verify(storeFacade, never()).update(any(PersistableMerchantStore.class));
		}
	}

	@Test
	public void updateChecksPermissionBeforeLoadingStore() {
		when(userFacade.authorizedStore(USER_NAME, STORE_CODE)).thenReturn(false);

		try {
			api.update(STORE_CODE, store(null), request);
			fail("Expected UnauthorizedException");
		} catch (UnauthorizedException expected) {
			verify(storeFacade, never()).getByCode(STORE_CODE);
			verify(storeFacade, never()).update(any(PersistableMerchantStore.class));
		}
	}

	@Test
	public void createAllowsBlankRetailerStore() {
		api.create(store(" "));

		verify(storeFacade).create(any(PersistableMerchantStore.class));
	}

	@Test
	public void createAllowsSuperadminToSetArbitraryParent() {
		when(userFacade.userInRoles(USER_NAME, Arrays.asList(Constants.GROUP_SUPERADMIN))).thenReturn(true);

		api.create(store("unrelated"));

		verify(storeFacade).create(any(PersistableMerchantStore.class));
	}

	@Test
	public void createAllowsNonSuperadminAuthorizedForParent() {
		when(userFacade.authorizedStore(USER_NAME, "parent")).thenReturn(true);

		api.create(store("parent"));

		verify(storeFacade).create(any(PersistableMerchantStore.class));
	}

	@Test
	public void createRejectsUnauthorizedParent() {
		when(userFacade.authorizedStore(USER_NAME, "unrelated")).thenReturn(false);

		try {
			api.create(store("unrelated"));
			fail("Expected UnauthorizedException");
		} catch (UnauthorizedException expected) {
			verify(storeFacade, never()).create(any(PersistableMerchantStore.class));
		}
	}

	private void update(String retailerStore, MerchantStore existing) {
		when(storeFacade.getByCode(STORE_CODE)).thenReturn(existingStore(existing == null ? null : retailerStore));
		api.update(STORE_CODE, store(retailerStore), request);
	}

	private static PersistableMerchantStore store(String retailerStore) {
		PersistableMerchantStore store = new PersistableMerchantStore();
		store.setRetailerStore(retailerStore);
		return store;
	}

	private static MerchantStore existingStore(String parentCode) {
		MerchantStore existing = new MerchantStore();
		if (parentCode != null) {
			MerchantStore parent = new MerchantStore();
			parent.setCode(parentCode);
			existing.setParent(parent);
		}
		return existing;
	}
}
