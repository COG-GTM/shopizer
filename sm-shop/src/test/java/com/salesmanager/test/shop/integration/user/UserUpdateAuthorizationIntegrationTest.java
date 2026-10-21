package com.salesmanager.test.shop.integration.user;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import java.util.Arrays;
import java.util.Collections;
import java.util.UUID;

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

@SpringBootTest(classes = ShopApplication.class, webEnvironment = WebEnvironment.RANDOM_PORT)
@RunWith(SpringRunner.class)
public class UserUpdateAuthorizationIntegrationTest extends ServicesTestSupport {

	private static final String ADMIN_PASSWORD = "Password1";

	@Inject
	private TestRestTemplate testRestTemplate;

	@Test
	public void updateWithoutAuthorizationReturnsUnauthorized() {
		ReadableUser target = createUser("ADMIN_CATALOGUE", null);

		ResponseEntity<ReadableUser> response = testRestTemplate.exchange("/api/v1/private/user/" + target.getId(),
				HttpMethod.PUT, new HttpEntity<>(updateBody(target.getUserName())), ReadableUser.class);

		assertEquals(HttpStatus.UNAUTHORIZED, response.getStatusCode());
	}

	@Test
	public void updateWithInvalidBearerTokenIsRejected() {
		ReadableUser target = createUser("ADMIN_CATALOGUE", null);
		HttpHeaders headers = new HttpHeaders();
		headers.setBearerAuth("not.a.valid.token");

		ResponseEntity<ReadableUser> response = testRestTemplate.exchange("/api/v1/private/user/" + target.getId(),
				HttpMethod.PUT, new HttpEntity<>(updateBody(target.getUserName()), headers), ReadableUser.class);

		assertTrue(response.getStatusCode().isError());
	}

	@Test
	public void adminCanChangeCatalogueUserProfileButNotGroup() {
		ReadableUser admin = createUser("ADMIN", null);
		ReadableUser target = createUser("ADMIN_CATALOGUE", null);
		PersistableUser body = updateBody(target.getUserName());
		body.setFirstName("Changed by admin");
		body.getGroups().add(new PersistableGroup("ADMIN"));

		ResponseEntity<ReadableUser> response = testRestTemplate.exchange("/api/v1/private/user/" + target.getId(),
				HttpMethod.PUT, new HttpEntity<>(body, getHeader(admin.getUserName(), ADMIN_PASSWORD)),
				ReadableUser.class);

		assertEquals(HttpStatus.OK, response.getStatusCode());
		ReadableUser saved = readUser(target.getId(), null);
		assertEquals("Changed by admin", saved.getFirstName());
		assertEquals(Collections.singletonList("ADMIN_CATALOGUE"), groupNames(saved));
		assertEquals("DEFAULT", saved.getMerchant());
	}

	@Test
	public void catalogueUserCannotEditAnotherCatalogueUser() {
		ReadableUser caller = createUser("ADMIN_CATALOGUE", null);
		ReadableUser target = createUser("ADMIN_CATALOGUE", null);
		PersistableUser body = updateBody(target.getUserName());
		body.setFirstName("Must not be saved");

		ResponseEntity<ReadableUser> response = testRestTemplate.exchange("/api/v1/private/user/" + target.getId(),
				HttpMethod.PUT, new HttpEntity<>(body, getHeader(caller.getUserName(), ADMIN_PASSWORD)),
				ReadableUser.class);

		assertEquals(HttpStatus.UNAUTHORIZED, response.getStatusCode());
		assertEquals("Test", readUser(target.getId(), null).getFirstName());
	}

	@Test
	public void orderAdminCanSelfEditWithoutChangingGroup() {
		ReadableUser user = createUser("ADMIN_ORDER", null);
		PersistableUser body = updateBody(user.getUserName());
		body.setFirstName("Self edited");
		body.getGroups().add(new PersistableGroup("ADMIN"));

		ResponseEntity<ReadableUser> response = testRestTemplate.exchange("/api/v1/private/user/" + user.getId(),
				HttpMethod.PUT, new HttpEntity<>(body, getHeader(user.getUserName(), ADMIN_PASSWORD)),
				ReadableUser.class);

		assertEquals(HttpStatus.OK, response.getStatusCode());
		ReadableUser saved = readUser(user.getId(), null);
		assertEquals("Self edited", saved.getFirstName());
		assertEquals(Collections.singletonList("ADMIN_ORDER"), groupNames(saved));
	}

	@Test
	public void adminCannotAssignSuperadminGroup() {
		ReadableUser admin = createUser("ADMIN", null);
		ReadableUser target = createUser("ADMIN_CATALOGUE", null);
		PersistableUser body = updateBody(target.getUserName());
		body.getGroups().add(new PersistableGroup("SUPERADMIN"));

		ResponseEntity<ReadableUser> response = testRestTemplate.exchange("/api/v1/private/user/" + target.getId(),
				HttpMethod.PUT, new HttpEntity<>(body, getHeader(admin.getUserName(), ADMIN_PASSWORD)),
				ReadableUser.class);

		assertEquals(HttpStatus.UNAUTHORIZED, response.getStatusCode());
		assertEquals(Collections.singletonList("ADMIN_CATALOGUE"), groupNames(readUser(target.getId(), null)));
	}

	@Test
	public void superadminCanMoveDefaultStoreUserToAnotherStore() {
		ReadableUser target = createUser("ADMIN_CATALOGUE", null);
		String otherStore = createStore();
		PersistableUser body = updateBody(target.getUserName());
		body.setStore(otherStore);

		ResponseEntity<ReadableUser> response = testRestTemplate.exchange("/api/v1/private/user/" + target.getId(),
				HttpMethod.PUT, new HttpEntity<>(body, getHeader()), ReadableUser.class);

		assertEquals(HttpStatus.OK, response.getStatusCode());
		assertEquals(otherStore, readUser(target.getId(), otherStore).getMerchant());
	}

