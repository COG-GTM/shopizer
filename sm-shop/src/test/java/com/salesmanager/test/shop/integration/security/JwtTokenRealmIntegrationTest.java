package com.salesmanager.test.shop.integration.security;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;
import static org.springframework.http.HttpStatus.OK;
import static org.springframework.http.HttpStatus.UNAUTHORIZED;

import java.util.Date;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.SpringBootTest.WebEnvironment;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.ResponseEntity;
import org.springframework.test.context.junit4.SpringRunner;

import com.salesmanager.core.business.constants.Constants;
import com.salesmanager.core.model.customer.CustomerGender;
import com.salesmanager.shop.application.ShopApplication;
import com.salesmanager.shop.model.customer.PersistableCustomer;
import com.salesmanager.shop.model.customer.address.Address;
import com.salesmanager.shop.model.security.PersistableGroup;
import com.salesmanager.shop.model.user.PersistableUser;
import com.salesmanager.shop.model.user.ReadableUser;
import com.salesmanager.shop.store.security.AuthenticationRequest;
import com.salesmanager.shop.store.security.AuthenticationResponse;
import com.salesmanager.shop.store.security.JWTTokenUtil;
import com.salesmanager.test.shop.common.ServicesTestSupport;

import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.SignatureAlgorithm;

@SpringBootTest(classes = ShopApplication.class, webEnvironment = WebEnvironment.RANDOM_PORT)
@RunWith(SpringRunner.class)
public class JwtTokenRealmIntegrationTest extends ServicesTestSupport {

  @Autowired
  private JWTTokenUtil jwtTokenUtil;

  @Value("${jwt.secret}")
  private String jwtSecret;

  @Test
  public void adminAndCustomerLoginsIssueDifferentTokenTypes() {
    String adminToken = adminLogin("admin@shopizer.com", "password");
    String email = uniqueEmail();
    String customerToken = registerAndLogin(email, "clear123");

    assertEquals(JWTTokenUtil.TOKEN_TYPE_ADMIN, jwtTokenUtil.getTokenTypeFromToken(adminToken));
    assertEquals(JWTTokenUtil.TOKEN_TYPE_CUSTOMER, jwtTokenUtil.getTokenTypeFromToken(customerToken));
  }

  @Test
  public void privateAndCustomerEndpointsAcceptOnlyTheirRealm() {
    String adminToken = adminLogin("admin@shopizer.com", "password");
    String email = uniqueEmail();
    String customerToken = registerAndLogin(email, "clear123");

    assertEquals(OK, testRestTemplate.exchange("/api/v1/private/customers", HttpMethod.GET,
        bearer(adminToken), String.class).getStatusCode());
    assertEquals(UNAUTHORIZED,
        testRestTemplate.exchange("/api/v1/private/customers", HttpMethod.GET,
            bearer(customerToken), String.class).getStatusCode());
    assertEquals(UNAUTHORIZED,
        testRestTemplate.exchange("/api/v1/auth/customer/profile", HttpMethod.GET,
            bearer(adminToken), String.class).getStatusCode());
    ResponseEntity<String> profile = testRestTemplate.exchange("/api/v1/auth/customer/profile",
        HttpMethod.GET, bearer(customerToken), String.class);
    assertEquals(OK, profile.getStatusCode());
    assertTrue(profile.getBody().contains(email));
  }

  @Test
  public void sharedLoginNameKeepsAdminAndCustomerTokensInSeparateRealms() {
    String email = uniqueEmail();
    String customerToken = registerAndLogin(email, "clear123");
    PersistableUser admin = new PersistableUser();
    admin.setDefaultLanguage("en");
    admin.setEmailAddress(email);
    admin.setUserName(email);
    admin.setFirstName("Shared");
    admin.setLastName("Principal");
    admin.setPassword("Password1");
    admin.setRepeatPassword("Password1");
    PersistableGroup group = new PersistableGroup();
    group.setName("ADMIN");
    admin.getGroups().add(group);
    ResponseEntity<ReadableUser> created = testRestTemplate.exchange("/api/v1/private/user/",
        HttpMethod.POST,
        new HttpEntity<>(admin, bearerHeaders(adminLogin("admin@shopizer.com", "password"))),
        ReadableUser.class);
    assertEquals(OK, created.getStatusCode());
    assertNotNull(created.getBody());

    String adminToken = adminLogin(email, "Password1");
    assertEquals(JWTTokenUtil.TOKEN_TYPE_ADMIN, jwtTokenUtil.getTokenTypeFromToken(adminToken));
    assertEquals(UNAUTHORIZED, testRestTemplate.exchange("/api/v1/auth/customer/profile",
        HttpMethod.GET, bearer(adminToken), String.class).getStatusCode());
    assertEquals(UNAUTHORIZED,
        testRestTemplate.exchange("/api/v1/private/users/" + created.getBody().getId(),
            HttpMethod.GET, bearer(customerToken), String.class).getStatusCode());
    assertEquals(OK,
        testRestTemplate.exchange("/api/v1/private/users/" + created.getBody().getId(),
            HttpMethod.GET, bearer(adminToken), String.class).getStatusCode());
  }

