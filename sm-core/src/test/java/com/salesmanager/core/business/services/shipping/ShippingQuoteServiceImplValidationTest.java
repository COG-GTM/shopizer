package com.salesmanager.core.business.services.shipping;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.mockito.ArgumentMatchers.anyLong;

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
import com.salesmanager.core.model.reference.zone.Zone;
import com.salesmanager.core.model.shipping.Quote;
import com.salesmanager.core.model.shipping.ShippingSummary;
import com.salesmanager.core.model.shoppingcart.ShoppingCart;

public class ShippingQuoteServiceImplValidationTest {

	private ShippingQuoteRepository repository;
	private ShippingService shippingService;
	private ShippingQuoteServiceImpl service;
	private MerchantStore store;
	private ShoppingCart cart;
	private Quote quote;

	@Before
	public void setUp() throws Exception {
		repository = org.mockito.Mockito.mock(ShippingQuoteRepository.class);
		shippingService = org.mockito.Mockito.mock(ShippingService.class);
		when(shippingService.hasTaxOnShipping(org.mockito.ArgumentMatchers.any())).thenReturn(false);
		service = new ShippingQuoteServiceImpl(repository);
		Field field = ShippingQuoteServiceImpl.class.getDeclaredField("shippingService");
		field.setAccessible(true);
		field.set(service, shippingService);

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
		quote.setHandling(new BigDecimal("2.50"));
		quote.setModule("weightBased");
		quote.setOptionName("Ground");
		quote.setOptionCode("GROUND");
		quote.setDelivery(delivery("CA", "H3B 1A1"));
		when(repository.findById(100L)).thenReturn(Optional.of(quote));
	}

	private static Delivery delivery(String iso, String postalCode) {
		Country country = new Country();
		country.setIsoCode(iso);
		Delivery address = new Delivery();
		address.setCountry(country);
		address.setPostalCode(postalCode);
		return address;
	}

	private void assertRejected(ShoppingCart targetCart, Customer customer, MerchantStore targetStore) {
		try {
			service.getShippingSummary(100L, targetCart, customer, targetStore);
			fail("quote should have been rejected");
		} catch (ServiceException exception) {
			assertEquals(ServiceException.EXCEPTION_VALIDATION, exception.getExceptionType());
		}
	}

	@Test
	public void acceptsAnonymousCartWithoutAddressValidation() throws Exception {
		quote.setDelivery(null);

		assertNotNull(service.getShippingSummary(100L, cart, null, store));
	}

	@Test
	public void acceptsMatchingCustomerWhenQuoteHasNoCustomerId() throws Exception {
		Customer customer = new Customer();
		customer.setId(55L);
		customer.setDelivery(delivery("CA", "H3B 1A1"));

		assertNotNull(service.getShippingSummary(100L, cart, customer, store));
	}

	@Test
	public void normalizesPostalCodeAndCountryCase() throws Exception {
		Customer customer = new Customer();
		customer.setDelivery(delivery("ca", "h3b1a1"));

		assertNotNull(service.getShippingSummary(100L, cart, customer, store));
	}

	@Test
	public void rejectsMissingQuoteDeliveryForCustomer() {
		quote.setDelivery(null);
		Customer customer = new Customer();
		customer.setDelivery(delivery("CA", "H3B 1A1"));

		assertRejected(cart, customer, store);
	}

	@Test
	public void ignoresCustomerZoneAndStateWhenQuoteOmitsThem() throws Exception {
		quote.getDelivery().setState(" ");
		Customer customer = new Customer();
		Delivery address = delivery("CA", "H3B 1A1");
		Zone zone = new Zone();
		zone.setCode("QC");
		address.setZone(zone);
		address.setState("Ontario");
		customer.setDelivery(address);

		assertNotNull(service.getShippingSummary(100L, cart, customer, store));
	}

	@Test
	public void rejectsBillingAddressWithDifferentZoneWhenDeliveryIsAbsent() {
		Zone quoteZone = new Zone();
		quoteZone.setCode("QC");
		quote.getDelivery().setZone(quoteZone);
		Customer customer = new Customer();
		Billing billing = new Billing();
		billing.setCountry(delivery("CA", "H3B 1A1").getCountry());
		billing.setPostalCode("H3B 1A1");
		Zone billingZone = new Zone();
		billingZone.setCode("ON");
		billing.setZone(billingZone);
		customer.setBilling(billing);

		assertRejected(cart, customer, store);
	}

	@Test
	public void rejectsQuoteWithoutCartId() {
		quote.setCartId(null);

		assertRejected(cart, null, store);
	}

	@Test
	public void rejectsCartWithoutMerchantStore() {
		cart.setMerchantStore(null);

		assertRejected(cart, null, store);
	}

	@Test
	public void nullCartIsRejectedByPrecondition() {
		try {
			service.getShippingSummary(100L, null, null, store);
			fail("null cart should fail the precondition");
		} catch (NullPointerException expected) {
			assertNotNull(expected);
		} catch (ServiceException unexpected) {
			fail("expected NullPointerException but got " + unexpected);
		}
	}

	@Test
	public void nullStoreIsRejectedByPrecondition() {
		try {
			service.getShippingSummary(100L, cart, null, null);
			fail("null store should fail the precondition");
		} catch (NullPointerException expected) {
			assertNotNull(expected);
		} catch (ServiceException unexpected) {
			fail("expected NullPointerException but got " + unexpected);
		}
	}

	@Test
	public void copiesQuoteFieldsAndTaxConfiguration() throws Exception {
		quote.setFreeShipping(true);
		when(shippingService.hasTaxOnShipping(store)).thenReturn(true);

		ShippingSummary summary = service.getShippingSummary(100L, cart, null, store);

		assertEquals(new BigDecimal("45"), summary.getShipping());
		assertEquals(new BigDecimal("2.50"), summary.getHandling());
		assertEquals("weightBased", summary.getShippingModule());
		assertEquals("Ground", summary.getShippingOption());
		assertEquals("GROUND", summary.getShippingOptionCode());
		assertTrue(summary.isFreeShipping());
		assertTrue(summary.isTaxOnShipping());
		assertSame(quote.getDelivery(), summary.getDeliveryAddress());
		verify(repository).findById(100L);
		verify(repository, never()).getOne(anyLong());
	}

	@Test
	public void doesNotSetTaxWhenShippingIsNotTaxable() throws Exception {
		ShippingSummary summary = service.getShippingSummary(100L, cart, null, store);

		assertFalse(summary.isTaxOnShipping());
	}
}
