package com.salesmanager.test.shop.integration.content;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotEquals;
import static org.junit.Assert.assertNotNull;

import java.util.Arrays;
import java.util.Collections;

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
import com.salesmanager.shop.model.content.box.PersistableContentBox;
import com.salesmanager.shop.model.content.box.ReadableContentBox;
import com.salesmanager.shop.model.content.common.ContentDescription;
import com.salesmanager.shop.model.content.page.PersistableContentPage;
import com.salesmanager.shop.model.content.page.ReadableContentPage;
import com.salesmanager.shop.model.entity.Entity;
import com.salesmanager.shop.model.references.PersistableAddress;
import com.salesmanager.shop.model.security.PersistableGroup;
import com.salesmanager.shop.model.store.PersistableMerchantStore;
import com.salesmanager.shop.model.user.PersistableUser;
import com.salesmanager.shop.model.user.ReadableUser;
import com.salesmanager.shop.store.security.AuthenticationRequest;
import com.salesmanager.shop.store.security.AuthenticationResponse;
import com.salesmanager.test.shop.common.ServicesTestSupport;

@SpringBootTest(classes = ShopApplication.class, webEnvironment = WebEnvironment.RANDOM_PORT)
@RunWith(SpringRunner.class)
public class ContentPageBoxIntegrationTest extends ServicesTestSupport {

	@Test
	public void createPageAndBoxWithForeignIdsKeepsBothStoresSeparate() {
		String storeCode = storeCode();
		createStore(storeCode);

		PersistableContentPage defaultPage = page("default-page-" + storeCode, "Default page");
		Long defaultPageId = createPage(defaultPage, MerchantStore.DEFAULT_STORE);
		PersistableContentPage otherPage = page("other-page-" + storeCode, "Other page");
		otherPage.setId(defaultPageId);
		Long otherPageId = createPage(otherPage, storeCode);
		assertNotEquals(defaultPageId, otherPageId);

		ReadableContentPage originalPage = readPage(defaultPage.getCode(), MerchantStore.DEFAULT_STORE);
		ReadableContentPage createdPage = readPage(otherPage.getCode(), storeCode);
		assertEquals(defaultPage.getCode(), originalPage.getCode());
		assertEquals("Default page", originalPage.getDescription().getName());
		assertEquals(otherPage.getCode(), createdPage.getCode());
		assertEquals("Other page", createdPage.getDescription().getName());

		PersistableContentBox defaultBox = box("default-box-" + storeCode, "Default box");
		Long defaultBoxId = createBox(defaultBox, MerchantStore.DEFAULT_STORE);
		PersistableContentBox otherBox = box("other-box-" + storeCode, "Other box");
		otherBox.setId(defaultBoxId);
		Long otherBoxId = createBox(otherBox, storeCode);
		assertNotEquals(defaultBoxId, otherBoxId);

		ReadableContentBox originalBox = readBox(defaultBox.getCode(), MerchantStore.DEFAULT_STORE);
		ReadableContentBox createdBox = readBox(otherBox.getCode(), storeCode);
		assertEquals("Default box", originalBox.getDescription().getName());
		assertEquals("Other box", createdBox.getDescription().getName());
	}

	@Test
	public void updatePageUsesPathIdAndRejectsOtherStoresPage() {
		String storeCode = storeCode();
		createStore(storeCode);

		PersistableContentPage target = page("target-page-" + storeCode, "Original title");
		Long targetId = createPage(target, MerchantStore.DEFAULT_STORE);
		PersistableContentPage untouched = page("untouched-page-" + storeCode, "Untouched title");
		Long untouchedId = createPage(untouched, MerchantStore.DEFAULT_STORE);

		PersistableContentPage update = page(target.getCode(), "Updated title");
		update.setId(untouchedId);
		updatePage(targetId, update, MerchantStore.DEFAULT_STORE);

		ReadableContentPage updated = readPage(target.getCode(), MerchantStore.DEFAULT_STORE);
		ReadableContentPage unchanged = readPage(untouched.getCode(), MerchantStore.DEFAULT_STORE);
		assertEquals(targetId, updated.getId());
		assertEquals("Updated title", updated.getDescription().getTitle());
		assertEquals(untouchedId, unchanged.getId());
		assertEquals("Untouched title", unchanged.getDescription().getTitle());

		ResponseEntity<String> crossStoreUpdate = testRestTemplate.exchange(
				"/api/v1/private/content/page/" + targetId + "?store=" + storeCode, HttpMethod.PUT,
				new HttpEntity<>(page(target.getCode(), "Wrong store update"), getHeader()), String.class);
		assertEquals(HttpStatus.BAD_REQUEST, crossStoreUpdate.getStatusCode());

		ReadableContentPage afterRejectedUpdate = readPage(target.getCode(), MerchantStore.DEFAULT_STORE);
		assertEquals("Updated title", afterRejectedUpdate.getDescription().getTitle());
	}

	@Test
	public void createContentPageWithoutTokenIsUnauthorized() {
		ResponseEntity<String> response = testRestTemplate.postForEntity(
				"/api/v1/private/content/page?store=DEFAULT",
				new HttpEntity<>(page("unauthenticated-" + storeCode(), "Unauthenticated")),
				String.class);

		assertEquals(HttpStatus.UNAUTHORIZED, response.getStatusCode());
	}

