package com.salesmanager.test.shop.integration.customer;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.salesmanager.core.business.services.customer.CustomerService;
import com.salesmanager.core.model.customer.Customer;
import com.salesmanager.core.model.merchant.MerchantStore;
import com.salesmanager.shop.model.customer.ReadableCustomer;
import com.salesmanager.shop.store.api.exception.ResourceNotFoundException;
import com.salesmanager.shop.store.controller.customer.facade.CustomerFacadeImpl;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class CustomerFacadeImplTest {

  @Mock
  private CustomerService customerService;

  @InjectMocks
  private CustomerFacadeImpl customerFacade;

  @Test
  void getCustomerByIdRejectsCustomerFromAnotherStore() {
    MerchantStore requestedStore = store(1);
    when(customerService.getById(42L)).thenReturn(customer(42L, store(2)));

    assertThrows(ResourceNotFoundException.class,
        () -> customerFacade.getCustomerById(42L, requestedStore, null));
  }

  @Test
  void getCustomerByIdAllowsCustomerFromSameStore() {
    MerchantStore store = store(1);
    when(customerService.getById(42L)).thenReturn(customer(42L, store));

    ReadableCustomer result = customerFacade.getCustomerById(42L, store, null);

    assertEquals(42L, result.getId());
  }

  @Test
  void deleteByIdRejectsCustomerFromAnotherStoreWithoutDeleting() throws Exception {
    when(customerService.getById(42L)).thenReturn(customer(42L, store(2)));

    assertThrows(ResourceNotFoundException.class,
        () -> customerFacade.deleteById(42L, store(1)));

    verify(customerService, never()).delete(any(Customer.class));
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
