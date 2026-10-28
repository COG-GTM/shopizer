package com.salesmanager.test.shop.integration.cart;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.http.HttpMethod.GET;
import static org.springframework.http.HttpMethod.POST;
import static org.springframework.http.HttpMethod.PUT;
import static org.springframework.http.HttpStatus.BAD_REQUEST;
import static org.springframework.http.HttpStatus.CREATED;
import static org.springframework.http.HttpStatus.OK;

import java.math.BigDecimal;
import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.SpringBootTest.WebEnvironment;
import org.springframework.http.HttpEntity;
import org.springframework.http.ResponseEntity;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.test.context.junit.jupiter.SpringExtension;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.salesmanager.core.business.constants.Constants;
import com.salesmanager.core.business.services.catalog.product.ProductService;
import com.salesmanager.core.business.services.merchant.MerchantStoreService;
import com.salesmanager.core.business.services.shoppingcart.ShoppingCartService;
import com.salesmanager.core.model.catalog.product.Product;
import com.salesmanager.core.model.merchant.MerchantStore;
import com.salesmanager.core.model.shoppingcart.ShoppingCart;
import com.salesmanager.core.model.shoppingcart.ShoppingCartItem;
import com.salesmanager.shop.application.ShopApplication;
import com.salesmanager.shop.model.catalog.product.ReadableProduct;
import com.salesmanager.shop.model.customer.PersistableCustomer;
import com.salesmanager.shop.model.customer.address.Address;
import com.salesmanager.shop.model.order.transaction.PersistablePayment;
import com.salesmanager.shop.model.order.v0.ReadableOrderList;
import com.salesmanager.shop.model.order.v1.PersistableAnonymousOrder;
import com.salesmanager.shop.model.shoppingcart.PersistableShoppingCartItem;
import com.salesmanager.shop.model.shoppingcart.ReadableShoppingCart;
import com.salesmanager.test.shop.common.ServicesTestSupport;

@SpringBootTest(classes = ShopApplication.class, webEnvironment = WebEnvironment.RANDOM_PORT)
@ExtendWith(SpringExtension.class)
public class CartQuantityValidationIntegrationTest extends ServicesTestSupport {

	private final ObjectMapper objectMapper = new ObjectMapper();

	@Autowired
	private ShoppingCartService shoppingCartService;

	@Autowired
	private MerchantStoreService merchantStoreService;

	@Autowired
	private ProductService productService;

	@Autowired
	private PlatformTransactionManager transactionManager;

	@Test
	public void postRejectsNegativeAndZeroQuantitiesAsJsonErrors() throws Exception {
		ReadableProduct product = sampleProduct(uniqueSku());

		ResponseEntity<String> negative = testRestTemplate.postForEntity("/api/v1/cart",
				new HttpEntity<>(cartItem(product.getSku(), -1), getHeader()), String.class);
		ResponseEntity<String> zero = testRestTemplate.postForEntity("/api/v1/cart",
				new HttpEntity<>(cartItem(product.getSku(), 0), getHeader()), String.class);

		assertEquals(BAD_REQUEST, negative.getStatusCode());
		assertEquals("500", errorBody(negative.getBody()).path("errorCode").asText());
		assertTrue(errorBody(negative.getBody()).path("message").asText().contains("Invalid quantity -1"));
		assertEquals(BAD_REQUEST, zero.getStatusCode());
		assertEquals("500", errorBody(zero.getBody()).path("errorCode").asText());
		assertTrue(errorBody(zero.getBody()).path("message").asText().contains("Invalid quantity 0"));
	}

	@Test
	public void putZeroForProductNotInCartDoesNotAddALine() throws Exception {
		ReadableProduct existing = sampleProduct(uniqueSku());
		ReadableProduct absent = sampleProduct(uniqueSku());
		ReadableShoppingCart created = createCart(existing.getSku(), 1);

		ResponseEntity<String> negative = testRestTemplate.exchange(
				"/api/v1/cart/" + created.getCode(), PUT,
				new HttpEntity<>(cartItem(absent.getSku(), -1), getHeader()), String.class);
		ResponseEntity<ReadableShoppingCart> response = testRestTemplate.exchange(
				"/api/v1/cart/" + created.getCode(), PUT,
				new HttpEntity<>(cartItem(absent.getSku(), 0), getHeader()), ReadableShoppingCart.class);
		ReadableShoppingCart cart = getCart(created.getCode());

		assertEquals(BAD_REQUEST, negative.getStatusCode());
		assertEquals("500", errorBody(negative.getBody()).path("errorCode").asText());
		assertTrue(errorBody(negative.getBody()).path("message").asText().contains("Invalid quantity -1"));
		assertEquals(CREATED, response.getStatusCode());
		assertEquals(1, cart.getProducts().size());
		assertEquals(existing.getSku(), cart.getProducts().get(0).getSku());
		assertEquals(1, cart.getQuantity());
	}

