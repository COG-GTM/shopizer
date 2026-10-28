package com.salesmanager.test.shop.integration.user;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import java.util.Collections;

import org.junit.Before;
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
import com.salesmanager.shop.model.references.PersistableAddress;
import com.salesmanager.shop.model.security.PersistableGroup;
import com.salesmanager.shop.model.store.PersistableMerchantStore;
import com.salesmanager.shop.model.user.PersistableUser;
import com.salesmanager.shop.model.user.ReadableUser;
import com.salesmanager.shop.store.security.AuthenticationRequest;
import com.salesmanager.shop.store.security.AuthenticationResponse;
import com.salesmanager.test.shop.common.ServicesTestSupport;

@SpringBootTest(classes = ShopApplication.class, webEnvironment = WebEnvironment.RANDOM_PORT)
@RunWith(SpringRunner.class)
public class UserCreateAuthorizationIntegrationTest extends ServicesTestSupport {

	private String suffix;
	private String otherStoreCode;
	private String parentStoreCode;
	private String childStoreCode;
	private HttpHeaders superadminHeaders;

	@Before
	public void createStores() {
		suffix = Long.toHexString(System.nanoTime());
		otherStoreCode = "other" + suffix;
		parentStoreCode = "parent" + suffix;
		childStoreCode = "child" + suffix;
		superadminHeaders = getHeader();

		createStore(otherStoreCode, null, false);
		createStore(parentStoreCode, null, true);
		createStore(childStoreCode, parentStoreCode, false);

		ResponseEntity<String> children = testRestTemplate.exchange(
				"/api/v1/private/merchant/" + parentStoreCode + "/children", HttpMethod.GET,
				new HttpEntity<>(superadminHeaders), String.class);
		assertEquals(HttpStatus.OK, children.getStatusCode());
		assertTrue(children.getBody().contains(childStoreCode));
	}

	@Test
	public void storeAdminCannotCreateUserInUnrelatedStore() {
		String adminEmail = email("default-admin");
		createUser(adminEmail, "ADMIN", null, "DEFAULT", superadminHeaders);
		HttpHeaders adminHeaders = getHeader(adminEmail, "Password1");

		String attemptedEmail = email("other-store-user");
		ResponseEntity<String> response = createUserResponse(attemptedEmail, "ADMIN", otherStoreCode, "DEFAULT",
				adminHeaders);
		assertEquals(HttpStatus.UNAUTHORIZED, response.getStatusCode());

		ResponseEntity<String> users = testRestTemplate.exchange(
				"/api/v1/private/users?store=" + otherStoreCode + "&emailAddress=" + attemptedEmail,
				HttpMethod.GET, new HttpEntity<>(superadminHeaders), String.class);
		assertEquals(HttpStatus.OK, users.getStatusCode());
		assertFalse(users.getBody().contains(attemptedEmail));
	}

	@Test
	public void storeAdminCanCreateAndLoginToAdminInOwnStore() {
		String adminEmail = email("default-admin");
		createUser(adminEmail, "ADMIN", null, "DEFAULT", superadminHeaders);
		HttpHeaders adminHeaders = getHeader(adminEmail, "Password1");

		String createdEmail = email("own-store-user");
		ResponseEntity<ReadableUser> created = createUser(createdEmail, "ADMIN", null, "DEFAULT", adminHeaders);
		assertEquals(HttpStatus.OK, created.getStatusCode());
		assertNotNull(created.getBody());

		ResponseEntity<AuthenticationResponse> login = testRestTemplate.postForEntity("/api/v1/private/login",
				new HttpEntity<>(new AuthenticationRequest(createdEmail, "Password1")),
				AuthenticationResponse.class);
		assertEquals(HttpStatus.OK, login.getStatusCode());
		assertNotNull(login.getBody());
		assertNotNull(login.getBody().getToken());
	}

	@Test
	public void parentStoreAdminCanCreateUserInLinkedChildStore() {
		String parentAdminEmail = email("parent-admin");
		createUser(parentAdminEmail, "ADMIN", null, parentStoreCode, superadminHeaders);
		HttpHeaders parentAdminHeaders = getHeader(parentAdminEmail, "Password1");

		ResponseEntity<ReadableUser> created = createUser(email("child-user"), "ADMIN", childStoreCode,
				parentStoreCode, parentAdminHeaders);
		assertEquals(HttpStatus.OK, created.getStatusCode());
		assertNotNull(created.getBody());
	}

	@Test
	public void storeAdminCannotCreateSuperadminWithCaseOrWhitespaceVariants() {
		String adminEmail = email("default-admin");
		createUser(adminEmail, "ADMIN", null, "DEFAULT", superadminHeaders);
		HttpHeaders adminHeaders = getHeader(adminEmail, "Password1");

		for (String groupName : new String[] { "SUPERADMIN", "superadmin", " SUPERADMIN " }) {
			ResponseEntity<String> response = createUserResponse(email("escalation"), groupName, null,
					"DEFAULT", adminHeaders);
			assertEquals(HttpStatus.UNAUTHORIZED, response.getStatusCode());
		}
	}

