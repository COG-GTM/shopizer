package com.salesmanager.test.shop.common;

import static org.junit.jupiter.api.Assertions.*;

import java.util.Arrays;
import java.util.UUID;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import com.salesmanager.shop.model.customer.PersistableCustomer;
import com.salesmanager.shop.model.customer.PersistableCustomerReview;
import com.salesmanager.shop.model.customer.address.Address;
import com.salesmanager.shop.model.references.PersistableAddress;
import com.salesmanager.shop.model.store.PersistableMerchantStore;
import com.salesmanager.shop.store.security.AuthenticationResponse;

public abstract class CustomerJourneySupport extends ServicesTestSupport {
  protected String unique(String prefix) {
    return prefix + UUID.randomUUID().toString().replace("-", "").substring(0, 10);
  }

  protected String createStore(HttpHeaders admin) {
    String code = unique("STORE");
    PersistableAddress address = new PersistableAddress();
    address.setAddress("123 Test Street");
    address.setPostalCode("33301");
    address.setCountry("US");
    address.setCity("Fort Lauderdale");
    address.setStateProvince("FL");
    PersistableMerchantStore store = new PersistableMerchantStore();
    store.setCode(code);
    store.setName(code);
    store.setCurrency("USD");
    store.setDefaultLanguage("en");
    store.setSupportedLanguages(Arrays.asList("en"));
    store.setEmail("store@example.com");
    store.setPhone("444-555-6666");
    store.setAddress(address);
    ResponseEntity<String> response = testRestTemplate.postForEntity("/api/v1/private/store",
        new HttpEntity<>(store, admin), String.class);
    assertEquals(HttpStatus.OK, response.getStatusCode(), response.getBody());
    return code;
  }

  protected AuthenticationResponse register(String store, String email) {
    PersistableCustomer customer = new PersistableCustomer();
    customer.setEmailAddress(email);
    customer.setPassword("clear123");
    customer.setRepeatPassword("clear123");
    customer.setGender("M");
    customer.setLanguage("en");
    customer.setStoreCode(store);
    Address address = new Address();
    address.setFirstName("Test");
    address.setLastName("Customer");
    address.setCountry("US");
    address.setStateProvince("FL");
    address.setAddress("123 Test Street");
    address.setCity("Fort Lauderdale");
    address.setPostalCode("33301");
    customer.setBilling(address);
    ResponseEntity<AuthenticationResponse> response = testRestTemplate.postForEntity(
        "/api/v1/customer/register?store=" + store, customer, AuthenticationResponse.class);
    assertEquals(HttpStatus.OK, response.getStatusCode());
    assertNotNull(response.getBody());
    assertNotNull(response.getBody().getId());
    assertNotNull(response.getBody().getToken());
    return response.getBody();
  }

  protected HttpHeaders bearer(String token) {
    HttpHeaders headers = new HttpHeaders();
    headers.setContentType(MediaType.APPLICATION_JSON);
    headers.setBearerAuth(token);
    return headers;
  }

  protected PersistableCustomerReview review(Long reviewer) {
    PersistableCustomerReview review = new PersistableCustomerReview();
    review.setCustomerId(reviewer);
    review.setDescription("Reliable customer");
    review.setRating(4D);
    return review;
  }

  protected String reviewUrl(Long customer, boolean admin, String store) {
    return "/api/v1/" + (admin ? "private/" : "") + "customers/" + customer + "/reviews?store=" + store;
  }

  protected PersistableCustomerReview createReview(Long customer, Long reviewer, HttpHeaders admin) {
    ResponseEntity<PersistableCustomerReview> response = testRestTemplate.postForEntity(
        reviewUrl(customer, true, "DEFAULT"), new HttpEntity<>(review(reviewer), admin),
        PersistableCustomerReview.class);
    assertEquals(HttpStatus.CREATED, response.getStatusCode());
    assertNotNull(response.getBody());
    assertNotNull(response.getBody().getId());
    return response.getBody();
  }

  protected ResponseEntity<String> request(String url, HttpMethod method, Object body, HttpHeaders headers) {
    return testRestTemplate.exchange(url, method, new HttpEntity<>(body, headers), String.class);
  }
}
