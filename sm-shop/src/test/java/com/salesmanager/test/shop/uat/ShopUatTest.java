package com.salesmanager.test.shop.uat;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import java.util.Collections;
import java.util.UUID;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.SpringBootTest.WebEnvironment;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.test.context.junit4.SpringRunner;

import com.fasterxml.jackson.databind.JsonNode;
import com.salesmanager.core.business.constants.Constants;
import com.salesmanager.shop.application.ShopApplication;
import com.salesmanager.shop.model.catalog.product.ReadableProduct;
import com.salesmanager.shop.model.customer.PersistableCustomer;
import com.salesmanager.shop.model.customer.address.Address;
import com.salesmanager.shop.model.order.v1.PersistableOrder;
import com.salesmanager.shop.model.order.transaction.PersistablePayment;
import com.salesmanager.shop.model.security.PersistableGroup;
import com.salesmanager.shop.model.shoppingcart.PersistableShoppingCartItem;
import com.salesmanager.shop.model.shoppingcart.ReadableShoppingCart;
import com.salesmanager.shop.model.system.IntegrationModuleConfiguration;
import com.salesmanager.shop.model.user.PersistableUser;
import com.salesmanager.shop.model.user.ReadableUser;
import com.salesmanager.shop.store.security.AuthenticationRequest;
import com.salesmanager.shop.store.security.AuthenticationResponse;
import com.salesmanager.test.shop.common.ServicesTestSupport;

@SpringBootTest(classes = ShopApplication.class, webEnvironment = WebEnvironment.RANDOM_PORT)
@RunWith(SpringRunner.class)
public class ShopUatTest extends ServicesTestSupport {

	private static final String PAYMENT_PATH = "/api/v1/private/modules/payment";
	private static final String MODULE_PATH = PAYMENT_PATH + "/moneyorder";
	private static final String PASSWORD = "Password1";

	@Test
	public void adminAndPaymentSettingsJourney() throws Exception {
		HttpHeaders adminHeaders = login("admin@shopizer.com", "password", "/api/v1/private/login");
		ResponseEntity<String> adminPaymentList = get(PAYMENT_PATH, adminHeaders);
		assertEquals(HttpStatus.OK, adminPaymentList.getStatusCode());
		assertEquals(HttpStatus.OK, get(MODULE_PATH, adminHeaders).getStatusCode());

		assertPrivatePaymentStatus(new HttpHeaders(), HttpStatus.UNAUTHORIZED);
		HttpHeaders badToken = new HttpHeaders();
		badToken.setBearerAuth("bad-token");
		assertPaymentEndpointsAreNotSuccessful(badToken);

		HttpHeaders catalogueHeaders = createStaff("ADMIN_CATALOGUE");
		assertEquals(HttpStatus.UNAUTHORIZED, get(PAYMENT_PATH, catalogueHeaders).getStatusCode());
		assertEquals(HttpStatus.UNAUTHORIZED, get(MODULE_PATH, catalogueHeaders).getStatusCode());
	}

