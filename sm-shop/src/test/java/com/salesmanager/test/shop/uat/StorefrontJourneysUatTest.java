package com.salesmanager.test.shop.uat;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;
import static org.springframework.http.HttpMethod.GET;
import static org.springframework.http.HttpMethod.POST;
import static org.springframework.http.HttpMethod.PUT;
import static org.springframework.http.HttpStatus.CREATED;
import static org.springframework.http.HttpStatus.FORBIDDEN;
import static org.springframework.http.HttpStatus.NOT_FOUND;
import static org.springframework.http.HttpStatus.OK;
import static org.springframework.http.HttpStatus.UNAUTHORIZED;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.SpringBootTest.WebEnvironment;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.test.context.junit4.SpringRunner;

import com.salesmanager.core.business.constants.Constants;
import com.salesmanager.core.model.customer.CustomerGender;
import com.salesmanager.shop.application.ShopApplication;
import com.salesmanager.shop.model.catalog.product.ReadableProduct;
import com.salesmanager.shop.model.customer.PersistableCustomer;
import com.salesmanager.shop.model.customer.ReadableCustomer;
import com.salesmanager.shop.model.customer.address.Address;
import com.salesmanager.shop.model.order.ReadableOrderTotalSummary;
import com.salesmanager.shop.model.order.transaction.PersistablePayment;
import com.salesmanager.shop.model.order.v1.PersistableOrder;
import com.salesmanager.shop.model.order.v1.ReadableOrder;
import com.salesmanager.shop.model.order.v1.ReadableOrderConfirmation;
import com.salesmanager.shop.model.shoppingcart.PersistableShoppingCartItem;
import com.salesmanager.shop.model.shoppingcart.ReadableShoppingCart;
import com.salesmanager.shop.model.store.ReadableMerchantStore;
import com.salesmanager.shop.model.system.IntegrationModuleConfiguration;
import com.salesmanager.shop.store.security.AuthenticationRequest;
import com.salesmanager.shop.store.security.AuthenticationResponse;
import com.salesmanager.test.shop.common.ServicesTestSupport;

@SpringBootTest(classes = ShopApplication.class, webEnvironment = WebEnvironment.RANDOM_PORT)
@RunWith(SpringRunner.class)
public class StorefrontJourneysUatTest extends ServicesTestSupport {

	@Test
	public void adminAuthenticationProtectsPrivateCustomerApi() {
		ResponseEntity<AuthenticationResponse> login = testRestTemplate.postForEntity("/api/v1/private/login",
				new HttpEntity<>(new AuthenticationRequest("admin@shopizer.com", "password")),
				AuthenticationResponse.class);
		assertEquals(OK, login.getStatusCode());
		assertNotNull(login.getBody());

		ResponseEntity<String> authorized = testRestTemplate.exchange("/api/v1/private/customers", GET,
				new HttpEntity<>(headers(login.getBody().getToken())), String.class);
		assertEquals(OK, authorized.getStatusCode());

		ResponseEntity<String> noToken = testRestTemplate.getForEntity("/api/v1/private/customers", String.class);
		assertTrue(noToken.getStatusCode().equals(UNAUTHORIZED) || noToken.getStatusCode().equals(FORBIDDEN));

		HttpHeaders invalidHeaders = new HttpHeaders();
		invalidHeaders.setBearerAuth("garbage");
		ResponseEntity<String> invalidToken = testRestTemplate.exchange("/api/v1/private/customers", GET,
				new HttpEntity<>(invalidHeaders), String.class);
		assertNotEquals(OK, invalidToken.getStatusCode());
		assertNotNull(invalidToken.getBody());
		assertFalse(invalidToken.getBody().contains("\"customers\""));
	}

	@Test
	public void registeredCustomerCanReadAuthenticatedProfile() {
		CustomerAccount customer = registerCustomer("profile");
		ResponseEntity<ReadableCustomer> profile = testRestTemplate.exchange("/api/v1/auth/customer/profile", GET,
				new HttpEntity<>(headers(customer.token)), ReadableCustomer.class);

		assertEquals(OK, profile.getStatusCode());
		assertEquals(customer.email, profile.getBody().getEmailAddress());
	}

	@Test
	public void storefrontCanBrowseStoreCategoriesAndProducts() {
		ReadableProduct product = sampleProduct("browse" + System.nanoTime());

		ResponseEntity<ReadableMerchantStore> store = testRestTemplate.getForEntity("/api/v1/store/DEFAULT",
				ReadableMerchantStore.class);
		assertEquals(OK, store.getStatusCode());

		ResponseEntity<String> categories = testRestTemplate.getForEntity("/api/v1/category?store=DEFAULT",
				String.class);
		assertEquals(OK, categories.getStatusCode());

		ResponseEntity<String> products = testRestTemplate.getForEntity("/api/v1/products?store=DEFAULT",
				String.class);
		assertEquals(OK, products.getStatusCode());
		assertTrue(products.getBody().contains(product.getSku()));

		ResponseEntity<ReadableProduct> detail = testRestTemplate.getForEntity(
				"/api/v2/product/" + product.getSku(), ReadableProduct.class);
		assertEquals(OK, detail.getStatusCode());
		assertEquals(product.getSku(), detail.getBody().getSku());
	}

