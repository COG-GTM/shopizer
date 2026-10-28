package com.salesmanager.test.shop.uat;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;
import static org.springframework.http.HttpMethod.GET;
import static org.springframework.http.HttpMethod.POST;
import static org.springframework.http.HttpMethod.PUT;
import static org.springframework.http.HttpStatus.OK;

import java.util.Map;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.SpringBootTest.WebEnvironment;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.test.context.junit4.SpringRunner;

import com.salesmanager.core.business.constants.Constants;
import com.salesmanager.core.model.customer.CustomerGender;
import com.salesmanager.shop.application.ShopApplication;
import com.salesmanager.shop.model.catalog.category.ReadableCategoryList;
import com.salesmanager.shop.model.catalog.product.ReadableProduct;
import com.salesmanager.shop.model.catalog.product.ReadableProductList;
import com.salesmanager.shop.model.customer.PersistableCustomer;
import com.salesmanager.shop.model.customer.ReadableCustomer;
import com.salesmanager.shop.model.customer.address.Address;
import com.salesmanager.shop.model.entity.Entity;
import com.salesmanager.shop.model.shoppingcart.PersistableShoppingCartItem;
import com.salesmanager.shop.model.shoppingcart.ReadableShoppingCart;
import com.salesmanager.shop.model.store.ReadableMerchantStore;
import com.salesmanager.shop.model.user.ReadableUser;
import com.salesmanager.shop.store.security.AuthenticationRequest;
import com.salesmanager.shop.store.security.AuthenticationResponse;
import com.salesmanager.test.shop.common.ServicesTestSupport;

@SpringBootTest(classes = ShopApplication.class, webEnvironment = WebEnvironment.RANDOM_PORT)
@RunWith(SpringRunner.class)
public class AdminAndStorefrontJourneysUatTest extends ServicesTestSupport {

