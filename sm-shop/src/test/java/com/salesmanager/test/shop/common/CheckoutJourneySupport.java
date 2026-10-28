package com.salesmanager.test.shop.common;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

import java.math.BigDecimal;
import java.util.Collections;
import java.util.UUID;

import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;

import com.salesmanager.core.business.constants.Constants;
import com.salesmanager.shop.model.catalog.category.Category;
import com.salesmanager.shop.model.catalog.category.PersistableCategory;
import com.salesmanager.shop.model.catalog.product.product.PersistableProduct;
import com.salesmanager.shop.model.catalog.product.product.ProductSpecification;
import com.salesmanager.shop.model.customer.PersistableCustomer;
import com.salesmanager.shop.model.customer.address.Address;
import com.salesmanager.shop.model.entity.Entity;
import com.salesmanager.shop.model.order.transaction.PersistablePayment;
import com.salesmanager.shop.model.order.v1.PersistableAnonymousOrder;
import com.salesmanager.shop.model.order.v1.PersistableOrder;
import com.salesmanager.shop.model.shoppingcart.PersistableShoppingCartItem;
import com.salesmanager.shop.model.shoppingcart.ReadableShoppingCart;
import com.salesmanager.shop.model.system.IntegrationModuleConfiguration;

/**
 * REST-only helpers for checkout journeys (catalog, cart, payment configuration, order payloads).
 */
public class CheckoutJourneySupport extends ServicesTestSupport {

	protected static String unique(String prefix) {
		return prefix + UUID.randomUUID().toString().replace("-", "").substring(0, 10);
	}

	protected String createProduct(String code, boolean shippable) {
		PersistableCategory category = category(code);
		ResponseEntity<PersistableCategory> created = testRestTemplate.postForEntity(
				"/api/v1/private/category?store=" + Constants.DEFAULT_STORE,
				new HttpEntity<>(category, getHeader()), PersistableCategory.class);
		assertEquals(HttpStatus.CREATED, created.getStatusCode());

		PersistableProduct product = product(code);
		Category cat = new Category();
		cat.setId(created.getBody().getId());
		cat.setCode(code);
		product.setCategories(Collections.singletonList(cat));
		ProductSpecification specifications = new ProductSpecification();
		specifications.setManufacturer(
				com.salesmanager.core.model.catalog.product.manufacturer.Manufacturer.DEFAULT_MANUFACTURER);
		product.setProductSpecifications(specifications);
		product.setAvailable(true);
		product.setPrice(BigDecimal.TEN);
		product.setSku(code);
		product.setQuantity(100);
		product.setProductShipeable(shippable);
		ResponseEntity<Entity> response = testRestTemplate.postForEntity(
				"/api/v1/private/product?store=" + Constants.DEFAULT_STORE, new HttpEntity<>(product, getHeader()),
				Entity.class);
		assertEquals(HttpStatus.CREATED, response.getStatusCode());
		return code;
	}

	protected ReadableShoppingCart createCart(String sku, int quantity) {
		PersistableShoppingCartItem item = new PersistableShoppingCartItem();
		item.setProduct(sku);
		item.setQuantity(quantity);
		ResponseEntity<ReadableShoppingCart> response = testRestTemplate.postForEntity("/api/v1/cart/",
				new HttpEntity<>(item, jsonHeaders()), ReadableShoppingCart.class);
		assertEquals(HttpStatus.CREATED, response.getStatusCode());
		assertNotNull(response.getBody().getCode());
		return response.getBody();
	}

	protected void enableMoneyOrder() {
		IntegrationModuleConfiguration configuration = new IntegrationModuleConfiguration();
		configuration.setCode("moneyorder");
		configuration.setActive(true);
		configuration.setDefaultSelected(true);
		configuration.getIntegrationKeys().put("address", "1 Test Street, Montreal");
		ResponseEntity<Void> response = testRestTemplate.postForEntity("/api/v1/private/modules/payment",
				new HttpEntity<>(configuration, getHeader()), Void.class);
		assertEquals(HttpStatus.OK, response.getStatusCode());
	}

	protected static HttpHeaders jsonHeaders() {
		HttpHeaders headers = new HttpHeaders();
		headers.setContentType(MediaType.APPLICATION_JSON);
		return headers;
	}

	protected static HttpHeaders bearer(String token) {
		HttpHeaders headers = jsonHeaders();
		headers.add("Authorization", "Bearer " + token);
		return headers;
	}

	protected static PersistablePayment moneyOrder(BigDecimal amount) {
		PersistablePayment payment = new PersistablePayment();
		payment.setPaymentModule("moneyorder");
		payment.setPaymentType("MONEYORDER");
		payment.setTransactionType("CAPTURE");
		payment.setAmount(amount.toPlainString());
		return payment;
	}

	protected static Address quebecAddress() {
		Address address = new Address();
		address.setFirstName("Test");
		address.setLastName("Buyer");
		address.setAddress("1 Test Street");
		address.setCity("Montreal");
		address.setPostalCode("H2H2H2");
		address.setCountry("CA");
		address.setZone("QC");
		address.setPhone("5145551234");
		return address;
	}

	protected static Address newYorkAddress() {
		Address address = quebecAddress();
		address.setCity("New York");
		address.setPostalCode("10001");
		address.setCountry("US");
		address.setZone("NY");
		return address;
	}

	protected static PersistableOrder order(BigDecimal amount, Long shippingQuote) {
		PersistableOrder order = new PersistableOrder();
		order.setCurrency("CAD");
		order.setPayment(moneyOrder(amount));
		order.setShippingQuote(shippingQuote);
		return order;
	}

	protected static PersistableAnonymousOrder guestOrder(BigDecimal amount, Long shippingQuote) {
		return guestOrder(amount, shippingQuote, quebecAddress());
	}

	protected static PersistableAnonymousOrder guestOrder(BigDecimal amount, Long shippingQuote, Address address) {
		PersistableAnonymousOrder order = new PersistableAnonymousOrder();
		order.setCurrency("CAD");
		order.setPayment(moneyOrder(amount));
		order.setShippingQuote(shippingQuote);
		PersistableCustomer customer = new PersistableCustomer();
		customer.setEmailAddress(unique("guest") + "@test.com");
		customer.setFirstName("Test");
		customer.setLastName("Buyer");
		customer.setLanguage("en");
		customer.setBilling(address);
		customer.setDelivery(address);
		order.setCustomer(customer);
		return order;
	}

	protected ResponseEntity<String> guestCheckout(String cartCode, PersistableAnonymousOrder order) {
		return testRestTemplate.postForEntity("/api/v1/cart/" + cartCode + "/checkout",
				new HttpEntity<>(order, jsonHeaders()), String.class);
	}
}
