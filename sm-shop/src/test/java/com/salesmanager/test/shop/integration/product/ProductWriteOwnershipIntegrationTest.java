package com.salesmanager.test.shop.integration.product;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.springframework.http.HttpStatus.CREATED;
import static org.springframework.http.HttpStatus.FORBIDDEN;
import static org.springframework.http.HttpStatus.NOT_FOUND;
import static org.springframework.http.HttpStatus.OK;
import static org.springframework.http.HttpStatus.UNAUTHORIZED;

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
import com.salesmanager.shop.model.catalog.product.LightPersistableProduct;
import com.salesmanager.shop.model.catalog.product.ReadableProduct;
import com.salesmanager.shop.model.catalog.product.product.PersistableProduct;
import com.salesmanager.shop.model.catalog.product.product.ProductSpecification;
import com.salesmanager.shop.model.customer.PersistableCustomer;
import com.salesmanager.shop.model.customer.address.Address;
import com.salesmanager.shop.model.entity.Entity;
import com.salesmanager.shop.model.references.PersistableAddress;
import com.salesmanager.shop.model.store.PersistableMerchantStore;
import com.salesmanager.shop.store.security.AuthenticationRequest;
import com.salesmanager.shop.store.security.AuthenticationResponse;
import com.salesmanager.test.shop.common.ServicesTestSupport;

@SpringBootTest(classes = ShopApplication.class, webEnvironment = WebEnvironment.RANDOM_PORT)
@RunWith(SpringRunner.class)
public class ProductWriteOwnershipIntegrationTest extends ServicesTestSupport {

	private static final String CHILD_STORE = "CHILDAUTHZ";

	@Test
	public void childStoreCannotWriteParentStoreProduct() {
		ensureChildStore();
		String sku = uniqueCode("childauthz");
		ReadableProduct existing = sampleProduct(sku);
		String url = "/api/v1/private/product/" + existing.getId() + "?store=" + CHILD_STORE;
		PersistableProduct update = writableProduct(sku);
		update.setId(existing.getId());

		ResponseEntity<String> put = testRestTemplate.exchange(url, HttpMethod.PUT,
				new HttpEntity<>(update, getHeader()), String.class);
		ResponseEntity<String> patch = testRestTemplate.exchange(url, HttpMethod.PATCH,
				new HttpEntity<>(lightUpdate("1", false, 1), jsonHeaders(getHeader())), String.class);
		ResponseEntity<String> delete = testRestTemplate.exchange(url, HttpMethod.DELETE,
				new HttpEntity<>(getHeader()), String.class);

		assertEquals(NOT_FOUND, put.getStatusCode());
		assertEquals(NOT_FOUND, patch.getStatusCode());
		assertEquals(NOT_FOUND, delete.getStatusCode());

		ResponseEntity<ReadableProduct> after = getProduct(sku, Constants.DEFAULT_STORE);
		assertEquals(OK, after.getStatusCode());
		assertEquals(0, existing.getPrice().compareTo(after.getBody().getPrice()));
		assertTrue(after.getBody().isAvailable());
	}

	@Test
	public void lightPatchUnknownProductReturns404() {
		ResponseEntity<String> response = testRestTemplate.exchange(
				"/api/v1/private/product/987654321?store=" + Constants.DEFAULT_STORE,
				HttpMethod.PATCH,
				new HttpEntity<>(lightUpdate("1", false, 1), jsonHeaders(getHeader())), String.class);

		assertEquals(NOT_FOUND, response.getStatusCode());
	}

	@Test
	public void lightPatchOwnProductStillWorks() {
		String sku = uniqueCode("patchown");
		ReadableProduct existing = sampleProduct(sku);
		ResponseEntity<String> response = testRestTemplate.exchange(
				"/api/v1/private/product/" + existing.getId() + "?store=" + Constants.DEFAULT_STORE,
				HttpMethod.PATCH,
				new HttpEntity<>(lightUpdate("", true, 42), jsonHeaders(getHeader())), String.class);

		assertTrue("PATCH returned " + response.getStatusCode(), response.getStatusCode().is2xxSuccessful());
	}

