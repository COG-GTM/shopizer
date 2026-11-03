package com.salesmanager.test.shop.integration.store;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.UUID;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.test.context.junit4.SpringRunner;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.SpringBootTest.WebEnvironment;

import com.salesmanager.shop.application.ShopApplication;
import com.salesmanager.shop.model.references.PersistableAddress;
import com.salesmanager.shop.model.security.PersistableGroup;
import com.salesmanager.shop.model.store.PersistableBrand;
import com.salesmanager.shop.model.store.PersistableMerchantStore;
import com.salesmanager.shop.model.user.PersistableUser;
import com.salesmanager.shop.model.user.ReadableUser;
import com.salesmanager.test.shop.common.ServicesTestSupport;

@RunWith(SpringRunner.class)
@SpringBootTest(classes = ShopApplication.class, webEnvironment = WebEnvironment.RANDOM_PORT)
public class MerchantStoreApiRoleAuthorizationIntegrationTest extends ServicesTestSupport {

	private static final String PASSWORD = "Password1";
	private static final String ADMIN_PASSWORD = "password";
	private final String suffix = UUID.randomUUID().toString().replace("-", "").substring(0, 10).toUpperCase();

	@Test
	public void storeRoutesEnforceRoleAndStoreMembership() {
		String catalogueUser = createUser("ADMIN_CATALOGUE", "catalogue");
		String orderUser = createUser("ADMIN_ORDER", "order");

		ResponseEntity<String> catalogueRead = exchange("/api/v1/private/store/DEFAULT/marketing", HttpMethod.GET,
				null, catalogueUser);
		assertEquals(HttpStatus.UNAUTHORIZED, catalogueRead.getStatusCode());
		assertUnauthorizedDefaultRoutes(catalogueUser);
		assertUnauthorized(HttpMethod.PUT, "/api/v1/private/store/DEFAULT", storePayload("DEFAULT", "Not changed"),
				catalogueUser);
		assertUnauthorized(HttpMethod.DELETE, "/api/v1/private/store/DEFAULT", null, catalogueUser);
		assertUnauthorized(HttpMethod.GET, "/api/v1/private/merchant/DEFAULT/children", null, catalogueUser);
		assertEquals(HttpStatus.OK, exchange("/api/v1/store/DEFAULT", HttpMethod.GET, null, null).getStatusCode());

		assertUnauthorized(HttpMethod.PUT, "/api/v1/private/store/DEFAULT", storePayload("DEFAULT", "Not changed"),
				orderUser);
		assertUnauthorized(HttpMethod.DELETE, "/api/v1/private/store/DEFAULT", null, orderUser);

		String ownedStore = createStore("OWN" + suffix);
		String retailerUser = createUser("ADMIN_RETAILER", "retailer", ownedStore);
		assertEquals(HttpStatus.OK,
				exchange("/api/v1/private/store/" + ownedStore + "/marketing", HttpMethod.GET, null, retailerUser)
						.getStatusCode());
		assertEquals(HttpStatus.OK,
				exchange("/api/v1/private/store/" + ownedStore, HttpMethod.PUT,
						storePayload(ownedStore, "Updated " + suffix), retailerUser).getStatusCode());

		String superadminStore = createStore("SUP" + suffix);
		ResponseEntity<String> superadminRead = exchange("/api/v1/private/store/" + superadminStore, HttpMethod.GET,
				null, "admin@shopizer.com");
		assertEquals("Superadmin store read response: " + superadminRead.getBody(), HttpStatus.OK,
				superadminRead.getStatusCode());
		ResponseEntity<String> superadminUpdate = exchange("/api/v1/private/store/" + superadminStore,
				HttpMethod.PUT, storePayload(superadminStore, "Superadmin update"), "admin@shopizer.com");
		assertEquals("Superadmin update response: " + superadminUpdate.getBody(), HttpStatus.OK,
				superadminUpdate.getStatusCode());
		assertEquals(HttpStatus.OK,
				exchange("/api/v1/private/store/" + superadminStore, HttpMethod.DELETE, null, "admin@shopizer.com")
						.getStatusCode());

		ResponseEntity<String> noToken = exchange("/api/v1/private/store/DEFAULT", HttpMethod.PUT,
				storePayload("DEFAULT", "Not changed"), null);
		assertTrue("No-token PUT should be rejected but got " + noToken.getStatusCode(),
				noToken.getStatusCode() == HttpStatus.UNAUTHORIZED || noToken.getStatusCode() == HttpStatus.FORBIDDEN);
	}

