package com.salesmanager.test.shop.common;

import static org.junit.Assert.*;
import java.util.UUID;
import org.springframework.http.*;
import com.salesmanager.shop.model.security.PersistableGroup;
import com.salesmanager.shop.model.user.PersistableUser;
import com.salesmanager.shop.model.user.ReadableUser;
import com.salesmanager.shop.store.security.AuthenticationRequest;

public class UserJourneySupport extends ServicesTestSupport {
    protected static final String PASSWORD = "Password1";

    protected PersistableUser profile(String username, String role) {
        PersistableUser body = new PersistableUser();
        body.setUserName(username);
        body.setEmailAddress(username);
        body.setFirstName("Journey");
        body.setLastName("User");
        body.setDefaultLanguage("en");
        body.setActive(true);
        PersistableGroup group = new PersistableGroup(); group.setName(role);
        body.getGroups().add(group);
        return body;
    }

    protected ReadableUser createUser(String role) {
        PersistableUser body = profile("journey-" + UUID.randomUUID() + "@test.com", role);
        body.setPassword(PASSWORD); body.setRepeatPassword(PASSWORD);
        ResponseEntity<ReadableUser> response = testRestTemplate.exchange(
            "/api/v1/private/user/?store=DEFAULT", HttpMethod.POST,
            new HttpEntity<>(body, getHeader()), ReadableUser.class);
        assertEquals(response.toString(), HttpStatus.OK, response.getStatusCode());
        assertNotNull(response.getBody().getId());
        return response.getBody();
    }

    protected ResponseEntity<String> update(Long id, PersistableUser body, HttpHeaders headers) {
        return testRestTemplate.exchange("/api/v1/private/user/" + id + "?store=DEFAULT",
            HttpMethod.PUT, new HttpEntity<>(body, headers), String.class);
    }

    protected ReadableUser readUser(Long id) {
        ResponseEntity<ReadableUser> response = testRestTemplate.exchange(
            "/api/v1/private/users/" + id + "?store=DEFAULT", HttpMethod.GET,
            new HttpEntity<>(getHeader()), ReadableUser.class);
        assertEquals(HttpStatus.OK, response.getStatusCode());
        return response.getBody();
    }

    protected HttpStatus login(String username, String password) {
        return testRestTemplate.postForEntity("/api/v1/private/login",
            new AuthenticationRequest(username, password), String.class).getStatusCode();
    }
}