	@Test
	public void storeScopedAdminCanCreatePageWithoutReusingForeignId() {
		String storeCode = storeCode();
		createStore(storeCode);
		Long foreignPageId = createPage(page("admin-default-" + storeCode, "Default page"),
				MerchantStore.DEFAULT_STORE);

		String userName = "admin-" + storeCode + "@example.test";
		PersistableUser user = new PersistableUser();
		user.setDefaultLanguage("en");
		user.setEmailAddress(userName);
		user.setFirstName("Store");
		user.setLastName("Administrator");
		user.setUserName(userName);
		user.setPassword("Password1");
		user.setRepeatPassword("Password1");
		user.setStore(storeCode);
		user.setActive(true);
		user.getGroups().add(new PersistableGroup("ADMIN"));

		ResponseEntity<ReadableUser> createdUser = testRestTemplate.exchange(
				"/api/v1/private/user/?store=DEFAULT", HttpMethod.POST,
				new HttpEntity<>(user, getHeader()), ReadableUser.class);
		assertEquals(HttpStatus.OK, createdUser.getStatusCode());
		assertNotNull(createdUser.getBody());

		ResponseEntity<AuthenticationResponse> login = testRestTemplate.postForEntity(
				"/api/v1/private/login", new HttpEntity<>(new AuthenticationRequest(userName, "Password1")),
				AuthenticationResponse.class);
		assertEquals(HttpStatus.OK, login.getStatusCode());
		assertNotNull(login.getBody());

		HttpHeaders storeAdminHeaders = new HttpHeaders();
		storeAdminHeaders.setBearerAuth(login.getBody().getToken());
		PersistableContentPage page = page("admin-store-page-" + storeCode, "Store page");
		page.setId(foreignPageId);
		ResponseEntity<Entity> createdPage = testRestTemplate.postForEntity(
				"/api/v1/private/content/page?store=" + storeCode,
				new HttpEntity<>(page, storeAdminHeaders), Entity.class);
		assertEquals(HttpStatus.CREATED, createdPage.getStatusCode());
		assertNotNull(createdPage.getBody());
		assertNotEquals(foreignPageId, createdPage.getBody().getId());
	}

	private void createStore(String code) {
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
		store.setEmail(code + "@example.test");
		store.setName(code);
		store.setPhone("444-555-6666");
		store.setSupportedLanguages(Arrays.asList("en"));
		store.setAddress(address);

		ResponseEntity<Void> response = testRestTemplate.exchange("/api/v1/private/store", HttpMethod.POST,
				new HttpEntity<>(store, getHeader()), Void.class);
		assertEquals(HttpStatus.OK, response.getStatusCode());
	}

	private Long createPage(PersistableContentPage page, String store) {
		ResponseEntity<Entity> response = testRestTemplate.postForEntity(
				"/api/v1/private/content/page?store=" + store, new HttpEntity<>(page, getHeader()), Entity.class);
		assertEquals(HttpStatus.CREATED, response.getStatusCode());
		assertNotNull(response.getBody());
		assertNotNull(response.getBody().getId());
		return response.getBody().getId();
	}

	private Long createBox(PersistableContentBox box, String store) {
		ResponseEntity<Entity> response = testRestTemplate.postForEntity(
				"/api/v1/private/content/box?store=" + store, new HttpEntity<>(box, getHeader()), Entity.class);
		assertEquals(HttpStatus.CREATED, response.getStatusCode());
		assertNotNull(response.getBody());
		assertNotNull(response.getBody().getId());
		return response.getBody().getId();
	}

	private void updatePage(Long id, PersistableContentPage page, String store) {
		ResponseEntity<String> response = testRestTemplate.exchange(
				"/api/v1/private/content/page/" + id + "?store=" + store, HttpMethod.PUT,
				new HttpEntity<>(page, getHeader()), String.class);
		assertEquals(HttpStatus.OK, response.getStatusCode());
	}

	private ReadableContentPage readPage(String code, String store) {
		return testRestTemplate.getForEntity(
				"/api/v1/content/pages/" + code + "?store=" + store, ReadableContentPage.class).getBody();
	}

	private ReadableContentBox readBox(String code, String store) {
		return testRestTemplate.getForEntity(
				"/api/v1/content/boxes/" + code + "?store=" + store, ReadableContentBox.class).getBody();
	}

	private static PersistableContentPage page(String code, String title) {
		PersistableContentPage page = new PersistableContentPage();
		page.setId(null);
		page.setCode(code);
		page.setVisible(true);
		page.setDescriptions(Collections.singletonList(description(title)));
		return page;
	}

	private static PersistableContentBox box(String code, String title) {
		PersistableContentBox box = new PersistableContentBox();
		box.setId(null);
		box.setCode(code);
		box.setVisible(true);
		box.setDescriptions(Collections.singletonList(description(title)));
		return box;
	}

	private static ContentDescription description(String title) {
		ContentDescription description = new ContentDescription();
		description.setLanguage("en");
		description.setName(title);
		description.setTitle(title);
		description.setDescription("<p>" + title + "</p>");
		description.setFriendlyUrl(title.toLowerCase().replace(' ', '-'));
		return description;
	}

	private String storeCode() {
		return "IDTEST" + System.nanoTime();
	}
}
