package com.salesmanager.test.shop.uat;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import java.nio.charset.StandardCharsets;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.SpringBootTest.WebEnvironment;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.test.context.junit4.SpringRunner;

import com.salesmanager.core.business.constants.Constants;
import com.salesmanager.core.model.customer.CustomerGender;
import com.salesmanager.shop.application.ShopApplication;
import com.salesmanager.shop.model.catalog.product.ReadableProduct;
import com.salesmanager.shop.model.catalog.product.ReadableProductList;
import com.salesmanager.shop.model.customer.PersistableCustomer;
import com.salesmanager.shop.model.customer.ReadableCustomer;
import com.salesmanager.shop.model.customer.address.Address;
import com.salesmanager.shop.model.shoppingcart.PersistableShoppingCartItem;
import com.salesmanager.shop.model.shoppingcart.ReadableShoppingCart;
import com.salesmanager.shop.model.store.ReadableMerchantStore;
import com.salesmanager.shop.model.user.ReadableUserList;
import com.salesmanager.shop.store.security.AuthenticationRequest;
import com.salesmanager.shop.store.security.AuthenticationResponse;
import com.salesmanager.test.shop.common.ServicesTestSupport;

@RunWith(SpringRunner.class)
@SpringBootTest(classes = ShopApplication.class, webEnvironment = WebEnvironment.RANDOM_PORT)
public class StorefrontAdminSmokeJourneyUATTest extends ServicesTestSupport {

