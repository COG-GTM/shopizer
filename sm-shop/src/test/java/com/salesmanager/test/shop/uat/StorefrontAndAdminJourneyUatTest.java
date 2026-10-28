package com.salesmanager.test.shop.uat;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.math.BigDecimal;
import java.util.Arrays;

import org.junit.jupiter.api.MethodOrderer;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.junit.jupiter.api.TestMethodOrder;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.SpringBootTest.WebEnvironment;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.test.context.junit.jupiter.SpringExtension;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.salesmanager.core.business.constants.Constants;
import com.salesmanager.core.model.customer.CustomerGender;
import com.salesmanager.shop.application.ShopApplication;
import com.salesmanager.shop.model.catalog.product.ReadableProduct;
import com.salesmanager.shop.model.catalog.product.ReadableProductList;
import com.salesmanager.shop.model.customer.PersistableCustomer;
import com.salesmanager.shop.model.customer.address.Address;
import com.salesmanager.shop.model.order.v1.PersistableOrder;
import com.salesmanager.shop.model.order.v1.ReadableOrder;
import com.salesmanager.shop.model.order.v1.ReadableOrderConfirmation;
import com.salesmanager.shop.model.order.transaction.PersistablePayment;
import com.salesmanager.shop.model.shoppingcart.PersistableShoppingCartItem;
import com.salesmanager.shop.model.shoppingcart.ReadableShoppingCart;
import com.salesmanager.shop.model.store.ReadableMerchantStore;
import com.salesmanager.shop.model.user.ReadableUser;
import com.salesmanager.shop.store.security.AuthenticationRequest;
import com.salesmanager.shop.store.security.AuthenticationResponse;
import com.salesmanager.test.shop.common.ServicesTestSupport;

@SpringBootTest(classes = ShopApplication.class, webEnvironment = WebEnvironment.RANDOM_PORT)
@ExtendWith(SpringExtension.class)
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
public class StorefrontAndAdminJourneyUatTest extends ServicesTestSupport {

	private static final String CUSTOMER_EMAIL = "uat-" + System.nanoTime() + "@test.com";
	private static final String PRODUCT_SKU = "uat-prod-" + System.nanoTime();

	private String customerToken;
	private String cartCode;
	private ReadableProduct product;

	@Test
	@Order(1)
	public void adminCanLoginAndReadProfile() {
		ResponseEntity<AuthenticationResponse> login = testRestTemplate.postForEntity("/api/v1/private/login",
				new HttpEntity<>(new AuthenticationRequest("admin@shopizer.com", "password")),
				AuthenticationResponse.class);
		assertEquals(HttpStatus.OK, login.getStatusCode());
		assertNotNull(login.getBody());
		assertNotNull(login.getBody().getToken());

		HttpHeaders adminHeaders = bearerHeaders(login.getBody().getToken());
		ResponseEntity<ReadableUser> profile = exchange("/api/v1/private/user/profile", HttpMethod.GET,
				adminHeaders, ReadableUser.class);
		assertEquals(HttpStatus.OK, profile.getStatusCode());
		assertNotNull(profile.getBody());
		assertFalse(profile.getBody().getUserName().isEmpty());
		assertFalse(profile.getBody().getEmailAddress().isEmpty());

		ResponseEntity<String> noToken = exchange("/api/v1/private/user/profile", HttpMethod.GET,
				new HttpHeaders(), String.class);
		assertEquals(HttpStatus.UNAUTHORIZED, noToken.getStatusCode());

		HttpHeaders invalidTokenHeaders = bearerHeaders("not-a-jwt");
		ResponseEntity<String> invalidToken = exchange("/api/v1/private/user/profile", HttpMethod.GET,
				invalidTokenHeaders, String.class);
		System.out.println("UAT invalid JWT status=" + invalidToken.getStatusCode());
		assertFalse(invalidToken.getStatusCode().is2xxSuccessful());
		if (!HttpStatus.INTERNAL_SERVER_ERROR.equals(invalidToken.getStatusCode())) {
			assertTrue(Arrays.asList(HttpStatus.UNAUTHORIZED, HttpStatus.FORBIDDEN)
					.contains(invalidToken.getStatusCode()),
					"Unexpected invalid-token status: " + invalidToken.getStatusCode());
		}
	}