  @Test
  public void adminRefreshRequiresAndPreservesAdminRealm() {
    String adminToken = adminLogin("admin@shopizer.com", "password");
    String customerToken = registerAndLogin(uniqueEmail(), "clear123");

    ResponseEntity<AuthenticationResponse> refreshed = testRestTemplate.exchange("/api/v1/auth/refresh",
        HttpMethod.GET, bearer(adminToken), AuthenticationResponse.class);
    assertEquals(OK, refreshed.getStatusCode());
    assertEquals(JWTTokenUtil.TOKEN_TYPE_ADMIN,
        jwtTokenUtil.getTokenTypeFromToken(refreshed.getBody().getToken()));
    assertEquals(OK, testRestTemplate.exchange("/api/v1/private/customers", HttpMethod.GET,
        bearer(refreshed.getBody().getToken()), String.class).getStatusCode());
    assertEquals(UNAUTHORIZED, testRestTemplate.exchange("/api/v1/auth/refresh",
        HttpMethod.GET, bearer(customerToken), String.class).getStatusCode());
    assertEquals(UNAUTHORIZED, testRestTemplate.getForEntity("/api/v1/auth/refresh", String.class).getStatusCode());
  }

  @Test
  public void customerRefreshRequiresAndPreservesCustomerRealm() {
    String customerToken = registerAndLogin(uniqueEmail(), "clear123");
    String adminToken = adminLogin("admin@shopizer.com", "password");

    ResponseEntity<AuthenticationResponse> refreshed = testRestTemplate.exchange(
        "/api/v1/auth/customer/refresh", HttpMethod.GET,
        bearer(customerToken), AuthenticationResponse.class);
    assertEquals(OK, refreshed.getStatusCode());
    String refreshedToken = refreshed.getBody().getToken();
    assertEquals(JWTTokenUtil.TOKEN_TYPE_CUSTOMER, jwtTokenUtil.getTokenTypeFromToken(refreshedToken));
    assertEquals(OK, testRestTemplate.exchange("/api/v1/auth/customer/profile",
        HttpMethod.GET, bearer(refreshedToken), String.class).getStatusCode());
    assertEquals(UNAUTHORIZED, testRestTemplate.exchange("/api/v1/auth/customer/refresh",
        HttpMethod.GET, bearer(adminToken), String.class).getStatusCode());
  }

  @Test
  public void untypedAdminTokenCannotAccessPrivateApi() {
    Date now = new Date();
    String token = Jwts.builder()
        .setSubject("admin@shopizer.com")
        .setAudience("api")
        .setIssuedAt(now)
        .setExpiration(new Date(now.getTime() + 3_600_000L))
        .signWith(SignatureAlgorithm.HS512, jwtSecret)
        .compact();

    assertFalse(jwtTokenUtil.isTokenOfType(token, JWTTokenUtil.TOKEN_TYPE_ADMIN));
    assertEquals(UNAUTHORIZED, testRestTemplate.exchange("/api/v1/private/customers",
        HttpMethod.GET, bearer(token), String.class).getStatusCode());
  }

  @Test
  public void administratorUserNameCannotBeRegisteredAsCustomer() {
    ResponseEntity<String> registration = testRestTemplate.postForEntity("/api/v1/customer/register",
        new HttpEntity<>(customer("ADMIN@SHOPIZER.COM", "clear123")), String.class);
    assertFalse(registration.getStatusCode().is2xxSuccessful());

    ResponseEntity<String> login = testRestTemplate.postForEntity("/api/v1/customer/login",
        new HttpEntity<>(new AuthenticationRequest("ADMIN@SHOPIZER.COM", "clear123")), String.class);
    assertFalse(login.getStatusCode().is2xxSuccessful());
  }

  @Test
  public void administratorNameCannotBeCreatedThroughCustomerAdminApi() {
    ResponseEntity<String> response = testRestTemplate.postForEntity("/api/v1/private/customer",
        new HttpEntity<>(customer("admin@shopizer.com", "clear123"),
            bearerHeaders(adminLogin("admin@shopizer.com", "password"))), String.class);

    assertFalse(response.getStatusCode().is2xxSuccessful());
  }

  private String registerAndLogin(String email, String password) {
    ResponseEntity<AuthenticationResponse> registered = testRestTemplate.postForEntity(
        "/api/v1/customer/register", new HttpEntity<>(customer(email, password)), AuthenticationResponse.class);
    assertEquals(OK, registered.getStatusCode());
    assertNotNull(registered.getBody());
    return customerLogin(email, password);
  }

  private String customerLogin(String email, String password) {
    ResponseEntity<AuthenticationResponse> response = testRestTemplate.postForEntity("/api/v1/customer/login",
        new HttpEntity<>(new AuthenticationRequest(email, password)), AuthenticationResponse.class);
    assertEquals(OK, response.getStatusCode());
    assertNotNull(response.getBody());
    return response.getBody().getToken();
  }

  private String adminLogin(String username, String password) {
    ResponseEntity<AuthenticationResponse> response = testRestTemplate.postForEntity("/api/v1/private/login",
        new HttpEntity<>(new AuthenticationRequest(username, password)), AuthenticationResponse.class);
    assertEquals(OK, response.getStatusCode());
    assertNotNull(response.getBody());
    return response.getBody().getToken();
  }

  private PersistableCustomer customer(String email, String password) {
    PersistableCustomer customer = new PersistableCustomer();
    customer.setEmailAddress(email);
    customer.setPassword(password);
    customer.setGender(CustomerGender.M.name());
    customer.setLanguage("en");
    customer.setStoreCode(Constants.DEFAULT_STORE);
    Address billing = new Address();
    billing.setFirstName("Test");
    billing.setLastName("Customer");
    billing.setCountry("BE");
    customer.setBilling(billing);
    return customer;
  }

  private HttpEntity<Void> bearer(String token) {
    return new HttpEntity<>(bearerHeaders(token));
  }

  private HttpHeaders bearerHeaders(String token) {
    HttpHeaders headers = new HttpHeaders();
    headers.setBearerAuth(token);
    return headers;
  }

  private String uniqueEmail() {
    return "jwt-" + System.nanoTime() + "@test.com";
  }
}
