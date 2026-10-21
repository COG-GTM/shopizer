package com.salesmanager.test.shop.uat;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;
import static org.springframework.http.HttpStatus.CREATED;
import static org.springframework.http.HttpStatus.OK;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.SpringBootTest.WebEnvironment;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.test.context.junit4.SpringRunner;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.salesmanager.core.business.constants.Constants;
import com.salesmanager.core.model.customer.CustomerGender;
import com.salesmanager.shop.application.ShopApplication;
import com.salesmanager.shop.model.catalog.product.ReadableProduct;
import com.salesmanager.shop.model.catalog.product.ReadableProductList;
import com.salesmanager.shop.model.customer.PersistableCustomer;
import com.salesmanager.shop.model.customer.address.Address;
import com.salesmanager.shop.model.order.transaction.PersistablePayment;
import com.salesmanager.shop.model.order.v1.PersistableOrder;
import com.salesmanager.shop.model.order.v1.ReadableOrderConfirmation;
import com.salesmanager.shop.model.shoppingcart.PersistableShoppingCartItem;
import com.salesmanager.shop.model.shoppingcart.ReadableShoppingCart;
import com.salesmanager.shop.model.store.ReadableMerchantStore;
import com.salesmanager.shop.model.system.IntegrationModuleConfiguration;
import com.salesmanager.shop.store.security.AuthenticationRequest;
import com.salesmanager.shop.store.security.AuthenticationResponse;
import com.salesmanager.test.shop.common.ServicesTestSupport;

@RunWith(SpringRunner.class)
@SpringBootTest(classes = ShopApplication.class, webEnvironment = WebEnvironment.RANDOM_PORT)
public class ShopUatJourneyTest extends ServicesTestSupport {

	private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

