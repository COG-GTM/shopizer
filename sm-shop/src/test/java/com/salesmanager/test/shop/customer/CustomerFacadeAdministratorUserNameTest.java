package com.salesmanager.test.shop.customer;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.mockito.ArgumentMatchers.any;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnitRunner;

import com.salesmanager.core.business.exception.ServiceException;
import com.salesmanager.core.business.services.customer.CustomerService;
import com.salesmanager.core.business.services.user.UserService;
import com.salesmanager.core.model.customer.Customer;
import com.salesmanager.core.model.user.User;
import com.salesmanager.shop.model.customer.PersistableCustomer;
import com.salesmanager.shop.model.customer.UserAlreadyExistException;
import com.salesmanager.shop.store.api.exception.ServiceRuntimeException;
import com.salesmanager.shop.store.controller.customer.facade.CustomerFacadeImpl;

@RunWith(MockitoJUnitRunner.class)
public class CustomerFacadeAdministratorUserNameTest {

  @Mock
  private UserService userService;

  @Mock
  private CustomerService customerService;

  @InjectMocks
  private CustomerFacadeImpl facade;

  @Test
  public void blankNamesDoNotQueryUsers() {
    assertFalse(facade.isAdministratorUserName(null));
    assertFalse(facade.isAdministratorUserName(""));
    assertFalse(facade.isAdministratorUserName("   "));

    verifyNoInteractions(userService);
  }

  @Test
  public void exactAdministratorNameIsRecognized() throws Exception {
    when(userService.getByUserName("admin@shopizer.com")).thenReturn(new User());

    assertTrue(facade.isAdministratorUserName("admin@shopizer.com"));
  }

  @Test
  public void trimmedNameChecksOriginalAndLowercaseValues() throws Exception {
    when(userService.getByUserName("ADMIN@Shopizer.com")).thenReturn(null);
    when(userService.getByUserName("admin@shopizer.com")).thenReturn(new User());

    assertTrue(facade.isAdministratorUserName("  ADMIN@Shopizer.com "));
    verify(userService).getByUserName("ADMIN@Shopizer.com");
    verify(userService).getByUserName("admin@shopizer.com");
  }

  @Test
  public void unknownNameReturnsFalseAfterBothLookups() throws Exception {
    when(userService.getByUserName("Unknown@Example.com")).thenReturn(null);
    when(userService.getByUserName("unknown@example.com")).thenReturn(null);

    assertFalse(facade.isAdministratorUserName("Unknown@Example.com"));
    verify(userService).getByUserName("Unknown@Example.com");
    verify(userService).getByUserName("unknown@example.com");
  }

  @Test(expected = ServiceRuntimeException.class)
  public void serviceFailureIsWrapped() throws Exception {
    when(userService.getByUserName("admin@shopizer.com"))
        .thenThrow(new ServiceException("lookup failed"));

    facade.isAdministratorUserName("admin@shopizer.com");
  }

  @Test
  public void registrationRejectsAdministratorNameBeforeSavingCustomer() throws Exception {
    when(customerService.getByNick("admin@shopizer.com")).thenReturn(null);
    when(userService.getByUserName("admin@shopizer.com")).thenReturn(new User());
    PersistableCustomer customer = new PersistableCustomer();
    customer.setUserName("admin@shopizer.com");

    try {
      facade.registerCustomer(customer, null, null);
      org.junit.Assert.fail("administrator username must not register as a customer");
    } catch (UserAlreadyExistException expected) {
      verify(customerService, never()).saveOrUpdate(any(Customer.class));
    }
  }
}
