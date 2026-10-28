package com.salesmanager.test.shop.uat;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;

import java.util.Arrays;

import com.salesmanager.shop.application.ShopApplication;
import com.salesmanager.shop.model.references.PersistableAddress;
import com.salesmanager.shop.model.security.PersistableGroup;
import com.salesmanager.shop.model.store.PersistableMerchantStore;
import com.salesmanager.shop.model.user.PersistableUser;
import com.salesmanager.shop.model.user.ReadableUser;
import com.salesmanager.test.shop.common.ServicesTestSupport;
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
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.junit4.SpringRunner;

@SpringBootTest(classes = ShopApplication.class, webEnvironment = WebEnvironment.RANDOM_PORT)
@RunWith(SpringRunner.class)
public class StoreAdminHierarchyJourneyTest extends ServicesTestSupport {

    private static final String SUFFIX = String.valueOf(System.nanoTime() % 100000L);
    private static final String PARENT = "UAT-P-" + SUFFIX;
    private static final String CHILD = "UAT-C-" + SUFFIX;
    private static final String PASSWORD = "Password1";
    private static final String PARENT_ADMIN = "uat-parent-" + SUFFIX + "@example.com";
    private static final String CHILD_ADMIN = "uat-child-" + SUFFIX + "@example.com";

    private static boolean setupComplete;

    @Autowired
    private TestRestTemplate testRestTemplate;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Before
    public void createHierarchyOnce() {
        // PageableMerchantRepository.listByStore uses an unqualified MERCHANT_STORE native query (no {h-schema}),
        // which fails on the H2 test schema SALESMANAGER for non-superadmin /private/stores calls.
        jdbcTemplate.execute(
                "CREATE VIEW IF NOT EXISTS PUBLIC.MERCHANT_STORE AS SELECT * FROM SALESMANAGER.MERCHANT_STORE");
        if (setupComplete) {
            return;
        }

        createStore(PARENT, null);
        createStore(CHILD, PARENT);
        createAdmin(PARENT_ADMIN, PARENT);
        createAdmin(CHILD_ADMIN, CHILD);
        setupComplete = true;
    }

    @Test
    public void parentAdminCanWorkInChildStoreButChildAdminCannotAccessParent() {
        HttpHeaders parentHeaders = getHeader(PARENT_ADMIN, PASSWORD);
        ResponseEntity<String> stores = testRestTemplate.exchange(
                "/api/v1/private/stores?store=" + CHILD, HttpMethod.GET,
                new HttpEntity<>(parentHeaders), String.class);
        assertEquals(stores.toString(), HttpStatus.OK, stores.getStatusCode());

        ResponseEntity<String> users = testRestTemplate.exchange(
                "/api/v1/private/users?store=" + CHILD, HttpMethod.GET,
                new HttpEntity<>(parentHeaders), String.class);
        assertEquals(users.toString(), HttpStatus.OK, users.getStatusCode());

        PersistableUser childUser = new PersistableUser();
        childUser.setDefaultLanguage("en");
        childUser.setEmailAddress("uat-child-created-" + SUFFIX + "@example.com");
        childUser.setFirstName("Created");
        childUser.setLastName("In Child");
        childUser.setUserName(childUser.getEmailAddress());
        childUser.setPassword(PASSWORD);
        childUser.setRepeatPassword(PASSWORD);
        childUser.getGroups().add(new PersistableGroup("ADMIN"));

        ResponseEntity<ReadableUser> created = testRestTemplate.exchange(
                "/api/v1/private/user/?store=" + CHILD, HttpMethod.POST,
                new HttpEntity<>(childUser, parentHeaders), ReadableUser.class);
        assertEquals(created.toString(), HttpStatus.OK, created.getStatusCode());
        assertNotNull(created.getBody());

        ResponseEntity<String> parentRequestedByChild = testRestTemplate.exchange(
                "/api/v1/private/stores?store=" + PARENT, HttpMethod.GET,
                new HttpEntity<>(getHeader(CHILD_ADMIN, PASSWORD)), String.class);
        assertEquals(parentRequestedByChild.toString(), HttpStatus.UNAUTHORIZED,
                parentRequestedByChild.getStatusCode());
    }

    private void createStore(String code, String parentCode) {
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
        store.setEmail(code.toLowerCase() + "@example.com");
        store.setName(code);
        store.setPhone("444-555-6666");
        store.setSupportedLanguages(Arrays.asList("en"));
        store.setAddress(address);
        store.setRetailerStore(parentCode);

        ResponseEntity<Void> response = testRestTemplate.exchange("/api/v1/private/store/",
                HttpMethod.POST, new HttpEntity<>(store, getHeader()), Void.class);
        assertEquals("Store creation failed for " + code + ": " + response,
                HttpStatus.OK, response.getStatusCode());
    }

    private void createAdmin(String username, String storeCode) {
        PersistableUser user = new PersistableUser();
        user.setDefaultLanguage("en");
        user.setEmailAddress(username);
        user.setFirstName("UAT");
        user.setLastName("Admin");
        user.setUserName(username);
        user.setPassword(PASSWORD);
        user.setRepeatPassword(PASSWORD);
        user.getGroups().add(new PersistableGroup("ADMIN"));

        ResponseEntity<ReadableUser> response = testRestTemplate.exchange(
                "/api/v1/private/user/?store=" + storeCode, HttpMethod.POST,
                new HttpEntity<>(user, getHeader()), ReadableUser.class);
        assertEquals("User creation failed for " + username + ": " + response,
                HttpStatus.OK, response.getStatusCode());
        assertNotNull(response.getBody());
    }
}
