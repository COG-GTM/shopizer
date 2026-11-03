package com.salesmanager.test.shop.util;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.Arrays;
import java.util.Collections;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnitRunner;
import org.springframework.security.authentication.AnonymousAuthenticationToken;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.AuthorityUtils;
import org.springframework.security.core.context.SecurityContextHolder;

import com.salesmanager.core.business.services.reference.language.LanguageService;
import com.salesmanager.core.business.services.user.PermissionService;
import com.salesmanager.core.business.services.user.UserService;
import com.salesmanager.core.model.merchant.MerchantStore;
import com.salesmanager.core.model.reference.language.Language;
import com.salesmanager.core.model.user.Group;
import com.salesmanager.core.model.user.GroupType;
import com.salesmanager.core.model.user.User;
import com.salesmanager.shop.model.security.PersistableGroup;
import com.salesmanager.shop.model.user.PersistableUser;
import com.salesmanager.shop.populator.user.PersistableUserPopulator;
import com.salesmanager.shop.store.api.exception.UnauthorizedException;
import com.salesmanager.shop.store.controller.security.facade.SecurityFacade;
import com.salesmanager.shop.store.facade.user.UserFacadeImpl;

@RunWith(MockitoJUnitRunner.Silent.class)
public class UserCreationAuthorizationTest {

    @Mock private UserService userService;
    @Mock private LanguageService languageService;
    @Mock private PermissionService permissionService;
    @Mock private PersistableUserPopulator persistableUserPopulator;
    @Mock private SecurityFacade securityFacade;
    @InjectMocks private UserFacadeImpl facade;

    private final MerchantStore ownStore = store("OWN");
    private final PersistableUser request = new PersistableUser();
    private final User caller = new User();
    private final User resolved = new User();

