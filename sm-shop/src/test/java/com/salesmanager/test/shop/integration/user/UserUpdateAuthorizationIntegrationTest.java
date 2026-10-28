package com.salesmanager.test.shop.integration.user;

import static org.junit.Assert.*;
import javax.inject.Inject;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.springframework.test.context.junit4.SpringRunner;
import org.springframework.http.*;
import com.salesmanager.shop.model.user.*;
import com.salesmanager.test.shop.common.UserJourneySupport;
import com.salesmanager.core.business.services.user.GroupService;
import com.salesmanager.core.model.user.Group;
import com.salesmanager.core.model.user.GroupType;

@RunWith(SpringRunner.class)
public class UserUpdateAuthorizationIntegrationTest extends UserJourneySupport {
    @Inject private GroupService groups;
    @Test public void noTokenRejected() {
        assertEquals(HttpStatus.UNAUTHORIZED, update(1L, profile("admin@shopizer.com", "SUPERADMIN"), new HttpHeaders()).getStatusCode());
    }
    @Test public void unknownIdReturnsNotFound() {
        ResponseEntity<String> response = update(Long.MAX_VALUE, profile("missing@test.com", "ADMIN_ORDER"), getHeader());
        System.out.println("UNKNOWN_USER_STATUS=" + response.getStatusCode());
        assertEquals(HttpStatus.NOT_FOUND, response.getStatusCode());
    }
    @Test public void retailAdminUpdatesSameStoreUser() throws Exception {
        if (groups.findByName("ADMIN_RETAIL") == null) {
            Group retail = new Group("ADMIN_RETAIL"); retail.setGroupType(GroupType.ADMIN);
            groups.create(retail);
        }
        ReadableUser caller = createUser("ADMIN_RETAIL"), target = createUser("ADMIN_ORDER");
        PersistableUser body = profile(target.getUserName(), "ADMIN_ORDER"); body.setFirstName("RetailUpdated");
        assertEquals(HttpStatus.OK, update(target.getId(), body, getHeader(caller.getUserName(), PASSWORD)).getStatusCode());
        assertEquals("RetailUpdated", readUser(target.getId()).getFirstName());
    }
    @Test public void superadminUpdateLeavesPasswordUnchanged() {
        ReadableUser target = createUser("ADMIN_ORDER");
        PersistableUser body = profile(target.getUserName(), "ADMIN_ORDER");
        body.setPassword("Password2"); body.setRepeatPassword("Password2");
        assertEquals(HttpStatus.OK, update(target.getId(), body, getHeader()).getStatusCode());
        assertEquals(HttpStatus.OK, login(target.getUserName(), PASSWORD));
        assertEquals(HttpStatus.UNAUTHORIZED, login(target.getUserName(), "Password2"));
    }
    @Test public void selfPasswordPatchStillWorks() {
        ReadableUser caller = createUser("ADMIN_ORDER");
        UserPassword body = new UserPassword(); body.setPassword(PASSWORD); body.setChangePassword("Password2");
        ResponseEntity<String> response = testRestTemplate.exchange("/api/v1/private/user/" + caller.getId() + "/password", HttpMethod.PATCH,
            new HttpEntity<>(body, getHeader(caller.getUserName(), PASSWORD)), String.class);
        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertEquals(HttpStatus.OK, login(caller.getUserName(), "Password2"));
        assertEquals(HttpStatus.UNAUTHORIZED, login(caller.getUserName(), PASSWORD));
    }
}
