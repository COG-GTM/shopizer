package com.salesmanager.test.shop.uat;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.test.context.junit4.SpringRunner;

import com.fasterxml.jackson.databind.JsonNode;
import com.salesmanager.shop.constants.Constants;
import com.salesmanager.shop.model.catalog.product.ReadableProduct;
import com.salesmanager.shop.model.catalog.product.ReadableProductList;
import com.salesmanager.shop.model.customer.PersistableCustomer;
import com.salesmanager.shop.model.customer.address.Address;
import com.salesmanager.shop.model.order.transaction.PersistablePayment;
import com.salesmanager.shop.model.order.v1.PersistableOrder;
import com.salesmanager.shop.model.shoppingcart.PersistableShoppingCartItem;
import com.salesmanager.shop.model.shoppingcart.ReadableShoppingCart;
import com.salesmanager.shop.model.system.IntegrationModuleConfiguration;
import com.salesmanager.shop.store.security.AuthenticationRequest;
import com.salesmanager.shop.store.security.AuthenticationResponse;
import com.salesmanager.test.shop.common.ProductGroupTestSupport;

@RunWith(SpringRunner.class)
public class ProductGroupJourneyTest extends ProductGroupTestSupport {

	@Test
	public void catalogueAdminCanPublishAndRemoveProductsForAnonymousShoppers() {
		HttpHeaders admin = createAdmin("DEFAULT", Constants.GROUP_ADMIN_CATALOGUE);
		String code = createGroup("DEFAULT", admin);
		ReadableProduct product = sampleProduct(uniqueCode());
		String membership = "/api/v1/private/products/" + product.getId() + "/group/" + code;

		ResponseEntity<ReadableProductList> added = testRestTemplate.exchange(membership, HttpMethod.POST,
				new HttpEntity<>(admin), ReadableProductList.class);
		assertEquals(HttpStatus.CREATED, added.getStatusCode());
		assertNotNull(added.getBody());
		assertEquals(product.getId(), added.getBody().getProducts().get(0).getId());

		ResponseEntity<ReadableProductList> publicRead = testRestTemplate.getForEntity(
				"/api/v1/products/group/" + code, ReadableProductList.class);
		assertEquals(HttpStatus.OK, publicRead.getStatusCode());
		assertNotNull(publicRead.getBody());
		assertEquals(1, publicRead.getBody().getProducts().size());
		assertEquals(product.getId(), publicRead.getBody().getProducts().get(0).getId());

		ResponseEntity<ReadableProductList> removed = testRestTemplate.exchange(membership, HttpMethod.DELETE,
				new HttpEntity<>(admin), ReadableProductList.class);
		assertEquals(HttpStatus.OK, removed.getStatusCode());
		assertNotNull(removed.getBody());
		assertTrue(removed.getBody().getProducts().isEmpty());
		assertEquals(HttpStatus.OK, testRestTemplate.exchange("/api/v1/private/products/group/" + code,
				HttpMethod.DELETE, new HttpEntity<>(admin), String.class).getStatusCode());
		assertGroupExists("DEFAULT", code, false);
	}

	@Test
	public void anonymousCannotDeleteButCanReadGroup() {
		String code = createGroup("DEFAULT", getHeader());
		assertEquals(HttpStatus.UNAUTHORIZED, testRestTemplate.exchange(
				"/api/v1/private/products/group/" + code, HttpMethod.DELETE,
				HttpEntity.EMPTY, String.class).getStatusCode());
		assertGroupExists("DEFAULT", code, true);
		assertEquals(HttpStatus.METHOD_NOT_ALLOWED, testRestTemplate.exchange(
				"/api/v1/products/group/" + code, HttpMethod.DELETE, HttpEntity.EMPTY, String.class).getStatusCode());
		assertGroupExists("DEFAULT", code, true);
		ResponseEntity<JsonNode> publicRead = testRestTemplate.getForEntity(
				"/api/v1/products/group/" + code, JsonNode.class);
		assertEquals(HttpStatus.OK, publicRead.getStatusCode());
		assertNotNull(publicRead.getBody());
		assertTrue(publicRead.getBody().get("products").isArray());
	}

