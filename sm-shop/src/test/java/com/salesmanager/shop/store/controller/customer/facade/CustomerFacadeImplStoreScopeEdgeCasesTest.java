package com.salesmanager.shop.store.controller.customer.facade;

import static org.junit.Assert.assertThrows;
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
public class CustomerFacadeImplStoreScopeEdgeCasesTest {

  private static final Long CUSTOMER_ID = 42L;
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
  }

  @Test
  public void getCustomerByIdRejectsMissingNullIdAndNullStoreCustomer() {
    when(customerService.getById(CUSTOMER_ID)).thenReturn(null);
    assertThrows(ResourceNotFoundException.class,
        () -> customerFacade.getCustomerById(CUSTOMER_ID, callerStore, language));
    assertThrows(ResourceNotFoundException.class,
        () -> customerFacade.getCustomerById(null, callerStore, language));

    Customer customerWithoutStore = new Customer();
    customerWithoutStore.setId(CUSTOMER_ID);
    when(customerService.getById(CUSTOMER_ID)).thenReturn(customerWithoutStore);
    assertThrows(ResourceNotFoundException.class,
        () -> customerFacade.getCustomerById(CUSTOMER_ID, callerStore, language));
  }

  @Test
  public void sameStoreDeleteLoadsAndDeletesCustomer() throws Exception {
    Customer customer = customer(CUSTOMER_ID, callerStore);
    when(customerService.getById(CUSTOMER_ID)).thenReturn(customer);

    customerFacade.deleteById(CUSTOMER_ID, callerStore);

    verify(customerService).delete(customer);
  }

  @Test
  public void deleteByIdRejectsCrossStoreAndMissingCustomers() throws Exception {
    when(customerService.getById(CUSTOMER_ID)).thenReturn(customer(CUSTOMER_ID, store(1)), null);

    assertThrows(ResourceNotFoundException.class,
        () -> customerFacade.deleteById(CUSTOMER_ID, callerStore));
    assertThrows(ResourceNotFoundException.class,
        () -> customerFacade.deleteById(99L, callerStore));

    verify(customerService, never()).delete(any(Customer.class));
  }

  @Test
  public void getAddressRejectsCrossStoreCustomerWithNotFound() {
    when(customerService.getById(CUSTOMER_ID)).thenReturn(customer(CUSTOMER_ID, store(1)));

    assertThrows(ResourceNotFoundException.class,
        () -> customerFacade.getAddress(CUSTOMER_ID, callerStore, true));
  }

  @Test
  public void updateAddressByIdRejectsCrossStoreBeforeSaving() throws Exception {
    when(customerService.getById(CUSTOMER_ID)).thenReturn(customer(CUSTOMER_ID, store(1)));

    assertThrows(ResourceNotFoundException.class,
        () -> customerFacade.updateAddress(CUSTOMER_ID, callerStore, new Address(), language));

    verify(customerService, never()).saveOrUpdate(any(Customer.class));
  }

  @Test
  public void updateAddressPersistableRejectsCrossStoreWithNotFound() throws Exception {
    when(customerService.getById(CUSTOMER_ID)).thenReturn(customer(CUSTOMER_ID, store(1)));
    PersistableCustomer customer = new PersistableCustomer();
    customer.setId(CUSTOMER_ID);

    assertThrows(ResourceNotFoundException.class,
        () -> customerFacade.updateAddress(customer, callerStore));

    verify(customerService, never()).saveOrUpdate(any(Customer.class));
  }

  @Test
  public void createReviewRejectsNullOrMissingReviewerBeforePersistence() throws Exception {
    Customer reviewed = customer(CUSTOMER_ID, callerStore);
    when(customerService.getById(CUSTOMER_ID)).thenReturn(reviewed);

    PersistableCustomerReview reviewWithoutReviewer = review(null);
    assertThrows(ResourceNotFoundException.class,
        () -> customerFacade.createCustomerReview(CUSTOMER_ID, reviewWithoutReviewer, callerStore, language));

    when(customerService.getById(99L)).thenReturn(null);
    PersistableCustomerReview reviewWithMissingReviewer = review(99L);
    assertThrows(ResourceNotFoundException.class,
        () -> customerFacade.createCustomerReview(CUSTOMER_ID, reviewWithMissingReviewer, callerStore, language));

    verify(customerReviewService, never()).create(any(CustomerReview.class));
  }

  @Test
  public void deleteReviewRejectsReviewForDifferentSameStoreCustomer() throws Exception {
    Customer reviewed = customer(CUSTOMER_ID, callerStore);
    Customer anotherCustomer = customer(43L, callerStore);
    CustomerReview persistedReview = new CustomerReview();
    persistedReview.setId(REVIEW_ID);
    persistedReview.setReviewedCustomer(anotherCustomer);
    when(customerService.getById(CUSTOMER_ID)).thenReturn(reviewed);
    when(customerReviewService.getById(REVIEW_ID)).thenReturn(persistedReview);

    assertThrows(ResourceNotFoundException.class,
        () -> customerFacade.deleteCustomerReview(CUSTOMER_ID, REVIEW_ID, callerStore, language));

    verify(customerReviewService, never()).delete(any(CustomerReview.class));
  }

  @Test
  public void updateReviewRejectsReviewForDifferentSameStoreCustomer() {
    Customer reviewed = customer(CUSTOMER_ID, callerStore);
    Customer anotherCustomer = customer(43L, callerStore);
    CustomerReview persistedReview = new CustomerReview();
    persistedReview.setId(REVIEW_ID);
    persistedReview.setReviewedCustomer(anotherCustomer);
    when(customerService.getById(CUSTOMER_ID)).thenReturn(reviewed);
    when(customerReviewService.getById(REVIEW_ID)).thenReturn(persistedReview);

    assertThrows(ResourceNotFoundException.class,
        () -> customerFacade.updateCustomerReview(CUSTOMER_ID, REVIEW_ID, review(null), callerStore, language));
  }

  private static PersistableCustomerReview review(Long reviewerId) {
    PersistableCustomerReview review = new PersistableCustomerReview();
    review.setCustomerId(reviewerId);
    review.setRating(5.0);
    review.setDescription("Store scope edge case");
    return review;
  }

  private static Customer customer(Long id, MerchantStore store) {
    Customer customer = new Customer();
    customer.setId(id);
    customer.setMerchantStore(store);
    return customer;
  }

  private static MerchantStore store(int id) {
    MerchantStore store = new MerchantStore();
    store.setId(id);
    return store;
  }
}
