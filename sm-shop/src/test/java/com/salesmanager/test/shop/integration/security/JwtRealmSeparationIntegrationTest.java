package com.salesmanager.test.shop.integration.security;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;
import static org.springframework.http.HttpMethod.GET;
import static org.springframework.http.HttpStatus.OK;
import static org.springframework.http.HttpStatus.UNAUTHORIZED;

import java.util.Date;
import java.util.HashMap;
import java.util.Map;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.SpringBootTest.WebEnvironment;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseEntity;
import org.springframework.test.context.junit4.SpringRunner;
import org.springframework.test.util.ReflectionTestUtils;

import com.salesmanager.core.business.constants.Constants;
import com.salesmanager.core.model.customer.CustomerGender;
import com.salesmanager.shop.application.ShopApplication;
import com.salesmanager.shop.model.customer.PersistableCustomer;
import com.salesmanager.shop.model.customer.address.Address;
import com.salesmanager.shop.store.security.AuthenticationRequest;
import com.salesmanager.shop.store.security.AuthenticationResponse;
import com.salesmanager.shop.store.security.JWTTokenUtil;
import com.salesmanager.test.shop.common.ServicesTestSupport;

import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.SignatureAlgorithm;

@SpringBootTest(classes = ShopApplication.class, webEnvironment = WebEnvironment.RANDOM_PORT)
@RunWith(SpringRunner.class)
public class JwtRealmSeparationIntegrationTest extends ServicesTestSupport {

    private static final String ADMIN_EMAIL = "admin@shopizer.com";
    private static final String ADMIN_PASSWORD = "password";
    private static final String CUSTOMER_PASSWORD = "clear123";
    private static final String PRIVATE_PROFILE = "/api/v1/private/user/profile";
    private static final String PRIVATE_USERS = "/api/v1/private/users?store=DEFAULT";
    private static final String ADMIN_TOKEN_TYPE = "admin";
    private static final String CUSTOMER_TOKEN_TYPE = "customer";

    @Autowired
    private JWTTokenUtil jwtTokenUtil;

    @Autowired
    private TestRestTemplate restTemplate;

    @Test
    public void adminTokenCanAccessAdminEndpoints() {
        AuthenticationResponse admin = adminLogin();
        HttpHeaders headers = bearer(admin.getToken());

        assertEquals(OK, get(PRIVATE_PROFILE, headers).getStatusCode());
        assertEquals(OK, get(PRIVATE_USERS, headers).getStatusCode());
    }

    @Test
    public void freshCustomerTokenWorksOnlyForCustomerEndpoints() {
        AuthenticationResponse customer = registerOrLoginCustomer(
                uniqueEmail("realm-customer"), CUSTOMER_PASSWORD);
        HttpHeaders headers = bearer(customer.getToken());

        ResponseEntity<String> customerProfile = get("/api/v1/auth/customer/profile", headers);
        assertEquals(OK, customerProfile.getStatusCode());
        assertTrue(customerProfile.getBody().contains(customerEmailFromToken(customer.getToken())));
        assertEquals(UNAUTHORIZED, get(PRIVATE_PROFILE, headers).getStatusCode());
    }

    @Test
    public void customerUsingAdminEmailCannotAccessAdminApisOrAdminRefresh() {
        AuthenticationResponse customer = registerOrLoginCustomer(ADMIN_EMAIL, CUSTOMER_PASSWORD);
        HttpHeaders headers = bearer(customer.getToken());

        assertEquals(UNAUTHORIZED, get(PRIVATE_PROFILE, headers).getStatusCode());
        assertEquals(UNAUTHORIZED, get(PRIVATE_USERS, headers).getStatusCode());
        assertEquals(UNAUTHORIZED, get("/api/v1/auth/refresh", headers).getStatusCode());
    }

    @Test
    public void adminTokenCannotAccessCustomerProfileOrRefresh() {
        HttpHeaders headers = bearer(adminLogin().getToken());

        assertEquals(UNAUTHORIZED, get("/api/v1/auth/customer/profile", headers).getStatusCode());
        assertEquals(UNAUTHORIZED, get("/api/v1/auth/customer/refresh", headers).getStatusCode());
    }

