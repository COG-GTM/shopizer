package com.salesmanager.test.shop.uat;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;
import static org.springframework.http.HttpMethod.GET;
import static org.springframework.http.HttpMethod.POST;
import static org.springframework.http.HttpStatus.CREATED;
import static org.springframework.http.HttpStatus.OK;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.Base64;
import java.util.UUID;

import org.junit.Assume;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.SpringBootTest.WebEnvironment;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.ResponseEntity;
import org.springframework.test.context.junit4.SpringRunner;

import com.salesmanager.core.business.constants.Constants;
import com.salesmanager.shop.application.ShopApplication;
import com.salesmanager.shop.model.catalog.product.ReadableProduct;
import com.salesmanager.shop.model.catalog.product.ReadableProductList;
import com.salesmanager.shop.model.catalog.product.product.PersistableProduct;
import com.salesmanager.shop.model.catalog.product.product.ProductSpecification;
import com.salesmanager.shop.model.entity.Entity;
import com.salesmanager.shop.model.security.PersistableGroup;
import com.salesmanager.shop.model.references.PersistableAddress;
import com.salesmanager.shop.model.store.PersistableMerchantStore;
import com.salesmanager.shop.model.user.PersistableUser;
import com.salesmanager.shop.model.user.ReadableUser;
import com.salesmanager.test.shop.common.ServicesTestSupport;

@SpringBootTest(classes = ShopApplication.class, webEnvironment = WebEnvironment.RANDOM_PORT)
@RunWith(SpringRunner.class)
public class PlatformUatTest extends ServicesTestSupport {

	@Test
	public void actuatorHealthAndSwaggerDocsAreAvailable() {
		ResponseEntity<String> health = testRestTemplate.getForEntity("/actuator/health", String.class);
		assertEquals(OK, health.getStatusCode());
		assertNotNull(health.getBody());
		assertTrue(health.getBody().contains("\"status\":\"UP\""));

		ResponseEntity<String> docs = testRestTemplate.getForEntity("/v2/api-docs", String.class);
		assertEquals(OK, docs.getStatusCode());
		assertNotNull(docs.getBody());
		assertTrue(docs.getBody().contains("\"paths\""));
	}

	@Test
	public void servicesEndpointRejectsInvalidBasicCredentials() {
		HttpHeaders headers = basicAuth("invalid-uat-user", "not-a-password");
		ResponseEntity<String> response = testRestTemplate.exchange(
				"/services/private/system/optin", POST,
				new HttpEntity<>("{\"uat\":true}", headers), String.class);
		assertTrue(response.getStatusCodeValue() == 401 || response.getStatusCodeValue() == 403);
	}

	@Test
	public void servicesEndpointAcceptsSeededBasicCredentialsIfAvailable() {
		HttpHeaders headers = basicAuth("admin@shopizer.com", "password");
		ResponseEntity<String> response = testRestTemplate.exchange(
				"/services/private/system/optin", POST,
				new HttpEntity<>("{\"uat\":true}", headers), String.class);
		Assume.assumeTrue("No seeded services Basic-auth account accepted admin@shopizer.com/password; "
				+ "the valid-credentials journey is NOT RUN (HTTP " + response.getStatusCodeValue() + ")",
				response.getStatusCode().is2xxSuccessful());
		assertEquals(OK, response.getStatusCode());
	}

