package com.salesmanager.test.shop.integration.store;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;

import java.util.Arrays;

import com.salesmanager.shop.application.ShopApplication;
import com.salesmanager.shop.model.references.PersistableAddress;
import com.salesmanager.shop.model.security.PersistableGroup;
import com.salesmanager.shop.model.store.PersistableMerchantStore;
import com.salesmanager.shop.model.store.ReadableMerchantStore;
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
public class StoreHierarchyAuthorizationIntegrationTest extends ServicesTestSupport {

    private static final String SUFFIX = String.valueOf(System.nanoTime() % 100000L);
    private static final String PARENT = "HIERARCHY-P-" + SUFFIX;
    private static final String CHILD = "HIERARCHY-C-" + SUFFIX;
    private static final String GRANDCHILD = "HIERARCHY-G-" + SUFFIX;
    private static final String UNRELATED = "HIERARCHY-U-" + SUFFIX;
    private static final String PASSWORD = "Password1";
    private static final String PARENT_ADMIN = "hierarchy-parent-" + SUFFIX + "@example.com";
    private static final String CHILD_ADMIN = "hierarchy-child-" + SUFFIX + "@example.com";
    private static final String GRANDCHILD_ADMIN = "hierarchy-grandchild-" + SUFFIX + "@example.com";
    private static final String UNRELATED_ADMIN = "hierarchy-unrelated-" + SUFFIX + "@example.com";

    private static boolean setupComplete;

    @Autowired
    private TestRestTemplate testRestTemplate;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Before
    public void createHierarchyOnce() {
        // PageableMerchantRepository.listByGroup uses an unqualified MERCHANT_STORE native query (no {h-schema}),
        // which fails on the H2 test schema SALESMANAGER for non-superadmin /private/stores calls.
        jdbcTemplate.execute(
                "CREATE VIEW IF NOT EXISTS PUBLIC.MERCHANT_STORE AS SELECT * FROM SALESMANAGER.MERCHANT_STORE");
        if (setupComplete) {
            return;
        }

        createStore(PARENT, null);
        createStore(CHILD, PARENT);
        createStore(GRANDCHILD, CHILD);
        createStore(UNRELATED, null);

        ResponseEntity<ReadableMerchantStore> childResponse = testRestTemplate.exchange(
                "/api/v1/store/" + CHILD, HttpMethod.GET, new HttpEntity<>(getHeader()),
                ReadableMerchantStore.class);
        assertEquals(childResponse.toString(), HttpStatus.OK, childResponse.getStatusCode());
        assertNotNull(childResponse.getBody());
        assertNotNull("Readable child store must include its parent", childResponse.getBody().getParent());
        assertEquals(PARENT, childResponse.getBody().getParent().getCode());

        createAdmin(PARENT_ADMIN, PARENT);
        createAdmin(CHILD_ADMIN, CHILD);
        createAdmin(GRANDCHILD_ADMIN, GRANDCHILD);
        createAdmin(UNRELATED_ADMIN, UNRELATED);
        setupComplete = true;
    }

    @Test
    public void parentAdminCanAccessOwnStoreAndChildButNotGrandchild() {
        assertProbe(PARENT_ADMIN, PARENT, HttpStatus.OK);
        assertProbe(PARENT_ADMIN, CHILD, HttpStatus.OK);
        assertProbe(PARENT_ADMIN, GRANDCHILD, HttpStatus.UNAUTHORIZED);
    }

    @Test
    public void childAdminCanAccessOwnStoreAndGrandchildButNotParent() {
        assertProbe(CHILD_ADMIN, CHILD, HttpStatus.OK);
        assertProbe(CHILD_ADMIN, PARENT, HttpStatus.UNAUTHORIZED);
        assertProbe(CHILD_ADMIN, GRANDCHILD, HttpStatus.OK);
    }

    @Test
    public void grandchildAdminCannotAccessAncestors() {
        assertProbe(GRANDCHILD_ADMIN, GRANDCHILD, HttpStatus.OK);
        assertProbe(GRANDCHILD_ADMIN, CHILD, HttpStatus.UNAUTHORIZED);
        assertProbe(GRANDCHILD_ADMIN, PARENT, HttpStatus.UNAUTHORIZED);
    }

    @Test
    public void unrelatedAdminCannotAccessTheHierarchy() {
        assertProbe(UNRELATED_ADMIN, PARENT, HttpStatus.UNAUTHORIZED);
        assertProbe(UNRELATED_ADMIN, CHILD, HttpStatus.UNAUTHORIZED);
        assertProbe(UNRELATED_ADMIN, GRANDCHILD, HttpStatus.UNAUTHORIZED);
    }

    @Test
    public void superadminCanAccessGrandchild() {
        assertProbe("admin@shopizer.com", getHeader(), GRANDCHILD, HttpStatus.OK);
    }

    @Test
    public void hierarchyProbeRequiresAValidBearerToken() {
        HttpHeaders noToken = new HttpHeaders();
        ResponseEntity<String> anonymous = testRestTemplate.exchange(
                "/api/v1/private/stores?store=" + GRANDCHILD, HttpMethod.GET,
                new HttpEntity<>(noToken), String.class);
        assertEquals(anonymous.toString(), HttpStatus.UNAUTHORIZED, anonymous.getStatusCode());

        HttpHeaders invalidToken = new HttpHeaders();
        invalidToken.set("Authorization", "Bearer ");
        ResponseEntity<String> invalid = testRestTemplate.exchange(
                "/api/v1/private/stores?store=" + GRANDCHILD, HttpMethod.GET,
                new HttpEntity<>(invalidToken), String.class);
        assertEquals(invalid.toString(), HttpStatus.UNAUTHORIZED, invalid.getStatusCode());
    }

    private void assertProbe(String username, String storeCode, HttpStatus expectedStatus) {
        assertProbe(username, getHeader(username, PASSWORD), storeCode, expectedStatus);
    }

    private void assertProbe(String username, HttpHeaders headers, String storeCode, HttpStatus expectedStatus) {
        ResponseEntity<String> response = testRestTemplate.exchange(
                "/api/v1/private/stores?store=" + storeCode, HttpMethod.GET,
                new HttpEntity<>(headers), String.class);
        assertEquals(username + " requesting " + storeCode + ": " + response,
                expectedStatus, response.getStatusCode());
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
        user.setFirstName("Hierarchy");
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
