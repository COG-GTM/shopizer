package com.salesmanager.test.shop.integration.order;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.math.BigDecimal;
import java.util.HashMap;
import java.util.Map;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;

import com.salesmanager.core.business.services.payments.PaymentService;
import com.salesmanager.core.business.services.merchant.MerchantStoreService;
import com.salesmanager.core.business.services.shipping.ShippingService;
import com.salesmanager.core.business.services.system.MerchantConfigurationService;
import com.salesmanager.core.business.constants.ShippingConstants;
import com.salesmanager.core.model.merchant.MerchantStore;
import com.salesmanager.core.model.shipping.ShippingConfiguration;
import com.salesmanager.core.model.system.IntegrationConfiguration;
import com.salesmanager.core.model.system.MerchantConfiguration;

import com.salesmanager.shop.model.catalog.product.product.PersistableProduct;
import com.salesmanager.shop.model.customer.PersistableCustomer;
import com.salesmanager.shop.model.customer.address.Address;
import com.salesmanager.shop.model.customer.address.AddressLocation;
import com.salesmanager.shop.model.order.ReadableOrderTotalSummary;
import com.salesmanager.shop.model.order.shipping.ReadableShippingSummary;
import com.salesmanager.shop.model.order.transaction.PersistablePayment;
import com.salesmanager.shop.model.order.v1.PersistableAnonymousOrder;
import com.salesmanager.shop.model.order.v1.ReadableOrderConfirmation;
import com.salesmanager.shop.model.shoppingcart.PersistableShoppingCartItem;
import com.salesmanager.shop.model.shoppingcart.ReadableShoppingCart;
import com.salesmanager.shop.model.system.IntegrationModuleConfiguration;
import com.salesmanager.shop.model.store.ReadableMerchantStore;
import com.salesmanager.shop.model.catalog.product.ReadableProduct;
import com.salesmanager.test.shop.common.ServicesTestSupport;

class ShippingQuoteCheckoutIntegrationTest extends ServicesTestSupport {

	@Autowired
	private MerchantStoreService merchantStoreService;
	@Autowired
	private PaymentService paymentService;
	@Autowired
	private ShippingService shippingService;
	@Autowired
	private MerchantConfigurationService merchantConfigurationService;

	private ShippingConfiguration originalShippingConfiguration;
	private IntegrationConfiguration originalWeightBasedConfiguration;
	private IntegrationConfiguration originalPickupConfiguration;
	private IntegrationConfiguration originalMoneyOrderConfiguration;

	@BeforeEach
	void clearShippingModules() throws Exception {
		MerchantStore store = merchantStoreService.getByCode("DEFAULT");
		originalShippingConfiguration = shippingService.getShippingConfiguration(store);
		originalWeightBasedConfiguration = shippingService.getShippingConfiguration("weightBased", store);
		originalPickupConfiguration = shippingService.getShippingConfiguration("storePickUp", store);
		originalMoneyOrderConfiguration = paymentService.getPaymentConfiguration("moneyorder", store);
		resetShippingModules();
	}

	@AfterEach
	void restoreShippingModules() throws Exception {
		resetShippingModules();
		MerchantStore store = merchantStoreService.getByCode("DEFAULT");
		if (originalWeightBasedConfiguration != null) {
			shippingService.saveShippingQuoteModuleConfiguration(originalWeightBasedConfiguration, store);
		}
		if (originalPickupConfiguration != null) {
			shippingService.saveShippingQuoteModuleConfiguration(originalPickupConfiguration, store);
		}
		if (originalShippingConfiguration == null) {
			MerchantConfiguration configuration = merchantConfigurationService.getMerchantConfiguration(
					ShippingConstants.SHIPPING_CONFIGURATION, store);
			if (configuration != null) {
				merchantConfigurationService.delete(configuration);
			}
		} else {
			shippingService.saveShippingConfiguration(originalShippingConfiguration, store);
		}
		if (originalMoneyOrderConfiguration == null) {
			paymentService.removePaymentModuleConfiguration("moneyorder", store);
		} else {
			paymentService.savePaymentModuleConfiguration(originalMoneyOrderConfiguration, store);
		}
	}