	@Test
	public void adminCrudOnOwnStoreStillWorks() {
		String sku = uniqueCode("crudown");
		ReadableProduct existing = sampleProduct(sku);
		String url = "/api/v1/private/product/" + existing.getId() + "?store=" + Constants.DEFAULT_STORE;
		PersistableProduct update = writableProduct(sku);
		update.setId(existing.getId());

		ResponseEntity<String> put = testRestTemplate.exchange(url, HttpMethod.PUT,
				new HttpEntity<>(update, getHeader()), String.class);
		ResponseEntity<String> delete = testRestTemplate.exchange(url, HttpMethod.DELETE,
				new HttpEntity<>(getHeader()), String.class);

		assertEquals(OK, put.getStatusCode());
		assertEquals(OK, delete.getStatusCode());
		assertTrue(getProduct(sku, Constants.DEFAULT_STORE).getStatusCode().is4xxClientError());
	}

	@Test
	public void privateProductWritesRequireAdminToken() {
		String sku = uniqueCode("authrequired");
		PersistableProduct body = writableProduct(sku);
		String url = "/api/v1/private/product?store=" + Constants.DEFAULT_STORE;
		HttpHeaders unauthenticated = jsonHeaders(new HttpHeaders());
		HttpHeaders invalidToken = jsonHeaders(new HttpHeaders());
		invalidToken.setBearerAuth("not-a-jwt");

		ResponseEntity<String> noToken = testRestTemplate.postForEntity(url,
				new HttpEntity<>(body, unauthenticated), String.class);
		ResponseEntity<String> badToken = testRestTemplate.postForEntity(url,
				new HttpEntity<>(body, invalidToken), String.class);

		assertUnauthorizedOrForbidden(noToken);
		assertRejected(badToken);
	}

	private void ensureChildStore() {
		ResponseEntity<String> existing = testRestTemplate.getForEntity(
				"/api/v1/store/" + CHILD_STORE, String.class);
		if (existing.getStatusCode() == OK) {
			return;
		}

		PersistableAddress address = new PersistableAddress();
		address.setAddress("1 Market street");
		address.setPostalCode("12345");
		address.setCountry("US");
		address.setCity("FT LD");
		address.setStateProvince("FL");

		PersistableMerchantStore store = new PersistableMerchantStore();
		store.setCode(CHILD_STORE);
		store.setCurrency("CAD");
		store.setDefaultLanguage("en");
		store.setEmail("childauthz@test.com");
		store.setName(CHILD_STORE);
		store.setPhone("444-555-6666");
		store.setSupportedLanguages(Arrays.asList("en"));
		store.setRetailerStore(Constants.DEFAULT_STORE);
		store.setAddress(address);

		ResponseEntity<String> created = testRestTemplate.postForEntity("/api/v1/private/store",
				new HttpEntity<>(store, getHeader()), String.class);
		assertTrue("store creation returned " + created.getStatusCode(),
				created.getStatusCode().is2xxSuccessful());
	}

	private PersistableProduct writableProduct(String sku) {
		PersistableProduct product = product(sku);
		ProductSpecification specifications = new ProductSpecification();
		specifications.setManufacturer(Manufacturer.DEFAULT_MANUFACTURER);
		product.setProductSpecifications(specifications);
		product.setAvailable(true);
		product.setSku(sku);
		product.setQuantity(100);
		product.setPrice(BigDecimal.TEN);
		return product;
	}

	private static LightPersistableProduct lightUpdate(String price, boolean available, int quantity) {
		LightPersistableProduct product = new LightPersistableProduct();
		product.setPrice(price);
		product.setAvailable(available);
		product.setQuantity(quantity);
		return product;
	}

	private static HttpHeaders jsonHeaders(HttpHeaders headers) {
		headers.setContentType(MediaType.APPLICATION_JSON);
		headers.setAccept(Arrays.asList(MediaType.APPLICATION_JSON));
		return headers;
	}

	private static String uniqueCode(String prefix) {
		return prefix + UUID.randomUUID().toString().replace("-", "").substring(0, 12);
	}

	private ResponseEntity<ReadableProduct> getProduct(String sku, String store) {
		return testRestTemplate.getForEntity("/api/v2/product/" + sku + "?store=" + store,
				ReadableProduct.class);
	}

	private static void assertUnauthorizedOrForbidden(ResponseEntity<?> response) {
		assertFalse("unexpected write status " + response.getStatusCode(),
				response.getStatusCode().is2xxSuccessful());
		assertTrue("expected 401 or 403, got " + response.getStatusCode(),
				response.getStatusCode() == UNAUTHORIZED || response.getStatusCode() == FORBIDDEN);
	}

	private static void assertRejected(ResponseEntity<?> response) {
		assertFalse("unexpected successful status " + response.getStatusCode(),
				response.getStatusCode().is2xxSuccessful());
	}
}
