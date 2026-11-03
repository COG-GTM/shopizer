package com.salesmanager.test.shop.uat;

import static org.junit.Assert.*;

import java.util.UUID;

import javax.inject.Inject;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.test.context.junit4.SpringRunner;

import com.fasterxml.jackson.databind.JsonNode;
import com.salesmanager.core.business.services.merchant.MerchantStoreService;
import com.salesmanager.core.business.services.payments.PaymentService;
import com.salesmanager.core.model.system.IntegrationConfiguration;
import com.salesmanager.shop.model.catalog.product.ReadableProduct;
import com.salesmanager.shop.model.customer.PersistableCustomer;
import com.salesmanager.shop.model.customer.address.Address;
import com.salesmanager.shop.model.order.transaction.PersistablePayment;
import com.salesmanager.shop.model.order.v1.PersistableOrder;
import com.salesmanager.shop.model.order.v1.ReadableOrderConfirmation;
import com.salesmanager.shop.model.shoppingcart.PersistableShoppingCartItem;
import com.salesmanager.shop.model.shoppingcart.ReadableShoppingCart;
import com.salesmanager.shop.store.security.AuthenticationRequest;
import com.salesmanager.shop.store.security.AuthenticationResponse;
import com.salesmanager.test.shop.common.ServicesTestSupport;

@RunWith(SpringRunner.class)
public class StorefrontJourneyTest extends ServicesTestSupport {

	@Inject private MerchantStoreService merchantService;
	@Inject private PaymentService paymentService;

	@Test
	public void adminLoginProtectsPrivateResources() {
		HttpHeaders admin = getHeader();
		assertNotNull(admin.getFirst("Authorization"));
		assertEquals(HttpStatus.OK, get("/api/v1/private/orders", admin).getStatusCode());
		assertDenied(get("/api/v1/private/orders", new HttpHeaders()));
	}

	@Test
	public void browseAndUpdateCart() {
		ReadableProduct product = sampleProduct("uatbrowse" + unique());
		assertEquals(HttpStatus.OK, get("/api/v1/store/DEFAULT", new HttpHeaders()).getStatusCode());
		assertEquals(HttpStatus.OK, get("/api/v1/category/", new HttpHeaders()).getStatusCode());
		JsonNode products = get("/api/v1/products", new HttpHeaders()).getBody();
		assertTrue(products.toString(), products.path("products").size() > 0);
		assertEquals(HttpStatus.OK, get("/api/v2/product/" + product.getSku(), new HttpHeaders()).getStatusCode());
		ReadableShoppingCart cart = createCart(product);
		PersistableShoppingCartItem item = new PersistableShoppingCartItem();
		item.setProduct(product.getSku());
		item.setQuantity(2);
		ResponseEntity<ReadableShoppingCart> updated = testRestTemplate.exchange("/api/v1/cart/" + cart.getCode(),
				HttpMethod.PUT, new HttpEntity<>(item), ReadableShoppingCart.class);
		assertEquals(HttpStatus.CREATED, updated.getStatusCode());
		assertEquals(2, updated.getBody().getQuantity());
		ResponseEntity<ReadableShoppingCart> fetched = testRestTemplate.getForEntity(
				"/api/v1/cart/" + cart.getCode(), ReadableShoppingCart.class);
		assertEquals(HttpStatus.OK, fetched.getStatusCode());
		assertEquals(2, fetched.getBody().getQuantity());
		assertEquals(product.getSku(), fetched.getBody().getProducts().get(0).getSku());
	}

