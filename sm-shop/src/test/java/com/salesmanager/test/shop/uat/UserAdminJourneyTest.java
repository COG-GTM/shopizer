package com.salesmanager.test.shop.uat;

import static org.junit.Assert.*;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.springframework.test.context.junit4.SpringRunner;
import org.springframework.http.HttpStatus;
import com.salesmanager.shop.model.user.ReadableUser;
import com.salesmanager.test.shop.common.UserJourneySupport;

@RunWith(SpringRunner.class)
public class UserAdminJourneyTest extends UserJourneySupport {
    @Test public void createdUserCannotUpdateSuperadmin() {
        ReadableUser caller = createUser("ADMIN_ORDER");
        assertEquals(HttpStatus.OK, login(caller.getUserName(), PASSWORD));
        assertEquals(HttpStatus.UNAUTHORIZED, update(1L, profile("admin@shopizer.com", "ADMIN_ORDER"),
            getHeader(caller.getUserName(), PASSWORD)).getStatusCode());
        assertEquals(HttpStatus.OK, login("admin@shopizer.com", "password"));
        assertTrue(readUser(1L).getGroups().stream().anyMatch(g -> "SUPERADMIN".equals(g.getName())));
    }
}
