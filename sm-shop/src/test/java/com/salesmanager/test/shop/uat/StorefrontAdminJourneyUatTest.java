package com.salesmanager.test.shop.uat;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import java.util.Collections;
import java.util.UUID;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.springframework.beans.factory.annotation.Autowired;
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
import com.fasterxml.jackson.databind.ObjectMapper;
import com.salesmanager.core.business.constants.Constants;
import com.salesmanager.core.model.customer.CustomerGender;
import com.salesmanager.shop.application.ShopApplication;
import com.salesmanager.shop.model.catalog.product.ReadableProduct;
import com.salesmanager.shop.model.customer.PersistableCustomer;
import com.salesmanager.shop.model.customer.address.Address;
import com.salesmanager.shop.model.order.v1.PersistableOrder;
import com.salesmanager.shop.model.order.v1.ReadableOrder;
import com.salesmanager.shop.model.order.v1.ReadableOrderConfirmation;
import com.salesmanager.shop.model.order.transaction.PersistablePayment;
import com.salesmanager.shop.model.shoppingcart.PersistableShoppingCartItem;
import com.salesmanager.shop.model.shoppingcart.ReadableShoppingCart;
import com.salesmanager.shop.model.store.ReadableMerchantStore;
import com.salesmanager.shop.model.system.IntegrationModuleConfiguration;
import com.salesmanager.shop.store.security.AuthenticationRequest;
import com.salesmanager.shop.store.security.AuthenticationResponse;
import com.salesmanager.test.shop.common.ServicesTestSupport;

@SpringBootTest(classes = ShopApplication.class, webEnvironment = WebEnvironment.RANDOM_PORT)
@RunWith(SpringRunner.class)
public class StorefrontAdminJourneyUatTest extends ServicesTestSupport {

	@Autowired
	private TestRestTemplate testRestTemplate;

	@Autowired
	private ObjectMapper objectMapper;

	@Test
	public void adminLoginProtectsAdminCustomerListing() {
		ResponseEntity<AuthenticationResponse> login = testRestTemplate.postForEntity(
				"/api/v1/private/login",
				new HttpEntity<>(new AuthenticationRequest("admin@shopizer.com", "password")),
				AuthenticationResponse.class);
		assertEquals(HttpStatus.OK, login.getStatusCode());
		assertNotNull(login.getBody());
		assertNotNull(login.getBody().getToken());

		HttpHeaders adminHeaders = bearerHeaders(login.getBody().getToken());
		ResponseEntity<String> authorized = testRestTemplate.exchange(
				"/api/v1/private/customers?store=DEFAULT", HttpMethod.GET,
				new HttpEntity<>(adminHeaders), String.class);
		assertEquals(HttpStatus.OK, authorized.getStatusCode());

		ResponseEntity<String> anonymous = testRestTemplate.getForEntity(
				"/api/v1/private/customers?store=DEFAULT", String.class);
		assertUnauthorizedOrForbidden(anonymous.getStatusCode());

	}

