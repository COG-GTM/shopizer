package com.salesmanager.test.shop.integration.store;

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
import com.salesmanager.shop.model.store.PersistableMerchantStore;
import com.salesmanager.shop.model.store.ReadableMerchantStore;
import com.salesmanager.shop.model.security.PersistableGroup;
import com.salesmanager.shop.model.user.PersistableUser;
import com.salesmanager.test.shop.common.ServicesTestSupport;

@SpringBootTest(classes = ShopApplication.class, webEnvironment = WebEnvironment.RANDOM_PORT)
@RunWith(SpringRunner.class)
public class StoreParentEscalationIntegrationTest extends ServicesTestSupport {

	private static final String PASSWORD = "Password1";

	@Test
	public void storeAdminCanUpdateTopLevelStoreWithoutParent() {
		String code = "pr210toplvl";
		createStore(code, null);
		String admin = createAdmin(code, "pr210toplvl-admin@example.com", "ADMIN");

		ResponseEntity<Void> response = testRestTemplate.exchange("/api/v1/private/store/" + code, HttpMethod.PUT,
				new HttpEntity<>(store(code, null), getHeader(admin, PASSWORD)), Void.class);

		assertThat(response.getStatusCode(), is(HttpStatus.OK));
	}

	@Test
	public void childAdminMayKeepButNotChangeItsParent() {
		String parent = "pr210parent";
		String child = "pr210child";
		String unrelated = "pr210other";
		createStore(parent, null);
		createStore(child, parent);
		createStore(unrelated, null);
		String admin = createAdmin(child, "pr210child-admin@example.com", "ADMIN");
		HttpHeaders headers = getHeader(admin, PASSWORD);

		ResponseEntity<Void> keepParent = testRestTemplate.exchange("/api/v1/private/store/" + child, HttpMethod.PUT,
				new HttpEntity<>(store(child, parent), headers), Void.class);
		assertThat(keepParent.getStatusCode(), is(HttpStatus.OK));

		ResponseEntity<Void> changeParent = testRestTemplate.exchange("/api/v1/private/store/" + child,
				HttpMethod.PUT, new HttpEntity<>(store(child, unrelated), headers), Void.class);
		assertThat(changeParent.getStatusCode(), is(HttpStatus.UNAUTHORIZED));

		ResponseEntity<ReadableMerchantStore> readable = testRestTemplate.exchange("/api/v1/store/" + child,
				HttpMethod.GET, new HttpEntity<>(getHeader()), ReadableMerchantStore.class);
		assertThat(readable.getStatusCode(), is(HttpStatus.OK));
		assertNotNull(readable.getBody());
		assertNotNull(readable.getBody().getParent());
		assertThat(readable.getBody().getParent().getCode(), is(parent));
	}

	@Test
	public void siblingAdminsCannotReadEachOthersPrivateCustomersOrOrders() {
		String parent = "pr210sibpar";
		String childA = "pr210siba";
		String childB = "pr210sibb";
		createStore(parent, null);
		createStore(childA, parent);
		createStore(childB, parent);
		String adminA = createAdmin(childA, "pr210siba-admin@example.com", "ADMIN");
		HttpHeaders headers = getHeader(adminA, PASSWORD);

		assertThat(listCustomers(childB, headers), is(HttpStatus.UNAUTHORIZED));
		assertThat(listOrders(childB, headers), is(HttpStatus.UNAUTHORIZED));
	}

	@Test
	public void parentAdminCanReadChildOrders() {
		String parent = "pr210ordpar";
		String child = "pr210ordchild";
		createStore(parent, null);
		createStore(child, parent);
		String parentAdmin = createAdmin(parent, "pr210ord-admin@example.com", "ADMIN");

		assertThat(listOrders(child, getHeader(parentAdmin, PASSWORD)), is(HttpStatus.OK));
	}

	@Test
	public void superadminCanReadAnyCustomersAndAnonymousRequestIsUnauthorized() {
		String first = "pr210anyone";
		String second = "pr210anytwo";
		createStore(first, null);
		createStore(second, first);

		assertThat(listCustomers(first, getHeader()), is(HttpStatus.OK));
		assertThat(listCustomers(second, getHeader()), is(HttpStatus.OK));
		assertThat(listCustomers(MerchantStore.DEFAULT_STORE, new HttpHeaders()), is(HttpStatus.UNAUTHORIZED));
	}

	private HttpStatus listCustomers(String code, HttpHeaders headers) {
		return testRestTemplate.exchange("/api/v1/private/customers?store=" + code, HttpMethod.GET,
				new HttpEntity<>(headers), String.class).getStatusCode();
	}

	private HttpStatus listOrders(String code, HttpHeaders headers) {
		return testRestTemplate.exchange("/api/v1/private/orders?store=" + code, HttpMethod.GET,
				new HttpEntity<>(headers), String.class).getStatusCode();
	}

	private void createStore(String code, String parent) {
		ResponseEntity<Void> response = testRestTemplate.exchange("/api/v1/private/store", HttpMethod.POST,
				new HttpEntity<>(store(code, parent), getHeader()), Void.class);
		assertThat(response.getStatusCode(), is(HttpStatus.OK));
	}

	private String createAdmin(String storeCode, String userName, String groupName) {
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
		group.setName(groupName);
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
		store.setEmail("store-" + code + "@example.com");
		store.setName(code);
		store.setPhone("444-555-6666");
		store.setSupportedLanguages(Arrays.asList("en"));
		store.setAddress(address);
		store.setRetailerStore(parent);
		return store;
	}
}