	@Test
	public void customerBrowseCartAndCheckoutJourney() throws Exception {
		String email = uniqueEmail("shopper");
		PersistableCustomer customer = customer(email);
		ResponseEntity<String> registration = testRestTemplate.postForEntity("/api/v1/customer/register",
				new HttpEntity<>(customer, getHeader()), String.class);
		assertTrue(registration.getStatusCode().is2xxSuccessful());
		HttpHeaders customerHeaders = login(email, PASSWORD, "/api/v1/customer/login");
		ResponseEntity<String> profile = get("/api/v1/auth/customer/profile", customerHeaders);
		assertEquals(HttpStatus.OK, profile.getStatusCode());

		ResponseEntity<JsonNode> store = testRestTemplate.getForEntity("/api/v1/store/DEFAULT", JsonNode.class);
		assertEquals(HttpStatus.OK, store.getStatusCode());
		ResponseEntity<JsonNode> categories = testRestTemplate.getForEntity("/api/v1/category?store=DEFAULT",
				JsonNode.class);
		assertEquals(HttpStatus.OK, categories.getStatusCode());
		ResponseEntity<JsonNode> products = testRestTemplate.getForEntity("/api/v1/products?store=DEFAULT",
				JsonNode.class);
		assertEquals(HttpStatus.OK, products.getStatusCode());
		ReadableProduct createdProduct = sampleProduct("shopUat" + UUID.randomUUID().toString().replace("-", ""));
		String sku = createdProduct.getSku();
		assertTrue("Created product has no SKU", !sku.isEmpty());
		products = testRestTemplate.getForEntity("/api/v1/products?store=DEFAULT", JsonNode.class);
		assertTrue(products.getBody().path("products").isArray());
		assertTrue(products.getBody().path("products").size() > 0);
		ResponseEntity<ReadableProduct> product = testRestTemplate.getForEntity("/api/v2/product/" + sku,
				ReadableProduct.class);
		assertEquals(HttpStatus.OK, product.getStatusCode());

		PersistableShoppingCartItem item = new PersistableShoppingCartItem();
		item.setProduct(sku);
		item.setQuantity(1);
		ResponseEntity<ReadableShoppingCart> created = testRestTemplate.postForEntity("/api/v1/cart",
				new HttpEntity<>(item), ReadableShoppingCart.class);
		assertEquals(HttpStatus.CREATED, created.getStatusCode());
		assertNotNull(created.getBody());
		item.setQuantity(2);
		ResponseEntity<ReadableShoppingCart> updated = testRestTemplate.exchange(
				"/api/v1/cart/" + created.getBody().getCode(), HttpMethod.PUT,
				new HttpEntity<>(item), ReadableShoppingCart.class);
		assertEquals(HttpStatus.CREATED, updated.getStatusCode());
		assertEquals(2, updated.getBody().getQuantity());
		ResponseEntity<ReadableShoppingCart> cart = testRestTemplate.getForEntity(
				"/api/v1/cart/" + created.getBody().getCode(), ReadableShoppingCart.class);
		assertEquals(HttpStatus.OK, cart.getStatusCode());
		assertEquals(2, cart.getBody().getQuantity());

		PersistableOrder order = new PersistableOrder();
		order.setCurrency(store.getBody().path("currency").path("code").asText("USD"));
		PersistablePayment payment = new PersistablePayment();
		payment.setPaymentModule("moneyorder");
		payment.setPaymentType("MONEYORDER");
		payment.setTransactionType("AUTHORIZECAPTURE");
		payment.setAmount(cart.getBody().getTotal().toPlainString());
		order.setPayment(payment);
		IntegrationModuleConfiguration moneyOrder = new IntegrationModuleConfiguration();
		moneyOrder.setCode("moneyorder");
		moneyOrder.setActive(true);
		moneyOrder.setDefaultSelected(true);
		moneyOrder.setIntegrationKeys(Collections.singletonMap("address", "UAT"));
		ResponseEntity<String> configuredPayment = testRestTemplate.exchange(PAYMENT_PATH, HttpMethod.POST,
				new HttpEntity<>(moneyOrder, getHeader()), String.class);
		assertTrue(configuredPayment.getStatusCode().is2xxSuccessful());
		ResponseEntity<JsonNode> checkout = testRestTemplate.postForEntity(
				"/api/v1/auth/cart/" + cart.getBody().getCode() + "/checkout",
				new HttpEntity<>(order, customerHeaders), JsonNode.class);
		assertEquals(HttpStatus.OK, checkout.getStatusCode());
		assertNotNull(checkout.getBody());
		String orderId = checkout.getBody().path("id").asText();
		assertTrue("Checkout response did not include an order id", !orderId.isEmpty());
		ResponseEntity<JsonNode> orderReadback = testRestTemplate.exchange("/api/v1/auth/orders/" + orderId,
				HttpMethod.GET, new HttpEntity<>(customerHeaders), JsonNode.class);
		assertEquals(HttpStatus.OK, orderReadback.getStatusCode());
	}

	@Test
	public void servicesBasicAuthHealthAndApiDocumentation() {
		HttpHeaders validBasicAuth = new HttpHeaders();
		validBasicAuth.setBasicAuth("admin@shopizer.com", "password");
		validBasicAuth.setContentType(MediaType.TEXT_PLAIN);
		ResponseEntity<String> services = testRestTemplate.exchange("/services/private/system/optin",
				HttpMethod.POST, new HttpEntity<>("{\"email\":\"uat@example.com\"}", validBasicAuth),
				String.class);
		assertEquals(HttpStatus.OK, services.getStatusCode());

		HttpHeaders invalidBasicAuth = new HttpHeaders();
		invalidBasicAuth.setBasicAuth("admin@shopizer.com", "incorrect-password");
		invalidBasicAuth.setContentType(MediaType.TEXT_PLAIN);
		ResponseEntity<String> denied = testRestTemplate.exchange("/services/private/system/optin",
				HttpMethod.POST, new HttpEntity<>("{\"email\":\"uat@example.com\"}", invalidBasicAuth),
				String.class);
		assertEquals(HttpStatus.UNAUTHORIZED, denied.getStatusCode());

		ResponseEntity<JsonNode> health = testRestTemplate.getForEntity("/actuator/health", JsonNode.class);
		assertEquals(HttpStatus.OK, health.getStatusCode());
		assertEquals("UP", health.getBody().path("status").asText());
		ResponseEntity<String> apiDocs = testRestTemplate.getForEntity("/v2/api-docs", String.class);
		assertEquals(HttpStatus.OK, apiDocs.getStatusCode());
	}

