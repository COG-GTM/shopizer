package com.salesmanager.test.shop.integration.user;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;

import java.util.UUID;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.SpringBootTest.WebEnvironment;
import org.springframework.http.HttpEntity;
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
import com.salesmanager.shop.store.security.AuthenticationResponse;
import com.salesmanager.test.shop.common.ServicesTestSupport;

@SpringBootTest(classes = ShopApplication.class, webEnvironment = WebEnvironment.RANDOM_PORT)
@RunWith(SpringRunner.class)
public class UserUpdateAuthorizationIntegrationTest extends ServicesTestSupport {

	private static final String CREATED_PASSWORD = "Password1";

	@Test
	public void updateWithoutAuthorizationIsUnauthorized() {
		ResponseEntity<ReadableUser> response = testRestTemplate.exchange("/api/v1/private/user/1", HttpMethod.PUT,
				new HttpEntity<>(userUpdate("admin@shopizer.com")), ReadableUser.class);

		assertEquals(HttpStatus.UNAUTHORIZED, response.getStatusCode());
	}

	@Test
	public void catalogueUserCannotUpdateAnotherUser() {
		ReadableUser caller = createUser("ADMIN_CATALOGUE");
		ReadableUser target = createUser("ADMIN_CATALOGUE");

		ResponseEntity<ReadableUser> response = testRestTemplate.exchange(
				"/api/v1/private/user/" + target.getId(), HttpMethod.PUT,
				new HttpEntity<>(userUpdate(target.getUserName()), getHeader(caller.getUserName(), CREATED_PASSWORD)),
				ReadableUser.class);

		assertEquals(HttpStatus.UNAUTHORIZED, response.getStatusCode());
		assertCanLogin(target.getUserName(), CREATED_PASSWORD);
	}

	@Test
	public void selfUpdateChangesProfileButNotPassword() {
		ReadableUser user = createUser("ADMIN_CATALOGUE");
		PersistableUser update = userUpdate(user.getUserName());
		update.setFirstName("Updated profile");
		update.setPassword("Other123");
		update.getGroups().add(new PersistableGroup("ADMIN_CATALOGUE"));

		ResponseEntity<ReadableUser> response = testRestTemplate.exchange(
				"/api/v1/private/user/" + user.getId(), HttpMethod.PUT,
				new HttpEntity<>(update, getHeader(user.getUserName(), CREATED_PASSWORD)), ReadableUser.class);

		assertEquals(HttpStatus.OK, response.getStatusCode());
		assertEquals("Updated profile", response.getBody().getFirstName());
		assertCanLogin(user.getUserName(), CREATED_PASSWORD);
		assertCannotLogin(user.getUserName(), "Other123");
	}

	@Test
	public void adminCannotGrantSuperadminGroup() {
		ReadableUser caller = createUser("ADMIN");
		ReadableUser target = createUser("ADMIN_CATALOGUE");
		PersistableUser update = userUpdate(target.getUserName());
		update.getGroups().add(new PersistableGroup("SUPERADMIN"));

		ResponseEntity<ReadableUser> response = testRestTemplate.exchange(
				"/api/v1/private/user/" + target.getId(), HttpMethod.PUT,
				new HttpEntity<>(update, getHeader(caller.getUserName(), CREATED_PASSWORD)), ReadableUser.class);

		assertEquals(HttpStatus.UNAUTHORIZED, response.getStatusCode());
	}

	@Test
	public void adminCannotTakeOverSuperadminAccount() {
		ReadableUser caller = createUser("ADMIN");
		PersistableUser update = userUpdate("admin@shopizer.com");
		update.setUserName("takeover-" + UUID.randomUUID() + "@shopizer.com");
		update.setEmailAddress(update.getUserName());
		update.setFirstName("Changed");

		ResponseEntity<ReadableUser> response = testRestTemplate.exchange("/api/v1/private/user/1", HttpMethod.PUT,
				new HttpEntity<>(update, getHeader(caller.getUserName(), CREATED_PASSWORD)), ReadableUser.class);

		assertEquals(HttpStatus.UNAUTHORIZED, response.getStatusCode());
		assertCanLogin("admin@shopizer.com", "password");
	}

	@Test
	public void passwordPatchStillWorks() {
		ReadableUser user = createUser("ADMIN_CATALOGUE");
		UserPassword password = new UserPassword();
		password.setPassword(CREATED_PASSWORD);
		password.setChangePassword("NewPass2");

		ResponseEntity<Void> response = testRestTemplate.exchange(
				"/api/v1/private/user/" + user.getId() + "/password", HttpMethod.PATCH,
				new HttpEntity<>(password, getHeader()), Void.class);

		assertEquals(HttpStatus.OK, response.getStatusCode());
		assertCanLogin(user.getUserName(), "NewPass2");
		assertCannotLogin(user.getUserName(), CREATED_PASSWORD);
	}

	private ReadableUser createUser(String groupName) {
		String username = "user-" + UUID.randomUUID() + "@shopizer.com";
		PersistableUser newUser = new PersistableUser();
		newUser.setDefaultLanguage("en");
		newUser.setEmailAddress(username);
		newUser.setFirstName("Test");
		newUser.setLastName("User");
		newUser.setUserName(username);
		newUser.setPassword(CREATED_PASSWORD);
		newUser.setRepeatPassword(CREATED_PASSWORD);
		newUser.setStore("DEFAULT");
		newUser.setActive(true);
		newUser.getGroups().add(new PersistableGroup(groupName));

		ResponseEntity<ReadableUser> response = testRestTemplate.exchange(
				"/api/v1/private/user/?store=DEFAULT", HttpMethod.POST,
				new HttpEntity<>(newUser, getHeader()), ReadableUser.class);
		assertEquals(HttpStatus.OK, response.getStatusCode());
		assertNotNull(response.getBody());
		return response.getBody();
	}

	private PersistableUser userUpdate(String username) {
		PersistableUser user = new PersistableUser();
		user.setDefaultLanguage("en");
		user.setEmailAddress(username);
		user.setFirstName("Changed");
		user.setLastName("User");
		user.setUserName(username);
		return user;
	}

	private void assertCanLogin(String username, String password) {
		ResponseEntity<AuthenticationResponse> response = testRestTemplate.postForEntity("/api/v1/private/login",
				new HttpEntity<>(new AuthenticationRequest(username, password)), AuthenticationResponse.class);
		assertEquals(HttpStatus.OK, response.getStatusCode());
		assertNotNull(response.getBody());
		assertNotNull(response.getBody().getToken());
	}

	private void assertCannotLogin(String username, String password) {
		ResponseEntity<AuthenticationResponse> response = testRestTemplate.postForEntity("/api/v1/private/login",
				new HttpEntity<>(new AuthenticationRequest(username, password)), AuthenticationResponse.class);
		assertEquals(HttpStatus.UNAUTHORIZED, response.getStatusCode());
	}
}
