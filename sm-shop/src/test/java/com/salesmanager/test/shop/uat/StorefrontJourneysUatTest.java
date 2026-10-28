package com.salesmanager.test.shop.uat;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;
import static org.springframework.http.HttpStatus.CREATED;
import static org.springframework.http.HttpStatus.OK;

import java.util.UUID;

import javax.inject.Inject;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.SpringBootTest.WebEnvironment;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.test.context.junit4.SpringRunner;

import com.salesmanager.core.model.customer.CustomerGender;
import com.salesmanager.shop.application.ShopApplication;
import com.salesmanager.shop.model.catalog.product.ReadableProduct;
import com.salesmanager.shop.model.catalog.product.ReadableProductList;
import com.salesmanager.shop.model.customer.PersistableCustomer;
import com.salesmanager.shop.model.customer.address.Address;
import com.salesmanager.shop.model.order.v1.PersistableOrder;
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
public class StorefrontJourneysUatTest extends ServicesTestSupport {

	@Inject
	private TestRestTemplate restTemplate;

	@Test
	public void publicCustomerCartAndStorefrontJourneys() {
		HttpHeaders adminHeaders = adminHeaders();
		assertEquals(OK, restTemplate.exchange("/api/v1/private/customers?store=DEFAULT", HttpMethod.GET,
				new HttpEntity<Void>(adminHeaders), String.class).getStatusCode());
		assertUnauthorized(restTemplate.exchange("/api/v1/private/customers?store=DEFAULT", HttpMethod.GET,
				new HttpEntity<Void>(new HttpHeaders()), String.class).getStatusCodeValue());

		String email = "uat-" + UUID.randomUUID().toString().substring(0, 8) + "@example.test";
		String password = "UatPassword1";
		PersistableCustomer customer = new PersistableCustomer();
		customer.setEmailAddress(email);
		customer.setPassword(password);
		customer.setGender(CustomerGender.M.name());
		customer.setLanguage("en");
		customer.setStoreCode("DEFAULT");
		Address billing = new Address();
		billing.setFirstName("UAT");
		billing.setLastName("Customer");
		billing.setCountry("US");
		customer.setBilling(billing);
		ResponseEntity<PersistableCustomer> registration = restTemplate.postForEntity(
				"/api/v1/customer/register", new HttpEntity<>(customer, adminHeaders), PersistableCustomer.class);
		assertEquals(OK, registration.getStatusCode());

		ResponseEntity<AuthenticationResponse> login = restTemplate.postForEntity("/api/v1/customer/login",
				new HttpEntity<>(new AuthenticationRequest(email, password)), AuthenticationResponse.class);
		assertEquals(OK, login.getStatusCode());
		assertNotNull(login.getBody().getToken());
		HttpHeaders customerHeaders = new HttpHeaders();
		customerHeaders.setBearerAuth(login.getBody().getToken());
		assertEquals(OK, restTemplate.exchange("/api/v1/auth/customer/profile?store=DEFAULT", HttpMethod.GET,
				new HttpEntity<Void>(customerHeaders), String.class).getStatusCode());

		ReadableProduct product = sampleProduct("uat-" + UUID.randomUUID().toString().replace("-", "").substring(0, 8));
		assertNotNull(product);
		ResponseEntity<ReadableMerchantStore> store = restTemplate.getForEntity(
				"/api/v1/store/DEFAULT?lang=en", ReadableMerchantStore.class);
		assertEquals(OK, store.getStatusCode());
		assertEquals(OK, restTemplate.getForEntity("/api/v1/category?store=DEFAULT&lang=en", String.class)
				.getStatusCode());
		ResponseEntity<ReadableProductList> products = restTemplate.getForEntity(
				"/api/v1/products?store=DEFAULT&lang=en", ReadableProductList.class);
		assertEquals(OK, products.getStatusCode());
		assertNotNull(products.getBody());
		assertTrue(!products.getBody().getProducts().isEmpty());
		assertTrue(products.getBody().getProducts().stream()
				.anyMatch(listedProduct -> product.getSku().equals(listedProduct.getSku())));
		assertEquals(OK, restTemplate.getForEntity("/api/v2/product/" + product.getSku()
				+ "?store=DEFAULT&lang=en", String.class).getStatusCode());

		PersistableShoppingCartItem cartItem = new PersistableShoppingCartItem();
		cartItem.setProduct(product.getSku());
		cartItem.setQuantity(1);
		ResponseEntity<ReadableShoppingCart> cartResponse = restTemplate.postForEntity(
				"/api/v1/cart?store=DEFAULT", new HttpEntity<>(cartItem), ReadableShoppingCart.class);
		assertEquals(CREATED, cartResponse.getStatusCode());
		String cartCode = cartResponse.getBody().getCode();
		assertNotNull(cartCode);
		cartItem.setQuantity(2);
		ResponseEntity<ReadableShoppingCart> changedCart = restTemplate.exchange(
				"/api/v1/cart/" + cartCode + "?store=DEFAULT", HttpMethod.PUT,
				new HttpEntity<>(cartItem), ReadableShoppingCart.class);
		assertEquals(CREATED, changedCart.getStatusCode());
		assertEquals(2, changedCart.getBody().getQuantity());
		ResponseEntity<ReadableShoppingCart> cartRead = restTemplate.getForEntity(
				"/api/v1/cart/" + cartCode + "?store=DEFAULT", ReadableShoppingCart.class);
		assertEquals(OK, cartRead.getStatusCode());

		IntegrationModuleConfiguration moneyOrder = new IntegrationModuleConfiguration();
		moneyOrder.setCode("moneyorder");
		moneyOrder.setActive(true);
		moneyOrder.getIntegrationKeys().put("address", "UAT");
		assertEquals(OK, restTemplate.postForEntity("/api/v1/private/modules/payment?store=DEFAULT",
				new HttpEntity<>(moneyOrder, adminHeaders), String.class).getStatusCode());

		PersistableOrder order = new PersistableOrder();
		order.setCurrency("CAD");
		order.setCustomerAgreement(true);
		PersistablePayment payment = new PersistablePayment();
		payment.setPaymentModule("moneyorder");
		payment.setPaymentType("MONEYORDER");
		payment.setTransactionType("AUTHORIZECAPTURE");
		payment.setAmount(cartRead.getBody().getTotal().toPlainString());
		payment.setPaymentToken("uat-payment");
		order.setPayment(payment);
		ResponseEntity<ReadableOrderConfirmation> checkout = restTemplate.postForEntity(
				"/api/v1/auth/cart/" + cartCode + "/checkout?store=DEFAULT",
				new HttpEntity<>(order, customerHeaders), ReadableOrderConfirmation.class);
		assertEquals(OK, checkout.getStatusCode());
		assertNotNull(checkout.getBody());
		assertNotNull(checkout.getBody().getId());
		assertEquals(OK, restTemplate.exchange("/api/v1/auth/orders/" + checkout.getBody().getId()
				+ "?store=DEFAULT", HttpMethod.GET, new HttpEntity<Void>(customerHeaders), String.class)
				.getStatusCode());

		HttpHeaders validBasic = new HttpHeaders();
		validBasic.setBasicAuth("admin@shopizer.com", "password");
		validBasic.setContentType(MediaType.TEXT_PLAIN);
		assertEquals(OK, restTemplate.postForEntity("/services/private/system/optin",
				new HttpEntity<>("uat-optin", validBasic), String.class).getStatusCode());
		HttpHeaders invalidBasic = new HttpHeaders();
		invalidBasic.setBasicAuth("admin@shopizer.com", "wrong-password");
		assertUnauthorized(restTemplate.postForEntity("/services/private/system/optin",
				new HttpEntity<>("uat-optin", invalidBasic), String.class).getStatusCodeValue());

		ResponseEntity<String> health = restTemplate.getForEntity("/actuator/health", String.class);
		assertEquals(OK, health.getStatusCode());
		assertTrue(health.getBody().contains("UP"));
		assertEquals(OK, restTemplate.getForEntity("/v2/api-docs", String.class).getStatusCode());
	}

	private HttpHeaders adminHeaders() {
		ResponseEntity<AuthenticationResponse> response = restTemplate.postForEntity("/api/v1/private/login",
				new HttpEntity<>(new AuthenticationRequest("admin@shopizer.com", "password")),
				AuthenticationResponse.class);
		assertEquals(OK, response.getStatusCode());
		HttpHeaders headers = new HttpHeaders();
		headers.setBearerAuth(response.getBody().getToken());
		return headers;
	}

	private void assertUnauthorized(int status) {
		assertTrue("Expected HTTP 401 or 403, got " + status, status == 401 || status == 403);
	}
}
