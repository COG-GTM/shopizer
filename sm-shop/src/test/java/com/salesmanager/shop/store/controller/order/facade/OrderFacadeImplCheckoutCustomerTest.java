package com.salesmanager.shop.store.controller.order.facade;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

import java.util.Locale;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.salesmanager.core.business.exception.ServiceException;
import com.salesmanager.core.model.customer.Customer;
import com.salesmanager.core.model.merchant.MerchantStore;
import com.salesmanager.core.model.reference.language.Language;
import com.salesmanager.shop.model.order.v1.PersistableAnonymousOrder;
import com.salesmanager.shop.model.order.v1.PersistableOrder;
import com.salesmanager.shop.populator.order.PersistableOrderApiPopulator;

@ExtendWith(MockitoExtension.class)
class OrderFacadeImplCheckoutCustomerTest {

	@Mock
	private PersistableOrderApiPopulator persistableOrderApiPopulator;

	@InjectMocks
	private OrderFacadeImpl orderFacade;

	@Test
	void anonymousCustomerCannotCheckoutWithAnotherCustomersId() throws Exception {
		PersistableAnonymousOrder order = new PersistableAnonymousOrder();
		order.setCustomerId(42L);

		assertThrows(ServiceException.class, () -> orderFacade.processOrder(order, new Customer(),
				new MerchantStore(), new Language("en"), Locale.ENGLISH));
		verify(persistableOrderApiPopulator, never()).populate(any(), any(), any(), any());
	}

	@Test
	void authenticatedCustomerCannotCheckoutWithAnotherCustomersId() throws Exception {
		PersistableOrder order = new PersistableOrder();
		order.setCustomerId(42L);
		Customer authenticated = new Customer();
		authenticated.setId(7L);

		assertThrows(ServiceException.class, () -> orderFacade.processOrder(order, authenticated,
				new MerchantStore(), new Language("en"), Locale.ENGLISH));
		verify(persistableOrderApiPopulator, never()).populate(any(), any(), any(), any());
	}
}