	private void resetShippingModules() throws Exception {
		MerchantStore store = merchantStoreService.getByCode("DEFAULT");
		shippingService.removeShippingQuoteModuleConfiguration("weightBased", store);
		shippingService.removeShippingQuoteModuleConfiguration("storePickUp", store);
	}

	@Override
	protected PersistableProduct product(String code) {
		PersistableProduct product = super.product(code);
		product.setProductShipeable(true);
		product.setProductVirtual(false);
		return product;
	}

	@Test
	void anonymousCheckoutWithoutQuoteWorksWhenShippingIsNotConfigured() {
		enableMoneyOrder();
		ReadableShoppingCart cart = newCart("no-shipping");

		ResponseEntity<ReadableOrderConfirmation> response = checkout(cart, orderFor(cart, null, "H3B 1A1"));

		assertTrue(response.getStatusCode().is2xxSuccessful(), response.getStatusCode() + " " + response.getBody());
		assertNotNull(response.getBody());
	}

	@Test
	void checkoutRejectsNonexistentQuote() {
		enableMoneyOrder();
		ReadableShoppingCart cart = newCart("unknown-quote");

		ResponseEntity<String> response = checkoutRaw(cart, orderFor(cart, 999999L, "H3B 1A1"), String.class);

		assertFalse(response.getStatusCode().is2xxSuccessful(), response.getStatusCode() + " " + response.getBody());
	}

	@Test
	void configuredShippingRequiresAQuote() throws Exception {
		enableMoneyOrder();
		enablePickupShipping();
		ReadableShoppingCart cart = newCart("required-quote");

		ResponseEntity<String> response = checkoutRaw(cart, orderFor(cart, null, "H3B 1A1"), String.class);

		assertFalse(response.getStatusCode().is2xxSuccessful(), response.getStatusCode() + " " + response.getBody());
		assertTrue(response.getBody() == null || response.getBody().contains("shipping quote")
				|| response.getBody().contains("shipping"));
	}

	@Test
	void checkoutRejectsQuoteForAnotherCart() throws Exception {
		enableMoneyOrder();
		enablePickupShipping();
		ReadableShoppingCart cartA = newCart("cart-a");
		ReadableShoppingCart cartB = newCart("cart-b");
		Long quoteA = requestShippingQuote(cartA);
		String amount = rawAmount(total(cartA, quoteA).getTotal());

		ResponseEntity<String> response = checkoutRaw(
				cartB, orderForWithAmount(cartB, quoteA, "H3B 1A1", amount), String.class);
		assertFalse(response.getStatusCode().is2xxSuccessful(),
				response.getStatusCode() + " " + response.getBody());
	}

	@Test
	void checkoutRejectsChangedDeliveryAddress() throws Exception {
		enableMoneyOrder();
		enablePickupShipping();
		ReadableShoppingCart cart = newCart("changed-address");
		Long quoteId = requestShippingQuote(cart);

		ResponseEntity<String> response = checkoutRaw(
				cart, orderFor(cart, quoteId, "V6B 1A1"), String.class);
		assertFalse(response.getStatusCode().is2xxSuccessful(),
				response.getStatusCode() + " " + response.getBody());
	}

	@Test
	void orderTotalsRejectQuoteForAnotherCart() throws Exception {
		enableMoneyOrder();
		enablePickupShipping();
		ReadableShoppingCart cartA = newCart("foreign-total-a");
		ReadableShoppingCart cartB = newCart("foreign-total-b");
		Long quoteA = requestShippingQuote(cartA);

		ResponseEntity<String> response = totalResponse(cartB, quoteA);
		assertFalse(response.getStatusCode().is2xxSuccessful(),
				response.getStatusCode() + " " + response.getBody());
	}

