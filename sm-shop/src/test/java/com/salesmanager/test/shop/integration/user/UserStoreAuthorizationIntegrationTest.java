package com.salesmanager.test.shop.integration.user;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.is;

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
 * A store admin must not be able to act on another store by passing its code.
 */
@SpringBootTest(classes = ShopApplication.class, webEnvironment = WebEnvironment.RANDOM_PORT)
@RunWith(SpringRunner.class)
public class UserStoreAuthorizationIntegrationTest extends ServicesTestSupport {

	private static final String OTHER_STORE = "authzstore";
	private static final String OTHER_ADMIN = "admin@authzstore.com";
	private static final String OTHER_ADMIN_PASSWORD = "Password1";
	private static final Long SUPERADMIN_ID = 1L;

	@Test
	public void storeAdminCannotAccessAnotherStore() throws Exception {

		HttpHeaders superadmin = getHeader();
		createStore(superadmin);
		ReadableUser otherAdmin = createStoreAdmin(superadmin);

		HttpHeaders storeAdmin = getHeader(OTHER_ADMIN, OTHER_ADMIN_PASSWORD);
		String defaultStore = MerchantStore.DEFAULT_STORE;

		assertThat(call("/api/v1/private/users?store=" + OTHER_STORE, HttpMethod.GET, storeAdmin, null),
				is(HttpStatus.OK));

		assertThat(call("/api/v1/private/users?store=" + defaultStore, HttpMethod.GET, storeAdmin, null),
				is(HttpStatus.UNAUTHORIZED));
		assertThat(call("/api/v1/private/orders?store=" + defaultStore, HttpMethod.GET, storeAdmin, null),
				is(HttpStatus.UNAUTHORIZED));

		PersistableUser disable = new PersistableUser();
		disable.setActive(false);
		assertThat(call("/api/v1/private/user/" + SUPERADMIN_ID + "/enabled?store=" + defaultStore,
				HttpMethod.PATCH, storeAdmin, disable), is(HttpStatus.UNAUTHORIZED));

		assertThat(call("/api/v1/private/user/" + SUPERADMIN_ID + "?store=" + defaultStore, HttpMethod.DELETE,
				storeAdmin, null), is(HttpStatus.UNAUTHORIZED));

		// a user id that is not in the requested store is not found
		assertThat(call("/api/v1/private/user/" + SUPERADMIN_ID + "?store=" + OTHER_STORE, HttpMethod.DELETE,
				storeAdmin, null), is(HttpStatus.BAD_REQUEST));

		// superadmin cannot be deleted, even by a superadmin
		assertThat(call("/api/v1/private/user/" + SUPERADMIN_ID + "?store=" + defaultStore, HttpMethod.DELETE,
				superadmin, null), is(HttpStatus.BAD_REQUEST));

		assertThat(call("/api/v1/private/users/" + SUPERADMIN_ID, HttpMethod.GET, superadmin, null),
				is(HttpStatus.OK));

		assertThat(call("/api/v1/private/user/" + otherAdmin.getId() + "?store=" + OTHER_STORE, HttpMethod.DELETE,
				superadmin, null), is(HttpStatus.OK));
	}

	private HttpStatus call(String url, HttpMethod method, HttpHeaders headers, Object body) {
		ResponseEntity<String> response = testRestTemplate.exchange(url, method, new HttpEntity<>(body, headers),
				String.class);
		return response.getStatusCode();
	}

	private void createStore(HttpHeaders superadmin) {
		PersistableAddress address = new PersistableAddress();
		address.setAddress("1 Authz street");
		address.setPostalCode("12345");
		address.setCountry("US");
		address.setCity("FT LD");
		address.setStateProvince("FL");

		PersistableMerchantStore store = new PersistableMerchantStore();
		store.setCode(OTHER_STORE);
		store.setCurrency("CAD");
		store.setDefaultLanguage("en");
		store.setEmail("store@authzstore.com");
		store.setName(OTHER_STORE);
		store.setPhone("444-555-6666");
		store.setSupportedLanguages(Arrays.asList("en"));
		store.setAddress(address);

		assertThat(call("/api/v1/private/store/", HttpMethod.POST, superadmin, store), is(HttpStatus.OK));
	}

	private ReadableUser createStoreAdmin(HttpHeaders superadmin) {
		PersistableUser user = new PersistableUser();
		user.setDefaultLanguage("en");
		user.setEmailAddress(OTHER_ADMIN);
		user.setFirstName("Other");
		user.setLastName("Admin");
		user.setUserName(OTHER_ADMIN);
		user.setPassword(OTHER_ADMIN_PASSWORD);
		user.setRepeatPassword(OTHER_ADMIN_PASSWORD);
		PersistableGroup group = new PersistableGroup();
		group.setName("ADMIN");
		user.getGroups().add(group);

		ResponseEntity<ReadableUser> response = testRestTemplate.exchange("/api/v1/private/user/?store=" + OTHER_STORE,
				HttpMethod.POST, new HttpEntity<>(user, superadmin), ReadableUser.class);
		assertThat(response.getStatusCode(), is(HttpStatus.OK));
		return response.getBody();
	}
}