	@Test
	public void exercisesStorefrontAuthenticationCartAndSystemContracts() {
		HttpHeaders adminHeaders = getHeader();
		ResponseEntity<ReadableUserList> adminUsers = testRestTemplate.exchange(
				"/api/v1/private/users?store=DEFAULT", HttpMethod.GET,
				new HttpEntity<>(adminHeaders), ReadableUserList.class);
		assertEquals(HttpStatus.OK, adminUsers.getStatusCode());

		ResponseEntity<String> anonymousPrivate = testRestTemplate.exchange(
				"/api/v1/private/users?store=DEFAULT", HttpMethod.GET, HttpEntity.EMPTY, String.class);
		assertTrue(isUnauthorized(anonymousPrivate.getStatusCode()));

		HttpHeaders invalidHeaders = new HttpHeaders();
		invalidHeaders.setBearerAuth("not-a-valid-token");
		ResponseEntity<String> invalidPrivate = testRestTemplate.exchange(
				"/api/v1/private/users?store=DEFAULT", HttpMethod.GET,
				new HttpEntity<>(invalidHeaders), String.class);
		assertFalse("Malformed token was accepted with " + invalidPrivate.getStatusCode(),
				invalidPrivate.getStatusCode().is2xxSuccessful());

		String customerEmail = "authz-uat-customer-" + System.currentTimeMillis() + "@test.com";
		PersistableCustomer customer = new PersistableCustomer();
		customer.setEmailAddress(customerEmail);
		customer.setPassword("Password1");
		customer.setRepeatPassword("Password1");
		customer.setGender(CustomerGender.M.name());
		customer.setLanguage("en");
		customer.setStoreCode(Constants.DEFAULT_STORE);
		Address billing = new Address();
		billing.setFirstName("Authz");
		billing.setLastName("Customer");
		billing.setCountry("US");
		customer.setBilling(billing);

		ResponseEntity<AuthenticationResponse> registration = testRestTemplate.postForEntity(
				"/api/v1/customer/register", new HttpEntity<>(customer), AuthenticationResponse.class);
		assertTrue(registration.getStatusCode().is2xxSuccessful());
		assertNotNull(registration.getBody());

		ResponseEntity<AuthenticationResponse> customerLogin = testRestTemplate.postForEntity(
				"/api/v1/customer/login",
				new HttpEntity<>(new AuthenticationRequest(customerEmail, "Password1")),
				AuthenticationResponse.class);
		assertEquals(HttpStatus.OK, customerLogin.getStatusCode());
		assertNotNull(customerLogin.getBody().getToken());

		HttpHeaders customerHeaders = new HttpHeaders();
		customerHeaders.setBearerAuth(customerLogin.getBody().getToken());
		customerHeaders.setContentType(MediaType.APPLICATION_JSON);
		ResponseEntity<ReadableCustomer> customerProfile = testRestTemplate.exchange(
				"/api/v1/auth/customer/profile?store=DEFAULT", HttpMethod.GET,
				new HttpEntity<>(customerHeaders), ReadableCustomer.class);
		assertEquals(HttpStatus.OK, customerProfile.getStatusCode());

		ResponseEntity<ReadableMerchantStore> store = testRestTemplate.exchange(
				"/api/v1/store/DEFAULT", HttpMethod.GET, HttpEntity.EMPTY, ReadableMerchantStore.class);
		assertEquals(HttpStatus.OK, store.getStatusCode());

		ResponseEntity<String> categories = testRestTemplate.exchange(
				"/api/v1/category?store=DEFAULT", HttpMethod.GET, HttpEntity.EMPTY, String.class);
		assertEquals(HttpStatus.OK, categories.getStatusCode());

		ResponseEntity<ReadableProductList> productResponse = testRestTemplate.exchange(
				"/api/v1/products?store=DEFAULT", HttpMethod.GET, HttpEntity.EMPTY, ReadableProductList.class);
		assertEquals(HttpStatus.OK, productResponse.getStatusCode());
		ReadableProduct product;
		if (productResponse.getBody() != null && !productResponse.getBody().getProducts().isEmpty()) {
			product = productResponse.getBody().getProducts().get(0);
		} else {
			product = sampleProduct("authz-uat-" + System.currentTimeMillis());
		}
		assertNotNull(product.getId());
		ResponseEntity<ReadableProduct> productDetail = testRestTemplate.exchange(
				"/api/v2/product/" + product.getSku() + "?store=DEFAULT", HttpMethod.GET,
				HttpEntity.EMPTY, ReadableProduct.class);
		assertEquals(HttpStatus.OK, productDetail.getStatusCode());

		PersistableShoppingCartItem item = new PersistableShoppingCartItem();
		item.setProduct(product.getSku());
		item.setQuantity(1);
		ResponseEntity<ReadableShoppingCart> cartCreate = testRestTemplate.postForEntity(
				"/api/v1/cart?store=DEFAULT", new HttpEntity<>(item), ReadableShoppingCart.class);
		assertEquals(HttpStatus.CREATED, cartCreate.getStatusCode());
		assertNotNull(cartCreate.getBody());

		item.setQuantity(2);
		ResponseEntity<ReadableShoppingCart> cartUpdate = testRestTemplate.exchange(
				"/api/v1/cart/" + cartCreate.getBody().getCode() + "?store=DEFAULT",
				HttpMethod.PUT, new HttpEntity<>(item), ReadableShoppingCart.class);
		assertEquals(HttpStatus.CREATED, cartUpdate.getStatusCode());
		ResponseEntity<ReadableShoppingCart> cartRead = testRestTemplate.exchange(
				"/api/v1/cart/" + cartCreate.getBody().getCode() + "?store=DEFAULT",
				HttpMethod.GET, HttpEntity.EMPTY, ReadableShoppingCart.class);
		assertEquals(HttpStatus.OK, cartRead.getStatusCode());
		assertNotNull(cartRead.getBody());

		ResponseEntity<String> orderList = testRestTemplate.exchange(
				"/api/v1/auth/orders?store=DEFAULT", HttpMethod.GET,
				new HttpEntity<>(customerHeaders), String.class);
		assertEquals(HttpStatus.OK, orderList.getStatusCode());

		HttpHeaders badBasic = new HttpHeaders();
		badBasic.setBasicAuth("admin@shopizer.com", "wrong-password", StandardCharsets.UTF_8);
		ResponseEntity<String> invalidServicesAuth = testRestTemplate.exchange(
				"/services/private/DEFAULT/product/optionValue", HttpMethod.GET,
				new HttpEntity<>(badBasic), String.class);
		assertEquals(HttpStatus.UNAUTHORIZED, invalidServicesAuth.getStatusCode());

		HttpHeaders validBasic = new HttpHeaders();
		validBasic.setBasicAuth("admin@shopizer.com", "password", StandardCharsets.UTF_8);
		ResponseEntity<String> validServicesAuth = testRestTemplate.exchange(
				"/services/private/DEFAULT/product/optionValue", HttpMethod.GET,
				new HttpEntity<>(validBasic), String.class);
		assertTrue("Basic-auth request was rejected: " + validServicesAuth.getStatusCode(),
				validServicesAuth.getStatusCode() != HttpStatus.UNAUTHORIZED
						&& validServicesAuth.getStatusCode() != HttpStatus.FORBIDDEN);

		ResponseEntity<String> health = testRestTemplate.exchange(
				"/actuator/health", HttpMethod.GET, HttpEntity.EMPTY, String.class);
		assertEquals(HttpStatus.OK, health.getStatusCode());
		assertTrue(health.getBody().contains("\"status\":\"UP\""));

		ResponseEntity<String> apiDocs = testRestTemplate.exchange(
				"/v2/api-docs", HttpMethod.GET, HttpEntity.EMPTY, String.class);
		assertEquals(HttpStatus.OK, apiDocs.getStatusCode());
	}

	private static boolean isUnauthorized(HttpStatus status) {
		return status == HttpStatus.UNAUTHORIZED || status == HttpStatus.FORBIDDEN;
	}
}
