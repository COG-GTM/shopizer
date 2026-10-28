package com.salesmanager.test.shop.integration.system;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;
import static org.springframework.http.HttpStatus.FORBIDDEN;
import static org.springframework.http.HttpStatus.OK;
import static org.springframework.http.HttpStatus.UNAUTHORIZED;

import java.nio.charset.Charset;
import java.util.Arrays;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.SpringBootTest.WebEnvironment;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.test.context.junit4.SpringRunner;

import com.salesmanager.core.business.constants.Constants;
import com.salesmanager.core.business.utils.CacheUtils;
import com.salesmanager.core.model.customer.CustomerGender;
import com.salesmanager.shop.application.ShopApplication;
import com.salesmanager.shop.model.customer.PersistableCustomer;
import com.salesmanager.shop.model.customer.address.Address;
import com.salesmanager.shop.model.references.PersistableAddress;
import com.salesmanager.shop.model.security.PersistableGroup;
import com.salesmanager.shop.model.store.PersistableMerchantStore;
import com.salesmanager.shop.model.store.ReadableMerchantStore;
import com.salesmanager.shop.model.user.PersistableUser;
import com.salesmanager.shop.model.user.ReadableUser;
import com.salesmanager.shop.store.security.AuthenticationRequest;
import com.salesmanager.shop.store.security.AuthenticationResponse;
import com.salesmanager.test.shop.common.ServicesTestSupport;

@SpringBootTest(classes = ShopApplication.class, webEnvironment = WebEnvironment.RANDOM_PORT)
@RunWith(SpringRunner.class)
public class CacheApiStoreScopeIntegrationTest extends ServicesTestSupport {

	private static final String PASSWORD = "Password1";

	@Autowired
	private CacheUtils cache;

	@Test
	public void superadminClearsOnlyTheRequestedStore() throws Exception {
		String defaultId = String.valueOf(fetchStore().getId());
		String storeKey = defaultId + "_scope_" + System.nanoTime();
		String otherStoreKey = (Integer.parseInt(defaultId) + 1000) + "_X";
		String referenceKey = "REF_X";
		cache.putInCache("remove", storeKey);
		cache.putInCache("keep", otherStoreKey);
		cache.putInCache("keep", referenceKey);

		ResponseEntity<String> response = clear("DEFAULT", getHeader());

		assertEquals(OK, response.getStatusCode());
		assertNull(cache.getFromCache(storeKey));
		assertEquals("keep", cache.getFromCache(otherStoreKey));
		assertEquals("keep", cache.getFromCache(referenceKey));
	}

	@Test
	public void superadminCanClearOneCacheKey() throws Exception {
		String defaultId = String.valueOf(fetchStore().getId());
		String one = "ONE_" + System.nanoTime();
		String two = "TWO_" + System.nanoTime();
		String oneKey = defaultId + "_" + one;
		String twoKey = defaultId + "_" + two;
		cache.putInCache("remove", oneKey);
		cache.putInCache("keep", twoKey);

		ResponseEntity<String> response = clear("DEFAULT", one, getHeader());

		assertEquals(OK, response.getStatusCode());
		assertNull(cache.getFromCache(oneKey));
		assertEquals("keep", cache.getFromCache(twoKey));
	}

	@Test
	public void unknownStoreReturnsNotFoundAndPreservesCacheKeys() throws Exception {
		String key = String.valueOf(fetchStore().getId()) + "_unknown_" + System.nanoTime();
		cache.putInCache("keep", key);

		ResponseEntity<String> response = clear("NOSUCHSTORE", getHeader());

		assertEquals(org.springframework.http.HttpStatus.NOT_FOUND, response.getStatusCode());
		assertEquals("keep", cache.getFromCache(key));
	}

	@Test
	public void anonymousCannotClearCache() throws Exception {
		String key = String.valueOf(fetchStore().getId()) + "_anonymous_" + System.nanoTime();
		cache.putInCache("keep", key);

		ResponseEntity<String> response = clear("DEFAULT", new HttpHeaders());

		assertEquals(UNAUTHORIZED, response.getStatusCode());
		assertEquals("keep", cache.getFromCache(key));
	}

	@Test
	public void customerCannotClearPrivateOrLegacyCacheRoutes() throws Exception {
		String key = String.valueOf(fetchStore().getId()) + "_customer_" + System.nanoTime();
		cache.putInCache("keep", key);
		HttpHeaders customer = customerHeader();

		ResponseEntity<String> privateRoute = clear("DEFAULT", customer);
		assertFalse(privateRoute.getStatusCode().is2xxSuccessful());
		assertEquals("keep", cache.getFromCache(key));

		ResponseEntity<String> legacyRoute = clearLegacy(customer);
		assertFalse(legacyRoute.getStatusCode().is2xxSuccessful());
		assertEquals("keep", cache.getFromCache(key));
	}

