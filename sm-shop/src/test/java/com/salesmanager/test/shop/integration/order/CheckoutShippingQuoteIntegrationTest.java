package com.salesmanager.test.shop.integration.order;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.math.BigDecimal;
import java.util.Date;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import com.salesmanager.core.business.exception.ServiceException;
import com.salesmanager.core.business.services.merchant.MerchantStoreService;
import com.salesmanager.core.business.services.shipping.ShippingQuoteService;
import com.salesmanager.core.business.services.shipping.ShippingService;
import com.salesmanager.core.business.services.shoppingcart.ShoppingCartService;
import com.salesmanager.core.business.services.reference.country.CountryService;
import com.salesmanager.core.model.common.Delivery;
import com.salesmanager.core.model.merchant.MerchantStore;
import com.salesmanager.core.model.shipping.Quote;
import com.salesmanager.core.model.shipping.ShippingConfiguration;
import com.salesmanager.core.model.shipping.ShippingSummary;
import com.salesmanager.core.model.shoppingcart.ShoppingCart;
import com.salesmanager.shop.model.order.ReadableOrderTotalSummary;
import com.salesmanager.shop.model.order.v1.ReadableOrderConfirmation;
import com.salesmanager.shop.model.shoppingcart.ReadableShoppingCart;
import com.salesmanager.test.shop.common.CheckoutJourneySupport;

import com.fasterxml.jackson.databind.ObjectMapper;

/**
 * PR #185: shipping quotes are bound to the cart (and store) they were issued for, and shippable carts
 * cannot be checked out without a valid shipping quote. Runs against the real Spring context and H2.
 */
class CheckoutShippingQuoteIntegrationTest extends CheckoutJourneySupport {

	@Autowired
	private ShippingQuoteService shippingQuoteService;
	@Autowired
	private ShoppingCartService shoppingCartService;
	@Autowired
	private MerchantStoreService merchantStoreService;
	@Autowired
	private ShippingService shippingService;
	@Autowired
	private CountryService countryService;

	private MerchantStore store;

	@BeforeEach
	void setUp() throws Exception {
		store = merchantStoreService.getByCode("DEFAULT");
		if (shippingService.getShippingConfiguration(store) == null) {
			shippingService.saveShippingConfiguration(new ShippingConfiguration(), store);
		}
		enableMoneyOrder();
	}

	private ShoppingCart cartModel(ReadableShoppingCart cart) throws ServiceException {
		ShoppingCart model = shoppingCartService.getByCode(cart.getCode(), store);
		assertNotNull(model);
		return model;
	}

	private Long storeQuote(ShoppingCart cart, String price) throws ServiceException {
		Quote quote = new Quote();
		quote.setCartId(cart.getId());
		quote.setModule("weightBased");
		quote.setOptionCode("STD");
		quote.setOptionName("Standard");
		quote.setPrice(new BigDecimal(price));
		quote.setQuoteDate(new Date());
		Delivery delivery = new Delivery();
		delivery.setCountry(countryService.getByCode("CA"));
		delivery.setPostalCode("H2H2H2");
		delivery.setCity("Montreal");
		quote.setDelivery(delivery);
		shippingQuoteService.save(quote);
		assertNotNull(quote.getId());
		return quote.getId();
	}

	@Test
	void scopedQuoteLookupOnlyResolvesForTheQuotedCart() throws Exception {
		ShoppingCart quoted = cartModel(createCart(createProduct(unique("itq"), true), 1));
		ShoppingCart other = cartModel(createCart(createProduct(unique("itq"), true), 1));
		Long quoteId = storeQuote(quoted, "12.50");

		ShippingSummary summary = shippingQuoteService.getShippingSummary(quoteId, quoted, store);
		assertEquals(0, new BigDecimal("12.50").compareTo(summary.getShipping()));
		assertEquals("weightBased", summary.getShippingModule());

		ServiceException foreign = assertThrows(ServiceException.class,
				() -> shippingQuoteService.getShippingSummary(quoteId, other, store));
		assertEquals("shipping.quote.invalid", foreign.getMessageCode());

		ServiceException unknown = assertThrows(ServiceException.class,
				() -> shippingQuoteService.getQuote(Long.MAX_VALUE, quoted, store));
		assertEquals("shipping.quote.invalid", unknown.getMessageCode());
	}

