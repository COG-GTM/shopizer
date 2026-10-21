package com.salesmanager.test.shop.uat;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;

import java.util.Arrays;

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
import com.salesmanager.shop.model.customer.address.Address;
import com.salesmanager.shop.model.references.PersistableAddress;
import com.salesmanager.shop.model.store.PersistableMerchantStore;
import com.salesmanager.shop.store.security.AuthenticationRequest;
import com.salesmanager.shop.store.security.AuthenticationResponse;
import com.salesmanager.test.shop.common.ServicesTestSupport;

@RunWith(SpringRunner.class)
@SpringBootTest(classes = ShopApplication.class, webEnvironment = WebEnvironment.RANDOM_PORT)
public class CustomerStoreScopeUatTest extends ServicesTestSupport {

  @Test
  public void adminCannotReadOrMutateCustomerThroughAnotherStore() {
    HttpHeaders adminHeaders = getHeader();
    String otherStore = "uat-scope-other";
    PersistableMerchantStore store = new PersistableMerchantStore();
    store.setCode(otherStore);
    store.setName("UAT Scope Store");
    store.setEmail("uat-scope-store@test.com");
    store.setCurrency("USD");
    store.setDefaultLanguage("en");
    store.setPhone("444-555-6666");
    store.setSupportedLanguages(Arrays.asList("en"));
    PersistableAddress storeAddress = new PersistableAddress();
    storeAddress.setAddress("100 UAT Scope Road");
    storeAddress.setCity("Miami");
    storeAddress.setPostalCode("33101");
    storeAddress.setCountry("US");
    storeAddress.setStateProvince("FL");
    store.setAddress(storeAddress);
    ResponseEntity<?> storeResponse = testRestTemplate.postForEntity(
        "/api/v1/private/store/", new HttpEntity<>(store, adminHeaders), Object.class);
    assertEquals(HttpStatus.OK, storeResponse.getStatusCode());

    String email = "uat.scope.customer@test.com";
    PersistableCustomer customer = new PersistableCustomer();
    customer.setEmailAddress(email);
    customer.setPassword("clear123");
    customer.setGender(CustomerGender.M.name());
    customer.setLanguage("en");
    customer.setStoreCode(Constants.DEFAULT_STORE);
    Address billing = new Address();
    billing.setFirstName("Scope");
    billing.setLastName("Customer");
    billing.setCountry("US");
    customer.setBilling(billing);
    ResponseEntity<PersistableCustomer> registration = testRestTemplate.postForEntity(
        "/api/v1/customer/register", new HttpEntity<>(customer, adminHeaders),
        PersistableCustomer.class);
    assertEquals(HttpStatus.OK, registration.getStatusCode());
    Long customerId = registration.getBody().getId();
    assertNotNull(customerId);

    String customerUrl = "/api/v1/private/customer/" + customerId;
    String otherStoreCustomerUrl = customerUrl + "?store=" + otherStore;
    assertEquals(HttpStatus.NOT_FOUND, testRestTemplate.exchange(
        otherStoreCustomerUrl, HttpMethod.GET, new HttpEntity<>(adminHeaders),
        String.class).getStatusCode());

    PersistableCustomer changedCustomer = new PersistableCustomer();
    changedCustomer.setId(customerId);
    changedCustomer.setEmailAddress(email);
    changedCustomer.setPassword("changed123");
    changedCustomer.setGender(CustomerGender.M.name());
    changedCustomer.setLanguage("en");
    changedCustomer.setStoreCode(Constants.DEFAULT_STORE);
    Address changedAddress = new Address();
    changedAddress.setFirstName("Changed");
    changedAddress.setLastName("Customer");
    changedAddress.setAddress("Changed address");
    changedAddress.setCountry("US");
    changedCustomer.setBilling(changedAddress);
    assertEquals(HttpStatus.NOT_FOUND, testRestTemplate.exchange(
        otherStoreCustomerUrl, HttpMethod.PUT,
        new HttpEntity<>(changedCustomer, adminHeaders), String.class).getStatusCode());
    assertEquals(HttpStatus.NOT_FOUND, testRestTemplate.exchange(
        customerUrl + "/address?store=" + otherStore, HttpMethod.PATCH,
        new HttpEntity<>(changedCustomer, adminHeaders), String.class).getStatusCode());
    assertEquals(HttpStatus.NOT_FOUND, testRestTemplate.exchange(
        otherStoreCustomerUrl, HttpMethod.DELETE,
        new HttpEntity<>(adminHeaders), String.class).getStatusCode());

    assertEquals(HttpStatus.OK, testRestTemplate.exchange(
        customerUrl + "?store=" + Constants.DEFAULT_STORE, HttpMethod.GET,
        new HttpEntity<>(adminHeaders), String.class).getStatusCode());

    ResponseEntity<AuthenticationResponse> login = testRestTemplate.postForEntity(
        "/api/v1/customer/login",
        new HttpEntity<>(new AuthenticationRequest(email, "clear123")),
        AuthenticationResponse.class);
    assertEquals(HttpStatus.OK, login.getStatusCode());
    assertNotNull(login.getBody().getToken());
  }
}
