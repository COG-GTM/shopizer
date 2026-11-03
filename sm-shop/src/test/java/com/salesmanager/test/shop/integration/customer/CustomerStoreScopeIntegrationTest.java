package com.salesmanager.test.shop.integration.customer;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Arrays;
import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import com.salesmanager.core.model.customer.CustomerGender;
import com.salesmanager.shop.model.customer.PersistableCustomer;
import com.salesmanager.shop.model.customer.PersistableCustomerReview;
import com.salesmanager.shop.model.customer.ReadableCustomer;
import com.salesmanager.shop.model.customer.address.Address;
import com.salesmanager.shop.model.references.PersistableAddress;
import com.salesmanager.shop.model.store.PersistableMerchantStore;
import com.salesmanager.test.shop.common.ServicesTestSupport;

public class CustomerStoreScopeIntegrationTest extends ServicesTestSupport {

  private static final String SECOND_STORE = "SCOPE2";

  @Test
  public void customerOperationsAreScopedToTheRequestedStore() {
    ensureSecondStore();
    String suffix = UUID.randomUUID().toString().replace("-", "");
    ReadableCustomer customerA = createCustomer("scope-a-" + suffix + "@example.test", "Default", "DEFAULT");
    ReadableCustomer customerB = createCustomer("scope-b-" + suffix + "@example.test", "Second", SECOND_STORE);
    ReadableCustomer reviewer = createCustomer("scope-reviewer-" + suffix + "@example.test", "Reviewer", "DEFAULT");
    assertNotNull(customerA);
    assertNotNull(customerB);
    assertNotNull(reviewer);

    Long aId = customerA.getId();
    Long bId = customerB.getId();
    Long reviewerId = reviewer.getId();
    String defaultQuery = "?store=DEFAULT";
    String bPath = "/api/v1/private/customer/" + bId;

    ResponseEntity<String> crossStoreGet = exchange(bPath + defaultQuery, HttpMethod.GET, null, String.class);
    ResponseEntity<String> crossStorePut = exchange(bPath + defaultQuery, HttpMethod.PUT,
        persistableCustomer("changed-" + suffix + "@example.test", "Changed"), String.class);
    ResponseEntity<String> crossStorePatch = exchange(bPath + "/address" + defaultQuery, HttpMethod.PATCH,
        persistableCustomer("changed-" + suffix + "@example.test", "Changed"), String.class);
    ResponseEntity<String> crossStoreDelete = exchange(bPath + defaultQuery, HttpMethod.DELETE, null, String.class);
    ResponseEntity<String> crossStoreReviewGet = exchange(
        "/api/v1/customers/" + bId + "/reviews" + defaultQuery, HttpMethod.GET, null, String.class);

    PersistableCustomerReview reviewOfB = review(aId);
    ResponseEntity<String> crossStoreReviewPost = exchange(
        "/api/v1/private/customers/" + bId + "/reviews" + defaultQuery, HttpMethod.POST, reviewOfB, String.class);
    PersistableCustomerReview reviewByB = review(bId);
    ResponseEntity<String> crossStoreReviewerPost = exchange(
        "/api/v1/private/customers/" + aId + "/reviews" + defaultQuery, HttpMethod.POST, reviewByB, String.class);

    ResponseEntity<ReadableCustomer> bInOwnStore = exchange(
        "/api/v1/private/customer/" + bId + "?store=" + SECOND_STORE, HttpMethod.GET, null, ReadableCustomer.class);

    assertAll(
        () -> assertEquals(HttpStatus.NOT_FOUND, crossStoreGet.getStatusCode()),
        () -> assertEquals(HttpStatus.NOT_FOUND, crossStorePut.getStatusCode()),
        () -> assertEquals(HttpStatus.NOT_FOUND, crossStorePatch.getStatusCode()),
        () -> assertEquals(HttpStatus.NOT_FOUND, crossStoreDelete.getStatusCode()),
        () -> assertEquals(HttpStatus.NOT_FOUND, crossStoreReviewGet.getStatusCode()),
        () -> assertEquals(HttpStatus.NOT_FOUND, crossStoreReviewPost.getStatusCode()),
        () -> assertEquals(HttpStatus.NOT_FOUND, crossStoreReviewerPost.getStatusCode()),
        () -> assertEquals(HttpStatus.OK, bInOwnStore.getStatusCode()),
        () -> assertEquals("scope-b-" + suffix + "@example.test", bInOwnStore.getBody().getEmailAddress()),
        () -> assertEquals("Second", bInOwnStore.getBody().getFirstName()));

    ResponseEntity<ReadableCustomer> sameStoreGet = exchange(
        "/api/v1/private/customer/" + aId + defaultQuery, HttpMethod.GET, null, ReadableCustomer.class);
    assertEquals(HttpStatus.OK, sameStoreGet.getStatusCode());

    ResponseEntity<PersistableCustomerReview> sameStoreReviewPost = exchange(
        "/api/v1/private/customers/" + aId + "/reviews" + defaultQuery, HttpMethod.POST,
        review(reviewerId), PersistableCustomerReview.class);
    assertEquals(HttpStatus.CREATED, sameStoreReviewPost.getStatusCode());

    // The HTTP update route has a pre-existing reviewid/reviewId path-variable mismatch; update is not exercised here.
    ResponseEntity<List> sameStoreReviewGet = exchange(
        "/api/v1/customers/" + aId + "/reviews" + defaultQuery, HttpMethod.GET, null, List.class);
    assertEquals(HttpStatus.OK, sameStoreReviewGet.getStatusCode());
    assertEquals(1, sameStoreReviewGet.getBody().size());

    ReadableCustomer customerToDelete = createCustomer("scope-delete-" + suffix + "@example.test", "Delete", "DEFAULT");
    ResponseEntity<String> delete = exchange("/api/v1/private/customer/" + customerToDelete.getId() + defaultQuery,
        HttpMethod.DELETE, null, String.class);
    ResponseEntity<String> getDeleted = exchange(
        "/api/v1/private/customer/" + customerToDelete.getId() + defaultQuery, HttpMethod.GET, null, String.class);
    assertAll(
        () -> assertEquals(HttpStatus.OK, delete.getStatusCode()),
        () -> assertEquals(HttpStatus.NOT_FOUND, getDeleted.getStatusCode()));

  }

