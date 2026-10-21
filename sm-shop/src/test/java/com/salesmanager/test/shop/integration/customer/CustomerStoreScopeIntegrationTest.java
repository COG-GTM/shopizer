package com.salesmanager.test.shop.integration.customer;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;

import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
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
import com.salesmanager.core.business.constants.Constants;
import com.salesmanager.core.model.customer.CustomerGender;
import com.salesmanager.shop.application.ShopApplication;
import com.salesmanager.shop.model.customer.PersistableCustomer;
import com.salesmanager.shop.model.customer.ReadableCustomer;
import com.salesmanager.shop.model.customer.address.Address;
import com.salesmanager.shop.model.security.PersistableGroup;
import com.salesmanager.shop.model.references.PersistableAddress;
import com.salesmanager.shop.model.store.PersistableMerchantStore;
import com.salesmanager.shop.model.user.PersistableUser;
import com.salesmanager.shop.model.user.ReadableUser;
import com.salesmanager.shop.store.security.AuthenticationRequest;
import com.salesmanager.shop.store.security.AuthenticationResponse;
import com.salesmanager.test.shop.common.ServicesTestSupport;

@SpringBootTest(classes = ShopApplication.class, webEnvironment = WebEnvironment.RANDOM_PORT)
@RunWith(SpringRunner.class)
public class CustomerStoreScopeIntegrationTest extends ServicesTestSupport {

  private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

  @Test
  public void crossStoreMutationAttemptsLeaveCustomerUnchanged() {
    HttpHeaders headers = getHeader();
    String otherStore = "scope-cross";
    String email = "scope-cross.customer@test.com";
    createStore(headers, otherStore);
    Long id = registerCustomer(headers, Constants.DEFAULT_STORE, email, "Original");

    String otherStoreCustomerUrl = customerUrl(id, otherStore);
    ResponseEntity<String> update = testRestTemplate.exchange(otherStoreCustomerUrl, HttpMethod.PUT,
        new HttpEntity<>(customer(email, Constants.DEFAULT_STORE, "Changed", "changed123"), headers),
        String.class);
    assertEquals(HttpStatus.NOT_FOUND, update.getStatusCode());

    ResponseEntity<AuthenticationResponse> login = testRestTemplate.postForEntity(
        "/api/v1/customer/login", new HttpEntity<>(new AuthenticationRequest(email, "clear123")),
        AuthenticationResponse.class);
    assertEquals(HttpStatus.OK, login.getStatusCode());
    assertNotNull(login.getBody().getToken());

    ResponseEntity<String> addressUpdate = testRestTemplate.exchange(
        "/api/v1/private/customer/" + id + "/address?store=" + otherStore,
        HttpMethod.PATCH,
        new HttpEntity<>(customer(email, Constants.DEFAULT_STORE, "Changed address", null), headers),
        String.class);
    assertEquals(HttpStatus.NOT_FOUND, addressUpdate.getStatusCode());

    ReadableCustomer original = getCustomer(headers, id, Constants.DEFAULT_STORE);
    assertEquals("Original", original.getBilling().getFirstName());
    assertEquals("1 scope street", original.getBilling().getAddress());

    ResponseEntity<String> delete = testRestTemplate.exchange(otherStoreCustomerUrl, HttpMethod.DELETE,
        new HttpEntity<>(headers), String.class);
    assertEquals(HttpStatus.NOT_FOUND, delete.getStatusCode());
    assertEquals(HttpStatus.OK,
        testRestTemplate.exchange(customerUrl(id, Constants.DEFAULT_STORE), HttpMethod.GET,
            new HttpEntity<>(headers), String.class).getStatusCode());
  }

