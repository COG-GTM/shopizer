package com.salesmanager.test.shop.uat;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.SpringBootTest.WebEnvironment;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.test.context.junit.jupiter.SpringExtension;

import com.salesmanager.shop.application.ShopApplication;
import com.salesmanager.shop.model.catalog.category.ReadableCategoryList;
import com.salesmanager.shop.model.catalog.product.ReadableProduct;
import com.salesmanager.shop.model.catalog.product.ReadableProductList;
import com.salesmanager.shop.model.customer.PersistableCustomer;
import com.salesmanager.shop.model.customer.ReadableCustomer;
import com.salesmanager.shop.model.customer.address.Address;
import com.salesmanager.shop.model.order.v1.PersistableOrder;
import com.salesmanager.shop.model.order.v1.ReadableOrder;
import com.salesmanager.shop.model.order.v1.ReadableOrderConfirmation;
import com.salesmanager.shop.model.order.transaction.PersistablePayment;
import com.salesmanager.shop.model.order.ReadableOrderTotalSummary;
import com.salesmanager.shop.model.shoppingcart.PersistableShoppingCartItem;
import com.salesmanager.shop.model.shoppingcart.ReadableShoppingCart;
import com.salesmanager.shop.model.system.IntegrationModuleConfiguration;
import com.salesmanager.shop.model.store.ReadableMerchantStore;
import com.salesmanager.shop.store.security.AuthenticationRequest;
import com.salesmanager.shop.store.security.AuthenticationResponse;
import com.salesmanager.test.shop.common.ServicesTestSupport;

@SpringBootTest(classes = ShopApplication.class, webEnvironment = WebEnvironment.RANDOM_PORT)
@ExtendWith(SpringExtension.class)
public class StorefrontAdminJourneyUatTest extends ServicesTestSupport {

	@Autowired
	private TestRestTemplate testRestTemplate;

	@Test
	public void authenticatesAdminAndProtectsPrivateShippingModules() {
		ResponseEntity<AuthenticationResponse> login = testRestTemplate.postForEntity(
				"/api/v1/private/login", new AuthenticationRequest("admin@shopizer.com", "password"),
				AuthenticationResponse.class);
		assertEquals(HttpStatus.OK, login.getStatusCode());
		assertNotNull(login.getBody());
		assertNotNull(login.getBody().getToken());

		HttpHeaders authenticated = new HttpHeaders();
		authenticated.setBearerAuth(login.getBody().getToken());
		ResponseEntity<String> modules = testRestTemplate.exchange("/api/v1/private/modules/shipping",
				HttpMethod.GET, new HttpEntity<Void>(authenticated), String.class);
		assertEquals(HttpStatus.OK, modules.getStatusCode());

		ResponseEntity<String> anonymous = testRestTemplate.getForEntity(
				"/api/v1/private/modules/shipping", String.class);
		System.out.println("Admin shipping modules without token: " + anonymous.getStatusCode());
		assertEquals(HttpStatus.UNAUTHORIZED, anonymous.getStatusCode());
	}

	@Test
	public void registersCustomerLogsInAndReadsItsProfile() {
		String email = "uat-" + UUID.randomUUID() + "@example.com";
		PersistableCustomer customer = new PersistableCustomer();
		customer.setEmailAddress(email);
		customer.setPassword("UatPassword123!");
		customer.setRepeatPassword("UatPassword123!");
		customer.setFirstName("UAT");
		customer.setLastName("Customer");
		customer.setLanguage("en");
		customer.setBilling(address());
		customer.setDelivery(address());

		ResponseEntity<String> registration = testRestTemplate.postForEntity(
				"/api/v1/customer/register", customer, String.class);
		assertEquals(HttpStatus.OK, registration.getStatusCode());

		ResponseEntity<AuthenticationResponse> login = testRestTemplate.postForEntity(
				"/api/v1/customer/login",
				new AuthenticationRequest(email, "UatPassword123!"), AuthenticationResponse.class);
		assertEquals(HttpStatus.OK, login.getStatusCode());
		assertNotNull(login.getBody());

		ResponseEntity<ReadableCustomer> profile = testRestTemplate.exchange(
				"/api/v1/auth/customer/profile", HttpMethod.GET,
				new HttpEntity<Void>(bearer(login.getBody().getToken())), ReadableCustomer.class);
		assertEquals(HttpStatus.OK, profile.getStatusCode());
		assertEquals(email, profile.getBody().getEmailAddress());
	}

