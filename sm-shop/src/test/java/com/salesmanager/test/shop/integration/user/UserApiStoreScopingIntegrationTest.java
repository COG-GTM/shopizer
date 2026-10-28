package com.salesmanager.test.shop.integration.user;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.is;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

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

import com.salesmanager.core.model.merchant.MerchantStore;
import com.salesmanager.shop.application.ShopApplication;
import com.salesmanager.shop.model.references.PersistableAddress;
import com.salesmanager.shop.model.security.PersistableGroup;
import com.salesmanager.shop.model.store.PersistableMerchantStore;
import com.salesmanager.shop.model.user.PersistableUser;
import com.salesmanager.shop.model.user.ReadableUser;
import com.salesmanager.test.shop.common.ServicesTestSupport;

/**
 * An admin of one store must not be able to delete or disable users of
 * another store, nor the superadmin.
 */
@SpringBootTest(classes = ShopApplication.class, webEnvironment = WebEnvironment.RANDOM_PORT)
@RunWith(SpringRunner.class)
public class UserApiStoreScopingIntegrationTest extends ServicesTestSupport {

	private static final String OTHER_STORE = "globex";
	private static final String PASSWORD = "Password1";

	@Test
	public void storeAdminCannotDeleteOrDisableUsersOfOtherStores() throws Exception {

		HttpHeaders superadmin = getHeader();
		createStore(OTHER_STORE, superadmin);

		ReadableUser superadminUser = profile(superadmin);
		ReadableUser bob = createUser("bob@acme.com", MerchantStore.DEFAULT_STORE, superadmin);
		createUser("mallory@globex.com", OTHER_STORE, superadmin);
		ReadableUser carol = createUser("carol@globex.com", OTHER_STORE, superadmin);

		HttpHeaders mallory = getHeader("mallory@globex.com", PASSWORD);

		// user of another store
		assertThat(delete(bob.getId(), OTHER_STORE, mallory), is(HttpStatus.NOT_FOUND));
		assertThat(setEnabled(bob.getId(), false, OTHER_STORE, mallory), is(HttpStatus.NOT_FOUND));

		// superadmin
		assertThat(delete(superadminUser.getId(), OTHER_STORE, mallory), is(HttpStatus.NOT_FOUND));
		assertThat(setEnabled(superadminUser.getId(), false, OTHER_STORE, mallory), is(HttpStatus.NOT_FOUND));

		// targeting the other store directly is rejected by store authorization
		assertThat(delete(bob.getId(), MerchantStore.DEFAULT_STORE, mallory), is(HttpStatus.UNAUTHORIZED));
		assertThat(setEnabled(bob.getId(), false, MerchantStore.DEFAULT_STORE, mallory), is(HttpStatus.UNAUTHORIZED));

		// targets are untouched
		assertTrue(get(bob.getId(), MerchantStore.DEFAULT_STORE, superadmin).isActive());
		assertTrue(get(superadminUser.getId(), MerchantStore.DEFAULT_STORE, superadmin).isActive());

		// the superadmin itself can never be deleted
		assertThat(delete(superadminUser.getId(), MerchantStore.DEFAULT_STORE, superadmin), is(HttpStatus.BAD_REQUEST));
		assertTrue(get(superadminUser.getId(), MerchantStore.DEFAULT_STORE, superadmin).isActive());

		// an admin can still manage users of its own store
		assertThat(setEnabled(carol.getId(), false, OTHER_STORE, mallory), is(HttpStatus.OK));
		assertThat(delete(carol.getId(), OTHER_STORE, mallory), is(HttpStatus.OK));
	}

	private void createStore(String code, HttpHeaders headers) {
		PersistableAddress address = new PersistableAddress();
		address.setAddress("1 Globex way");
		address.setPostalCode("12345");
		address.setCountry("US");
		address.setCity("Springfield");
		address.setStateProvince("FL");

		PersistableMerchantStore store = new PersistableMerchantStore();
		store.setCode(code);
		store.setCurrency("CAD");
		store.setDefaultLanguage("en");
		store.setEmail("store@" + code + ".com");
		store.setName(code);
		store.setPhone("444-555-6666");
		store.setSupportedLanguages(Arrays.asList("en"));
		store.setAddress(address);

		ResponseEntity<Void> response = testRestTemplate.exchange("/api/v1/private/store/", HttpMethod.POST,
				new HttpEntity<>(store, headers), Void.class);
		assertThat(response.getStatusCode(), is(HttpStatus.OK));
	}

	private ReadableUser createUser(String userName, String store, HttpHeaders headers) {
		PersistableUser user = new PersistableUser();
		user.setDefaultLanguage("en");
		user.setEmailAddress(userName);
		user.setFirstName("Test");
		user.setLastName("User");
		user.setUserName(userName);
		user.setPassword(PASSWORD);
		user.setRepeatPassword(PASSWORD);
		user.setActive(true);
		PersistableGroup group = new PersistableGroup();
		group.setName("ADMIN");
		user.getGroups().add(group);

		ResponseEntity<ReadableUser> response = testRestTemplate.exchange("/api/v1/private/user/?store=" + store,
				HttpMethod.POST, new HttpEntity<>(user, headers), ReadableUser.class);
		assertThat(response.getStatusCode(), is(HttpStatus.OK));
		assertNotNull(response.getBody());
		return response.getBody();
	}

	private ReadableUser profile(HttpHeaders headers) {
		ResponseEntity<ReadableUser> response = testRestTemplate.exchange("/api/v1/private/user/profile",
				HttpMethod.GET, new HttpEntity<>(headers), ReadableUser.class);
		assertThat(response.getStatusCode(), is(HttpStatus.OK));
		return response.getBody();
	}

	private ReadableUser get(Long id, String store, HttpHeaders headers) {
		ResponseEntity<ReadableUser> response = testRestTemplate.exchange(
				"/api/v1/private/users/" + id + "?store=" + store, HttpMethod.GET, new HttpEntity<>(headers),
				ReadableUser.class);
		assertThat(response.getStatusCode(), is(HttpStatus.OK));
		return response.getBody();
	}

	private HttpStatus delete(Long id, String store, HttpHeaders headers) {
		return testRestTemplate.exchange("/api/v1/private/user/" + id + "?store=" + store, HttpMethod.DELETE,
				new HttpEntity<>(headers), String.class).getStatusCode();
	}

	private HttpStatus setEnabled(Long id, boolean active, String store, HttpHeaders headers) {
		PersistableUser body = new PersistableUser();
		body.setActive(active);
		return testRestTemplate.exchange("/api/v1/private/user/" + id + "/enabled?store=" + store,
				HttpMethod.PATCH, new HttpEntity<>(body, headers), String.class).getStatusCode();
	}
}