	private void assertPrivatePaymentStatus(HttpHeaders headers, HttpStatus expected) {
		assertEquals(expected, get(PAYMENT_PATH, headers).getStatusCode());
		assertEquals(expected, get(MODULE_PATH, headers).getStatusCode());
		IntegrationModuleConfiguration configuration = new IntegrationModuleConfiguration();
		configuration.setCode("moneyorder");
		configuration.setActive(true);
		ResponseEntity<String> post = testRestTemplate.exchange(PAYMENT_PATH, HttpMethod.POST,
				new HttpEntity<>(configuration, headers), String.class);
		assertEquals(expected, post.getStatusCode());
	}

	private void assertPaymentEndpointsAreNotSuccessful(HttpHeaders headers) {
		ResponseEntity<String> list = get(PAYMENT_PATH, headers);
		ResponseEntity<String> module = get(MODULE_PATH, headers);
		IntegrationModuleConfiguration configuration = new IntegrationModuleConfiguration();
		configuration.setCode("moneyorder");
		configuration.setActive(true);
		ResponseEntity<String> post = testRestTemplate.exchange(PAYMENT_PATH, HttpMethod.POST,
				new HttpEntity<>(configuration, headers), String.class);
		System.out.println("UAT invalid-token payment statuses list/single/post: "
				+ list.getStatusCodeValue() + "/" + module.getStatusCodeValue() + "/"
				+ post.getStatusCodeValue());
		assertTrue(!list.getStatusCode().is2xxSuccessful());
		assertTrue(!module.getStatusCode().is2xxSuccessful());
		assertTrue(!post.getStatusCode().is2xxSuccessful());
	}

	private ResponseEntity<String> get(String path, HttpHeaders headers) {
		return testRestTemplate.exchange(path, HttpMethod.GET, new HttpEntity<>(headers), String.class);
	}

	private HttpHeaders login(String email, String password, String path) {
		ResponseEntity<AuthenticationResponse> response = testRestTemplate.postForEntity(path,
				new HttpEntity<>(new AuthenticationRequest(email, password)), AuthenticationResponse.class);
		assertEquals(HttpStatus.OK, response.getStatusCode());
		assertNotNull(response.getBody());
		HttpHeaders headers = new HttpHeaders();
		headers.setBearerAuth(response.getBody().getToken());
		return headers;
	}

	private HttpHeaders createStaff(String groupName) throws Exception {
		String email = uniqueEmail(groupName.toLowerCase());
		PersistableUser user = new PersistableUser();
		user.setDefaultLanguage("en");
		user.setEmailAddress(email);
		user.setFirstName("UAT");
		user.setLastName("Staff");
		user.setUserName(email);
		user.setPassword(PASSWORD);
		user.setRepeatPassword(PASSWORD);
		PersistableGroup group = new PersistableGroup();
		group.setName(groupName);
		user.getGroups().add(group);
		ResponseEntity<ReadableUser> response = testRestTemplate.exchange("/api/v1/private/user/",
				HttpMethod.POST, new HttpEntity<>(user, getHeader()), ReadableUser.class);
		assertEquals(HttpStatus.OK, response.getStatusCode());
		return login(email, PASSWORD, "/api/v1/private/login");
	}

	private PersistableCustomer customer(String email) {
		PersistableCustomer customer = new PersistableCustomer();
		customer.setEmailAddress(email);
		customer.setPassword(PASSWORD);
		customer.setGender("M");
		customer.setLanguage("en");
		customer.setStoreCode(Constants.DEFAULT_STORE);
		Address billing = new Address();
		billing.setFirstName("UAT");
		billing.setLastName("Customer");
		billing.setCountry("BE");
		customer.setBilling(billing);
		return customer;
	}

	private String uniqueEmail(String prefix) {
		return prefix + "." + UUID.randomUUID().toString().replace("-", "").substring(0, 12) + "@uat.test";
	}
}