	@Test
	public void putZeroForExistingLineRemovesOnlyThatLine() throws Exception {
		ReadableProduct removed = sampleProduct(uniqueSku());
		ReadableProduct remaining = sampleProduct(uniqueSku());
		ReadableShoppingCart created = createCart(removed.getSku(), 1);
		testRestTemplate.exchange("/api/v1/cart/" + created.getCode(), PUT,
				new HttpEntity<>(cartItem(remaining.getSku(), 1), getHeader()), ReadableShoppingCart.class);

		ResponseEntity<ReadableShoppingCart> response = testRestTemplate.exchange(
				"/api/v1/cart/" + created.getCode(), PUT,
				new HttpEntity<>(cartItem(removed.getSku(), 0), getHeader()), ReadableShoppingCart.class);
		ReadableShoppingCart cart = getCart(created.getCode());

		assertEquals(CREATED, response.getStatusCode());
		assertEquals(1, cart.getProducts().size());
		assertEquals(remaining.getSku(), cart.getProducts().get(0).getSku());
		assertEquals(1, cart.getQuantity());
	}

	@Test
	public void updatingQuantityRecalculatesNonNegativeCartTotals() throws Exception {
		ReadableProduct product = sampleProduct(uniqueSku());
		ReadableShoppingCart created = createCart(product.getSku(), 1);

		ResponseEntity<ReadableShoppingCart> response = testRestTemplate.exchange(
				"/api/v1/cart/" + created.getCode(), PUT,
				new HttpEntity<>(cartItem(product.getSku(), 3), getHeader()), ReadableShoppingCart.class);
		ReadableShoppingCart cart = response.getBody();
		BigDecimal unitPrice = new BigDecimal(product.getFinalPrice().replaceAll("[^0-9.-]", ""));
		BigDecimal expected = unitPrice.multiply(BigDecimal.valueOf(3));

		assertEquals(CREATED, response.getStatusCode());
		assertNotNull(cart);
		assertEquals(3, cart.getQuantity());
		assertEquals(3, cart.getProducts().get(0).getQuantity());
		assertEquals(0, expected.compareTo(cart.getSubtotal()));
		assertTrue(cart.getSubtotal().signum() >= 0);
		assertTrue(cart.getTotal().signum() >= 0);
	}

	@Test
	public void multiRejectsInvalidLineWithoutChangingTheCart() throws Exception {
		ReadableProduct existing = sampleProduct(uniqueSku());
		ReadableProduct added = sampleProduct(uniqueSku());
		ReadableShoppingCart created = createCart(existing.getSku(), 1);
		PersistableShoppingCartItem[] updates = {
				cartItem(added.getSku(), 2),
				cartItem(existing.getSku(), -1)
		};

		ResponseEntity<String> response = testRestTemplate.exchange(
				"/api/v1/cart/" + created.getCode() + "/multi", POST,
				new HttpEntity<>(updates, getHeader()), String.class);
		ReadableShoppingCart cart = getCart(created.getCode());

		assertEquals(BAD_REQUEST, response.getStatusCode());
		assertEquals("500", errorBody(response.getBody()).path("errorCode").asText());
		assertTrue(errorBody(response.getBody()).path("message").asText().contains("Invalid quantity -1"));
		assertEquals(1, cart.getProducts().size());
		assertEquals(existing.getSku(), cart.getProducts().get(0).getSku());
		assertEquals(1, cart.getQuantity());
	}

	@Test
	public void multiZeroForNewProductDoesNotAddALine() throws Exception {
		ReadableProduct existing = sampleProduct(uniqueSku());
		ReadableProduct absent = sampleProduct(uniqueSku());
		ReadableShoppingCart created = createCart(existing.getSku(), 1);

		ResponseEntity<ReadableShoppingCart> response = testRestTemplate.exchange(
				"/api/v1/cart/" + created.getCode() + "/multi", POST,
				new HttpEntity<>(new PersistableShoppingCartItem[] { cartItem(absent.getSku(), 0) },
						getHeader()),
				ReadableShoppingCart.class);
		ReadableShoppingCart cart = getCart(created.getCode());

		assertEquals(CREATED, response.getStatusCode());
		assertEquals(1, cart.getProducts().size());
		assertEquals(existing.getSku(), cart.getProducts().get(0).getSku());
		assertEquals(1, cart.getQuantity());
	}

