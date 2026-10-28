package com.salesmanager.test.shop.integration.category;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;
import static org.springframework.http.HttpStatus.CREATED;
import static org.springframework.http.HttpStatus.NOT_FOUND;
import static org.springframework.http.HttpStatus.OK;

import java.util.Collections;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.SpringBootTest.WebEnvironment;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.ResponseEntity;
import org.springframework.test.context.junit4.SpringRunner;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import com.fasterxml.jackson.databind.JsonNode;
import com.salesmanager.core.business.services.merchant.MerchantStoreService;
import com.salesmanager.core.business.services.reference.language.LanguageService;
import com.salesmanager.core.model.merchant.MerchantStore;
import com.salesmanager.core.model.reference.language.Language;
import com.salesmanager.shop.application.ShopApplication;
import com.salesmanager.shop.model.catalog.category.Category;
import com.salesmanager.shop.model.catalog.category.CategoryDescription;
import com.salesmanager.shop.model.catalog.category.PersistableCategory;
import com.salesmanager.shop.model.catalog.category.ReadableCategory;
import com.salesmanager.shop.model.catalog.category.ReadableCategoryFull;
import com.salesmanager.shop.model.security.PersistableGroup;
import com.salesmanager.shop.model.store.PersistableMerchantStore;
import com.salesmanager.shop.model.user.PersistableUser;
import com.salesmanager.shop.model.user.ReadableUser;
import com.salesmanager.shop.model.references.PersistableAddress;
import com.salesmanager.shop.store.api.exception.ResourceNotFoundException;
import com.salesmanager.shop.store.security.AuthenticationRequest;
import com.salesmanager.shop.store.security.AuthenticationResponse;
import com.salesmanager.shop.store.controller.category.facade.CategoryFacade;
import com.salesmanager.test.shop.common.ServicesTestSupport;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.SignatureAlgorithm;
import java.util.Date;
import org.springframework.beans.factory.annotation.Value;

@SpringBootTest(classes = ShopApplication.class, webEnvironment = WebEnvironment.RANDOM_PORT)
@RunWith(SpringRunner.class)
public class CategoryStoreScopeIntegrationTest extends ServicesTestSupport {

	private static final String DEFAULT_STORE = "DEFAULT";

	@Autowired
	private TestRestTemplate testRestTemplate;

	@Autowired
	private CategoryFacade categoryFacade;

	@Autowired
	private MerchantStoreService merchantStoreService;

	@Autowired
	private LanguageService languageService;

	@Autowired
	private PlatformTransactionManager transactionManager;

	@Value("${jwt.secret}")
	private String jwtSecret;

