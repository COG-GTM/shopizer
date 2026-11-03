package com.salesmanager.test.shop.integration.user;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import java.util.Arrays;

import org.junit.Assume;
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
import com.salesmanager.shop.model.references.PersistableAddress;
import com.salesmanager.shop.model.security.PersistableGroup;
import com.salesmanager.shop.model.store.PersistableMerchantStore;
import com.salesmanager.shop.model.user.PersistableUser;
import com.salesmanager.shop.model.user.ReadableUser;
import com.salesmanager.test.shop.common.ServicesTestSupport;

@RunWith(SpringRunner.class)
@SpringBootTest(classes = ShopApplication.class, webEnvironment = WebEnvironment.RANDOM_PORT)
public class UserStoreAuthorizationIntegrationTest extends ServicesTestSupport {

	private static final String PASSWORD = "Password1";
	private static final String STORE_B = "authzb";

	@Test
	public void enforcesStoreAndSuperadminBoundariesForUserChanges() {
		createStore();
		ReadableUser adminB = createUser("authz-adminb@test.com", "ADMIN", STORE_B);
		ReadableUser userB = createUser("authz-userb@test.com", "ADMIN", STORE_B);
		ReadableUser victimDel = createUser("authz-victim-del@test.com", "ADMIN", "DEFAULT");
		ReadableUser victimEn = createUser("authz-victim-en@test.com", "ADMIN", "DEFAULT");
		ReadableUser adminA = createUser("authz-admina@test.com", "ADMIN", "DEFAULT");
		ReadableUser adminAVictim = createUser("authz-admina-victim@test.com", "ADMIN", "DEFAULT");

		ResponseEntity<Void> crossStoreDelete = delete(adminB, victimDel.getId(), STORE_B);
		assertEquals(HttpStatus.INTERNAL_SERVER_ERROR, crossStoreDelete.getStatusCode());
		assertNotNull(getUser(victimDel.getId(), "DEFAULT").getBody());

		ResponseEntity<Void> crossStoreDisable = disable(adminB, victimEn.getId(), STORE_B, false);
		assertEquals(HttpStatus.NOT_FOUND, crossStoreDisable.getStatusCode());
		assertTrue(getUser(victimEn.getId(), "DEFAULT").getBody().isActive());

		ResponseEntity<Void> wrongStoreParameter = delete(adminB, victimDel.getId(), "DEFAULT");
		assertEquals(HttpStatus.UNAUTHORIZED, wrongStoreParameter.getStatusCode());
		assertNotNull(getUser(victimDel.getId(), "DEFAULT").getBody());

		ResponseEntity<Void> sameStoreDisable = disable(adminB, userB.getId(), STORE_B, false);
		assertEquals(HttpStatus.OK, sameStoreDisable.getStatusCode());
		assertEquals(Boolean.FALSE, getUser(userB.getId(), STORE_B).getBody().isActive());

		ResponseEntity<Void> sameStoreDelete = delete(adminB, userB.getId(), STORE_B);
		assertEquals(HttpStatus.OK, sameStoreDelete.getStatusCode());
		assertEquals(HttpStatus.NOT_FOUND, getUser(userB.getId(), STORE_B).getStatusCode());

		ResponseEntity<Void> adminADelete = delete(adminA, adminAVictim.getId(), "DEFAULT");
		assertEquals(HttpStatus.OK, adminADelete.getStatusCode());
		assertEquals(HttpStatus.NOT_FOUND, getUser(adminAVictim.getId(), "DEFAULT").getStatusCode());

		ResponseEntity<ReadableUser> superadminDeleteTarget = createUserResponse(
				"authz-sa-del@test.com", "SUPERADMIN", "DEFAULT");
		ResponseEntity<ReadableUser> superadminDisableTarget = createUserResponse(
				"authz-sa-en@test.com", "SUPERADMIN", "DEFAULT");
		Assume.assumeTrue("The user API does not accept users in the SUPERADMIN group",
				superadminDeleteTarget.getStatusCode() == HttpStatus.OK
						&& superadminDisableTarget.getStatusCode() == HttpStatus.OK);

		ReadableUser saDel = superadminDeleteTarget.getBody();
		ReadableUser saEn = superadminDisableTarget.getBody();
		assertNotNull(saDel);
		assertNotNull(saEn);

		ResponseEntity<Void> protectedDelete = delete(null, saDel.getId(), "DEFAULT");
		assertEquals(HttpStatus.INTERNAL_SERVER_ERROR, protectedDelete.getStatusCode());
		assertNotNull(getUser(saDel.getId(), "DEFAULT").getBody());

		ResponseEntity<Void> protectedDisable = disable(adminA, saEn.getId(), "DEFAULT", false);
		assertEquals(HttpStatus.UNAUTHORIZED, protectedDisable.getStatusCode());
		assertTrue(getUser(saEn.getId(), "DEFAULT").getBody().isActive());

		ResponseEntity<Void> superadminEnable = disable(null, adminB.getId(), STORE_B, true);
		assertEquals(HttpStatus.OK, superadminEnable.getStatusCode());
		assertTrue(getUser(adminB.getId(), STORE_B).getBody().isActive());
	}

