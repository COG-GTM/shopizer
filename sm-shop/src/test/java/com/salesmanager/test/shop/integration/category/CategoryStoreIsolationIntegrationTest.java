package com.salesmanager.test.shop.integration.category;

import static org.hamcrest.core.Is.is;
import static org.hamcrest.core.IsNot.not;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertThat;

import java.util.Arrays;
import java.util.concurrent.atomic.AtomicInteger;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.SpringBootTest.WebEnvironment;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.test.context.junit4.SpringRunner;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.salesmanager.core.business.services.catalog.category.CategoryService;
import com.salesmanager.core.business.services.merchant.MerchantStoreService;
import com.salesmanager.core.model.catalog.category.Category;
import com.salesmanager.shop.application.ShopApplication;
import com.salesmanager.shop.model.catalog.category.PersistableCategory;
import com.salesmanager.shop.model.references.PersistableAddress;
import com.salesmanager.shop.model.store.PersistableMerchantStore;
import com.salesmanager.shop.model.security.PersistableGroup;
import com.salesmanager.shop.model.user.PersistableUser;
import com.salesmanager.test.shop.common.ServicesTestSupport;

/**
 * Cross-store category access through the real security chain, JPA and H2 (category IDOR fix, PR #196).
 * An ADMIN of store GLOBEX must not be able to update, hide, delete or overwrite a DEFAULT store category.
 */
@SpringBootTest(classes = ShopApplication.class, webEnvironment = WebEnvironment.RANDOM_PORT)
@RunWith(SpringRunner.class)
public class CategoryStoreIsolationIntegrationTest extends ServicesTestSupport {

	private static final String DEFAULT_STORE = "DEFAULT";
	private static final String PASSWORD = "Password1";
	private static final AtomicInteger SEQ = new AtomicInteger();
	private static final ObjectMapper MAPPER = new ObjectMapper();

	private static String globexStore;
	private static String globexAdmin;

	@Autowired
	private TestRestTemplate testRestTemplate;

	@Autowired
	private CategoryService categoryService;

	@Autowired
	private MerchantStoreService merchantStoreService;

	@Before
	public void createSecondStoreAndItsAdmin() {
		if (globexStore != null) {
			return;
		}
		String suffix = Long.toString(System.currentTimeMillis() % 1000000);
		String storeCode = "globex" + suffix;

		PersistableAddress address = new PersistableAddress();
		address.setAddress("1 Globex way");
		address.setPostalCode("12345");
		address.setCountry("US");
		address.setCity("Cypress Creek");
		address.setStateProvince("FL");
		PersistableMerchantStore store = new PersistableMerchantStore();
		store.setCode(storeCode);
		store.setName(storeCode);
		store.setCurrency("CAD");
		store.setDefaultLanguage("en");
		store.setSupportedLanguages(Arrays.asList("en"));
		store.setEmail("globex@test.com");
		store.setPhone("444-555-6666");
		store.setAddress(address);
		ResponseEntity<Void> storeResponse = testRestTemplate.exchange("/api/v1/private/store/", HttpMethod.POST,
				new HttpEntity<>(store, getHeader()), Void.class);
		assertThat(storeResponse.getStatusCode(), is(HttpStatus.OK));

		String userName = "admin" + suffix + "@globex.test";
		PersistableUser user = new PersistableUser();
		user.setDefaultLanguage("en");
		user.setEmailAddress(userName);
		user.setUserName(userName);
		user.setFirstName("Globex");
		user.setLastName("Admin");
		user.setPassword(PASSWORD);
		user.setRepeatPassword(PASSWORD);
		user.setStore(storeCode);
		PersistableGroup group = new PersistableGroup();
		group.setName("ADMIN");
		user.getGroups().add(group);
		ResponseEntity<String> userResponse = testRestTemplate.exchange("/api/v1/private/user/?store=" + storeCode,
				HttpMethod.POST, new HttpEntity<>(user, getHeader()), String.class);
		assertThat(userResponse.getBody(), userResponse.getStatusCode(), is(HttpStatus.OK));

		globexStore = storeCode;
		globexAdmin = userName;
	}

	@Test
	public void foreignStoreAdminCannotUpdateCategory() throws Exception {
		long victimId = createCategory(getHeader(), DEFAULT_STORE, "acme-upd");
		String victimCode = storedCategory(victimId, DEFAULT_STORE).getCode();

		ResponseEntity<String> put = testRestTemplate.exchange(
				"/api/v1/private/category/" + victimId + "?store=" + globexStore, HttpMethod.PUT,
				new HttpEntity<>(category(unique("hijacked"), unique("hijacked")), globexHeader()), String.class);

		assertThat(put.getBody(), put.getStatusCode(), is(HttpStatus.NOT_FOUND));
		assertEquals(victimCode, storedCategory(victimId, DEFAULT_STORE).getCode());
	}

	@Test
	public void foreignStoreAdminCannotChangeVisibility() throws Exception {
		long victimId = createCategory(getHeader(), DEFAULT_STORE, "acme-vis");

		PersistableCategory hide = new PersistableCategory();
		hide.setVisible(false);
		ResponseEntity<String> patch = testRestTemplate.exchange(
				"/api/v1/private/category/" + victimId + "/visible?store=" + globexStore, HttpMethod.PATCH,
				new HttpEntity<>(hide, globexHeader()), String.class);

		assertThat(patch.getBody(), patch.getStatusCode(), is(HttpStatus.NOT_FOUND));
		assertEquals(true, storedCategory(victimId, DEFAULT_STORE).isVisible());
	}