	@Test
	public void cartCanAddAndUpdateItems() {
		ReadableProduct firstProduct = sampleProduct("cart-first" + System.nanoTime());
		ReadableProduct secondProduct = sampleProduct("cart-second" + System.nanoTime());

		PersistableShoppingCartItem firstItem = cartItem(firstProduct.getSku(), 1);
		ResponseEntity<ReadableShoppingCart> created = testRestTemplate.postForEntity("/api/v1/cart",
				new HttpEntity<>(firstItem), ReadableShoppingCart.class);
		assertEquals(CREATED, created.getStatusCode());

		PersistableShoppingCartItem secondItem = cartItem(secondProduct.getSku(), 1);
		ResponseEntity<ReadableShoppingCart> added = testRestTemplate.exchange(
				"/api/v1/cart/" + created.getBody().getCode(), PUT, new HttpEntity<>(secondItem),
				ReadableShoppingCart.class);
		assertEquals(CREATED, added.getStatusCode());

		secondItem.setQuantity(3);
		ResponseEntity<ReadableShoppingCart> updated = testRestTemplate.exchange(
				"/api/v1/cart/" + created.getBody().getCode(), PUT, new HttpEntity<>(secondItem),
				ReadableShoppingCart.class);
		assertEquals(CREATED, updated.getStatusCode());

		ResponseEntity<ReadableShoppingCart> fetched = testRestTemplate.getForEntity(
				"/api/v1/cart/" + created.getBody().getCode(), ReadableShoppingCart.class);
		assertEquals(OK, fetched.getStatusCode());
		assertEquals(4, fetched.getBody().getQuantity());
	}

	@Test
	public void checkoutProtectsOwnedCartAndAllowsOwnerToReadOrder() {
		CustomerAccount owner = registerCustomer("checkout-owner");
		CustomerAccount other = registerCustomer("checkout-other");
		ReadableProduct product = sampleProduct("checkout" + System.nanoTime());
		ReadableShoppingCart anonymousCart = createCart(product.getSku());
		ReadableShoppingCart claimedCart = claimCart(owner, anonymousCart);
		assertEquals(owner.id, claimedCart.getCustomer());

		PersistableOrder order = orderFor(owner, claimedCart);
		ResponseEntity<ReadableOrderConfirmation> rejected = checkout(claimedCart.getCode(), order, other.token);
		assertEquals(NOT_FOUND, rejected.getStatusCode());

		ResponseEntity<ReadableOrderConfirmation> unknown = checkout(
				String.format("%032x", System.nanoTime()), orderWithAmount("0"), owner.token);
		assertEquals(NOT_FOUND, unknown.getStatusCode());

		ResponseEntity<ReadableOrderConfirmation> placed = checkout(claimedCart.getCode(), order, owner.token);
		assertEquals(OK, placed.getStatusCode());
		assertNotNull(placed.getBody());
		assertTrue(placed.getBody().getId() > 0L);
		ResponseEntity<ReadableOrder> readBack = testRestTemplate.exchange(
				"/api/v1/auth/orders/" + placed.getBody().getId(), GET,
				new HttpEntity<>(headers(owner.token)), ReadableOrder.class);
		assertEquals(OK, readBack.getStatusCode());
		assertNotNull(readBack.getBody());
		assertEquals(placed.getBody().getId(), readBack.getBody().getId());
	}

	@Test
	public void servicesEndpointUsesBasicAuthentication() {
		HttpHeaders validHeaders = new HttpHeaders();
		validHeaders.setContentType(MediaType.APPLICATION_JSON);
		validHeaders.setBasicAuth("admin@shopizer.com", "password");
		ResponseEntity<String> valid = testRestTemplate.exchange("/services/private/system/optin", POST,
				new HttpEntity<>("{\"journey\":\"uat\"}", validHeaders), String.class);
		assertEquals(OK, valid.getStatusCode());

		HttpHeaders invalidHeaders = new HttpHeaders();
		invalidHeaders.setContentType(MediaType.APPLICATION_JSON);
		invalidHeaders.setBasicAuth("admin@shopizer.com", "wrong-password");
		ResponseEntity<String> invalid = testRestTemplate.exchange("/services/private/system/optin", POST,
				new HttpEntity<>("{\"journey\":\"uat\"}", invalidHeaders), String.class);
		assertEquals(UNAUTHORIZED, invalid.getStatusCode());
	}

	@Test
	public void actuatorAndSwaggerDocumentationAreAvailable() {
		ResponseEntity<String> health = testRestTemplate.getForEntity("/actuator/health", String.class);
		assertEquals(OK, health.getStatusCode());
		assertTrue(health.getBody().contains("UP"));

		ResponseEntity<String> apiDocs = testRestTemplate.getForEntity("/v2/api-docs", String.class);
		assertEquals(OK, apiDocs.getStatusCode());
		assertTrue(apiDocs.getBody().contains("swagger"));
	}