	private void createStore() {
		PersistableAddress address = new PersistableAddress();
		address.setAddress("12 Authorization Avenue");
		address.setPostalCode("12345");
		address.setCountry("US");
		address.setCity("Springfield");
		address.setStateProvince("FL");

		PersistableMerchantStore store = new PersistableMerchantStore();
		store.setCode(STORE_B);
		store.setCurrency("CAD");
		store.setDefaultLanguage("en");
		store.setEmail("authzb@test.com");
		store.setName(STORE_B);
		store.setPhone("444-555-6666");
		store.setSupportedLanguages(Arrays.asList("en"));
		store.setAddress(address);

		ResponseEntity<Void> response = testRestTemplate.exchange("/api/v1/private/store",
				HttpMethod.POST, new HttpEntity<>(store, getHeader()), Void.class);
		assertTrue("Create store returned " + response.getStatusCode(),
				response.getStatusCode().is2xxSuccessful());
	}

	private ReadableUser createUser(String email, String group, String store) {
		ResponseEntity<ReadableUser> response = createUserResponse(email, group, store);
		assertEquals("Create user " + email + " returned " + response.getStatusCode(),
				HttpStatus.OK, response.getStatusCode());
		assertNotNull(response.getBody());
		return response.getBody();
	}

	private ResponseEntity<ReadableUser> createUserResponse(String email, String group, String store) {
		PersistableUser user = new PersistableUser();
		user.setDefaultLanguage("en");
		user.setEmailAddress(email);
		user.setFirstName("Authz");
		user.setLastName("Test");
		user.setUserName(email);
		user.setPassword(PASSWORD);
		user.setRepeatPassword(PASSWORD);
		user.setActive(true);
		PersistableGroup userGroup = new PersistableGroup();
		userGroup.setName(group);
		user.getGroups().add(userGroup);

		String path = "/api/v1/private/user/?store=" + store;
		return testRestTemplate.exchange(path, HttpMethod.POST,
				new HttpEntity<>(user, getHeader()), ReadableUser.class);
	}

	private ResponseEntity<Void> delete(ReadableUser caller, Long targetId, String store) {
		return testRestTemplate.exchange("/api/v1/private/user/" + targetId + "?store=" + store,
				HttpMethod.DELETE, new HttpEntity<>(caller == null ? getHeader() : getHeader(caller.getUserName(), PASSWORD)),
				Void.class);
	}

	private ResponseEntity<Void> disable(ReadableUser caller, Long targetId, String store, boolean active) {
		PersistableUser update = new PersistableUser();
		update.setActive(active);
		return testRestTemplate.exchange("/api/v1/private/user/" + targetId + "/enabled?store=" + store,
				HttpMethod.PATCH,
				new HttpEntity<>(update, caller == null ? getHeader() : getHeader(caller.getUserName(), PASSWORD)),
				Void.class);
	}

	private ResponseEntity<ReadableUser> getUser(Long id, String store) {
		return testRestTemplate.exchange("/api/v1/private/users/" + id + "?store=" + store,
				HttpMethod.GET, new HttpEntity<>(getHeader()), ReadableUser.class);
	}
}
