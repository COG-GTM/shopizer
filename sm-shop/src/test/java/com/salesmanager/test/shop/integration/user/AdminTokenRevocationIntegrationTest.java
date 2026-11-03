package com.salesmanager.test.shop.integration.user;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;

import java.util.Date;
import java.util.UUID;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.SpringBootTest.WebEnvironment;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.test.context.junit4.SpringRunner;

import com.salesmanager.shop.application.ShopApplication;
import com.salesmanager.shop.model.customer.PersistableCustomer;
import com.salesmanager.shop.model.customer.ReadableCustomer;
import com.salesmanager.shop.model.customer.address.Address;
import com.salesmanager.shop.model.security.PersistableGroup;
import com.salesmanager.shop.model.user.PersistableUser;
import com.salesmanager.shop.model.user.ReadableUser;
import com.salesmanager.shop.model.user.UserPassword;
import com.salesmanager.shop.store.security.AuthenticationRequest;
import com.salesmanager.shop.store.security.AuthenticationResponse;
import com.salesmanager.test.shop.common.ServicesTestSupport;

import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.SignatureAlgorithm;

@SpringBootTest(classes = ShopApplication.class, webEnvironment = WebEnvironment.RANDOM_PORT)
@RunWith(SpringRunner.class)
public class AdminTokenRevocationIntegrationTest extends ServicesTestSupport {

  private static final String INITIAL_PASSWORD = "Password1";
  private static final String CHANGED_PASSWORD = "Password2";

  @Value("${jwt.secret}")
  private String jwtSecret;

  @Test
  public void refreshedAdminTokenWorksOnPrivateEndpoint() throws Exception {
    ReadableUser user = createAdminUser(uniqueUsername(), INITIAL_PASSWORD);
    String token = login(user.getUserName(), INITIAL_PASSWORD).getToken();

    ResponseEntity<AuthenticationResponse> refreshed = refresh(token);
    assertEquals(HttpStatus.OK, refreshed.getStatusCode());
    assertNotNull(refreshed.getBody());
    assertEquals(HttpStatus.OK, getUserWith(user.getId(), bearer(refreshed.getBody().getToken())));
  }

  @Test
  public void passwordPatchRejectsRefreshAndOldToken() throws Exception {
    ReadableUser user = createAdminUser(uniqueUsername(), INITIAL_PASSWORD);
    String oldToken = login(user.getUserName(), INITIAL_PASSWORD).getToken();

    changePassword(user.getId(), INITIAL_PASSWORD, CHANGED_PASSWORD);

    ResponseEntity<AuthenticationResponse> refreshed = refresh(oldToken);
    assertEquals(HttpStatus.BAD_REQUEST, refreshed.getStatusCode());
    assertEquals(HttpStatus.UNAUTHORIZED, getUserWith(user.getId(), bearer(oldToken)));
  }

  @Test
  public void disabledUserRefreshIsRejectedAndLoginResponsesAreIdentical() throws Exception {
    String username = uniqueUsername();
    ReadableUser user = createAdminUser(username, INITIAL_PASSWORD);
    String oldToken = login(username, INITIAL_PASSWORD).getToken();

    setEnabled(user.getId(), false);

    ResponseEntity<AuthenticationResponse> refreshed = refresh(oldToken);
    assertEquals(HttpStatus.BAD_REQUEST, refreshed.getStatusCode());

    ResponseEntity<String> disabledLogin = rawLogin(username, INITIAL_PASSWORD);
    ResponseEntity<String> wrongPasswordLogin = rawLogin(username, "WrongPassword");
    assertEquals(HttpStatus.UNAUTHORIZED, disabledLogin.getStatusCode());
    assertEquals(HttpStatus.UNAUTHORIZED, wrongPasswordLogin.getStatusCode());
    assertEquals("{\"message\":\"Bad credentials\"}", disabledLogin.getBody());
    assertEquals(disabledLogin.getBody(), wrongPasswordLogin.getBody());
  }

