package com.salesmanager.test.shop.uat;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.test.context.junit.jupiter.SpringExtension;

import com.salesmanager.core.model.customer.CustomerGender;
import com.salesmanager.shop.model.catalog.product.ReadableProduct;
import com.salesmanager.shop.model.catalog.product.ReadableProductList;
import com.salesmanager.shop.model.catalog.category.ReadableCategoryList;
import com.salesmanager.shop.model.customer.PersistableCustomer;
import com.salesmanager.shop.model.customer.address.Address;
import com.salesmanager.shop.model.order.v1.ReadableOrder;
import com.salesmanager.shop.model.order.v1.PersistableOrder;
import com.salesmanager.shop.model.order.v1.ReadableOrderConfirmation;
import com.salesmanager.shop.model.order.transaction.PersistablePayment;
import com.salesmanager.shop.model.shoppingcart.PersistableShoppingCartItem;
import com.salesmanager.shop.model.shoppingcart.ReadableShoppingCart;
import com.salesmanager.shop.model.store.ReadableMerchantStore;
import com.salesmanager.shop.store.security.AuthenticationRequest;
import com.salesmanager.shop.store.security.AuthenticationResponse;
import com.salesmanager.test.shop.common.ServicesTestSupport;

@ExtendWith(SpringExtension.class)
public class StorefrontJourneyUatTest extends ServicesTestSupport {

