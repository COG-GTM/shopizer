package com.salesmanager.test.shop.integration.category;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;
import static org.springframework.http.HttpStatus.CREATED;
import static org.springframework.http.HttpStatus.NOT_FOUND;
import static org.springframework.http.HttpStatus.OK;

import java.util.Arrays;
import java.util.UUID;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.SpringBootTest.WebEnvironment;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.test.context.junit4.SpringRunner;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.salesmanager.core.business.constants.Constants;
import com.salesmanager.shop.application.ShopApplication;
import com.salesmanager.shop.model.catalog.category.CategoryDescription;
import com.salesmanager.shop.model.catalog.category.PersistableCategory;
import com.salesmanager.shop.model.catalog.category.ReadableCategory;
import com.salesmanager.shop.model.catalog.category.ReadableCategoryList;
import com.salesmanager.shop.model.references.PersistableAddress;
import com.salesmanager.shop.model.security.PersistableGroup;
import com.salesmanager.shop.model.store.PersistableMerchantStore;
import com.salesmanager.shop.model.user.PersistableUser;
import com.salesmanager.shop.model.user.ReadableUser;
import com.salesmanager.test.shop.common.ServicesTestSupport;

@SpringBootTest(classes = ShopApplication.class, webEnvironment = WebEnvironment.RANDOM_PORT)
@RunWith(SpringRunner.class)
public class CategoryStoreScopeIntegrationTest extends ServicesTestSupport {

    @Autowired
    private TestRestTemplate testRestTemplate;

    private final ObjectMapper objectMapper = new ObjectMapper();

    @Test
    public void crossStorePutAndDeleteLeaveTheOwnerCategoryIntact() {
        PersistableCategory original = createCategory(uniqueCode("owned"));
        String storeB = createStore();

        PersistableCategory update = categoryPayload(uniqueCode("changed"));
        ResponseEntity<String> put = testRestTemplate.exchange(
                "/api/v1/private/category/" + original.getId() + "?store=" + storeB, HttpMethod.PUT,
                new HttpEntity<>(update, getHeader()), String.class);
        ResponseEntity<String> delete = testRestTemplate.exchange(
                "/api/v1/private/category/" + original.getId() + "?store=" + storeB, HttpMethod.DELETE,
                new HttpEntity<>(getHeader()), String.class);

        assertEquals(NOT_FOUND, put.getStatusCode());
        assertEquals(NOT_FOUND, delete.getStatusCode());

        ReadableCategory ownerRead = findCategory(Constants.DEFAULT_STORE, original.getCode());
        assertNotNull(ownerRead);
        assertTrue(ownerRead.isVisible());
        assertNull(findCategory(storeB, original.getCode()));
    }

