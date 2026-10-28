package com.salesmanager.shop.store.api.v1.store;

import static org.junit.Assert.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.security.Principal;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import javax.servlet.http.HttpServletRequest;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnitRunner;
import org.springframework.web.multipart.MultipartFile;

import com.salesmanager.shop.model.store.PersistableBrand;
import com.salesmanager.shop.model.store.PersistableMerchantStore;
import com.salesmanager.shop.store.api.exception.UnauthorizedException;
import com.salesmanager.shop.store.controller.store.facade.StoreFacade;
import com.salesmanager.shop.store.controller.user.facade.UserFacade;

@RunWith(MockitoJUnitRunner.Silent.class)
public class MerchantStoreApiAuthorizationTest {

	private static final String STORE = "ACME";

	@Mock
	private StoreFacade storeFacade;

	@Mock
	private UserFacade userFacade;

	@InjectMocks
	private MerchantStoreApi api;

	private final Map<String, String> userGroups = new HashMap<>();

	@Before
	public void setUp() {
		userGroups.put("superadmin", "SUPERADMIN");
		userGroups.put("admin", "ADMIN");
		userGroups.put("retailer", "ADMIN_RETAILER");
		userGroups.put("storeadmin", "ADMIN_STORE");
		userGroups.put("shipping", "SHIPPING");
		userGroups.put("orders", "ADMIN_ORDER");
		userGroups.put("catalogue", "ADMIN_CATALOGUE");

		// mirrors UserFacadeImpl.authorizedGroup: throws unless one of the user's groups is allowed
		doAnswer(inv -> {
			String user = inv.getArgument(0);
			List<String> allowed = inv.getArgument(1);
			if (!allowed.contains(userGroups.get(user))) {
				throw new UnauthorizedException("User " + user + " not authorized");
			}
			return null;
		}).when(userFacade).authorizedGroup(anyString(), anyList());

		// every user in this test is attached to the store
		when(userFacade.authorizedStore(anyString(), anyString())).thenReturn(true);
	}

	private HttpServletRequest requestAs(String user) {
		HttpServletRequest request = mock(HttpServletRequest.class);
		Principal principal = () -> user;
		when(request.getUserPrincipal()).thenReturn(principal);
		return request;
	}

	@Test
	public void nonAdminStoreMembersCannotMutateStore() throws Exception {
		MultipartFile logo = mock(MultipartFile.class);
		for (String user : Arrays.asList("shipping", "orders", "catalogue")) {
			HttpServletRequest request = requestAs(user);
			assertThrows(UnauthorizedException.class,
					() -> api.update(STORE, new PersistableMerchantStore(), request));
			assertThrows(UnauthorizedException.class, () -> api.delete(STORE, request));
			assertThrows(UnauthorizedException.class,
					() -> api.saveStoreMarketing(STORE, new PersistableBrand(), request));
			assertThrows(UnauthorizedException.class, () -> api.getStoreMarketing(STORE, request));
			assertThrows(UnauthorizedException.class, () -> api.addLogo(STORE, logo, request));
			assertThrows(UnauthorizedException.class, () -> api.deleteStoreLogo(STORE, request));
			assertThrows(UnauthorizedException.class, () -> api.children(STORE, null, 0, 10, request));
		}
		verify(storeFacade, never()).update(any());
		verify(storeFacade, never()).delete(anyString());
		verify(storeFacade, never()).createBrand(anyString(), any());
		verify(storeFacade, never()).getBrand(anyString());
		verify(storeFacade, never()).addStoreLogo(anyString(), any());
		verify(storeFacade, never()).deleteLogo(anyString());
		verify(storeFacade, never()).getChildStores(any(), anyString(), anyInt(), anyInt());
	}

	@Test
	public void storeAdminsCanUpdateStore() {
		for (String user : Arrays.asList("superadmin", "admin", "retailer", "storeadmin")) {
			api.update(STORE, new PersistableMerchantStore(), requestAs(user));
			api.deleteStoreLogo(STORE, requestAs(user));
		}
		verify(storeFacade, times(4)).update(any());
		verify(storeFacade, times(4)).deleteLogo(STORE);
	}

	@Test
	public void onlySuperadminOrRetailerCanDeleteStore() {
		assertThrows(UnauthorizedException.class, () -> api.delete(STORE, requestAs("admin")));
		assertThrows(UnauthorizedException.class, () -> api.delete(STORE, requestAs("storeadmin")));
		verify(storeFacade, never()).delete(anyString());

		api.delete(STORE, requestAs("superadmin"));
		api.delete(STORE, requestAs("retailer"));
		verify(storeFacade, times(2)).delete(STORE);
	}

	@Test
	public void adminNotAttachedToStoreIsRejected() {
		when(userFacade.authorizedStore("retailer", "OTHER")).thenReturn(false);
		assertThrows(UnauthorizedException.class, () -> api.delete("OTHER", requestAs("retailer")));
		assertThrows(UnauthorizedException.class,
				() -> api.update("OTHER", new PersistableMerchantStore(), requestAs("retailer")));
		verify(storeFacade, never()).delete(anyString());
		verify(storeFacade, never()).update(any());
	}

	@Test
	public void missingPrincipalIsRejected() {
		HttpServletRequest request = mock(HttpServletRequest.class);
		assertThrows(UnauthorizedException.class, () -> api.delete(STORE, request));
		verify(storeFacade, never()).delete(anyString());
	}
}
