package com.salesmanager.test.shop.uat;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;
import static org.springframework.http.HttpStatus.CREATED;
import static org.springframework.http.HttpStatus.OK;
import static org.springframework.http.HttpStatus.UNAUTHORIZED;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.SpringBootTest.WebEnvironment;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.ResponseEntity;
import org.springframework.test.context.junit4.SpringRunner;

import com.salesmanager.shop.application.ShopApplication;
import com.salesmanager.shop.model.catalog.product.ReadableProduct;
import com.salesmanager.shop.model.shoppingcart.PersistableShoppingCartItem;
import com.salesmanager.shop.model.shoppingcart.ReadableShoppingCart;
import com.salesmanager.shop.model.customer.PersistableCustomer;
import com.salesmanager.shop.model.customer.address.Address;
import com.salesmanager.core.business.constants.Constants;
import com.salesmanager.shop.store.security.AuthenticationRequest;
import com.salesmanager.shop.store.security.AuthenticationResponse;
import com.salesmanager.test.shop.common.ServicesTestSupport;

@SpringBootTest(classes = ShopApplication.class, webEnvironment = WebEnvironment.RANDOM_PORT)
@RunWith(SpringRunner.class)
public class StorefrontAdminJourneysUatTest extends ServicesTestSupport {

	@Test
	public void adminAndStorefrontCatalogCartAndRuntimeJourneys() {
		ResponseEntity<String> noToken = testRestTemplate.getForEntity("/api/v1/private/customers", String.class);
		assertTrue(noToken.getStatusCode().is3xxRedirection() || noToken.getStatusCode() == UNAUTHORIZED);
		org.springframework.http.HttpHeaders admin = getHeader("admin@shopizer.com", "password");
		assertNotNull(admin.getFirst("Authorization"));
		assertEquals(OK, testRestTemplate.exchange("/api/v1/private/customers", HttpMethod.GET,
				new HttpEntity<>(admin), String.class).getStatusCode());
		HttpHeaders invalidBearer = new HttpHeaders();
		invalidBearer.setBearerAuth("bad");
		ResponseEntity<String> badToken = testRestTemplate.exchange("/api/v1/private/customers",
				HttpMethod.GET, new HttpEntity<>(invalidBearer), String.class);
		assertTrue(badToken.getStatusCode() == UNAUTHORIZED
				|| badToken.getStatusCode().value() == 403
				|| badToken.getStatusCode().is5xxServerError());

		ReadableProduct first = sampleProduct("uat-" + System.nanoTime());
		assertNotNull(first);
		assertEquals(OK, testRestTemplate.getForEntity("/api/v1/store/DEFAULT", String.class).getStatusCode());
		assertEquals(OK, testRestTemplate.getForEntity("/api/v1/category?store=DEFAULT", String.class)
				.getStatusCode());
		assertEquals(OK, testRestTemplate.getForEntity("/api/v1/products?store=DEFAULT", String.class)
				.getStatusCode());
		ResponseEntity<ReadableProduct> product = testRestTemplate.getForEntity(
				"/api/v2/product/" + first.getSku(), ReadableProduct.class);
		assertEquals(OK, product.getStatusCode());

		ReadableProduct second = sampleProduct("uat-" + System.nanoTime());
		PersistableShoppingCartItem item = new PersistableShoppingCartItem();
		item.setProduct(first.getSku());
		item.setQuantity(1);
		ResponseEntity<ReadableShoppingCart> cart = testRestTemplate.postForEntity("/api/v1/cart",
				new HttpEntity<>(item), ReadableShoppingCart.class);
		assertEquals(CREATED, cart.getStatusCode());
		assertNotNull(cart.getBody());
		PersistableShoppingCartItem secondItem = new PersistableShoppingCartItem();
		secondItem.setProduct(second.getSku());
		secondItem.setQuantity(1);
		assertEquals(CREATED, testRestTemplate.exchange("/api/v1/cart/" + cart.getBody().getCode(),
				HttpMethod.PUT, new HttpEntity<>(secondItem), ReadableShoppingCart.class).getStatusCode());
		assertEquals(OK, testRestTemplate.getForEntity("/api/v1/cart/" + cart.getBody().getCode(),
				ReadableShoppingCart.class).getStatusCode());

		String email = "uat-customer-" + System.nanoTime() + "@example.com";
		PersistableCustomer customer = new PersistableCustomer();
		customer.setEmailAddress(email);
		customer.setPassword("clear123");
		customer.setGender("M");
		customer.setLanguage("en");
		customer.setStoreCode(Constants.DEFAULT_STORE);
		Address billing = new Address();
		billing.setFirstName("Uat");
		billing.setLastName("Customer");
		billing.setCountry("BE");
		customer.setBilling(billing);
		assertEquals(OK, testRestTemplate.postForEntity("/api/v1/customer/register",
				new HttpEntity<>(customer), AuthenticationResponse.class).getStatusCode());
		AuthenticationResponse login = testRestTemplate.postForEntity("/api/v1/customer/login",
				new HttpEntity<>(new AuthenticationRequest(email, "clear123")), AuthenticationResponse.class)
				.getBody();
		assertNotNull(login);
		HttpHeaders customerHeaders = new HttpHeaders();
		customerHeaders.setBearerAuth(login.getToken());
		ResponseEntity<String> profile = testRestTemplate.exchange("/api/v1/auth/customer/profile",
				HttpMethod.GET, new HttpEntity<>(customerHeaders), String.class);
		assertEquals(OK, profile.getStatusCode());
		assertTrue(profile.getBody().contains(email));

		ResponseEntity<String> health = testRestTemplate.getForEntity("/actuator/health", String.class);
		assertTrue(health.getStatusCode().is2xxSuccessful());
		assertTrue(health.getBody().contains("\"status\":\"UP\""));
		ResponseEntity<String> docs = testRestTemplate.getForEntity("/v2/api-docs", String.class);
		assertTrue(docs.getStatusCode().is2xxSuccessful());
		assertTrue(docs.getBody().contains("swagger"));
		ResponseEntity<String> privateWithoutAuth = testRestTemplate.postForEntity(
				"/services/private/system/optin", new HttpEntity<>("{}"), String.class);
		assertTrue(privateWithoutAuth.getStatusCode().is3xxRedirection()
				|| privateWithoutAuth.getStatusCode() == UNAUTHORIZED);

		HttpHeaders validBasic = new HttpHeaders();
		validBasic.setBasicAuth("admin@shopizer.com", "password");
		assertTrue(testRestTemplate.postForEntity("/services/private/system/optin",
				new HttpEntity<>("{}", validBasic), String.class).getStatusCode().is2xxSuccessful());
		HttpHeaders invalidBasic = new HttpHeaders();
		invalidBasic.setBasicAuth("admin@shopizer.com", "wrong");
		ResponseEntity<String> rejectedBasic = testRestTemplate.postForEntity("/services/private/system/optin",
				new HttpEntity<>("{}", invalidBasic), String.class);
		assertEquals(UNAUTHORIZED, rejectedBasic.getStatusCode());
	}
}
