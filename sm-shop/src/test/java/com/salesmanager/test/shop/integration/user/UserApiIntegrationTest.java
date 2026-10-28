package com.salesmanager.test.shop.integration.user;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;

import java.util.UUID;

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
  



  @Test
  public void lowPrivilegeUserCannotTakeOverSuperadmin() {
      ReadableUser caller = createUser("ADMIN_ORDER");
      PersistableUser update = userUpdate("admin@shopizer.com", "attacker@test.com", "Attacker", "ADMIN_ORDER");
      update.setPassword("Attacker1!");
      update.setRepeatPassword("Attacker1!");

      ResponseEntity<String> response = updateUser(DEFAULT_USER_ID, update,
          getHeader(caller.getUserName(), CREATED_PASSWORD));

      assertEquals(HttpStatus.UNAUTHORIZED, response.getStatusCode());
      assertEquals(HttpStatus.OK, login("admin@shopizer.com", "password"));
      assertEquals(HttpStatus.UNAUTHORIZED, login("admin@shopizer.com", "Attacker1!"));
      assertEquals("admin@shopizer.com", getUser(DEFAULT_USER_ID).getEmailAddress());
  }

  @Test
  public void adminCannotUpdateSuperadmin() {
      ReadableUser caller = createUser("ADMIN");
      PersistableUser update = userUpdate("admin@shopizer.com", "attacker@test.com", "Attacker", "ADMIN");

      ResponseEntity<String> response = updateUser(DEFAULT_USER_ID, update,
          getHeader(caller.getUserName(), CREATED_PASSWORD));

      assertEquals(HttpStatus.UNAUTHORIZED, response.getStatusCode());
      assertEquals("admin@shopizer.com", getUser(DEFAULT_USER_ID).getEmailAddress());
  }

  @Test
  public void lowPrivilegeUserCannotUpdateOtherUser() {
      ReadableUser caller = createUser("ADMIN_ORDER");
      ReadableUser target = createUser("ADMIN_CATALOGUE");
      PersistableUser update = userUpdate(target.getUserName(), "changed@test.com", "Changed", "ADMIN_CATALOGUE");

      ResponseEntity<String> response = updateUser(target.getId(), update,
          getHeader(caller.getUserName(), CREATED_PASSWORD));

      assertEquals(HttpStatus.UNAUTHORIZED, response.getStatusCode());
      assertEquals(target.getEmailAddress(), getUser(target.getId()).getEmailAddress());
  }

  @Test
  public void updateNeverChangesPassword() {
      ReadableUser target = createUser("ADMIN_CATALOGUE");
      PersistableUser update = userUpdate(target.getUserName(), target.getEmailAddress(), "Updated", "ADMIN_CATALOGUE");
      update.setPassword(NEW_CREATED_PASSWORD);
      update.setRepeatPassword(NEW_CREATED_PASSWORD);

      ResponseEntity<String> response = updateUser(target.getId(), update, getHeader());

      assertEquals(HttpStatus.OK, response.getStatusCode());
      assertEquals(HttpStatus.OK, login(target.getUserName(), CREATED_PASSWORD));
      assertEquals(HttpStatus.UNAUTHORIZED, login(target.getUserName(), NEW_CREATED_PASSWORD));
  }

  @Test
  public void updateRejectsUserNameChange() {
      ReadableUser target = createUser("ADMIN_CATALOGUE");
      String newName = "renamed-" + UUID.randomUUID() + "@test.com";
      PersistableUser update = userUpdate(newName, target.getEmailAddress(), "Updated", "ADMIN_CATALOGUE");

      ResponseEntity<String> response = updateUser(target.getId(), update, getHeader());

      assertEquals(HttpStatus.BAD_REQUEST, response.getStatusCode());
      assertEquals(target.getUserName(), getUser(target.getId()).getUserName());
  }

  @Test
  public void adminCanUpdateSameStoreUser() {
      ReadableUser caller = createUser("ADMIN");
      ReadableUser target = createUser("ADMIN_CATALOGUE");
      PersistableUser update = userUpdate(target.getUserName(), target.getEmailAddress(), "Updated", "ADMIN_CATALOGUE");

      ResponseEntity<String> response = updateUser(target.getId(), update,
          getHeader(caller.getUserName(), CREATED_PASSWORD));

      assertEquals(HttpStatus.OK, response.getStatusCode());
      assertEquals("Updated", getUser(target.getId()).getFirstName());
  }

  @Test
  public void userCanUpdateOwnProfile() {
      ReadableUser caller = createUser("ADMIN_ORDER");
      PersistableUser update = userUpdate(caller.getUserName(), caller.getEmailAddress(), "Updated", "ADMIN_ORDER");

      ResponseEntity<String> response = updateUser(caller.getId(), update,
          getHeader(caller.getUserName(), CREATED_PASSWORD));

      assertEquals(HttpStatus.OK, response.getStatusCode());
      assertEquals("Updated", getUser(caller.getId()).getFirstName());
  }

  private HttpStatus login(String userName, String password) {
      return testRestTemplate.postForEntity("/api/v1/private/login",
          new HttpEntity<>(new AuthenticationRequest(userName, password)), String.class)
          .getStatusCode();
  }

  private ReadableUser getUser(Long id) {
      ResponseEntity<ReadableUser> response = testRestTemplate.exchange(
          "/api/v1/private/users/" + id + "?store=DEFAULT", HttpMethod.GET,
          new HttpEntity<String>(getHeader()), ReadableUser.class);
      assertEquals(HttpStatus.OK, response.getStatusCode());
      return response.getBody();
  }

  private ReadableUser createUser(String groupName) {
      String username = "user-" + UUID.randomUUID() + "@test.com";
      PersistableUser newUser = userUpdate(username, username, "Test", groupName);
      newUser.setPassword(CREATED_PASSWORD);
      newUser.setRepeatPassword(CREATED_PASSWORD);
      newUser.setActive(true);

      ResponseEntity<ReadableUser> response = testRestTemplate.exchange(
          "/api/v1/private/user/?store=DEFAULT", HttpMethod.POST,
          new HttpEntity<PersistableUser>(newUser, getHeader()), ReadableUser.class);

      assertEquals(HttpStatus.OK, response.getStatusCode());
      assertNotNull(response.getBody().getId());
      return response.getBody();
  }

  private PersistableUser userUpdate(String username, String email, String firstName, String groupName) {
      PersistableUser user = new PersistableUser();
      user.setUserName(username);
      user.setEmailAddress(email);
      user.setFirstName(firstName);
      user.setLastName("User");
      user.setDefaultLanguage("en");
      user.setActive(true);

      PersistableGroup group = new PersistableGroup();
      group.setName(groupName);
      user.getGroups().add(group);
      return user;
  }

  private ResponseEntity<String> updateUser(Long id, PersistableUser user, HttpHeaders headers) {
      return testRestTemplate.exchange("/api/v1/private/user/" + id + "?store=DEFAULT", HttpMethod.PUT,
          new HttpEntity<PersistableUser>(user, headers), String.class);
  }

}
