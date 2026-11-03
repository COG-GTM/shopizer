package com.salesmanager.test.shop.uat;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import java.util.Arrays;

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
import com.salesmanager.shop.model.user.ReadableUserList;
import com.salesmanager.test.shop.common.ServicesTestSupport;

@RunWith(SpringRunner.class)
@SpringBootTest(classes = ShopApplication.class, webEnvironment = WebEnvironment.RANDOM_PORT)
public class UserAdministrationJourneyUATTest extends ServicesTestSupport {

	private static final String STORE = "authzc";
	private static final String PASSWORD = "Password1";

	@Test
	public void storeAdministratorCanManageOnlyItsStoreUsers() {
		ensureStore();
		ReadableUser storeAdmin = ensureUser("authz-adminc@test.com", "ADMIN", STORE);
		ReadableUser storeMember = ensureUser("authz-memberc@test.com", "ADMIN", STORE);
		ReadableUser defaultDeleteVictim = ensureUser("authz-default-delete-victim@test.com", "ADMIN", "DEFAULT");
		ReadableUser defaultDisableVictim = ensureUser("authz-default-disable-victim@test.com", "ADMIN", "DEFAULT");

		HttpEntity<String> storeAdminRequest = new HttpEntity<>(getHeader(storeAdmin.getUserName(), PASSWORD));
		ResponseEntity<ReadableUserList> listResponse = testRestTemplate.exchange(
				"/api/v1/private/users?store=" + STORE, HttpMethod.GET,
				storeAdminRequest, ReadableUserList.class);
		assertEquals(HttpStatus.OK, listResponse.getStatusCode());
		assertNotNull(listResponse.getBody());
		assertTrue(listResponse.getBody().getData().stream()
				.allMatch(user -> STORE.equals(user.getMerchant())));
		assertTrue(listResponse.getBody().getData().stream()
				.noneMatch(user -> "admin@shopizer.com".equals(user.getUserName())));

		ResponseEntity<Void> disable = setActive(storeAdmin, storeMember, false, STORE);
		assertEquals(HttpStatus.OK, disable.getStatusCode());
		assertEquals(Boolean.FALSE, getUser(storeMember.getId(), STORE).getBody().isActive());

		ResponseEntity<Void> enable = setActive(storeAdmin, storeMember, true, STORE);
		assertEquals(HttpStatus.OK, enable.getStatusCode());
		assertTrue(getUser(storeMember.getId(), STORE).getBody().isActive());

		ResponseEntity<Void> deleteMember = delete(storeAdmin, storeMember.getId(), STORE);
		assertEquals(HttpStatus.OK, deleteMember.getStatusCode());
		assertEquals(HttpStatus.NOT_FOUND, getUser(storeMember.getId(), STORE).getStatusCode());

		ResponseEntity<Void> crossStoreDelete = delete(storeAdmin, defaultDeleteVictim.getId(), STORE);
		assertEquals(HttpStatus.INTERNAL_SERVER_ERROR, crossStoreDelete.getStatusCode());
		assertNotNull(getUser(defaultDeleteVictim.getId(), "DEFAULT").getBody());

		ResponseEntity<Void> crossStoreDisable = setActive(storeAdmin, defaultDisableVictim, false, STORE);
		assertEquals(HttpStatus.NOT_FOUND, crossStoreDisable.getStatusCode());
		assertTrue(getUser(defaultDisableVictim.getId(), "DEFAULT").getBody().isActive());

		ResponseEntity<ReadableUser> profile = testRestTemplate.exchange(
				"/api/v1/private/user/profile", HttpMethod.GET,
				new HttpEntity<>(getHeader()), ReadableUser.class);
		assertEquals(HttpStatus.OK, profile.getStatusCode());
		assertTrue(profile.getBody().isActive());
	}

	private void ensureStore() {
		ResponseEntity<String> existing = testRestTemplate.exchange(
				"/api/v1/private/store/" + STORE, HttpMethod.GET,
				new HttpEntity<>(getHeader()), String.class);
		if (existing.getStatusCode().is2xxSuccessful()) {
			return;
		}

		PersistableAddress address = new PersistableAddress();
		address.setAddress("13 Authz Way");
		address.setPostalCode("12345");
		address.setCountry("US");
		address.setCity("Springfield");
		address.setStateProvince("FL");

		PersistableMerchantStore store = new PersistableMerchantStore();
		store.setCode(STORE);
		store.setCurrency("CAD");
		store.setDefaultLanguage("en");
		store.setEmail("authzc@test.com");
		store.setName(STORE);
		store.setPhone("444-555-6666");
		store.setSupportedLanguages(Arrays.asList("en"));
		store.setAddress(address);
		ResponseEntity<Void> created = testRestTemplate.exchange("/api/v1/private/store",
				HttpMethod.POST, new HttpEntity<>(store, getHeader()), Void.class);
		assertEquals(HttpStatus.OK, created.getStatusCode());
	}

	private ReadableUser ensureUser(String email, String group, String store) {
		ResponseEntity<ReadableUserList> list = testRestTemplate.exchange(
				"/api/v1/private/users?store=" + store + "&emailAddress=" + email,
				HttpMethod.GET, new HttpEntity<>(getHeader()), ReadableUserList.class);
		if (list.getBody() != null && !list.getBody().getData().isEmpty()) {
			return list.getBody().getData().get(0);
		}

		PersistableUser user = new PersistableUser();
		user.setDefaultLanguage("en");
		user.setEmailAddress(email);
		user.setFirstName("Authz");
		user.setLastName("Journey");
		user.setUserName(email);
		user.setPassword(PASSWORD);
		user.setRepeatPassword(PASSWORD);
		user.setActive(true);
		PersistableGroup userGroup = new PersistableGroup();
		userGroup.setName(group);
		user.getGroups().add(userGroup);
		ResponseEntity<ReadableUser> response = testRestTemplate.exchange(
				"/api/v1/private/user/?store=" + store, HttpMethod.POST,
				new HttpEntity<>(user, getHeader()), ReadableUser.class);
		assertEquals(HttpStatus.OK, response.getStatusCode());
		assertNotNull(response.getBody());
		return response.getBody();
	}

	private ResponseEntity<ReadableUser> getUser(Long id, String store) {
		return testRestTemplate.exchange("/api/v1/private/users/" + id + "?store=" + store,
				HttpMethod.GET, new HttpEntity<>(getHeader()), ReadableUser.class);
	}

	private ResponseEntity<Void> setActive(ReadableUser caller, ReadableUser target, boolean active, String store) {
		PersistableUser update = new PersistableUser();
		update.setActive(active);
		return testRestTemplate.exchange("/api/v1/private/user/" + target.getId() + "/enabled?store=" + store,
				HttpMethod.PATCH,
				new HttpEntity<>(update, getHeader(caller.getUserName(), PASSWORD)), Void.class);
	}

	private ResponseEntity<Void> delete(ReadableUser caller, Long id, String store) {
		return testRestTemplate.exchange("/api/v1/private/user/" + id + "?store=" + store,
				HttpMethod.DELETE, new HttpEntity<>(getHeader(caller.getUserName(), PASSWORD)), Void.class);
	}
}