  @Test
  public void sameStoreUpdateAddressAndDeleteRemainAvailable() {
    HttpHeaders headers = getHeader();
    String email = "scope-same.customer@test.com";
    Long id = registerCustomer(headers, Constants.DEFAULT_STORE, email, "Original");
    String customerUrl = customerUrl(id, Constants.DEFAULT_STORE);

    ResponseEntity<String> update = testRestTemplate.exchange(customerUrl, HttpMethod.PUT,
        new HttpEntity<>(customer(email, Constants.DEFAULT_STORE, "Updated", "clear123"), headers),
        String.class);
    assertEquals(HttpStatus.OK, update.getStatusCode());
    assertEquals("Updated", getCustomer(headers, id, Constants.DEFAULT_STORE).getBilling().getFirstName());

    PersistableCustomer addressUpdate =
        customer(email, Constants.DEFAULT_STORE, "Address updated", "clear123");
    addressUpdate.getBilling().setAddress("2 scope street");
    ResponseEntity<String> address = testRestTemplate.exchange(
        "/api/v1/private/customer/" + id + "/address?store=" + Constants.DEFAULT_STORE,
        HttpMethod.PATCH, new HttpEntity<>(addressUpdate, headers), String.class);
    assertEquals(HttpStatus.OK, address.getStatusCode());
    assertEquals("2 scope street",
        getCustomer(headers, id, Constants.DEFAULT_STORE).getBilling().getAddress());

    ResponseEntity<String> delete = testRestTemplate.exchange(customerUrl, HttpMethod.DELETE,
        new HttpEntity<>(headers), String.class);
    assertEquals(HttpStatus.OK, delete.getStatusCode());
    assertEquals(HttpStatus.NOT_FOUND,
        testRestTemplate.exchange(customerUrl, HttpMethod.GET, new HttpEntity<>(headers),
            String.class).getStatusCode());
  }

  @Test
  public void customerInOtherStoreCannotBeReadThroughDefaultStore() {
    HttpHeaders headers = getHeader();
    String otherStore = "scope-reverse";
    String email = "scope-reverse.customer@test.com";
    createStore(headers, otherStore);
    Long id = registerCustomer(headers, otherStore, email, "Other store");

    assertEquals(HttpStatus.NOT_FOUND,
        testRestTemplate.exchange(customerUrl(id, Constants.DEFAULT_STORE), HttpMethod.GET,
            new HttpEntity<>(headers), String.class).getStatusCode());
    assertEquals(HttpStatus.OK,
        testRestTemplate.exchange(customerUrl(id, otherStore), HttpMethod.GET,
            new HttpEntity<>(headers), String.class).getStatusCode());
  }

  @Test
  public void crossStoreAndMissingCustomerHaveSameErrorFields() throws Exception {
    HttpHeaders headers = getHeader();
    String otherStore = "scope-oracle";
    createStore(headers, otherStore);
    Long id = registerCustomer(headers, Constants.DEFAULT_STORE,
        "scope-oracle.customer@test.com", "Oracle");

    ResponseEntity<String> crossStore = testRestTemplate.exchange(
        customerUrl(id, otherStore), HttpMethod.GET, new HttpEntity<>(headers), String.class);
    ResponseEntity<String> missing = testRestTemplate.exchange(
        customerUrl(999999L, Constants.DEFAULT_STORE), HttpMethod.GET,
        new HttpEntity<>(headers), String.class);
    assertEquals(HttpStatus.NOT_FOUND, crossStore.getStatusCode());
    assertEquals(HttpStatus.NOT_FOUND, missing.getStatusCode());

    JsonNode crossStoreJson = OBJECT_MAPPER.readTree(crossStore.getBody());
    JsonNode missingJson = OBJECT_MAPPER.readTree(missing.getBody());
    assertEquals(fieldNames(crossStoreJson), fieldNames(missingJson));
  }