    @Test
    public void storeAdminCannotModifyAnotherStoresCategory() {
        PersistableCategory original = createCategory(uniqueCode("tenant-owned"));
        String storeB = createStore();
        String email = uniqueCode("admin") + "@test.com";
        String password = "Password1";

        PersistableUser user = new PersistableUser();
        user.setDefaultLanguage("en");
        user.setEmailAddress(email);
        user.setFirstName("Store");
        user.setLastName("Admin");
        user.setUserName(email);
        user.setPassword(password);
        user.setRepeatPassword(password);
        user.setStore(storeB);
        user.setActive(true);
        PersistableGroup group = new PersistableGroup();
        group.setName("ADMIN");
        user.getGroups().add(group);

        ResponseEntity<ReadableUser> createdUser = testRestTemplate.exchange(
                "/api/v1/private/user/?store=" + storeB, HttpMethod.POST,
                new HttpEntity<>(user, getHeader()), ReadableUser.class);
        assertEquals(OK, createdUser.getStatusCode());
        assertNotNull(createdUser.getBody());

        org.springframework.http.HttpHeaders storeAdminHeaders = getHeader(email, password);
        PersistableCategory update = categoryPayload(uniqueCode("cross-tenant"));

        ResponseEntity<String> putFromOtherStore = testRestTemplate.exchange(
                "/api/v1/private/category/" + original.getId() + "?store=" + storeB, HttpMethod.PUT,
                new HttpEntity<>(update, storeAdminHeaders), String.class);
        ResponseEntity<String> deleteFromOtherStore = testRestTemplate.exchange(
                "/api/v1/private/category/" + original.getId() + "?store=" + storeB, HttpMethod.DELETE,
                new HttpEntity<>(storeAdminHeaders), String.class);
        assertEquals(NOT_FOUND, putFromOtherStore.getStatusCode());
        assertEquals(NOT_FOUND, deleteFromOtherStore.getStatusCode());

        ResponseEntity<String> defaultStorePut = testRestTemplate.exchange(
                "/api/v1/private/category/" + original.getId() + "?store=" + Constants.DEFAULT_STORE,
                HttpMethod.PUT, new HttpEntity<>(update, storeAdminHeaders), String.class);
        ResponseEntity<String> defaultStoreDelete = testRestTemplate.exchange(
                "/api/v1/private/category/" + original.getId() + "?store=" + Constants.DEFAULT_STORE,
                HttpMethod.DELETE, new HttpEntity<>(storeAdminHeaders), String.class);
        assertFalse(defaultStorePut.getStatusCode().is2xxSuccessful());
        assertFalse(defaultStoreDelete.getStatusCode().is2xxSuccessful());

        ReadableCategory ownerRead = findCategory(Constants.DEFAULT_STORE, original.getCode());
        assertNotNull(ownerRead);
        assertEquals(original.getCode(), ownerRead.getCode());
    }

    @Test
    public void postWithAnExistingIdReturnsBadRequestWithAnErrorMessage() throws Exception {
        PersistableCategory original = createCategory(uniqueCode("existing"));
        PersistableCategory withId = categoryPayload(uniqueCode("post-id"));
        withId.setId(original.getId());

        ResponseEntity<String> response = testRestTemplate.postForEntity(
                "/api/v1/private/category?store=" + Constants.DEFAULT_STORE,
                new HttpEntity<>(withId, getHeader()), String.class);

        assertEquals(HttpStatus.BAD_REQUEST, response.getStatusCode());
        JsonNode body = objectMapper.readTree(response.getBody());
        assertTrue(body.hasNonNull("message"));
        assertFalse(body.get("message").asText().trim().isEmpty());

        ReadableCategory unchanged = findCategory(Constants.DEFAULT_STORE, original.getCode());
        assertNotNull(unchanged);
        assertEquals(original.getCode(), unchanged.getCode());
    }

    @Test
    public void postWithZeroIdCreatesANewCategory() {
        PersistableCategory withZeroId = categoryPayload(uniqueCode("zero-id"));
        withZeroId.setId(0L);

        ResponseEntity<PersistableCategory> response = testRestTemplate.postForEntity(
                "/api/v1/private/category?store=" + Constants.DEFAULT_STORE,
                new HttpEntity<>(withZeroId, getHeader()), PersistableCategory.class);

        assertEquals(CREATED, response.getStatusCode());
        assertNotNull(response.getBody());
        assertNotNull(response.getBody().getId());
        assertNotEquals(Long.valueOf(0L), response.getBody().getId());
    }

    @Test
    public void ownerPutAndDeleteOfMissingCategoriesReturnNotFound() {
        PersistableCategory update = categoryPayload(uniqueCode("missing"));

        ResponseEntity<String> put = testRestTemplate.exchange(
                "/api/v1/private/category/999999?store=" + Constants.DEFAULT_STORE, HttpMethod.PUT,
                new HttpEntity<>(update, getHeader()), String.class);
        ResponseEntity<String> delete = testRestTemplate.exchange(
                "/api/v1/private/category/999999?store=" + Constants.DEFAULT_STORE, HttpMethod.DELETE,
                new HttpEntity<>(getHeader()), String.class);

        assertEquals(NOT_FOUND, put.getStatusCode());
        assertEquals(NOT_FOUND, delete.getStatusCode());
    }

