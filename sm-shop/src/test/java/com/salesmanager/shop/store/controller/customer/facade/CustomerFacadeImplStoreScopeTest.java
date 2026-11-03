package com.salesmanager.shop.store.controller.customer.facade;

import static org.junit.Assert.assertThrows;
import static org.junit.Assert.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnitRunner;

import com.salesmanager.core.business.services.customer.CustomerService;
import com.salesmanager.core.business.services.customer.review.CustomerReviewService;
import com.salesmanager.core.model.customer.Customer;
import com.salesmanager.core.model.customer.review.CustomerReview;
import com.salesmanager.core.model.merchant.MerchantStore;
import com.salesmanager.core.model.reference.language.Language;
import com.salesmanager.shop.model.customer.PersistableCustomer;
import com.salesmanager.shop.model.customer.PersistableCustomerReview;
import com.salesmanager.shop.model.customer.address.Address;
import com.salesmanager.shop.populator.customer.CustomerPopulator;
import com.salesmanager.shop.store.api.exception.ResourceNotFoundException;

@RunWith(MockitoJUnitRunner.Silent.class)
public class CustomerFacadeImplStoreScopeTest {

  private static final Long OTHER_STORE_CUSTOMER_ID = 42L;
  private static final Long REVIEW_ID = 7L;

  @Mock
  private CustomerService customerService;

  @Mock
  private CustomerReviewService customerReviewService;

  @Mock
  private CustomerPopulator customerPopulator;

  @InjectMocks
  private CustomerFacadeImpl customerFacade;

  private MerchantStore callerStore;
  private Language language;

  @Before
  public void setUp() {
    callerStore = store(2);
    language = new Language();

    Customer otherStoreCustomer = new Customer();
    otherStoreCustomer.setId(OTHER_STORE_CUSTOMER_ID);
    otherStoreCustomer.setMerchantStore(store(1));
    when(customerService.getById(OTHER_STORE_CUSTOMER_ID)).thenReturn(otherStoreCustomer);

    CustomerReview review = new CustomerReview();
    review.setId(REVIEW_ID);
    review.setReviewedCustomer(otherStoreCustomer);
    when(customerReviewService.getById(REVIEW_ID)).thenReturn(review);
  }

  @Test
  public void getCustomerByIdRejectsCustomerOfAnotherStore() {
    assertThrows(ResourceNotFoundException.class,
        () -> customerFacade.getCustomerById(OTHER_STORE_CUSTOMER_ID, callerStore, language));
  }

  @Test
  public void updateRejectsCustomerOfAnotherStore() throws Exception {
    PersistableCustomer customer = new PersistableCustomer();
    customer.setId(OTHER_STORE_CUSTOMER_ID);
    assertThrows(ResourceNotFoundException.class, () -> customerFacade.update(customer, callerStore));
    verify(customerPopulator, never()).populate(any(), any(), any(), any());
    verify(customerService, never()).saveOrUpdate(any());
  }

  @Test
  public void updateAddressRejectsCustomerOfAnotherStore() throws Exception {
    PersistableCustomer customer = new PersistableCustomer();
    customer.setId(OTHER_STORE_CUSTOMER_ID);
    customer.setBilling(new Address());
    assertThrows(ResourceNotFoundException.class, () -> customerFacade.updateAddress(customer, callerStore));
    verify(customerService, never()).saveOrUpdate(any());
  }

  @Test
  public void deleteByIdRejectsCustomerOfAnotherStore() throws Exception {
    assertThrows(ResourceNotFoundException.class,
        () -> customerFacade.deleteById(OTHER_STORE_CUSTOMER_ID, callerStore));
    verify(customerService, never()).delete(any());
  }

  @Test
  public void listReviewsRejectsCustomerOfAnotherStore() {
    assertThrows(ResourceNotFoundException.class,
        () -> customerFacade.getAllCustomerReviewsByReviewed(OTHER_STORE_CUSTOMER_ID, callerStore, language));
  }

  @Test
  public void createReviewRejectsReviewedCustomerOfAnotherStore() throws Exception {
    PersistableCustomerReview review = new PersistableCustomerReview();
    review.setRating(5.0);
    assertThrows(ResourceNotFoundException.class,
        () -> customerFacade.createCustomerReview(OTHER_STORE_CUSTOMER_ID, review, callerStore, language));
    verify(customerReviewService, never()).create(any());
  }

  @Test
  public void createReviewRejectsReviewerOfAnotherStore() throws Exception {
    Customer reviewed = new Customer();
    reviewed.setId(5L);
    reviewed.setMerchantStore(callerStore);
    when(customerService.getById(5L)).thenReturn(reviewed);

    PersistableCustomerReview review = new PersistableCustomerReview();
    review.setRating(5.0);
    review.setCustomerId(OTHER_STORE_CUSTOMER_ID);
    assertThrows(ResourceNotFoundException.class,
        () -> customerFacade.createCustomerReview(5L, review, callerStore, language));
    verify(customerReviewService, never()).create(any());
  }

  @Test
  public void updateReviewRejectsCustomerOfAnotherStore() {
    PersistableCustomerReview review = new PersistableCustomerReview();
    review.setRating(5.0);
    assertThrows(ResourceNotFoundException.class,
        () -> customerFacade.updateCustomerReview(OTHER_STORE_CUSTOMER_ID, REVIEW_ID, review, callerStore, language));
  }

  @Test
  public void deleteReviewRejectsCustomerOfAnotherStore() throws Exception {
    assertThrows(ResourceNotFoundException.class,
        () -> customerFacade.deleteCustomerReview(OTHER_STORE_CUSTOMER_ID, REVIEW_ID, callerStore, language));
    verify(customerReviewService, never()).delete(any());
  }

  @Test
  public void listReviewsAllowsCustomerOfCallerStore() {
    Customer sameStoreCustomer = new Customer();
    sameStoreCustomer.setId(5L);
    sameStoreCustomer.setMerchantStore(store(2));
    when(customerService.getById(5L)).thenReturn(sameStoreCustomer);
    when(customerReviewService.getByReviewedCustomer(sameStoreCustomer)).thenReturn(java.util.Collections.emptyList());

    assertTrue(customerFacade.getAllCustomerReviewsByReviewed(5L, callerStore, language).isEmpty());
  }

  private static MerchantStore store(int id) {
    MerchantStore store = new MerchantStore();
    store.setId(id);
    return store;
  }
}