	@Test
	void orderTotalAppliesOwnQuoteAndRefusesQuoteOfAnotherCart() throws Exception {
		ReadableShoppingCart quoted = createCart(createProduct(unique("itt"), true), 1);
		ReadableShoppingCart other = createCart(createProduct(unique("itt"), true), 1);
		Long quoteId = storeQuote(cartModel(quoted), "7.00");

		ResponseEntity<ReadableOrderTotalSummary> own = testRestTemplate.exchange(
				"/api/v1/cart/" + quoted.getCode() + "/total?quote=" + quoteId, HttpMethod.GET,
				new HttpEntity<>(jsonHeaders()), ReadableOrderTotalSummary.class);
		assertEquals(HttpStatus.OK, own.getStatusCode());
		assertTrue(own.getBody().getTotals().stream()
				.anyMatch(t -> t.getValue() != null && new BigDecimal("7.00").compareTo(t.getValue()) == 0),
				"shipping total of the quote is applied");

		ResponseEntity<String> foreign = testRestTemplate.exchange(
				"/api/v1/cart/" + other.getCode() + "/total?quote=" + quoteId, HttpMethod.GET,
				new HttpEntity<>(jsonHeaders()), String.class);
		assertFalse(foreign.getStatusCode().is2xxSuccessful(), "foreign quote must not be priced: " + foreign);
	}

	@Test
	void guestCheckoutWithQuoteOfAnotherCartIsRejected() throws Exception {
		ReadableShoppingCart quoted = createCart(createProduct(unique("itg"), true), 1);
		ReadableShoppingCart other = createCart(createProduct(unique("itg"), true), 1);
		Long quoteId = storeQuote(cartModel(quoted), "1.00");

		ResponseEntity<String> response = guestCheckout(other.getCode(), guestOrder(other.getTotal().add(BigDecimal.ONE), quoteId));
		assertEquals(HttpStatus.BAD_REQUEST, response.getStatusCode(), response.getBody());
		assertTrue(cartModel(other).getOrderId() == null, "cart must not be converted to an order");
	}

	@Test
	void guestCheckoutOfShippableCartWithoutQuoteToUnservedDestinationIsRejected() throws Exception {
		ReadableShoppingCart cart = createCart(createProduct(unique("itn"), true), 1);
		ResponseEntity<String> response = guestCheckout(cart.getCode(),
				guestOrder(cart.getTotal(), null, newYorkAddress()));
		assertEquals(HttpStatus.BAD_REQUEST, response.getStatusCode(), response.getBody());
		assertTrue(cartModel(cart).getOrderId() == null, "cart must not be converted to an order");
	}

	@Test
	void guestCheckoutWithOwnQuoteWhoseOptionIsNoLongerOfferedIsRejected() throws Exception {
		ReadableShoppingCart cart = createCart(createProduct(unique("ite"), true), 1);
		Long quoteId = storeQuote(cartModel(cart), "3.00");
		ResponseEntity<String> response = guestCheckout(cart.getCode(),
				guestOrder(cart.getTotal().add(new BigDecimal("3.00")), quoteId));
		assertEquals(HttpStatus.BAD_REQUEST, response.getStatusCode(), response.getBody());
	}

	@Test
	void guestCheckoutOfVirtualCartWithoutQuoteSucceeds() throws Exception {
		ReadableShoppingCart cart = createCart(createProduct(unique("itv"), false), 2);
		ResponseEntity<String> response = guestCheckout(cart.getCode(), guestOrder(cart.getTotal(), null));
		assertEquals(HttpStatus.OK, response.getStatusCode(), response.getBody());
		ReadableOrderConfirmation confirmation = new ObjectMapper()
				.readValue(response.getBody(), ReadableOrderConfirmation.class);
		assertTrue(confirmation.getId() > 0);
	}

	@Test
	void guestCheckoutOfShippableCartWithoutQuoteSucceedsWhenNoShippingModuleIsConfigured() throws Exception {
		ReadableShoppingCart cart = createCart(createProduct(unique("itm"), true), 1);
		ResponseEntity<String> response = guestCheckout(cart.getCode(), guestOrder(cart.getTotal(), null));
		assertEquals(HttpStatus.OK, response.getStatusCode(), response.getBody());
		assertNotNull(cartModel(cart).getOrderId());
	}
}