	private void assertUnauthorizedDefaultRoutes(String username) {
		assertUnauthorized(HttpMethod.PUT, "/api/v1/private/store/DEFAULT", storePayload("DEFAULT", "Not changed"),
				username);
		assertUnauthorized(HttpMethod.DELETE, "/api/v1/private/store/DEFAULT", null, username);
		assertUnauthorized(HttpMethod.GET, "/api/v1/private/store/DEFAULT/marketing", null, username);
		assertUnauthorized(HttpMethod.POST, "/api/v1/private/store/DEFAULT/marketing", new PersistableBrand(),
				username);
		assertUnauthorized(HttpMethod.POST, "/api/v1/private/store/DEFAULT/marketing/logo", logoUpload(), username);
		assertUnauthorized(HttpMethod.DELETE, "/api/v1/private/store/DEFAULT/marketing/logo", null, username);
		assertUnauthorized(HttpMethod.GET, "/api/v1/private/merchant/DEFAULT/children", null, username);
	}

	private void assertUnauthorized(HttpMethod method, String path, Object body, String username) {
		ResponseEntity<String> response = exchange(path, method, body, username);
		assertTrue(method + " " + path + " expected 401 but got " + response.getStatusCode() + ": "
				+ response.getBody(), response.getStatusCode() == HttpStatus.UNAUTHORIZED);
	}

	private ResponseEntity<String> exchange(String path, HttpMethod method, Object body, String username) {
		String password = "admin@shopizer.com".equals(username) ? ADMIN_PASSWORD : PASSWORD;
		HttpHeaders headers = username == null ? new HttpHeaders() : getHeader(username, password);
		headers.setContentType(body instanceof LinkedMultiValueMap ? MediaType.MULTIPART_FORM_DATA : MediaType.APPLICATION_JSON);
		return testRestTemplate.exchange(path, method, new HttpEntity<>(body, headers), String.class);
	}

	private String createStore(String code) {
		ResponseEntity<Void> response = testRestTemplate.exchange("/api/v1/private/store", HttpMethod.POST,
				new HttpEntity<>(storePayload(code, code), getHeader()), Void.class);
		assertEquals(HttpStatus.OK, response.getStatusCode());
		return code;
	}

	private PersistableMerchantStore storePayload(String code, String name) {
		PersistableAddress address = new PersistableAddress();
		address.setAddress("1 Test Street");
		address.setPostalCode("12345");
		address.setCountry("US");
		address.setCity("Test City");
		address.setStateProvince("CA");

		PersistableMerchantStore store = new PersistableMerchantStore();
		store.setCode(code);
		store.setCurrency("CAD");
		store.setDefaultLanguage("en");
		store.setEmail("test-" + code.toLowerCase() + "@example.com");
		store.setName(name);
		store.setPhone("555-0100");
		store.setSupportedLanguages(Arrays.asList("en"));
		store.setAddress(address);
		return store;
	}

	private String createUser(String groupName, String label) {
		return createUser(groupName, label, "DEFAULT");
	}

	private String createUser(String groupName, String label, String storeCode) {
		String username = label + "-" + suffix.toLowerCase() + "@example.com";
		PersistableUser user = new PersistableUser();
		user.setDefaultLanguage("en");
		user.setEmailAddress(username);
		user.setFirstName("Role");
		user.setLastName(label);
		user.setUserName(username);
		user.setPassword(PASSWORD);
		user.setRepeatPassword(PASSWORD);
		user.setStore(storeCode);

		PersistableGroup group = new PersistableGroup();
		group.setName(groupName);
		user.getGroups().add(group);
		ResponseEntity<ReadableUser> response = testRestTemplate.exchange("/api/v1/private/user/?store=" + storeCode,
				HttpMethod.POST, new HttpEntity<>(user, getHeader()), ReadableUser.class);
		assertEquals("User creation response: " + response.getBody(), HttpStatus.OK, response.getStatusCode());
		assertNotNull(response.getBody());
		assertEquals(storeCode, response.getBody().getMerchant());
		return username;
	}

	private LinkedMultiValueMap<String, Object> logoUpload() {
		LinkedMultiValueMap<String, Object> parts = new LinkedMultiValueMap<>();
		parts.add("file", new ByteArrayResource("logo".getBytes(StandardCharsets.UTF_8)) {
			@Override
			public String getFilename() {
				return "logo.png";
			}
		});
		return parts;
	}
}