    @Test
    public void ownerPutPersistsUpdatesAndRetainsTheStoreAndId() {
        PersistableCategory original = createCategory(uniqueCode("persisted"));
        PersistableCategory update = categoryPayload(original.getCode());
        update.setSortOrder(12);
        update.setVisible(false);

        ResponseEntity<PersistableCategory> put = testRestTemplate.exchange(
                "/api/v1/private/category/" + original.getId() + "?store=" + Constants.DEFAULT_STORE,
                HttpMethod.PUT, new HttpEntity<>(update, getHeader()), PersistableCategory.class);
        assertEquals(OK, put.getStatusCode());

        ReadableCategory read = findCategory(Constants.DEFAULT_STORE, original.getCode());
        assertNotNull(read);
        assertEquals(original.getId(), read.getId());
        assertEquals(original.getCode(), read.getCode());
        assertEquals(12, read.getSortOrder());
        assertFalse(read.isVisible());
        assertEquals(Constants.DEFAULT_STORE, read.getStore());
    }

    @Test
    public void visibilityPatchFromAnotherStoreDoesNotChangeTheCategory() {
        PersistableCategory original = createCategory(uniqueCode("visible"));
        String storeB = createStore();
        PersistableCategory hide = new PersistableCategory();
        hide.setVisible(false);

        ResponseEntity<String> patch = testRestTemplate.exchange(
                "/api/v1/private/category/" + original.getId() + "/visible?store=" + storeB,
                HttpMethod.PATCH, new HttpEntity<>(hide, getHeader()), String.class);
        assertFalse(patch.getStatusCode().is2xxSuccessful());

        ReadableCategory ownerRead = findCategory(Constants.DEFAULT_STORE, original.getCode());
        assertNotNull(ownerRead);
        assertTrue(ownerRead.isVisible());
    }

    private ReadableCategory findCategory(String store, String code) {
        ResponseEntity<ReadableCategoryList> response = testRestTemplate.exchange(
                "/api/v1/category?store=" + store + "&lang=en&count=1000", HttpMethod.GET,
                new HttpEntity<>(getHeader()), ReadableCategoryList.class);
        assertEquals(OK, response.getStatusCode());
        return response.getBody().getCategories().stream()
                .filter(category -> code.equals(category.getCode()))
                .findFirst()
                .orElse(null);
    }

    private PersistableCategory createCategory(String code) {
        PersistableCategory request = categoryPayload(code);
        ResponseEntity<PersistableCategory> response = testRestTemplate.postForEntity(
                "/api/v1/private/category?store=" + Constants.DEFAULT_STORE,
                new HttpEntity<>(request, getHeader()), PersistableCategory.class);
        assertEquals(CREATED, response.getStatusCode());
        assertNotNull(response.getBody());
        assertNotNull(response.getBody().getId());
        return response.getBody();
    }

    private String createStore() {
        String code = uniqueCode("store");
        PersistableAddress address = new PersistableAddress();
        address.setAddress("1 scope street");
        address.setPostalCode("12345");
        address.setCountry("US");
        address.setCity("Test City");
        address.setStateProvince("CA");

        PersistableMerchantStore store = new PersistableMerchantStore();
        store.setCode(code);
        store.setCurrency("CAD");
        store.setDefaultLanguage("en");
        store.setEmail(code + "@test.com");
        store.setName(code);
        store.setPhone("444-555-6666");
        store.setSupportedLanguages(Arrays.asList("en"));
        store.setAddress(address);

        ResponseEntity<Void> response = testRestTemplate.exchange("/api/v1/private/store/",
                HttpMethod.POST, new HttpEntity<>(store, getHeader()), Void.class);
        assertEquals(OK, response.getStatusCode());
        return code;
    }

    private PersistableCategory categoryPayload(String code) {
        PersistableCategory category = new PersistableCategory();
        category.setCode(code);
        category.setSortOrder(1);
        category.setVisible(true);

        CategoryDescription description = new CategoryDescription();
        description.setLanguage("en");
        description.setName(code);
        description.setFriendlyUrl(code);
        description.setTitle(code);
        category.setDescriptions(Arrays.asList(description));
        return category;
    }

    private String uniqueCode(String prefix) {
        return prefix + UUID.randomUUID().toString().replace("-", "");
    }
}
