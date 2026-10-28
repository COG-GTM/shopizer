package com.salesmanager.test.shop.integration.store;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

import javax.inject.Inject;

import org.junit.Before;
import org.junit.FixMethodOrder;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.MethodSorters;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.SpringBootTest.WebEnvironment;
import org.springframework.core.io.ClassPathResource;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.test.context.junit4.SpringRunner;
import org.springframework.util.LinkedMultiValueMap;

import com.salesmanager.shop.application.ShopApplication;
import com.salesmanager.shop.model.references.PersistableAddress;
import com.salesmanager.shop.model.references.ReadableLanguage;
import com.salesmanager.shop.model.store.PersistableBrand;
import com.salesmanager.shop.model.store.PersistableMerchantStore;
import com.salesmanager.shop.model.store.ReadableMerchantStore;
import com.salesmanager.shop.model.user.PersistableUser;
import com.salesmanager.shop.model.user.ReadableUser;
import com.salesmanager.shop.model.security.PersistableGroup;
import com.salesmanager.test.shop.common.ServicesTestSupport;

@SpringBootTest(classes = ShopApplication.class, webEnvironment = WebEnvironment.RANDOM_PORT)
@RunWith(SpringRunner.class)
@FixMethodOrder(MethodSorters.NAME_ASCENDING)
public class MerchantStoreGroupAuthorizationIntegrationTest extends ServicesTestSupport {

	private static final String DELETE_STORE = "pr151del";
	private static final String DELETE_CHILD_STORE = "pr151delchild";
	private static final String PASSWORD = "Password1";
	private static final String USER_PREFIX = "pr151-" + UUID.randomUUID().toString().replace("-", "");
	private static final Map<String, String> USERNAMES = new HashMap<>();
	private static final Map<String, HttpHeaders> USER_HEADERS = new HashMap<>();
	private static boolean initialized;
	private static String defaultStoreName;
	private static String defaultStorePhone;

	@Inject
	private org.springframework.boot.test.web.client.TestRestTemplate restTemplate;

	@Before
	public void setUpUsersAndStore() {
		synchronized (MerchantStoreGroupAuthorizationIntegrationTest.class) {
			if (initialized) {
				return;
			}

			HttpHeaders superadmin = getHeader();
			ensureDeleteStoreExists(superadmin);
			ReadableMerchantStore defaultStore = restTemplate.getForObject("/api/v1/store/DEFAULT",
					ReadableMerchantStore.class);
			assertNotNull(defaultStore);
			defaultStoreName = defaultStore.getName();
			defaultStorePhone = defaultStore.getPhone();

			createAndAuthenticate("catalogue", "ADMIN_CATALOGUE", "DEFAULT", superadmin);
			createAndAuthenticate("order", "ADMIN_ORDER", "DEFAULT", superadmin);
			createAndAuthenticate("content", "ADMIN_CONTENT", "DEFAULT", superadmin);
			createAndAuthenticate("admin", "ADMIN", "DEFAULT", superadmin);
			createAndAuthenticate("store", "ADMIN_STORE", "DEFAULT", superadmin);
			createAndAuthenticate("retailer", "ADMIN_RETAILER", "DEFAULT", superadmin);
			createAndAuthenticate("admin-delete", "ADMIN", DELETE_STORE, superadmin);
			createAndAuthenticate("retailer-delete", "ADMIN_RETAILER", DELETE_STORE, superadmin);

			initialized = true;
		}
	}

	@Test
	public void test01CatalogueCannotPutStore() {
		assertDenied("catalogue", HttpMethod.PUT, "/api/v1/private/store/DEFAULT", updateBody());
	}

	@Test
	public void test02CatalogueCannotGetMarketing() {
		assertDenied("catalogue", HttpMethod.GET, "/api/v1/private/store/DEFAULT/marketing", null);
	}

	@Test
	public void test03CatalogueCannotSaveMarketing() {
		assertDenied("catalogue", HttpMethod.POST, "/api/v1/private/store/DEFAULT/marketing",
				new PersistableBrand());
	}

	@Test
	public void test04CatalogueCannotAddLogo() {
		assertLogoDenied("catalogue");
	}

