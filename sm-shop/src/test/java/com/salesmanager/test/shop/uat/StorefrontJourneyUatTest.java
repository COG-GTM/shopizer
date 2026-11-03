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
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.test.context.junit4.SpringRunner;

import com.salesmanager.shop.application.ShopApplication;
import com.salesmanager.shop.model.catalog.product.ReadableProduct;
import com.salesmanager.shop.model.catalog.product.ReadableProductList;
import com.salesmanager.shop.model.customer.PersistableCustomer;
import com.salesmanager.shop.model.customer.address.Address;
import com.salesmanager.shop.model.shoppingcart.PersistableShoppingCartItem;
import com.salesmanager.shop.model.shoppingcart.ReadableShoppingCart;
import com.salesmanager.shop.store.security.AuthenticationRequest;
import com.salesmanager.shop.store.security.AuthenticationResponse;
import com.salesmanager.test.shop.common.ServicesTestSupport;

@SpringBootTest(classes = ShopApplication.class, webEnvironment = WebEnvironment.RANDOM_PORT)
@RunWith(SpringRunner.class)
public class StorefrontJourneyUatTest extends ServicesTestSupport {

	@Test
	public void adminCustomerBrowseCartServicesAndOperationalEndpointsWork() {
		ResponseEntity<AuthenticationResponse> adminLogin = testRestTemplate.postForEntity("/api/v1/private/login",
				new AuthenticationRequest("admin@shopizer.com", "password"), AuthenticationResponse.class);
		assertEquals(HttpStatus.OK, adminLogin.getStatusCode());
		assertNotNull(adminLogin.getBody().getToken());

		HttpHeaders adminHeaders = bearer(adminLogin.getBody().getToken());
		assertTrue(testRestTemplate.exchange("/api/v1/private/customers", org.springframework.http.HttpMethod.GET,
				new HttpEntity<>(adminHeaders), String.class).getStatusCode().is2xxSuccessful());
		ResponseEntity<String> anonymousAdminResponse = testRestTemplate.getForEntity(
				"/api/v1/private/customers", String.class);
		assertTrue(anonymousAdminResponse.getStatusCode().value() == 401
				|| anonymousAdminResponse.getStatusCode().value() == 403);
		HttpHeaders badAdminHeaders = bearer("bad");
		ResponseEntity<String> badAdminResponse = testRestTemplate.exchange("/api/v1/private/customers",
				org.springframework.http.HttpMethod.GET, new HttpEntity<>(badAdminHeaders), String.class);
		assertFalse(badAdminResponse.getStatusCode().is2xxSuccessful());

		String username = "uat-" + System.nanoTime() + "@example.test";
		PersistableCustomer customer = new PersistableCustomer();
		customer.setEmailAddress(username);
		customer.setPassword("UatPass1!");
		customer.setGender("M");
		customer.setLanguage("en");
		customer.setStoreCode("DEFAULT");
		Address billing = new Address();
		billing.setFirstName("Uat");
		billing.setLastName("Shopper");
		billing.setCountry("BE");
		customer.setBilling(billing);
		assertEquals(HttpStatus.OK,
				testRestTemplate.postForEntity("/api/v1/customer/register?store=DEFAULT", customer, String.class)
						.getStatusCode());

		ResponseEntity<AuthenticationResponse> customerLogin = testRestTemplate.postForEntity(
				"/api/v1/customer/login?store=DEFAULT",
				new AuthenticationRequest(username, "UatPass1!"), AuthenticationResponse.class);
		assertEquals(HttpStatus.OK, customerLogin.getStatusCode());
		HttpHeaders customerHeaders = bearer(customerLogin.getBody().getToken());
		assertTrue(testRestTemplate.exchange("/api/v1/auth/customer/profile?store=DEFAULT",
				org.springframework.http.HttpMethod.GET, new HttpEntity<>(customerHeaders), String.class)
				.getStatusCode().is2xxSuccessful());

		assertTrue(testRestTemplate.getForEntity("/api/v1/store/DEFAULT", String.class)
				.getStatusCode().is2xxSuccessful());
		assertTrue(testRestTemplate.getForEntity("/api/v1/category?store=DEFAULT", String.class)
				.getStatusCode().is2xxSuccessful());
		ResponseEntity<ReadableProductList> products = testRestTemplate.getForEntity(
				"/api/v1/products?store=DEFAULT", ReadableProductList.class);
		assertTrue(products.getStatusCode().is2xxSuccessful());
		assertNotNull(products.getBody());

		ReadableProduct firstProduct = products.getBody().getProducts().isEmpty()
				? sampleProduct("uat-product-" + System.nanoTime())
				: products.getBody().getProducts().get(0);
		ResponseEntity<String> productDetail = testRestTemplate.getForEntity(
				"/api/v2/product/" + firstProduct.getSku() + "?store=DEFAULT", String.class);
		assertTrue(productDetail.getStatusCode().is2xxSuccessful());

		PersistableShoppingCartItem item = new PersistableShoppingCartItem();
		item.setProduct(firstProduct.getSku());
		item.setQuantity(1);
		ResponseEntity<ReadableShoppingCart> createdCart = testRestTemplate.postForEntity(
				"/api/v1/cart?store=DEFAULT", item, ReadableShoppingCart.class);
		assertEquals(HttpStatus.CREATED, createdCart.getStatusCode());
		String cartCode = createdCart.getBody().getCode();
		item.setQuantity(2);
		assertTrue(testRestTemplate.exchange("/api/v1/cart/" + cartCode + "?store=DEFAULT",
				org.springframework.http.HttpMethod.PUT, new HttpEntity<>(item), String.class)
				.getStatusCode().is2xxSuccessful());
		assertTrue(testRestTemplate.getForEntity("/api/v1/cart/" + cartCode + "?store=DEFAULT", String.class)
				.getStatusCode().is2xxSuccessful());

		HttpHeaders serviceHeaders = new HttpHeaders();
		serviceHeaders.setBasicAuth("admin@shopizer.com", "password", StandardCharsets.UTF_8);
		serviceHeaders.setContentType(MediaType.TEXT_PLAIN);
		assertTrue(testRestTemplate.postForEntity("/services/private/system/optin",
				new HttpEntity<>("{\"enabled\":true}", serviceHeaders), String.class)
				.getStatusCode().is2xxSuccessful());
		serviceHeaders.setBasicAuth("admin@shopizer.com", "wrong", StandardCharsets.UTF_8);
		ResponseEntity<String> deniedService = testRestTemplate.postForEntity("/services/private/system/optin",
				new HttpEntity<>("{\"enabled\":true}", serviceHeaders), String.class);
		assertEquals(HttpStatus.UNAUTHORIZED, deniedService.getStatusCode());

		ResponseEntity<String> health = testRestTemplate.getForEntity("/actuator/health", String.class);
		assertTrue(health.getStatusCode().is2xxSuccessful());
		assertTrue(health.getBody().contains("\"status\":\"UP\""));
		assertTrue(testRestTemplate.getForEntity("/v2/api-docs", String.class).getStatusCode().is2xxSuccessful());
	}

	private HttpHeaders bearer(String token) {
		HttpHeaders headers = new HttpHeaders();
		headers.setContentType(new MediaType("application", "json", StandardCharsets.UTF_8));
		headers.setBearerAuth(token);
		return headers;
	}
}