    @Test
    public void customerRefreshPreservesRealmAndIdentity() {
        AuthenticationResponse customer = registerOrLoginCustomer(
                uniqueEmail("refresh-customer"), CUSTOMER_PASSWORD);
        HttpHeaders customerHeaders = bearer(customer.getToken());
        ResponseEntity<AuthenticationResponse> response = restTemplate.exchange(
                "/api/v1/auth/customer/refresh", GET, new HttpEntity<>(customerHeaders),
                AuthenticationResponse.class);

        assertEquals(OK, response.getStatusCode());
        assertNotNull(response.getBody());
        String refreshedToken = response.getBody().getToken();
        HttpHeaders refreshedHeaders = bearer(refreshedToken);

        assertEquals(OK, get("/api/v1/auth/customer/profile", refreshedHeaders).getStatusCode());
        assertEquals(UNAUTHORIZED, get(PRIVATE_PROFILE, refreshedHeaders).getStatusCode());
        assertEquals(CUSTOMER_TOKEN_TYPE, tokenType(refreshedToken));
        assertEquals(userId(customer.getToken()), userId(refreshedToken));
    }

    @Test
    public void adminRefreshPreservesRealmAndIdentity() {
        AuthenticationResponse admin = adminLogin();
        ResponseEntity<AuthenticationResponse> response = restTemplate.exchange(
                "/api/v1/auth/refresh", GET, new HttpEntity<>(bearer(admin.getToken())),
                AuthenticationResponse.class);

        assertEquals(OK, response.getStatusCode());
        assertNotNull(response.getBody());
        String refreshedToken = response.getBody().getToken();

        assertEquals(OK, get(PRIVATE_PROFILE, bearer(refreshedToken)).getStatusCode());
        assertEquals(ADMIN_TOKEN_TYPE, tokenType(refreshedToken));
        assertEquals(admin.getId(), userId(refreshedToken));
    }

    @Test
    public void adminRefreshRejectsMissingAndExpiredTokens() {
        assertEquals(UNAUTHORIZED, get("/api/v1/auth/refresh", new HttpHeaders()).getStatusCode());

        Date issuedAt = new Date(System.currentTimeMillis() - 120_000L);
        String expiredToken = signedToken(ADMIN_TOKEN_TYPE, adminLogin().getId(),
                ADMIN_EMAIL, signingSecret(), issuedAt, new Date(System.currentTimeMillis() - 60_000L));
        assertEquals(UNAUTHORIZED, get("/api/v1/auth/refresh", bearer(expiredToken)).getStatusCode());
    }

    @Test
    public void privateEndpointRejectsLegacyAndMismatchedUserIdTokens() {
        Long adminId = adminLogin().getId();
        Date now = new Date();

        String legacyToken = Jwts.builder()
                .setSubject(ADMIN_EMAIL)
                .setAudience("api")
                .setIssuedAt(now)
                .setExpiration(new Date(now.getTime() + 60_000L))
                .signWith(SignatureAlgorithm.HS512, signingSecret())
                .compact();
        String mismatchedToken = signedToken(ADMIN_TOKEN_TYPE, 999_999L,
                ADMIN_EMAIL, signingSecret(), now, new Date(now.getTime() + 60_000L));
        String matchingToken = signedToken(ADMIN_TOKEN_TYPE, adminId,
                ADMIN_EMAIL, signingSecret(), now, new Date(now.getTime() + 60_000L));

        assertEquals(UNAUTHORIZED, get(PRIVATE_PROFILE, bearer(legacyToken)).getStatusCode());
        assertEquals(UNAUTHORIZED, get(PRIVATE_PROFILE, bearer(mismatchedToken)).getStatusCode());
        assertEquals(OK, get(PRIVATE_PROFILE, bearer(matchingToken)).getStatusCode());
    }

