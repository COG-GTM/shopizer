package com.salesmanager.test.shop.uat;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.SpringBootTest.WebEnvironment;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.test.context.junit4.SpringRunner;

import com.salesmanager.core.business.constants.Constants;
import com.salesmanager.core.model.customer.CustomerGender;
import com.salesmanager.shop.application.ShopApplication;
import com.salesmanager.shop.model.catalog.product.ReadableProduct;
import com.salesmanager.shop.model.customer.PersistableCustomer;
import com.salesmanager.shop.model.customer.ReadableCustomer;
import com.salesmanager.shop.model.customer.address.Address;
import com.salesmanager.shop.model.shoppingcart.PersistableShoppingCartItem;
import com.salesmanager.shop.model.shoppingcart.ReadableShoppingCart;
import com.salesmanager.shop.store.security.AuthenticationRequest;
import com.salesmanager.shop.store.security.AuthenticationResponse;
import com.salesmanager.test.shop.common.ServicesTestSupport;

@SpringBootTest(classes = ShopApplication.class, webEnvironment = WebEnvironment.RANDOM_PORT)
@RunWith(SpringRunner.class)
public class StorefrontJourneyUatTest extends ServicesTestSupport {

	private static final String CUSTOMER_PASSWORD = "Password1";

	@Test
	public void storefrontCustomerCartAndProfileJourney() {
		assertEquals(HttpStatus.OK,
				testRestTemplate.getForEntity("/api/v1/store/DEFAULT", String.class).getStatusCode());
		assertEquals(HttpStatus.OK,
				testRestTemplate.getForEntity("/api/v1/category?store=DEFAULT", String.class).getStatusCode());

		ReadableProduct product = sampleProduct("uat-storefront-" + System.nanoTime());
		ResponseEntity<String> productList = testRestTemplate.getForEntity("/api/v1/products?store=DEFAULT",
				String.class);
		assertEquals(HttpStatus.OK, productList.getStatusCode());
		assertTrue(productList.getBody().contains(product.getSku()));
		ResponseEntity<ReadableProduct> productDetail = testRestTemplate.getForEntity(
				"/api/v2/product/" + product.getSku(), ReadableProduct.class);
		assertEquals(HttpStatus.OK, productDetail.getStatusCode());
		assertNotNull(productDetail.getBody());

		PersistableShoppingCartItem item = new PersistableShoppingCartItem();
		item.setProduct(product.getSku());
		item.setQuantity(1);
		ResponseEntity<ReadableShoppingCart> createdCart = testRestTemplate.postForEntity("/api/v1/cart",
				new HttpEntity<>(item), ReadableShoppingCart.class);
		assertEquals(HttpStatus.CREATED, createdCart.getStatusCode());
		assertNotNull(createdCart.getBody());
		String cartCode = createdCart.getBody().getCode();

		item.setQuantity(2);
		ResponseEntity<ReadableShoppingCart> updatedCart = testRestTemplate.exchange("/api/v1/cart/" + cartCode,
				HttpMethod.PUT, new HttpEntity<>(item), ReadableShoppingCart.class);
		assertEquals(HttpStatus.CREATED, updatedCart.getStatusCode());
		ResponseEntity<ReadableShoppingCart> readCart = testRestTemplate.getForEntity("/api/v1/cart/" + cartCode,
				ReadableShoppingCart.class);
		assertEquals(HttpStatus.OK, readCart.getStatusCode());
		assertNotNull(readCart.getBody());
		assertTrue(readCart.getBody().getQuantity() > 0);

		String email = "storefront-uat-" + System.nanoTime() + "@test.com";
		PersistableCustomer customer = new PersistableCustomer();
		customer.setEmailAddress(email);
		customer.setPassword(CUSTOMER_PASSWORD);
		customer.setRepeatPassword(CUSTOMER_PASSWORD);
		customer.setGender(CustomerGender.M.name());
		customer.setLanguage("en");
		customer.setStoreCode(Constants.DEFAULT_STORE);
		Address billing = new Address();
		billing.setFirstName("Storefront");
		billing.setLastName("UAT");
		billing.setCountry("BE");
		customer.setBilling(billing);
		ResponseEntity<String> registeredCustomer = testRestTemplate.postForEntity("/api/v1/customer/register",
				new HttpEntity<>(customer, getHeader()), String.class);
		assertTrue(registeredCustomer.getStatusCode().is2xxSuccessful());

		AuthenticationRequest credentials = new AuthenticationRequest(email, CUSTOMER_PASSWORD);
		ResponseEntity<AuthenticationResponse> login = testRestTemplate.postForEntity("/api/v1/customer/login",
				new HttpEntity<>(credentials), AuthenticationResponse.class);
		assertEquals(HttpStatus.OK, login.getStatusCode());
		assertNotNull(login.getBody());
		HttpHeaders customerHeaders = new HttpHeaders();
		customerHeaders.set("Authorization", "Bearer " + login.getBody().getToken());
		ResponseEntity<ReadableCustomer> profile = testRestTemplate.exchange("/api/v1/auth/customer/profile",
				HttpMethod.GET, new HttpEntity<>(customerHeaders), ReadableCustomer.class);
		assertEquals(HttpStatus.OK, profile.getStatusCode());
		assertNotNull(profile.getBody());
	}
}
