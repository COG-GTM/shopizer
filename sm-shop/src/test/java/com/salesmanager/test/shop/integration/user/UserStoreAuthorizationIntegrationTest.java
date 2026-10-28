package com.salesmanager.test.shop.integration.user;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.hasItem;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.SpringBootTest.WebEnvironment;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.test.context.junit4.SpringRunner;

import com.salesmanager.core.business.services.user.UserService;
import com.salesmanager.core.model.merchant.MerchantStore;
import com.salesmanager.core.model.user.Group;
import com.salesmanager.core.model.user.User;
import com.salesmanager.shop.application.ShopApplication;
import com.salesmanager.shop.model.references.PersistableAddress;
import com.salesmanager.shop.model.security.PersistableGroup;
import com.salesmanager.shop.model.store.PersistableMerchantStore;
import com.salesmanager.shop.model.user.PersistableUser;
import com.salesmanager.shop.model.user.ReadableUser;
import com.salesmanager.shop.model.user.ReadableUserList;
import com.salesmanager.shop.store.api.exception.ErrorEntity;
import com.salesmanager.test.shop.common.ServicesTestSupport;

@SpringBootTest(classes = ShopApplication.class, webEnvironment = WebEnvironment.RANDOM_PORT)
@RunWith(SpringRunner.class)
public class UserStoreAuthorizationIntegrationTest extends ServicesTestSupport {

	private static final String INITECH = "initech";
	private static final String PASSWORD = "Password1";
	private static boolean fixturesCreated;
	private static Long superadminId;
	private static Long defaultAdminId;
	private static Long initechThrowawayId;

	@Autowired
	private UserService userService;

	@Before
	public void createFixtures() {
		synchronized (UserStoreAuthorizationIntegrationTest.class) {
			if (fixturesCreated) {
				return;
			}

			org.springframework.http.HttpHeaders superadmin = getHeader();
			ReadableUser superadminUser = profile(superadmin);
			createStore(INITECH, superadmin);
			createUser("peter@initech.com", INITECH, superadmin);
			ReadableUser defaultAdmin = createUser("dflt-admin@acme.com", MerchantStore.DEFAULT_STORE, superadmin);
			ReadableUser initechThrowaway = createUser("throwaway-" + System.nanoTime() + "@initech.com", INITECH,
					superadmin);

			superadminId = superadminUser.getId();
			defaultAdminId = defaultAdmin.getId();
			initechThrowawayId = initechThrowaway.getId();
			fixturesCreated = true;
		}
	}

	@Test
	public void defaultAdminCannotDisableSuperadmin() {
		org.springframework.http.HttpHeaders defaultAdmin = getHeader("dflt-admin@acme.com", PASSWORD);

		assertThat(setEnabled(superadminId, false, MerchantStore.DEFAULT_STORE, defaultAdmin), is(HttpStatus.UNAUTHORIZED));

		org.springframework.http.HttpHeaders superadmin = getHeader();
		assertTrue(get(superadminId, MerchantStore.DEFAULT_STORE, superadmin).isActive());
		assertNotNull(superadmin.getFirst("Authorization"));
	}

	@Test
	public void defaultAdminCannotDeleteSuperadmin() {
		org.springframework.http.HttpHeaders defaultAdmin = getHeader("dflt-admin@acme.com", PASSWORD);
		ResponseEntity<ErrorEntity> response = testRestTemplate.exchange(
				"/api/v1/private/user/" + superadminId + "?store=" + MerchantStore.DEFAULT_STORE, HttpMethod.DELETE,
				new HttpEntity<>(defaultAdmin), ErrorEntity.class);

		assertThat(response.getStatusCode(), is(HttpStatus.BAD_REQUEST));
		assertNotNull(response.getBody());
		assertThat(response.getBody().getErrorCode(), is("304"));
		assertTrue(get(superadminId, MerchantStore.DEFAULT_STORE, getHeader()).isActive());
	}

	@Test
	public void superadminCanDisableAndEnableDefaultAdmin() {
		org.springframework.http.HttpHeaders superadmin = getHeader();

		assertThat(setEnabled(defaultAdminId, false, MerchantStore.DEFAULT_STORE, superadmin), is(HttpStatus.OK));
		assertThat(get(defaultAdminId, MerchantStore.DEFAULT_STORE, superadmin).isActive(), is(false));
		assertThat(setEnabled(defaultAdminId, true, MerchantStore.DEFAULT_STORE, superadmin), is(HttpStatus.OK));
		assertThat(get(defaultAdminId, MerchantStore.DEFAULT_STORE, superadmin).isActive(), is(true));
	}

