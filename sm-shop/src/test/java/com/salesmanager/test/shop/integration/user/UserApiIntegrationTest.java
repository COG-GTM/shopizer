package com.salesmanager.test.shop.integration.user;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import javax.inject.Inject;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.SpringBootTest.WebEnvironment;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.test.context.junit4.SpringRunner;
import com.salesmanager.shop.application.ShopApplication;
import com.salesmanager.shop.model.security.PersistableGroup;
import com.salesmanager.shop.model.user.PersistableUser;
import com.salesmanager.shop.model.user.ReadableUser;
import com.salesmanager.shop.model.user.UserPassword;
import com.salesmanager.shop.store.security.AuthenticationRequest;
import com.salesmanager.test.shop.common.ServicesTestSupport;

@SpringBootTest(classes = ShopApplication.class, webEnvironment = WebEnvironment.RANDOM_PORT)
@RunWith(SpringRunner.class)
public class UserApiIntegrationTest extends ServicesTestSupport {
  
  private static Long DEFAULT_USER_ID = 1L;
  private static String CREATED_PASSWORD = "Password1";
  private static String NEW_CREATED_PASSWORD = "Password2";
  
  @Inject
  private TestRestTemplate testRestTemplate;
  
  @Test
  public void getUser() throws Exception {
      final HttpEntity<String> httpEntity = new HttpEntity<>(getHeader());

      final ResponseEntity<ReadableUser> response = testRestTemplate.exchange(String.format("/api/v1/private/users/" + DEFAULT_USER_ID), HttpMethod.GET,
              httpEntity, ReadableUser.class);
      if (response.getStatusCode() != HttpStatus.OK) {
          throw new Exception(response.toString());
      } else {
          final ReadableUser user = response.getBody();
          assertNotNull(user);
      }
  }
  
  @Test
  public void createUserChangePassword() throws Exception {
 
      PersistableUser newUser = new PersistableUser();
      newUser.setDefaultLanguage("en");
      newUser.setEmailAddress("test@test.com");
      newUser.setFirstName("Test");
      newUser.setLastName("User");
      newUser.setUserName("test@test.com");
      newUser.setPassword(CREATED_PASSWORD);
      newUser.setRepeatPassword(CREATED_PASSWORD);
      
      PersistableGroup g = new PersistableGroup();
      g.setName("ADMIN");
      
      newUser.getGroups().add(g);
      
      final HttpEntity<PersistableUser> persistableUser = new HttpEntity<PersistableUser>(newUser, getHeader());

      ReadableUser user = null;
      final ResponseEntity<ReadableUser> response = testRestTemplate.exchange(String.format("/api/v1/private/user/"), HttpMethod.POST,
          persistableUser, ReadableUser.class);
      if (response.getStatusCode() != HttpStatus.OK) {
          throw new Exception(response.toString());
      } else {
          user = response.getBody();
          assertNotNull(user); 
      }
      
      String oldPassword = CREATED_PASSWORD;
      String newPassword = NEW_CREATED_PASSWORD;
      String repeatPassword = newPassword;
      
      UserPassword userPassword = new UserPassword();
      userPassword.setPassword(oldPassword);
      userPassword.setChangePassword(newPassword);
      
      final HttpEntity<UserPassword> changePasswordEntity = new HttpEntity<UserPassword>(userPassword, getHeader());

      
      final ResponseEntity<Void> changePassword = testRestTemplate.exchange(String.format("/api/v1/private/user/" + user.getId() + "/password"), HttpMethod.PATCH, changePasswordEntity, Void.class);
      if (changePassword.getStatusCode() != HttpStatus.OK) {
          throw new Exception(response.toString());
      } else {
        assertNotNull("Password changed"); 
      }
      
      
  }

