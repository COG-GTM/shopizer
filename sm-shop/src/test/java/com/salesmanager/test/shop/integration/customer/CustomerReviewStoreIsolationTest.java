package com.salesmanager.test.shop.integration.customer;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.Arrays;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.salesmanager.core.business.services.customer.CustomerService;
import com.salesmanager.core.business.services.customer.review.CustomerReviewService;
import com.salesmanager.core.business.services.reference.language.LanguageService;
import com.salesmanager.core.model.common.Billing;
import com.salesmanager.core.model.customer.Customer;
import com.salesmanager.core.model.customer.review.CustomerReview;
import com.salesmanager.core.model.merchant.MerchantStore;
import com.salesmanager.core.model.reference.language.Language;
import com.salesmanager.shop.model.customer.PersistableCustomerReview;
import com.salesmanager.shop.model.customer.ReadableCustomerReview;
import com.salesmanager.shop.store.api.exception.ResourceNotFoundException;
import com.salesmanager.shop.store.controller.customer.facade.CustomerFacadeImpl;

@ExtendWith(MockitoExtension.class)
class CustomerReviewStoreIsolationTest {

  private static final Language EN = new Language("en");

  @Mock
  private CustomerService customerService;

  @Mock
  private CustomerReviewService customerReviewService;

  @Mock
  private LanguageService languageService;

  @InjectMocks
  private CustomerFacadeImpl customerFacade;

  private final MerchantStore storeA = new MerchantStore(1, "ACME", "Acme");
  private final MerchantStore storeB = new MerchantStore(2, "GLOBEX", "Globex");

  @Test
  void createRejectsReviewedCustomerFromAnotherStore() throws Exception {
    when(customerService.getById(42L)).thenReturn(customer(42L, storeA));

    assertThrows(ResourceNotFoundException.class,
        () -> customerFacade.createCustomerReview(42L, review(5L), storeB, EN));

    verify(customerReviewService, never()).create(any(CustomerReview.class));
  }

  @Test
  void createRejectsReviewerFromAnotherStore() throws Exception {
    when(customerService.getById(42L)).thenReturn(customer(42L, storeB));
    when(customerService.getById(5L)).thenReturn(customer(5L, storeA));

    assertThrows(ResourceNotFoundException.class,
        () -> customerFacade.createCustomerReview(42L, review(5L), storeB, EN));

    verify(customerReviewService, never()).create(any(CustomerReview.class));
  }

  @Test
  void createRejectsMissingReviewer() throws Exception {
    when(customerService.getById(42L)).thenReturn(customer(42L, storeA));

    assertThrows(ResourceNotFoundException.class,
        () -> customerFacade.createCustomerReview(42L, review(null), storeA, EN));

    verify(customerReviewService, never()).create(any(CustomerReview.class));
  }

  @Test
  void createAllowsCustomersOfSameStore() throws Exception {
    when(customerService.getById(42L)).thenReturn(customer(42L, storeA));
    when(customerService.getById(5L)).thenReturn(customer(5L, storeA));
    when(languageService.getByCode("en")).thenReturn(EN);

    customerFacade.createCustomerReview(42L, review(5L), storeA, EN);

    verify(customerReviewService).create(any(CustomerReview.class));
  }

  @Test
  void listRejectsCustomerFromAnotherStore() {
    when(customerService.getById(42L)).thenReturn(customer(42L, storeB));

    assertThrows(ResourceNotFoundException.class,
        () -> customerFacade.getAllCustomerReviewsByReviewed(42L, storeA, EN));

    verify(customerReviewService, never()).getByReviewedCustomer(any(Customer.class));
  }

  @Test
  void listOnlyReturnsReviewsWrittenByCustomersOfSameStore() {
    Customer reviewed = customer(42L, storeA);
    when(customerService.getById(42L)).thenReturn(reviewed);
    CustomerReview sameStore = customerReview(7L, customer(5L, storeA), reviewed);
    CustomerReview otherStore = customerReview(8L, customer(6L, storeB), reviewed);
    when(customerReviewService.getByReviewedCustomer(reviewed))
        .thenReturn(Arrays.asList(sameStore, otherStore));

    List<ReadableCustomerReview> reviews =
        customerFacade.getAllCustomerReviewsByReviewed(42L, storeA, EN);

    assertEquals(1, reviews.size());
    assertEquals(7L, reviews.get(0).getId());
  }

  @Test
  void deleteRejectsCustomerFromAnotherStore() throws Exception {
    Customer reviewed = customer(42L, storeA);
    when(customerService.getById(42L)).thenReturn(reviewed);
    lenient().when(customerReviewService.getById(7L))
        .thenReturn(customerReview(7L, customer(5L, storeA), reviewed));

    assertThrows(ResourceNotFoundException.class,
        () -> customerFacade.deleteCustomerReview(42L, 7L, storeB, EN));

    verify(customerReviewService, never()).delete(any(CustomerReview.class));
  }

  @Test
  void deleteRejectsReviewOfAnotherCustomer() throws Exception {
    when(customerService.getById(42L)).thenReturn(customer(42L, storeA));
    when(customerReviewService.getById(7L))
        .thenReturn(customerReview(7L, customer(5L, storeB), customer(99L, storeB)));

    assertThrows(ResourceNotFoundException.class,
        () -> customerFacade.deleteCustomerReview(42L, 7L, storeA, EN));

    verify(customerReviewService, never()).delete(any(CustomerReview.class));
  }

  @Test
  void deleteAllowsReviewOfSameStoreCustomer() throws Exception {
    Customer reviewed = customer(42L, storeA);
    when(customerService.getById(42L)).thenReturn(reviewed);
    CustomerReview review = customerReview(7L, customer(5L, storeA), reviewed);
    when(customerReviewService.getById(7L)).thenReturn(review);

    customerFacade.deleteCustomerReview(42L, 7L, storeA, EN);

    verify(customerReviewService).delete(review);
  }

  @Test
  void updateRejectsCustomerFromAnotherStore() {
    Customer reviewed = customer(42L, storeA);
    when(customerService.getById(42L)).thenReturn(reviewed);
    lenient().when(customerReviewService.getById(7L))
        .thenReturn(customerReview(7L, customer(5L, storeA), reviewed));

    assertThrows(ResourceNotFoundException.class,
        () -> customerFacade.updateCustomerReview(42L, 7L, review(5L), storeB, EN));
  }

  private PersistableCustomerReview review(Long reviewerId) {
    PersistableCustomerReview review = new PersistableCustomerReview();
    review.setCustomerId(reviewerId);
    review.setRating(4D);
    review.setDescription("Great customer");
    return review;
  }

  private CustomerReview customerReview(Long id, Customer reviewer, Customer reviewed) {
    CustomerReview review = new CustomerReview();
    review.setId(id);
    review.setCustomer(reviewer);
    review.setReviewedCustomer(reviewed);
    return review;
  }

  private Customer customer(Long id, MerchantStore store) {
    Customer customer = new Customer();
    customer.setId(id);
    customer.setMerchantStore(store);
    Billing billing = new Billing();
    billing.setFirstName("First" + id);
    billing.setLastName("Last" + id);
    customer.setBilling(billing);
    return customer;
  }
}
