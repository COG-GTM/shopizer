package com.salesmanager.test.shop.uat;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.springframework.http.HttpMethod.GET;
import static org.springframework.http.HttpStatus.OK;
import static org.springframework.http.HttpStatus.UNAUTHORIZED;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.SpringBootTest.WebEnvironment;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseEntity;
import org.springframework.test.context.junit4.SpringRunner;

import com.salesmanager.shop.application.ShopApplication;
import com.salesmanager.shop.store.security.AuthenticationRequest;
import com.salesmanager.shop.store.security.AuthenticationResponse;
import com.salesmanager.test.shop.common.ServicesTestSupport;

@SpringBootTest(classes = ShopApplication.class, webEnvironment = WebEnvironment.RANDOM_PORT)
@RunWith(SpringRunner.class)
public class AdminJourneyUatTest extends ServicesTestSupport {

    @Test
    public void adminCanReadUsersAndAnonymousRequestsAreRejected() {
        ResponseEntity<AuthenticationResponse> login = testRestTemplate.postForEntity(
                "/api/v1/private/login",
                new HttpEntity<>(new AuthenticationRequest("admin@shopizer.com", "password")),
                AuthenticationResponse.class);
        assertEquals(OK, login.getStatusCode());

        HttpHeaders headers = new HttpHeaders();
        headers.setBearerAuth(login.getBody().getToken());
        assertEquals(OK, testRestTemplate.exchange(
                "/api/v1/private/users?store=DEFAULT", GET, new HttpEntity<>(headers), String.class).getStatusCode());

        ResponseEntity<String> anonymous = testRestTemplate.exchange(
                "/api/v1/private/users?store=DEFAULT", GET, HttpEntity.EMPTY, String.class);
        assertEquals(UNAUTHORIZED, anonymous.getStatusCode());

        HttpHeaders garbageHeaders = new HttpHeaders();
        garbageHeaders.setBearerAuth("garbage");
        ResponseEntity<String> garbage = testRestTemplate.exchange(
                "/api/v1/private/users?store=DEFAULT", GET, new HttpEntity<>(garbageHeaders), String.class);
        assertFalse(garbage.getStatusCode().is2xxSuccessful());
        assertTrue(garbage.getStatusCode().isError());
    }
}
