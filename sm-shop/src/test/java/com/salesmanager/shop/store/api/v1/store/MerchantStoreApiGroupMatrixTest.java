package com.salesmanager.shop.store.api.v1.store;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertThrows;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.reset;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.security.Principal;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import javax.servlet.http.HttpServletRequest;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnitRunner;
import org.springframework.web.multipart.MultipartFile;

import com.salesmanager.shop.constants.Constants;
import com.salesmanager.shop.model.store.PersistableBrand;
import com.salesmanager.shop.model.store.PersistableMerchantStore;
import com.salesmanager.shop.store.api.exception.RestApiException;
import com.salesmanager.shop.store.api.exception.UnauthorizedException;
import com.salesmanager.shop.store.controller.store.facade.StoreFacade;
import com.salesmanager.shop.store.controller.user.facade.UserFacade;

@RunWith(MockitoJUnitRunner.class)
public class MerchantStoreApiGroupMatrixTest {

	private static final String STORE = "ACME";
	private static final String USER = "admin@example.com";
	private static final Set<String> STORE_ADMIN_GROUPS = new HashSet<>(Arrays.asList("SUPERADMIN", "ADMIN",
			"ADMIN_RETAIL", "ADMIN_RETAILER", "ADMIN_STORE"));
	private static final Set<String> STORE_DELETE_GROUPS = new HashSet<>(
			Arrays.asList("SUPERADMIN", "ADMIN_RETAIL", "ADMIN_RETAILER"));

	@Mock
	private StoreFacade storeFacade;

	@Mock
	private UserFacade userFacade;

	@InjectMocks
	private MerchantStoreApi api;

	@Test
	public void routesPassTheExpectedGroupLists() throws Exception {
		for (Route route : Route.values()) {
			reset(userFacade, storeFacade);
			when(userFacade.authorizedStore(USER, STORE)).thenReturn(true);

			call(route, requestAs(USER));

			ArgumentCaptor<List<String>> groups = ArgumentCaptor.forClass(List.class);
			verify(userFacade).authorizedGroup(eq(USER), groups.capture());
			assertEquals(route == Route.DELETE ? STORE_DELETE_GROUPS : STORE_ADMIN_GROUPS,
					new HashSet<>(groups.getValue()));
		}
	}

	@Test
	public void groupDenialPreventsStoreAuthorizationAndFacadeCallsOnEveryRoute() {
		for (Route route : Route.values()) {
			reset(userFacade, storeFacade);
			doThrow(new UnauthorizedException("denied")).when(userFacade).authorizedGroup(anyString(), anyList());

			assertThrows(UnauthorizedException.class, () -> call(route, requestAs(USER)));

			verify(userFacade, never()).authorizedStore(anyString(), anyString());
			verifyNoInteractions(storeFacade);
		}
	}

	@Test
	public void storeDenialPreventsFacadeCallsOnEveryRoute() {
		for (Route route : Route.values()) {
			reset(userFacade, storeFacade);
			doNothing().when(userFacade).authorizedGroup(anyString(), anyList());
			when(userFacade.authorizedStore(USER, STORE)).thenReturn(false);

			assertThrows(UnauthorizedException.class, () -> call(route, requestAs(USER)));

			verifyNoInteractions(storeFacade);
		}
	}

	@Test
	public void missingPrincipalDoesNotCallFacadesOnAnyRoute() {
		for (Route route : Route.values()) {
			reset(userFacade, storeFacade);
			HttpServletRequest request = mock(HttpServletRequest.class);

			assertThrows(UnauthorizedException.class, () -> call(route, request));

			verifyNoInteractions(userFacade, storeFacade);
		}
	}

	@Test
	public void legacyRetailGroupCanUpdateAndDeleteButContentGroupCannotUseAnyRoute() throws Exception {
		Map<String, String> userGroups = new HashMap<>();
		userGroups.put("retail", Constants.GROUP_ADMIN_RETAIL);
		userGroups.put("content", "ADMIN_CONTENT");
		doAnswer(invocation -> {
			String user = invocation.getArgument(0);
			List<String> groups = invocation.getArgument(1);
			if (!groups.contains(userGroups.get(user))) {
				throw new UnauthorizedException("denied");
			}
			return null;
		}).when(userFacade).authorizedGroup(anyString(), anyList());
		when(userFacade.authorizedStore(anyString(), eq(STORE))).thenReturn(true);

		api.update(STORE, new PersistableMerchantStore(), requestAs("retail"));
		api.delete(STORE, requestAs("retail"));
		verify(storeFacade).update(org.mockito.ArgumentMatchers.any(PersistableMerchantStore.class));
		verify(storeFacade).delete(STORE);

		for (Route route : Route.values()) {
			assertThrows(UnauthorizedException.class, () -> call(route, requestAs("content")));
		}
	}

	@Test
	public void updateUsesPathCodeInsteadOfBodyCode() throws Exception {
		when(userFacade.authorizedStore(USER, STORE)).thenReturn(true);
		PersistableMerchantStore body = new PersistableMerchantStore();
		body.setCode("BODY");

		api.update(STORE, body, requestAs(USER));

		ArgumentCaptor<PersistableMerchantStore> store = ArgumentCaptor.forClass(PersistableMerchantStore.class);
		verify(storeFacade).update(store.capture());
		assertEquals(STORE, store.getValue().getCode());
	}

	@Test
	public void emptyLogoIsRejectedBeforeCallingStoreFacade() {
		when(userFacade.authorizedStore(USER, STORE)).thenReturn(true);
		MultipartFile file = mock(MultipartFile.class);
		when(file.isEmpty()).thenReturn(true);

		assertThrows(RestApiException.class, () -> api.addLogo(STORE, file, requestAs(USER)));

		verifyNoInteractions(storeFacade);
	}

	@Test
	public void adminRetailerGroupNameIsStable() {
		assertEquals("ADMIN_RETAILER", Constants.GROUP_ADMIN_RETAILER);
	}

	private void call(Route route, HttpServletRequest request) throws Exception {
		switch (route) {
		case UPDATE:
			api.update(STORE, new PersistableMerchantStore(), request);
			break;
		case DELETE:
			api.delete(STORE, request);
			break;
		case GET_MARKETING:
			api.getStoreMarketing(STORE, request);
			break;
		case SAVE_MARKETING:
			api.saveStoreMarketing(STORE, new PersistableBrand(), request);
			break;
		case ADD_LOGO:
			MultipartFile file = mock(MultipartFile.class);
			when(file.isEmpty()).thenReturn(false);
			when(file.getBytes()).thenReturn(new byte[0]);
			api.addLogo(STORE, file, request);
			break;
		case DELETE_LOGO:
			api.deleteStoreLogo(STORE, request);
			break;
		case CHILDREN:
			api.children(STORE, null, 0, 10, request);
			break;
		default:
			throw new IllegalStateException("Unexpected route " + route);
		}
	}

	private HttpServletRequest requestAs(String user) {
		HttpServletRequest request = mock(HttpServletRequest.class);
		Principal principal = () -> user;
		when(request.getUserPrincipal()).thenReturn(principal);
		return request;
	}

	private enum Route {
		UPDATE, DELETE, GET_MARKETING, SAVE_MARKETING, ADD_LOGO, DELETE_LOGO, CHILDREN
	}
}
