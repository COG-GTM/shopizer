package com.salesmanager.test.shop.integration.security;

import static org.junit.Assert.*;

import java.util.Date;
import java.util.UUID;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.ResponseEntity;
import org.springframework.test.context.junit4.SpringRunner;

import com.salesmanager.core.business.constants.Constants;
import com.salesmanager.core.model.customer.CustomerGender;
import com.salesmanager.shop.model.customer.PersistableCustomer;
import com.salesmanager.shop.model.customer.address.Address;
import com.salesmanager.shop.store.security.AuthenticationRequest;
import com.salesmanager.shop.store.security.AuthenticationResponse;
import com.salesmanager.test.shop.common.ServicesTestSupport;

import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.SignatureAlgorithm;

@RunWith(SpringRunner.class)
public class JWTTokenIsolationIntegrationTest extends ServicesTestSupport {
    private static final String ADMIN = "admin@shopizer.com";
    private static final String PRIVATE = "/api/v1/private/customers";
    private static final String PROFILE = "/api/v1/auth/customer/profile";

    @Value("${jwt.secret}")
    private String secret;

    @Value("${authToken.header}")
    private String tokenHeader;

    @Test
    public void customerSharingAdminUsernameCannotAccessPrivateApi() {
        String admin = login("/api/v1/private/login", ADMIN, "password");
        expectStatus("admin private API", get(PRIVATE, admin), 200);
        register(ADMIN);
        String customer = login("/api/v1/customer/login", ADMIN, "clear123");
        try {
            expectDenied("same-username customer private API", get(PRIVATE, customer));
            expectStatus("same-username customer profile", get(PROFILE, customer), 200);
            expectDenied("admin customer profile", get(PROFILE, admin));
        } finally {
            expectStatus("remove same-username customer", testRestTemplate.exchange("/api/v1/auth/customer/",
                    HttpMethod.DELETE, bearer(customer), String.class), 200);
        }
    }

    @Test
    public void customerProfileRequiresCustomerToken() {
        String email = "jwt-" + UUID.randomUUID() + "@test.com";
        register(email);
        String customer = login("/api/v1/customer/login", email, "clear123");
        expectStatus("unique customer profile", get(PROFILE, customer), 200);
        expectDenied("unique customer private API", get(PRIVATE, customer));
        expectDenied("admin unique customer profile",
                get(PROFILE, login("/api/v1/private/login", ADMIN, "password")));
    }

    @Test
    public void refreshEndpointsKeepTokenDomainsSeparate() {
        String email = "jwt-" + UUID.randomUUID() + "@test.com";
        register(email);
        String customer = login("/api/v1/customer/login", email, "clear123");
        String admin = login("/api/v1/private/login", ADMIN, "password");
        ResponseEntity<AuthenticationResponse> adminRefresh = refresh("/api/v1/auth/refresh", admin);
        expectStatus("admin refresh with admin", adminRefresh, 200);
        assertNotNull(adminRefresh.getBody());
        assertNotNull(adminRefresh.getBody().getToken());
        expectStatus("refreshed admin private API", get(PRIVATE, adminRefresh.getBody().getToken()), 200);
        expectStatus("admin refresh with customer", get("/api/v1/auth/refresh", customer), 401);
        ResponseEntity<AuthenticationResponse> customerRefresh = refresh("/api/v1/auth/customer/refresh", customer);
        expectStatus("customer refresh with customer", customerRefresh, 200);
        assertNotNull(customerRefresh.getBody());
        assertNotNull(customerRefresh.getBody().getToken());
        expectStatus("refreshed customer profile", get(PROFILE, customerRefresh.getBody().getToken()), 200);
        expectStatus("customer refresh with admin", get("/api/v1/auth/customer/refresh", admin), 401);
    }

    @Test
    public void privateApiRejectsLegacyTokenWithoutType() {
        Date now = new Date();
        String legacy = Jwts.builder().setSubject(ADMIN).setIssuedAt(now)
                .setExpiration(new Date(now.getTime() + 3600000L))
                .signWith(SignatureAlgorithm.HS512, secret).compact();
        expectDenied("legacy admin private API", get(PRIVATE, legacy));
    }

    private void register(String email) {
        PersistableCustomer customer = new PersistableCustomer();
        customer.setEmailAddress(email);
        customer.setPassword("clear123");
        customer.setGender(CustomerGender.M.name());
        customer.setLanguage("en");
        customer.setStoreCode(Constants.DEFAULT_STORE);
        Address billing = new Address();
        billing.setFirstName("JWT");
        billing.setLastName("Customer");
        billing.setCountry("BE");
        customer.setBilling(billing);
        ResponseEntity<String> response = testRestTemplate.postForEntity(
                "/api/v1/customer/register", new HttpEntity<>(customer), String.class);
        expectStatus("customer registration", response, 200);
    }

    private String login(String path, String username, String password) {
        ResponseEntity<AuthenticationResponse> response = testRestTemplate.postForEntity(path,
                new HttpEntity<>(new AuthenticationRequest(username, password)), AuthenticationResponse.class);
        expectStatus(path, response, 200);
        assertNotNull(response.getBody());
        assertNotNull(response.getBody().getToken());
        return response.getBody().getToken();
    }

    private HttpEntity<Void> bearer(String token) {
        HttpHeaders headers = new HttpHeaders();
        headers.set(tokenHeader, "Bearer " + token);
        return new HttpEntity<>(headers);
    }

    private ResponseEntity<String> get(String path, String token) {
        return testRestTemplate.exchange(path, HttpMethod.GET, bearer(token), String.class);
    }

    private ResponseEntity<AuthenticationResponse> refresh(String path, String token) {
        return testRestTemplate.exchange(path, HttpMethod.GET, bearer(token), AuthenticationResponse.class);
    }

    private void expectStatus(String scenario, ResponseEntity<?> response, int expected) {
        System.out.println("JWT scenario: " + scenario + " -> " + response.getStatusCodeValue());
        assertEquals(scenario + ": " + response.getBody(), expected, response.getStatusCodeValue());
    }

    private void expectDenied(String scenario, ResponseEntity<?> response) {
        int status = response.getStatusCodeValue();
        System.out.println("JWT scenario: " + scenario + " -> " + status);
        assertTrue(scenario + ": " + response.getBody(), status == 401 || status == 403);
    }
}