	@Test
	void ownQuoteAddsShippingToOrderTotals() throws Exception {
		enableMoneyOrder();
		enablePickupShipping();
		ReadableShoppingCart cart = newCart("own-total");
		Long quoteId = requestShippingQuote(cart);

		ReadableOrderTotalSummary withoutShipping = total(cart, null);
		ReadableOrderTotalSummary withShipping = total(cart, quoteId);
		assertTrue(new BigDecimal(rawAmount(withShipping.getTotal()))
				.compareTo(new BigDecimal(rawAmount(withoutShipping.getTotal()))) > 0);
	}

	@Test
	void ownQuoteCheckoutSucceeds() throws Exception {
		enableMoneyOrder();
		enablePickupShipping();
		ReadableShoppingCart cart = newCart("own-checkout");
		Long quoteId = requestShippingQuote(cart);

		ResponseEntity<ReadableOrderConfirmation> response =
				checkout(cart, orderFor(cart, quoteId, "H3B 1A1"));
		assertTrue(response.getStatusCode().is2xxSuccessful(),
				response.getStatusCode() + " " + response.getBody());
	}

	private ReadableShoppingCart newCart(String suffix) {
		String sku = "quote-" + suffix + "-" + System.nanoTime();
		ReadableProduct product = sampleProduct(sku);
		PersistableShoppingCartItem item = new PersistableShoppingCartItem();
		item.setProduct(product.getSku());
		item.setQuantity(1);
		HttpEntity<PersistableShoppingCartItem> request = new HttpEntity<>(item, getHeader());
		ResponseEntity<ReadableShoppingCart> response =
				testRestTemplate.postForEntity("/api/v1/cart", request, ReadableShoppingCart.class);
		assertTrue(response.getStatusCode() == HttpStatus.CREATED,
				response.getStatusCode() + " " + response.getBody());
		return response.getBody();
	}

	private PersistableAnonymousOrder orderFor(ReadableShoppingCart cart, Long quoteId, String postalCode) {
		String amount = "1.00";
		if (!Long.valueOf(999999L).equals(quoteId)) {
			ReadableOrderTotalSummary total = testRestTemplate.getForObject(
					totalPath(cart, quoteId),
					ReadableOrderTotalSummary.class);
			assertNotNull(total);
			assertNotNull(total.getTotal());
			amount = rawAmount(total.getTotal());
		}
		return orderForWithAmount(cart, quoteId, postalCode, amount);
	}

	private PersistableAnonymousOrder orderForWithAmount(
			ReadableShoppingCart cart, Long quoteId, String postalCode, String amount) {
		ReadableMerchantStore store = fetchStore();

		PersistableCustomer customer = new PersistableCustomer();
		customer.setFirstName("Shipping");
		customer.setLastName("Test");
		customer.setEmailAddress("shipping-" + System.nanoTime() + "@example.com");
		customer.setGender("M");
		customer.setLanguage("en");
		customer.setBilling(address(postalCode));
		customer.setDelivery(address(postalCode));

		PersistablePayment payment = new PersistablePayment();
		payment.setPaymentModule("moneyorder");
		payment.setPaymentType("MONEYORDER");
		payment.setTransactionType("AUTHORIZE");
		payment.setAmount(amount);

		PersistableAnonymousOrder order = new PersistableAnonymousOrder();
		order.setCustomer(customer);
		order.setPayment(payment);
		order.setCurrency(store.getCurrency());
		order.setCustomerAgreement(true);
		order.setShippingQuote(quoteId);
		return order;
	}

	private Long requestShippingQuote(ReadableShoppingCart cart) {
		AddressLocation address = new AddressLocation();
		address.setCountryCode("CA");
		address.setPostalCode("H3B 1A1");

		ResponseEntity<ReadableShippingSummary> response = testRestTemplate.postForEntity(
				"/api/v1/cart/" + cart.getCode() + "/shipping?store=DEFAULT",
				address, ReadableShippingSummary.class);
		assertTrue(response.getStatusCode().is2xxSuccessful(),
				response.getStatusCode() + " " + response.getBody());
		assertNotNull(response.getBody());
		assertNotNull(response.getBody().getShippingOptions());
		assertFalse(response.getBody().getShippingOptions().isEmpty());
		assertTrue(response.getBody().getShippingOptions().get(0).getOptionPrice()
				.compareTo(BigDecimal.ZERO) > 0);
		assertNotNull(response.getBody().getShippingOptions().get(0).getShippingQuoteOptionId());
		return response.getBody().getShippingOptions().get(0).getShippingQuoteOptionId();
	}