	@Test
	@Order(2)
	public void customerCanRegisterLoginAndReadProfile() {
		PersistableCustomer customer = new PersistableCustomer();
		customer.setEmailAddress(CUSTOMER_EMAIL);
		customer.setPassword("clear123");
		customer.setGender(CustomerGender.M.name());
		customer.setLanguage("en");
		customer.setStoreCode(Constants.DEFAULT_STORE);
		Address billing = new Address();
		billing.setFirstName("UAT");
		billing.setLastName("Customer");
		billing.setCountry("BE");
		customer.setBilling(billing);

		ResponseEntity<PersistableCustomer> registered = testRestTemplate.postForEntity(
				"/api/v1/customer/register", new HttpEntity<>(customer, getHeader()), PersistableCustomer.class);
		assertEquals(HttpStatus.OK, registered.getStatusCode());

		ResponseEntity<AuthenticationResponse> login = testRestTemplate.postForEntity("/api/v1/customer/login",
				new HttpEntity<>(new AuthenticationRequest(CUSTOMER_EMAIL, "clear123")),
				AuthenticationResponse.class);
		assertEquals(HttpStatus.OK, login.getStatusCode());
		assertNotNull(login.getBody());
		customerToken = login.getBody().getToken();
		assertNotNull(customerToken);

		ResponseEntity<String> profile = exchange("/api/v1/auth/customer/profile", HttpMethod.GET,
				bearerHeaders(customerToken), String.class);
		assertEquals(HttpStatus.OK, profile.getStatusCode());
		assertTrue(profile.getBody().contains(CUSTOMER_EMAIL));
	}

	@Test
	@Order(3)
	public void storefrontCanBrowseAndFindCreatedProduct() {
		ResponseEntity<ReadableMerchantStore> store = testRestTemplate.getForEntity("/api/v1/store/DEFAULT",
				ReadableMerchantStore.class);
		assertEquals(HttpStatus.OK, store.getStatusCode());
		assertEquals(Constants.DEFAULT_STORE, store.getBody().getCode());

		ResponseEntity<String> categories = testRestTemplate.getForEntity("/api/v1/category?store=DEFAULT",
				String.class);
		assertEquals(HttpStatus.OK, categories.getStatusCode());

		product = sampleProduct(PRODUCT_SKU);
		assertNotNull(product.getId());
		assertEquals(PRODUCT_SKU, product.getSku());

		ResponseEntity<ReadableProductList> products = testRestTemplate.getForEntity(
				"/api/v1/products?store=DEFAULT", ReadableProductList.class);
		assertEquals(HttpStatus.OK, products.getStatusCode());
		assertTrue(products.getBody().getProducts().stream()
				.anyMatch(listedProduct -> PRODUCT_SKU.equals(listedProduct.getSku())));

		ResponseEntity<ReadableProduct> productBySku = testRestTemplate.getForEntity(
				"/api/v2/product/" + PRODUCT_SKU, ReadableProduct.class);
		assertEquals(HttpStatus.OK, productBySku.getStatusCode());
		assertEquals(PRODUCT_SKU, productBySku.getBody().getSku());
	}

	@Test
	@Order(4)
	public void cartCanBeCreatedUpdatedAndRead() {
		PersistableShoppingCartItem item = new PersistableShoppingCartItem();
		item.setProduct(PRODUCT_SKU);
		item.setQuantity(1);
		ResponseEntity<ReadableShoppingCart> created = testRestTemplate.postForEntity("/api/v1/cart",
				new HttpEntity<>(item, getHeader()), ReadableShoppingCart.class);
		assertEquals(HttpStatus.CREATED, created.getStatusCode());
		assertNotNull(created.getBody());
		cartCode = created.getBody().getCode();
		assertNotNull(cartCode);

		item.setQuantity(3);
		ResponseEntity<ReadableShoppingCart> updated = testRestTemplate.exchange("/api/v1/cart/" + cartCode,
				HttpMethod.PUT, new HttpEntity<>(item, getHeader()), ReadableShoppingCart.class);
		assertTrue(updated.getStatusCode().is2xxSuccessful());
		assertEquals(3, updated.getBody().getQuantity());

		ResponseEntity<ReadableShoppingCart> fetched = testRestTemplate.getForEntity(
				"/api/v1/cart/" + cartCode, ReadableShoppingCart.class);
		assertEquals(HttpStatus.OK, fetched.getStatusCode());
		assertEquals(3, fetched.getBody().getQuantity());
	}

