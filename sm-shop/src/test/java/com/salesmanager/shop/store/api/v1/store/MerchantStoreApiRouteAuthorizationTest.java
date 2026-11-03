package com.salesmanager.shop.store.api.v1.store;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.security.Principal;
import java.util.Arrays;

import javax.servlet.http.HttpServletRequest;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnitRunner;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.util.ReflectionTestUtils;

import com.salesmanager.shop.model.store.PersistableBrand;
import com.salesmanager.shop.model.store.PersistableMerchantStore;
import com.salesmanager.shop.store.api.exception.UnauthorizedException;
import com.salesmanager.shop.store.controller.store.facade.StoreFacade;
import com.salesmanager.shop.store.controller.user.facade.UserFacade;
import com.salesmanager.shop.utils.AuthorizationUtils;

@RunWith(MockitoJUnitRunner.class)
public class MerchantStoreApiRouteAuthorizationTest {

	private static final String STORE = "ACME";
	private static final String USER = "bob@acme.com";

	@Mock
	private UserFacade userFacade;

	@Mock
	private StoreFacade storeFacade;

	@Mock
	private HttpServletRequest request;

	private MerchantStoreApi api;

	@Before
	public void setUp() {
		AuthorizationUtils authorizationUtils = new AuthorizationUtils();
		ReflectionTestUtils.setField(authorizationUtils, "userFacade", userFacade);

		api = new MerchantStoreApi();
		ReflectionTestUtils.setField(api, "userFacade", userFacade);
		ReflectionTestUtils.setField(api, "storeFacade", storeFacade);
		ReflectionTestUtils.setField(api, "authorizationUtils", authorizationUtils);

		Principal principal = mock(Principal.class);
		when(principal.getName()).thenReturn(USER);
		when(request.getUserPrincipal()).thenReturn(principal);
	}

	private void failGroupCheck() {
		doThrow(new UnauthorizedException("User " + USER + " not authorized")).when(userFacade)
				.authorizedGroup(eq(USER), anyList());
	}

	@Test(expected = UnauthorizedException.class)
	public void getMarketingRejectsUserFailingGroupCheck() {
		failGroupCheck();
		try {
			api.getStoreMarketing(STORE, request);
		} finally {
			verifyNoInteractions(storeFacade);
		}
	}

	@Test(expected = UnauthorizedException.class)
	public void saveMarketingRejectsUserFailingGroupCheck() {
		failGroupCheck();
		try {
			api.saveStoreMarketing(STORE, new PersistableBrand(), request);
		} finally {
			verifyNoInteractions(storeFacade);
		}
	}

	@Test(expected = UnauthorizedException.class)
	public void addLogoRejectsUserFailingGroupCheck() {
		failGroupCheck();
		try {
			api.addLogo(STORE, new MockMultipartFile("file", "logo.png", "image/png", new byte[] { 1 }), request);
		} finally {
			verifyNoInteractions(storeFacade);
		}
	}

	@Test(expected = UnauthorizedException.class)
	public void childrenRejectsUserFailingGroupCheck() {
		failGroupCheck();
		try {
			api.children(STORE, null, 0, 10, request);
		} finally {
			verifyNoInteractions(storeFacade);
		}
	}

	@Test
	public void updateAllowsAttachedAdminRetailer() {
		when(userFacade.userInRoles(eq(USER), anyList())).thenReturn(false);
		when(userFacade.authorizedStore(USER, STORE)).thenReturn(true);

		api.update(STORE, new PersistableMerchantStore(), request);

		verify(userFacade).authorizedStore(USER, STORE);
		verify(storeFacade).update(any());
	}
}
