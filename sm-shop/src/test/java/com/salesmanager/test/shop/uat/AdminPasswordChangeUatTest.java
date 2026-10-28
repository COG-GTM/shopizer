package com.salesmanager.test.shop.uat;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;

import java.util.Arrays;

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

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.salesmanager.shop.application.ShopApplication;
import com.salesmanager.shop.constants.Constants;
import com.salesmanager.shop.model.security.PersistableGroup;
import com.salesmanager.shop.model.user.PersistableUser;
import com.salesmanager.shop.model.user.ReadableUser;
import com.salesmanager.shop.model.user.UserPassword;
import com.salesmanager.shop.store.security.AuthenticationRequest;
import com.salesmanager.shop.store.security.AuthenticationResponse;
import com.salesmanager.test.shop.common.ServicesTestSupport;

@SpringBootTest(classes = ShopApplication.class, webEnvironment = WebEnvironment.RANDOM_PORT)
@RunWith(SpringRunner.class)
public class AdminPasswordChangeUatTest extends ServicesTestSupport {

	private static final String INITIAL_PASSWORD = "Password1";
	private static final String NEW_PASSWORD = "Password2";
	private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

	@Test
	public void adminCanRotateOwnPasswordAndLogInWithTheNewPassword() {
		ReadableUser admin = createAdmin();
		ResponseEntity<String> changeResponse = changePassword(admin.getId(), admin.getUserName(), INITIAL_PASSWORD,
				INITIAL_PASSWORD, NEW_PASSWORD);

		assertEquals(HttpStatus.OK, changeResponse.getStatusCode());
		assertEquals(HttpStatus.OK, login(admin.getUserName(), NEW_PASSWORD).getStatusCode());
		assertEquals(HttpStatus.UNAUTHORIZED, login(admin.getUserName(), INITIAL_PASSWORD).getStatusCode());
	}

	@Test
	public void adminCannotTakeOverSuperadminAccount() throws Exception {
		ReadableUser admin = createAdmin();
		ResponseEntity<String> changeResponse = changePassword(1L, admin.getUserName(), INITIAL_PASSWORD, "password",
				NEW_PASSWORD);

		assertEquals(HttpStatus.FORBIDDEN, changeResponse.getStatusCode());
		assertEquals("403", OBJECT_MAPPER.readTree(changeResponse.getBody()).path("errorCode").asText());
		assertEquals(HttpStatus.OK, login("admin@shopizer.com", "password").getStatusCode());
	}

	private ReadableUser createAdmin() {
		String username = "password-uat-" + System.nanoTime() + "@example.com";
		PersistableUser user = new PersistableUser();
		user.setDefaultLanguage("en");
		user.setEmailAddress(username);
		user.setFirstName("Password");
		user.setLastName("UAT");
		user.setUserName(username);
		user.setPassword(INITIAL_PASSWORD);
		user.setRepeatPassword(INITIAL_PASSWORD);

		PersistableGroup group = new PersistableGroup();
		group.setName(Constants.GROUP_ADMIN);
		user.setGroups(Arrays.asList(group));

		ResponseEntity<ReadableUser> response = testRestTemplate.exchange("/api/v1/private/user/?store=DEFAULT",
				HttpMethod.POST, new HttpEntity<>(user, getHeader()), ReadableUser.class);
		assertEquals(HttpStatus.OK, response.getStatusCode());
		assertNotNull(response.getBody());
		return response.getBody();
	}

	private ResponseEntity<String> changePassword(Long userId, String callerName, String callerPassword,
			String currentPassword, String newPassword) {
		UserPassword password = new UserPassword();
		password.setPassword(currentPassword);
		password.setChangePassword(newPassword);
		return testRestTemplate.exchange("/api/v1/private/user/" + userId + "/password", HttpMethod.PATCH,
				new HttpEntity<>(password, getHeader(callerName, callerPassword)), String.class);
	}

	private ResponseEntity<String> login(String username, String password) {
		return testRestTemplate.postForEntity("/api/v1/private/login",
				new HttpEntity<>(new AuthenticationRequest(username, password)), String.class);
	}
}