	@Test
	public void storeAdminCanClearOnlyItsOwnStore() throws Exception {
		String storeCode = createStore();
		ReadableMerchantStore createdStore = testRestTemplate.getForObject(
				"/api/v1/store/" + storeCode, ReadableMerchantStore.class);
		assertNotNull(createdStore);
		int ownId = createdStore.getId();
		String defaultId = String.valueOf(fetchStore().getId());
		String ownKey = ownId + "_own_" + System.nanoTime();
		String defaultKey = defaultId + "_default_" + System.nanoTime();
		cache.putInCache("remove", ownKey);
		cache.putInCache("keep", defaultKey);
		String email = "cache-admin-" + System.nanoTime() + "@test.com";
		createUser(email, storeCode, "ADMIN");
		HttpHeaders storeAdmin = getHeader(email, PASSWORD);

		ResponseEntity<String> defaultResponse = clear("DEFAULT", storeAdmin);
		assertEquals(UNAUTHORIZED, defaultResponse.getStatusCode());
		assertEquals("keep", cache.getFromCache(defaultKey));

		ResponseEntity<String> ownResponse = clear(storeCode, storeAdmin);
		assertEquals(OK, ownResponse.getStatusCode());
		assertNull(cache.getFromCache(ownKey));
		assertEquals("keep", cache.getFromCache(defaultKey));
	}

	@Test
	public void nonAdminGroupCannotClearCache() throws Exception {
		String key = String.valueOf(fetchStore().getId()) + "_catalogue_" + System.nanoTime();
		cache.putInCache("keep", key);
		String email = "cache-catalogue-" + System.nanoTime() + "@test.com";
		createUser(email, "DEFAULT", "ADMIN_CATALOGUE");

		ResponseEntity<String> response = clear("DEFAULT", getHeader(email, PASSWORD));

		assertEquals(UNAUTHORIZED, response.getStatusCode());
		assertEquals("keep", cache.getFromCache(key));
	}

	private ResponseEntity<String> clear(String store, HttpHeaders headers) {
		return testRestTemplate.exchange("/api/v1/private/cache/store/" + store + "/clear",
				HttpMethod.DELETE, new HttpEntity<>(headers), String.class);
	}

	private ResponseEntity<String> clear(String store, String cacheKey, HttpHeaders headers) {
		return testRestTemplate.exchange("/api/v1/private/cache/store/" + store + "/clear?cacheKey=" + cacheKey,
				HttpMethod.DELETE, new HttpEntity<>(headers), String.class);
	}

	private ResponseEntity<String> clearLegacy(HttpHeaders headers) {
		return testRestTemplate.exchange("/api/v1/auth/cache/store/DEFAULT/clear",
				HttpMethod.DELETE, new HttpEntity<>(headers), String.class);
	}

	private HttpHeaders customerHeader() {
		String email = "cache-customer-" + System.nanoTime() + "@test.com";
		PersistableCustomer customer = new PersistableCustomer();
		customer.setEmailAddress(email);
		customer.setPassword("clear123");
		customer.setGender(CustomerGender.M.name());
		customer.setLanguage("en");
		Address billing = new Address();
		billing.setFirstName("cache");
		billing.setLastName("customer");
		billing.setCountry("BE");
		customer.setBilling(billing);
		customer.setStoreCode(Constants.DEFAULT_STORE);

		ResponseEntity<PersistableCustomer> registration = testRestTemplate.postForEntity(
				"/api/v1/customer/register", new HttpEntity<>(customer, getHeader()), PersistableCustomer.class);
		assertTrue(registration.getStatusCode().is2xxSuccessful());
		ResponseEntity<AuthenticationResponse> login = testRestTemplate.postForEntity("/api/v1/customer/login",
				new HttpEntity<>(new AuthenticationRequest(email, "clear123")), AuthenticationResponse.class);
		assertEquals(OK, login.getStatusCode());

		HttpHeaders headers = new HttpHeaders();
		headers.setContentType(new MediaType("application", "json", Charset.forName("UTF-8")));
		headers.setBearerAuth(login.getBody().getToken());
		return headers;
	}

	private String createStore() {
		String code = String.format("cs%08x", System.nanoTime() & 0xffffffffL);
		PersistableAddress address = new PersistableAddress();
		address.setAddress("121212 simple address");
		address.setPostalCode("12345");
		address.setCountry("US");
		address.setCity("Test City");
		address.setStateProvince("CA");

		PersistableMerchantStore store = new PersistableMerchantStore();
		store.setCode(code);
		store.setCurrency("CAD");
		store.setDefaultLanguage("en");
		store.setEmail("store-" + code + "@test.com");
		store.setName(code);
		store.setPhone("444-555-6666");
		store.setSupportedLanguages(Arrays.asList("en"));
		store.setAddress(address);
		ResponseEntity<Void> response = testRestTemplate.exchange("/api/v1/private/store/",
				HttpMethod.POST, new HttpEntity<>(store, getHeader()), Void.class);
		assertEquals(OK, response.getStatusCode());
		return code;
	}

	private void createUser(String email, String storeCode, String groupName) {
		PersistableUser user = new PersistableUser();
		user.setDefaultLanguage("en");
		user.setEmailAddress(email);
		user.setFirstName("Cache");
		user.setLastName("User");
		user.setUserName(email);
		user.setPassword(PASSWORD);
		user.setRepeatPassword(PASSWORD);
		PersistableGroup group = new PersistableGroup();
		group.setName(groupName);
		user.getGroups().add(group);

		ResponseEntity<ReadableUser> response = testRestTemplate.exchange(
				"/api/v1/private/user/?store=" + storeCode, HttpMethod.POST,
				new HttpEntity<>(user, getHeader()), ReadableUser.class);
		assertEquals(OK, response.getStatusCode());
		assertNotNull(response.getBody());
	}
}
