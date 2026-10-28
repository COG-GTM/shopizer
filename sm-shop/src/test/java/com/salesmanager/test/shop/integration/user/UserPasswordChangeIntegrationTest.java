package com.salesmanager.test.shop.integration.user;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import java.util.Arrays;

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

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.salesmanager.shop.application.ShopApplication;
import com.salesmanager.shop.constants.Constants;
import com.salesmanager.shop.model.references.PersistableAddress;
import com.salesmanager.shop.model.security.PersistableGroup;
import com.salesmanager.shop.model.store.PersistableMerchantStore;
import com.salesmanager.shop.model.user.PersistableUser;
import com.salesmanager.shop.model.user.ReadableUser;
import com.salesmanager.shop.model.user.UserPassword;
import com.salesmanager.shop.store.security.AuthenticationRequest;
import com.salesmanager.shop.store.security.AuthenticationResponse;
import com.salesmanager.test.shop.common.ServicesTestSupport;

@SpringBootTest(classes = ShopApplication.class, webEnvironment = WebEnvironment.RANDOM_PORT)
@RunWith(SpringRunner.class)
public class UserPasswordChangeIntegrationTest extends ServicesTestSupport {

	private static final String INITIAL_PASSWORD = "Password1";
	private static final String NEW_PASSWORD = "Password2";
	private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

	@Inject
	private TestRestTemplate testRestTemplate;

	@Test
	public void adminCanChangeOwnPasswordAndLogInWithIt() throws Exception {
		ReadableUser user = createUser(Constants.GROUP_ADMIN, "DEFAULT");

		ResponseEntity<String> changeResponse = changePassword(user.getId(), user.getUserName(), INITIAL_PASSWORD,
				INITIAL_PASSWORD, NEW_PASSWORD);
		assertEquals(HttpStatus.OK, changeResponse.getStatusCode());
		assertEquals(HttpStatus.OK, login(user.getUserName(), NEW_PASSWORD).getStatusCode());
		assertEquals(HttpStatus.UNAUTHORIZED, login(user.getUserName(), INITIAL_PASSWORD).getStatusCode());
	}

	@Test
	public void sameStoreAdminCannotChangeSuperadminPassword() throws Exception {
		ReadableUser admin = createUser(Constants.GROUP_ADMIN, "DEFAULT");

		ResponseEntity<String> changeResponse = changePassword(1L, admin.getUserName(), INITIAL_PASSWORD, "password",
				NEW_PASSWORD);
		assertEquals(HttpStatus.FORBIDDEN, changeResponse.getStatusCode());
		assertEquals("403", errorCode(changeResponse));
		assertEquals(HttpStatus.OK, login("admin@shopizer.com", "password").getStatusCode());
	}

	@Test
	public void sameStoreAdminCanChangeCatalogueUserPassword() throws Exception {
		ReadableUser admin = createUser(Constants.GROUP_ADMIN, "DEFAULT");
		ReadableUser catalogueUser = createUser(Constants.GROUP_ADMIN_CATALOGUE, "DEFAULT");

		ResponseEntity<String> changeResponse = changePassword(catalogueUser.getId(), admin.getUserName(),
				INITIAL_PASSWORD, INITIAL_PASSWORD, NEW_PASSWORD);
		assertEquals(HttpStatus.OK, changeResponse.getStatusCode());
		assertEquals(HttpStatus.OK, login(catalogueUser.getUserName(), NEW_PASSWORD).getStatusCode());
	}

	@Test
	public void catalogueUserCannotChangeAdminPassword() throws Exception {
		ReadableUser admin = createUser(Constants.GROUP_ADMIN, "DEFAULT");
		ReadableUser catalogueUser = createUser(Constants.GROUP_ADMIN_CATALOGUE, "DEFAULT");

		ResponseEntity<String> changeResponse = changePassword(admin.getId(), catalogueUser.getUserName(),
				INITIAL_PASSWORD, INITIAL_PASSWORD, NEW_PASSWORD);
		assertEquals(HttpStatus.FORBIDDEN, changeResponse.getStatusCode());
		assertEquals(HttpStatus.OK, login(admin.getUserName(), INITIAL_PASSWORD).getStatusCode());
	}

	@Test
	public void adminOfAnotherStoreCannotChangeDefaultStoreAdminPassword() throws Exception {
		String otherStoreCode = createStore();
		ReadableUser otherStoreAdmin = createUser(Constants.GROUP_ADMIN, otherStoreCode);
		ReadableUser defaultStoreAdmin = createUser(Constants.GROUP_ADMIN, "DEFAULT");

		ResponseEntity<String> changeResponse = changePassword(defaultStoreAdmin.getId(), otherStoreAdmin.getUserName(),
				INITIAL_PASSWORD, INITIAL_PASSWORD, NEW_PASSWORD);
		assertEquals(HttpStatus.FORBIDDEN, changeResponse.getStatusCode());
		assertEquals(HttpStatus.OK, login(defaultStoreAdmin.getUserName(), INITIAL_PASSWORD).getStatusCode());
	}

	private ReadableUser createUser(String groupName, String storeCode) {
		String username = "password-change-" + System.nanoTime() + "@example.com";
		PersistableUser user = new PersistableUser();
		user.setDefaultLanguage("en");
		user.setEmailAddress(username);
		user.setFirstName("Password");
		user.setLastName("Change");
		user.setUserName(username);
		user.setPassword(INITIAL_PASSWORD);
		user.setRepeatPassword(INITIAL_PASSWORD);

		PersistableGroup group = new PersistableGroup();
		group.setName(groupName);
		user.getGroups().add(group);

		ResponseEntity<ReadableUser> response = testRestTemplate.exchange(
				"/api/v1/private/user/?store=" + storeCode, HttpMethod.POST,
				new HttpEntity<>(user, getHeader()), ReadableUser.class);
		assertEquals(HttpStatus.OK, response.getStatusCode());
		assertNotNull(response.getBody());
		return response.getBody();
	}

	private String createStore() {
		String storeCode = "P" + Math.abs(System.nanoTime() % 1000000000L);
		PersistableAddress address = new PersistableAddress();
		address.setAddress("1 Test Street");
		address.setPostalCode("12345");
		address.setCountry("US");
		address.setCity("Test City");
		address.setStateProvince("CA");

		PersistableMerchantStore store = new PersistableMerchantStore();
		store.setCode(storeCode);
		store.setCurrency("USD");
		store.setDefaultLanguage("en");
		store.setEmail(storeCode.toLowerCase() + "@example.com");
		store.setName(storeCode);
		store.setPhone("555-555-5555");
		store.setSupportedLanguages(Arrays.asList("en"));
		store.setAddress(address);

		ResponseEntity<String> response = testRestTemplate.exchange("/api/v1/private/store/", HttpMethod.POST,
				new HttpEntity<>(store, getHeader()), String.class);
		assertEquals(HttpStatus.OK, response.getStatusCode());
		return storeCode;
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

	private String errorCode(ResponseEntity<String> response) throws Exception {
		JsonNode body = OBJECT_MAPPER.readTree(response.getBody());
		return body.path("errorCode").asText();
	}
}