	@Test
	public void adminCustomerStorefrontCartAndOrderJourneys() {
		HttpHeaders adminHeaders = login("admin@shopizer.com", "password");

		ResponseEntity<ReadableUser> adminUser = testRestTemplate.exchange("/api/v1/private/users/1", GET,
				new HttpEntity<>(adminHeaders), ReadableUser.class);
		assertEquals(OK, adminUser.getStatusCode());

		ResponseEntity<String> anonymousDenied = testRestTemplate.exchange("/api/v1/private/users/1", GET,
				HttpEntity.EMPTY, String.class);
		assertTrue(isAuthorizationDenied(anonymousDenied.getStatusCode().value()));
		String suffix = Long.toString(System.nanoTime(), 36);
		String customerEmail = "uat-customer-" + suffix + "@example.test";
		String customerPassword = "clear123";
		PersistableCustomer customer = new PersistableCustomer();
		customer.setEmailAddress(customerEmail);
		customer.setPassword(customerPassword);
		customer.setGender(CustomerGender.M.name());
		customer.setLanguage("en");
		customer.setStoreCode(Constants.DEFAULT_STORE);
		Address billing = new Address();
		billing.setFirstName("UAT");
		billing.setLastName("Customer");
		billing.setCountry("BE");
		customer.setBilling(billing);

		ResponseEntity<PersistableCustomer> registered = testRestTemplate.postForEntity("/api/v1/customer/register",
				new HttpEntity<>(customer, adminHeaders), PersistableCustomer.class);
		assertEquals(OK, registered.getStatusCode());
		HttpHeaders customerHeaders = loginCustomer(customerEmail, customerPassword);
		ResponseEntity<ReadableCustomer> profile = testRestTemplate.exchange("/api/v1/auth/customer/profile", GET,
				new HttpEntity<>(customerHeaders), ReadableCustomer.class);
		assertEquals(OK, profile.getStatusCode());

		ResponseEntity<ReadableMerchantStore> store = testRestTemplate.exchange("/api/v1/store/DEFAULT", GET,
				new HttpEntity<>(adminHeaders), ReadableMerchantStore.class);
		assertEquals(OK, store.getStatusCode());
		ResponseEntity<ReadableCategoryList> categories = testRestTemplate.exchange(
				"/api/v1/category?store=DEFAULT", GET, new HttpEntity<>(adminHeaders), ReadableCategoryList.class);
		assertEquals(OK, categories.getStatusCode());
		ResponseEntity<ReadableProductList> products = testRestTemplate.exchange(
				"/api/v1/products?store=DEFAULT&count=100", GET,
				new HttpEntity<>(adminHeaders), ReadableProductList.class);
		assertEquals(OK, products.getStatusCode());
		ReadableProduct product;
		if (products.getBody() == null || products.getBody().getProducts().isEmpty()) {
			product = sampleProduct("uat-product-" + suffix);
		} else {
			product = products.getBody().getProducts().get(0);
		}
		assertNotNull(product);
		ResponseEntity<ReadableProduct> productDetail = testRestTemplate.exchange(
				"/api/v2/product/" + product.getSku() + "?store=DEFAULT", GET,
				new HttpEntity<>(customerHeaders), ReadableProduct.class);
		assertEquals(OK, productDetail.getStatusCode());

		PersistableShoppingCartItem item = new PersistableShoppingCartItem();
		item.setProduct(product.getSku());
		item.setQuantity(1);
		ResponseEntity<ReadableShoppingCart> createdCart = testRestTemplate.postForEntity(
				"/api/v1/cart?store=DEFAULT", new HttpEntity<>(item, customerHeaders), ReadableShoppingCart.class);
		assertEquals(org.springframework.http.HttpStatus.CREATED, createdCart.getStatusCode());
		assertNotNull(createdCart.getBody());
		String cartCode = createdCart.getBody().getCode();

		item.setQuantity(2);
		ResponseEntity<ReadableShoppingCart> updatedCart = testRestTemplate.exchange(
				"/api/v1/cart/" + cartCode + "?store=DEFAULT", PUT,
				new HttpEntity<>(item, customerHeaders), ReadableShoppingCart.class);
		assertEquals(org.springframework.http.HttpStatus.CREATED, updatedCart.getStatusCode());
		ResponseEntity<ReadableShoppingCart> cartRead = testRestTemplate.exchange(
				"/api/v1/cart/" + cartCode + "?store=DEFAULT", GET,
				new HttpEntity<>(customerHeaders), ReadableShoppingCart.class);
		assertEquals(OK, cartRead.getStatusCode());
		assertNotNull(cartRead.getBody().getTotal());

		HttpHeaders basic = new HttpHeaders();
		basic.setBasicAuth("admin@shopizer.com", "password");
		basic.setContentType(MediaType.TEXT_PLAIN);
		ResponseEntity<String> validBasicAuth = testRestTemplate.postForEntity("/services/private/system/optin",
				new HttpEntity<>("{}", basic), String.class);
		assertEquals(OK, validBasicAuth.getStatusCode());
		HttpHeaders invalidBasic = new HttpHeaders();
		invalidBasic.setBasicAuth("admin@shopizer.com", "invalid");
		invalidBasic.setContentType(MediaType.TEXT_PLAIN);
		ResponseEntity<String> invalidBasicAuth = testRestTemplate.postForEntity("/services/private/system/optin",
				new HttpEntity<>("{}", invalidBasic), String.class);
		assertTrue(isAuthorizationDenied(invalidBasicAuth.getStatusCode().value()));

		ResponseEntity<Map> health = testRestTemplate.getForEntity("/actuator/health", Map.class);
		assertEquals(OK, health.getStatusCode());
		assertEquals("UP", health.getBody().get("status"));
		ResponseEntity<String> apiDocs = testRestTemplate.getForEntity("/v2/api-docs", String.class);
		assertEquals(OK, apiDocs.getStatusCode());
	}

	private HttpHeaders login(String username, String password) {
		ResponseEntity<AuthenticationResponse> response = testRestTemplate.postForEntity("/api/v1/private/login",
				new HttpEntity<>(new AuthenticationRequest(username, password)), AuthenticationResponse.class);
		assertEquals(OK, response.getStatusCode());
		assertNotNull(response.getBody());
		HttpHeaders headers = new HttpHeaders();
		headers.setContentType(MediaType.APPLICATION_JSON);
		headers.setBearerAuth(response.getBody().getToken());
		return headers;
	}

	private HttpHeaders loginCustomer(String username, String password) {
		ResponseEntity<AuthenticationResponse> response = testRestTemplate.postForEntity("/api/v1/customer/login",
				new HttpEntity<>(new AuthenticationRequest(username, password)), AuthenticationResponse.class);
		assertEquals(OK, response.getStatusCode());
		assertNotNull(response.getBody());
		HttpHeaders headers = new HttpHeaders();
		headers.setContentType(MediaType.APPLICATION_JSON);
		headers.setBearerAuth(response.getBody().getToken());
		return headers;
	}

	private static boolean isAuthorizationDenied(int status) {
		return status == 401 || status == 403;
	}
}
