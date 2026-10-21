package com.salesmanager.test.shop.uat;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import java.nio.charset.StandardCharsets;
import java.util.Map;

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

import com.salesmanager.core.business.constants.Constants;
import com.salesmanager.core.model.payments.PaymentType;
import com.salesmanager.core.model.payments.TransactionType;
import com.salesmanager.core.model.customer.CustomerGender;
import com.salesmanager.shop.application.ShopApplication;
import com.salesmanager.shop.model.catalog.product.ReadableProduct;
import com.salesmanager.shop.model.catalog.product.ReadableProductList;
import com.salesmanager.shop.model.customer.PersistableCustomer;
import com.salesmanager.shop.model.customer.address.Address;
import com.salesmanager.shop.model.order.v1.PersistableOrder;
import com.salesmanager.shop.model.order.v1.ReadableOrderConfirmation;
import com.salesmanager.shop.model.order.v0.ReadableOrder;
import com.salesmanager.shop.model.order.transaction.PersistablePayment;
import com.salesmanager.shop.model.shoppingcart.PersistableShoppingCartItem;
import com.salesmanager.shop.model.shoppingcart.ReadableShoppingCart;
import com.salesmanager.shop.model.store.ReadableMerchantStore;
import com.salesmanager.shop.populator.customer.ReadableCustomerList;
import com.salesmanager.shop.store.security.AuthenticationRequest;
import com.salesmanager.shop.store.security.AuthenticationResponse;
import com.salesmanager.test.shop.common.ServicesTestSupport;

@RunWith(SpringRunner.class)
@SpringBootTest(classes = ShopApplication.class, webEnvironment = WebEnvironment.RANDOM_PORT)
public class StorefrontAdminJourneyUatTest extends ServicesTestSupport {

	@Test
	public void exercisesAdminStorefrontCartCheckoutAndServicesJourneys() {
		AuthenticationResponse adminLogin = loginAdmin();
		HttpHeaders adminHeaders = bearerHeaders(adminLogin.getToken());
		assertPrivateUser(adminHeaders);

		String email = "uat-" + System.currentTimeMillis() + "@example.com";
		AuthenticationResponse customerLogin = registerAndLoginCustomer(email, adminHeaders);
		HttpHeaders customerHeaders = bearerHeaders(customerLogin.getToken());
		assertCustomerProfile(customerHeaders);

		assertStorefrontBrowsing(adminHeaders);
		ReadableProduct firstProduct = sampleProduct("uat-" + System.currentTimeMillis() + "-one");
		ReadableProduct secondProduct = sampleProduct("uat-" + System.currentTimeMillis() + "-two");
		assertProductList(firstProduct);

		ReadableShoppingCart cart = exerciseCart(firstProduct, secondProduct, customerHeaders);
		exerciseCheckout(cart.getCode(), cart.getTotal().toPlainString(), customerHeaders);
		exerciseServicesBasicAuthentication();
		assertOperationalEndpoints();
	}

	private AuthenticationResponse loginAdmin() {
		ResponseEntity<AuthenticationResponse> response = testRestTemplate.postForEntity(
				"/api/v1/private/login", new AuthenticationRequest("admin@shopizer.com", "password"),
				AuthenticationResponse.class);
		assertEquals(200, response.getStatusCodeValue());
		assertNotNull(response.getBody());
		assertNotNull(response.getBody().getToken());
		return response.getBody();
	}

	private void assertPrivateUser(HttpHeaders headers) {
		ResponseEntity<String> authorized = testRestTemplate.exchange("/api/v1/private/users/1", HttpMethod.GET,
				new HttpEntity<Void>(headers), String.class);
		assertEquals(200, authorized.getStatusCodeValue());

		ResponseEntity<String> missingToken = testRestTemplate.getForEntity("/api/v1/private/users/1", String.class);
		assertEquals(401, missingToken.getStatusCodeValue());

		ResponseEntity<String> invalidToken = testRestTemplate.exchange("/api/v1/private/users/1", HttpMethod.GET,
				new HttpEntity<Void>(bearerHeaders("invalid-token")), String.class);
		assertEquals(500, invalidToken.getStatusCodeValue());
	}