    @Test
    public void privateEndpointDoesNotAcceptMalformedOrWrongSecretTokens() {
        ResponseEntity<String> malformed = get(PRIVATE_PROFILE, bearer("not-a-jwt"));
        ResponseEntity<String> wrongSecret = get(PRIVATE_PROFILE, bearer(signedToken(
                ADMIN_TOKEN_TYPE, 1L, ADMIN_EMAIL, "wrong-secret",
                new Date(), new Date(System.currentTimeMillis() + 60_000L))));

        System.out.println("Malformed token on " + PRIVATE_PROFILE + " returned " + malformed.getStatusCode());
        System.out.println("Wrong-secret token on " + PRIVATE_PROFILE + " returned " + wrongSecret.getStatusCode());
        assertFalse(malformed.getStatusCode().is2xxSuccessful());
        assertFalse(wrongSecret.getStatusCode().is2xxSuccessful());
    }

    private AuthenticationResponse adminLogin() {
        ResponseEntity<AuthenticationResponse> response = restTemplate.postForEntity(
                "/api/v1/private/login",
                new HttpEntity<>(new AuthenticationRequest(ADMIN_EMAIL, ADMIN_PASSWORD)),
                AuthenticationResponse.class);
        assertEquals(OK, response.getStatusCode());
        assertNotNull(response.getBody());
        return response.getBody();
    }

    private AuthenticationResponse registerOrLoginCustomer(String email, String password) {
        ResponseEntity<AuthenticationResponse> login = customerLogin(email, password);
        if (login.getStatusCode().is2xxSuccessful() && login.getBody() != null
                && login.getBody().getToken() != null) {
            return login.getBody();
        }

        PersistableCustomer customer = new PersistableCustomer();
        customer.setEmailAddress(email);
        customer.setPassword(password);
        customer.setGender(CustomerGender.M.name());
        customer.setLanguage("en");
        customer.setStoreCode(Constants.DEFAULT_STORE);
        Address billing = new Address();
        billing.setFirstName("JWT");
        billing.setLastName("Realm Test");
        billing.setCountry("BE");
        customer.setBilling(billing);

        ResponseEntity<AuthenticationResponse> registration = restTemplate.postForEntity(
                "/api/v1/customer/register", new HttpEntity<>(customer), AuthenticationResponse.class);
        if (registration.getStatusCode().is2xxSuccessful() && registration.getBody() != null
                && registration.getBody().getToken() != null) {
            return registration.getBody();
        }

        login = customerLogin(email, password);
        assertEquals(OK, login.getStatusCode());
        assertNotNull(login.getBody());
        assertNotNull(login.getBody().getToken());
        return login.getBody();
    }

    private ResponseEntity<AuthenticationResponse> customerLogin(String email, String password) {
        return restTemplate.postForEntity("/api/v1/customer/login",
                new HttpEntity<>(new AuthenticationRequest(email, password)), AuthenticationResponse.class);
    }

    private String customerEmailFromToken(String token) {
        return Jwts.parser().setSigningKey(signingSecret()).parseClaimsJws(token).getBody().getSubject();
    }

    private String tokenType(String token) {
        return (String) Jwts.parser().setSigningKey(signingSecret()).parseClaimsJws(token)
                .getBody().get("tokenType");
    }

    private Long userId(String token) {
        Object userId = Jwts.parser().setSigningKey(signingSecret()).parseClaimsJws(token)
                .getBody().get("uid");
        return userId instanceof Number ? ((Number) userId).longValue() : null;
    }

    private String signingSecret() {
        return (String) ReflectionTestUtils.getField(jwtTokenUtil, "secret");
    }

    private HttpHeaders bearer(String token) {
        HttpHeaders headers = new HttpHeaders();
        headers.set(HttpHeaders.AUTHORIZATION, "Bearer " + token);
        return headers;
    }

    private ResponseEntity<String> get(String path, HttpHeaders headers) {
        return restTemplate.exchange(path, GET, new HttpEntity<>(headers), String.class);
    }

    private String uniqueEmail(String prefix) {
        return prefix + "-" + System.nanoTime() + "@test.com";
    }

    private String signedToken(String tokenType, Object userId, String subject, String secret,
            Date issuedAt, Date expiration) {
        Map<String, Object> claims = new HashMap<>();
        claims.put("tokenType", tokenType);
        claims.put("uid", userId);
        return Jwts.builder()
                .setClaims(claims)
                .setSubject(subject)
                .setAudience("api")
                .setIssuedAt(issuedAt)
                .setExpiration(expiration)
                .signWith(SignatureAlgorithm.HS512, secret)
                .compact();
    }
}