  private void ensureSecondStore() {
    ResponseEntity<String> existing = exchange(
        "/api/v1/private/store/" + SECOND_STORE, HttpMethod.GET, null, String.class);
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
    store.setCode(SECOND_STORE);
    store.setCurrency("USD");
    store.setDefaultLanguage("en");
    store.setEmail("scope2@example.test");
    store.setName(SECOND_STORE);
    store.setPhone("555-0100");
    store.setSupportedLanguages(Arrays.asList("en"));
    store.setAddress(address);

    ResponseEntity<String> created = exchange("/api/v1/private/store", HttpMethod.POST, store, String.class);
    assertEquals(HttpStatus.OK, created.getStatusCode());
  }

  private ReadableCustomer createCustomer(String email, String firstName, String store) {
    ResponseEntity<ReadableCustomer> response = exchange(
        "/api/v1/private/customer?store=" + store, HttpMethod.POST, persistableCustomer(email, firstName),
        ReadableCustomer.class);
    assertTrue(response.getStatusCode().is2xxSuccessful(), response.toString());
    assertNotNull(response.getBody());
    return response.getBody();
  }

  private PersistableCustomer persistableCustomer(String email, String firstName) {
    PersistableCustomer customer = new PersistableCustomer();
    customer.setEmailAddress(email);
    customer.setPassword("clear123");
    customer.setGender(CustomerGender.M.name());
    customer.setLanguage("en");
    customer.setStoreCode("DEFAULT");
    customer.setFirstName(firstName);
    customer.setLastName("Scope");
    Address billing = new Address();
    billing.setFirstName(firstName);
    billing.setLastName("Scope");
    billing.setAddress("1 Test Street");
    billing.setCity("New York");
    billing.setPostalCode("12345");
    billing.setCountry("US");
    customer.setBilling(billing);
    return customer;
  }

  private PersistableCustomerReview review(Long customerId) {
    PersistableCustomerReview review = new PersistableCustomerReview();
    review.setCustomerId(customerId);
    review.setRating(5.0);
    review.setDescription("Store scope review");
    return review;
  }

  private <T> ResponseEntity<T> exchange(String path, HttpMethod method, Object body, Class<T> responseType) {
    return testRestTemplate.exchange(path, method, new HttpEntity<>(body, getHeader()), responseType);
  }
}
