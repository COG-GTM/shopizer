package com.salesmanager.test.shop.unit.customer;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;

import com.salesmanager.core.business.services.customer.CustomerService;
import com.salesmanager.core.business.services.reference.country.CountryService;
import com.salesmanager.core.model.customer.Customer;
import com.salesmanager.core.model.merchant.MerchantStore;
import com.salesmanager.shop.model.customer.PersistableCustomer;
import com.salesmanager.shop.model.customer.ReadableCustomer;
import com.salesmanager.shop.model.customer.address.Address;
import com.salesmanager.shop.populator.customer.CustomerPopulator;
import com.salesmanager.shop.store.api.exception.ResourceNotFoundException;
import com.salesmanager.shop.store.controller.customer.facade.CustomerFacadeImpl;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class CustomerFacadeImplStoreScopeTest {

  @Mock
  private CustomerService customerService;

  @Mock
  private CountryService countryService;

  @Mock
  private CustomerPopulator customerPopulator;

  @InjectMocks
  private CustomerFacadeImpl customerFacade;

  @Test
  void getCustomerByIdUsesSameNotFoundMessageForMissingAndCrossStoreCustomers() {
    when(customerService.getById(42L)).thenReturn(null, customer(42L, store(2)));

    ResourceNotFoundException missing = assertThrows(ResourceNotFoundException.class,
        () -> customerFacade.getCustomerById(42L, store(1), null));
    ResourceNotFoundException crossStore = assertThrows(ResourceNotFoundException.class,
        () -> customerFacade.getCustomerById(42L, store(1), null));

    assertEquals("No Customer found for ID : 42", missing.getErrorMessage());
    assertEquals(missing.getErrorMessage(), crossStore.getErrorMessage());
  }

  @Test
  void getCustomerByIdRejectsCustomerWithoutStore() {
    when(customerService.getById(42L)).thenReturn(customer(42L, null));

    assertThrows(ResourceNotFoundException.class,
        () -> customerFacade.getCustomerById(42L, store(1), null));
  }

  @Test
  void getCustomerByIdComparesStoreIdsRatherThanReferences() {
    MerchantStore requestedStore = store(1000);
    when(customerService.getById(42L)).thenReturn(customer(42L, store(1000)));

    ReadableCustomer result = customerFacade.getCustomerById(42L, requestedStore, null);

    assertEquals(42L, result.getId());
  }

  @Test
  void getCustomerByIdRejectsNullStore() {
    assertThrows(NullPointerException.class,
        () -> customerFacade.getCustomerById(42L, null, null));
  }

  @Test
  void updateRejectsCustomerFromAnotherStoreBeforePopulating() {
    when(customerService.getById(42L)).thenReturn(customer(42L, store(2)));
    PersistableCustomer update = new PersistableCustomer();
    update.setId(42L);

    assertThrows(ResourceNotFoundException.class,
        () -> customerFacade.update(update, store(1)));

    verify(customerService).getById(42L);
    verifyNoMoreInteractions(customerService);
    verifyNoInteractions(customerPopulator);
  }

  @Test
  void updateAddressRejectsCustomerFromAnotherStoreBeforeCountryLookup() {
    when(customerService.getById(42L)).thenReturn(customer(42L, store(2)));
    PersistableCustomer update = new PersistableCustomer();
    update.setId(42L);
    update.setBilling(new Address());

    assertThrows(ResourceNotFoundException.class,
        () -> customerFacade.updateAddress(update, store(1)));

    verify(customerService).getById(42L);
    verifyNoMoreInteractions(customerService);
    verifyNoInteractions(countryService);
  }

  @Test
  void updateAddressByIdRejectsCustomerFromAnotherStoreBeforeCountryLookup() {
    when(customerService.getById(42L)).thenReturn(customer(42L, store(2)));

    assertThrows(ResourceNotFoundException.class,
        () -> customerFacade.updateAddress(42L, store(1), new Address(), null));

    verifyNoInteractions(countryService);
  }

  @Test
  void getAddressRejectsCustomerFromAnotherStore() {
    when(customerService.getById(42L)).thenReturn(customer(42L, store(2)));

    assertThrows(ResourceNotFoundException.class,
        () -> customerFacade.getAddress(42L, store(1), true));
  }

  @Test
  void deleteByIdDeletesCustomerFromSameStore() throws Exception {
    Customer customer = customer(42L, store(1));
    when(customerService.getById(42L)).thenReturn(customer);

    customerFacade.deleteById(42L, store(1));

    verify(customerService).delete(customer);
  }

  @Test
  void deleteByIdDoesNotDeleteMissingCustomer() {
    when(customerService.getById(99L)).thenReturn(null);

    assertThrows(ResourceNotFoundException.class,
        () -> customerFacade.deleteById(99L, store(1)));

    verify(customerService).getById(99L);
    verifyNoMoreInteractions(customerService);
  }

  private Customer customer(Long id, MerchantStore store) {
    Customer customer = new Customer();
    customer.setId(id);
    customer.setMerchantStore(store);
    return customer;
  }

  private MerchantStore store(Integer id) {
    return new MerchantStore(id, "store" + id, "Store " + id);
  }
}
