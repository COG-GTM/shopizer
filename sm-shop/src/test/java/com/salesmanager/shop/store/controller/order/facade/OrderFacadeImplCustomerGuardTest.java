package com.salesmanager.shop.store.controller.order.facade;

import static org.junit.Assert.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.Locale;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnitRunner;

import com.salesmanager.core.business.exception.ConversionException;
import com.salesmanager.core.business.exception.ServiceException;
import com.salesmanager.core.model.customer.Customer;
import com.salesmanager.core.model.merchant.MerchantStore;
import com.salesmanager.core.model.reference.language.Language;
import com.salesmanager.shop.model.order.v1.PersistableOrder;
import com.salesmanager.shop.populator.order.PersistableOrderApiPopulator;

@RunWith(MockitoJUnitRunner.class)
public class OrderFacadeImplCustomerGuardTest {

	@Mock
	private PersistableOrderApiPopulator persistableOrderApiPopulator;

	@InjectMocks
	private OrderFacadeImpl orderFacade;

	private MerchantStore store;
	private Language language;

	@Before
	public void setUp() throws ConversionException {
		store = new MerchantStore();
		language = new Language();
		language.setCode("en");
		when(persistableOrderApiPopulator.populate(any(), any(), any(), any()))
				.thenThrow(new ConversionException("stop"));
	}

	@Test
	public void missingOrderCustomerIdPassesTheGuard() throws ConversionException {
		PersistableOrder order = new PersistableOrder();

		assertThrows(ServiceException.class,
				() -> orderFacade.processOrder(order, new Customer(), store, language, Locale.ENGLISH));
		verify(persistableOrderApiPopulator, times(1)).populate(any(), any(), any(), any());
	}

	@Test
	public void matchingOrderAndAuthenticatedCustomerIdsPassTheGuard() throws ConversionException {
		PersistableOrder order = new PersistableOrder();
		order.setCustomerId(7L);
		Customer customer = new Customer();
		customer.setId(7L);

		assertThrows(ServiceException.class,
				() -> orderFacade.processOrder(order, customer, store, language, Locale.ENGLISH));
		verify(persistableOrderApiPopulator, times(1)).populate(any(), any(), any(), any());
	}

	@Test
	public void missingAuthenticatedCustomerIdStopsBeforePopulation() throws ConversionException {
		PersistableOrder order = new PersistableOrder();
		order.setCustomerId(7L);

		assertThrows(ServiceException.class,
				() -> orderFacade.processOrder(order, new Customer(), store, language, Locale.ENGLISH));
		verify(persistableOrderApiPopulator, never()).populate(any(), any(), any(), any());
	}
}
