package com.salesmanager.test.shop.uat;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.is;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.SpringBootTest.WebEnvironment;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.test.context.junit.jupiter.SpringExtension;

import com.salesmanager.shop.application.ShopApplication;
import com.salesmanager.shop.store.security.AuthenticationRequest;
import com.salesmanager.shop.store.security.AuthenticationResponse;
import com.salesmanager.test.shop.common.ServicesTestSupport;

@SpringBootTest(classes = ShopApplication.class, webEnvironment = WebEnvironment.RANDOM_PORT)
@ExtendWith(SpringExtension.class)
public class AdminAuthJourneyUatTest extends ServicesTestSupport {

    @Test
    public void adminAuthJourney() {
        ResponseEntity<AuthenticationResponse> login = testRestTemplate.postForEntity(
                "/api/v1/private/login",
                new HttpEntity<>(new AuthenticationRequest("admin@shopizer.com", "password")),
                AuthenticationResponse.class);
        assertThat(login.getStatusCode(), is(HttpStatus.OK));
        assertThat(login.getBody() != null && login.getBody().getToken() != null
                && !login.getBody().getToken().isEmpty(), is(true));

        HttpHeaders authorizedHeaders = new HttpHeaders();
        authorizedHeaders.setBearerAuth(login.getBody().getToken());
        ResponseEntity<String> authorizedCustomers = testRestTemplate.exchange(
                "/api/v1/private/customers", HttpMethod.GET, new HttpEntity<>(authorizedHeaders), String.class);
        assertThat(authorizedCustomers.getStatusCode(), is(HttpStatus.OK));

        ResponseEntity<String> customersWithoutToken = testRestTemplate.exchange(
                "/api/v1/private/customers", HttpMethod.GET, new HttpEntity<>(new HttpHeaders()), String.class);
        assertThat(customersWithoutToken.getStatusCode(), is(HttpStatus.UNAUTHORIZED));

        HttpHeaders malformedHeaders = new HttpHeaders();
        malformedHeaders.setBearerAuth("not.a.jwt");
        ResponseEntity<String> customersWithMalformedToken = testRestTemplate.exchange(
                "/api/v1/private/customers", HttpMethod.GET, new HttpEntity<>(malformedHeaders), String.class);
        assertThat(customersWithMalformedToken.getStatusCode(), is(HttpStatus.INTERNAL_SERVER_ERROR));

        ResponseEntity<String> wrongPassword = testRestTemplate.postForEntity(
                "/api/v1/private/login",
                new HttpEntity<>(new AuthenticationRequest("admin@shopizer.com", "wrong")),
                String.class);
        assertThat(wrongPassword.getStatusCode(), is(HttpStatus.UNAUTHORIZED));
    }
}