	@Test
	public void foreignStoreAdminCannotDeleteCategory() throws Exception {
		long victimId = createCategory(getHeader(), DEFAULT_STORE, "acme-del");

		ResponseEntity<String> delete = testRestTemplate.exchange(
				"/api/v1/private/category/" + victimId + "?store=" + globexStore, HttpMethod.DELETE,
				new HttpEntity<>(globexHeader()), String.class);

		assertThat(delete.getBody(), delete.getStatusCode(), is(HttpStatus.NOT_FOUND));
		assertNotNull(storedCategory(victimId, DEFAULT_STORE));
	}

	@Test
	public void createWithExistingForeignIdCreatesNewCategory() throws Exception {
		long victimId = createCategory(getHeader(), DEFAULT_STORE, "acme-post");
		String victimCode = storedCategory(victimId, DEFAULT_STORE).getCode();

		String newCode = unique("overwrite");
		PersistableCategory overwrite = category(newCode, newCode);
		overwrite.setId(victimId);
		ResponseEntity<String> post = testRestTemplate.exchange("/api/v1/private/category?store=" + globexStore,
				HttpMethod.POST, new HttpEntity<>(overwrite, globexHeader()), String.class);

		assertThat(post.getBody(), post.getStatusCode(), is(HttpStatus.CREATED));
		long newId = MAPPER.readTree(post.getBody()).get("id").asLong();
		assertThat(newId, not(is(victimId)));
		assertEquals(victimCode, storedCategory(victimId, DEFAULT_STORE).getCode());
		assertEquals(newCode, storedCategory(newId, globexStore).getCode());
	}

	@Test
	public void storeAdminCanStillManageOwnCategories() throws Exception {
		HttpHeaders globex = globexHeader();
		long ownId = createCategory(globex, globexStore, "globex-own");

		String newCode = unique("globex-renamed");
		ResponseEntity<String> put = testRestTemplate.exchange(
				"/api/v1/private/category/" + ownId + "?store=" + globexStore, HttpMethod.PUT,
				new HttpEntity<>(category(newCode, newCode), globex), String.class);
		assertThat(put.getBody(), put.getStatusCode(), is(HttpStatus.OK));
		assertEquals(newCode, storedCategory(ownId, globexStore).getCode());

		PersistableCategory hide = new PersistableCategory();
		hide.setVisible(false);
		ResponseEntity<String> patch = testRestTemplate.exchange(
				"/api/v1/private/category/" + ownId + "/visible?store=" + globexStore, HttpMethod.PATCH,
				new HttpEntity<>(hide, globex), String.class);
		assertThat(patch.getBody(), patch.getStatusCode(), is(HttpStatus.OK));
		assertEquals(false, storedCategory(ownId, globexStore).isVisible());

		ResponseEntity<String> delete = testRestTemplate.exchange(
				"/api/v1/private/category/" + ownId + "?store=" + globexStore, HttpMethod.DELETE,
				new HttpEntity<>(globex), String.class);
		assertThat(delete.getBody(), delete.getStatusCode(), is(HttpStatus.OK));
		assertNull(categoryService.getById(ownId, storeId(globexStore)));
	}

	@Test
	public void defaultStoreAdminCannotTouchSecondStoreCategory() throws Exception {
		long globexId = createCategory(globexHeader(), globexStore, "globex-victim");
		String globexCode = storedCategory(globexId, globexStore).getCode();

		ResponseEntity<String> put = testRestTemplate.exchange(
				"/api/v1/private/category/" + globexId + "?store=" + DEFAULT_STORE, HttpMethod.PUT,
				new HttpEntity<>(category(unique("hijacked"), unique("hijacked")), getHeader()), String.class);
		assertThat(put.getBody(), put.getStatusCode(), is(HttpStatus.NOT_FOUND));

		ResponseEntity<String> delete = testRestTemplate.exchange(
				"/api/v1/private/category/" + globexId + "?store=" + DEFAULT_STORE, HttpMethod.DELETE,
				new HttpEntity<>(getHeader()), String.class);
		assertThat(delete.getBody(), delete.getStatusCode(), is(HttpStatus.NOT_FOUND));

		assertEquals(globexCode, storedCategory(globexId, globexStore).getCode());
	}

	private HttpHeaders globexHeader() {
		return getHeader(globexAdmin, PASSWORD);
	}

	private long createCategory(HttpHeaders headers, String store, String prefix) {
		String code = unique(prefix);
		ResponseEntity<PersistableCategory> response = testRestTemplate.postForEntity(
				"/api/v1/private/category?store=" + store, new HttpEntity<>(category(code, code), headers),
				PersistableCategory.class);
		assertThat(response.getStatusCode(), is(HttpStatus.CREATED));
		return response.getBody().getId();
	}

	private Category storedCategory(long id, String store) throws Exception {
		Category category = categoryService.getById(id, storeId(store));
		assertNotNull("category " + id + " not found in store " + store, category);
		return category;
	}

	private int storeId(String store) throws Exception {
		return merchantStoreService.getByCode(store).getId();
	}

	private static String unique(String prefix) {
		return prefix + "-" + SEQ.incrementAndGet() + "-" + (System.nanoTime() % 100000);
	}
}