	@Test
	public void test05CatalogueCannotDeleteLogo() {
		assertDenied("catalogue", HttpMethod.DELETE, "/api/v1/private/store/DEFAULT/marketing/logo", null);
	}

	@Test
	public void test06CatalogueCannotListChildren() {
		assertDenied("catalogue", HttpMethod.GET, "/api/v1/private/merchant/DEFAULT/children?page=0&count=10", null);
	}

	@Test
	public void test07CatalogueCannotDeleteStore() {
		assertDeleteDeniedForGroup("ADMIN_CATALOGUE");
	}

	@Test
	public void test08OrderCannotPutStore() {
		assertDenied("order", HttpMethod.PUT, "/api/v1/private/store/DEFAULT", updateBody());
	}

	@Test
	public void test09OrderCannotGetMarketing() {
		assertDenied("order", HttpMethod.GET, "/api/v1/private/store/DEFAULT/marketing", null);
	}

	@Test
	public void test10OrderCannotSaveMarketing() {
		assertDenied("order", HttpMethod.POST, "/api/v1/private/store/DEFAULT/marketing", new PersistableBrand());
	}

	@Test
	public void test11OrderCannotAddLogo() {
		assertLogoDenied("order");
	}

	@Test
	public void test12OrderCannotDeleteLogo() {
		assertDenied("order", HttpMethod.DELETE, "/api/v1/private/store/DEFAULT/marketing/logo", null);
	}

	@Test
	public void test13OrderCannotListChildren() {
		assertDenied("order", HttpMethod.GET, "/api/v1/private/merchant/DEFAULT/children?page=0&count=10", null);
	}

	@Test
	public void test14OrderCannotDeleteStore() {
		assertDeleteDeniedForGroup("ADMIN_ORDER");
	}

	@Test
	public void test15ContentCannotPutStore() {
		assertDenied("content", HttpMethod.PUT, "/api/v1/private/store/DEFAULT", updateBody());
	}

	@Test
	public void test16ContentCannotGetMarketing() {
		assertDenied("content", HttpMethod.GET, "/api/v1/private/store/DEFAULT/marketing", null);
	}

	@Test
	public void test17ContentCannotSaveMarketing() {
		assertDenied("content", HttpMethod.POST, "/api/v1/private/store/DEFAULT/marketing",
				new PersistableBrand());
	}

	@Test
	public void test18ContentCannotAddLogo() {
		assertLogoDenied("content");
	}

	@Test
	public void test19ContentCannotDeleteLogo() {
		assertDenied("content", HttpMethod.DELETE, "/api/v1/private/store/DEFAULT/marketing/logo", null);
	}

	@Test
	public void test20ContentCannotListChildren() {
		assertDenied("content", HttpMethod.GET, "/api/v1/private/merchant/DEFAULT/children?page=0&count=10", null);
	}

	@Test
	public void test21ContentCannotDeleteStore() {
		assertDeleteDeniedForGroup("ADMIN_CONTENT");
	}

	@Test
	public void test22AdminAndStoreAdminCanManageDefaultStore() {
		for (String username : Arrays.asList("admin", "store")) {
			HttpHeaders headers = userHeaders(username);
			assertEquals(HttpStatus.OK, restTemplate.exchange("/api/v1/private/store/DEFAULT", HttpMethod.PUT,
					new HttpEntity<>(updateBody(), headers), String.class).getStatusCode());
			assertEquals(HttpStatus.OK, restTemplate.exchange("/api/v1/private/store/DEFAULT/marketing",
					HttpMethod.GET, new HttpEntity<>(headers), String.class).getStatusCode());
		}
		assertDefaultStoreUnchanged();

		ensureDeleteStoreExists(getHeader());
		for (String username : Arrays.asList("admin", "store")) {
			assertEquals(HttpStatus.UNAUTHORIZED, restTemplate.exchange("/api/v1/private/store/" + DELETE_STORE,
					HttpMethod.DELETE, new HttpEntity<>(userHeaders(username)), String.class).getStatusCode());
		}
		assertTrue(storeExists(DELETE_STORE));
	}