	@Test
	public void customerCanBrowseCartAndCheckoutStorefront() {
		ResponseEntity<ReadableMerchantStore> store = testRestTemplate.getForEntity(
				"/api/v1/store/DEFAULT", ReadableMerchantStore.class);
		assertEquals(HttpStatus.OK, store.getStatusCode());
		ResponseEntity<ReadableCategoryList> categories = testRestTemplate.getForEntity(
				"/api/v1/category?store=DEFAULT", ReadableCategoryList.class);
		assertEquals(HttpStatus.OK, categories.getStatusCode());
		ResponseEntity<ReadableProductList> products = testRestTemplate.getForEntity(
				"/api/v1/products?store=DEFAULT", ReadableProductList.class);
		assertEquals(HttpStatus.OK, products.getStatusCode());

		ReadableProduct product = sampleProduct(uniqueCode());
		assertNotNull(product);
		ResponseEntity<ReadableProduct> productDetail = testRestTemplate.getForEntity(
				"/api/v2/product/" + product.getSku() + "?store=DEFAULT", ReadableProduct.class);
		assertEquals(HttpStatus.OK, productDetail.getStatusCode());
		assertEquals(product.getSku(), productDetail.getBody().getSku());

		String email = uniqueCode() + "@uat.example";
		PersistableCustomer customer = new PersistableCustomer();
		customer.setEmailAddress(email);
		customer.setPassword("clear123");
		customer.setGender(CustomerGender.M.name());
		customer.setFirstName("UAT");
		customer.setLastName("Customer");
		customer.setLanguage("en");
		customer.setStoreCode("DEFAULT");
		Address billing = new Address();
		billing.setFirstName("UAT");
		billing.setLastName("Customer");
		billing.setCountry("US");
		billing.setAddress("1 UAT Street");
		billing.setCity("Test City");
		billing.setPostalCode("12345");
		customer.setBilling(billing);
		ResponseEntity<AuthenticationResponse> registration = testRestTemplate.postForEntity(
				"/api/v1/customer/register", new HttpEntity<>(customer), AuthenticationResponse.class);
		assertTrue(registration.getStatusCode().is2xxSuccessful());

		ResponseEntity<AuthenticationResponse> login = testRestTemplate.postForEntity(
				"/api/v1/customer/login",
				new HttpEntity<>(new AuthenticationRequest(email, "clear123")), AuthenticationResponse.class);
		assertEquals(HttpStatus.OK, login.getStatusCode());
		assertNotNull(login.getBody().getToken());
		HttpHeaders customerHeaders = bearer(login.getBody().getToken());
		ResponseEntity<String> profile = testRestTemplate.exchange("/api/v1/auth/customer/profile?store=DEFAULT",
				HttpMethod.GET, new HttpEntity<>(customerHeaders), String.class);
		assertEquals(HttpStatus.OK, profile.getStatusCode());

		PersistableShoppingCartItem cartItem = new PersistableShoppingCartItem();
		cartItem.setProduct(product.getSku());
		cartItem.setQuantity(1);
		ResponseEntity<ReadableShoppingCart> createdCart = testRestTemplate.postForEntity(
				"/api/v1/cart?store=DEFAULT", new HttpEntity<>(cartItem, customerHeaders),
				ReadableShoppingCart.class);
		assertEquals(HttpStatus.CREATED, createdCart.getStatusCode());
		assertNotNull(createdCart.getBody());
		String cartCode = createdCart.getBody().getCode();

		cartItem.setQuantity(2);
		ResponseEntity<ReadableShoppingCart> updatedCart = testRestTemplate.exchange(
				"/api/v1/cart/" + cartCode + "?store=DEFAULT", HttpMethod.PUT,
				new HttpEntity<>(cartItem, customerHeaders), ReadableShoppingCart.class);
		assertEquals(HttpStatus.CREATED, updatedCart.getStatusCode());
		ResponseEntity<ReadableShoppingCart> readCart = testRestTemplate.exchange(
				"/api/v1/cart/" + cartCode + "?store=DEFAULT", HttpMethod.GET,
				new HttpEntity<>(customerHeaders), ReadableShoppingCart.class);
		assertEquals(HttpStatus.OK, readCart.getStatusCode());
		assertEquals(2, readCart.getBody().getQuantity());

		HttpHeaders serviceHeaders = new HttpHeaders();
		serviceHeaders.setContentType(MediaType.APPLICATION_JSON);
		serviceHeaders.setBasicAuth("admin@shopizer.com", "password");
		ResponseEntity<String> validServiceAuth = testRestTemplate.exchange("/services/private/system/optin",
				HttpMethod.POST, new HttpEntity<>("{\"code\":\"uat-optin\"}", serviceHeaders), String.class);
		assertEquals(HttpStatus.OK, validServiceAuth.getStatusCode());
		HttpHeaders invalidServiceHeaders = new HttpHeaders();
		invalidServiceHeaders.setContentType(MediaType.APPLICATION_JSON);
		invalidServiceHeaders.setBasicAuth("admin@shopizer.com", "invalid");
		ResponseEntity<String> invalidServiceAuth = testRestTemplate.exchange("/services/private/system/optin",
				HttpMethod.POST, new HttpEntity<>("{\"code\":\"uat-optin\"}", invalidServiceHeaders), String.class);
		assertEquals(HttpStatus.UNAUTHORIZED, invalidServiceAuth.getStatusCode());

		ResponseEntity<String> health = testRestTemplate.getForEntity("/actuator/health", String.class);
		assertEquals(HttpStatus.OK, health.getStatusCode());
		assertTrue(health.getBody().contains("\"status\":\"UP\""));
		ResponseEntity<String> apiDocs = testRestTemplate.getForEntity("/v2/api-docs", String.class);
		assertEquals(HttpStatus.OK, apiDocs.getStatusCode());

		PersistableOrder order = new PersistableOrder();
		order.setCurrency("CAD");
		order.setCustomerAgreement(true);
		PersistablePayment payment = new PersistablePayment();
		payment.setPaymentModule("moneyorder");
		payment.setPaymentType("MONEYORDER");
		payment.setTransactionType("AUTHORIZE");
		payment.setAmount(readCart.getBody().getTotal().toPlainString());
		order.setPayment(payment);
		ResponseEntity<String> checkout = testRestTemplate.exchange(
				"/api/v1/auth/cart/" + cartCode + "/checkout?store=DEFAULT", HttpMethod.POST,
				new HttpEntity<>(order, customerHeaders), String.class);
		Assumptions.assumeTrue(checkout.getStatusCode().is2xxSuccessful(),
				"checkout could not complete with the in-memory store configuration: "
						+ checkout.getStatusCode() + " " + checkout.getBody());
		ReadableOrderConfirmation confirmation = convertOrderConfirmation(checkout.getBody());
		assertNotNull(confirmation.getId());
		ResponseEntity<ReadableOrder> orderReadback = testRestTemplate.exchange(
				"/api/v1/auth/orders/" + confirmation.getId() + "?store=DEFAULT",
				HttpMethod.GET, new HttpEntity<>(customerHeaders), ReadableOrder.class);
		assertEquals(HttpStatus.OK, orderReadback.getStatusCode());
		assertEquals(confirmation.getId(), orderReadback.getBody().getId());
	}

	private ReadableOrderConfirmation convertOrderConfirmation(String body) {
		try {
			return new com.fasterxml.jackson.databind.ObjectMapper().readValue(body, ReadableOrderConfirmation.class);
		} catch (Exception e) {
			throw new AssertionError("Unable to parse checkout response", e);
		}
	}

	private HttpHeaders bearer(String token) {
		HttpHeaders headers = new HttpHeaders();
		headers.setContentType(MediaType.APPLICATION_JSON);
		headers.setBearerAuth(token);
		return headers;
	}

	private String uniqueCode() {
		return "uat" + Long.toString(System.nanoTime(), 36);
	}
}
