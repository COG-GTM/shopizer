package com.salesmanager.test.shop.uat;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.SpringBootTest.WebEnvironment;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.test.context.junit.jupiter.SpringExtension;

import com.salesmanager.core.business.constants.Constants;
import com.salesmanager.core.model.customer.CustomerGender;
import com.salesmanager.shop.application.ShopApplication;
import com.salesmanager.shop.model.customer.PersistableCustomer;
import com.salesmanager.shop.model.customer.address.Address;
import com.salesmanager.shop.model.shoppingcart.PersistableShoppingCartItem;
import com.salesmanager.shop.model.shoppingcart.ReadableShoppingCart;
import com.salesmanager.shop.store.security.AuthenticationRequest;
import com.salesmanager.shop.store.security.AuthenticationResponse;
import com.salesmanager.test.shop.common.ServicesTestSupport;

@SpringBootTest(classes = ShopApplication.class, webEnvironment = WebEnvironment.RANDOM_PORT)
@ExtendWith(SpringExtension.class)
class JourneysUatTest extends ServicesTestSupport {

	@Test
	void adminLoginAndMissingTokenHandling() {
		AuthenticationResponse login = testRestTemplate.postForObject("/api/v1/private/login",
				new HttpEntity<>(new AuthenticationRequest("admin@shopizer.com", "password")),
				AuthenticationResponse.class);
		assertNotNull(login);
		assertNotNull(login.getToken());
		assertEquals(HttpStatus.OK, testRestTemplate.exchange("/api/v1/private/stores",
				HttpMethod.GET, new HttpEntity<>(getHeader()), String.class).getStatusCode());
		assertTrue(isUnauthorized(testRestTemplate.getForEntity("/api/v1/private/stores", String.class)
				.getStatusCode()));
	}

	@Test
	void customerCanRegisterLoginAndReadAuthenticatedProfile() {
		String email = "uat" + Long.toString(System.nanoTime(), 36) + "@example.com";
		PersistableCustomer customer = new PersistableCustomer();
		customer.setEmailAddress(email);
		customer.setPassword("clear123");
		customer.setGender(CustomerGender.M.name());
		customer.setLanguage("en");
		customer.setStoreCode(Constants.DEFAULT_STORE);
		Address billing = new Address();
		billing.setFirstName("UAT");
		billing.setLastName("Customer");
		billing.setCountry("BE");
		customer.setBilling(billing);
		ResponseEntity<PersistableCustomer> registered = testRestTemplate.postForEntity(
				"/api/v1/customer/register", new HttpEntity<>(customer, getHeader()),
				PersistableCustomer.class);
		assertEquals(HttpStatus.OK, registered.getStatusCode());

		AuthenticationResponse login = testRestTemplate.postForObject("/api/v1/customer/login",
				new HttpEntity<>(new AuthenticationRequest(email, "clear123")), AuthenticationResponse.class);
		assertNotNull(login);
		assertNotNull(login.getToken());
		HttpHeaders headers = new HttpHeaders();
		headers.setBearerAuth(login.getToken());
		assertEquals(HttpStatus.OK, testRestTemplate.exchange("/api/v1/auth/customer/profile?store=DEFAULT",
				HttpMethod.GET, new HttpEntity<>(headers), String.class).getStatusCode());
	}

	@Test
	void browseStoreCategoriesProductsAndProductDetail() {
		assertEquals(HttpStatus.OK, testRestTemplate.getForEntity("/api/v1/store/DEFAULT", String.class)
				.getStatusCode());
		assertEquals(HttpStatus.OK, testRestTemplate.getForEntity("/api/v1/category?store=DEFAULT", String.class)
				.getStatusCode());
		assertEquals(HttpStatus.OK, testRestTemplate.getForEntity("/api/v1/products?store=DEFAULT", String.class)
				.getStatusCode());
		String sku = "UATBROWSE" + Long.toString(System.nanoTime(), 36).toUpperCase();
		assertNotNull(sampleProduct(sku));
	}

	@Test
	void cartCanBeCreatedModifiedAndRead() {
		String sku = "UATCART" + Long.toString(System.nanoTime(), 36).toUpperCase();
		com.salesmanager.shop.model.catalog.product.product.PersistableProduct productBody = product(sku);
		productBody.setSku(sku);
		ResponseEntity<com.salesmanager.shop.model.entity.Entity> product =
				testRestTemplate.postForEntity("/api/v1/private/product?store=DEFAULT",
						new HttpEntity<>(productBody, getHeader()),
						com.salesmanager.shop.model.entity.Entity.class);
		assertTrue(product.getStatusCode().is2xxSuccessful());
		PersistableShoppingCartItem item = new PersistableShoppingCartItem();
		item.setProduct(sku);
		item.setQuantity(1);
		ReadableShoppingCart cart = testRestTemplate.postForObject("/api/v1/cart?store=DEFAULT",
				new HttpEntity<>(item), ReadableShoppingCart.class);
		assertNotNull(cart);
		assertNotNull(cart.getCode());
		item.setQuantity(2);
		assertTrue(testRestTemplate.exchange("/api/v1/cart/" + cart.getCode() + "?store=DEFAULT",
				HttpMethod.PUT, new HttpEntity<>(item), String.class).getStatusCode().is2xxSuccessful());
		assertEquals(HttpStatus.OK, testRestTemplate.getForEntity("/api/v1/cart/" + cart.getCode()
				+ "?store=DEFAULT", String.class).getStatusCode());
	}

	@Test
	void platformEndpointsRespondAndServicesRequireBasicAuth() {
		assertEquals(HttpStatus.OK, testRestTemplate.getForEntity("/actuator/health", String.class).getStatusCode());
		assertEquals(HttpStatus.OK, testRestTemplate.getForEntity("/v2/api-docs", String.class).getStatusCode());

		String endpoint = "/services/private/system/optin";
		HttpHeaders valid = new HttpHeaders();
		valid.setBasicAuth("admin@shopizer.com", "password");
		valid.setContentType(MediaType.TEXT_PLAIN);
		ResponseEntity<String> authenticated = testRestTemplate.postForEntity(endpoint,
				new HttpEntity<>("{}", valid), String.class);
		assertTrue(authenticated.getStatusCode().value() != HttpStatus.UNAUTHORIZED.value());
		HttpHeaders invalid = new HttpHeaders();
		invalid.setBasicAuth("invalid", "invalid");
		invalid.setContentType(MediaType.TEXT_PLAIN);
		assertEquals(HttpStatus.UNAUTHORIZED, testRestTemplate.postForEntity(endpoint,
				new HttpEntity<>("{}", invalid), String.class).getStatusCode());
	}

	private boolean isUnauthorized(HttpStatus status) {
		return status == HttpStatus.UNAUTHORIZED || status == HttpStatus.FORBIDDEN;
	}
}
