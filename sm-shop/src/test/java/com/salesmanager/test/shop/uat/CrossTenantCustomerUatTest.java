package com.salesmanager.test.shop.uat;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

import com.salesmanager.shop.model.customer.PersistableCustomer;
import com.salesmanager.shop.model.customer.ReadableCustomer;
import com.salesmanager.shop.model.customer.address.Address;
import com.salesmanager.shop.model.references.PersistableAddress;
import com.salesmanager.shop.model.store.PersistableMerchantStore;
import com.salesmanager.shop.application.ShopApplication;
import com.salesmanager.test.shop.common.ServicesTestSupport;
import java.util.Collections;
import java.util.UUID;
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
class CrossTenantCustomerUatTest extends ServicesTestSupport {

  @Test
  void storeBCustomerCannotBeReadUpdatedOrDeletedThroughDefaultStore() {
    String suffix = UUID.randomUUID().toString().replace("-", "").substring(0, 10);
    String storeCode = "idorb" + suffix;
    String email = "cross" + suffix + "@test.com";
    HttpHeaders adminHeaders = getHeader();
    createStore(storeCode, suffix, adminHeaders);
    ReadableCustomer customer = createCustomer(storeCode, email, adminHeaders);

    ResponseEntity<String> crossStoreRead = testRestTemplate.exchange(
        customerPath(customer.getId(), "DEFAULT"), HttpMethod.GET,
        new HttpEntity<>(adminHeaders), String.class);
    ResponseEntity<String> crossStoreUpdate = testRestTemplate.exchange(
        customerPath(customer.getId(), "DEFAULT"), HttpMethod.PUT,
        new HttpEntity<>(customerRequest("DEFAULT", email, "HACKED", "StoreB",
            "9 Evil st"), adminHeaders), String.class);
    ResponseEntity<String> crossStoreAddressUpdate = testRestTemplate.exchange(
        customerAddressPath(customer.getId(), "DEFAULT"), HttpMethod.PATCH,
        new HttpEntity<>(customerRequest("DEFAULT", email, "HACKED", "StoreB",
            "9 Evil st"), adminHeaders), String.class);
    ResponseEntity<String> crossStoreDelete = testRestTemplate.exchange(
        customerPath(customer.getId(), "DEFAULT"), HttpMethod.DELETE,
        new HttpEntity<>(adminHeaders), String.class);
    ResponseEntity<ReadableCustomer> sameStoreRead = testRestTemplate.exchange(
        customerPath(customer.getId(), storeCode), HttpMethod.GET,
        new HttpEntity<>(adminHeaders), ReadableCustomer.class);

    assertEquals(HttpStatus.NOT_FOUND, crossStoreRead.getStatusCode());
    assertEquals(HttpStatus.NOT_FOUND, crossStoreUpdate.getStatusCode());
    assertEquals(HttpStatus.NOT_FOUND, crossStoreAddressUpdate.getStatusCode());
    assertEquals(HttpStatus.NOT_FOUND, crossStoreDelete.getStatusCode());
    assertEquals(HttpStatus.OK, sameStoreRead.getStatusCode());
    assertEquals("Bob", sameStoreRead.getBody().getBilling().getFirstName());
    assertEquals("1 B st", sameStoreRead.getBody().getBilling().getAddress());
  }

  private void createStore(String storeCode, String suffix, HttpHeaders adminHeaders) {
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
      String storeCode, String email, HttpHeaders adminHeaders) {
    ResponseEntity<ReadableCustomer> response = testRestTemplate.exchange(
        "/api/v1/private/customer?store=" + storeCode, HttpMethod.POST,
        new HttpEntity<>(customerRequest(storeCode, email, "Bob", "StoreB", "1 B st"),
            adminHeaders), ReadableCustomer.class);
    assertEquals(HttpStatus.OK, response.getStatusCode());
    assertNotNull(response.getBody());
    return response.getBody();
  }

  private PersistableCustomer customerRequest(
      String storeCode, String email, String firstName, String lastName, String address) {
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
    customer.setStoreCode(storeCode);
    customer.setBilling(billing);
    return customer;
  }

  private String customerPath(Long id, String storeCode) {
    return "/api/v1/private/customer/" + id + "?store=" + storeCode;
  }

  private String customerAddressPath(Long id, String storeCode) {
    return "/api/v1/private/customer/" + id + "/address?store=" + storeCode;
  }
}
