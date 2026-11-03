package com.salesmanager.test.shop.integration.user;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;

import java.util.Arrays;
import java.util.Collections;
import java.util.UUID;
import java.util.stream.Collectors;

import javax.inject.Inject;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.SpringBootTest.WebEnvironment;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.test.context.junit4.SpringRunner;

import com.salesmanager.core.business.services.user.UserService;
import com.salesmanager.shop.application.ShopApplication;
import com.salesmanager.shop.model.security.PersistableGroup;
import com.salesmanager.shop.model.user.PersistableUser;
import com.salesmanager.shop.model.user.ReadableUser;
import com.salesmanager.test.shop.common.ServicesTestSupport;

@SpringBootTest(classes = ShopApplication.class, webEnvironment = WebEnvironment.RANDOM_PORT)
@RunWith(SpringRunner.class)
public class UserCreationBoundaryIntegrationTest extends ServicesTestSupport {

    @Inject private UserService userService;
    private HttpHeaders adminHeaders;

    @Before
    public void createStoreAdmin() {
        PersistableUser admin = user("ADMIN");
        ResponseEntity<ReadableUser> response = create(admin, getHeader());
        assertEquals(HttpStatus.OK, response.getStatusCode());
        adminHeaders = getHeader(admin.getUserName(), "Password1");
    }

    @Test
    public void whitespaceAndCaseDoNotBypassControllerGroupAuthorization() throws Exception {
        for (String name : Arrays.asList(" SUPERADMIN ", " \tSuPeRaDmIn ")) {
            PersistableUser candidate = user(name);
            assertEquals(HttpStatus.UNAUTHORIZED, create(candidate, adminHeaders).getStatusCode());
            assertNull(userService.getByUserName(candidate.getUserName()));
        }
    }

    @Test
    public void mixedPermittedAndForbiddenGroupsDoNotPartiallyPersistUser() throws Exception {
        PersistableUser candidate = user("ADMIN_CATALOGUE", "ADMIN_RETAILER");
        assertEquals(HttpStatus.UNAUTHORIZED, create(candidate, adminHeaders).getStatusCode());
        assertNull(userService.getByUserName(candidate.getUserName()));
    }

    @Test
    public void rejectedUsernameCanBeUsedForSubsequentAuthorizedCreation() throws Exception {
        PersistableUser candidate = user("ADMIN_RETAILER");
        assertEquals(HttpStatus.UNAUTHORIZED, create(candidate, adminHeaders).getStatusCode());
        assertNull(userService.getByUserName(candidate.getUserName()));
        candidate.getGroups().clear();
        PersistableGroup permitted = new PersistableGroup();
        permitted.setName("ADMIN_ORDER");
        candidate.getGroups().add(permitted);
        ResponseEntity<ReadableUser> accepted = create(candidate, adminHeaders);
        assertEquals(HttpStatus.OK, accepted.getStatusCode());
        assertEquals("DEFAULT", accepted.getBody().getMerchant());
        assertEquals("ADMIN_ORDER", accepted.getBody().getGroups().get(0).getName());
        assertEquals(Collections.singleton("ADMIN_ORDER"), userService.getByUserName(candidate.getUserName())
                .getGroups().stream().map(group -> group.getGroupName()).collect(Collectors.toSet()));
    }

    private ResponseEntity<ReadableUser> create(PersistableUser user, HttpHeaders headers) {
        return testRestTemplate.exchange("/api/v1/private/user/", HttpMethod.POST,
                new HttpEntity<>(user, headers), ReadableUser.class);
    }

    private PersistableUser user(String... names) {
        PersistableUser user = new PersistableUser();
        user.setUserName("boundary-" + UUID.randomUUID() + "@example.test");
        user.setEmailAddress(user.getUserName());
        user.setFirstName("Boundary");
        user.setLastName("Test");
        user.setDefaultLanguage("en");
        user.setPassword("Password1");
        user.setRepeatPassword("Password1");
        user.setActive(true);
        for (String name : names) {
            PersistableGroup group = new PersistableGroup();
            group.setName(name);
            user.getGroups().add(group);
        }
        return user;
    }
}