	@Test
	public void customerCanRegisterLogInAndReadProfileButCannotDeleteGroup() {
		String email = uniqueCode().toLowerCase(java.util.Locale.ROOT) + "@test.com";
		HttpHeaders headers = registerCustomer(email);
		ResponseEntity<JsonNode> profile = testRestTemplate.exchange("/api/v1/auth/customer/profile", HttpMethod.GET,
				new HttpEntity<>(headers), JsonNode.class);
		assertEquals(HttpStatus.OK, profile.getStatusCode());
		assertNotNull(profile.getBody());
		assertEquals(email, profile.getBody().get("emailAddress").asText());
		String code = createGroup("DEFAULT", getHeader());
		assertEquals(HttpStatus.UNAUTHORIZED, testRestTemplate.exchange("/api/v1/private/products/group/" + code,
				HttpMethod.DELETE, new HttpEntity<>(headers), String.class).getStatusCode());
		assertGroupExists("DEFAULT", code, true);
	}

	@Test
	public void shopperCanBrowseAndCreateUpdateReadCart() {
		ReadableProduct product = sampleProduct(uniqueCode());
		for (String path : new String[] { "/api/v1/store/DEFAULT", "/api/v1/category", "/api/v1/products",
				"/api/v2/product/" + product.getSku() }) {
			ResponseEntity<JsonNode> response = testRestTemplate.getForEntity(path, JsonNode.class);
			assertEquals(path, HttpStatus.OK, response.getStatusCode());
			assertNotNull(response.getBody());
		}
		PersistableShoppingCartItem item = new PersistableShoppingCartItem();
		item.setProduct(product.getSku());
		item.setQuantity(1);
		ResponseEntity<ReadableShoppingCart> created = testRestTemplate.postForEntity("/api/v1/cart", item,
				ReadableShoppingCart.class);
		assertEquals(HttpStatus.CREATED, created.getStatusCode());
		assertNotNull(created.getBody());
		String path = "/api/v1/cart/" + created.getBody().getCode();
		item.setQuantity(2);
		ResponseEntity<ReadableShoppingCart> updated = testRestTemplate.exchange(path, HttpMethod.PUT,
				new HttpEntity<>(item), ReadableShoppingCart.class);
		assertEquals(HttpStatus.CREATED, updated.getStatusCode());
		ResponseEntity<ReadableShoppingCart> read = testRestTemplate.getForEntity(path, ReadableShoppingCart.class);
		assertEquals(HttpStatus.OK, read.getStatusCode());
		assertNotNull(read.getBody());
		assertEquals(2, read.getBody().getQuantity());
	}

