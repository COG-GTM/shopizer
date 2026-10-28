package com.salesmanager.test.shop.uat;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import java.util.Collections;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.SpringBootTest.WebEnvironment;
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
import com.salesmanager.shop.store.security.AuthenticationRequest;
import com.salesmanager.shop.store.security.AuthenticationResponse;
import com.salesmanager.test.shop.common.ServicesTestSupport;

@SpringBootTest(classes = ShopApplication.class, webEnvironment = WebEnvironment.RANDOM_PORT)
@RunWith(SpringRunner.class)
public class AdminUserManagementJourneyUatTest extends ServicesTestSupport {

	@Test
	public void privateUserListRequiresValidBearerToken() {
		HttpHeaders superadminHeaders = getHeader();
		ResponseEntity<String> authenticated = testRestTemplate.exchange(
				"/api/v1/private/users?store=DEFAULT", HttpMethod.GET,
				new HttpEntity<>(superadminHeaders), String.class);
		assertEquals(HttpStatus.OK, authenticated.getStatusCode());

		ResponseEntity<String> anonymous = testRestTemplate.exchange(
				"/api/v1/private/users?store=DEFAULT", HttpMethod.GET,
				HttpEntity.EMPTY, String.class);
		assertEquals(HttpStatus.UNAUTHORIZED, anonymous.getStatusCode());

		HttpHeaders invalidTokenHeaders = new HttpHeaders();
		invalidTokenHeaders.setBearerAuth("not-a-valid-jwt");
		ResponseEntity<String> invalidToken = testRestTemplate.exchange(
				"/api/v1/private/users?store=DEFAULT", HttpMethod.GET,
				new HttpEntity<>(invalidTokenHeaders), String.class);
		assertTrue("Garbage bearer token was accepted with " + invalidToken.getStatusCode(),
				!invalidToken.getStatusCode().is2xxSuccessful());
	}

	@Test
	public void storeAdminCannotEscalateAndCanCreateAnotherAdmin() {
		String suffix = Long.toHexString(System.nanoTime());
		String storeAdminEmail = "journey-admin-" + suffix + "@example.com";
		ResponseEntity<ReadableUser> storeAdmin = createUser(storeAdminEmail, "ADMIN", getHeader());
		assertEquals(HttpStatus.OK, storeAdmin.getStatusCode());

		HttpHeaders storeAdminHeaders = getHeader(storeAdminEmail, "Password1");
		ResponseEntity<String> escalation = createUserResponse(
				"journey-escalation-" + suffix + "@example.com", "SUPERADMIN", storeAdminHeaders);
		assertEquals(HttpStatus.UNAUTHORIZED, escalation.getStatusCode());

		String regularAdminEmail = "journey-regular-" + suffix + "@example.com";
		ResponseEntity<ReadableUser> regularAdmin = createUser(regularAdminEmail, "ADMIN", storeAdminHeaders);
		assertEquals(HttpStatus.OK, regularAdmin.getStatusCode());
		assertNotNull(regularAdmin.getBody());

		ResponseEntity<AuthenticationResponse> login = testRestTemplate.postForEntity("/api/v1/private/login",
				new HttpEntity<>(new AuthenticationRequest(regularAdminEmail, "Password1")),
				AuthenticationResponse.class);
		assertEquals(HttpStatus.OK, login.getStatusCode());
		assertNotNull(login.getBody());
		assertNotNull(login.getBody().getToken());
	}

	private ResponseEntity<ReadableUser> createUser(String email, String group, HttpHeaders headers) {
		return testRestTemplate.exchange("/api/v1/private/user/?store=DEFAULT", HttpMethod.POST,
				new HttpEntity<>(user(email, group), headers), ReadableUser.class);
	}

	private ResponseEntity<String> createUserResponse(String email, String group, HttpHeaders headers) {
		return testRestTemplate.exchange("/api/v1/private/user/?store=DEFAULT", HttpMethod.POST,
				new HttpEntity<>(user(email, group), headers), String.class);
	}

	private PersistableUser user(String email, String groupName) {
		PersistableUser user = new PersistableUser();
		user.setUserName(email);
		user.setEmailAddress(email);
		user.setFirstName("Journey");
		user.setLastName("User");
		user.setPassword("Password1");
		user.setRepeatPassword("Password1");
		user.setDefaultLanguage("en");
		user.setActive(true);

		PersistableGroup group = new PersistableGroup();
		group.setName(groupName);
		user.setGroups(Collections.singletonList(group));
		return user;
	}
}
