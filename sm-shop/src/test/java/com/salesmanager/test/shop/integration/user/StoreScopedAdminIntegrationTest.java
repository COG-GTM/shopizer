package com.salesmanager.test.shop.integration.user;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.is;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;
import static org.springframework.http.HttpStatus.NOT_FOUND;
import static org.springframework.http.HttpStatus.OK;
import static org.springframework.http.HttpStatus.UNAUTHORIZED;

import java.util.Arrays;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.SpringBootTest.WebEnvironment;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
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

@SpringBootTest(classes = ShopApplication.class, webEnvironment = WebEnvironment.RANDOM_PORT)
@RunWith(SpringRunner.class)
public class StoreScopedAdminIntegrationTest extends ServicesTestSupport {

	private static final String STORE = "itstore";
	private static final String CHILD_STORE = "itchild";
	private static final String STORE_ADMIN = "admin@itstore.com";
	private static final String STORE_CLERK = "clerk@itstore.com";
	private static final String DEFAULT_ADMIN = "admin2@default.com";
	private static final String CHILD_ADMIN = "childadmin@itchild.com";
	private static final String PASSWORD = "StorePass1!";

	@Test
	public void storeAdminsAreScopedAcrossUserOrderAndShippingEndpoints() {
		HttpHeaders superadmin = getHeader();
		createStore(superadmin, STORE, null, true);
		ReadableUser storeAdmin = createAdmin(superadmin, STORE_ADMIN, STORE);
		ReadableUser clerk = createAdmin(superadmin, STORE_CLERK, STORE);
		ReadableUser defaultAdmin = createAdmin(superadmin, DEFAULT_ADMIN, MerchantStore.DEFAULT_STORE);

		HttpHeaders admin = getHeader(STORE_ADMIN, PASSWORD);
		PersistableUser disableClerk = new PersistableUser();
		disableClerk.setActive(false);
		assertThat(call("/api/v1/private/user/" + clerk.getId() + "/enabled?store=" + STORE,
				HttpMethod.PATCH, admin, disableClerk), is(OK));

		ReadableUser inactiveClerk = getUser(superadmin, clerk.getId(), STORE);
		assertFalse(inactiveClerk.isActive());

		assertThat(call("/api/v1/private/user/" + clerk.getId() + "?store=" + STORE,
				HttpMethod.DELETE, admin, null), is(OK));
		assertThat(call("/api/v1/private/users/" + clerk.getId() + "?store=" + STORE,
				HttpMethod.GET, superadmin, null), is(NOT_FOUND));

		PersistableUser disableDefaultAdmin = new PersistableUser();
		disableDefaultAdmin.setActive(false);
		assertThat(call("/api/v1/private/user/" + defaultAdmin.getId() + "/enabled?store=DEFAULT",
				HttpMethod.PATCH, admin, disableDefaultAdmin), is(UNAUTHORIZED));
		assertThat(call("/api/v1/private/user/" + defaultAdmin.getId() + "?store=DEFAULT",
				HttpMethod.DELETE, admin, null), is(UNAUTHORIZED));
		assertFalse(call("/api/v1/private/user/" + defaultAdmin.getId() + "?store=" + STORE,
				HttpMethod.DELETE, admin, null).is2xxSuccessful());
		assertTrue(getUser(superadmin, defaultAdmin.getId(), MerchantStore.DEFAULT_STORE).isActive());

		assertThat(call("/api/v1/private/orders?store=" + STORE, HttpMethod.GET, admin, null), is(OK));
		assertThat(call("/api/v1/private/orders?store=DEFAULT", HttpMethod.GET, admin, null), is(UNAUTHORIZED));
		assertThat(call("/api/v1/private/shipping/packages?store=" + STORE, HttpMethod.GET, admin, null), is(OK));
		assertThat(call("/api/v1/private/shipping/packages?store=DEFAULT", HttpMethod.GET, admin, null),
				is(UNAUTHORIZED));

		createStore(superadmin, CHILD_STORE, STORE, false);
		createAdmin(superadmin, CHILD_ADMIN, CHILD_STORE);
		assertThat(call("/api/v1/private/users?store=" + STORE, HttpMethod.GET,
				getHeader(CHILD_ADMIN, PASSWORD), null), is(UNAUTHORIZED));

		assertFalse(call("/api/v1/private/user/1?store=DEFAULT", HttpMethod.DELETE, superadmin, null)
				.is2xxSuccessful());
		assertNotNull(getUser(superadmin, 1L, MerchantStore.DEFAULT_STORE));
		PersistableUser enableDefaultAdmin = new PersistableUser();
		enableDefaultAdmin.setActive(true);
		assertThat(call("/api/v1/private/user/" + defaultAdmin.getId() + "/enabled?store=" + STORE,
				HttpMethod.PATCH, superadmin, enableDefaultAdmin), is(NOT_FOUND));

		assertNotNull(storeAdmin);
	}

	private void createStore(HttpHeaders headers, String code, String parentCode, boolean retailer) {
		PersistableAddress address = new PersistableAddress();
		address.setAddress("1 Integration Way");
		address.setPostalCode("12345");
		address.setCountry("US");
		address.setCity("Seattle");
		address.setStateProvince("WA");

		PersistableMerchantStore store = new PersistableMerchantStore();
		store.setCode(code);
		store.setCurrency("CAD");
		store.setDefaultLanguage("en");
		store.setEmail(code + "@example.com");
		store.setName(code);
		store.setPhone("444-555-6666");
		store.setSupportedLanguages(Arrays.asList("en"));
		store.setAddress(address);
		store.setRetailer(retailer);
		store.setRetailerStore(parentCode);

		assertThat(call("/api/v1/private/store", HttpMethod.POST, headers, store), is(OK));
	}

	private ReadableUser createAdmin(HttpHeaders headers, String email, String storeCode) {
		PersistableUser user = new PersistableUser();
		user.setDefaultLanguage("en");
		user.setEmailAddress(email);
		user.setFirstName("Integration");
		user.setLastName("Admin");
		user.setUserName(email);
		user.setPassword(PASSWORD);
		user.setRepeatPassword(PASSWORD);
		user.setActive(true);
		PersistableGroup group = new PersistableGroup();
		group.setName("ADMIN");
		user.getGroups().add(group);

		ResponseEntity<ReadableUser> response = testRestTemplate.exchange(
				"/api/v1/private/user/?store=" + storeCode, HttpMethod.POST, new HttpEntity<>(user, headers),
				ReadableUser.class);
		assertThat(response.getStatusCode(), is(OK));
		assertNotNull(response.getBody());
		return response.getBody();
	}

	private ReadableUser getUser(HttpHeaders headers, Long id, String storeCode) {
		ResponseEntity<ReadableUser> response = testRestTemplate.exchange(
				"/api/v1/private/users/" + id + "?store=" + storeCode, HttpMethod.GET,
				new HttpEntity<>(headers), ReadableUser.class);
		assertThat(response.getStatusCode(), is(OK));
		return response.getBody();
	}

	private org.springframework.http.HttpStatus call(String url, HttpMethod method, HttpHeaders headers, Object body) {
		ResponseEntity<String> response = testRestTemplate.exchange(url, method, new HttpEntity<>(body, headers),
				String.class);
		return response.getStatusCode();
	}
}
