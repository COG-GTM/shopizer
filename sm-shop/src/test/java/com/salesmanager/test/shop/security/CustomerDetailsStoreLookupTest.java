package com.salesmanager.test.shop.security;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.fail;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import com.salesmanager.core.business.services.customer.CustomerService;
import com.salesmanager.core.business.services.user.GroupService;
import com.salesmanager.core.business.services.user.PermissionService;
import com.salesmanager.core.model.common.Billing;
import com.salesmanager.core.model.customer.Customer;
import com.salesmanager.core.model.merchant.MerchantStore;
import com.salesmanager.shop.store.security.customer.JWTCustomerServicesImpl;
import com.salesmanager.shop.store.security.user.JWTUser;

/**
 * Same nick in two stores: each login resolves the customer of the requested store.
 */
public class CustomerDetailsStoreLookupTest {

	private static final String NICK = "alice@example.com";

	private CustomerService customerService;
	private JWTCustomerServicesImpl detailsService;

	@Before
	public void setUp() {
		customerService = mock(CustomerService.class);
		detailsService = new JWTCustomerServicesImpl(customerService, mock(PermissionService.class),
				mock(GroupService.class));
		when(customerService.getByNick(NICK, "DEFAULT")).thenReturn(customer(1L, "DEFAULT"));
		when(customerService.getByNick(NICK, "GLOBEX")).thenReturn(customer(2L, "GLOBEX"));
	}

	@After
	public void tearDown() {
		RequestContextHolder.resetRequestAttributes();
	}

	private static Customer customer(Long id, String storeCode) {
		MerchantStore store = new MerchantStore();
		store.setCode(storeCode);
		Billing billing = new Billing();
		billing.setFirstName("Alice");
		billing.setLastName("Doe");
		Customer customer = new Customer();
		customer.setId(id);
		customer.setNick(NICK);
		customer.setEmailAddress(NICK);
		customer.setPassword("hash");
		customer.setMerchantStore(store);
		customer.setBilling(billing);
		return customer;
	}

	private static void requestForStore(String storeCode) {
		MockHttpServletRequest request = new MockHttpServletRequest();
		if (storeCode != null) {
			request.setParameter("store", storeCode);
		}
		RequestContextHolder.setRequestAttributes(new ServletRequestAttributes(request));
	}

	@Test
	public void resolvesCustomerInRequestedStore() {
		requestForStore("GLOBEX");
		JWTUser user = (JWTUser) detailsService.loadUserByUsername(NICK);
		assertEquals(Long.valueOf(2L), user.getId());
		assertEquals("GLOBEX", user.getStoreCode());

		requestForStore(null);
		user = (JWTUser) detailsService.loadUserByUsername(NICK);
		assertEquals(Long.valueOf(1L), user.getId());
		assertEquals("DEFAULT", user.getStoreCode());

		verify(customerService, never()).getByNick(anyString());
	}

	@Test
	public void unknownCustomerInRequestedStoreIsNotFound() {
		requestForStore("ACME");
		try {
			detailsService.loadUserByUsername(NICK);
			fail("expected UsernameNotFoundException");
		} catch (UsernameNotFoundException expected) {
		}
	}
}
