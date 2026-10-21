package com.salesmanager.test.shop.uat;

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
public class BackOfficeUserManagementJourneyUatTest extends ServicesTestSupport {

	private static final String PASSWORD = "Password1";

	@Test
	public void createManageAndChangeUserPassword() {
		ReadableUser created = createUser("ADMIN_CATALOGUE");
		ResponseEntity<AuthenticationResponse> userLogin = login(created.getUserName(), PASSWORD);
		assertEquals(HttpStatus.OK, userLogin.getStatusCode());
		assertNotNull(userLogin.getBody());
		assertNotNull(userLogin.getBody().getToken());

		ResponseEntity<ReadableUser> profile = testRestTemplate.exchange("/api/v1/private/user/profile",
				HttpMethod.GET, new HttpEntity<>(getHeader(created.getUserName(), PASSWORD)), ReadableUser.class);
		assertEquals(HttpStatus.OK, profile.getStatusCode());

		PersistableUser selfUpdate = userUpdate(created.getUserName());
		selfUpdate.setFirstName("Profile updated");
		selfUpdate.getGroups().add(new PersistableGroup("ADMIN_CATALOGUE"));
		ResponseEntity<ReadableUser> updated = testRestTemplate.exchange(
				"/api/v1/private/user/" + created.getId(), HttpMethod.PUT,
				new HttpEntity<>(selfUpdate, getHeader(created.getUserName(), PASSWORD)), ReadableUser.class);
		assertEquals(HttpStatus.OK, updated.getStatusCode());
		assertEquals("Profile updated", updated.getBody().getFirstName());

		PersistableUser takeover = userUpdate("admin@shopizer.com");
		takeover.setPassword("Other123");
		ResponseEntity<ReadableUser> rejected = testRestTemplate.exchange("/api/v1/private/user/1", HttpMethod.PUT,
				new HttpEntity<>(takeover, getHeader(created.getUserName(), PASSWORD)), ReadableUser.class);
		assertEquals(HttpStatus.UNAUTHORIZED, rejected.getStatusCode());
		assertEquals(HttpStatus.OK, login("admin@shopizer.com", "password").getStatusCode());

		UserPassword password = new UserPassword();
		password.setPassword(PASSWORD);
		password.setChangePassword("Other123");
		ResponseEntity<Void> changed = testRestTemplate.exchange(
				"/api/v1/private/user/" + created.getId() + "/password", HttpMethod.PATCH,
				new HttpEntity<>(password, getHeader(created.getUserName(), PASSWORD)), Void.class);
		assertEquals(HttpStatus.OK, changed.getStatusCode());
		assertEquals(HttpStatus.OK, login(created.getUserName(), "Other123").getStatusCode());
	}

	private ReadableUser createUser(String groupName) {
		String username = "uat-" + UUID.randomUUID() + "@shopizer.com";
		PersistableUser user = new PersistableUser();
		user.setDefaultLanguage("en");
		user.setEmailAddress(username);
		user.setFirstName("UAT");
		user.setLastName("User");
		user.setUserName(username);
		user.setPassword(PASSWORD);
		user.setRepeatPassword(PASSWORD);
		user.setStore("DEFAULT");
		user.setActive(true);
		user.getGroups().add(new PersistableGroup(groupName));

		ResponseEntity<ReadableUser> response = testRestTemplate.exchange(
				"/api/v1/private/user/?store=DEFAULT", HttpMethod.POST, new HttpEntity<>(user, getHeader()),
				ReadableUser.class);
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

	private ResponseEntity<AuthenticationResponse> login(String username, String password) {
		return testRestTemplate.postForEntity("/api/v1/private/login",
				new HttpEntity<>(new AuthenticationRequest(username, password)), AuthenticationResponse.class);
	}
}
