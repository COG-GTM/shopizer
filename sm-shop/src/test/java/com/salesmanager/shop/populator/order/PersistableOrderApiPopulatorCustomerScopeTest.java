package com.salesmanager.shop.populator.order;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
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
public class PersistableOrderApiPopulatorCustomerScopeTest {

	private static final long CUSTOMER_ID = 42L;

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
	public void setUp() {
		language = new Language();
		language.setCode("en");
		store = store(1);
		lenient().when(currencyService.getByCode(anyString())).thenReturn(new Currency());
	}

	@Test
	public void anonymousOrderWithoutCustomerIsRejected() throws ConversionException {
		PersistableAnonymousOrder order = anonymousOrder();
		order.setCustomer(null);

		assertThrows(ConversionException.class, () -> populator.populate(order, new Order(), store, language));
		verify(customerService, never()).getById(anyLong());
		verify(customerPopulator, never()).populate(any(), any(), any(), any());
	}

	@Test
	public void anonymousOrderWithZeroIdsIsAccepted() throws Exception {
		PersistableAnonymousOrder order = anonymousOrder();
		PersistableCustomer submitted = new PersistableCustomer();
		submitted.setId(0L);
		submitted.setEmailAddress("guest@example.com");
		order.setCustomer(submitted);

		Customer converted = customer(0L, store, "guest@example.com");
		when(customerPopulator.populate(eq(submitted), any(Customer.class), eq(store), eq(language)))
				.thenReturn(converted);

		Order target = populator.populate(order, new Order(), store, language);

		assertNull(target.getCustomerId());
		verify(customerPopulator).populate(eq(submitted), any(Customer.class), eq(store), eq(language));
		verify(customerService, never()).getById(anyLong());
	}

	@Test
	public void nonAnonymousOrderWithoutCustomerIdIsRejected() {
		PersistableOrder order = order(new PersistableOrder());

		assertThrows(ConversionException.class, () -> populator.populate(order, new Order(), store, language));
		verify(customerService, never()).getById(anyLong());
	}

	@Test
	public void customerWithoutMerchantStoreIsRejected() {
		PersistableOrder order = order(new PersistableOrder());
		order.setCustomerId(CUSTOMER_ID);
		when(customerService.getById(CUSTOMER_ID)).thenReturn(customer(CUSTOMER_ID, null, "customer@example.com"));

		assertThrows(ConversionException.class, () -> populator.populate(order, new Order(), store, language));
	}

	@Test
	public void storeWithoutIdIsRejected() {
		PersistableOrder order = order(new PersistableOrder());
		order.setCustomerId(CUSTOMER_ID);
		MerchantStore storeWithoutId = store(1);
		storeWithoutId.setId(null);
		when(customerService.getById(CUSTOMER_ID))
				.thenReturn(customer(CUSTOMER_ID, store(1), "customer@example.com"));

		assertThrows(ConversionException.class,
				() -> populator.populate(order, new Order(), storeWithoutId, language));
	}

	@Test
	public void unknownAndOtherStoreCustomerHaveTheSameErrorMessage() {
		PersistableOrder order = order(new PersistableOrder());
		order.setCustomerId(CUSTOMER_ID);
		when(customerService.getById(CUSTOMER_ID)).thenReturn(null);
		String unknownCustomerMessage = assertThrows(ConversionException.class,
				() -> populator.populate(order, new Order(), store, language)).getMessage();

		when(customerService.getById(CUSTOMER_ID))
				.thenReturn(customer(CUSTOMER_ID, store(2), "other-store@example.com"));
		String otherStoreMessage = assertThrows(ConversionException.class,
				() -> populator.populate(order, new Order(), store, language)).getMessage();

		assertEquals(unknownCustomerMessage, otherStoreMessage);
	}

	private PersistableAnonymousOrder anonymousOrder() {
		PersistableAnonymousOrder order = order(new PersistableAnonymousOrder());
		PersistableCustomer customer = new PersistableCustomer();
		customer.setEmailAddress("guest@example.com");
		order.setCustomer(customer);
		return order;
	}

	private static <T extends PersistableOrder> T order(T order) {
		PersistablePayment payment = new PersistablePayment();
		payment.setPaymentModule("moneyorder");
		payment.setPaymentType("MONEYORDER");
		payment.setTransactionType("CAPTURE");
		payment.setAmount("10.00");
		order.setPayment(payment);
		order.setCurrency("USD");
		return order;
	}

	private MerchantStore store(Integer id) {
		MerchantStore merchantStore = new MerchantStore();
		merchantStore.setId(id);
		merchantStore.setDefaultLanguage(language);
		return merchantStore;
	}

	private static Customer customer(Long id, MerchantStore merchantStore, String email) {
		Customer customer = new Customer();
		customer.setId(id);
		customer.setMerchantStore(merchantStore);
		customer.setEmailAddress(email);
		customer.setBilling(new Billing());
		customer.setDelivery(new Delivery());
		return customer;
	}
}
