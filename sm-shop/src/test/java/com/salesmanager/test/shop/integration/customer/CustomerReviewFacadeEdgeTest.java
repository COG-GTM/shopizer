package com.salesmanager.test.shop.integration.customer;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import java.util.Collections;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import com.salesmanager.core.business.services.customer.CustomerService;
import com.salesmanager.core.business.services.customer.review.CustomerReviewService;
import com.salesmanager.core.model.common.Billing;
import com.salesmanager.core.model.customer.Customer;
import com.salesmanager.core.model.customer.review.CustomerReview;
import com.salesmanager.core.model.merchant.MerchantStore;
import com.salesmanager.core.model.reference.language.Language;
import com.salesmanager.shop.store.api.exception.ResourceNotFoundException;
import com.salesmanager.shop.store.controller.customer.facade.CustomerFacadeImpl;

@ExtendWith(MockitoExtension.class)
class CustomerReviewFacadeEdgeTest {
  @Mock private CustomerService customers;
  @Mock private CustomerReviewService reviews;
  @InjectMocks private CustomerFacadeImpl facade;
  private final MerchantStore store = new MerchantStore(1, "DEFAULT", "Default");
  private final Language en = new Language("en");

  @Test void legacyCrossStoreReviewerRemainsVisible() {
    Customer reviewed = customer(2L, store);
    CustomerReview review = legacyReview(reviewed);
    when(customers.getById(2L)).thenReturn(reviewed);
    when(reviews.getByReviewedCustomer(reviewed)).thenReturn(Collections.singletonList(review));
    assertEquals(7L, facade.getAllCustomerReviewsByReviewed(2L, store, en).get(0).getId());
  }

  @Test void legacyCrossStoreReviewerDoesNotPreventDeletion() throws Exception {
    Customer reviewed = customer(2L, store);
    CustomerReview review = legacyReview(reviewed);
    when(customers.getById(2L)).thenReturn(reviewed);
    when(reviews.getById(7L)).thenReturn(review);
    facade.deleteCustomerReview(2L, 7L, store, en);
    verify(reviews).delete(review);
  }

  @Test void nullCustomerIdIsNotFound() {
    assertThrows(ResourceNotFoundException.class, () -> facade.getAllCustomerReviewsByReviewed(null, store, en));
    verifyNoInteractions(customers, reviews);
  }

  @Test void nullReviewIdIsNotFound() {
    when(customers.getById(2L)).thenReturn(customer(2L, store));
    assertThrows(ResourceNotFoundException.class, () -> facade.deleteCustomerReview(2L, null, store, en));
    verifyNoInteractions(reviews);
  }

  @Test void emptyReviewListStaysEmpty() {
    Customer reviewed = customer(2L, store);
    when(customers.getById(2L)).thenReturn(reviewed);
    when(reviews.getByReviewedCustomer(reviewed)).thenReturn(Collections.emptyList());
    assertTrue(facade.getAllCustomerReviewsByReviewed(2L, store, en).isEmpty());
  }

  private CustomerReview legacyReview(Customer reviewed) {
    CustomerReview review = new CustomerReview();
    review.setId(7L);
    review.setCustomer(customer(1L, new MerchantStore(2, "OTHER", "Other")));
    review.setReviewedCustomer(reviewed);
    return review;
  }

  private Customer customer(Long id, MerchantStore merchantStore) {
    Customer customer = new Customer();
    customer.setId(id);
    customer.setMerchantStore(merchantStore);
    Billing billing = new Billing();
    billing.setFirstName("First");
    billing.setLastName("Last");
    customer.setBilling(billing);
    return customer;
  }
}