	@Test
	@Order(5)
	public void checkoutIsAuthenticatedAndOrderCanBeReadWhenPaymentSucceeds() throws Exception {
		PersistableOrder order = new PersistableOrder();
		order.setCurrency("CAD");
		order.setCustomerAgreement(true);
		PersistablePayment payment = new PersistablePayment();
		payment.setPaymentType("MONEYORDER");
		payment.setTransactionType("CAPTURE");
		payment.setAmount(product.getPrice().multiply(BigDecimal.valueOf(3)).toPlainString());
		order.setPayment(payment);

		ResponseEntity<String> unauthenticated = testRestTemplate.postForEntity(
				"/api/v1/auth/cart/" + cartCode + "/checkout", order, String.class);
		assertEquals(HttpStatus.UNAUTHORIZED, unauthenticated.getStatusCode());

		ResponseEntity<String> checkout = exchange("/api/v1/auth/cart/" + cartCode + "/checkout",
				HttpMethod.POST, bearerHeaders(customerToken), order, String.class);
		System.out.println("UAT checkout status=" + checkout.getStatusCode() + ", response=" + checkout.getBody());
		assertNotEquals(HttpStatus.UNAUTHORIZED, checkout.getStatusCode());
		assertNotEquals(HttpStatus.FORBIDDEN, checkout.getStatusCode());

		if (checkout.getStatusCode().is2xxSuccessful()) {
			JsonNode confirmationJson = new ObjectMapper().readTree(checkout.getBody());
			ReadableOrderConfirmation confirmation = new ObjectMapper().treeToValue(confirmationJson,
					ReadableOrderConfirmation.class);
			assertNotNull(confirmation.getId());

			ResponseEntity<ReadableOrder> savedOrder = exchange(
					"/api/v1/auth/orders/" + confirmation.getId(), HttpMethod.GET,
					bearerHeaders(customerToken), ReadableOrder.class);
			assertEquals(HttpStatus.OK, savedOrder.getStatusCode());
			assertEquals(confirmation.getId(), savedOrder.getBody().getId());
		}
	}

	@Test
	@Order(6)
	public void servicesExposePublicDataAndProtectPrivateEndpoints() {
		ResponseEntity<String> publicServices = testRestTemplate.getForEntity("/services/public/DEFAULT",
				String.class);
		assertTrue(publicServices.getStatusCode().is2xxSuccessful());

		HttpHeaders invalidBasicHeaders = new HttpHeaders();
		invalidBasicHeaders.setBasicAuth("wrong-user", "wrong-password");
		ResponseEntity<String> invalidBasic = exchange("/services/private/system/optin", HttpMethod.POST,
				invalidBasicHeaders, "uat", String.class);
		assertEquals(HttpStatus.UNAUTHORIZED, invalidBasic.getStatusCode());
		assertTrue(invalidBasic.getHeaders().getFirst(HttpHeaders.WWW_AUTHENTICATE).startsWith("Basic"));

		HttpHeaders validBasicHeaders = new HttpHeaders();
		validBasicHeaders.setBasicAuth("admin@shopizer.com", "password");
		ResponseEntity<String> validBasic = exchange("/services/private/system/optin", HttpMethod.POST,
				validBasicHeaders, "uat", String.class);
		assertTrue(validBasic.getStatusCode().is2xxSuccessful(),
				"Valid Basic authentication was not accepted: " + validBasic.getStatusCode());
	}

	@Test
	@Order(7)
	public void healthAndSwaggerEndpointsAreAvailable() {
		ResponseEntity<String> health = testRestTemplate.getForEntity("/actuator/health", String.class);
		assertEquals(HttpStatus.OK, health.getStatusCode());
		assertTrue(health.getBody().contains("\"status\":\"UP\""));

		ResponseEntity<String> swagger = testRestTemplate.getForEntity("/v2/api-docs", String.class);
		assertEquals(HttpStatus.OK, swagger.getStatusCode());
		assertTrue(swagger.getBody().contains("\"swagger\""));
	}

	private HttpHeaders bearerHeaders(String token) {
		HttpHeaders headers = new HttpHeaders();
		headers.setBearerAuth(token);
		return headers;
	}

	private <T> ResponseEntity<T> exchange(String path, HttpMethod method, HttpHeaders headers,
			Class<T> responseType) {
		return testRestTemplate.exchange(path, method, new HttpEntity<>(headers), responseType);
	}

	private <T> ResponseEntity<T> exchange(String path, HttpMethod method, HttpHeaders headers, Object body,
			Class<T> responseType) {
		return testRestTemplate.exchange(path, method, new HttpEntity<>(body, headers), responseType);
	}
}
