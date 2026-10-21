package com.salesmanager.test.shop.uat;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;

import java.util.Collections;
import java.util.UUID;

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

import com.salesmanager.shop.application.ShopApplication;
import com.salesmanager.shop.model.security.PersistableGroup;
import com.salesmanager.shop.model.security.ReadableGroup;
import com.salesmanager.shop.model.user.PersistableUser;
import com.salesmanager.shop.model.user.ReadableUser;
import com.salesmanager.shop.model.user.UserPassword;
import com.salesmanager.shop.store.security.AuthenticationRequest;
import com.salesmanager.shop.store.security.AuthenticationResponse;
import com.salesmanager.test.shop.common.ServicesTestSupport;

@SpringBootTest(classes = ShopApplication.class, webEnvironment = WebEnvironment.RANDOM_PORT)
@RunWith(SpringRunner.class)
public class AdminUserManagementUatTest extends ServicesTestSupport {

	private static final String INITIAL_PASSWORD = "Password1";

	@Inject
	private TestRestTemplate testRestTemplate;

	@Test
	public void catalogueUserCannotChangeSuperadminAndSharedAdminCanStillLogin() {
		ReadableUser catalogueUser = createUser("ADMIN_CATALOGUE");
		ReadableUser protectedSuperadmin = createUser("SUPERADMIN");
		PersistableUser attack = updateBody(protectedSuperadmin.getUserName());
		attack.setEmailAddress("compromised-" + UUID.randomUUID() + "@shopizer.com");
		attack.setFirstName("Compromised");
		attack.setPassword("Compromised9");

		ResponseEntity<ReadableUser> response = testRestTemplate.exchange(
				"/api/v1/private/user/" + protectedSuperadmin.getId(),
				HttpMethod.PUT,
				new HttpEntity<>(attack, getHeader(catalogueUser.getUserName(), INITIAL_PASSWORD)),
				ReadableUser.class);
		assertEquals(HttpStatus.UNAUTHORIZED, response.getStatusCode());

		assertEquals(HttpStatus.OK, login("admin@shopizer.com", "password").getStatusCode());
		ReadableUser sharedAdmin = readUser(1L);
		assertEquals("admin@shopizer.com", sharedAdmin.getEmailAddress());
		assertEquals(protectedSuperadmin.getEmailAddress(), readUser(protectedSuperadmin.getId()).getEmailAddress());
	}

	@Test
	public void adminCanEditCatalogueUserButCannotEditSuperadmin() {
		ReadableUser admin = createUser("ADMIN");
		ReadableUser catalogueUser = createUser("ADMIN_CATALOGUE");
		ReadableUser protectedSuperadmin = createUser("SUPERADMIN");
		PersistableUser edit = updateBody(catalogueUser.getUserName());
		edit.setFirstName("Edited by admin");

		ResponseEntity<ReadableUser> editResponse = testRestTemplate.exchange(
				"/api/v1/private/user/" + catalogueUser.getId(), HttpMethod.PUT,
				new HttpEntity<>(edit, getHeader(admin.getUserName(), INITIAL_PASSWORD)), ReadableUser.class);
		assertEquals(HttpStatus.OK, editResponse.getStatusCode());
		assertEquals("Edited by admin", readUser(catalogueUser.getId()).getFirstName());

		PersistableUser sharedAdminEdit = updateBody(protectedSuperadmin.getUserName());
		sharedAdminEdit.setFirstName("Must not change");
		ResponseEntity<ReadableUser> denied = testRestTemplate.exchange(
				"/api/v1/private/user/" + protectedSuperadmin.getId(),
				HttpMethod.PUT,
				new HttpEntity<>(sharedAdminEdit, getHeader(admin.getUserName(), INITIAL_PASSWORD)),
				ReadableUser.class);
		assertEquals(HttpStatus.UNAUTHORIZED, denied.getStatusCode());
		assertEquals(protectedSuperadmin.getFirstName(), readUser(protectedSuperadmin.getId()).getFirstName());
	}

	@Test
	public void selfProfilePutIgnoresPasswordWhilePatchChangesIt() {
		ReadableUser user = createUser("ADMIN_CATALOGUE");
		PersistableUser edit = updateBody(user.getUserName());
		edit.setPassword("Ignored9");

		ResponseEntity<ReadableUser> putResponse = testRestTemplate.exchange(
				"/api/v1/private/user/" + user.getId(), HttpMethod.PUT,
				new HttpEntity<>(edit, getHeader(user.getUserName(), INITIAL_PASSWORD)), ReadableUser.class);
		assertEquals(HttpStatus.OK, putResponse.getStatusCode());
		assertEquals(HttpStatus.OK, login(user.getUserName(), INITIAL_PASSWORD).getStatusCode());
		assertEquals(HttpStatus.UNAUTHORIZED, login(user.getUserName(), "Ignored9").getStatusCode());

		UserPassword passwordChange = new UserPassword();
		passwordChange.setPassword(INITIAL_PASSWORD);
		passwordChange.setChangePassword("Password2");
		ResponseEntity<Void> patchResponse = testRestTemplate.exchange(
				"/api/v1/private/user/" + user.getId() + "/password", HttpMethod.PATCH,
				new HttpEntity<>(passwordChange, getHeader(user.getUserName(), INITIAL_PASSWORD)), Void.class);
		assertEquals(HttpStatus.OK, patchResponse.getStatusCode());
		assertEquals(HttpStatus.OK, login(user.getUserName(), "Password2").getStatusCode());
		assertEquals(HttpStatus.UNAUTHORIZED, login(user.getUserName(), INITIAL_PASSWORD).getStatusCode());
	}

	private ReadableUser createUser(String groupName) {
		String username = "u-" + UUID.randomUUID() + "@shopizer.com";
		PersistableUser user = new PersistableUser();
		user.setDefaultLanguage("en");
		user.setEmailAddress(username);
		user.setFirstName("Test");
		user.setLastName("User");
		user.setUserName(username);
		user.setPassword(INITIAL_PASSWORD);
		user.setRepeatPassword(INITIAL_PASSWORD);
		user.getGroups().add(new PersistableGroup(groupName));

		ResponseEntity<ReadableUser> response = testRestTemplate.exchange("/api/v1/private/user/",
				HttpMethod.POST, new HttpEntity<>(user, getHeader()), ReadableUser.class);
		assertEquals(HttpStatus.OK, response.getStatusCode());
		assertNotNull(response.getBody());
		return response.getBody();
	}

	private PersistableUser updateBody(String username) {
		PersistableUser body = new PersistableUser();
		body.setDefaultLanguage("en");
		body.setEmailAddress(username);
		body.setFirstName("Changed");
		body.setLastName("User");
		body.setUserName(username);
		return body;
	}

	private ReadableUser readUser(Long id) {
		ResponseEntity<ReadableUser> response = testRestTemplate.exchange("/api/v1/private/users/" + id,
				HttpMethod.GET, new HttpEntity<>(getHeader()), ReadableUser.class);
		assertEquals(HttpStatus.OK, response.getStatusCode());
		assertNotNull(response.getBody());
		return response.getBody();
	}

	private ResponseEntity<AuthenticationResponse> login(String username, String password) {
		return testRestTemplate.postForEntity("/api/v1/private/login",
				new HttpEntity<>(new AuthenticationRequest(username, password)), AuthenticationResponse.class);
	}
}
