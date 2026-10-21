package com.salesmanager.test.shop.integration.customer;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.salesmanager.core.model.merchant.MerchantStore;
import com.salesmanager.shop.model.customer.PersistableCustomer;
import com.salesmanager.shop.model.customer.ReadableCustomer;
import com.salesmanager.shop.model.customer.address.Address;
import com.salesmanager.shop.model.references.PersistableAddress;
import com.salesmanager.shop.model.store.PersistableMerchantStore;
import com.salesmanager.shop.store.security.AuthenticationRequest;
import com.salesmanager.shop.store.security.AuthenticationResponse;
import com.salesmanager.shop.application.ShopApplication;
import com.salesmanager.test.shop.common.ServicesTestSupport;
import java.util.Collections;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.SpringBootTest.WebEnvironment;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.test.context.junit.jupiter.SpringExtension;

@ExtendWith(SpringExtension.class)
@SpringBootTest(classes = ShopApplication.class, webEnvironment = WebEnvironment.RANDOM_PORT)
class CustomerStoreScopeIntegrationTest extends ServicesTestSupport {

  private String suffix;
  private String storeCode;
  private HttpHeaders adminHeaders;
  private ReadableCustomer storeCustomer;
  private ReadableCustomer defaultCustomer;

  @BeforeEach
  void createStoreAndCustomers() {
    suffix = UUID.randomUUID().toString().replace("-", "").substring(0, 10);
    storeCode = "idorb" + suffix;
    adminHeaders = getHeader();
    createStore();
    storeCustomer = createCustomer(storeCode, "store" + suffix + "@test.com",
        "Bob", "StoreB", "1 B st");
    defaultCustomer = createCustomer(MerchantStore.DEFAULT_STORE,
        "default" + suffix + "@test.com", "Dee", "Fault", "1 Default st");
  }

  @Test
  void customerReadIsScopedToTheRequestedStore() {
    ResponseEntity<ReadableCustomer> crossStore = getCustomer(storeCustomer.getId(), "DEFAULT");
    ResponseEntity<ReadableCustomer> sameStore = getCustomer(storeCustomer.getId(), storeCode);

    assertEquals(HttpStatus.NOT_FOUND, crossStore.getStatusCode());
    assertEquals(HttpStatus.OK, sameStore.getStatusCode());
    assertEquals(storeCustomer.getId(), sameStore.getBody().getId());
    assertEquals(storeCustomer.getEmailAddress(), sameStore.getBody().getEmailAddress());
  }

  @Test
  void missingCustomerHasTheSameNotFoundMessageForm() {
    ResponseEntity<String> response = testRestTemplate.exchange(
        "/api/v1/private/customer/987654321?store=DEFAULT", HttpMethod.GET,
        new HttpEntity<>(adminHeaders), String.class);

    assertEquals(HttpStatus.NOT_FOUND, response.getStatusCode());
    assertTrue(response.getBody().contains("No Customer found for ID : 987654321"));
  }

  @Test
  void crossStoreUpdateDoesNotChangeCustomer() {
    ResponseEntity<ReadableCustomer> response = testRestTemplate.exchange(
        customerPath(storeCustomer.getId(), "DEFAULT"), HttpMethod.PUT,
        new HttpEntity<>(customerRequest("DEFAULT", storeCustomer.getEmailAddress(),
            "HACKED", "StoreB", "1 B st"), adminHeaders), ReadableCustomer.class);
    ResponseEntity<ReadableCustomer> after = getCustomer(storeCustomer.getId(), storeCode);

    assertEquals(HttpStatus.NOT_FOUND, response.getStatusCode());
    assertEquals("Bob", after.getBody().getBilling().getFirstName());
  }

  @Test
  void crossStoreAddressUpdateDoesNotChangeCustomer() {
    ResponseEntity<String> response = testRestTemplate.exchange(
        customerAddressPath(storeCustomer.getId(), "DEFAULT"), HttpMethod.PATCH,
        new HttpEntity<>(customerRequest("DEFAULT", storeCustomer.getEmailAddress(),
            "HACKED", "StoreB", "9 Evil st"), adminHeaders), String.class);
    ResponseEntity<ReadableCustomer> after = getCustomer(storeCustomer.getId(), storeCode);

    assertEquals(HttpStatus.NOT_FOUND, response.getStatusCode());
    assertEquals("1 B st", after.getBody().getBilling().getAddress());
  }

