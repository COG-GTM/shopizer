package com.salesmanager.test.shop.integration.security;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNotEquals;
import static org.junit.Assert.assertTrue;
import static org.springframework.http.HttpMethod.GET;
import static org.springframework.http.HttpStatus.BAD_REQUEST;
import static org.springframework.http.HttpStatus.OK;
import static org.springframework.http.HttpStatus.UNAUTHORIZED;

import java.util.Date;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.SpringBootTest.WebEnvironment;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
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

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.SignatureAlgorithm;

@RunWith(SpringRunner.class)
@SpringBootTest(classes = ShopApplication.class, webEnvironment = WebEnvironment.RANDOM_PORT)
public class JWTPrincipalTypeRefreshIntegrationTest extends ServicesTestSupport {

    @Autowired
    private JWTTokenUtil jwtTokenUtil;

    private String customerEmail;
    private String customerToken;

    @Before
    public void setUp() {
        customerEmail = "jwt-refresh-" + System.nanoTime() + "@test.com";
    }

    @Test
    public void adminRefreshPreservesAdminTypeAndCanAccessPrivateResource() {
        String adminToken = adminToken();
        assertEquals("admin", principalType(adminToken));

        ResponseEntity<AuthenticationResponse> refresh = testRestTemplate.exchange("/api/v1/auth/refresh", GET,
                bearer(adminToken), AuthenticationResponse.class);

        assertEquals(OK, refresh.getStatusCode());
        String refreshedToken = refresh.getBody().getToken();
        assertEquals("admin", principalType(refreshedToken));
        assertEquals(OK, testRestTemplate.exchange("/api/v1/private/users/1", GET, bearer(refreshedToken),
                String.class).getStatusCode());
    }

    @Test
    public void customerTokenCannotUseAdminRefresh() {
        registerCustomer();
        String token = customerToken();

        ResponseEntity<String> refresh = testRestTemplate.exchange("/api/v1/auth/refresh", GET, bearer(token),
                String.class);

        assertEquals(BAD_REQUEST, refresh.getStatusCode());
    }

    @Test
    public void customerLoginProducesCustomerTokenThatCanReadProfile() {
        registerCustomer();
        String token = customerToken();

        assertEquals("customer", principalType(token));
        assertEquals(OK, testRestTemplate.exchange("/api/v1/auth/customer/profile", GET, bearer(token),
                String.class).getStatusCode());
    }

    @Test
    public void legacyAdminAndCustomerTokensAreRejected() {
        registerCustomer();
        String legacyAdmin = legacyToken("admin@shopizer.com");
        String legacyCustomer = legacyToken(customerEmail);

        ResponseEntity<String> adminResponse = testRestTemplate.exchange("/api/v1/private/users/1", GET,
                bearer(legacyAdmin), String.class);
        ResponseEntity<String> customerResponse = testRestTemplate.exchange("/api/v1/auth/customer/profile", GET,
                bearer(legacyCustomer), String.class);

        assertEquals(UNAUTHORIZED, customerResponse.getStatusCode());
        assertEquals(UNAUTHORIZED, adminResponse.getStatusCode());
    }

    @Test
    public void missingAdminTokenIsUnauthorized() {
        assertEquals(UNAUTHORIZED, testRestTemplate.getForEntity("/api/v1/private/users/1", String.class)
                .getStatusCode());
    }

    @Test
    public void customerTokenCannotAccessPrivateCustomerList() {
        registerCustomer();

        int status = testRestTemplate.exchange("/api/v1/private/customers", GET, bearer(customerToken()),
                String.class).getStatusCodeValue();
        assertNotEquals(200, status);
    }

    private void registerCustomer() {
        PersistableCustomer customer = new PersistableCustomer();
        customer.setEmailAddress(customerEmail);
        customer.setPassword("clear123");
        customer.setGender(CustomerGender.M.name());
        customer.setLanguage("en");
        customer.setStoreCode(Constants.DEFAULT_STORE);
        Address billing = new Address();
        billing.setFirstName("JWT");
        billing.setLastName("Refresh");
        billing.setCountry("BE");
        customer.setBilling(billing);

        ResponseEntity<String> response = testRestTemplate.postForEntity("/api/v1/customer/register",
                new HttpEntity<>(customer, getHeader()), String.class);
        assertTrue("customer registration status: " + response.getStatusCode(), response.getStatusCode().is2xxSuccessful());
    }

    private String customerToken() {
        ResponseEntity<AuthenticationResponse> login = testRestTemplate.postForEntity("/api/v1/customer/login",
                new HttpEntity<>(new AuthenticationRequest(customerEmail, "clear123")), AuthenticationResponse.class);
        assertEquals(OK, login.getStatusCode());
        assertNotNull(login.getBody());
        return login.getBody().getToken();
    }

    private String adminToken() {
        ResponseEntity<AuthenticationResponse> login = testRestTemplate.postForEntity("/api/v1/private/login",
                new HttpEntity<>(new AuthenticationRequest("admin@shopizer.com", "password")),
                AuthenticationResponse.class);
        assertEquals(OK, login.getStatusCode());
        assertNotNull(login.getBody());
        return login.getBody().getToken();
    }

    private String principalType(String token) {
        return claims(token).get("principal_type", String.class);
    }

    private Claims claims(String token) {
        String secret = (String) ReflectionTestUtils.getField(jwtTokenUtil, "secret");
        return Jwts.parser().setSigningKey(secret).parseClaimsJws(token).getBody();
    }

    private String legacyToken(String username) {
        String secret = (String) ReflectionTestUtils.getField(jwtTokenUtil, "secret");
        long now = System.currentTimeMillis();
        return Jwts.builder().setSubject(username).setAudience("api").setIssuedAt(new Date(now))
                .setExpiration(new Date(now + 3600000L)).signWith(SignatureAlgorithm.HS512, secret).compact();
    }

    private HttpEntity<String> bearer(String token) {
        HttpHeaders requestHeaders = headers();
        requestHeaders.setBearerAuth(token);
        return new HttpEntity<>(requestHeaders);
    }

    private HttpHeaders headers() {
        HttpHeaders requestHeaders = new HttpHeaders();
        requestHeaders.setContentType(MediaType.APPLICATION_JSON);
        return requestHeaders;
    }
}