	@Test
	public void lowercaseSuperadminUpdateIsUnauthorized() {
		String adminEmail = email("default-admin");
		createUser(adminEmail, "ADMIN", null, "DEFAULT", superadminHeaders);
		HttpHeaders adminHeaders = getHeader(adminEmail, "Password1");

		ReadableUser ownUser = createUser(email("own-user"), "ADMIN", null, "DEFAULT", adminHeaders).getBody();
		assertNotNull(ownUser);
		PersistableUser update = user(ownUser.getUserName(), "superadmin", null);
		ResponseEntity<String> response = testRestTemplate.exchange(
				"/api/v1/private/user/" + ownUser.getId() + "?store=DEFAULT", HttpMethod.PUT,
				new HttpEntity<>(update, adminHeaders), String.class);
		assertEquals(HttpStatus.UNAUTHORIZED, response.getStatusCode());
	}

	@Test
	public void superadminCanCreateSuperadminInAnotherStore() {
		ResponseEntity<ReadableUser> created = createUser(email("new-superadmin"), "SUPERADMIN", otherStoreCode,
				otherStoreCode, superadminHeaders);
		assertEquals(HttpStatus.OK, created.getStatusCode());
		assertNotNull(created.getBody());
	}

	@Test
	public void anonymousRequestCannotCreateUser() {
		ResponseEntity<String> response = createUserResponse(email("anonymous"), "ADMIN", null, "DEFAULT",
				new HttpHeaders());
		assertEquals(HttpStatus.UNAUTHORIZED, response.getStatusCode());
	}

	@Test
	public void adminCatalogueCannotCreateUser() {
		String catalogueEmail = email("catalogue-admin");
		createUser(catalogueEmail, "ADMIN_CATALOGUE", null, "DEFAULT", superadminHeaders);
		HttpHeaders catalogueHeaders = getHeader(catalogueEmail, "Password1");

		ResponseEntity<String> response = createUserResponse(email("catalogue-user"), "ADMIN", null, "DEFAULT",
				catalogueHeaders);
		assertEquals(HttpStatus.UNAUTHORIZED, response.getStatusCode());
	}

	private void createStore(String code, String parentCode, boolean retailer) {
		PersistableMerchantStore store = new PersistableMerchantStore();
		store.setCode(code);
		store.setName(code);
		store.setEmail(code + "@example.com");
		store.setPhone("5555555555");
		store.setCurrency("USD");
		store.setDefaultLanguage("en");
		store.setSupportedLanguages(Collections.singletonList("en"));
		store.setRetailer(retailer);
		store.setRetailerStore(parentCode);

		PersistableAddress address = new PersistableAddress();
		address.setAddress("1 Main Street");
		address.setCity("Los Angeles");
		address.setPostalCode("90001");
		address.setCountry("US");
		address.setStateProvince("CA");
		store.setAddress(address);

		ResponseEntity<String> response = testRestTemplate.exchange("/api/v1/private/store", HttpMethod.POST,
				new HttpEntity<>(store, superadminHeaders), String.class);
		assertTrue("Store creation failed for " + code + ": " + response.getBody(),
				response.getStatusCode().is2xxSuccessful());
	}

	private ResponseEntity<ReadableUser> createUser(String email, String group, String bodyStore, String queryStore,
			HttpHeaders headers) {
		return testRestTemplate.exchange("/api/v1/private/user/?store=" + queryStore, HttpMethod.POST,
				new HttpEntity<>(user(email, group, bodyStore), headers), ReadableUser.class);
	}

	private ResponseEntity<String> createUserResponse(String email, String group, String bodyStore, String queryStore,
			HttpHeaders headers) {
		return testRestTemplate.exchange("/api/v1/private/user/?store=" + queryStore, HttpMethod.POST,
				new HttpEntity<>(user(email, group, bodyStore), headers), String.class);
	}

	private PersistableUser user(String email, String groupName, String storeCode) {
		PersistableUser user = new PersistableUser();
		user.setUserName(email);
		user.setEmailAddress(email);
		user.setFirstName("Test");
		user.setLastName("User");
		user.setPassword("Password1");
		user.setRepeatPassword("Password1");
		user.setDefaultLanguage("en");
		user.setActive(true);
		user.setStore(storeCode);

		PersistableGroup group = new PersistableGroup();
		group.setName(groupName);
		user.setGroups(Collections.singletonList(group));
		return user;
	}

	private String email(String purpose) {
		return purpose + "-" + suffix + "@example.com";
	}
}