	@Test
	public void test23AdminAttachedToDeleteStoreCannotDeleteIt() {
		ensureDeleteStoreExists(getHeader());
		assertEquals(HttpStatus.UNAUTHORIZED, restTemplate.exchange("/api/v1/private/store/" + DELETE_STORE,
				HttpMethod.DELETE, new HttpEntity<>(userHeaders("admin-delete")), String.class).getStatusCode());
		assertDefaultStoreUnchanged();
		assertTrue(storeExists(DELETE_STORE));
	}

	@Test
	public void test25MissingAuthorizationCannotPutOrDeleteStore() {
		assertEquals(HttpStatus.UNAUTHORIZED, restTemplate.exchange("/api/v1/private/store/DEFAULT", HttpMethod.PUT,
				new HttpEntity<>(updateBody()), String.class).getStatusCode());
		assertEquals(HttpStatus.UNAUTHORIZED, restTemplate.exchange("/api/v1/private/store/DEFAULT",
				HttpMethod.DELETE, HttpEntity.EMPTY, String.class).getStatusCode());
		assertDefaultStoreUnchanged();
	}

	@Test
	public void test26RetailerCanDeleteChildStore() {
		ensureStoreExists(DELETE_CHILD_STORE, "DEFAULT", getHeader());
		assertEquals(HttpStatus.OK, restTemplate.exchange("/api/v1/private/store/" + DELETE_CHILD_STORE,
				HttpMethod.DELETE, new HttpEntity<>(userHeaders("retailer")), String.class).getStatusCode());
		assertFalse(storeExists(DELETE_CHILD_STORE));
		assertDefaultStoreUnchanged();
	}

	private void assertDenied(String userKey, HttpMethod method, String path, Object body) {
		ResponseEntity<String> response = restTemplate.exchange(path, method,
				new HttpEntity<>(body, userHeaders(userKey)), String.class);
		assertEquals(path, HttpStatus.UNAUTHORIZED, response.getStatusCode());
		assertDefaultStoreUnchanged();
		assertTrue(storeExists(DELETE_STORE));
	}

	private void assertLogoDenied(String userKey) {
		LinkedMultiValueMap<String, Object> parameters = new LinkedMultiValueMap<>();
		parameters.add("file", new ClassPathResource("image.jpg"));
		HttpHeaders headers = new HttpHeaders();
		headers.setContentType(MediaType.MULTIPART_FORM_DATA);
		headers.set("Authorization", userHeaders(userKey).getFirst("Authorization"));

		ResponseEntity<String> response = restTemplate.exchange("/api/v1/private/store/DEFAULT/marketing/logo",
				HttpMethod.POST, new HttpEntity<>(parameters, headers), String.class);
		assertEquals(HttpStatus.UNAUTHORIZED, response.getStatusCode());
	}

	private void assertDeleteDeniedForGroup(String group) {
		ensureDeleteStoreExists(getHeader());
		String username = createAndAuthenticate(group + "-delete-check", group, DELETE_STORE, getHeader());
		assertEquals(HttpStatus.UNAUTHORIZED, restTemplate.exchange("/api/v1/private/store/" + DELETE_STORE,
				HttpMethod.DELETE, new HttpEntity<>(getHeader(username, PASSWORD)), String.class).getStatusCode());
		assertTrue(storeExists(DELETE_STORE));
		assertDefaultStoreUnchanged();
	}

	private void assertDefaultStoreUnchanged() {
		ReadableMerchantStore current = restTemplate.getForObject("/api/v1/store/DEFAULT",
				ReadableMerchantStore.class);
		assertNotNull(current);
		assertEquals(defaultStoreName, current.getName());
		assertEquals(defaultStorePhone, current.getPhone());
	}

