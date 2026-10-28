package com.salesmanager.test.shop.security;

import static org.junit.Assert.assertNull;
import static org.junit.Assert.fail;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnitRunner;

import com.salesmanager.core.business.services.customer.CustomerService;
import com.salesmanager.core.model.customer.Customer;
import com.salesmanager.core.model.merchant.MerchantStore;
import com.salesmanager.core.model.reference.language.Language;
import com.salesmanager.shop.store.api.exception.ResourceNotFoundException;
import com.salesmanager.shop.store.controller.customer.facade.CustomerFacadeImpl;

@RunWith(MockitoJUnitRunner.class)
public class CustomerFacadeStoreScopedLookupTest {

	private static final String NICK = "alice@example.com";
	private static final int STORE_ID = 2;

	@Mock
	private CustomerService customerService;

	@InjectMocks
	private CustomerFacadeImpl customerFacade;

	@Test
	public void getCustomerByNickUsesStoreIdAndReportsMissingCustomer() {
		MerchantStore store = store();
		when(customerService.getByNick(NICK, STORE_ID)).thenReturn(null);

		try {
			customerFacade.getCustomerByNick(NICK, store, new Language());
			fail("expected ResourceNotFoundException");
		} catch (ResourceNotFoundException expected) {
		}

		verify(customerService).getByNick(NICK, STORE_ID);
		verify(customerService, never()).getByNick(anyString());
	}

	@Test
	public void deleteByNickLooksUpAndDeletesCustomerInStore() throws Exception {
		MerchantStore store = store();
		Customer customer = new Customer();
		customer.setId(8L);
		when(customerService.getByNick(NICK, STORE_ID)).thenReturn(customer);

		customerFacade.deleteByNick(NICK, store);

		verify(customerService).getByNick(NICK, STORE_ID);
		verify(customerService).delete(customer);
		verify(customerService, never()).getByNick(anyString());
	}

	@Test
	public void getCustomerDataByUserNameUsesStoreIdAndReturnsNullWhenMissing() throws Exception {
		MerchantStore store = store();
		when(customerService.getByNick(NICK, STORE_ID)).thenReturn(null);

		assertNull(customerFacade.getCustomerDataByUserName(NICK, store, new Language()));

		verify(customerService).getByNick(NICK, STORE_ID);
		verify(customerService, never()).getByNick(anyString());
	}

	private MerchantStore store() {
		MerchantStore store = new MerchantStore();
		store.setId(STORE_ID);
		store.setCode("globex");
		return store;
	}
}
