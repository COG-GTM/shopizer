package com.salesmanager.shop.store.controller.customer.facade;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.fail;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.util.Collections;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.runners.MockitoJUnitRunner;
import com.salesmanager.core.business.services.customer.CustomerService;
import com.salesmanager.core.business.services.reference.country.CountryService;
import com.salesmanager.core.model.customer.Customer;
import com.salesmanager.core.model.merchant.MerchantStore;
import com.salesmanager.core.model.reference.language.Language;
import com.salesmanager.shop.model.customer.PersistableCustomer;
import com.salesmanager.shop.model.customer.address.Address;
import com.salesmanager.shop.populator.customer.CustomerPopulator;
import com.salesmanager.shop.store.api.exception.ResourceNotFoundException;

@RunWith(MockitoJUnitRunner.class)
public class CustomerFacadeImplStoreScopeTest {

  private static final Long CUSTOMER_ID = 17L;

  @InjectMocks
  private CustomerFacadeImpl customerFacade;

  @Mock
  private CustomerService customerService;

  @Mock
  private CustomerPopulator customerPopulator;

  @Mock
  private CountryService countryService;

  private MerchantStore storeA;
  private MerchantStore storeB;

  @Before
  public void setUp() {
    storeA = store(1L);
    storeB = store(2L);
  }

  @Test
  public void getCustomerByIdRejectsAnotherStoreBeforeConversion() throws Exception {
    when(customerService.getById(CUSTOMER_ID)).thenReturn(customer(storeB));

    expectNotFound(() -> customerFacade.getCustomerById(CUSTOMER_ID, storeA, new Language()));

    verify(customerService).getById(CUSTOMER_ID);
    verifyNoInteractions(customerPopulator);
  }

  @Test
  public void getCustomerByIdRejectsCustomerWithoutStore() throws Exception {
    when(customerService.getById(CUSTOMER_ID)).thenReturn(customer(null));

    expectNotFound(() -> customerFacade.getCustomerById(CUSTOMER_ID, storeA, new Language()));
  }

  @Test
  public void getCustomerByIdRejectsStoreWithoutId() throws Exception {
    MerchantStore storeWithoutId = store(null);
    when(customerService.getById(CUSTOMER_ID)).thenReturn(customer(storeA));

    expectNotFound(
        () -> customerFacade.getCustomerById(CUSTOMER_ID, storeWithoutId, new Language()));
  }

  @Test
  public void getCustomerByIdRejectsMissingCustomer() throws Exception {
    when(customerService.getById(CUSTOMER_ID)).thenReturn(null);

    expectNotFound(() -> customerFacade.getCustomerById(CUSTOMER_ID, storeA, new Language()));
  }

  @Test
  public void deleteByIdRejectsAnotherStoreWithoutDeleting() throws Exception {
    when(customerService.getById(CUSTOMER_ID)).thenReturn(customer(storeB));

    expectNotFound(() -> customerFacade.deleteById(CUSTOMER_ID, storeA));

    verify(customerService, never()).delete(org.mockito.ArgumentMatchers.any(Customer.class));
  }

  @Test
  public void deleteByIdAcceptsDifferentStoreInstancesWithEqualIds() throws Exception {
    MerchantStore matchingStore = store(1L);
    Customer customer = customer(matchingStore);
    when(customerService.getById(CUSTOMER_ID)).thenReturn(customer);

    customerFacade.deleteById(CUSTOMER_ID, storeA);

    verify(customerService).delete(customer);
  }

  @Test
  public void updateRejectsAnotherStoreBeforePopulatingOrSaving() throws Exception {
    when(customerService.getById(CUSTOMER_ID)).thenReturn(customer(storeB));
    PersistableCustomer update = new PersistableCustomer();
    update.setId(CUSTOMER_ID);

    expectNotFound(() -> customerFacade.update(update, storeA));

    verifyNoInteractions(customerPopulator);
    verify(customerService, never()).saveOrUpdate(org.mockito.ArgumentMatchers.any(Customer.class));
    verify(customerService, never()).save(org.mockito.ArgumentMatchers.any(Customer.class));
  }

  @Test
  public void updateAddressRejectsAnotherStoreBeforeSaving() throws Exception {
    when(customerService.getById(CUSTOMER_ID)).thenReturn(customer(storeB));
    PersistableCustomer update = new PersistableCustomer();
    update.setId(CUSTOMER_ID);

    expectNotFound(() -> customerFacade.updateAddress(update, storeA));

    verify(customerService, never()).saveOrUpdate(org.mockito.ArgumentMatchers.any(Customer.class));
    verify(customerService, never()).save(org.mockito.ArgumentMatchers.any(Customer.class));
  }

  @Test
  public void getAddressRejectsAnotherStoreWithGenericNotFoundException() throws Exception {
    when(customerService.getById(CUSTOMER_ID)).thenReturn(customer(storeB));

    expectMissingCustomer(() -> customerFacade.getAddress(CUSTOMER_ID, storeA, true));
  }

  @Test
  public void updateAddressByIdRejectsAnotherStoreWithoutSaving() throws Exception {
    when(customerService.getById(CUSTOMER_ID)).thenReturn(customer(storeB));
    when(countryService.getCountriesMap(org.mockito.ArgumentMatchers.any(Language.class)))
        .thenReturn(Collections.emptyMap());

    expectMissingCustomer(
        () -> customerFacade.updateAddress(CUSTOMER_ID, storeA, new Address(), new Language()));

    verify(customerService, never()).saveOrUpdate(org.mockito.ArgumentMatchers.any(Customer.class));
    verify(customerService, never()).save(org.mockito.ArgumentMatchers.any(Customer.class));
  }

  private Customer customer(MerchantStore store) {
    Customer customer = new Customer();
    customer.setMerchantStore(store);
    customer.setId(CUSTOMER_ID);
    return customer;
  }

  private MerchantStore store(Long id) {
    MerchantStore store = new MerchantStore();
    if (id != null) {
      store.setId(id.intValue());
    }
    return store;
  }

  private void expectNotFound(ThrowingRunnable action) throws Exception {
    try {
      action.run();
      fail("Expected ResourceNotFoundException");
    } catch (ResourceNotFoundException expected) {
    }
  }

  private void expectMissingCustomer(ThrowingRunnable action) {
    try {
      action.run();
      fail("Expected exception");
    } catch (Exception expected) {
      assertEquals(Exception.class, expected.getClass());
      assertEquals("customer with given id does not exists", expected.getMessage());
    }
  }

  private interface ThrowingRunnable {
    void run() throws Exception;
  }
}