	@Test
	public void categoryLookupsAreStoreScopedAndPrivateReadsRequireAuthorization() throws Exception {
		String suffix = UUID.randomUUID().toString().replace("-", "").substring(0, 8);
		String secondStoreCode = "SCOPE" + suffix.toUpperCase();
		createStore(secondStoreCode);
		PersistableCategory storeBCategory = createCategory("scopeb" + suffix, "Store B", true, null, secondStoreCode);

		PersistableCategory parent = createCategory("scopep" + suffix, "Parent", true, null, DEFAULT_STORE);
		PersistableCategory hidden = createCategory("scopeh" + suffix, "Hidden", false, parent.getCode(), DEFAULT_STORE);
		PersistableCategory grandchild =
				createCategory("scopeg" + suffix, "Grandchild", true, hidden.getCode(), DEFAULT_STORE);
		PersistableCategory visibleChild =
				createCategory("scopev" + suffix, "Visible child", true, parent.getCode(), DEFAULT_STORE);

		MerchantStore defaultStore = merchantStoreService.getByCode(DEFAULT_STORE);
		MerchantStore storeB = merchantStoreService.getByCode(secondStoreCode);
		Language english = languageService.getByCode("en");

		new TransactionTemplate(transactionManager).execute(status -> {
			assertNotFound(() -> categoryFacade.getVisibleById(defaultStore, storeBCategory.getId(), english));
			assertNotFound(() -> categoryFacade.getById(defaultStore, storeBCategory.getId(), english));
			assertEquals(storeBCategory.getId(),
					categoryFacade.getVisibleById(storeB, storeBCategory.getId(), english).getId());
			assertNotFound(() -> categoryFacade.getVisibleById(defaultStore, hidden.getId(), english));

			ReadableCategory publicParent = categoryFacade.getVisibleById(defaultStore, parent.getId(), english);
			List<Long> publicChildIds = publicParent.getChildren().stream().map(ReadableCategory::getId)
					.collect(Collectors.toList());
			assertEquals(Collections.singletonList(visibleChild.getId()), publicChildIds);

			ReadableCategory adminParent = categoryFacade.getById(defaultStore, parent.getId(), english);
			assertEquals(2, adminParent.getChildren().size());
			ReadableCategory adminHidden = adminParent.getChildren().stream()
					.filter(category -> hidden.getId().equals(category.getId())).findFirst().orElse(null);
			assertNotNull(adminHidden);
			assertEquals(grandchild.getId(), adminHidden.getChildren().get(0).getId());
			return null;
		});

		String hiddenPath = "/api/v1/private/category/" + hidden.getId() + "?store=" + DEFAULT_STORE;
		ResponseEntity<ReadableCategory> adminRead =
				testRestTemplate.exchange(hiddenPath + "&lang=en", HttpMethod.GET, new HttpEntity<>(getHeader()),
						ReadableCategory.class);
		assertEquals(OK, adminRead.getStatusCode());
		assertEquals(hidden.getId(), adminRead.getBody().getId());
		assertTrue(!adminRead.getBody().isVisible());

		ResponseEntity<ReadableCategoryFull> allLanguages =
				testRestTemplate.exchange(hiddenPath + "&lang=_all", HttpMethod.GET, new HttpEntity<>(getHeader()),
						ReadableCategoryFull.class);
		assertEquals(OK, allLanguages.getStatusCode());
		assertFalse(allLanguages.getBody().getDescriptions().isEmpty());

		ResponseEntity<String> anonymousRead = testRestTemplate.exchange(hiddenPath, HttpMethod.GET,
				new HttpEntity<>(new HttpHeaders()), String.class);
		assertEquals(401, anonymousRead.getStatusCodeValue());

		HttpHeaders invalidHeaders = new HttpHeaders();
		invalidHeaders.set("Authorization", "Bearer " + expiredToken());
		ResponseEntity<String> invalidToken =
				testRestTemplate.exchange(hiddenPath, HttpMethod.GET, new HttpEntity<>(invalidHeaders), String.class);
		assertTrue("Expected 4xx for an invalid token, got " + invalidToken.getStatusCodeValue(),
				invalidToken.getStatusCode().is4xxClientError());

		ResponseEntity<String> wrongStore = testRestTemplate.exchange(
				"/api/v1/private/category/" + storeBCategory.getId() + "?store=" + DEFAULT_STORE, HttpMethod.GET,
				new HttpEntity<>(getHeader()), String.class);
		assertEquals(NOT_FOUND, wrongStore.getStatusCode());

		ResponseEntity<String> missingCategory = testRestTemplate.exchange(
				"/api/v1/private/category/999999?store=" + DEFAULT_STORE, HttpMethod.GET, new HttpEntity<>(getHeader()),
				String.class);
		assertEquals(NOT_FOUND, missingCategory.getStatusCode());

		String scopedAdminName = "scope-admin-" + suffix + "@test.com";
		String scopedAdminToken = createUserAndLogin(scopedAdminName, "ADMIN", secondStoreCode);
		HttpHeaders scopedAdminHeaders = bearer(scopedAdminToken);
		ResponseEntity<String> deniedDefaultStore = testRestTemplate.exchange(
				"/api/v1/private/category/" + parent.getId() + "?store=" + DEFAULT_STORE, HttpMethod.GET,
				new HttpEntity<>(scopedAdminHeaders), String.class);
		assertTrue("Expected 4xx for the other store, got " + deniedDefaultStore.getStatusCodeValue(),
				deniedDefaultStore.getStatusCode().is4xxClientError());

		ResponseEntity<ReadableCategory> allowedStoreB = testRestTemplate.exchange(
				"/api/v1/private/category/" + storeBCategory.getId() + "?store=" + secondStoreCode, HttpMethod.GET,
				new HttpEntity<>(scopedAdminHeaders), ReadableCategory.class);
		assertEquals(OK, allowedStoreB.getStatusCode());

		String orderAdminName = "scope-order-" + suffix + "@test.com";
		String orderAdminToken = createUserAndLogin(orderAdminName, "ADMIN_ORDER", DEFAULT_STORE);
		ResponseEntity<String> nonCatalogueAdmin = testRestTemplate.exchange(
				"/api/v1/private/category/" + parent.getId() + "?store=" + DEFAULT_STORE, HttpMethod.GET,
				new HttpEntity<>(bearer(orderAdminToken)), String.class);
		assertTrue("Expected 4xx for a non-catalogue group, got " + nonCatalogueAdmin.getStatusCodeValue(),
				nonCatalogueAdmin.getStatusCode().is4xxClientError());

		ResponseEntity<JsonNode> apiDocs =
				testRestTemplate.getForEntity("/v2/api-docs", JsonNode.class);
		assertEquals(OK, apiDocs.getStatusCode());
		assertTrue(apiDocs.getBody().path("paths").path("/api/v1/private/category/{id}").has("get"));
	}