  @Test
  public void samePasswordPutPreservesTokenButChangedPasswordInvalidatesIt() throws Exception {
    String username = uniqueUsername();
    ReadableUser user = createAdminUser(username, INITIAL_PASSWORD);
    String oldToken = login(username, INITIAL_PASSWORD).getToken();

    updateUser(user, username, INITIAL_PASSWORD, "Updated");
    assertEquals(HttpStatus.OK, getUserWith(user.getId(), bearer(oldToken)));

    updateUser(user, username, CHANGED_PASSWORD, "UpdatedAgain");
    assertEquals(HttpStatus.UNAUTHORIZED, getUserWith(user.getId(), bearer(oldToken)));
    assertEquals(HttpStatus.OK, rawLogin(username, CHANGED_PASSWORD).getStatusCode());
  }

  @Test
  public void reenabledUserCanLoginAndUsePreDisableToken() throws Exception {
    String username = uniqueUsername();
    ReadableUser user = createAdminUser(username, INITIAL_PASSWORD);
    String oldToken = login(username, INITIAL_PASSWORD).getToken();

    setEnabled(user.getId(), false);
    assertEquals(HttpStatus.UNAUTHORIZED, getUserWith(user.getId(), bearer(oldToken)));

    setEnabled(user.getId(), true);
    assertEquals(HttpStatus.OK, rawLogin(username, INITIAL_PASSWORD).getStatusCode());
    assertEquals(HttpStatus.OK, getUserWith(user.getId(), bearer(oldToken)));
  }

  @Test
  public void legacyTokenIsRejectedAfterPasswordChange() throws Exception {
    String username = uniqueUsername();
    ReadableUser user = createAdminUser(username, INITIAL_PASSWORD);
    Thread.sleep(1100L);
    String legacyToken = legacyToken(username);
    assertEquals(HttpStatus.OK, getUserWith(user.getId(), bearer(legacyToken)));

    Thread.sleep(1100L);
    changePassword(user.getId(), INITIAL_PASSWORD, CHANGED_PASSWORD);

    assertEquals(HttpStatus.UNAUTHORIZED, getUserWith(user.getId(), bearer(legacyToken)));
  }

  @Test
  public void customerTokenIsUnaffectedByAdminPasswordChange() throws Exception {
    String customerName = uniqueUsername();
    registerCustomer(customerName, "Customer1");
    String customerToken = customerLogin(customerName, "Customer1").getToken();
    assertEquals(HttpStatus.OK, customerProfile(customerToken));

    ReadableUser admin = createAdminUser(uniqueUsername(), INITIAL_PASSWORD);
    changePassword(admin.getId(), INITIAL_PASSWORD, CHANGED_PASSWORD);

    assertEquals(HttpStatus.OK, customerProfile(customerToken));
  }

  private ReadableUser createAdminUser(String username, String password) throws Exception {
    PersistableUser newUser = new PersistableUser();
    newUser.setDefaultLanguage("en");
    newUser.setEmailAddress(username);
    newUser.setFirstName("Test");
    newUser.setLastName("User");
    newUser.setUserName(username);
    newUser.setPassword(password);
    newUser.setRepeatPassword(password);
    newUser.setActive(true);

    PersistableGroup group = new PersistableGroup();
    group.setName("ADMIN");
    newUser.getGroups().add(group);

    ResponseEntity<ReadableUser> response = testRestTemplate.exchange("/api/v1/private/user/", HttpMethod.POST,
        new HttpEntity<PersistableUser>(newUser, getHeader()), ReadableUser.class);
    assertEquals(response.toString(), HttpStatus.OK, response.getStatusCode());
    assertNotNull(response.getBody());
    return response.getBody();
  }

  private void changePassword(Long userId, String oldPassword, String newPassword) {
    UserPassword password = new UserPassword();
    password.setPassword(oldPassword);
    password.setChangePassword(newPassword);
    ResponseEntity<Void> response = testRestTemplate.exchange("/api/v1/private/user/" + userId + "/password",
        HttpMethod.PATCH, new HttpEntity<UserPassword>(password, getHeader()), Void.class);
    assertEquals(HttpStatus.OK, response.getStatusCode());
  }

  private void setEnabled(Long userId, boolean enabled) {
    PersistableUser update = new PersistableUser();
    update.setActive(enabled);
    ResponseEntity<Void> response = testRestTemplate.exchange("/api/v1/private/user/" + userId + "/enabled",
        HttpMethod.PATCH, new HttpEntity<PersistableUser>(update, getHeader()), Void.class);
    assertEquals(HttpStatus.OK, response.getStatusCode());
  }

