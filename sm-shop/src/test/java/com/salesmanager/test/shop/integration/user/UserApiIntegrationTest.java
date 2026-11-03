package com.salesmanager.test.shop.integration.user;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotEquals;
import static org.junit.Assert.assertNotNull;
import java.util.Arrays;
import javax.inject.Inject;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.SpringBootTest.WebEnvironment;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.test.context.junit4.SpringRunner;
import org.springframework.http.HttpHeaders;
import com.salesmanager.shop.application.ShopApplication;
import com.salesmanager.shop.model.references.PersistableAddress;
import com.salesmanager.shop.model.store.PersistableMerchantStore;
import com.salesmanager.shop.model.security.PersistableGroup;
import com.salesmanager.shop.model.user.PersistableUser;
import com.salesmanager.shop.model.user.ReadableUser;
import com.salesmanager.shop.model.user.UserPassword;
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
  public void storeAdminCannotDeleteOrDisableUsersOfOtherStores() throws Exception {
      String otherStore = "authzstore";
      createStore(otherStore);

      ReadableUser otherAdmin = createUser("authz-admin@test.com", otherStore, getHeader());
      ReadableUser otherStaff = createUser("authz-staff@test.com", otherStore, getHeader());
      assertNotNull(otherAdmin);

      HttpHeaders otherAdminHeader = getHeader("authz-admin@test.com", CREATED_PASSWORD);

      PersistableUser disable = new PersistableUser();
      disable.setActive(false);

      // superadmin lives in DEFAULT store: other store admin is not authorized for it
      ResponseEntity<Void> deleteInDefault = testRestTemplate.exchange("/api/v1/private/user/" + DEFAULT_USER_ID + "?store=DEFAULT",
          HttpMethod.DELETE, new HttpEntity<>(otherAdminHeader), Void.class);
      assertEquals(HttpStatus.UNAUTHORIZED, deleteInDefault.getStatusCode());

      ResponseEntity<Void> disableInDefault = testRestTemplate.exchange("/api/v1/private/user/" + DEFAULT_USER_ID + "/enabled?store=DEFAULT",
          HttpMethod.PATCH, new HttpEntity<>(disable, otherAdminHeader), Void.class);
      assertEquals(HttpStatus.UNAUTHORIZED, disableInDefault.getStatusCode());

      // superadmin does not belong to the other store, so it cannot be targeted through it
      ResponseEntity<Void> deleteViaOwnStore = testRestTemplate.exchange("/api/v1/private/user/" + DEFAULT_USER_ID + "?store=" + otherStore,
          HttpMethod.DELETE, new HttpEntity<>(otherAdminHeader), Void.class);
      assertNotEquals(HttpStatus.OK, deleteViaOwnStore.getStatusCode());

      ResponseEntity<Void> disableViaOwnStore = testRestTemplate.exchange("/api/v1/private/user/" + DEFAULT_USER_ID + "/enabled?store=" + otherStore,
          HttpMethod.PATCH, new HttpEntity<>(disable, otherAdminHeader), Void.class);
      assertNotEquals(HttpStatus.OK, disableViaOwnStore.getStatusCode());

      // superadmin is untouched
      ResponseEntity<ReadableUser> superadmin = testRestTemplate.exchange("/api/v1/private/users/" + DEFAULT_USER_ID, HttpMethod.GET,
          new HttpEntity<>(getHeader()), ReadableUser.class);
      assertEquals(HttpStatus.OK, superadmin.getStatusCode());
      assertEquals(true, superadmin.getBody().isActive());

      // admin can still manage users of its own store
      ResponseEntity<Void> disableOwnStaff = testRestTemplate.exchange("/api/v1/private/user/" + otherStaff.getId() + "/enabled?store=" + otherStore,
          HttpMethod.PATCH, new HttpEntity<>(disable, otherAdminHeader), Void.class);
      assertEquals(HttpStatus.OK, disableOwnStaff.getStatusCode());

      ResponseEntity<Void> deleteOwnStaff = testRestTemplate.exchange("/api/v1/private/user/" + otherStaff.getId() + "?store=" + otherStore,
          HttpMethod.DELETE, new HttpEntity<>(otherAdminHeader), Void.class);
      assertEquals(HttpStatus.OK, deleteOwnStaff.getStatusCode());
  }

  private void createStore(String code) {
      PersistableAddress address = new PersistableAddress();
      address.setAddress("121212 simple address");
      address.setPostalCode("12345");
      address.setCountry("US");
      address.setCity("FT LD");
      address.setStateProvince("FL");

      PersistableMerchantStore store = new PersistableMerchantStore();
      store.setCode(code);
      store.setCurrency("CAD");
      store.setDefaultLanguage("en");
      store.setEmail("test@test.com");
      store.setName(code);
      store.setPhone("444-555-6666");
      store.setSupportedLanguages(Arrays.asList("en"));
      store.setAddress(address);

      ResponseEntity<Void> response = testRestTemplate.exchange("/api/v1/private/store/", HttpMethod.POST,
          new HttpEntity<>(store, getHeader()), Void.class);
      assertEquals(HttpStatus.OK, response.getStatusCode());
  }

  private ReadableUser createUser(String userName, String store, HttpHeaders header) {
      PersistableUser newUser = new PersistableUser();
      newUser.setDefaultLanguage("en");
      newUser.setEmailAddress(userName);
      newUser.setFirstName("Test");
      newUser.setLastName("User");
      newUser.setUserName(userName);
      newUser.setPassword(CREATED_PASSWORD);
      newUser.setRepeatPassword(CREATED_PASSWORD);
      newUser.setActive(true);

      PersistableGroup g = new PersistableGroup();
      g.setName("ADMIN");
      newUser.getGroups().add(g);

      ResponseEntity<ReadableUser> response = testRestTemplate.exchange("/api/v1/private/user/?store=" + store, HttpMethod.POST,
          new HttpEntity<>(newUser, header), ReadableUser.class);
      assertEquals(HttpStatus.OK, response.getStatusCode());
      return response.getBody();
  }

}