	private void createStore(String code) {
		PersistableAddress address = new PersistableAddress();
		address.setAddress("12 Scope Street");
		address.setPostalCode("12345");
		address.setCountry("US");
		address.setCity("Scope City");
		address.setStateProvince("CA");

		PersistableMerchantStore store = new PersistableMerchantStore();
		store.setCode(code);
		store.setCurrency("CAD");
		store.setDefaultLanguage("en");
		store.setEmail(code.toLowerCase() + "@test.com");
		store.setName(code);
		store.setPhone("444-555-6666");
		store.setSupportedLanguages(Collections.singletonList("en"));
		store.setAddress(address);

		ResponseEntity<Void> response = testRestTemplate.exchange("/api/v1/private/store/", HttpMethod.POST,
				new HttpEntity<>(store, getHeader()), Void.class);
		assertEquals(OK, response.getStatusCode());
	}

	private PersistableCategory createCategory(String code, String name, boolean visible, String parentCode,
			String storeCode) {
		PersistableCategory category = category(code, name);
		category.setVisible(visible);
		if (parentCode != null) {
			Category parent = new Category();
			parent.setCode(parentCode);
			category.setParent(parent);
		}
		CategoryDescription description = category.getDescriptions().get(0);
		description.setFriendlyUrl(code);
		description.setTitle(name);

		ResponseEntity<PersistableCategory> response = testRestTemplate.postForEntity(
				"/api/v1/private/category?store=" + storeCode, new HttpEntity<>(category, getHeader()),
				PersistableCategory.class);
		assertEquals(CREATED, response.getStatusCode());
		assertNotNull(response.getBody());
		assertNotNull(response.getBody().getId());
		return response.getBody();
	}

	private String createUserAndLogin(String userName, String groupName, String storeCode) {
		PersistableUser user = new PersistableUser();
		user.setDefaultLanguage("en");
		user.setEmailAddress(userName);
		user.setFirstName("Scope");
		user.setLastName("Admin");
		user.setUserName(userName);
		user.setPassword("Scope1234");
		user.setRepeatPassword("Scope1234");
		user.setStore(storeCode);
		PersistableGroup group = new PersistableGroup();
		group.setName(groupName);
		user.getGroups().add(group);

		ResponseEntity<ReadableUser> created = testRestTemplate.exchange("/api/v1/private/user/?store=" + DEFAULT_STORE,
				HttpMethod.POST, new HttpEntity<>(user, getHeader()), ReadableUser.class);
		assertEquals(OK, created.getStatusCode());

		ResponseEntity<AuthenticationResponse> login =
				testRestTemplate.postForEntity("/api/v1/private/login",
						new HttpEntity<>(new AuthenticationRequest(userName, "Scope1234")),
						AuthenticationResponse.class);
		assertEquals(OK, login.getStatusCode());
		assertNotNull(login.getBody());
		return login.getBody().getToken();
	}

	private String expiredToken() {
		Date now = new Date();
		return Jwts.builder().setSubject("admin@shopizer.com").setIssuedAt(now)
				.setExpiration(new Date(now.getTime() - 1000))
				.signWith(SignatureAlgorithm.HS512, jwtSecret).compact();
	}

	private static HttpHeaders bearer(String token) {
		HttpHeaders headers = new HttpHeaders();
		headers.set("Authorization", "Bearer " + token);
		return headers;
	}

	private static void assertNotFound(Runnable lookup) {
		try {
			lookup.run();
			fail("Expected ResourceNotFoundException");
		} catch (ResourceNotFoundException expected) {
			// expected
		}
	}
}
