package com.salesmanager.test.shop.integration.store;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.is;

import java.util.Arrays;

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
import com.salesmanager.shop.model.store.PersistableMerchantStore;
import com.salesmanager.shop.model.user.PersistableUser;
import com.salesmanager.test.shop.common.ServicesTestSupport;

@SpringBootTest(classes = ShopApplication.class, webEnvironment = WebEnvironment.RANDOM_PORT)
@RunWith(SpringRunner.class)
public class MerchantStoreParentAuthorizationIntegrationTest extends ServicesTestSupport {

	private static final String PARENT_STORE = "parentx";
	private static final String CHILD_STORE = "globex";
	private static final String ATTACKER_STORE = "mall";
	private static final String ATTACKER_USER = "mallory@mall.com";
	private static final String PARENT_ADMIN_USER = "admin@parentx.com";
	private static final String PASSWORD = "Password1";

	@Inject
	private TestRestTemplate testRestTemplate;

	@Test
	public void storeAdminCannotUseParentRelationshipToAccessAnotherStore() {
		HttpHeaders superadmin = getHeader();

		PersistableMerchantStore parent = store(PARENT_STORE, null);
		parent.setRetailer(true);
		assertThat(post("/api/v1/private/store/", parent, superadmin), is(HttpStatus.OK));
		assertThat(post("/api/v1/private/store/", store(CHILD_STORE, PARENT_STORE), superadmin), is(HttpStatus.OK));
		assertThat(post("/api/v1/private/store/", store(ATTACKER_STORE, null), superadmin), is(HttpStatus.OK));

		assertThat(post("/api/v1/private/user/?store=" + ATTACKER_STORE, admin(ATTACKER_USER), superadmin),
				is(HttpStatus.OK));
		assertThat(post("/api/v1/private/user/?store=" + PARENT_STORE, admin(PARENT_ADMIN_USER), superadmin),
				is(HttpStatus.OK));

		HttpHeaders attacker = getHeader(ATTACKER_USER, PASSWORD);

		// store admin can still update own store when parent is unchanged
		assertThat(put("/api/v1/private/store/" + ATTACKER_STORE, store(ATTACKER_STORE, null), attacker),
				is(HttpStatus.OK));

		// store admin cannot attach own store under another store
		assertThat(put("/api/v1/private/store/" + ATTACKER_STORE, store(ATTACKER_STORE, CHILD_STORE), attacker),
				is(HttpStatus.UNAUTHORIZED));

		// even if the parent link exists, a child store admin gets no access to the parent store
		assertThat(put("/api/v1/private/store/" + ATTACKER_STORE, store(ATTACKER_STORE, CHILD_STORE), superadmin),
				is(HttpStatus.OK));
		assertThat(get("/api/v1/private/customers?store=" + CHILD_STORE, attacker), is(HttpStatus.UNAUTHORIZED));
		assertThat(get("/api/v1/private/customers?store=" + ATTACKER_STORE, attacker), is(HttpStatus.OK));

		// parent store admin can access child store
		HttpHeaders parentAdmin = getHeader(PARENT_ADMIN_USER, PASSWORD);
		assertThat(get("/api/v1/private/customers?store=" + CHILD_STORE, parentAdmin), is(HttpStatus.OK));
	}

	private HttpStatus post(String url, Object body, HttpHeaders headers) {
		return exchange(url, HttpMethod.POST, new HttpEntity<>(body, headers));
	}

	private HttpStatus put(String url, Object body, HttpHeaders headers) {
		return exchange(url, HttpMethod.PUT, new HttpEntity<>(body, headers));
	}

	private HttpStatus get(String url, HttpHeaders headers) {
		return exchange(url, HttpMethod.GET, new HttpEntity<>(headers));
	}

	private HttpStatus exchange(String url, HttpMethod method, HttpEntity<?> entity) {
		ResponseEntity<String> response = testRestTemplate.exchange(url, method, entity, String.class);
		return response.getStatusCode();
	}

	private PersistableMerchantStore store(String code, String retailerStore) {
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
		store.setEmail(code + "@test.com");
		store.setName(code);
		store.setPhone("444-555-6666");
		store.setSupportedLanguages(Arrays.asList("en"));
		store.setAddress(address);
		store.setRetailerStore(retailerStore);
		return store;
	}

	private PersistableUser admin(String userName) {
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
		return user;
	}
}