	private AuthenticationResponse registerAndLoginCustomer(String email, HttpHeaders adminHeaders) {
		PersistableCustomer customer = new PersistableCustomer();
		customer.setEmailAddress(email);
		customer.setPassword("Password1!");
		customer.setRepeatPassword("Password1!");
		customer.setGender(CustomerGender.M.name());
		customer.setLanguage("en");
		customer.setStoreCode(Constants.DEFAULT_STORE);

		Address billing = new Address();
		billing.setFirstName("UAT");
		billing.setLastName("Customer");
		billing.setCountry("BE");
		customer.setBilling(billing);

		ResponseEntity<String> registration = testRestTemplate.postForEntity(
				"/api/v1/customer/register?store=DEFAULT",
				new HttpEntity<PersistableCustomer>(customer, adminHeaders), String.class);
		assertTrue("Customer registration returned " + registration.getStatusCode(),
				registration.getStatusCode().is2xxSuccessful());

		ResponseEntity<AuthenticationResponse> login = testRestTemplate.postForEntity(
				"/api/v1/customer/login?store=DEFAULT",
				new AuthenticationRequest(email, "Password1!"), AuthenticationResponse.class);
		assertEquals(200, login.getStatusCodeValue());
		assertNotNull(login.getBody());
		assertNotNull(login.getBody().getToken());
		return login.getBody();
	}

	private void assertCustomerProfile(HttpHeaders headers) {
		ResponseEntity<String> profile = testRestTemplate.exchange("/api/v1/auth/customer/profile?store=DEFAULT",
				HttpMethod.GET, new HttpEntity<Void>(headers), String.class);
		assertEquals(200, profile.getStatusCodeValue());
	}

	private void assertStorefrontBrowsing(HttpHeaders headers) {
		ResponseEntity<ReadableMerchantStore> store = testRestTemplate.getForEntity(
				"/api/v1/store/DEFAULT", ReadableMerchantStore.class);
		assertEquals(200, store.getStatusCodeValue());
		assertNotNull(store.getBody());

		ResponseEntity<String> categories = testRestTemplate.getForEntity(
				"/api/v1/category?store=DEFAULT", String.class);
		assertEquals(200, categories.getStatusCodeValue());

		ResponseEntity<String> existingProducts = testRestTemplate.exchange("/api/v1/products?store=DEFAULT",
				HttpMethod.GET, new HttpEntity<Void>(headers), String.class);
		assertEquals(200, existingProducts.getStatusCodeValue());
	}

	private void assertProductList(ReadableProduct product) {
		ResponseEntity<ReadableProductList> products = testRestTemplate.getForEntity(
				"/api/v1/products?store=DEFAULT&sku=" + product.getSku(), ReadableProductList.class);
		assertEquals(200, products.getStatusCodeValue());
		assertNotNull(products.getBody());

		ResponseEntity<ReadableProduct> detail = testRestTemplate.getForEntity(
				"/api/v2/product/" + product.getSku() + "?store=DEFAULT", ReadableProduct.class);
		assertEquals(200, detail.getStatusCodeValue());
		assertNotNull(detail.getBody());
	}

	private ReadableShoppingCart exerciseCart(ReadableProduct firstProduct, ReadableProduct secondProduct,
			HttpHeaders customerHeaders) {
		PersistableShoppingCartItem firstItem = cartItem(firstProduct.getSku(), 1);
		ResponseEntity<ReadableShoppingCart> created = testRestTemplate.postForEntity(
				"/api/v1/cart?store=DEFAULT", new HttpEntity<PersistableShoppingCartItem>(firstItem, customerHeaders),
				ReadableShoppingCart.class);
		assertEquals(201, created.getStatusCodeValue());
		assertNotNull(created.getBody());
		assertNotNull(created.getBody().getCode());

		ResponseEntity<ReadableShoppingCart> added = testRestTemplate.exchange(
				"/api/v1/cart/" + created.getBody().getCode() + "?store=DEFAULT", HttpMethod.PUT,
				new HttpEntity<PersistableShoppingCartItem>(cartItem(secondProduct.getSku(), 1), customerHeaders),
				ReadableShoppingCart.class);
		assertEquals(201, added.getStatusCodeValue());

		ResponseEntity<ReadableShoppingCart> updated = testRestTemplate.exchange(
				"/api/v1/cart/" + created.getBody().getCode() + "?store=DEFAULT", HttpMethod.PUT,
				new HttpEntity<PersistableShoppingCartItem>(cartItem(firstProduct.getSku(), 2), customerHeaders),
				ReadableShoppingCart.class);
		assertEquals(201, updated.getStatusCodeValue());

		ResponseEntity<ReadableShoppingCart> read = testRestTemplate.getForEntity(
				"/api/v1/cart/" + created.getBody().getCode() + "?store=DEFAULT", ReadableShoppingCart.class);
		assertEquals(200, read.getStatusCodeValue());
		assertNotNull(read.getBody());
		assertEquals(3, read.getBody().getQuantity());
		return read.getBody();
	}