	@Test
	public void customerCanBrowseCartCheckoutAndReadBackOrder() throws Exception {
		String suffix = UUID.randomUUID().toString().replace("-", "");
		String email = "uat-" + suffix + "@example.test";
		String password = "clear123";

		PersistableCustomer customer = new PersistableCustomer();
		customer.setEmailAddress(email);
		customer.setPassword(password);
		customer.setGender(CustomerGender.M.name());
		customer.setLanguage("en");
		customer.setStoreCode(Constants.DEFAULT_STORE);
		customer.setBilling(address("UAT", "Customer"));
		customer.setDelivery(address("UAT", "Customer"));

		ResponseEntity<PersistableCustomer> registration = testRestTemplate.postForEntity(
				"/api/v1/customer/register",
				new HttpEntity<>(customer, getHeader()),
				PersistableCustomer.class);
		assertEquals(HttpStatus.OK, registration.getStatusCode());

		ResponseEntity<AuthenticationResponse> login = testRestTemplate.postForEntity(
				"/api/v1/customer/login",
				new HttpEntity<>(new AuthenticationRequest(email, password)),
				AuthenticationResponse.class);
		assertEquals(HttpStatus.OK, login.getStatusCode());
		assertNotNull(login.getBody());
		assertNotNull(login.getBody().getToken());
		HttpHeaders customerHeaders = bearerHeaders(login.getBody().getToken());
		HttpEntity<Void> customerRequest = new HttpEntity<>(customerHeaders);

		ResponseEntity<String> profile = testRestTemplate.exchange(
				"/api/v1/auth/customer/profile?store=DEFAULT", HttpMethod.GET,
				customerRequest, String.class);
		assertEquals(HttpStatus.OK, profile.getStatusCode());

		ResponseEntity<ReadableMerchantStore> store = testRestTemplate.getForEntity(
				"/api/v1/store/DEFAULT", ReadableMerchantStore.class);
		assertEquals(HttpStatus.OK, store.getStatusCode());
		assertNotNull(store.getBody());

		ResponseEntity<String> categories = testRestTemplate.getForEntity(
				"/api/v1/category?store=DEFAULT", String.class);
		assertEquals(HttpStatus.OK, categories.getStatusCode());
		ResponseEntity<String> products = testRestTemplate.getForEntity(
				"/api/v1/products?store=DEFAULT", String.class);
		assertEquals(HttpStatus.OK, products.getStatusCode());

		ReadableProduct product = sampleProduct("uat" + suffix.substring(0, 12));
		assertNotNull(product);
		ResponseEntity<ReadableProduct> productDetails = testRestTemplate.getForEntity(
				"/api/v2/product/" + product.getSku() + "?store=DEFAULT", ReadableProduct.class);
		assertEquals(HttpStatus.OK, productDetails.getStatusCode());
		assertNotNull(productDetails.getBody());

		PersistableShoppingCartItem item = new PersistableShoppingCartItem();
		item.setProduct(product.getSku());
		item.setQuantity(1);
		ResponseEntity<ReadableShoppingCart> createdCart = testRestTemplate.postForEntity(
				"/api/v1/cart?store=DEFAULT", new HttpEntity<>(item, customerHeaders), ReadableShoppingCart.class);
		assertEquals(HttpStatus.CREATED, createdCart.getStatusCode());
		assertNotNull(createdCart.getBody());
		assertEquals(1, createdCart.getBody().getQuantity());

		item.setQuantity(2);
		ResponseEntity<ReadableShoppingCart> updatedCart = testRestTemplate.exchange(
				"/api/v1/cart/" + createdCart.getBody().getCode() + "?store=DEFAULT", HttpMethod.PUT,
				new HttpEntity<>(item, customerHeaders), ReadableShoppingCart.class);
		assertEquals(HttpStatus.CREATED, updatedCart.getStatusCode());
		assertNotNull(updatedCart.getBody());
		assertEquals(2, updatedCart.getBody().getQuantity());

		ResponseEntity<ReadableShoppingCart> readCart = testRestTemplate.exchange(
				"/api/v1/cart/" + createdCart.getBody().getCode() + "?store=DEFAULT", HttpMethod.GET,
				customerRequest, ReadableShoppingCart.class);
		assertEquals(HttpStatus.OK, readCart.getStatusCode());
		assertNotNull(readCart.getBody());
		assertEquals(2, readCart.getBody().getQuantity());

		assertNotNull("Shopping cart total is required for checkout", readCart.getBody().getTotal());

		IntegrationModuleConfiguration paymentConfiguration = new IntegrationModuleConfiguration();
		paymentConfiguration.setCode("moneyorder");
		paymentConfiguration.setActive(true);
		paymentConfiguration.setDefaultSelected(true);
		paymentConfiguration.setIntegrationKeys(Collections.singletonMap("address", "test"));
		ResponseEntity<Void> configuredPayment = testRestTemplate.exchange(
				"/api/v1/private/modules/payment?store=DEFAULT", HttpMethod.POST,
				new HttpEntity<>(paymentConfiguration, getHeader()), Void.class);
		assertEquals(HttpStatus.OK, configuredPayment.getStatusCode());

		PersistablePayment payment = new PersistablePayment();
		payment.setAmount(readCart.getBody().getTotal().toPlainString());
		payment.setPaymentType("MONEYORDER");
		payment.setTransactionType("AUTHORIZECAPTURE");
		payment.setPaymentModule("moneyorder");
		PersistableOrder order = new PersistableOrder();
		order.setCurrency(store.getBody().getCurrency());
		order.setPayment(payment);
		order.setCustomerAgreement(true);

		ResponseEntity<ReadableOrderConfirmation> checkout = testRestTemplate.postForEntity(
				"/api/v1/auth/cart/" + createdCart.getBody().getCode() + "/checkout?store=DEFAULT",
				new HttpEntity<>(order, customerHeaders), ReadableOrderConfirmation.class);
		assertEquals(HttpStatus.OK, checkout.getStatusCode());
		assertNotNull(checkout.getBody());
		assertNotNull(checkout.getBody().getId());

		ResponseEntity<ReadableOrder> readOrder = testRestTemplate.exchange(
				"/api/v1/auth/orders/" + checkout.getBody().getId() + "?store=DEFAULT",
				HttpMethod.GET, customerRequest, ReadableOrder.class);
		assertEquals(HttpStatus.OK, readOrder.getStatusCode());
		assertNotNull(readOrder.getBody());
		assertEquals(checkout.getBody().getId(), readOrder.getBody().getId());
	}

