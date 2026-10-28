package com.salesmanager.test.shop.uat;

import static org.junit.Assert.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.springframework.http.HttpStatus.*;

import java.util.UUID;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.kie.api.io.Resource;
import org.kie.api.runtime.KieSession;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.ResponseEntity;
import org.springframework.test.context.junit4.SpringRunner;
import org.springframework.test.util.ReflectionTestUtils;

import com.fasterxml.jackson.databind.JsonNode;
import com.salesmanager.core.business.configuration.DroolsBeanFactory;
import com.salesmanager.core.business.modules.order.total.PromoCodeCalculatorModule;
import com.salesmanager.core.model.customer.CustomerGender;
import com.salesmanager.shop.model.catalog.product.ReadableProduct;
import com.salesmanager.shop.model.customer.PersistableCustomer;
import com.salesmanager.shop.model.customer.address.Address;
import com.salesmanager.shop.model.shoppingcart.PersistableShoppingCartItem;
import com.salesmanager.shop.model.shoppingcart.ReadableShoppingCart;
import com.salesmanager.shop.store.security.AuthenticationRequest;
import com.salesmanager.shop.store.security.AuthenticationResponse;
import com.salesmanager.test.shop.common.ServicesTestSupport;

@RunWith(SpringRunner.class)
public class StorefrontUatTest extends ServicesTestSupport {

	@Autowired
	private PromoCodeCalculatorModule promoCodeCalculatorModule;

	private DroolsBeanFactory originalDroolsBeanFactory;

	@Before
	public void stubPromoRules() {
		originalDroolsBeanFactory = (DroolsBeanFactory) ReflectionTestUtils.getField(
				promoCodeCalculatorModule, "droolsBeanFactory");
		DroolsBeanFactory droolsBeanFactory = mock(DroolsBeanFactory.class);
		when(droolsBeanFactory.getKieSession(any(Resource.class))).thenReturn(mock(KieSession.class));
		ReflectionTestUtils.setField(promoCodeCalculatorModule, "droolsBeanFactory", droolsBeanFactory);
	}

	@After
	public void restorePromoRules() {
		ReflectionTestUtils.setField(promoCodeCalculatorModule, "droolsBeanFactory", originalDroolsBeanFactory);
	}

	@Test
	public void adminJwtProtectsPrivateApi() {
		HttpHeaders headers = getHeader();
		assertEquals(OK, testRestTemplate.exchange("/api/v1/private/customers", HttpMethod.GET,
				new HttpEntity<>(headers), String.class).getStatusCode());
		assertDenied(testRestTemplate.getForEntity("/api/v1/private/customers", String.class));
	}

	@Test
	public void customerCanRegisterLoginAndReadProfile() {
		HttpHeaders headers = registerAndLogin();
		assertEquals(OK, testRestTemplate.exchange("/api/v1/auth/customer/profile", HttpMethod.GET,
				new HttpEntity<>(headers), String.class).getStatusCode());
	}

	@Test
	public void shopperCanBrowseAndUpdatePromoCart() {
		ReadableProduct product = sampleProduct("uat-" + UUID.randomUUID());
		for (String path : new String[] { "/api/v1/store/DEFAULT", "/api/v1/category",
				"/api/v1/products", "/api/v2/product/" + product.getSku() }) {
			ResponseEntity<JsonNode> response = testRestTemplate.getForEntity(path, JsonNode.class);
			assertEquals(path, OK, response.getStatusCode());
			assertNotNull(path, response.getBody());
		}
		PersistableShoppingCartItem item = new PersistableShoppingCartItem();
		item.setProduct(product.getSku());
		item.setQuantity(1);
		item.setPromoCode("ONEDAY");
		ResponseEntity<ReadableShoppingCart> created = testRestTemplate.postForEntity(
				"/api/v1/cart", item, ReadableShoppingCart.class);
		assertEquals(CREATED, created.getStatusCode());
		String code = created.getBody().getCode();
		item.setQuantity(2);
		assertEquals(CREATED, testRestTemplate.exchange("/api/v1/cart/" + code, HttpMethod.PUT,
				new HttpEntity<>(item), ReadableShoppingCart.class).getStatusCode());
		ResponseEntity<ReadableShoppingCart> read = testRestTemplate.getForEntity(
				"/api/v1/cart/" + code, ReadableShoppingCart.class);
		assertEquals(OK, read.getStatusCode());
		assertEquals(2, read.getBody().getQuantity());
		assertEquals("ONEDAY", read.getBody().getPromoCode());
	}

	@Test
	public void legacyServicesRequireBasicAuthentication() {
		String path = "/services/private/system/optin";
		assertEquals(OK, testRestTemplate.withBasicAuth("admin@shopizer.com", "password")
				.postForEntity(path, "{}", String.class).getStatusCode());
		assertDenied(testRestTemplate.withBasicAuth("admin@shopizer.com", "incorrect")
				.postForEntity(path, "{}", String.class));
		ResponseEntity<String> anonymous = testRestTemplate.postForEntity(path, "{}", String.class);
		assertEquals(FOUND, anonymous.getStatusCode());
		assertNotNull(anonymous.getHeaders().getLocation());
		assertTrue(anonymous.getHeaders().getLocation().getPath().endsWith("/login"));
	}

	@Test
	public void healthAndApiDocumentationAreAvailable() {
		ResponseEntity<JsonNode> health = testRestTemplate.getForEntity("/actuator/health", JsonNode.class);
		assertEquals(OK, health.getStatusCode());
		assertEquals("UP", health.getBody().path("status").asText());
		assertEquals(OK, testRestTemplate.getForEntity("/v2/api-docs", String.class).getStatusCode());
	}

	private HttpHeaders registerAndLogin() {
		String email = "uat-" + UUID.randomUUID() + "@example.com";
		PersistableCustomer customer = new PersistableCustomer();
		customer.setEmailAddress(email);
		customer.setPassword("clear123");
		customer.setGender(CustomerGender.M.name());
		customer.setLanguage("en");
		customer.setStoreCode("DEFAULT");
		Address billing = new Address();
		billing.setFirstName("Test");
		billing.setLastName("Shopper");
		billing.setCountry("CA");
		billing.setZone("QC");
		billing.setAddress("123 Test Street");
		billing.setCity("Montreal");
		billing.setPostalCode("H2Y1C6");
		customer.setBilling(billing);
		assertEquals(OK, testRestTemplate.postForEntity("/api/v1/customer/register",
				customer, PersistableCustomer.class).getStatusCode());
		ResponseEntity<AuthenticationResponse> login = testRestTemplate.postForEntity("/api/v1/customer/login",
				new AuthenticationRequest(email, "clear123"), AuthenticationResponse.class);
		assertEquals(OK, login.getStatusCode());
		assertNotNull(login.getBody().getToken());
		HttpHeaders headers = new HttpHeaders();
		headers.setBearerAuth(login.getBody().getToken());
		return headers;
	}

	private void assertDenied(ResponseEntity<?> response) {
		assertTrue("Expected 401/403, got " + response.getStatusCode(),
				response.getStatusCode() == UNAUTHORIZED || response.getStatusCode() == FORBIDDEN);
	}
}
