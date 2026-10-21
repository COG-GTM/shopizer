package com.salesmanager.test.shop.integration.product;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;
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
	private static final String SECOND_STORE = "AUTHZSTORE";

	@Test
	public void customerCannotCreateUpdateOrDeleteProducts() {
		String suffix = UUID.randomUUID().toString().replace("-", "");
		String email = "product-authz-" + suffix + "@test.com";
		String productCode = "authzproduct" + suffix;

		PersistableCustomer customer = new PersistableCustomer();
		customer.setEmailAddress(email);
		customer.setPassword(PASSWORD);
		customer.setGender(CustomerGender.M.name());
		customer.setLanguage("en");
		customer.setStoreCode(Constants.DEFAULT_STORE);
		Address billing = new Address();
		billing.setFirstName("product");
		billing.setLastName("authz");
		billing.setCountry("BE");
		customer.setBilling(billing);

		ResponseEntity<PersistableCustomer> registrationResponse = testRestTemplate.postForEntity(
				"/api/v1/customer/register",
				new HttpEntity<>(customer, getHeader()), PersistableCustomer.class);
		assertTrue(registrationResponse.getStatusCode().is2xxSuccessful());

		ResponseEntity<AuthenticationResponse> loginResponse = testRestTemplate.postForEntity(
				"/api/v1/customer/login",
				new HttpEntity<>(new AuthenticationRequest(email, PASSWORD)), AuthenticationResponse.class);
		assertEquals(OK, loginResponse.getStatusCode());
		assertNotNull(loginResponse.getBody());
		assertNotNull(loginResponse.getBody().getToken());

		HttpHeaders customerHeaders = new HttpHeaders();
		customerHeaders.setContentType(MediaType.APPLICATION_JSON);
		customerHeaders.setBearerAuth(loginResponse.getBody().getToken());

		ReadableProduct createdProduct = sampleProduct(productCode);
		PersistableProduct update = product(productCode);
		update.setId(createdProduct.getId());
		update.setSku(productCode);
		update.setPrice(BigDecimal.valueOf(9999));

		PersistableProduct create = product(productCode + "customer");
		create.setSku(productCode + "customer");
		create.setPrice(BigDecimal.TEN);
		create.setAvailable(true);
		ProductSpecification specifications = new ProductSpecification();
		specifications.setManufacturer(
				com.salesmanager.core.model.catalog.product.manufacturer.Manufacturer.DEFAULT_MANUFACTURER);
		create.setProductSpecifications(specifications);

		ResponseEntity<String> createResponse = testRestTemplate.exchange(
				"/api/v1/auth/products?store=" + Constants.DEFAULT_STORE,
				HttpMethod.POST, new HttpEntity<>(create, customerHeaders), String.class);
		ResponseEntity<String> updateResponse = testRestTemplate.exchange(
				"/api/v1/auth/product/" + createdProduct.getId() + "?store=" + Constants.DEFAULT_STORE,
				HttpMethod.PUT, new HttpEntity<>(update, customerHeaders), String.class);
		ResponseEntity<String> deleteResponse = testRestTemplate.exchange(
				"/api/v1/auth/product/" + createdProduct.getId() + "?store=" + Constants.DEFAULT_STORE,
				HttpMethod.DELETE, new HttpEntity<>(customerHeaders), String.class);
		ResponseEntity<String> reorderResponse = testRestTemplate.exchange(
				"/api/v1/auth/product/" + createdProduct.getId() + "?store=" + Constants.DEFAULT_STORE + "&order=5",
				HttpMethod.PATCH, new HttpEntity<>(customerHeaders), String.class);

		assertFalse("Customer product writes unexpectedly succeeded: POST " + createResponse.getStatusCode()
				+ ", PUT " + updateResponse.getStatusCode() + ", DELETE " + deleteResponse.getStatusCode()
				+ ", PATCH " + reorderResponse.getStatusCode(),
				createResponse.getStatusCode().is2xxSuccessful()
						|| updateResponse.getStatusCode().is2xxSuccessful()
						|| deleteResponse.getStatusCode().is2xxSuccessful()
						|| reorderResponse.getStatusCode().is2xxSuccessful());

		ResponseEntity<ReadableProduct> publicProduct = getPublicProduct(productCode, Constants.DEFAULT_STORE);
		assertEquals(OK, publicProduct.getStatusCode());
		assertNotNull(publicProduct.getBody());
	}

	@Test
	public void updateCannotReassignProductFromAnotherStore() {
		ensureSecondStoreExists();

		String productCode = "authzcross" + UUID.randomUUID().toString().replace("-", "");
		ReadableProduct createdProduct = sampleProduct(productCode);
		PersistableProduct update = product(productCode);
		update.setId(createdProduct.getId());
		update.setSku(productCode);
		update.setPrice(BigDecimal.valueOf(9999));

		ResponseEntity<String> updateResponse = testRestTemplate.exchange(
				"/api/v1/private/product/" + createdProduct.getId() + "?store=" + SECOND_STORE,
				HttpMethod.PUT, new HttpEntity<>(update, getHeader()), String.class);
		assertTrue("Cross-store update should fail with a 4xx response, but was "
				+ updateResponse.getStatusCode(), updateResponse.getStatusCode().is4xxClientError());

		ResponseEntity<ReadableProduct> defaultStoreProduct = getPublicProduct(productCode, Constants.DEFAULT_STORE);
		assertEquals(OK, defaultStoreProduct.getStatusCode());
		ResponseEntity<ReadableProduct> secondStoreProduct = getPublicProduct(productCode, SECOND_STORE);
		assertTrue(secondStoreProduct.getStatusCode().is4xxClientError());
	}

	@Test
	public void updateWithMismatchedIdReturnsBadRequest() {
		String productCode = "authzmismatch" + UUID.randomUUID().toString().replace("-", "");
		ReadableProduct createdProduct = sampleProduct(productCode);
		BigDecimal originalPrice = createdProduct.getPrice();
		PersistableProduct update = product(productCode);
		update.setId(createdProduct.getId() + 1000);
		update.setSku(productCode);
		update.setPrice(BigDecimal.valueOf(9999));

		ResponseEntity<String> updateResponse = testRestTemplate.exchange(
				"/api/v1/private/product/" + createdProduct.getId() + "?store=" + Constants.DEFAULT_STORE,
				HttpMethod.PUT, new HttpEntity<>(update, getHeader()), String.class);
		assertEquals(org.springframework.http.HttpStatus.BAD_REQUEST, updateResponse.getStatusCode());

		ResponseEntity<ReadableProduct> publicProduct = getPublicProduct(productCode, Constants.DEFAULT_STORE);
		assertEquals(OK, publicProduct.getStatusCode());
		assertEquals(0, originalPrice.compareTo(publicProduct.getBody().getPrice()));
	}

	private ResponseEntity<ReadableProduct> getPublicProduct(String sku, String store) {
		return testRestTemplate.getForEntity("/api/v2/product/" + sku + "?store=" + store, ReadableProduct.class);
	}

	private void ensureSecondStoreExists() {
		PersistableAddress address = new PersistableAddress();
		address.setAddress("121212 simple address");
		address.setPostalCode("12345");
		address.setCountry("US");
		address.setCity("FT LD");
		address.setStateProvince("FL");

		PersistableMerchantStore store = new PersistableMerchantStore();
		store.setCode(SECOND_STORE);
		store.setCurrency("CAD");
		store.setDefaultLanguage("en");
		store.setEmail("authz-store@test.com");
		store.setName(SECOND_STORE);
		store.setPhone("444-555-6666");
		store.setSupportedLanguages(Arrays.asList("en"));
		store.setAddress(address);

		ResponseEntity<Void> createResponse = testRestTemplate.exchange(
				"/api/v1/private/store/", HttpMethod.POST,
				new HttpEntity<>(store, getHeader()), Void.class);
		ResponseEntity<String> existingStore = testRestTemplate.getForEntity(
				"/api/v1/store/" + SECOND_STORE, String.class);
		assertTrue("Second store creation failed: " + createResponse.getStatusCode(),
				createResponse.getStatusCode().is2xxSuccessful() || existingStore.getStatusCode() == OK);
	}
}