  private ReadableUser createAdminUser(String userName, String password) throws Exception {
      PersistableUser newUser = new PersistableUser();
      newUser.setDefaultLanguage("en");
      newUser.setEmailAddress(userName);
      newUser.setFirstName("Test");
      newUser.setLastName("User");
      newUser.setUserName(userName);
      newUser.setPassword(password);
      newUser.setRepeatPassword(password);
      newUser.setActive(true);
      PersistableGroup g = new PersistableGroup();
      g.setName("ADMIN");
      newUser.getGroups().add(g);

      final ResponseEntity<ReadableUser> response = testRestTemplate.exchange("/api/v1/private/user/", HttpMethod.POST,
          new HttpEntity<PersistableUser>(newUser, getHeader()), ReadableUser.class);
      assertEquals(HttpStatus.OK, response.getStatusCode());
      return response.getBody();
  }

  private HttpStatus getUserWith(HttpHeaders headers, Long id) {
      return testRestTemplate.exchange("/api/v1/private/users/" + id, HttpMethod.GET,
          new HttpEntity<String>(headers), String.class).getStatusCode();
  }

  private HttpStatus login(String userName, String password) {
      return testRestTemplate.postForEntity("/api/v1/private/login",
          new HttpEntity<>(new AuthenticationRequest(userName, password)), String.class).getStatusCode();
  }

  @Test
  public void disabledUserTokenIsRejected() throws Exception {
      String userName = "disabled@test.com";
      ReadableUser user = createAdminUser(userName, CREATED_PASSWORD);
      HttpHeaders userHeaders = getHeader(userName, CREATED_PASSWORD);
      assertEquals(HttpStatus.OK, getUserWith(userHeaders, user.getId()));

      PersistableUser disable = new PersistableUser();
      disable.setActive(false);
      final ResponseEntity<Void> disabled = testRestTemplate.exchange("/api/v1/private/user/" + user.getId() + "/enabled",
          HttpMethod.PATCH, new HttpEntity<PersistableUser>(disable, getHeader()), Void.class);
      assertEquals(HttpStatus.OK, disabled.getStatusCode());

      assertEquals(HttpStatus.UNAUTHORIZED, getUserWith(userHeaders, user.getId()));
      assertEquals(HttpStatus.UNAUTHORIZED, testRestTemplate.exchange("/api/v1/auth/refresh", HttpMethod.GET,
          new HttpEntity<String>(userHeaders), String.class).getStatusCode());
      assertEquals(HttpStatus.UNAUTHORIZED, login(userName, CREATED_PASSWORD));
  }

  @Test
  public void tokenIssuedBeforePasswordChangeIsRejected() throws Exception {
      String userName = "rotated@test.com";
      ReadableUser user = createAdminUser(userName, CREATED_PASSWORD);
      HttpHeaders oldHeaders = getHeader(userName, CREATED_PASSWORD);
      assertEquals(HttpStatus.OK, getUserWith(oldHeaders, user.getId()));

      // token issued-at has second precision
      Thread.sleep(1100);

      UserPassword userPassword = new UserPassword();
      userPassword.setPassword(CREATED_PASSWORD);
      userPassword.setChangePassword(NEW_CREATED_PASSWORD);
      final ResponseEntity<Void> changed = testRestTemplate.exchange("/api/v1/private/user/" + user.getId() + "/password",
          HttpMethod.PATCH, new HttpEntity<UserPassword>(userPassword, getHeader()), Void.class);
      assertEquals(HttpStatus.OK, changed.getStatusCode());

      assertEquals(HttpStatus.UNAUTHORIZED, getUserWith(oldHeaders, user.getId()));
      assertEquals(HttpStatus.UNAUTHORIZED, testRestTemplate.exchange("/api/v1/auth/refresh", HttpMethod.GET,
          new HttpEntity<String>(oldHeaders), String.class).getStatusCode());

      assertEquals(HttpStatus.OK, getUserWith(getHeader(userName, NEW_CREATED_PASSWORD), user.getId()));
  }

}
