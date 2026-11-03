package com.salesmanager.shop.populator.order;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnitRunner;

import com.salesmanager.core.business.exception.ConversionException;
import com.salesmanager.core.business.services.customer.CustomerService;
import com.salesmanager.core.business.services.reference.currency.CurrencyService;
import com.salesmanager.core.model.common.Billing;
import com.salesmanager.core.model.customer.Customer;
import com.salesmanager.core.model.merchant.MerchantStore;
import com.salesmanager.core.model.order.Order;
import com.salesmanager.core.model.reference.currency.Currency;
import com.salesmanager.core.model.reference.language.Language;
import com.salesmanager.shop.model.customer.PersistableCustomer;
import com.salesmanager.shop.model.order.transaction.PersistablePayment;
import com.salesmanager.shop.model.order.v1.PersistableAnonymousOrder;
import com.salesmanager.shop.model.order.v1.PersistableOrder;
import com.salesmanager.shop.populator.customer.CustomerPopulator;

@RunWith(MockitoJUnitRunner.class)
public class PersistableOrderApiPopulatorTest {

	@Mock
	private CurrencyService currencyService;
	@Mock
	private CustomerService customerService;
	@Mock
	private CustomerPopulator customerPopulator;

	@InjectMocks
	private PersistableOrderApiPopulator populator;

	private MerchantStore store;
	private Language language;

	@Before
	public void setUp() throws Exception {
		language = new Language("en");
		store = new MerchantStore();
		store.setId(1);
		store.setDefaultLanguage(language);
		when(currencyService.getByCode("USD")).thenReturn(new Currency());
	}

	private <T extends PersistableOrder> T withPayment(T order) {
		PersistablePayment payment = new PersistablePayment();
		payment.setPaymentModule("moneyorder");
		payment.setPaymentType("MONEYORDER");
		order.setPayment(payment);
		order.setCurrency("USD");
		return order;
	}

	private Customer customer(Long id, MerchantStore merchant, String email, String lastName) {
		Customer customer = new Customer();
		customer.setId(id);
		customer.setMerchantStore(merchant);
		customer.setEmailAddress(email);
		Billing billing = new Billing();
		billing.setLastName(lastName);
		customer.setBilling(billing);
		return customer;
	}

	@Test
	public void anonymousOrderIgnoresCustomerIdFromBody() throws Exception {
		PersistableAnonymousOrder order = withPayment(new PersistableAnonymousOrder());
		order.setCustomerId(42L);
		PersistableCustomer submitted = new PersistableCustomer();
		order.setCustomer(submitted);

		Customer submittedModel = customer(null, store, "mallory@example.com", "Mallory");
		when(customerPopulator.populate(eq(submitted), any(Customer.class), eq(store), eq(language)))
				.thenReturn(submittedModel);

		Order result = populator.populate(order, new Order(), store, language);

		verify(customerService, never()).getById(anyLong());
		assertNull(result.getCustomerId());
		assertEquals("mallory@example.com", result.getCustomerEmailAddress());
		assertEquals("Mallory", result.getBilling().getLastName());
	}

	@Test(expected = ConversionException.class)
	public void authenticatedOrderRejectsCustomerFromAnotherStore() throws Exception {
		MerchantStore otherStore = new MerchantStore();
		otherStore.setId(2);
		when(customerService.getById(42L)).thenReturn(customer(42L, otherStore, "bob@example.com", "Bob"));

		PersistableOrder order = withPayment(new PersistableOrder());
		order.setCustomerId(42L);

		populator.populate(order, new Order(), store, language);
	}

	@Test
	public void authenticatedOrderUsesCustomerFromSameStore() throws Exception {
		when(customerService.getById(42L)).thenReturn(customer(42L, store, "bob@example.com", "Bob"));

		PersistableOrder order = withPayment(new PersistableOrder());
		order.setCustomerId(42L);

		Order result = populator.populate(order, new Order(), store, language);

		assertEquals(Long.valueOf(42L), result.getCustomerId());
		assertEquals("bob@example.com", result.getCustomerEmailAddress());
	}
}