	@Test
	public void completesCustomerShoppingJourneyAndVerifiesSecuredEndpoints() throws Exception {
		ResponseEntity<AuthenticationResponse> adminLogin = testRestTemplate.postForEntity(
				"/api/v1/private/login",
				new HttpEntity<>(new AuthenticationRequest("admin@shopizer.com", "password")),
				AuthenticationResponse.class);
		assertEquals(OK, adminLogin.getStatusCode());
		assertNotNull(adminLogin.getBody());

		String adminToken = adminLogin.getBody().getToken();
		assertEquals(OK, get("/api/v1/private/user/profile", bearer(adminToken)).getStatusCode());
		assertUnauthorized(get("/api/v1/private/user/profile", new HttpHeaders()));
		assertUnauthorized(get("/api/v1/private/user/profile", bearer("abc.def.ghi")));

		String email = "shop-uat-" + System.currentTimeMillis() + "@example.com";
		PersistableCustomer customer = new PersistableCustomer();
		customer.setEmailAddress(email);
		customer.setPassword("clear123");
		customer.setGender(CustomerGender.M.name());
		customer.setLanguage("en");
		customer.setStoreCode(Constants.DEFAULT_STORE);
		Address billing = new Address();
		billing.setFirstName("Shop");
		billing.setLastName("UAT");
		billing.setCountry("BE");
		customer.setBilling(billing);
		ResponseEntity<PersistableCustomer> registration = testRestTemplate.postForEntity(
				"/api/v1/customer/register", new HttpEntity<>(customer, bearer(adminToken)),
				PersistableCustomer.class);
		assertEquals(OK, registration.getStatusCode());

		ResponseEntity<AuthenticationResponse> customerLogin = testRestTemplate.postForEntity(
				"/api/v1/customer/login",
				new HttpEntity<>(new AuthenticationRequest(email, "clear123")),
				AuthenticationResponse.class);
		assertEquals(OK, customerLogin.getStatusCode());
		assertNotNull(customerLogin.getBody());
		String customerToken = customerLogin.getBody().getToken();
		HttpHeaders customerHeaders = bearer(customerToken);
		assertEquals(OK, get("/api/v1/auth/customer/profile", customerHeaders).getStatusCode());

		ResponseEntity<ReadableMerchantStore> store = get("/api/v1/store/DEFAULT", new HttpHeaders(),
				ReadableMerchantStore.class);
		assertEquals(OK, store.getStatusCode());
		ResponseEntity<String> categories = get("/api/v1/category?store=DEFAULT", new HttpHeaders(), String.class);
		assertEquals(OK, categories.getStatusCode());

		String sku = "uat" + System.currentTimeMillis();
		ReadableProduct createdProduct = sampleProduct(sku);
		assertNotNull(createdProduct);
		ResponseEntity<ReadableProductList> products = get(
				"/api/v1/products?store=DEFAULT&count=100", new HttpHeaders(), ReadableProductList.class);
		assertEquals(OK, products.getStatusCode());
		assertNotNull(products.getBody());
		assertTrue(products.getBody().getProducts().stream().anyMatch(product -> sku.equals(product.getSku())));
		ResponseEntity<ReadableProduct> productDetail = get(
				"/api/v2/product/" + sku + "?store=DEFAULT", new HttpHeaders(), ReadableProduct.class);
		assertEquals(OK, productDetail.getStatusCode());

		PersistableShoppingCartItem cartItem = new PersistableShoppingCartItem();
		cartItem.setProduct(sku);
		cartItem.setQuantity(1);
		ResponseEntity<ReadableShoppingCart> cartCreated = testRestTemplate.postForEntity(
				"/api/v1/cart?store=DEFAULT", new HttpEntity<>(cartItem), ReadableShoppingCart.class);
		assertEquals(CREATED, cartCreated.getStatusCode());
		assertNotNull(cartCreated.getBody());
		String cartCode = cartCreated.getBody().getCode();
		cartItem.setQuantity(2);
		ResponseEntity<ReadableShoppingCart> cartUpdated = testRestTemplate.exchange(
				"/api/v1/cart/" + cartCode + "?store=DEFAULT", HttpMethod.PUT,
				new HttpEntity<>(cartItem), ReadableShoppingCart.class);
		assertEquals(CREATED, cartUpdated.getStatusCode());
		assertEquals(OK, get("/api/v1/cart/" + cartCode + "?store=DEFAULT", new HttpHeaders(),
				ReadableShoppingCart.class).getStatusCode());

		IntegrationModuleConfiguration paymentConfiguration = new IntegrationModuleConfiguration();
		paymentConfiguration.setCode("moneyorder");
		paymentConfiguration.setActive(true);
		paymentConfiguration.setDefaultSelected(true);
		paymentConfiguration.getIntegrationKeys().put("address", "Test Store");
		paymentConfiguration.getIntegrationKeys().put("transaction", "AUTHORIZECAPTURE");
		assertEquals(OK, testRestTemplate.postForEntity(
				"/api/v1/private/modules/payment?store=DEFAULT",
				new HttpEntity<>(paymentConfiguration, bearer(adminToken)), Void.class).getStatusCode());

		JsonNode totalResponse = OBJECT_MAPPER.readTree(testRestTemplate.getForObject(
				"/api/v1/cart/" + cartCode + "/total?store=DEFAULT", String.class));
		String amount = totalResponse.path("total").asText().replaceAll("[^0-9.-]", "");
		assertTrue(!amount.isEmpty());
		PersistablePayment payment = new PersistablePayment();
		payment.setAmount(amount);
		payment.setPaymentType("MONEYORDER");
		payment.setTransactionType("AUTHORIZECAPTURE");
		payment.setPaymentModule("moneyorder");
		PersistableOrder order = new PersistableOrder();
		order.setCurrency(store.getBody().getCurrency());
		order.setCustomerAgreement(true);
		order.setPayment(payment);
		ResponseEntity<ReadableOrderConfirmation> checkout = testRestTemplate.postForEntity(
				"/api/v1/auth/cart/" + cartCode + "/checkout?store=DEFAULT",
				new HttpEntity<>(order, customerHeaders), ReadableOrderConfirmation.class);
		assertEquals(OK, checkout.getStatusCode());
		assertNotNull(checkout.getBody());
		assertNotNull(checkout.getBody().getId());
		assertEquals(OK, get("/api/v1/auth/orders/" + checkout.getBody().getId() + "?store=DEFAULT",
				customerHeaders).getStatusCode());

		HttpHeaders validBasicAuth = new HttpHeaders();
		validBasicAuth.setBasicAuth("admin@shopizer.com", "password");
		HttpHeaders invalidBasicAuth = new HttpHeaders();
		invalidBasicAuth.setBasicAuth("admin@shopizer.com", "incorrect");
		String optinEndpoint = "/services/private/system/optin";
		assertEquals(OK, testRestTemplate.postForEntity(optinEndpoint,
				new HttpEntity<>("{}", validBasicAuth), String.class).getStatusCode());
		assertUnauthorized(testRestTemplate.postForEntity(optinEndpoint,
				new HttpEntity<>("{}", invalidBasicAuth), String.class));

		ResponseEntity<String> health = get("/actuator/health", new HttpHeaders(), String.class);
		assertEquals(OK, health.getStatusCode());
		assertEquals("UP", OBJECT_MAPPER.readTree(health.getBody()).path("status").asText());
		assertEquals(OK, get("/v2/api-docs", new HttpHeaders(), String.class).getStatusCode());
	}

	private HttpHeaders bearer(String token) {
		HttpHeaders headers = new HttpHeaders();
		headers.setContentType(MediaType.APPLICATION_JSON);
		headers.setBearerAuth(token);
		return headers;
	}

	private ResponseEntity<String> get(String path, HttpHeaders headers) {
		return get(path, headers, String.class);
	}

	private <T> ResponseEntity<T> get(String path, HttpHeaders headers, Class<T> responseType) {
		return testRestTemplate.exchange(path, HttpMethod.GET, new HttpEntity<>(headers), responseType);
	}

	private void assertUnauthorized(ResponseEntity<?> response) {
		assertTrue("Unexpected status " + response.getStatusCode(),
				response.getStatusCode().value() == 401 || response.getStatusCode().value() == 403);
		assertTrue(!response.getStatusCode().is5xxServerError());
	}
}
