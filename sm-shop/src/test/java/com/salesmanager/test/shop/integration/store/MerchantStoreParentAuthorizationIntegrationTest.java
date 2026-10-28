package com.salesmanager.test.shop.integration.store;

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
import com.salesmanager.shop.model.store.PersistableMerchantStore;
import com.salesmanager.shop.model.security.PersistableGroup;
import com.salesmanager.shop.model.user.PersistableUser;
import com.salesmanager.test.shop.common.ServicesTestSupport;

/**
 * A store administrator must not be able to reach another store's private API
 * by re-parenting their own store.
 */
@SpringBootTest(classes = ShopApplication.class, webEnvironment = WebEnvironment.RANDOM_PORT)
@RunWith(SpringRunner.class)
public class MerchantStoreParentAuthorizationIntegrationTest extends ServicesTestSupport {

	private static final String PASSWORD = "Password1";

	@Test
	public void storeAdminCannotReparentOwnStoreOrAccessTargetStore() throws Exception {

		String victim = "globexp";
		String attacker = "mallp";

		createStore(victim, MerchantStore.DEFAULT_STORE);
		createStore(attacker, null);
		String mallory = createAdmin(attacker, "mallory@mallp.com");

		HttpHeaders malloryHeaders = getHeader(mallory, PASSWORD);

		// sanity: own store is reachable, victim store is not
		assertThat(listCustomers(attacker, malloryHeaders), is(HttpStatus.OK));
		assertThat(listCustomers(victim, malloryHeaders), is(HttpStatus.UNAUTHORIZED));

		// store admin cannot set an arbitrary parent on their own store
		ResponseEntity<Void> update = testRestTemplate.exchange("/api/v1/private/store/" + attacker, HttpMethod.PUT,
				new HttpEntity<>(store(attacker, victim), malloryHeaders), Void.class);
		assertThat(update.getStatusCode(), is(HttpStatus.UNAUTHORIZED));

		// even if a superadmin attaches the attacker store under the victim,
		// a child store admin gains no access to the parent store
		ResponseEntity<Void> superUpdate = testRestTemplate.exchange("/api/v1/private/store/" + attacker,
				HttpMethod.PUT, new HttpEntity<>(store(attacker, victim), getHeader()), Void.class);
		assertThat(superUpdate.getStatusCode(), is(HttpStatus.OK));

		assertThat(listCustomers(victim, malloryHeaders), is(HttpStatus.UNAUTHORIZED));
		assertThat(listCustomers(MerchantStore.DEFAULT_STORE, malloryHeaders), is(HttpStatus.UNAUTHORIZED));
	}

	@Test
	public void parentStoreAdminCanAccessChildStore() throws Exception {

		String parent = "parentp";
		String child = "childp";

		createStore(parent, null);
		createStore(child, parent);
		String parentAdmin = createAdmin(parent, "admin@parentp.com");
		String childAdmin = createAdmin(child, "admin@childp.com");

		assertThat(listCustomers(child, getHeader(parentAdmin, PASSWORD)), is(HttpStatus.OK));
		assertThat(listCustomers(parent, getHeader(childAdmin, PASSWORD)), is(HttpStatus.UNAUTHORIZED));
	}

	private HttpStatus listCustomers(String storeCode, HttpHeaders headers) {
		return testRestTemplate.exchange("/api/v1/private/customers?store=" + storeCode, HttpMethod.GET,
				new HttpEntity<>(headers), String.class).getStatusCode();
	}

	private void createStore(String code, String parent) {
		ResponseEntity<Void> response = testRestTemplate.exchange("/api/v1/private/store/", HttpMethod.POST,
				new HttpEntity<>(store(code, parent), getHeader()), Void.class);
		assertThat(response.getStatusCode(), is(HttpStatus.OK));
	}

	private String createAdmin(String storeCode, String userName) {
		PersistableUser user = new PersistableUser();
		user.setDefaultLanguage("en");
		user.setEmailAddress(userName);
		user.setFirstName("Store");
		user.setLastName("Admin");
		user.setUserName(userName);
		user.setPassword(PASSWORD);
		user.setRepeatPassword(PASSWORD);
		user.setActive(true);
		PersistableGroup group = new PersistableGroup();
		group.setName("ADMIN");
		user.getGroups().add(group);

		ResponseEntity<String> response = testRestTemplate.exchange("/api/v1/private/user/?store=" + storeCode,
				HttpMethod.POST, new HttpEntity<>(user, getHeader()), String.class);
		assertThat(response.getStatusCode(), is(HttpStatus.OK));
		return userName;
	}

	private PersistableMerchantStore store(String code, String parent) {
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
		store.setEmail("store@" + code + ".com");
		store.setName(code);
		store.setPhone("444-555-6666");
		store.setSupportedLanguages(Arrays.asList("en"));
		store.setAddress(address);
		store.setRetailerStore(parent);
		return store;
	}
}
