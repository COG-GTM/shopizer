package com.salesmanager.test.shop.integration.user;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import java.util.Arrays;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.SpringBootTest.WebEnvironment;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.test.context.junit4.SpringRunner;

import com.salesmanager.shop.application.ShopApplication;
import com.salesmanager.shop.model.references.PersistableAddress;
import com.salesmanager.shop.model.security.PersistableGroup;
import com.salesmanager.shop.model.security.ReadableGroup;
import com.salesmanager.shop.model.store.PersistableMerchantStore;
import com.salesmanager.shop.model.user.PersistableUser;
import com.salesmanager.shop.model.user.ReadableUser;
import com.salesmanager.shop.store.security.AuthenticationRequest;
import com.salesmanager.shop.store.security.AuthenticationResponse;
import com.salesmanager.test.shop.common.ServicesTestSupport;

/**
 * PUT /api/v1/private/user/{id} through the real security chain, facade and H2.
 */
@SpringBootTest(classes = ShopApplication.class, webEnvironment = WebEnvironment.RANDOM_PORT)
@RunWith(SpringRunner.class)
public class UserUpdateAuthorizationIntegrationTest extends ServicesTestSupport {

	private static final Long SUPERADMIN_ID = 1L;
	private static final String PASSWORD = "Password1";

	@Test
	public void updateWithoutTokenIsRejected() {
		ReadableUser target = createUser("ADMIN_CATALOGUE", "DEFAULT");
		HttpHeaders headers = new HttpHeaders();
		headers.setContentType(MediaType.APPLICATION_JSON);

		ResponseEntity<String> response = put(target.getId(), body(target, "DEFAULT", "ADMIN_CATALOGUE"), headers);

		assertEquals(HttpStatus.UNAUTHORIZED, response.getStatusCode());
	}

	@Test
	public void updateWithInvalidTokenIsRejected() {
		ReadableUser target = createUser("ADMIN_CATALOGUE", "DEFAULT");
		HttpHeaders headers = new HttpHeaders();
		headers.setContentType(MediaType.APPLICATION_JSON);
		headers.add("Authorization", "Bearer not-a-valid-jwt");

		PersistableUser update = body(target, "DEFAULT", "ADMIN_CATALOGUE");
		update.setFirstName("Forged");

		ResponseEntity<String> response = put(target.getId(), update, headers);

		assertFalse(response.getStatusCode().is2xxSuccessful());
		assertEquals("Test", fetch(target.getId(), "DEFAULT").getFirstName());
	}

	@Test
	public void storeAdminCannotGrantSuperadminGroup() {
		ReadableUser caller = createUser("ADMIN", "DEFAULT");
		ReadableUser target = createUser("ADMIN_CATALOGUE", "DEFAULT");

		ResponseEntity<String> response = put(target.getId(), body(target, "DEFAULT", "SUPERADMIN"),
				getHeader(caller.getUserName(), PASSWORD));

		assertEquals(HttpStatus.UNAUTHORIZED, response.getStatusCode());
		assertEquals(Arrays.asList("ADMIN_CATALOGUE"), groups(fetch(target.getId(), "DEFAULT")));
	}

	@Test
	public void storeAdminCannotUpdateUserOfUnrelatedStore() {
		String otherStore = createStore();
		ReadableUser caller = createUser("ADMIN", "DEFAULT");
		ReadableUser target = createUser("ADMIN_CATALOGUE", otherStore);
		PersistableUser update = body(target, otherStore, "ADMIN_CATALOGUE");
		update.setFirstName("Hijacked");

		ResponseEntity<String> response = put(target.getId(), update, otherStore,
				getHeader(caller.getUserName(), PASSWORD));

		assertEquals(HttpStatus.UNAUTHORIZED, response.getStatusCode());
		assertEquals("Test", fetch(target.getId(), otherStore).getFirstName());
	}

	@Test
	public void userCannotMoveOwnAccountToAnotherStore() {
		String otherStore = createStore();
		ReadableUser self = createUser("ADMIN", "DEFAULT");

		ResponseEntity<String> response = put(self.getId(), body(self, otherStore, "ADMIN"),
				getHeader(self.getUserName(), PASSWORD));

		assertFalse(response.getStatusCode().is2xxSuccessful());
		assertEquals("DEFAULT", fetch(self.getId(), "DEFAULT").getMerchant());
	}

	@Test
	public void lowPrivilegeSelfUpdateCannotEscalateGroupsButCanChangePassword() {
		ReadableUser self = createUser("ADMIN_CATALOGUE", "DEFAULT");
		PersistableUser update = body(self, "DEFAULT", "ADMIN");
		update.setPassword("Password9");
		update.setRepeatPassword("Password9");

		ResponseEntity<String> response = put(self.getId(), update, getHeader(self.getUserName(), PASSWORD));

		assertEquals(HttpStatus.OK, response.getStatusCode());
		assertEquals(Arrays.asList("ADMIN_CATALOGUE"), groups(fetch(self.getId(), "DEFAULT")));
		assertEquals(HttpStatus.OK, login(self.getUserName(), "Password9").getStatusCode());
		assertEquals(HttpStatus.UNAUTHORIZED, login(self.getUserName(), PASSWORD).getStatusCode());
	}