	private String createAndAuthenticate(String key, String group, String store, HttpHeaders superadmin) {
		String username = USER_PREFIX + "-" + key.toLowerCase().replaceAll("[^a-z0-9]+", "-") + "@example.com";
		PersistableUser user = new PersistableUser();
		user.setUserName(username);
		user.setEmailAddress(username);
		user.setFirstName("PR151");
		user.setLastName(group);
		user.setPassword(PASSWORD);
		user.setRepeatPassword(PASSWORD);
		user.setDefaultLanguage("en");
		user.setStore(store);
		user.setActive(true);
		user.getGroups().add(new PersistableGroup(group));

		ResponseEntity<ReadableUser> response = restTemplate.exchange("/api/v1/private/user/?store=DEFAULT",
				HttpMethod.POST, new HttpEntity<>(user, superadmin), ReadableUser.class);
		assertEquals(HttpStatus.OK, response.getStatusCode());
		assertNotNull(response.getBody());
		USERNAMES.put(key, username);
		if (key.equals("admin-delete") || key.equals("retailer-delete") || key.equals("admin")
				|| key.equals("store") || key.equals("retailer") || key.equals("catalogue")
				|| key.equals("order") || key.equals("content")) {
			USER_HEADERS.put(key, getHeader(username, PASSWORD));
		}
		return username;
	}

	private HttpHeaders userHeaders(String key) {
		return new HttpHeaders(USER_HEADERS.get(key));
	}

	private boolean storeExists(String code) {
		ResponseEntity<com.salesmanager.shop.model.entity.EntityExists> response = restTemplate.getForEntity(
				"/api/v1/store/unique?code=" + code, com.salesmanager.shop.model.entity.EntityExists.class);
		assertEquals(HttpStatus.OK, response.getStatusCode());
		assertNotNull(response.getBody());
		return response.getBody().isExists();
	}

	private void ensureDeleteStoreExists(HttpHeaders superadmin) {
		ensureStoreExists(DELETE_STORE, null, superadmin);
	}

	private void ensureStoreExists(String code, String parentStore, HttpHeaders superadmin) {
		if (storeExists(code)) {
			return;
		}

		PersistableAddress address = new PersistableAddress();
		address.setAddress("1 PR151 Road");
		address.setPostalCode("12345");
		address.setCountry("US");
		address.setCity("Test City");
		address.setStateProvince("FL");

		PersistableMerchantStore store = new PersistableMerchantStore();
		store.setCode(code);
		store.setCurrency("CAD");
		store.setDefaultLanguage("en");
		store.setEmail(code + "@example.com");
		store.setName(code);
		store.setPhone("555-151-0000");
		store.setSupportedLanguages(Arrays.asList("en"));
		store.setAddress(address);
		if (parentStore != null) {
			store.setRetailer(true);
			store.setRetailerStore(parentStore);
		}

		ResponseEntity<String> response = restTemplate.exchange("/api/v1/private/store/", HttpMethod.POST,
				new HttpEntity<>(store, superadmin), String.class);
		assertEquals(HttpStatus.OK, response.getStatusCode());
	}

	private PersistableMerchantStore updateBody() {
		ReadableMerchantStore current = restTemplate.getForObject("/api/v1/store/DEFAULT", ReadableMerchantStore.class);
		assertNotNull(current);

		PersistableMerchantStore body = new PersistableMerchantStore();
		body.setCode(current.getCode());
		body.setName(current.getName());
		body.setEmail(current.getEmail());
		body.setPhone(current.getPhone());
		body.setCurrency(current.getCurrency());
		body.setDefaultLanguage(current.getDefaultLanguage());
		body.setInBusinessSince(current.getInBusinessSince());
		body.setTemplate(current.getTemplate());
		body.setUseCache(current.isUseCache());
		body.setCurrencyFormatNational(current.isCurrencyFormatNational());
		body.setRetailer(current.isRetailer());
		body.setDimension(current.getDimension());
		body.setWeight(current.getWeight());
		body.setSupportedLanguages(current.getSupportedLanguages() == null ? null
				: current.getSupportedLanguages().stream().map(ReadableLanguage::getCode).collect(Collectors.toList()));
		if (current.getParent() != null) {
			body.setRetailerStore(current.getParent().getCode());
		}
		if (current.getAddress() != null) {
			PersistableAddress address = new PersistableAddress();
			address.setAddress(current.getAddress().getAddress());
			address.setCity(current.getAddress().getCity());
			address.setCountry(current.getAddress().getCountry());
			address.setPostalCode(current.getAddress().getPostalCode());
			address.setStateProvince(current.getAddress().getStateProvince());
			address.setActive(current.getAddress().isActive());
			body.setAddress(address);
		}
		return body;
	}
}
