package com.salesmanager.shop.store.controller.order.facade;

import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertThrows;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.Collections;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnitRunner;

import com.salesmanager.core.business.services.order.OrderService;
import com.salesmanager.core.model.merchant.MerchantStore;
import com.salesmanager.core.model.order.Order;
import com.salesmanager.core.model.reference.language.Language;
import com.salesmanager.shop.model.customer.ReadableCustomer;
import com.salesmanager.shop.populator.order.ReadableOrderPopulator;
import com.salesmanager.shop.store.api.exception.ResourceNotFoundException;
import com.salesmanager.shop.store.controller.customer.facade.CustomerFacade;

@RunWith(MockitoJUnitRunner.Silent.class)
public class OrderFacadeImplReadableOrderCustomerScopeTest {

  private static final Long ORDER_ID = 21L;
  private static final Long CUSTOMER_ID = 42L;

  @Mock
  private OrderService orderService;

  @Mock
  private CustomerFacade customerFacade;

  @Mock
  private ReadableOrderPopulator readableOrderPopulator;

  @InjectMocks
  private OrderFacadeImpl orderFacade;

  private MerchantStore store;
  private Language language;
  private Order order;

  @Before
  public void setUp() {
    store = new MerchantStore();
    store.setId(2);
    language = new Language();
    order = new Order();
    order.setCustomerId(CUSTOMER_ID);
    order.setOrderProducts(Collections.emptySet());
    when(orderService.getOrder(ORDER_ID, store)).thenReturn(order);
  }

  @Test
  public void returnsOrderWithoutCustomerWhenCustomerIsNotReadableInStore() throws Exception {
    when(customerFacade.getCustomerById(CUSTOMER_ID, store, language))
        .thenThrow(new ResourceNotFoundException("Customer is outside this store"));

    com.salesmanager.shop.model.order.v0.ReadableOrder readable =
        orderFacade.getReadableOrder(ORDER_ID, store, language);

    assertNotNull(readable);
    assertNull(readable.getCustomer());
    verify(readableOrderPopulator).populate(order, readable, store, language);
  }

  @Test
  public void sameStoreOrderIncludesItsCustomer() throws Exception {
    ReadableCustomer customer = new ReadableCustomer();
    when(customerFacade.getCustomerById(CUSTOMER_ID, store, language)).thenReturn(customer);

    com.salesmanager.shop.model.order.v0.ReadableOrder readable =
        orderFacade.getReadableOrder(ORDER_ID, store, language);

    assertSame(customer, readable.getCustomer());
    verify(readableOrderPopulator).populate(order, readable, store, language);
  }

  @Test
  public void missingOrderThrowsNotFound() throws Exception {
    when(orderService.getOrder(ORDER_ID, store)).thenReturn(null);

    assertThrows(ResourceNotFoundException.class,
        () -> orderFacade.getReadableOrder(ORDER_ID, store, language));

    verify(readableOrderPopulator, never()).populate(order, null, store, language);
  }
}