	@Test
	public void checkoutRejectsTamperedNegativeLineWithoutInventoryOrOrderChanges() throws Exception {
		ReadableProduct product = sampleProduct(uniqueSku());
		ReadableShoppingCart created = createCart(product.getSku(), 1);
		ResponseEntity<ReadableOrderList> beforeOrders = testRestTemplate.exchange(
				"/api/v1/private/orders?count=10000&page=0", GET,
				new HttpEntity<>(getHeader()), ReadableOrderList.class);
		assertEquals(OK, beforeOrders.getStatusCode());
		int inventoryBefore = inventoryAndSetCartQuantity(product.getSku(), created.getCode(), -1);

		PersistableAnonymousOrder order = new PersistableAnonymousOrder();
		order.setCurrency("USD");
		PersistablePayment payment = new PersistablePayment();
		payment.setPaymentType("FREE");
		payment.setTransactionType("OK");
		payment.setPaymentModule("free");
		payment.setAmount("0");
		order.setPayment(payment);
		order.setCustomer(anonymousCustomer());
		ResponseEntity<String> checkout = testRestTemplate.postForEntity(
				"/api/v1/cart/" + created.getCode() + "/checkout",
				new HttpEntity<>(order, getHeader()), String.class);
		ResponseEntity<ReadableOrderList> afterOrders = testRestTemplate.exchange(
				"/api/v1/private/orders?count=10000&page=0", GET,
				new HttpEntity<>(getHeader()), ReadableOrderList.class);

		assertEquals(BAD_REQUEST, checkout.getStatusCode());
		assertTrue(checkout.getBody().contains("Invalid quantity -1"));
		assertEquals(inventoryBefore, inventory(product.getSku()));
		assertEquals(beforeOrders.getBody().getRecordsTotal(), afterOrders.getBody().getRecordsTotal());
	}

	private int inventoryAndSetCartQuantity(String sku, String cartCode, int quantity) {
		Integer inventory = new TransactionTemplate(transactionManager).execute(status -> {
			try {
				MerchantStore store = merchantStoreService.getByCode(Constants.DEFAULT_STORE);
				Product product = productService.getBySku(sku, store, store.getDefaultLanguage());
				int productQuantity = product.getAvailabilities().iterator().next().getProductQuantity();
				ShoppingCart cart = shoppingCartService.getByCode(cartCode, store);
				ShoppingCartItem item = cart.getLineItems().iterator().next();
				item.setQuantity(quantity);
				shoppingCartService.saveOrUpdate(cart);
				return productQuantity;
			} catch (Exception e) {
				throw new IllegalStateException(e);
			}
		});
		return inventory;
	}

	private int inventory(String sku) {
		Integer inventory = new TransactionTemplate(transactionManager).execute(status -> {
			try {
				MerchantStore store = merchantStoreService.getByCode(Constants.DEFAULT_STORE);
				Product product = productService.getBySku(sku, store, store.getDefaultLanguage());
				return product.getAvailabilities().iterator().next().getProductQuantity();
			} catch (Exception e) {
				throw new IllegalStateException(e);
			}
		});
		return inventory;
	}

	private PersistableCustomer anonymousCustomer() {
		PersistableCustomer customer = new PersistableCustomer();
		customer.setEmailAddress("pr198-anonymous-" + UUID.randomUUID().toString().replace("-", "") + "@example.com");
		customer.setFirstName("PR198");
		customer.setLastName("Cart");
		customer.setLanguage("en");
		customer.setStoreCode(Constants.DEFAULT_STORE);
		Address billing = new Address();
		billing.setFirstName("PR198");
		billing.setLastName("Cart");
		billing.setAddress("1 Test Street");
		billing.setCity("Brussels");
		billing.setPostalCode("1000");
		billing.setCountry("BE");
		billing.setBillingAddress(true);
		Address delivery = new Address();
		delivery.setFirstName("PR198");
		delivery.setLastName("Cart");
		delivery.setAddress("1 Test Street");
		delivery.setCity("Brussels");
		delivery.setPostalCode("1000");
		delivery.setCountry("BE");
		customer.setBilling(billing);
		customer.setDelivery(delivery);
		return customer;
	}

	private ReadableShoppingCart createCart(String sku, int quantity) {
		ResponseEntity<ReadableShoppingCart> response = testRestTemplate.postForEntity("/api/v1/cart",
				new HttpEntity<>(cartItem(sku, quantity), getHeader()), ReadableShoppingCart.class);

		assertEquals(CREATED, response.getStatusCode());
		assertNotNull(response.getBody());
		return response.getBody();
	}

	private ReadableShoppingCart getCart(String code) {
		ResponseEntity<ReadableShoppingCart> response = testRestTemplate.getForEntity(
				"/api/v1/cart/" + code, ReadableShoppingCart.class);

		assertEquals(OK, response.getStatusCode());
		assertNotNull(response.getBody());
		return response.getBody();
	}

	private JsonNode errorBody(String body) throws Exception {
		return objectMapper.readTree(body);
	}

	private static PersistableShoppingCartItem cartItem(String sku, int quantity) {
		PersistableShoppingCartItem item = new PersistableShoppingCartItem();
		item.setProduct(sku);
		item.setQuantity(quantity);
		return item;
	}

	private static String uniqueSku() {
		return "pr198-cart-" + UUID.randomUUID().toString().replace("-", "");
	}
}
