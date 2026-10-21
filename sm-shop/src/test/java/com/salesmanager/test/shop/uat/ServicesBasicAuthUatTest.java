package com.salesmanager.test.shop.uat;

import static org.junit.Assert.assertEquals;
import static org.springframework.http.HttpMethod.GET;
import static org.springframework.http.HttpMethod.POST;
import static org.springframework.http.HttpStatus.ACCEPTED;
import static org.springframework.http.HttpStatus.FOUND;
import static org.springframework.http.HttpStatus.OK;
import static org.springframework.http.HttpStatus.UNAUTHORIZED;

import java.nio.charset.StandardCharsets;
import java.util.Base64;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.SpringBootTest.WebEnvironment;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.test.context.junit4.SpringRunner;

import com.salesmanager.shop.application.ShopApplication;
import com.salesmanager.test.shop.common.ServicesTestSupport;

@SpringBootTest(classes = ShopApplication.class, webEnvironment = WebEnvironment.RANDOM_PORT)
@RunWith(SpringRunner.class)
public class ServicesBasicAuthUatTest extends ServicesTestSupport {

    @Test
    public void servicesEndpointsEnforceBasicAuthentication() {
        HttpHeaders validHeaders = basicAuth("admin@shopizer.com", "password");
        HttpHeaders invalidHeaders = basicAuth("admin@shopizer.com", "incorrect");

        ResponseEntity<String> valid = testRestTemplate.exchange(
                "/services/private/system/optin", POST,
                new HttpEntity<>("{\"code\":\"uat\"}", validHeaders), String.class);
        ResponseEntity<String> invalid = testRestTemplate.exchange(
                "/services/private/system/optin", POST,
                new HttpEntity<>("{\"code\":\"uat\"}", invalidHeaders), String.class);
        ResponseEntity<String> anonymous = testRestTemplate.exchange(
                "/services/private/system/optin", POST,
                new HttpEntity<>("{\"code\":\"uat\"}", new HttpHeaders()), String.class);
        ResponseEntity<String> publicStore = testRestTemplate.exchange(
                "/services/public/DEFAULT", GET, HttpEntity.EMPTY, String.class);

        assertEquals(OK, valid.getStatusCode());
        assertEquals(UNAUTHORIZED, invalid.getStatusCode());
        assertEquals(FOUND, anonymous.getStatusCode());
        assertEquals(ACCEPTED, publicStore.getStatusCode());
    }

    private HttpHeaders basicAuth(String username, String password) {
        String credentials = username + ":" + password;
        HttpHeaders headers = new HttpHeaders();
        headers.set(HttpHeaders.AUTHORIZATION, "Basic " + Base64.getEncoder().encodeToString(
                credentials.getBytes(StandardCharsets.UTF_8)));
        headers.setContentType(MediaType.TEXT_PLAIN);
        return headers;
    }
}