  private void updateUser(ReadableUser user, String username, String password, String firstName) {
    PersistableUser update = new PersistableUser();
    update.setDefaultLanguage("en");
    update.setEmailAddress(username);
    update.setUserName(username);
    update.setFirstName(firstName);
    update.setLastName("User");
    update.setPassword(password);
    update.setRepeatPassword(password);
    update.setActive(true);
    PersistableGroup group = new PersistableGroup();
    group.setName("ADMIN");
    update.getGroups().add(group);
    ResponseEntity<ReadableUser> response = testRestTemplate.exchange("/api/v1/private/user/" + user.getId(),
        HttpMethod.PUT, new HttpEntity<PersistableUser>(update, getHeader()), ReadableUser.class);
    assertEquals(response.toString(), HttpStatus.OK, response.getStatusCode());
  }

  private AuthenticationResponse login(String username, String password) {
    ResponseEntity<AuthenticationResponse> response = testRestTemplate.postForEntity("/api/v1/private/login",
        new HttpEntity<AuthenticationRequest>(new AuthenticationRequest(username, password)),
        AuthenticationResponse.class);
    assertEquals(HttpStatus.OK, response.getStatusCode());
    assertNotNull(response.getBody());
    return response.getBody();
  }

  private ResponseEntity<String> rawLogin(String username, String password) {
    return testRestTemplate.postForEntity("/api/v1/private/login",
        new HttpEntity<AuthenticationRequest>(new AuthenticationRequest(username, password)), String.class);
  }

  private ResponseEntity<AuthenticationResponse> refresh(String token) {
    return testRestTemplate.exchange("/api/v1/auth/refresh", HttpMethod.GET,
        new HttpEntity<String>(bearer(token)), AuthenticationResponse.class);
  }

  private HttpStatus getUserWith(Long userId, HttpHeaders headers) {
    return testRestTemplate.exchange("/api/v1/private/users/" + userId, HttpMethod.GET,
        new HttpEntity<String>(headers), String.class).getStatusCode();
  }

  private HttpHeaders bearer(String token) {
    HttpHeaders headers = new HttpHeaders();
    headers.setContentType(org.springframework.http.MediaType.APPLICATION_JSON);
    headers.set("Authorization", "Bearer " + token);
    return headers;
  }

  private void registerCustomer(String username, String password) {
    PersistableCustomer customer = new PersistableCustomer();
    customer.setEmailAddress(username);
    customer.setUserName(username);
    customer.setPassword(password);
    customer.setRepeatPassword(password);
    customer.setFirstName("Customer");
    customer.setLastName("Test");
    customer.setLanguage("en");
    customer.setStoreCode("DEFAULT");
    Address billing = new Address();
    billing.setFirstName("Customer");
    billing.setLastName("Test");
    billing.setCountry("US");
    customer.setBilling(billing);
    ResponseEntity<ReadableCustomer> response = testRestTemplate.postForEntity("/api/v1/customer/register",
        new HttpEntity<PersistableCustomer>(customer, getHeader()), ReadableCustomer.class);
    assertEquals(response.toString(), HttpStatus.OK, response.getStatusCode());
  }

  private AuthenticationResponse customerLogin(String username, String password) {
    ResponseEntity<AuthenticationResponse> response = testRestTemplate.postForEntity("/api/v1/customer/login",
        new HttpEntity<AuthenticationRequest>(new AuthenticationRequest(username, password)),
        AuthenticationResponse.class);
    assertEquals(HttpStatus.OK, response.getStatusCode());
    assertNotNull(response.getBody());
    return response.getBody();
  }

  private HttpStatus customerProfile(String token) {
    return testRestTemplate.exchange("/api/v1/auth/customer/profile", HttpMethod.GET,
        new HttpEntity<String>(bearer(token)), String.class).getStatusCode();
  }

  private String legacyToken(String username) {
    Date issuedAt = new Date();
    Date expiration = new Date(issuedAt.getTime() + 3600000L);
    return Jwts.builder().setSubject(username).setAudience("api").setIssuedAt(issuedAt)
        .setExpiration(expiration).signWith(SignatureAlgorithm.HS512, jwtSecret).compact();
  }

  private String uniqueUsername() {
    return "pr311-" + UUID.randomUUID().toString().replace("-", "") + "@example.test";
  }
}