	@Test
	public void servicesEndpointRequiresValidBasicAuthentication() {
		HttpHeaders validHeaders = new HttpHeaders();
		validHeaders.setBasicAuth("admin@shopizer.com", "password");
		validHeaders.setContentType(MediaType.TEXT_PLAIN);
		ResponseEntity<String> valid = testRestTemplate.exchange(
				"/services/private/system/optin", HttpMethod.POST,
				new HttpEntity<>("uat-optin", validHeaders), String.class);
		assertEquals(HttpStatus.OK, valid.getStatusCode());

		HttpHeaders invalidHeaders = new HttpHeaders();
		invalidHeaders.setBasicAuth("admin@shopizer.com", "bad-password");
		invalidHeaders.setContentType(MediaType.TEXT_PLAIN);
		ResponseEntity<String> invalid = testRestTemplate.exchange(
				"/services/private/system/optin", HttpMethod.POST,
				new HttpEntity<>("uat-optin", invalidHeaders), String.class);
		assertUnauthorizedOrForbidden(invalid.getStatusCode());
	}

	@Test
	public void actuatorHealthIsUpAndOpenApiDocsAreAvailable() throws Exception {
		ResponseEntity<String> health = testRestTemplate.getForEntity("/actuator/health", String.class);
		assertEquals(HttpStatus.OK, health.getStatusCode());
		JsonNode healthJson = objectMapper.readTree(health.getBody());
		assertEquals("UP", healthJson.path("status").asText());

		ResponseEntity<String> docs = testRestTemplate.getForEntity("/v3/api-docs", String.class);
		assertEquals(HttpStatus.OK, docs.getStatusCode());
	}

	private Address address(String firstName, String lastName) {
		Address address = new Address();
		address.setFirstName(firstName);
		address.setLastName(lastName);
		address.setCountry("BE");
		address.setAddress("1 UAT Street");
		address.setCity("Brussels");
		address.setPhone("123456789");
		return address;
	}

	private HttpHeaders bearerHeaders(String token) {
		HttpHeaders headers = new HttpHeaders();
		headers.setBearerAuth(token);
		headers.setContentType(MediaType.APPLICATION_JSON);
		return headers;
	}

	private void assertUnauthorizedOrForbidden(HttpStatus status) {
		assertTrue("Expected 401 or 403, but received " + status,
				status == HttpStatus.UNAUTHORIZED || status == HttpStatus.FORBIDDEN);
	}
}