	@Test
	public void catalogueAdminCannotResetSuperadminPassword() {
		ReadableUser caller = createUser("ADMIN_CATALOGUE", "DEFAULT");
		ReadableUser admin = fetch(SUPERADMIN_ID, "DEFAULT");
		PersistableUser update = body(admin, "DEFAULT", "ADMIN");
		update.setPassword("Attacker1!");
		update.setRepeatPassword("Attacker1!");

		ResponseEntity<String> response = put(SUPERADMIN_ID, update, getHeader(caller.getUserName(), PASSWORD));

		assertEquals(HttpStatus.UNAUTHORIZED, response.getStatusCode());
		assertEquals(HttpStatus.OK, login("admin@shopizer.com", "password").getStatusCode());
	}

	@Test
	public void superadminCanChangeGroupsOfUserInAnyStore() {
		String otherStore = createStore();
		ReadableUser target = createUser("ADMIN_CATALOGUE", otherStore);

		ResponseEntity<String> response = put(target.getId(), body(target, otherStore, "ADMIN_ORDER"), otherStore,
				getHeader());

		assertEquals(HttpStatus.OK, response.getStatusCode());
		assertEquals(Arrays.asList("ADMIN_ORDER"), groups(fetch(target.getId(), otherStore)));
	}

	private ResponseEntity<AuthenticationResponse> login(String user, String password) {
		return testRestTemplate.postForEntity("/api/v1/private/login",
				new HttpEntity<>(new AuthenticationRequest(user, password)), AuthenticationResponse.class);
	}

	private ResponseEntity<String> put(Long id, PersistableUser user, HttpHeaders headers) {
		return put(id, user, "DEFAULT", headers);
	}

	private ResponseEntity<String> put(Long id, PersistableUser user, String store, HttpHeaders headers) {
		return testRestTemplate.exchange("/api/v1/private/user/" + id + "?store=" + store, HttpMethod.PUT,
				new HttpEntity<>(user, headers), String.class);
	}

	private ReadableUser fetch(Long id, String store) {
		ResponseEntity<ReadableUser> response = testRestTemplate.exchange(
				"/api/v1/private/users/" + id + "?store=" + store, HttpMethod.GET, new HttpEntity<>(getHeader()),
				ReadableUser.class);
		assertEquals(HttpStatus.OK, response.getStatusCode());
		return response.getBody();
	}

	private List<String> groups(ReadableUser user) {
		return user.getGroups().stream().map(ReadableGroup::getName).collect(Collectors.toList());
	}

	private ReadableUser createUser(String group, String store) {
		String username = "authz-" + UUID.randomUUID() + "@test.com";
		PersistableUser user = new PersistableUser();
		user.setUserName(username);
		user.setEmailAddress(username);
		user.setFirstName("Test");
		user.setLastName("User");
		user.setDefaultLanguage("en");
		user.setStore(store);
		user.setActive(true);
		user.setPassword(PASSWORD);
		user.setRepeatPassword(PASSWORD);
		PersistableGroup g = new PersistableGroup();
		g.setName(group);
		user.getGroups().add(g);

		ResponseEntity<ReadableUser> response = testRestTemplate.exchange("/api/v1/private/user/?store=" + store,
				HttpMethod.POST, new HttpEntity<>(user, getHeader()), ReadableUser.class);
		assertEquals(HttpStatus.OK, response.getStatusCode());
		assertNotNull(response.getBody().getId());
		return response.getBody();
	}

	private PersistableUser body(ReadableUser target, String store, String group) {
		PersistableUser user = new PersistableUser();
		user.setUserName(target.getUserName());
		user.setEmailAddress(target.getEmailAddress());
		user.setFirstName(target.getFirstName());
		user.setLastName(target.getLastName());
		user.setDefaultLanguage("en");
		user.setStore(store);
		user.setActive(true);
		PersistableGroup g = new PersistableGroup();
		g.setName(group);
		user.getGroups().add(g);
		return user;
	}

	private String createStore() {
		String code = "authz" + UUID.randomUUID().toString().replace("-", "").substring(0, 10);
		PersistableAddress address = new PersistableAddress();
		address.setAddress("1 Test street");
		address.setPostalCode("12345");
		address.setCountry("US");
		address.setCity("Miami");
		address.setStateProvince("FL");

		PersistableMerchantStore store = new PersistableMerchantStore();
		store.setCode(code);
		store.setCurrency("CAD");
		store.setDefaultLanguage("en");
		store.setEmail("store@test.com");
		store.setName(code);
		store.setPhone("444-555-6666");
		store.setSupportedLanguages(Arrays.asList("en"));
		store.setAddress(address);

		ResponseEntity<String> response = testRestTemplate.exchange("/api/v1/private/store", HttpMethod.POST,
				new HttpEntity<>(store, getHeader()), String.class);
		assertTrue("store creation failed: " + response, response.getStatusCode().is2xxSuccessful());
		return code;
	}
}