	private CustomerAccount registerCustomer(String name) {
		String email = name + "-" + System.nanoTime() + "@uat-test.com";
		PersistableCustomer customer = new PersistableCustomer();
		customer.setEmailAddress(email);
		customer.setPassword("clear123");
		customer.setGender(CustomerGender.M.name());
		customer.setLanguage("en");
		customer.setStoreCode(Constants.DEFAULT_STORE);
		Address billing = new Address();
		billing.setFirstName(name);
		billing.setLastName("UAT");
		billing.setCountry("BE");
		customer.setBilling(billing);
		ResponseEntity<PersistableCustomer> registered = testRestTemplate.postForEntity("/api/v1/customer/register",
				new HttpEntity<>(customer, getHeader()), PersistableCustomer.class);
		assertEquals(OK, registered.getStatusCode());
		assertNotNull(registered.getBody());

		ResponseEntity<AuthenticationResponse> login = testRestTemplate.postForEntity("/api/v1/customer/login",
				new HttpEntity<>(new AuthenticationRequest(email, "clear123")), AuthenticationResponse.class);
		assertEquals(OK, login.getStatusCode());
		return new CustomerAccount(email, registered.getBody().getId(), login.getBody().getToken());
	}

	private ReadableShoppingCart createCart(String sku) {
		ResponseEntity<ReadableShoppingCart> response = testRestTemplate.postForEntity("/api/v1/cart",
				new HttpEntity<>(cartItem(sku, 1)), ReadableShoppingCart.class);
		assertEquals(CREATED, response.getStatusCode());
		return response.getBody();
	}

	private ReadableShoppingCart claimCart(CustomerAccount customer, ReadableShoppingCart cart) {
		ResponseEntity<ReadableShoppingCart> claimed = testRestTemplate.exchange(
				"/api/v1/auth/customer/cart?cart=" + cart.getCode(), GET,
				new HttpEntity<>(headers(customer.token)), ReadableShoppingCart.class);
		assertEquals(OK, claimed.getStatusCode());
		return claimed.getBody();
	}

	private PersistableOrder orderFor(CustomerAccount customer, ReadableShoppingCart cart) {
		ResponseEntity<ReadableOrderTotalSummary> total = testRestTemplate.exchange(
				"/api/v1/auth/cart/" + cart.getId() + "/total", GET,
				new HttpEntity<>(headers(customer.token)), ReadableOrderTotalSummary.class);
		if (total.getStatusCode() == OK && total.getBody() != null) {
			return orderWithAmount(numericAmount(total.getBody().getTotal()));
		}
		ResponseEntity<ReadableOrderTotalSummary> publicTotal = testRestTemplate.getForEntity(
				"/api/v1/cart/" + cart.getCode() + "/total", ReadableOrderTotalSummary.class);
		assertEquals(OK, publicTotal.getStatusCode());
		return orderWithAmount(numericAmount(publicTotal.getBody().getTotal()));
	}

	private PersistableOrder orderWithAmount(String amount) {
		IntegrationModuleConfiguration paymentModule = new IntegrationModuleConfiguration();
		paymentModule.setCode("moneyorder");
		paymentModule.setActive(true);
		paymentModule.setDefaultSelected(true);
		paymentModule.getIntegrationKeys().put("address", "Checkout Test");
		ResponseEntity<Void> configured = testRestTemplate.postForEntity("/api/v1/private/modules/payment",
				new HttpEntity<>(paymentModule, getHeader()), Void.class);
		assertEquals(OK, configured.getStatusCode());

		PersistablePayment payment = new PersistablePayment();
		payment.setPaymentType("MONEYORDER");
		payment.setTransactionType("CAPTURE");
		payment.setPaymentModule("moneyorder");
		payment.setAmount(amount);
		PersistableOrder order = new PersistableOrder();
		order.setPayment(payment);
		order.setCurrency("USD");
		order.setCustomerAgreement(true);
		return order;
	}

	private ResponseEntity<ReadableOrderConfirmation> checkout(String code, PersistableOrder order, String token) {
		return testRestTemplate.exchange("/api/v1/auth/cart/" + code + "/checkout?store=DEFAULT", POST,
				new HttpEntity<>(order, headers(token)), ReadableOrderConfirmation.class);
	}

	private PersistableShoppingCartItem cartItem(String sku, int quantity) {
		PersistableShoppingCartItem item = new PersistableShoppingCartItem();
		item.setProduct(sku);
		item.setQuantity(quantity);
		return item;
	}

	private String numericAmount(String amount) {
		return amount.replaceAll("[^0-9.]", "");
	}

	private HttpHeaders headers(String token) {
		HttpHeaders headers = new HttpHeaders();
		headers.setContentType(MediaType.APPLICATION_JSON);
		headers.setBearerAuth(token);
		return headers;
	}

	private static class CustomerAccount {
		private final String email;
		private final Long id;
		private final String token;

		private CustomerAccount(String email, Long id, String token) {
			this.email = email;
			this.id = id;
			this.token = token;
		}
	}
}