	@Test
	public void customerCanCheckOutWithMoneyOrderAndReadOrder() {
		IntegrationModuleConfiguration configuration = new IntegrationModuleConfiguration();
		configuration.setCode("moneyorder");
		configuration.setActive(true);
		configuration.getIntegrationKeys().put("address", "123 Test Street");
		assertEquals(HttpStatus.OK, testRestTemplate.postForEntity("/api/v1/private/modules/payment",
				new HttpEntity<>(configuration, getHeader()), String.class).getStatusCode());
		HttpHeaders customer = registerCustomer(uniqueCode().toLowerCase(java.util.Locale.ROOT) + "@test.com");
		ReadableProduct product = sampleProduct(uniqueCode());
		PersistableShoppingCartItem item = new PersistableShoppingCartItem();
		item.setProduct(product.getSku());
		item.setQuantity(1);
		ResponseEntity<ReadableShoppingCart> cart = testRestTemplate.postForEntity("/api/v1/cart", item,
				ReadableShoppingCart.class);
		assertEquals(HttpStatus.CREATED, cart.getStatusCode());
		assertNotNull(cart.getBody());
		PersistablePayment payment = new PersistablePayment();
		payment.setPaymentModule("moneyorder");
		payment.setPaymentType("MONEYORDER");
		payment.setTransactionType("AUTHORIZECAPTURE");
		payment.setAmount(cart.getBody().getTotal().toPlainString());
		PersistableOrder order = new PersistableOrder();
		order.setCurrency("CAD");
		order.setCustomerAgreement(true);
		order.setPayment(payment);
		ResponseEntity<JsonNode> checkout = testRestTemplate.postForEntity(
				"/api/v1/auth/cart/" + cart.getBody().getCode() + "/checkout",
				new HttpEntity<>(order, customer), JsonNode.class);
		assertEquals(HttpStatus.OK, checkout.getStatusCode());
		assertNotNull(checkout.getBody());
		assertTrue(checkout.getBody().get("id").asLong() > 0);
		ResponseEntity<JsonNode> read = testRestTemplate.exchange(
				"/api/v1/auth/orders/" + checkout.getBody().get("id").asLong(), HttpMethod.GET,
				new HttpEntity<>(customer), JsonNode.class);
		assertEquals(HttpStatus.OK, read.getStatusCode());
		assertNotNull(read.getBody());
		assertEquals(checkout.getBody().get("id"), read.getBody().get("id"));
		assertEquals(product.getSku(), read.getBody().get("products").get(0).get("sku").asText());
	}

	@Test
	public void legacyServiceStillRequiresValidBasicCredentials() {
		String path = "/services/private/system/optin";
		assertEquals(HttpStatus.OK, testRestTemplate.withBasicAuth("admin@shopizer.com", "password")
				.postForEntity(path, "{}", String.class).getStatusCode());
		assertEquals(HttpStatus.UNAUTHORIZED, testRestTemplate.withBasicAuth("admin@shopizer.com", "incorrect")
				.postForEntity(path, "{}", String.class).getStatusCode());
	}

	@Test
	public void healthAndApiDocsRemainAvailable() {
		ResponseEntity<JsonNode> health = testRestTemplate.getForEntity("/actuator/health", JsonNode.class);
		assertEquals(HttpStatus.OK, health.getStatusCode());
		assertNotNull(health.getBody());
		assertEquals("UP", health.getBody().get("status").asText());
		ResponseEntity<JsonNode> docs = testRestTemplate.getForEntity("/v2/api-docs", JsonNode.class);
		assertEquals(HttpStatus.OK, docs.getStatusCode());
		assertNotNull(docs.getBody());
		assertEquals("2.0", docs.getBody().get("swagger").asText());
		assertNotNull(docs.getBody().get("paths").get("/api/v1/products/group/{code}"));
	}

	private HttpHeaders registerCustomer(String email) {
		PersistableCustomer customer = new PersistableCustomer();
		customer.setEmailAddress(email);
		customer.setPassword("clear123");
		customer.setGender("M");
		customer.setLanguage("en");
		customer.setStoreCode("DEFAULT");
		Address billing = new Address();
		billing.setFirstName("Group");
		billing.setLastName("Shopper");
		billing.setCountry("US");
		billing.setStateProvince("FL");
		billing.setAddress("123 Test Street");
		billing.setCity("Fort Lauderdale");
		billing.setPostalCode("33301");
		customer.setBilling(billing);
		assertEquals(HttpStatus.OK, testRestTemplate.postForEntity("/api/v1/customer/register",
				customer, String.class).getStatusCode());
		ResponseEntity<AuthenticationResponse> login = testRestTemplate.postForEntity("/api/v1/customer/login",
				new AuthenticationRequest(email, "clear123"), AuthenticationResponse.class);
		assertEquals(HttpStatus.OK, login.getStatusCode());
		assertNotNull(login.getBody());
		assertNotNull(login.getBody().getToken());
		HttpHeaders headers = new HttpHeaders();
		headers.setBearerAuth(login.getBody().getToken());
		return headers;
	}
}