    @Before
    public void setUp() throws Exception {
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken("caller", "unused"));
        request.setUserName("new@example.test");
        request.setPassword("Password1");
        request.setRepeatPassword("Password1");
        caller.setMerchantStore(ownStore);
        caller.setGroups(Arrays.asList(group("ADMIN", GroupType.ADMIN)));
        resolved.setId(10L);
        resolved.setAdminName(request.getUserName());
        resolved.setMerchantStore(ownStore);
        resolved.setDefaultLanguage(new Language("en"));
        resolved.setGroups(Arrays.asList(group("ADMIN_CATALOGUE", GroupType.ADMIN)));
        when(userService.getByUserName("caller")).thenReturn(caller);
        when(securityFacade.matchRawPasswords("Password1", "Password1")).thenReturn(true);
        when(securityFacade.validateUserPassword("Password1")).thenReturn(true);
        when(securityFacade.encodePassword("Password1")).thenReturn("encoded");
        when(persistableUserPopulator.populate(any(), any(), any(), any())).thenReturn(resolved);
        when(userService.getById(10L)).thenReturn(resolved);
        when(permissionService.getPermissions(anyList())).thenReturn(Collections.emptyList());
    }

    @After
    public void clearSecurityContext() {
        SecurityContextHolder.clearContext();
    }

    @Test
    public void missingAuthenticationCannotCallFacadeDirectly() throws Exception {
        SecurityContextHolder.clearContext();
        rejected();
    }

    @Test
    public void anonymousAuthenticationCannotCallFacadeDirectly() throws Exception {
        SecurityContextHolder.getContext().setAuthentication(new AnonymousAuthenticationToken(
                "key", "anonymousUser", AuthorityUtils.createAuthorityList("ROLE_ANONYMOUS")));
        rejected();
    }

    @Test
    public void unknownAuthenticatedUserCannotCreate() throws Exception {
        when(userService.getByUserName("caller")).thenReturn(null);
        rejected();
    }

    @Test
    public void resolvedSuperadminIsRejectedEvenWhenRequestClaimsLowerRole() throws Exception {
        request.setGroups(Arrays.asList(requestGroup("ADMIN_CATALOGUE")));
        resolved.setGroups(Arrays.asList(group("SUPERADMIN", GroupType.ADMIN)));
        rejected();
    }

    @Test
    public void adminCannotAssignRetailerRole() throws Exception {
        resolved.setGroups(Arrays.asList(group("ADMIN_RETAILER", GroupType.ADMIN)));
        rejected();
    }

    @Test
    public void nonAdminGroupTypeIsRejectedEvenIfCallerHasSameGroupName() throws Exception {
        resolved.setGroups(Arrays.asList(group("ADMIN", GroupType.CUSTOMER)));
        rejected();
    }

    @Test
    public void oneForbiddenGroupRejectsEntireMixedAssignment() throws Exception {
        resolved.setGroups(Arrays.asList(group("ADMIN_CATALOGUE", GroupType.ADMIN),
                group("SUPERADMIN", GroupType.ADMIN)));
        rejected();
    }

    @Test
    public void resolvedStoreCannotOverrideCallerStore() throws Exception {
        request.setStore(ownStore.getCode());
        resolved.setMerchantStore(store("OTHER"));
        rejected();
    }

    @Test
    public void grandchildStoreIsNotAnImmediateChild() throws Exception {
        MerchantStore child = store("CHILD");
        child.setParent(ownStore);
        MerchantStore grandchild = store("GRANDCHILD");
        grandchild.setParent(child);
        resolved.setMerchantStore(grandchild);
        rejected();
    }

    @Test
    public void missingResolvedStoreFailsClosed() throws Exception {
        resolved.setMerchantStore(null);
        rejected();
    }

    @Test
    public void callerWithoutGroupsCannotAssignAdminGroups() throws Exception {
        caller.setGroups(null);
        rejected();
    }

    @Test
    public void catalogueAdminCannotAssignSiblingOrderRole() throws Exception {
        caller.setGroups(Arrays.asList(group("ADMIN_CATALOGUE", GroupType.ADMIN)));
        resolved.setGroups(Arrays.asList(group("ADMIN_ORDER", GroupType.ADMIN)));
        rejected();
    }

    @Test
    public void adminCanAssignEachStoreLevelRoleInOwnStore() throws Exception {
        resolved.setGroups(Arrays.asList(group("ADMIN_STORE", GroupType.ADMIN),
                group("ADMIN_CATALOGUE", GroupType.ADMIN), group("ADMIN_ORDER", GroupType.ADMIN),
                group("ADMIN_CONTENT", GroupType.ADMIN)));
        accepted();
    }

    @Test
    public void retailerCanAssignAdminInImmediateChildStore() throws Exception {
        caller.setGroups(Arrays.asList(group("ADMIN_RETAILER", GroupType.ADMIN)));
        MerchantStore child = store("CHILD");
        child.setParent(store(ownStore.getCode()));
        resolved.setMerchantStore(child);
        resolved.setGroups(Arrays.asList(group("ADMIN", GroupType.ADMIN)));
        accepted();
    }

    @Test
    public void superadminCanCreateSuperadminInUnrelatedStore() throws Exception {
        caller.setGroups(Arrays.asList(group("SUPERADMIN", GroupType.ADMIN)));
        resolved.setMerchantStore(store("OTHER"));
        resolved.setGroups(Arrays.asList(group("SUPERADMIN", GroupType.ADMIN)));
        accepted();
    }

    @Test
    public void authorizedGroupsRejectsTrimmedMixedCaseSuperadmin() {
        request.setGroups(Arrays.asList(requestGroup(" \tSuPeRaDmIn  ")));
        assertThrows(UnauthorizedException.class, () -> facade.authorizedGroups("caller", request));
    }

    @Test
    public void authorizedGroupsRejectsUnknownCaller() throws Exception {
        when(userService.getByUserName("caller")).thenReturn(null);
        assertThrows(UnauthorizedException.class, () -> facade.authorizedGroups("caller", request));
    }

    @Test
    public void authorizedGroupsWithNullCallerGroupsStillRejectsSuperadmin() {
        caller.setGroups(null);
        request.setGroups(Arrays.asList(requestGroup("SUPERADMIN")));
        assertThrows(UnauthorizedException.class, () -> facade.authorizedGroups("caller", request));
    }

    private void rejected() throws Exception {
        assertThrows(UnauthorizedException.class, () -> facade.create(request, ownStore));
        verify(userService, never()).saveOrUpdate(any(User.class));
    }

    private void accepted() throws Exception {
        assertEquals(resolved.getId(), facade.create(request, ownStore).getId());
        assertEquals("encoded", resolved.getAdminPassword());
        verify(userService).saveOrUpdate(resolved);
    }

    private static MerchantStore store(String code) {
        MerchantStore store = new MerchantStore();
        store.setCode(code);
        return store;
    }

    private static Group group(String name, GroupType type) {
        Group group = new Group(name);
        group.setId(1);
        group.setGroupType(type);
        return group;
    }

    private static PersistableGroup requestGroup(String name) {
        PersistableGroup group = new PersistableGroup();
        group.setName(name);
        return group;
    }
}