	private void exerciseCheckout(String cartCode, String cartTotal, HttpHeaders customerHeaders) {
		PersistableOrder order = new PersistableOrder();
		order.setCurrency("USD");
		order.setCustomerAgreement(true);
		PersistablePayment payment = new PersistablePayment();
		payment.setPaymentModule("moneyorder");
		payment.setPaymentType(PaymentType.MONEYORDER.name());
		payment.setTransactionType(TransactionType.AUTHORIZECAPTURE.name());
		payment.setAmount(cartTotal);
		order.setPayment(payment);

		ResponseEntity<ReadableOrderConfirmation> checkout = testRestTemplate.postForEntity(
				"/api/v1/auth/cart/" + cartCode + "/checkout?store=DEFAULT",
				new HttpEntity<PersistableOrder>(order, customerHeaders), ReadableOrderConfirmation.class);

		if (checkout.getStatusCode().is2xxSuccessful() && checkout.getBody() != null
				&& checkout.getBody().getId() != null) {
			ResponseEntity<ReadableOrder> orderReadback = testRestTemplate.getForEntity(
					"/api/v1/auth/orders/" + checkout.getBody().getId() + "?store=DEFAULT", ReadableOrder.class);
			assertEquals(200, orderReadback.getStatusCodeValue());
			assertNotNull(orderReadback.getBody());
			return;
		}

		assertEquals(503, checkout.getStatusCodeValue());
	}

	private void exerciseServicesBasicAuthentication() {
		HttpHeaders validHeaders = new HttpHeaders();
		validHeaders.setBasicAuth("admin@shopizer.com", "password", StandardCharsets.UTF_8);
		ResponseEntity<String> valid = testRestTemplate.postForEntity("/services/private/system/optin",
				new HttpEntity<String>("{}", validHeaders), String.class);
		assertEquals(200, valid.getStatusCodeValue());

		HttpHeaders invalidHeaders = new HttpHeaders();
		invalidHeaders.setBasicAuth("admin@shopizer.com", "incorrect", StandardCharsets.UTF_8);
		ResponseEntity<String> invalid = testRestTemplate.postForEntity("/services/private/system/optin",
				new HttpEntity<String>("{}", invalidHeaders), String.class);
		assertEquals(401, invalid.getStatusCodeValue());
	}

	private void assertOperationalEndpoints() {
		ResponseEntity<Map> health = testRestTemplate.getForEntity("/actuator/health", Map.class);
		assertEquals(200, health.getStatusCodeValue());
		assertNotNull(health.getBody());
		assertEquals("UP", health.getBody().get("status"));

		ResponseEntity<String> apiDocs = testRestTemplate.getForEntity("/v2/api-docs", String.class);
		assertEquals(200, apiDocs.getStatusCodeValue());
	}

	private HttpHeaders bearerHeaders(String token) {
		HttpHeaders headers = new HttpHeaders();
		headers.setContentType(MediaType.APPLICATION_JSON);
		headers.set("Authorization", "Bearer " + token);
		return headers;
	}

	private PersistableShoppingCartItem cartItem(String sku, int quantity) {
		PersistableShoppingCartItem item = new PersistableShoppingCartItem();
		item.setProduct(sku);
		item.setQuantity(quantity);
		return item;
	}

}
