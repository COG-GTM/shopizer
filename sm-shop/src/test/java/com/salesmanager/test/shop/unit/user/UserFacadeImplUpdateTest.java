package com.salesmanager.test.shop.unit.user;

import static org.junit.Assert.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;

import org.junit.Before;
import org.junit.Test;
import org.mockito.Mockito;
import org.springframework.test.util.ReflectionTestUtils;

import com.salesmanager.core.business.services.user.UserService;
import com.salesmanager.core.business.services.user.PermissionService;
import com.salesmanager.core.business.services.reference.language.LanguageService;
import com.salesmanager.core.model.merchant.MerchantStore;
import com.salesmanager.core.model.user.Group;
import com.salesmanager.core.model.user.User;
import com.salesmanager.shop.model.user.PersistableUser;
import com.salesmanager.shop.model.security.PersistableGroup;
import com.salesmanager.shop.populator.user.PersistableUserPopulator;
import com.salesmanager.shop.store.api.exception.OperationNotAllowedException;
import com.salesmanager.shop.store.api.exception.ResourceNotFoundException;
import com.salesmanager.shop.store.api.exception.UnauthorizedException;
import com.salesmanager.shop.store.facade.user.UserFacadeImpl;

public class UserFacadeImplUpdateTest {
    private UserFacadeImpl facade;
    private UserService users;
    private MerchantStore store;
    private User caller;
    private User target;
    private PersistableUser body;

    @Before
    public void setUp() throws Exception {
        facade = new UserFacadeImpl();
        users = mock(UserService.class);
        store = new MerchantStore();
        store.setCode("DEFAULT");
        caller = user(1L, "ADMIN");
        target = user(2L, "ADMIN_ORDER");
        body = new PersistableUser();
        body.setUserName(target.getAdminName());
        ReflectionTestUtils.setField(facade, "userService", users);
        ReflectionTestUtils.setField(facade, "languageService", mock(LanguageService.class));
        PermissionService permissions = mock(PermissionService.class);
        when(permissions.getPermissions(anyList())).thenReturn(Collections.emptyList());
        ReflectionTestUtils.setField(facade, "permissionService", permissions);
        PersistableUserPopulator populator = mock(PersistableUserPopulator.class);
        when(populator.populate(any(), any(), any(), any())).thenAnswer(invocation -> {
            User result = invocation.getArgument(1);
            PersistableUser input = invocation.getArgument(0);
            result.setActive(input.isActive());
            result.setGroups(new ArrayList<>(Arrays.asList(group("SUPERADMIN"))));
            if (input.getPassword() != null) result.setAdminPassword("changed-hash");
            return result;
        });
        ReflectionTestUtils.setField(facade, "persistableUserPopulator", populator);
        when(users.getByUserName("caller")).thenAnswer(i -> caller);
        when(users.getById(anyLong())).thenAnswer(i -> target);
        when(users.getById(anyLong(), eq(store))).thenAnswer(i -> target);
    }

    private Group group(String name) {
        Group group = new Group();
        group.setId(1);
        group.setGroupName(name);
        return group;
    }

    private User user(Long id, String role) {
        User user = new User();
        user.setId(id);
        user.setAdminName("user-" + id);
        user.setAdminPassword("original-hash");
        user.setMerchantStore(store);
        user.setGroups(new ArrayList<>(Arrays.asList(group(role))));
        user.setActive(true);
        return user;
    }

    private void rejected(Class<? extends Throwable> type) throws Exception {
        assertThrows(type, () -> facade.update(target.getId(), "caller", store, body));
        verify(users, never()).update(Mockito.any(User.class));
    }

    @Test public void orderAdminCannotUpdateSuperadmin() throws Exception {
        caller = user(1L, "ADMIN_ORDER"); target = user(2L, "SUPERADMIN");
        rejected(UnauthorizedException.class);
    }
    @Test public void adminCannotUpdateSuperadmin() throws Exception {
        target = user(2L, "SUPERADMIN"); rejected(UnauthorizedException.class);
    }
    @Test public void nonAdminCannotUpdateOtherUser() throws Exception {
        caller = user(1L, "ADMIN_ORDER"); rejected(UnauthorizedException.class);
    }
    @Test public void superadminCannotChangePasswordViaUpdate() throws Exception {
        caller = user(1L, "SUPERADMIN"); body.setPassword("NewPassword1!");
        facade.update(2L, "caller", store, body);
        verify(users).update(target);
        assertEquals("original-hash", target.getAdminPassword());
    }
    @Test public void usernameChangeRejected() throws Exception {
        body.setUserName("renamed"); rejected(OperationNotAllowedException.class);
    }
    @Test public void storeChangeRejected() throws Exception {
        body.setStore("OTHER"); rejected(OperationNotAllowedException.class);
    }
    @Test public void adminCannotUpdateOtherStore() throws Exception {
        MerchantStore other = new MerchantStore(); other.setCode("OTHER");
        target.setMerchantStore(other); rejected(ResourceNotFoundException.class);
    }
    @Test public void adminCannotChangeGroupsOrActive() throws Exception {
        PersistableGroup group = new PersistableGroup(); group.setName("SUPERADMIN");
        body.getGroups().add(group); body.setActive(false);
        facade.update(2L, "caller", store, body);
        verify(users).update(target);
        assertTrue(target.isActive());
        assertEquals("ADMIN_ORDER", target.getGroups().get(0).getGroupName());
    }
    @Test public void selfEditCannotEscalatePrivileges() throws Exception {
        caller = target;
        PersistableGroup group = new PersistableGroup(); group.setName("SUPERADMIN");
        body.getGroups().add(group);
        facade.update(2L, "caller", store, body);
        verify(users).update(target);
        assertEquals("ADMIN_ORDER", target.getGroups().get(0).getGroupName());
    }
    @Test public void nullAuthenticatedUserRejected() throws Exception {
        assertThrows(UnauthorizedException.class, () -> facade.update(2L, null, store, body));
        verify(users, never()).update(any(User.class));
    }
    @Test public void unknownIdRejected() throws Exception {
        caller = user(1L, "SUPERADMIN"); target = null;
        assertThrows(ResourceNotFoundException.class, () -> facade.update(999L, "caller", store, body));
        verify(users, never()).update(any(User.class));
    }
}