	private void enablePickupShipping() throws Exception {
		MerchantStore store = merchantStoreService.getByCode("DEFAULT");
		shippingService.saveShippingConfiguration(new ShippingConfiguration(), store);
		IntegrationConfiguration primaryModule = new IntegrationConfiguration();
		primaryModule.setModuleCode("weightBased");
		primaryModule.setActive(true);
		primaryModule.setDefaultSelected(true);
		shippingService.saveShippingQuoteModuleConfiguration(primaryModule, store);

		IntegrationConfiguration pickupModule = new IntegrationConfiguration();
		pickupModule.setModuleCode("storePickUp");
		pickupModule.setActive(true);
		pickupModule.setDefaultSelected(true);
		Map<String, String> keys = new HashMap<>();
		keys.put("price", "15.00");
		keys.put("note", "Pick up");
		pickupModule.setIntegrationKeys(keys);
		shippingService.saveShippingQuoteModuleConfiguration(pickupModule, store);
	}

	private ReadableOrderTotalSummary total(ReadableShoppingCart cart, Long quoteId) {
		return testRestTemplate.getForObject(totalPath(cart, quoteId), ReadableOrderTotalSummary.class);
	}

	private ResponseEntity<String> totalResponse(ReadableShoppingCart cart, Long quoteId) {
		return testRestTemplate.getForEntity(totalPath(cart, quoteId), String.class);
	}

	private String totalPath(ReadableShoppingCart cart, Long quoteId) {
		return "/api/v1/cart/" + cart.getCode() + "/total"
				+ (quoteId == null ? "" : "?quote=" + quoteId);
	}

	private String rawAmount(String amount) {
		return amount.replaceAll("[^0-9.\\-]", "");
	}

	private Address address(String postalCode) {
		Address address = new Address();
		address.setFirstName("Shipping");
		address.setLastName("Test");
		address.setAddress("123 Main Street");
		address.setCity("Montreal");
		address.setCountry("CA");
		address.setPostalCode(postalCode);
		return address;
	}

	private ResponseEntity<ReadableOrderConfirmation> checkout(
			ReadableShoppingCart cart, PersistableAnonymousOrder order) {
		return checkoutRaw(cart, order, ReadableOrderConfirmation.class);
	}

	private <T> ResponseEntity<T> checkoutRaw(ReadableShoppingCart cart, PersistableAnonymousOrder order, Class<T> type) {
		HttpHeaders headers = new HttpHeaders();
		headers.setContentType(MediaType.APPLICATION_JSON);
		return testRestTemplate.postForEntity("/api/v1/cart/" + cart.getCode() + "/checkout",
				new HttpEntity<>(order, headers), type);
	}

	private void enableMoneyOrder() {
		IntegrationModuleConfiguration configuration = new IntegrationModuleConfiguration();
		configuration.setCode("moneyorder");
		configuration.setActive(true);
		configuration.setDefaultSelected(true);
		Map<String, String> keys = new HashMap<>();
		keys.put("address", "Money order payment address");
		configuration.setIntegrationKeys(keys);
		configure("/api/v1/private/modules/payment", configuration);
	}

	private void configure(String path, IntegrationModuleConfiguration configuration) {
		HttpHeaders headers = getHeader();
		headers.setContentType(MediaType.APPLICATION_JSON);
		ResponseEntity<Void> response =
				testRestTemplate.exchange(path + "?store=DEFAULT", HttpMethod.POST,
						new HttpEntity<>(configuration, headers), Void.class);
		assertTrue(response.getStatusCode().is2xxSuccessful(),
				response.getStatusCode() + " " + response.getBody());
	}
}