	@Test
	public void initechAdminCannotDeleteDefaultAdmin() {
		org.springframework.http.HttpHeaders peter = getHeader("peter@initech.com", PASSWORD);

		assertThat(delete(defaultAdminId, INITECH, peter), is(HttpStatus.NOT_FOUND));
		assertThat(delete(defaultAdminId, MerchantStore.DEFAULT_STORE, peter), is(HttpStatus.UNAUTHORIZED));
		assertNotNull(get(defaultAdminId, MerchantStore.DEFAULT_STORE, getHeader()));
	}

	@Test
	public void initechAdminCanOnlyAccessItsStore() {
		org.springframework.http.HttpHeaders peter = getHeader("peter@initech.com", PASSWORD);

		assertThat(getStatus("/api/v1/private/store/DEFAULT/marketing", peter), is(HttpStatus.UNAUTHORIZED));
		assertThat(getStatus("/api/v1/private/store/initech/marketing", peter), is(HttpStatus.OK));
		assertThat(getUsersStatus(MerchantStore.DEFAULT_STORE, peter), is(HttpStatus.UNAUTHORIZED));
		assertThat(getUsersStatus(INITECH, peter), is(HttpStatus.OK));
	}

	@Test
	public void superadminCannotDeleteMissingUser() {
		assertThat(delete(999999L, MerchantStore.DEFAULT_STORE, getHeader()), is(HttpStatus.NOT_FOUND));
	}

	@Test
	public void superadminDeleteIsScopedToRequestedStore() {
		org.springframework.http.HttpHeaders superadmin = getHeader();

		assertThat(delete(initechThrowawayId, MerchantStore.DEFAULT_STORE, superadmin), is(HttpStatus.NOT_FOUND));
		assertThat(delete(initechThrowawayId, INITECH, superadmin), is(HttpStatus.OK));
		assertThat(getStatus("/api/v1/private/users/" + initechThrowawayId + "?store=" + INITECH, superadmin)
				.is2xxSuccessful(), is(false));
	}

	@Test
	public void userServiceFindByStoreReturnsOnlyMatchingStore() throws Exception {
		User user = userService.findByStore(superadminId, MerchantStore.DEFAULT_STORE);
		assertNotNull(user);
		List<String> groupNames = user.getGroups().stream().map(Group::getGroupName).collect(Collectors.toList());
		assertThat(groupNames, hasItem("SUPERADMIN"));

		assertNull(userService.findByStore(superadminId, INITECH));
		assertNull(userService.findByStore(superadminId, "NO_SUCH_STORE"));
	}

	private void createStore(String code, org.springframework.http.HttpHeaders headers) {
		PersistableAddress address = new PersistableAddress();
		address.setAddress("1 Initech way");
		address.setPostalCode("12345");
		address.setCountry("US");
		address.setCity("Springfield");
		address.setStateProvince("CA");

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

	private ReadableUser createUser(String userName, String store, org.springframework.http.HttpHeaders headers) {
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

	private ReadableUser profile(org.springframework.http.HttpHeaders headers) {
		ResponseEntity<ReadableUser> response = testRestTemplate.exchange("/api/v1/private/user/profile",
				HttpMethod.GET, new HttpEntity<>(headers), ReadableUser.class);
		assertThat(response.getStatusCode(), is(HttpStatus.OK));
		return response.getBody();
	}

	private ReadableUser get(Long id, String store, org.springframework.http.HttpHeaders headers) {
		ResponseEntity<ReadableUser> response = testRestTemplate.exchange(
				"/api/v1/private/users/" + id + "?store=" + store, HttpMethod.GET, new HttpEntity<>(headers),
				ReadableUser.class);
		assertThat(response.getStatusCode(), is(HttpStatus.OK));
		return response.getBody();
	}

	private HttpStatus delete(Long id, String store, org.springframework.http.HttpHeaders headers) {
		return testRestTemplate.exchange("/api/v1/private/user/" + id + "?store=" + store, HttpMethod.DELETE,
				new HttpEntity<>(headers), String.class).getStatusCode();
	}

	private HttpStatus setEnabled(Long id, boolean active, String store,
			org.springframework.http.HttpHeaders headers) {
		PersistableUser body = new PersistableUser();
		body.setActive(active);
		return testRestTemplate.exchange("/api/v1/private/user/" + id + "/enabled?store=" + store,
				HttpMethod.PATCH, new HttpEntity<>(body, headers), String.class).getStatusCode();
	}

	private HttpStatus getUsersStatus(String store, org.springframework.http.HttpHeaders headers) {
		return testRestTemplate.exchange("/api/v1/private/users?store=" + store, HttpMethod.GET,
				new HttpEntity<>(headers), ReadableUserList.class).getStatusCode();
	}

	private HttpStatus getStatus(String path, org.springframework.http.HttpHeaders headers) {
		return testRestTemplate.exchange(path, HttpMethod.GET, new HttpEntity<>(headers), String.class).getStatusCode();
	}
}
