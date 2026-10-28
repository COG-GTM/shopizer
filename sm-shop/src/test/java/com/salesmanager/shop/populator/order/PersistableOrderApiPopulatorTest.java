package com.salesmanager.shop.populator.order;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

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

@ExtendWith(MockitoExtension.class)
class PersistableOrderApiPopulatorTest {

	private static final long VICTIM_ID = 42L;

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

	@BeforeEach
	void setUp() {
		language = new Language("en");
		store = store(1);
		lenient().when(currencyService.getByCode(any())).thenReturn(new Currency());
	}

	@Test
	void anonymousCheckoutRejectsCustomerIdOfAnotherCustomer() {
		PersistableAnonymousOrder order = anonymousOrder();
		order.setCustomerId(VICTIM_ID);

		assertThrows(ConversionException.class, () -> populator.populate(order, new Order(), store, language));
		verify(customerService, never()).getById(anyLong());
	}

	@Test
	void anonymousCheckoutRejectsCustomerEntityId() {
		PersistableAnonymousOrder order = anonymousOrder();
		order.getCustomer().setId(VICTIM_ID);

		assertThrows(ConversionException.class, () -> populator.populate(order, new Order(), store, language));
		verify(customerService, never()).getById(anyLong());
	}

	@Test
	void anonymousCheckoutUsesOnlySubmittedCustomerDetails() throws Exception {
		PersistableAnonymousOrder order = anonymousOrder();
		Customer submitted = customer(null, store, "mallory@example.com");
		when(customerPopulator.populate(eq(order.getCustomer()), any(Customer.class), eq(store), eq(language)))
				.thenReturn(submitted);

		Order target = populator.populate(order, new Order(), store, language);

		assertEquals("mallory@example.com", target.getCustomerEmailAddress());
		assertSame(submitted.getBilling(), target.getBilling());
		assertSame(submitted.getDelivery(), target.getDelivery());
		assertNull(target.getCustomerId());
		verify(customerService, never()).getById(anyLong());
	}

	@Test
	void customerIdFromAnotherStoreIsRejected() {
		PersistableOrder order = order(new PersistableOrder());
		order.setCustomerId(VICTIM_ID);
		when(customerService.getById(VICTIM_ID)).thenReturn(customer(VICTIM_ID, store(2), "bob@example.com"));

		assertThrows(ConversionException.class, () -> populator.populate(order, new Order(), store, language));
	}

	@Test
	void unknownCustomerIdIsRejected() {
		PersistableOrder order = order(new PersistableOrder());
		order.setCustomerId(VICTIM_ID);
		when(customerService.getById(VICTIM_ID)).thenReturn(null);

		assertThrows(ConversionException.class, () -> populator.populate(order, new Order(), store, language));
	}

	@Test
	void customerIdOfSameStoreIsAccepted() throws Exception {
		PersistableOrder order = order(new PersistableOrder());
		order.setCustomerId(VICTIM_ID);
		Customer existing = customer(VICTIM_ID, store(1), "bob@example.com");
		when(customerService.getById(VICTIM_ID)).thenReturn(existing);

		Order target = populator.populate(order, new Order(), store, language);

		assertEquals(Long.valueOf(VICTIM_ID), target.getCustomerId());
		assertEquals("bob@example.com", target.getCustomerEmailAddress());
		assertSame(existing.getBilling(), target.getBilling());
	}

	private PersistableAnonymousOrder anonymousOrder() {
		PersistableAnonymousOrder order = order(new PersistableAnonymousOrder());
		PersistableCustomer customer = new PersistableCustomer();
		customer.setEmailAddress("mallory@example.com");
		order.setCustomer(customer);
		return order;
	}

	private static <T extends PersistableOrder> T order(T order) {
		PersistablePayment payment = new PersistablePayment();
		payment.setPaymentModule("moneyorder");
		payment.setPaymentType("MONEYORDER");
		payment.setAmount("10.00");
		order.setPayment(payment);
		order.setCurrency("USD");
		return order;
	}

	private MerchantStore store(int id) {
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