  @Test
  void crossStoreDeleteDoesNotDeleteCustomer() {
    ResponseEntity<String> deleted = testRestTemplate.exchange(
        customerPath(storeCustomer.getId(), "DEFAULT"), HttpMethod.DELETE,
        new HttpEntity<>(adminHeaders), String.class);
    ResponseEntity<ReadableCustomer> after = getCustomer(storeCustomer.getId(), storeCode);

    assertEquals(HttpStatus.NOT_FOUND, deleted.getStatusCode());
    assertEquals(HttpStatus.OK, after.getStatusCode());
  }

  @Test
  void sameStoreUpdateAddressAndDeleteStillWork() {
    ResponseEntity<ReadableCustomer> updated = testRestTemplate.exchange(
        customerPath(storeCustomer.getId(), storeCode), HttpMethod.PUT,
        new HttpEntity<>(customerRequest(storeCode, storeCustomer.getEmailAddress(),
            "Updated", "StoreB", "1 B st"), adminHeaders), ReadableCustomer.class);
    ResponseEntity<ReadableCustomer> afterUpdate = getCustomer(storeCustomer.getId(), storeCode);

    ResponseEntity<String> addressUpdated = testRestTemplate.exchange(
        customerAddressPath(storeCustomer.getId(), storeCode), HttpMethod.PATCH,
        new HttpEntity<>(customerRequest(storeCode, storeCustomer.getEmailAddress(),
            "Updated", "StoreB", "2 B st"), adminHeaders), String.class);
    ResponseEntity<ReadableCustomer> afterAddressUpdate =
        getCustomer(storeCustomer.getId(), storeCode);

    ResponseEntity<String> deleted = testRestTemplate.exchange(
        customerPath(storeCustomer.getId(), storeCode), HttpMethod.DELETE,
        new HttpEntity<>(adminHeaders), String.class);
    ResponseEntity<ReadableCustomer> afterDelete = getCustomer(storeCustomer.getId(), storeCode);

    assertEquals(HttpStatus.OK, updated.getStatusCode());
    assertEquals("Updated", afterUpdate.getBody().getBilling().getFirstName());
    assertEquals(HttpStatus.OK, addressUpdated.getStatusCode());
    assertEquals("2 B st", afterAddressUpdate.getBody().getBilling().getAddress());
    assertEquals(HttpStatus.OK, deleted.getStatusCode());
    assertEquals(HttpStatus.NOT_FOUND, afterDelete.getStatusCode());
  }

  @Test
  void defaultCustomerCanBeReadWithAndWithoutStoreParameter() {
    ResponseEntity<ReadableCustomer> withStore =
        getCustomer(defaultCustomer.getId(), MerchantStore.DEFAULT_STORE);
    ResponseEntity<ReadableCustomer> withoutStore = testRestTemplate.exchange(
        "/api/v1/private/customer/" + defaultCustomer.getId(), HttpMethod.GET,
        new HttpEntity<>(adminHeaders), ReadableCustomer.class);

    assertEquals(HttpStatus.OK, withStore.getStatusCode());
    assertEquals(HttpStatus.OK, withoutStore.getStatusCode());
    assertEquals(defaultCustomer.getEmailAddress(), withoutStore.getBody().getEmailAddress());
  }