  @Test
  public void storeAdminGetsNotFoundForOtherStoreCustomer() {
    HttpHeaders superAdminHeaders = getHeader();
    String otherStore = "scope-admin";
    createStore(superAdminHeaders, otherStore);
    Long defaultCustomerId = registerCustomer(superAdminHeaders, Constants.DEFAULT_STORE,
        "scope-admin.customer@test.com", "Default customer");

    PersistableUser admin = new PersistableUser();
    admin.setDefaultLanguage("en");
    admin.setEmailAddress("scope-admin.user@test.com");
    admin.setFirstName("Store");
    admin.setLastName("Admin");
    admin.setUserName(admin.getEmailAddress());
    admin.setPassword("Password1");
    admin.setRepeatPassword("Password1");
    admin.setActive(true);
    PersistableGroup group = new PersistableGroup();
    group.setName("ADMIN");
    admin.getGroups().add(group);

    ResponseEntity<ReadableUser> created = testRestTemplate.exchange(
        "/api/v1/private/user/?store=" + otherStore, HttpMethod.POST,
        new HttpEntity<>(admin, superAdminHeaders), ReadableUser.class);
    assertEquals(HttpStatus.OK, created.getStatusCode());
    assertNotNull(created.getBody());

    ResponseEntity<AuthenticationResponse> login = testRestTemplate.postForEntity(
        "/api/v1/private/login",
        new HttpEntity<>(new AuthenticationRequest(admin.getEmailAddress(), "Password1")),
        AuthenticationResponse.class);
    assertEquals(HttpStatus.OK, login.getStatusCode());
    HttpHeaders storeAdminHeaders = bearerHeaders(login.getBody().getToken());

    assertEquals(HttpStatus.NOT_FOUND,
        testRestTemplate.exchange(customerUrl(defaultCustomerId, otherStore), HttpMethod.GET,
            new HttpEntity<>(storeAdminHeaders), String.class).getStatusCode());
    assertEquals(HttpStatus.UNAUTHORIZED,
        testRestTemplate.exchange(customerUrl(defaultCustomerId, Constants.DEFAULT_STORE),
            HttpMethod.GET, new HttpEntity<>(storeAdminHeaders), String.class).getStatusCode());
  }

  private void createStore(HttpHeaders headers, String code) {
    PersistableAddress address = new PersistableAddress();
    address.setAddress("1 " + code + " street");
    address.setPostalCode("12345");
    address.setCountry("US");
    address.setCity("FT LD");
    address.setStateProvince("FL");

    PersistableMerchantStore store = new PersistableMerchantStore();
    store.setCode(code);
    store.setCurrency("CAD");
    store.setDefaultLanguage("en");
    store.setEmail(code + "@test.com");
    store.setName(code);
    store.setPhone("444-555-6666");
    store.setSupportedLanguages(Arrays.asList("en"));
    store.setAddress(address);

    ResponseEntity<String> response = testRestTemplate.exchange("/api/v1/private/store/",
        HttpMethod.POST, new HttpEntity<>(store, headers), String.class);
    assertEquals(HttpStatus.OK, response.getStatusCode());
  }

  private Long registerCustomer(HttpHeaders headers, String storeCode, String email,
      String firstName) {
    PersistableCustomer customer = customer(email, storeCode, firstName, "clear123");
    ResponseEntity<AuthenticationResponse> response = testRestTemplate.postForEntity(
        "/api/v1/customer/register?store=" + storeCode, new HttpEntity<>(customer, headers),
        AuthenticationResponse.class);
    assertEquals(HttpStatus.OK, response.getStatusCode());
    assertNotNull(response.getBody());
    return response.getBody().getId();
  }

  private PersistableCustomer customer(String email, String storeCode, String firstName,
      String password) {
    PersistableCustomer customer = new PersistableCustomer();
    customer.setEmailAddress(email);
    customer.setPassword(password);
    customer.setGender(CustomerGender.M.name());
    customer.setLanguage("en");
    customer.setStoreCode(storeCode);
    Address billing = new Address();
    billing.setFirstName(firstName);
    billing.setLastName("Scope");
    billing.setAddress("1 scope street");
    billing.setCity("FT LD");
    billing.setPostalCode("12345");
    billing.setCountry("US");
    customer.setBilling(billing);
    return customer;
  }

  private ReadableCustomer getCustomer(HttpHeaders headers, Long id, String storeCode) {
    ResponseEntity<ReadableCustomer> response = testRestTemplate.exchange(
        customerUrl(id, storeCode), HttpMethod.GET, new HttpEntity<>(headers),
        ReadableCustomer.class);
    assertEquals(HttpStatus.OK, response.getStatusCode());
    return response.getBody();
  }

  private String customerUrl(Long id, String storeCode) {
    return "/api/v1/private/customer/" + id + "?store=" + storeCode;
  }

  private HttpHeaders bearerHeaders(String token) {
    HttpHeaders headers = new HttpHeaders();
    headers.setBearerAuth(token);
    return headers;
  }

  private Set<String> fieldNames(JsonNode json) {
    Set<String> names = new HashSet<>();
    json.fieldNames().forEachRemaining(names::add);
    return names;
  }
}