	@Test
	public void superadminUpdateOfMissingIdIsRejected() {
		PersistableUser body = updateBody("u-" + UUID.randomUUID() + "@shopizer.com");

		ResponseEntity<ReadableUser> response = testRestTemplate.exchange("/api/v1/private/user/999999",
				HttpMethod.PUT, new HttpEntity<>(body, getHeader()), ReadableUser.class);

		assertTrue(response.getStatusCode().isError());
	}

	@Test
	public void selfEditPutIgnoresPasswordChange() {
		ReadableUser user = createUser("ADMIN_CATALOGUE", null);
		PersistableUser body = updateBody(user.getUserName());
		body.setPassword("Password9");

		ResponseEntity<ReadableUser> response = testRestTemplate.exchange("/api/v1/private/user/" + user.getId(),
				HttpMethod.PUT,
				new HttpEntity<>(body, getHeader(user.getUserName(), ADMIN_PASSWORD)), ReadableUser.class);

		assertEquals(HttpStatus.OK, response.getStatusCode());
		assertEquals(HttpStatus.OK, login(user.getUserName(), ADMIN_PASSWORD).getStatusCode());
		assertEquals(HttpStatus.UNAUTHORIZED, login(user.getUserName(), "Password9").getStatusCode());
	}

	@Test
	public void superadminCanChangeAdminGroup() {
		ReadableUser target = createUser("ADMIN", null);
		PersistableUser body = updateBody(target.getUserName());
		body.getGroups().add(new PersistableGroup("ADMIN_ORDER"));

		ResponseEntity<ReadableUser> response = testRestTemplate.exchange("/api/v1/private/user/" + target.getId(),
				HttpMethod.PUT, new HttpEntity<>(body, getHeader()), ReadableUser.class);

		assertEquals(HttpStatus.OK, response.getStatusCode());
		assertEquals(Collections.singletonList("ADMIN_ORDER"), groupNames(readUser(target.getId(), null)));
	}

	@Test
	public void adminCanUpdatePeerAdminInSameStore() {
		ReadableUser admin = createUser("ADMIN", null);
		ReadableUser target = createUser("ADMIN", null);
		PersistableUser body = updateBody(target.getUserName());
		body.setFirstName("Peer updated");

		ResponseEntity<ReadableUser> response = testRestTemplate.exchange("/api/v1/private/user/" + target.getId(),
				HttpMethod.PUT, new HttpEntity<>(body, getHeader(admin.getUserName(), ADMIN_PASSWORD)),
				ReadableUser.class);

		assertEquals(HttpStatus.OK, response.getStatusCode());
		assertEquals("Peer updated", readUser(target.getId(), null).getFirstName());
	}

	private ReadableUser createUser(String groupName, String storeCode) {
		String username = "u-" + UUID.randomUUID() + "@shopizer.com";
		PersistableUser user = new PersistableUser();
		user.setDefaultLanguage("en");
		user.setEmailAddress(username);
		user.setFirstName("Test");
		user.setLastName("User");
		user.setUserName(username);
		user.setPassword(ADMIN_PASSWORD);
		user.setRepeatPassword(ADMIN_PASSWORD);
		if (storeCode != null) {
			user.setStore(storeCode);
		}
		user.getGroups().add(new PersistableGroup(groupName));

		String path = "/api/v1/private/user/";
		if (storeCode != null) {
			path += "?store=" + storeCode;
		}
		ResponseEntity<ReadableUser> response = testRestTemplate.exchange(path, HttpMethod.POST,
				new HttpEntity<>(user, getHeader()), ReadableUser.class);
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

	private ReadableUser readUser(Long id, String storeCode) {
		String path = "/api/v1/private/users/" + id;
		if (storeCode != null) {
			path += "?store=" + storeCode;
		}
		ResponseEntity<ReadableUser> response = testRestTemplate.exchange(path, HttpMethod.GET,
				new HttpEntity<>(getHeader()), ReadableUser.class);
		assertEquals(HttpStatus.OK, response.getStatusCode());
		assertNotNull(response.getBody());
		return response.getBody();
	}

	private ResponseEntity<AuthenticationResponse> login(String username, String password) {
		return testRestTemplate.postForEntity("/api/v1/private/login",
				new HttpEntity<>(new AuthenticationRequest(username, password)), AuthenticationResponse.class);
	}

	private String createStore() {
		String code = "s" + UUID.randomUUID().toString().replace("-", "").substring(0, 8);
		PersistableAddress address = new PersistableAddress();
		address.setAddress("121212 simple address");
		address.setPostalCode("12345");
		address.setCountry("US");
		address.setCity("FT LD");
		address.setStateProvince("FL");
		PersistableMerchantStore store = new PersistableMerchantStore();
		store.setCode(code);
		store.setCurrency("CAD");
		store.setDefaultLanguage("en");
		store.setEmail("test-" + code + "@shopizer.com");
		store.setName(code);
		store.setPhone("444-555-6666");
		store.setSupportedLanguages(Arrays.asList("en"));
		store.setAddress(address);

		ResponseEntity<Void> response = testRestTemplate.exchange("/api/v1/private/store/", HttpMethod.POST,
				new HttpEntity<>(store, getHeader()), Void.class);
		assertEquals(HttpStatus.OK, response.getStatusCode());
		return code;
	}

	private java.util.List<String> groupNames(ReadableUser user) {
		return user.getGroups().stream().map(ReadableGroup::getName).collect(java.util.stream.Collectors.toList());
	}
}
