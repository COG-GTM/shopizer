package com.salesmanager.test.shop.uat;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

import com.salesmanager.core.business.constants.Constants;
import com.salesmanager.core.model.customer.CustomerGender;
import com.salesmanager.shop.model.catalog.product.ReadableProduct;
import com.salesmanager.shop.model.customer.PersistableCustomer;
import com.salesmanager.shop.model.customer.ReadableCustomer;
import com.salesmanager.shop.model.customer.address.Address;
import com.salesmanager.shop.model.shoppingcart.PersistableShoppingCartItem;
import com.salesmanager.shop.model.shoppingcart.ReadableShoppingCart;
import com.salesmanager.shop.store.security.AuthenticationRequest;
import com.salesmanager.shop.store.security.AuthenticationResponse;
import com.salesmanager.test.shop.common.ServicesTestSupport;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

class CustomerJourneyUatTest extends ServicesTestSupport {

	@Test
	void customerRegistersLogsInBrowsesProfileAndUpdatesCart() {
		String suffix = UUID.randomUUID().toString().replace("-", "");
		String email = "uat-" + suffix + "@shopizer.com";
		CustomerSession customer = registerAndLogin(email, "clear123");
		ResponseEntity<ReadableCustomer> profile = testRestTemplate.exchange(
				"/api/v1/auth/customer/profile", HttpMethod.GET,
				new HttpEntity<>(customer.headers), ReadableCustomer.class);
		assertEquals(HttpStatus.OK, profile.getStatusCode());
		assertEquals(email, profile.getBody().getEmailAddress());

		ReadableProduct firstProduct = sampleProduct("uat-cart-a-" + suffix);
		ReadableProduct secondProduct = sampleProduct("uat-cart-b-" + suffix);
		PersistableShoppingCartItem firstItem = cartItem(firstProduct.getSku(), 1);
		ResponseEntity<ReadableShoppingCart> createdCart = testRestTemplate.postForEntity(
				"/api/v1/cart", new HttpEntity<>(firstItem, customer.headers),
				ReadableShoppingCart.class);
		assertEquals(HttpStatus.CREATED, createdCart.getStatusCode());

		PersistableShoppingCartItem firstUpdate = cartItem(firstProduct.getSku(), 2);
		ResponseEntity<ReadableShoppingCart> updatedCart = testRestTemplate.exchange(
				"/api/v1/cart/" + createdCart.getBody().getCode(), HttpMethod.PUT,
				new HttpEntity<>(firstUpdate, customer.headers), ReadableShoppingCart.class);
		assertEquals(HttpStatus.CREATED, updatedCart.getStatusCode());

		PersistableShoppingCartItem secondUpdate = cartItem(secondProduct.getSku(), 1);
		ResponseEntity<ReadableShoppingCart> cartWithTwoItems = testRestTemplate.exchange(
				"/api/v1/cart/" + createdCart.getBody().getCode(), HttpMethod.PUT,
				new HttpEntity<>(secondUpdate, customer.headers), ReadableShoppingCart.class);
		assertEquals(HttpStatus.CREATED, cartWithTwoItems.getStatusCode());

		ResponseEntity<ReadableShoppingCart> readCart = testRestTemplate.exchange(
				"/api/v1/cart/" + createdCart.getBody().getCode(), HttpMethod.GET,
				new HttpEntity<>(customer.headers), ReadableShoppingCart.class);
		assertAll(
				() -> assertEquals(HttpStatus.OK, readCart.getStatusCode()),
				() -> assertEquals(2, readCart.getBody().getProducts().size()),
				() -> assertEquals(3, readCart.getBody().getQuantity()));
	}

	private CustomerSession registerAndLogin(String email, String password) {
		PersistableCustomer customer = new PersistableCustomer();
		customer.setEmailAddress(email);
		customer.setPassword(password);
		customer.setGender(CustomerGender.M.name());
		customer.setLanguage("en");
		customer.setStoreCode(Constants.DEFAULT_STORE);
		Address billing = new Address();
		billing.setFirstName("UAT");
		billing.setLastName("Customer");
		billing.setCountry("BE");
		customer.setBilling(billing);

		ResponseEntity<PersistableCustomer> registration = testRestTemplate.postForEntity(
				"/api/v1/customer/register", new HttpEntity<>(customer, getHeader()),
				PersistableCustomer.class);
		assertEquals(HttpStatus.OK, registration.getStatusCode());

		ResponseEntity<AuthenticationResponse> login = testRestTemplate.postForEntity(
				"/api/v1/customer/login",
				new HttpEntity<>(new AuthenticationRequest(email, password)),
				AuthenticationResponse.class);
		assertEquals(HttpStatus.OK, login.getStatusCode());
		assertNotNull(login.getBody());

		HttpHeaders headers = new HttpHeaders();
		headers.setBearerAuth(login.getBody().getToken());
		return new CustomerSession(headers);
	}

	private static PersistableShoppingCartItem cartItem(String sku, int quantity) {
		PersistableShoppingCartItem item = new PersistableShoppingCartItem();
		item.setProduct(sku);
		item.setQuantity(quantity);
		return item;
	}

	private static class CustomerSession {
		private final HttpHeaders headers;

		private CustomerSession(HttpHeaders headers) {
			this.headers = headers;
		}
	}
}