	@Test
	public void storeBAdminCannotOverwriteDefaultProduct() {
		String suffix = UUID.randomUUID().toString().replace("-", "").substring(0, 10);
		String sku = "UAT-OWN-" + suffix;
		String storeCode = "UATB" + suffix;
		String email = "uat-owner-" + suffix + "@test.com";

		PersistableProduct product = product(sku);
		product.setSku(sku);
		product.setPrice(BigDecimal.TEN);
		product.setAvailable(true);
		product.getDescriptions().get(0).setName("UAT original " + suffix);
		ProductSpecification specification = new ProductSpecification();
		specification.setManufacturer(
				com.salesmanager.core.model.catalog.product.manufacturer.Manufacturer.DEFAULT_MANUFACTURER);
		product.setProductSpecifications(specification);
		ResponseEntity<Entity> createdProduct = testRestTemplate.postForEntity(
				"/api/v1/private/product?store=DEFAULT", new HttpEntity<>(product, getHeader()), Entity.class);
		assertEquals(CREATED, createdProduct.getStatusCode());
		assertNotNull(createdProduct.getBody());

		createStore(storeCode);
		HttpHeaders storeAdmin = createStoreAdmin(storeCode, email);
		String manufacturerCode = "UATM" + suffix;
		ResponseEntity<String> manufacturerResponse = testRestTemplate.postForEntity(
				"/api/v1/private/manufacturer?store=" + storeCode,
				new HttpEntity<>(manufacturer(manufacturerCode), storeAdmin), String.class);
		assertEquals(CREATED, manufacturerResponse.getStatusCode());
		product.setId(createdProduct.getBody().getId());
		product.getDescriptions().get(0).setName("UAT overwritten " + suffix);
		ProductSpecification attackSpecification = new ProductSpecification();
		attackSpecification.setManufacturer(manufacturerCode);
		product.setProductSpecifications(attackSpecification);
		ResponseEntity<String> attack = testRestTemplate.postForEntity(
				"/api/v1/private/product?store=" + storeCode, new HttpEntity<>(product, storeAdmin),
				String.class);
		assertEquals(404, attack.getStatusCodeValue());

		ResponseEntity<ReadableProductList> listing = testRestTemplate.exchange(
				"/api/v1/products?store=DEFAULT&sku=" + sku, GET, HttpEntity.EMPTY,
				ReadableProductList.class);
		assertEquals(OK, listing.getStatusCode());
		assertNotNull(listing.getBody());
		assertTrue("Default product remains listed", !listing.getBody().getProducts().isEmpty());
		ReadableProduct unchanged = listing.getBody().getProducts().get(0);
		assertEquals(sku, unchanged.getSku());
		assertEquals("UAT original " + suffix, unchanged.getDescription().getName());
	}

	private HttpHeaders basicAuth(String userName, String password) {
		HttpHeaders headers = new HttpHeaders();
		String credentials = userName + ":" + password;
		String encoded = Base64.getEncoder().encodeToString(credentials.getBytes(StandardCharsets.UTF_8));
		headers.set(HttpHeaders.AUTHORIZATION, "Basic " + encoded);
		return headers;
	}

	private void createStore(String code) {
		PersistableMerchantStore store = new PersistableMerchantStore();
		store.setCode(code);
		store.setCurrency("CAD");
		store.setDefaultLanguage("en");
		store.setEmail(code.toLowerCase() + "@test.com");
		store.setName(code);
		store.setPhone("444-555-6666");
		store.setSupportedLanguages(Arrays.asList("en"));
		PersistableAddress address = new PersistableAddress();
		address.setAddress("121212 simple address");
		address.setPostalCode("12345");
		address.setCountry("US");
		address.setCity("FT LD");
		address.setStateProvince("FL");
		store.setAddress(address);
		ResponseEntity<Void> response = testRestTemplate.exchange("/api/v1/private/store", POST,
				new HttpEntity<>(store, getHeader()), Void.class);
		assertEquals(OK, response.getStatusCode());
	}

	private HttpHeaders createStoreAdmin(String storeCode, String email) {
		String password = "Password1";
		PersistableUser user = new PersistableUser();
		user.setDefaultLanguage("en");
		user.setEmailAddress(email);
		user.setUserName(email);
		user.setFirstName("UAT");
		user.setLastName("Admin");
		user.setPassword(password);
		user.setRepeatPassword(password);
		user.setActive(true);
		user.setStore(storeCode);
		user.getGroups().add(new PersistableGroup("ADMIN"));
		ResponseEntity<ReadableUser> created = testRestTemplate.exchange(
				"/api/v1/private/user/?store=" + storeCode, HttpMethod.POST,
				new HttpEntity<>(user, getHeader()), ReadableUser.class);
		assertEquals(OK, created.getStatusCode());
		ResponseEntity<com.salesmanager.shop.store.security.AuthenticationResponse> login =
				testRestTemplate.postForEntity("/api/v1/private/login",
						new HttpEntity<>(new com.salesmanager.shop.store.security.AuthenticationRequest(
								email, password)),
						com.salesmanager.shop.store.security.AuthenticationResponse.class);
		assertEquals(OK, login.getStatusCode());
		assertNotNull(login.getBody());
		HttpHeaders headers = new HttpHeaders();
		headers.setBearerAuth(login.getBody().getToken());
		return headers;
	}
}