	@Test
	public void registeredCustomerChecksOutAndAdminFindsOrder() throws Exception {
		String name = "Uat" + unique();
		String email = name + "@test.invalid";
		PersistableCustomer customer = new PersistableCustomer();
		customer.setEmailAddress(email);
		customer.setPassword("Password1");
		customer.setRepeatPassword("Password1");
		customer.setGender("M");
		customer.setLanguage("en");
		customer.setStoreCode("DEFAULT");
		Address address = new Address();
		address.setFirstName("Journey");
		address.setLastName(name);
		address.setAddress("1 Main Street");
		address.setCity("Burlington");
		address.setCountry("US");
		address.setZone("VT");
		address.setPostalCode("05401");
		address.setPhone("5550002222");
		customer.setBilling(address);
		customer.setDelivery(address);
		ResponseEntity<PersistableCustomer> registered = testRestTemplate.postForEntity("/api/v1/customer/register",
				new HttpEntity<>(customer), PersistableCustomer.class);
		assertEquals(HttpStatus.OK, registered.getStatusCode());
		ResponseEntity<AuthenticationResponse> login = testRestTemplate.postForEntity("/api/v1/customer/login",
				new AuthenticationRequest(email, "Password1"), AuthenticationResponse.class);
		assertEquals(HttpStatus.OK, login.getStatusCode());
		assertNotNull(login.getBody().getToken());
		HttpHeaders headers = new HttpHeaders();
		headers.setBearerAuth(login.getBody().getToken());
		assertEquals(HttpStatus.OK, get("/api/v1/auth/customer/profile", headers).getStatusCode());

		ReadableShoppingCart cart = createCart(sampleProduct("uatcheckout" + unique()));
		ResponseEntity<ReadableShoppingCart> attached = testRestTemplate.exchange(
				"/api/v1/auth/customer/cart?cart=" + cart.getCode(), HttpMethod.GET,
				new HttpEntity<>(headers), ReadableShoppingCart.class);
		assertEquals(HttpStatus.OK, attached.getStatusCode());
		assertNotNull(attached.getBody().getCustomer());
		IntegrationConfiguration configuration = new IntegrationConfiguration();
		configuration.setModuleCode("moneyorder");
		configuration.setActive(true);
		configuration.getIntegrationKeys().put("address", "1 Main Street");
		paymentService.savePaymentModuleConfiguration(configuration, merchantService.getByCode("DEFAULT"));
		PersistablePayment payment = new PersistablePayment();
		payment.setPaymentModule("moneyorder");
		payment.setPaymentType("MONEYORDER");
		payment.setTransactionType("AUTHORIZECAPTURE");
		payment.setAmount(attached.getBody().getTotal().toPlainString());
		PersistableOrder order = new PersistableOrder();
		order.setCurrency(merchantService.getByCode("DEFAULT").getCurrency().getCode());
		order.setCustomerAgreement(true);
		order.setPayment(payment);
		ResponseEntity<ReadableOrderConfirmation> placed = testRestTemplate.postForEntity(
				"/api/v1/auth/cart/" + cart.getCode() + "/checkout", new HttpEntity<>(order, headers),
				ReadableOrderConfirmation.class);
		assertEquals(placed.toString(), HttpStatus.OK, placed.getStatusCode());
		Long id = placed.getBody().getId();
		assertNotNull(id);
		ResponseEntity<JsonNode> fetched = get("/api/v1/auth/orders/" + id, headers);
		assertEquals(HttpStatus.OK, fetched.getStatusCode());
		assertEquals(id.longValue(), fetched.getBody().path("id").asLong());
		ResponseEntity<JsonNode> search = get("/api/v1/private/orders?name=" + name, getHeader());
		assertEquals(HttpStatus.OK, search.getStatusCode());
		assertEquals(1, search.getBody().path("recordsTotal").asInt());
		assertEquals(id.longValue(), search.getBody().path("orders").get(0).path("id").asLong());
	}

	@Test
	public void basicAuthenticationAndDiagnostics() {
		HttpHeaders valid = new HttpHeaders();
		valid.setBasicAuth("admin@shopizer.com", "password");
		ResponseEntity<String> accepted = testRestTemplate.postForEntity("/services/private/system/optin",
				new HttpEntity<>("{}", valid), String.class);
		assertEquals(accepted.toString(), HttpStatus.OK, accepted.getStatusCode());
		HttpHeaders invalid = new HttpHeaders();
		invalid.setBasicAuth("admin@shopizer.com", "wrong-password");
		assertDenied(testRestTemplate.postForEntity("/services/private/system/optin", new HttpEntity<>("{}", invalid), String.class));
		ResponseEntity<JsonNode> health = get("/actuator/health", new HttpHeaders());
		assertEquals(HttpStatus.OK, health.getStatusCode());
		assertEquals("UP", health.getBody().path("status").asText());
		assertEquals(HttpStatus.OK, testRestTemplate.getForEntity("/v2/api-docs", String.class).getStatusCode());
	}

	private ReadableShoppingCart createCart(ReadableProduct product) {
		PersistableShoppingCartItem item = new PersistableShoppingCartItem();
		item.setProduct(product.getSku());
		item.setQuantity(1);
		ResponseEntity<ReadableShoppingCart> response = testRestTemplate.postForEntity("/api/v1/cart",
				item, ReadableShoppingCart.class);
		assertEquals(HttpStatus.CREATED, response.getStatusCode());
		assertEquals(1, response.getBody().getQuantity());
		return response.getBody();
	}

	private ResponseEntity<JsonNode> get(String url, HttpHeaders headers) {
		return testRestTemplate.exchange(url, HttpMethod.GET, new HttpEntity<>(headers), JsonNode.class);
	}

	private void assertDenied(ResponseEntity<?> response) {
		assertTrue(response.toString(), response.getStatusCodeValue() == 401 || response.getStatusCodeValue() == 403);
	}

	private String unique() {
		return UUID.randomUUID().toString().substring(0, 8);
	}
}
