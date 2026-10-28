package com.salesmanager.test.shop.integration.product;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;
import static org.springframework.http.HttpStatus.NOT_FOUND;
import static org.springframework.http.HttpStatus.OK;

import java.math.BigDecimal;
import java.util.Arrays;
import java.util.UUID;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.SpringBootTest.WebEnvironment;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.test.context.junit4.SpringRunner;

import com.salesmanager.core.business.constants.Constants;
import com.salesmanager.core.model.catalog.product.manufacturer.Manufacturer;
import com.salesmanager.core.model.customer.CustomerGender;
import com.salesmanager.shop.application.ShopApplication;
import com.salesmanager.shop.model.catalog.product.ReadableProduct;
import com.salesmanager.shop.model.catalog.product.product.PersistableProduct;
import com.salesmanager.shop.model.catalog.product.product.ProductSpecification;
import com.salesmanager.shop.model.customer.PersistableCustomer;
import com.salesmanager.shop.model.customer.address.Address;
import com.salesmanager.shop.model.references.PersistableAddress;
import com.salesmanager.shop.model.store.PersistableMerchantStore;
import com.salesmanager.shop.store.security.AuthenticationRequest;
import com.salesmanager.shop.store.security.AuthenticationResponse;
import com.salesmanager.test.shop.common.ServicesTestSupport;

@SpringBootTest(classes = ShopApplication.class, webEnvironment = WebEnvironment.RANDOM_PORT)
@RunWith(SpringRunner.class)
public class ProductApiAuthorizationIntegrationTest extends ServicesTestSupport {

	private static final String PASSWORD = "clear123";
	private static final String OTHER_STORE = "PRODAUTHZ";

	@Test
	public void customerCannotWriteProductsThroughAuthRoutes() {
		String code = uniqueCode("authzcust");
		ReadableProduct existing = sampleProduct(code);
		HttpHeaders customer = customerHeaders();
		String productUrl = "/api/v1/auth/product/" + existing.getId() + "?store=" + Constants.DEFAULT_STORE;

		PersistableProduct update = writableProduct(code, BigDecimal.valueOf(1));
		update.setId(existing.getId());
		update.getDescriptions().get(0).setName("defaced");

		ResponseEntity<String> create = testRestTemplate.exchange(
				"/api/v1/auth/products?store=" + Constants.DEFAULT_STORE, HttpMethod.POST,
				new HttpEntity<>(writableProduct(code + "new", BigDecimal.ONE), customer), String.class);
		ResponseEntity<String> put = testRestTemplate.exchange(productUrl, HttpMethod.PUT,
				new HttpEntity<>(update, customer), String.class);
		ResponseEntity<String> patch = testRestTemplate.exchange(productUrl + "&order=99", HttpMethod.PATCH,
				new HttpEntity<>(customer), String.class);
		ResponseEntity<String> delete = testRestTemplate.exchange(productUrl, HttpMethod.DELETE,
				new HttpEntity<>(customer), String.class);

		assertFalse("POST /auth/products returned " + create.getStatusCode(), create.getStatusCode().is2xxSuccessful());
		assertFalse("PUT /auth/product returned " + put.getStatusCode(), put.getStatusCode().is2xxSuccessful());
		assertFalse("PATCH /auth/product returned " + patch.getStatusCode(), patch.getStatusCode().is2xxSuccessful());
		assertFalse("DELETE /auth/product returned " + delete.getStatusCode(), delete.getStatusCode().is2xxSuccessful());

		ReadableProduct after = getProduct(code, Constants.DEFAULT_STORE).getBody();
		assertNotNull(after);
		assertEquals(existing.getId(), after.getId());
		assertEquals(0, existing.getPrice().compareTo(after.getPrice()));
		assertEquals(OK, getProduct(code, Constants.DEFAULT_STORE).getStatusCode());
		assertTrue(getProduct(code + "new", Constants.DEFAULT_STORE).getStatusCode().is4xxClientError());
	}

	@Test
	public void adminCannotUpdateOrDeleteProductOfAnotherStore() {
		ensureOtherStore();
		String code = uniqueCode("authzcross");
		ReadableProduct existing = sampleProduct(code);
		String otherStoreUrl = "/api/v1/private/product/" + existing.getId() + "?store=" + OTHER_STORE;

		PersistableProduct update = writableProduct(code, BigDecimal.valueOf(1));
		update.setId(existing.getId());

		ResponseEntity<String> put = testRestTemplate.exchange(otherStoreUrl, HttpMethod.PUT,
				new HttpEntity<>(update, getHeader()), String.class);
		ResponseEntity<String> delete = testRestTemplate.exchange(otherStoreUrl, HttpMethod.DELETE,
				new HttpEntity<>(getHeader()), String.class);

		assertEquals(NOT_FOUND, put.getStatusCode());
		assertEquals(NOT_FOUND, delete.getStatusCode());

		ResponseEntity<ReadableProduct> inDefault = getProduct(code, Constants.DEFAULT_STORE);
		assertEquals(OK, inDefault.getStatusCode());
		assertEquals(0, existing.getPrice().compareTo(inDefault.getBody().getPrice()));
		assertTrue(getProduct(code, OTHER_STORE).getStatusCode().is4xxClientError());
	}