	@Test
	public void browsesStoreCategoriesProductsAndProductDetails() {
		ResponseEntity<ReadableMerchantStore> store =
				testRestTemplate.getForEntity("/api/v1/store/DEFAULT", ReadableMerchantStore.class);
		assertEquals(HttpStatus.OK, store.getStatusCode());
		assertNotNull(store.getBody());

		ReadableProduct product = sampleProduct("uat-browse-" + UUID.randomUUID());
		ResponseEntity<ReadableCategoryList> categories = testRestTemplate.getForEntity(
				"/api/v1/category?store=DEFAULT", ReadableCategoryList.class);
		assertEquals(HttpStatus.OK, categories.getStatusCode());
		assertNotNull(categories.getBody());

		ResponseEntity<ReadableProductList> products = testRestTemplate.getForEntity(
				"/api/v1/products?store=DEFAULT", ReadableProductList.class);
		assertEquals(HttpStatus.OK, products.getStatusCode());
		assertTrue(products.getBody().getProducts().stream()
				.anyMatch(candidate -> product.getSku().equals(candidate.getSku())));

		ResponseEntity<ReadableProduct> detail = testRestTemplate.getForEntity(
				"/api/v2/product/" + product.getSku(), ReadableProduct.class);
		assertEquals(HttpStatus.OK, detail.getStatusCode());
		assertEquals(product.getSku(), detail.getBody().getSku());
	}

	@Test
	public void createsUpdatesAndReadsShoppingCart() {
		ReadableProduct first = sampleProduct("uat-cart-one-" + UUID.randomUUID());
		ReadableProduct second = sampleProduct("uat-cart-two-" + UUID.randomUUID());

		ResponseEntity<ReadableShoppingCart> created = testRestTemplate.postForEntity("/api/v1/cart/",
				item(first.getSku(), 1), ReadableShoppingCart.class);
		assertEquals(HttpStatus.CREATED, created.getStatusCode());
		String cartCode = created.getBody().getCode();

		ResponseEntity<ReadableShoppingCart> added = testRestTemplate.exchange(
				"/api/v1/cart/" + cartCode, HttpMethod.PUT,
				new HttpEntity<PersistableShoppingCartItem>(item(second.getSku(), 1)),
				ReadableShoppingCart.class);
		assertEquals(HttpStatus.CREATED, added.getStatusCode());

		PersistableShoppingCartItem[] updates = { item(first.getSku(), 2), item(second.getSku(), 3) };
		ResponseEntity<ReadableShoppingCart> updated = testRestTemplate.postForEntity(
				"/api/v1/cart/" + cartCode + "/multi", updates, ReadableShoppingCart.class);
		assertEquals(HttpStatus.CREATED, updated.getStatusCode());

		ResponseEntity<ReadableShoppingCart> read = testRestTemplate.getForEntity(
				"/api/v1/cart/" + cartCode, ReadableShoppingCart.class);
		assertEquals(HttpStatus.OK, read.getStatusCode());
		assertEquals(5, read.getBody().getQuantity());
	}

	@Test
	public void placesAndReadsAnOrderForAnAuthenticatedCustomer() {
		String email = "u" + UUID.randomUUID() + "@ex.com";
		AuthenticationResponse customer = registerAndLogin(email);
		ReadableProduct product = sampleProduct("uat-order-product-" + UUID.randomUUID());

		ResponseEntity<ReadableShoppingCart> cartResponse = testRestTemplate.postForEntity("/api/v1/cart/",
				new HttpEntity<PersistableShoppingCartItem>(item(product.getSku(), 1),
						bearer(customer.getToken())), ReadableShoppingCart.class);
		assertEquals(HttpStatus.CREATED, cartResponse.getStatusCode());
		ReadableShoppingCart cart = cartResponse.getBody();

		IntegrationModuleConfiguration moneyOrder = new IntegrationModuleConfiguration();
		moneyOrder.setCode("moneyorder");
		moneyOrder.setActive(true);
		moneyOrder.setDefaultSelected(true);
		Map<String, String> moneyOrderKeys = new HashMap<String, String>();
		moneyOrderKeys.put("address", "Payable to Shopizer");
		moneyOrder.setIntegrationKeys(moneyOrderKeys);
		ResponseEntity<Void> paymentConfiguration = testRestTemplate.exchange(
				"/api/v1/private/modules/payment", HttpMethod.POST,
				new HttpEntity<IntegrationModuleConfiguration>(moneyOrder, getHeader()), Void.class);
		assertEquals(HttpStatus.OK, paymentConfiguration.getStatusCode());

		ResponseEntity<ReadableOrderTotalSummary> total = testRestTemplate.exchange(
				"/api/v1/cart/" + cart.getCode() + "/total", HttpMethod.GET,
				null, ReadableOrderTotalSummary.class);
		assertEquals(HttpStatus.OK, total.getStatusCode());

		PersistablePayment payment = new PersistablePayment();
		payment.setPaymentModule("moneyorder");
		payment.setPaymentType("MONEYORDER");
		payment.setTransactionType("AUTHORIZECAPTURE");
		payment.setAmount(total.getBody().getTotal().replaceAll("[^0-9.-]", ""));
		PersistableOrder order = new PersistableOrder();
		order.setCustomerAgreement(true);
		order.setCurrency("CAD");
		order.setPayment(payment);
		ResponseEntity<ReadableOrderConfirmation> placed = testRestTemplate.exchange(
				"/api/v1/auth/cart/" + cart.getCode() + "/checkout", HttpMethod.POST,
				new HttpEntity<PersistableOrder>(order, bearer(customer.getToken())),
				ReadableOrderConfirmation.class);
		assertEquals(HttpStatus.OK, placed.getStatusCode());
		assertNotNull(placed.getBody());

		ResponseEntity<ReadableOrder> read = testRestTemplate.exchange(
				"/api/v1/auth/orders/" + placed.getBody().getId(), HttpMethod.GET,
				new HttpEntity<Void>(bearer(customer.getToken())), ReadableOrder.class);
		assertEquals(HttpStatus.OK, read.getStatusCode());
	}

