package com.salesmanager.test.shop.integration.customer;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.salesmanager.core.business.exception.ConversionException;
import com.salesmanager.core.business.services.customer.CustomerService;
import com.salesmanager.core.business.services.reference.language.LanguageService;
import com.salesmanager.core.model.customer.Customer;
import com.salesmanager.core.model.customer.review.CustomerReview;
import com.salesmanager.core.model.merchant.MerchantStore;
import com.salesmanager.core.model.reference.language.Language;
import com.salesmanager.shop.model.customer.PersistableCustomerReview;
import com.salesmanager.shop.populator.customer.PersistableCustomerReviewPopulator;

@ExtendWith(MockitoExtension.class)
class PersistableCustomerReviewPopulatorTest {
  @Mock private CustomerService customers;
  @Mock private LanguageService languages;
  private final MerchantStore store = new MerchantStore(1, "DEFAULT", "Default");
  private final MerchantStore other = new MerchantStore(2, "OTHER", "Other");
  private final Language en = new Language("en");
  private PersistableCustomerReviewPopulator populator;
  private PersistableCustomerReview source;

  @BeforeEach
  void setUp() {
    populator = new PersistableCustomerReviewPopulator();
    populator.setCustomerService(customers);
    populator.setLanguageService(languages);
    source = new PersistableCustomerReview();
    source.setCustomerId(1L);
    source.setReviewedCustomer(2L);
    source.setRating(4D);
    source.setDescription("Helpful customer");
  }

  @Test void sameStoreCustomersPopulateReview() throws Exception {
    Customer reviewer = customer(1L, store);
    Customer reviewed = customer(2L, new MerchantStore(1, "DEFAULT", "Default"));
    when(customers.getById(1L)).thenReturn(reviewer);
    when(customers.getById(2L)).thenReturn(reviewed);
    when(languages.getByCode("en")).thenReturn(en);
    CustomerReview result = populator.populate(source, null, store, en);
    assertSame(reviewer, result.getCustomer());
    assertSame(reviewed, result.getReviewedCustomer());
    assertEquals(4D, result.getReviewRating());
    assertEquals("Helpful customer", result.getDescriptions().iterator().next().getDescription());
  }

  @Test void rejectsReviewerInAnotherStore() { reject(customer(1L, other), customer(2L, store)); }
  @Test void rejectsReviewedInAnotherStore() { reject(customer(1L, store), customer(2L, other)); }
  @Test void rejectsReviewerWithoutStore() { reject(customer(1L, null), customer(2L, store)); }
  @Test void rejectsReviewedWithoutStore() { reject(customer(1L, store), customer(2L, null)); }
  @Test void rejectsMissingReviewer() { reject(null, customer(2L, store)); }
  @Test void rejectsMissingReviewed() { reject(customer(1L, store), null); }

  private void reject(Customer reviewer, Customer reviewed) {
    when(customers.getById(1L)).thenReturn(reviewer);
    when(customers.getById(2L)).thenReturn(reviewed);
    assertThrows(ConversionException.class, () -> populator.populate(source, null, store, en));
    verifyNoInteractions(languages);
  }

  private Customer customer(Long id, MerchantStore merchantStore) {
    Customer customer = new Customer();
    customer.setId(id);
    customer.setMerchantStore(merchantStore);
    return customer;
  }
}
