package com.salesmanager.core.business.services.shipping;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.fail;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.lang.reflect.Field;
import java.math.BigDecimal;
import java.util.Optional;

import org.junit.Before;
import org.junit.Test;

import com.salesmanager.core.business.exception.ServiceException;
import com.salesmanager.core.business.repositories.shipping.ShippingQuoteRepository;
import com.salesmanager.core.model.common.Billing;
import com.salesmanager.core.model.common.Delivery;
import com.salesmanager.core.model.customer.Customer;
import com.salesmanager.core.model.merchant.MerchantStore;
import com.salesmanager.core.model.reference.country.Country;
import com.salesmanager.core.model.shipping.Quote;
import com.salesmanager.core.model.shipping.ShippingSummary;
import com.salesmanager.core.model.shoppingcart.ShoppingCart;

public class ShippingQuoteServiceImplTest {

	private ShippingQuoteRepository repository;
	private ShippingQuoteServiceImpl service;
	private MerchantStore store;
	private ShoppingCart cart;
	private Quote quote;

	@Before
	public void setUp() throws Exception {
		repository = mock(ShippingQuoteRepository.class);
		ShippingService shippingService = mock(ShippingService.class);
		when(shippingService.hasTaxOnShipping(any())).thenReturn(false);
		service = new ShippingQuoteServiceImpl(repository);
		Field f = ShippingQuoteServiceImpl.class.getDeclaredField("shippingService");
		f.setAccessible(true);
		f.set(service, shippingService);

		store = new MerchantStore();
		store.setId(1);
		store.setCode("DEFAULT");

		cart = new ShoppingCart();
		cart.setId(10L);
		cart.setMerchantStore(store);

		quote = new Quote();
		quote.setId(100L);
		quote.setCartId(10L);
		quote.setPrice(new BigDecimal("45"));
		quote.setModule("weightBased");
		quote.setDelivery(delivery("CA", "H2X 1Y4"));
		when(repository.findById(100L)).thenReturn(Optional.of(quote));
		when(repository.findById(999L)).thenReturn(Optional.empty());
	}

	private static Delivery delivery(String iso, String postalCode) {
		Country country = new Country();
		country.setIsoCode(iso);
		Delivery d = new Delivery();
		d.setCountry(country);
		d.setPostalCode(postalCode);
		return d;
	}

	private void assertRejected(Long quoteId, ShoppingCart c, Customer customer) {
		try {
			service.getShippingSummary(quoteId, c, customer, store);
			fail("quote should have been rejected");
		} catch (ServiceException e) {
			assertEquals(ServiceException.EXCEPTION_VALIDATION, e.getExceptionType());
		}
	}

	@Test
	public void acceptsQuoteOfSameCart() throws Exception {
		ShippingSummary summary = service.getShippingSummary(100L, cart, null, store);
		assertNotNull(summary);
		assertEquals(new BigDecimal("45"), summary.getShipping());
	}

	@Test
	public void acceptsQuoteMatchingCustomerAddress() throws Exception {
		Customer customer = new Customer();
		customer.setDelivery(delivery("ca", "h2x1y4"));
		assertNotNull(service.getShippingSummary(100L, cart, customer, store));
	}

	@Test
	public void acceptsQuoteMatchingBillingWhenNoDelivery() throws Exception {
		Customer customer = new Customer();
		Billing billing = new Billing();
		billing.setCountry(delivery("CA", null).getCountry());
		billing.setPostalCode("H2X 1Y4");
		customer.setBilling(billing);
		assertNotNull(service.getShippingSummary(100L, cart, customer, store));
	}

	@Test
	public void rejectsUnknownQuote() {
		assertRejected(999L, cart, null);
	}

	@Test
	public void rejectsQuoteOfAnotherCart() {
		ShoppingCart other = new ShoppingCart();
		other.setId(11L);
		other.setMerchantStore(store);
		assertRejected(100L, other, null);
	}

	@Test
	public void rejectsCartOfAnotherStore() {
		MerchantStore otherStore = new MerchantStore();
		otherStore.setId(2);
		cart.setMerchantStore(otherStore);
		assertRejected(100L, cart, null);
	}

	@Test
	public void rejectsQuoteOfAnotherCustomer() {
		quote.setCustomerId(5L);
		Customer customer = new Customer();
		customer.setId(6L);
		customer.setDelivery(delivery("CA", "H2X 1Y4"));
		assertRejected(100L, cart, customer);
	}

	@Test
	public void rejectsQuoteForDifferentDeliveryAddress() {
		Customer customer = new Customer();
		customer.setDelivery(delivery("US", "90210"));
		assertRejected(100L, cart, customer);
	}

	@Test
	public void rejectsQuoteAlreadyUsedByOrder() {
		quote.setOrderId(50L);
		assertRejected(100L, cart, null);
	}
}
