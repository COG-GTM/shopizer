package com.salesmanager.test.shop.uat;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Arrays;
import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import com.salesmanager.core.model.customer.CustomerGender;
import com.salesmanager.shop.model.customer.PersistableCustomer;
import com.salesmanager.shop.model.customer.ReadableCustomer;
import com.salesmanager.shop.model.customer.address.Address;
import com.salesmanager.shop.model.references.PersistableAddress;
import com.salesmanager.shop.model.store.PersistableMerchantStore;
import com.salesmanager.shop.populator.customer.ReadableCustomerList;
import com.salesmanager.test.shop.common.ServicesTestSupport;

public class CustomerStoreIsolationUatTest extends ServicesTestSupport {

  @Test
  public void defaultStoreAdminCannotListOrOpenSecondStoreCustomer() {
    ensureSecondStore();
    String email = "isolated-" + UUID.randomUUID().toString().replace("-", "") + "@example.test";
    PersistableCustomer customer = new PersistableCustomer();
    customer.setEmailAddress(email);
    customer.setPassword("clear123");
    customer.setGender(CustomerGender.M.name());
    customer.setLanguage("en");
    customer.setStoreCode("SCOPE2");
    customer.setFirstName("Isolated");
    customer.setLastName("Customer");
    Address billing = new Address();
    billing.setFirstName("Isolated");
    billing.setLastName("Customer");
    billing.setAddress("2 Scope Street");
    billing.setCity("New York");
    billing.setPostalCode("12345");
    billing.setCountry("US");
    customer.setBilling(billing);

    ResponseEntity<ReadableCustomer> created = testRestTemplate.postForEntity(
        "/api/v1/private/customer?store=SCOPE2", new HttpEntity<>(customer, getHeader()), ReadableCustomer.class);
    assertTrue(created.getStatusCode().is2xxSuccessful(), created.toString());
    assertNotNull(created.getBody());

    ResponseEntity<ReadableCustomerList> defaultCustomers = testRestTemplate.exchange(
        "/api/v1/private/customers?store=DEFAULT", HttpMethod.GET,
        new HttpEntity<>(getHeader()), ReadableCustomerList.class);
    ResponseEntity<String> crossStoreOpen = testRestTemplate.exchange(
        "/api/v1/private/customer/" + created.getBody().getId() + "?store=DEFAULT", HttpMethod.GET,
        new HttpEntity<>(getHeader()), String.class);

    assertEquals(HttpStatus.OK, defaultCustomers.getStatusCode());
    assertFalse(defaultCustomers.getBody().getCustomers().stream()
        .anyMatch(listed -> email.equals(listed.getEmailAddress())));
    assertEquals(HttpStatus.NOT_FOUND, crossStoreOpen.getStatusCode());
  }

  private void ensureSecondStore() {
    ResponseEntity<String> existing = testRestTemplate.exchange(
        "/api/v1/private/store/SCOPE2", HttpMethod.GET, new HttpEntity<>(getHeader()), String.class);
    if (existing.getStatusCode().is2xxSuccessful()) {
      return;
    }

    PersistableAddress address = new PersistableAddress();
    address.setAddress("42 Store Scope Way");
    address.setPostalCode("12345");
    address.setCountry("US");
    address.setCity("New York");
    address.setStateProvince("NY");
    PersistableMerchantStore store = new PersistableMerchantStore();
    store.setCode("SCOPE2");
    store.setCurrency("USD");
    store.setDefaultLanguage("en");
    store.setEmail("scope2@example.test");
    store.setName("SCOPE2");
    store.setPhone("555-0100");
    store.setSupportedLanguages(Arrays.asList("en"));
    store.setAddress(address);
    ResponseEntity<String> response = testRestTemplate.exchange(
        "/api/v1/private/store", HttpMethod.POST, new HttpEntity<>(store, getHeader()), String.class);
    assertEquals(HttpStatus.OK, response.getStatusCode());
  }
}
