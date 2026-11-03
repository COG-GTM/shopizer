package com.salesmanager.test.shop.unit.store;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.security.Principal;
import java.util.Collections;
import java.util.List;

import javax.servlet.http.HttpServletRequest;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import com.salesmanager.core.model.merchant.MerchantStore;
import com.salesmanager.shop.model.store.PersistableMerchantStore;
import com.salesmanager.shop.model.user.ReadableUser;
import com.salesmanager.shop.store.api.exception.UnauthorizedException;
import com.salesmanager.shop.store.api.v1.store.MerchantStoreApi;
import com.salesmanager.shop.store.controller.store.facade.StoreFacade;
import com.salesmanager.shop.store.controller.user.facade.UserFacade;

/**
 * Unit tests for the parent store (retailerStore) rules of
 * {@link MerchantStoreApi#create} and {@link MerchantStoreApi#update}.
 */
public class MerchantStoreApiParentChangeTest {

	private static final String USER = "admin@child.com";
	private static final List<String> SUPERADMIN = Collections.singletonList("SUPERADMIN");

	private MerchantStoreApi api;
	private StoreFacade storeFacade;
	private UserFacade userFacade;
	private HttpServletRequest request;

	@BeforeEach
	public void setUp() {
		api = new MerchantStoreApi();
		storeFacade = mock(StoreFacade.class);
		userFacade = mock(UserFacade.class);
		ReflectionTestUtils.setField(api, "storeFacade", storeFacade);
		ReflectionTestUtils.setField(api, "userFacade", userFacade);
		request = mock(HttpServletRequest.class);
		Principal principal = () -> USER;
		when(request.getUserPrincipal()).thenReturn(principal);
		when(userFacade.authenticatedUser()).thenReturn(USER);
		when(userFacade.authorizedStore(USER, "child")).thenReturn(true);
	}

	// ---- update ----

	@Test
	public void updateResendingCurrentParentIsAllowedForStoreAdmin() {
		givenExisting("child", "parent");

		api.update("child", store("child", "parent"), request);

		verify(storeFacade).update(any(PersistableMerchantStore.class));
		verify(userFacade, never()).userInRoles(eq(USER), anyList());
	}

	@Test
	public void updateResendingCurrentParentIgnoresCase() {
		givenExisting("child", "parent");

		api.update("child", store("child", "PARENT"), request);

		verify(storeFacade).update(any(PersistableMerchantStore.class));
	}

	@Test
	public void updateChangingParentIsRejectedForStoreAdmin() {
		givenExisting("child", "parent");
		when(userFacade.userInRoles(USER, SUPERADMIN)).thenReturn(false);

		assertThrows(UnauthorizedException.class,
				() -> api.update("child", store("child", "victim"), request));
		verify(storeFacade, never()).update(any(PersistableMerchantStore.class));
	}

	@Test
	public void updateSettingFirstParentIsRejectedForStoreAdmin() {
		givenExisting("child", null);
		when(userFacade.userInRoles(USER, SUPERADMIN)).thenReturn(false);

		assertThrows(UnauthorizedException.class,
				() -> api.update("child", store("child", "victim"), request));
		verify(storeFacade, never()).update(any(PersistableMerchantStore.class));
	}

	@Test
	public void updateChangingParentIsAllowedForSuperadmin() {
		givenExisting("child", "parent");
		when(userFacade.userInRoles(USER, SUPERADMIN)).thenReturn(true);

		api.update("child", store("child", "other"), request);

		verify(storeFacade).update(any(PersistableMerchantStore.class));
	}

	@Test
	public void updateOnUnauthorizedStoreFailsBeforeParentCheck() {
		when(userFacade.authorizedStore(USER, "victim")).thenReturn(false);

		assertThrows(UnauthorizedException.class,
				() -> api.update("victim", store("victim", "mine"), request));
		verify(storeFacade, never()).get("victim");
		verify(storeFacade, never()).update(any(PersistableMerchantStore.class));
	}

	@Test
	public void updateForcesPathCodeOnPayload() {
		givenExisting("child", "parent");

		PersistableMerchantStore payload = store("somethingelse", "parent");
		api.update("child", payload, request);

		verify(storeFacade).get("child");
		verify(storeFacade).update(payload);
		org.junit.jupiter.api.Assertions.assertEquals("child", payload.getCode());
	}

	// ---- create ----

	@Test
	public void createWithoutParentIsAllowed() {
		api.create(store("newstore", null));

		verify(storeFacade).create(any(PersistableMerchantStore.class));
		verify(userFacade, never()).findByUserName(USER);
	}

	@Test
	public void createWithAnyParentIsAllowedForSuperadmin() {
		when(userFacade.userInRoles(USER, SUPERADMIN)).thenReturn(true);

		api.create(store("newstore", "anyparent"));

		verify(storeFacade).create(any(PersistableMerchantStore.class));
	}

	@Test
	public void createUnderOwnStoreIsAllowedForRetailerAdmin() {
		when(userFacade.userInRoles(USER, SUPERADMIN)).thenReturn(false);
		when(userFacade.findByUserName(USER)).thenReturn(user("retailer"));

		api.create(store("newstore", "RETAILER"));

		verify(storeFacade).create(any(PersistableMerchantStore.class));
	}

	@Test
	public void createUnderOtherStoreIsRejectedForRetailerAdmin() {
		when(userFacade.userInRoles(USER, SUPERADMIN)).thenReturn(false);
		when(userFacade.findByUserName(USER)).thenReturn(user("retailer"));

		assertThrows(UnauthorizedException.class, () -> api.create(store("newstore", "victim")));
		verify(storeFacade, never()).create(any(PersistableMerchantStore.class));
	}

	@Test
	public void createWithParentIsRejectedWhenUserCannotBeResolved() {
		when(userFacade.userInRoles(USER, SUPERADMIN)).thenReturn(false);
		when(userFacade.findByUserName(USER)).thenReturn(null);

		assertThrows(UnauthorizedException.class, () -> api.create(store("newstore", "victim")));
		verify(storeFacade, never()).create(any(PersistableMerchantStore.class));
	}

	@Test
	public void createIsRejectedForUserOutsideAllowedGroups() {
		doThrow(new UnauthorizedException("no")).when(userFacade).authorizedGroup(eq(USER), anyList());

		assertThrows(UnauthorizedException.class, () -> api.create(store("newstore", null)));
		verify(storeFacade, never()).create(any(PersistableMerchantStore.class));
	}

	@Test
	public void createIsRejectedWhenNotAuthenticated() {
		when(userFacade.authenticatedUser()).thenReturn(null);

		assertThrows(UnauthorizedException.class, () -> api.create(store("newstore", null)));
		verify(storeFacade, never()).create(any(PersistableMerchantStore.class));
	}

	private void givenExisting(String code, String parentCode) {
		MerchantStore existing = new MerchantStore();
		existing.setCode(code);
		if (parentCode != null) {
			MerchantStore parent = new MerchantStore();
			parent.setCode(parentCode);
			existing.setParent(parent);
		}
		when(storeFacade.get(code)).thenReturn(existing);
	}

	private PersistableMerchantStore store(String code, String retailerStore) {
		PersistableMerchantStore store = new PersistableMerchantStore();
		store.setCode(code);
		store.setRetailerStore(retailerStore);
		return store;
	}

	private ReadableUser user(String merchant) {
		ReadableUser user = new ReadableUser();
		user.setUserName(USER);
		user.setMerchant(merchant);
		return user;
	}
}