  @Test
  void loggedInCustomerCanUpdateAddress() {
    String email = "auth" + suffix + "@test.com";
    PersistableCustomer registration = customerRequest("DEFAULT", email,
        "Auth", "Customer", "1 Auth st");
    ResponseEntity<PersistableCustomer> registered = testRestTemplate.postForEntity(
        "/api/v1/customer/register", new HttpEntity<>(registration), PersistableCustomer.class);
    ResponseEntity<AuthenticationResponse> login = testRestTemplate.postForEntity(
        "/api/v1/customer/login",
        new HttpEntity<>(new AuthenticationRequest(email, "clear123")),
        AuthenticationResponse.class);
    assertNotNull(login.getBody());
    HttpHeaders customerHeaders = new HttpHeaders();
    customerHeaders.setContentType(org.springframework.http.MediaType.APPLICATION_JSON);
    customerHeaders.setBearerAuth(login.getBody().getToken());
    ResponseEntity<String> updated = testRestTemplate.exchange(
        "/api/v1/auth/customer/address?store=DEFAULT", HttpMethod.PATCH,
        new HttpEntity<>(customerRequest("DEFAULT", email, "Auth", "Customer",
            "2 Auth st"), customerHeaders), String.class);
    ResponseEntity<ReadableCustomer> profile = testRestTemplate.exchange(
        "/api/v1/auth/customer/profile", HttpMethod.GET,
        new HttpEntity<>(customerHeaders), ReadableCustomer.class);

    assertEquals(HttpStatus.OK, registered.getStatusCode());
    assertEquals(HttpStatus.OK, login.getStatusCode());
    assertEquals(HttpStatus.OK, updated.getStatusCode());
    assertEquals(HttpStatus.OK, profile.getStatusCode());
    assertEquals("2 Auth st", profile.getBody().getBilling().getAddress());
  }

  private void createStore() {
    PersistableAddress address = new PersistableAddress();
    address.setAddress("1 Store st");
    address.setCity("Montreal");
    address.setPostalCode("H2H2H2");
    address.setCountry("US");
    address.setStateProvince("FL");

    PersistableMerchantStore store = new PersistableMerchantStore();
    store.setCode(storeCode);
    store.setName(storeCode);
    store.setCurrency("CAD");
    store.setDefaultLanguage("en");
    store.setEmail("store" + suffix + "@test.com");
    store.setPhone("444-555-6666");
    store.setSupportedLanguages(Collections.singletonList("en"));
    store.setAddress(address);
    ResponseEntity<Void> response = testRestTemplate.exchange("/api/v1/private/store/",
        HttpMethod.POST, new HttpEntity<>(store, adminHeaders), Void.class);
    assertEquals(HttpStatus.OK, response.getStatusCode());
  }

  private ReadableCustomer createCustomer(
      String code, String email, String firstName, String lastName, String address) {
    ResponseEntity<ReadableCustomer> response = testRestTemplate.exchange(
        "/api/v1/private/customer?store=" + code, HttpMethod.POST,
        new HttpEntity<>(customerRequest(code, email, firstName, lastName, address),
            adminHeaders), ReadableCustomer.class);
    assertEquals(HttpStatus.OK, response.getStatusCode());
    assertNotNull(response.getBody());
    return response.getBody();
  }

  private PersistableCustomer customerRequest(
      String code, String email, String firstName, String lastName, String address) {
    Address billing = new Address();
    billing.setFirstName(firstName);
    billing.setLastName(lastName);
    billing.setAddress(address);
    billing.setCity("Montreal");
    billing.setPostalCode("H2H2H2");
    billing.setCountry("CA");
    billing.setZone("QC");
    billing.setBillingAddress(true);

    PersistableCustomer customer = new PersistableCustomer();
    customer.setEmailAddress(email);
    customer.setPassword("clear123");
    customer.setGender("M");
    customer.setLanguage("en");
    customer.setStoreCode(code);
    customer.setBilling(billing);
    return customer;
  }

  private ResponseEntity<ReadableCustomer> getCustomer(Long id, String code) {
    return testRestTemplate.exchange(customerPath(id, code), HttpMethod.GET,
        new HttpEntity<>(adminHeaders), ReadableCustomer.class);
  }

  private String customerPath(Long id, String code) {
    return "/api/v1/private/customer/" + id + "?store=" + code;
  }

  private String customerAddressPath(Long id, String code) {
    return "/api/v1/private/customer/" + id + "/address?store=" + code;
  }
}