	@Test
	public void adminUpdateWithMismatchedIdIsRejected() {
		String code = uniqueCode("authzmismatch");
		ReadableProduct existing = sampleProduct(code);

		PersistableProduct update = writableProduct(code, BigDecimal.valueOf(1));
		update.setId(existing.getId() + 100000);

		ResponseEntity<String> put = testRestTemplate.exchange(
				"/api/v1/private/product/" + existing.getId() + "?store=" + Constants.DEFAULT_STORE,
				HttpMethod.PUT, new HttpEntity<>(update, getHeader()), String.class);

		assertEquals(org.springframework.http.HttpStatus.BAD_REQUEST, put.getStatusCode());
		ReadableProduct after = getProduct(code, Constants.DEFAULT_STORE).getBody();
		assertEquals(0, existing.getPrice().compareTo(after.getPrice()));
	}

	private static String uniqueCode(String prefix) {
		return prefix + UUID.randomUUID().toString().replace("-", "").substring(0, 12);
	}

	private PersistableProduct writableProduct(String code, BigDecimal price) {
		PersistableProduct product = product(code);
		ProductSpecification specifications = new ProductSpecification();
		specifications.setManufacturer(Manufacturer.DEFAULT_MANUFACTURER);
		product.setProductSpecifications(specifications);
		product.setAvailable(true);
		product.setSku(code);
		product.setQuantity(100);
		product.setPrice(price);
		return product;
	}

	private ResponseEntity<ReadableProduct> getProduct(String sku, String store) {
		return testRestTemplate.getForEntity("/api/v2/product/" + sku + "?store=" + store, ReadableProduct.class);
	}

	private HttpHeaders customerHeaders() {
		String email = uniqueCode("shopper") + "@test.com";
		PersistableCustomer customer = new PersistableCustomer();
		customer.setEmailAddress(email);
		customer.setPassword(PASSWORD);
		customer.setGender(CustomerGender.F.name());
		customer.setLanguage("en");
		customer.setStoreCode(Constants.DEFAULT_STORE);
		Address billing = new Address();
		billing.setFirstName("Mallory");
		billing.setLastName("Shopper");
		billing.setCountry("BE");
		customer.setBilling(billing);

		HttpHeaders json = new HttpHeaders();
		json.setContentType(MediaType.APPLICATION_JSON);
		ResponseEntity<String> registration = testRestTemplate.postForEntity("/api/v1/customer/register",
				new HttpEntity<>(customer, json), String.class);
		assertTrue("registration returned " + registration.getStatusCode(),
				registration.getStatusCode().is2xxSuccessful());

		ResponseEntity<AuthenticationResponse> login = testRestTemplate.postForEntity("/api/v1/customer/login",
				new HttpEntity<>(new AuthenticationRequest(email, PASSWORD)), AuthenticationResponse.class);
		assertEquals(OK, login.getStatusCode());

		HttpHeaders headers = new HttpHeaders();
		headers.setContentType(MediaType.APPLICATION_JSON);
		headers.setBearerAuth(login.getBody().getToken());
		return headers;
	}

	private void ensureOtherStore() {
		if (testRestTemplate.getForEntity("/api/v1/store/" + OTHER_STORE, String.class).getStatusCode() == OK) {
			return;
		}
		PersistableAddress address = new PersistableAddress();
		address.setAddress("1 Market street");
		address.setPostalCode("12345");
		address.setCountry("US");
		address.setCity("FT LD");
		address.setStateProvince("FL");

		PersistableMerchantStore store = new PersistableMerchantStore();
		store.setCode(OTHER_STORE);
		store.setCurrency("CAD");
		store.setDefaultLanguage("en");
		store.setEmail("prodauthz@test.com");
		store.setName(OTHER_STORE);
		store.setPhone("444-555-6666");
		store.setSupportedLanguages(Arrays.asList("en"));
		store.setAddress(address);

		ResponseEntity<String> created = testRestTemplate.exchange("/api/v1/private/store/", HttpMethod.POST,
				new HttpEntity<>(store, getHeader()), String.class);
		assertTrue("store creation returned " + created.getStatusCode(), created.getStatusCode().is2xxSuccessful());
	}
}