	@Test
	public void servicesPrivateEndpointUsesBasicAuthentication() {
		HttpHeaders valid = new HttpHeaders();
		valid.setBasicAuth("admin@shopizer.com", "password");
		ResponseEntity<String> accepted = testRestTemplate.exchange(
				"/services/private/system/module", HttpMethod.GET,
				new HttpEntity<Void>(valid), String.class);

		HttpHeaders invalid = new HttpHeaders();
		invalid.setBasicAuth("admin@shopizer.com", "not-the-password");
		ResponseEntity<String> rejected = testRestTemplate.exchange(
				"/services/private/system/module", HttpMethod.GET,
				new HttpEntity<Void>(invalid), String.class);
		System.out.println("Services private endpoint with valid basic auth: " + accepted.getStatusCode());
		assertTrue(accepted.getStatusCode() != HttpStatus.UNAUTHORIZED);
		assertEquals(HttpStatus.UNAUTHORIZED, rejected.getStatusCode());
	}

	@Test
	public void actuatorHealthAndOpenApiDocumentsAreAvailable() {
		ResponseEntity<String> health = testRestTemplate.getForEntity("/actuator/health", String.class);
		assertEquals(HttpStatus.OK, health.getStatusCode());
		assertTrue(health.getBody().contains("\"status\":\"UP\""));

		ResponseEntity<String> docs = testRestTemplate.getForEntity("/v2/api-docs", String.class);
		assertEquals(HttpStatus.OK, docs.getStatusCode());
		assertTrue(docs.getBody().contains("\"swagger\""));
	}

	private AuthenticationResponse registerAndLogin(String email) {
		PersistableCustomer customer = new PersistableCustomer();
		customer.setEmailAddress(email);
		customer.setPassword("UatPassword123!");
		customer.setRepeatPassword("UatPassword123!");
		customer.setFirstName("UAT");
		customer.setLastName("Order");
		customer.setLanguage("en");
		customer.setBilling(address());
		customer.setDelivery(address());
		testRestTemplate.postForEntity("/api/v1/customer/register", customer, String.class);
		ResponseEntity<AuthenticationResponse> login = testRestTemplate.postForEntity(
				"/api/v1/customer/login", new AuthenticationRequest(email, "UatPassword123!"),
				AuthenticationResponse.class);
		assertEquals(HttpStatus.OK, login.getStatusCode());
		return login.getBody();
	}

	private Address address() {
		Address address = new Address();
		address.setFirstName("UAT");
		address.setLastName("Customer");
		address.setCountry("CA");
		address.setZone("ON");
		address.setPostalCode("K1A 0B1");
		address.setCity("Ottawa");
		address.setAddress("100 Main Street");
		address.setPhone("6135550100");
		return address;
	}

	private PersistableShoppingCartItem item(String sku, int quantity) {
		PersistableShoppingCartItem item = new PersistableShoppingCartItem();
		item.setProduct(sku);
		item.setQuantity(quantity);
		return item;
	}

	private HttpHeaders bearer(String token) {
		HttpHeaders headers = new HttpHeaders();
		headers.setBearerAuth(token);
		return headers;
	}
}
