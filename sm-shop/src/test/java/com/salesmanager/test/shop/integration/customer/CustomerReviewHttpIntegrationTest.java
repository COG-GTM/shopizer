package com.salesmanager.test.shop.integration.customer;

import static org.junit.jupiter.api.Assertions.*;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import com.salesmanager.shop.model.customer.PersistableCustomerReview;
import com.salesmanager.shop.model.customer.ReadableCustomerReview;
import com.salesmanager.test.shop.common.CustomerJourneySupport;

@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class CustomerReviewHttpIntegrationTest extends CustomerJourneySupport {
  private HttpHeaders admin;
  private String other;
  private Long reviewer;
  private Long otherReviewer;
  private Long reviewed;

  @BeforeAll void storesAndReviewers() {
    admin = getHeader();
    other = createStore(admin);
    reviewer = register("DEFAULT", unique("reviewer") + "@example.com").getId();
    otherReviewer = register(other, unique("reviewer") + "@example.com").getId();
  }

  @BeforeEach void newReviewedCustomer() {
    reviewed = register("DEFAULT", unique("reviewed") + "@example.com").getId();
  }

  @Test void sameStoreCreateAndAnonymousList() {
    PersistableCustomerReview created = createReview(reviewed, reviewer, admin);
    ResponseEntity<ReadableCustomerReview[]> response = testRestTemplate.getForEntity(
        reviewUrl(reviewed, false, "DEFAULT"), ReadableCustomerReview[].class);
    assertEquals(HttpStatus.OK, response.getStatusCode());
    assertEquals(1, response.getBody().length);
    assertEquals(created.getId(), response.getBody()[0].getId());
  }

  @Test void otherStoreCannotListExistingCustomer() {
    createReview(reviewed, reviewer, admin);
    assertEquals(HttpStatus.NOT_FOUND, testRestTemplate.getForEntity(
        reviewUrl(reviewed, false, other), String.class).getStatusCode());
  }

  @Test void otherStoreCannotCreateForDefaultCustomer() {
    ResponseEntity<String> response = request(reviewUrl(reviewed, true, other), HttpMethod.POST, review(reviewer), admin);
    assertEquals(HttpStatus.NOT_FOUND, response.getStatusCode(), response.getBody());
  }

  @Test void reviewerFromOtherStoreIsNotFound() {
    ResponseEntity<String> response = request(reviewUrl(reviewed, true, "DEFAULT"), HttpMethod.POST, review(otherReviewer), admin);
    assertEquals(HttpStatus.NOT_FOUND, response.getStatusCode(), response.getBody());
  }

  @Test void putReviewIdRouteIsReachable() {
    PersistableCustomerReview created = createReview(reviewed, reviewer, admin);
    ResponseEntity<String> response = request("/api/v1/private/customers/" + reviewed + "/reviews/"
        + created.getId() + "?store=DEFAULT", HttpMethod.PUT, review(reviewer), admin);
    assertEquals(HttpStatus.OK, response.getStatusCode(), response.getBody());
  }

  @Test void deleteIsScopedAndLeavesEmptyList() {
    PersistableCustomerReview created = createReview(reviewed, reviewer, admin);
    String url = "/api/v1/private/customers/" + reviewed + "/reviews/" + created.getId() + "?store=";
    assertEquals(HttpStatus.NOT_FOUND, request(url + other, HttpMethod.DELETE, null, admin).getStatusCode());
    assertEquals(1, testRestTemplate.getForObject(reviewUrl(reviewed, false, "DEFAULT"), ReadableCustomerReview[].class).length);
    assertEquals(HttpStatus.OK, request(url + "DEFAULT", HttpMethod.DELETE, null, admin).getStatusCode());
    assertEquals(0, testRestTemplate.getForObject(reviewUrl(reviewed, false, "DEFAULT"), ReadableCustomerReview[].class).length);
  }
}
