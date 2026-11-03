package com.salesmanager.shop.populator.order;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.lenient;
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
import com.salesmanager.core.model.common.Delivery;
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
public class PersistableOrderApiPopulatorScopeTest {

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
		Delivery delivery = new Delivery();
		delivery.setLastName(lastName);
		customer.setDelivery(delivery);
		return customer;
	}

	private PersistableAnonymousOrder anonymousOrder(Long customerId) {
		PersistableAnonymousOrder order = withPayment(new PersistableAnonymousOrder());
		order.setCustomerId(customerId);
		order.setCustomer(new PersistableCustomer());
		return order;
	}

	@Test(expected = ConversionException.class)
	public void anonymousOrderRequiresCustomerDetails() throws Exception {
		populator.populate(withPayment(new PersistableAnonymousOrder()), new Order(), store, language);
	}

	@Test
	public void anonymousOrderAcceptsNullAndZeroCustomerIdsWithoutLoadingCustomer() throws Exception {
		PersistableCustomer submitted = new PersistableCustomer();
		Customer submittedModel = customer(null, store, "attacker@example.com", "Attacker");
		when(customerPopulator.populate(any(PersistableCustomer.class), any(Customer.class), eq(store), eq(language)))
				.thenReturn(submittedModel);

		Order nullIdResult = populator.populate(anonymousOrder(null), new Order(), store, language);
		Order zeroIdResult = populator.populate(anonymousOrder(0L), new Order(), store, language);

		verify(customerService, never()).getById(anyLong());
		assertNull(nullIdResult.getCustomerId());
		assertNull(zeroIdResult.getCustomerId());
		assertEquals("attacker@example.com", nullIdResult.getCustomerEmailAddress());
		assertEquals("attacker@example.com", zeroIdResult.getCustomerEmailAddress());
	}

	@Test
	public void anonymousOrderUsesSubmittedDeliveryAndBillingInsteadOfDatabaseCustomer() throws Exception {
		PersistableAnonymousOrder order = anonymousOrder(42L);
		PersistableCustomer submitted = order.getCustomer();
		Customer submittedModel = customer(null, store, "attacker@example.com", "Attacker");
		Customer victim = customer(42L, store, "victim@example.com", "Victim");
		lenient().when(customerService.getById(42L)).thenReturn(victim);
		when(customerPopulator.populate(eq(submitted), any(Customer.class), eq(store), eq(language)))
				.thenReturn(submittedModel);

		Order result = populator.populate(order, new Order(), store, language);

		verify(customerService, never()).getById(anyLong());
		assertNull(result.getCustomerId());
		assertEquals("attacker@example.com", result.getCustomerEmailAddress());
		assertEquals("Attacker", result.getBilling().getLastName());
		assertEquals("Attacker", result.getDelivery().getLastName());
	}

	@Test(expected = ConversionException.class)
	public void authenticatedOrderRejectsNullCustomerId() throws Exception {
		populator.populate(withPayment(new PersistableOrder()), new Order(), store, language);
	}

	@Test(expected = ConversionException.class)
	public void authenticatedOrderRejectsZeroCustomerId() throws Exception {
		PersistableOrder order = withPayment(new PersistableOrder());
		order.setCustomerId(0L);
		populator.populate(order, new Order(), store, language);
	}

	@Test(expected = ConversionException.class)
	public void authenticatedOrderRejectsNegativeCustomerId() throws Exception {
		PersistableOrder order = withPayment(new PersistableOrder());
		order.setCustomerId(-1L);
		populator.populate(order, new Order(), store, language);
	}

	@Test(expected = ConversionException.class)
	public void authenticatedOrderRejectsUnknownCustomer() throws Exception {
		PersistableOrder order = withPayment(new PersistableOrder());
		order.setCustomerId(42L);
		when(customerService.getById(42L)).thenReturn(null);
		populator.populate(order, new Order(), store, language);
	}

	@Test(expected = ConversionException.class)
	public void authenticatedOrderRejectsCustomerWithoutStore() throws Exception {
		PersistableOrder order = withPayment(new PersistableOrder());
		order.setCustomerId(42L);
		when(customerService.getById(42L)).thenReturn(customer(42L, null, "user@example.com", "User"));
		populator.populate(order, new Order(), store, language);
	}

	@Test
	public void authenticatedOrderAcceptsDifferentStoreInstanceWithSameId() throws Exception {
		PersistableOrder order = withPayment(new PersistableOrder());
		order.setCustomerId(42L);
		MerchantStore sameIdStore = new MerchantStore();
		sameIdStore.setId(1);
		when(customerService.getById(42L))
				.thenReturn(customer(42L, sameIdStore, "user@example.com", "User"));

		Order result = populator.populate(order, new Order(), store, language);

		assertEquals(Long.valueOf(42L), result.getCustomerId());
		assertEquals("user@example.com", result.getCustomerEmailAddress());
		verify(customerPopulator, never()).populate(any(PersistableCustomer.class), any(Customer.class),
				any(MerchantStore.class), any(Language.class));
	}
}
