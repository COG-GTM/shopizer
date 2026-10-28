package com.salesmanager.test.shop.unit.user;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.util.Collections;

import com.salesmanager.core.business.exception.ServiceException;
import com.salesmanager.core.business.services.merchant.MerchantStoreService;
import com.salesmanager.core.business.services.reference.language.LanguageService;
import com.salesmanager.core.business.services.user.PermissionService;
import com.salesmanager.core.business.services.user.UserService;
import com.salesmanager.core.model.merchant.MerchantStore;
import com.salesmanager.core.model.user.Group;
import com.salesmanager.core.model.user.User;
import com.salesmanager.shop.store.api.exception.ResourceNotFoundException;
import com.salesmanager.shop.store.api.exception.UnauthorizedException;
import com.salesmanager.shop.store.facade.user.UserFacadeImpl;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnitRunner;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;

@RunWith(MockitoJUnitRunner.Silent.class)
public class UserFacadeAuthorizeStoreEdgeCasesTest {

    private static final String USERNAME = "store-admin@example.com";
    private static final String PRIVATE_PATH = "/api/v1/private/stores";

    @Mock
    private UserService userService;

    @Mock
    private PermissionService permissionService;

    @Mock
    private LanguageService languageService;

    @Mock
    private MerchantStoreService merchantStoreService;

    @InjectMocks
    private UserFacadeImpl userFacade;

    private MerchantStore acme;
    private MerchantStore acmeWest;
    private MerchantStore acmeWest2;
    private MerchantStore other;

    @Before
    public void setUp() {
        acme = store("ACME", null);
        acmeWest = store("ACME-WEST", acme);
        acmeWest2 = store("ACME-WEST-2", acmeWest);
        other = store("OTHER", null);
        SecurityContextHolder.getContext()
                .setAuthentication(new UsernamePasswordAuthenticationToken(USERNAME, "password"));
    }

    @After
    public void clearSecurityContext() {
        SecurityContextHolder.clearContext();
    }

    @Test(expected = ResourceNotFoundException.class)
    public void nullStoreIsNotFound() {
        userFacade.authorizeStore(null, PRIVATE_PATH);
    }

    @Test
    public void nullAndBlankPathsArePublicWithoutLookingUpTheUser() {
        assertTrue(userFacade.authorizeStore(acme, null));
        assertTrue(userFacade.authorizeStore(acme, "  "));
        verifyNoInteractions(userService);
    }

    @Test
    public void grandparentStoreCannotAccessGrandchild() throws Exception {
        when(userService.getByUserName(USERNAME)).thenReturn(user(acme, "ADMIN"));

        assertFalse(userFacade.authorizeStore(acmeWest2, PRIVATE_PATH));
    }

    @Test
    public void ownStoreMatchIsCaseInsensitive() throws Exception {
        when(userService.getByUserName(USERNAME)).thenReturn(user(store("ACME-WEST", null), "ADMIN"));

        assertTrue(userFacade.authorizeStore(store("acme-west", null), PRIVATE_PATH));
    }

    @Test
    public void storeWithoutParentDoesNotAllowDifferentUserStore() throws Exception {
        when(userService.getByUserName(USERNAME)).thenReturn(user(other, "ADMIN"));

        assertFalse(userFacade.authorizeStore(acme, PRIVATE_PATH));
    }

    @Test(expected = UnauthorizedException.class)
    public void missingUserIsUnauthorizedWhenLookupReturnsNull() throws Exception {
        when(userService.getByUserName(USERNAME)).thenReturn(null);

        userFacade.authorizeStore(acme, PRIVATE_PATH);
    }

    @Test(expected = UnauthorizedException.class)
    public void serviceFailureLookingUpUserIsUnauthorized() throws Exception {
        when(userService.getByUserName(USERNAME)).thenThrow(new ServiceException("lookup failed"));

        userFacade.authorizeStore(acme, PRIVATE_PATH);
    }

    @Test
    public void directParentCanAccessChildWithoutReverseParentLookup() throws Exception {
        when(userService.getByUserName(USERNAME)).thenReturn(user(acme, "ADMIN"));

        assertTrue(userFacade.authorizeStore(acmeWest, PRIVATE_PATH));
        verify(merchantStoreService, never()).getParent(anyString());
    }

    @Test
    public void retailAndCatalogueAdminGroupsDoNotBypassParentAuthorization() throws Exception {
        when(userService.getByUserName(USERNAME)).thenReturn(user(acmeWest2, "ADMIN_RETAIL"));
        assertFalse(userFacade.authorizeStore(acmeWest, PRIVATE_PATH));

        when(userService.getByUserName(USERNAME)).thenReturn(user(acmeWest2, "ADMIN_CATALOGUE"));
        assertFalse(userFacade.authorizeStore(acmeWest, PRIVATE_PATH));
    }

    private MerchantStore store(String code, MerchantStore parent) {
        MerchantStore store = new MerchantStore();
        store.setCode(code);
        store.setParent(parent);
        return store;
    }

    private User user(MerchantStore merchantStore, String groupName) {
        User user = new User(USERNAME, "password", USERNAME);
        user.setId(1L);
        user.setMerchantStore(merchantStore);
        Group group = new Group(groupName);
        group.setId(1);
        user.setGroups(Collections.singletonList(group));
        return user;
    }
}
