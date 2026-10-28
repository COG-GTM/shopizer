package com.salesmanager.test.shop.integration.product;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.UUID;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.SpringBootTest.WebEnvironment;
import org.springframework.core.io.ClassPathResource;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.test.context.junit4.SpringRunner;
import org.springframework.util.LinkedMultiValueMap;

import com.salesmanager.core.business.constants.Constants;
import com.salesmanager.core.model.customer.CustomerGender;
import com.salesmanager.shop.application.ShopApplication;
import com.salesmanager.shop.model.catalog.product.ReadableImage;
import com.salesmanager.shop.model.catalog.product.product.PersistableProduct;
import com.salesmanager.shop.model.catalog.product.product.ProductSpecification;
import com.salesmanager.shop.model.customer.PersistableCustomer;
import com.salesmanager.shop.model.customer.address.Address;
import com.salesmanager.shop.model.entity.Entity;
import com.salesmanager.shop.model.references.PersistableAddress;
import com.salesmanager.shop.model.security.PersistableGroup;
import com.salesmanager.shop.model.store.PersistableMerchantStore;
import com.salesmanager.shop.model.user.PersistableUser;
import com.salesmanager.shop.store.security.AuthenticationRequest;
import com.salesmanager.shop.store.security.AuthenticationResponse;
import com.salesmanager.test.shop.common.ServicesTestSupport;

/**
 * Integration tests for DELETE /api/v1/private/product/image/{id} store scoping (PR #137).
 */
@SpringBootTest(classes = ShopApplication.class, webEnvironment = WebEnvironment.RANDOM_PORT)
@RunWith(SpringRunner.class)
public class ProductImageDeleteIntegrationTest extends ServicesTestSupport {

	private static final String OTHER_STORE = "imgother";
	private static final String STORE_ADMIN = "imgadmin@test.com";
	private static final String STORE_ADMIN_PASSWORD = "Password1";
	private static final String CUSTOMER = "imgcustomer@test.com";
	private static final String CUSTOMER_PASSWORD = "clear123";

	private static boolean fixturesCreated = false;

	@Test
	public void sameStoreAdminDeletesImage() {
		Long productId = createProduct(Constants.DEFAULT_STORE);
		Long imageId = uploadImage(productId, Constants.DEFAULT_STORE);

		ResponseEntity<String> delete = delete("/api/v1/private/product/image/" + imageId + "?store=DEFAULT",
				getHeader());

		assertEquals(HttpStatus.OK, delete.getStatusCode());
		assertFalse(imageIds(productId, Constants.DEFAULT_STORE).contains(imageId));
	}

	@Test
	public void deleteWithoutStoreParamUsesDefaultStore() {
		Long productId = createProduct(Constants.DEFAULT_STORE);
		Long imageId = uploadImage(productId, Constants.DEFAULT_STORE);

		ResponseEntity<String> delete = delete("/api/v1/private/product/image/" + imageId, getHeader());

		assertEquals(HttpStatus.OK, delete.getStatusCode());
		assertFalse(imageIds(productId, Constants.DEFAULT_STORE).contains(imageId));
	}

	@Test
	public void superadminScopedToDefaultCannotDeleteOtherStoreImage() {
		fixtures();
		Long productId = createProduct(OTHER_STORE);
		Long imageId = uploadImage(productId, OTHER_STORE);

		ResponseEntity<String> delete = delete("/api/v1/private/product/image/" + imageId + "?store=DEFAULT",
				getHeader());

		assertEquals(HttpStatus.NOT_FOUND, delete.getStatusCode());
		assertTrue("image must still exist", imageIds(productId, OTHER_STORE).contains(imageId));

		// the owning store can still delete it
		ResponseEntity<String> ownerDelete = delete(
				"/api/v1/private/product/image/" + imageId + "?store=" + OTHER_STORE, getHeader());
		assertEquals(HttpStatus.OK, ownerDelete.getStatusCode());
		assertFalse(imageIds(productId, OTHER_STORE).contains(imageId));
	}

	@Test
	public void defaultStoreAdminCannotDeleteOtherStoreImage() {
		fixtures();
		Long productId = createProduct(OTHER_STORE);
		Long imageId = uploadImage(productId, OTHER_STORE);
		HttpHeaders storeAdmin = getHeader(STORE_ADMIN, STORE_ADMIN_PASSWORD);

		ResponseEntity<String> ownStore = delete("/api/v1/private/product/image/" + imageId + "?store=DEFAULT",
				storeAdmin);
		assertEquals(HttpStatus.NOT_FOUND, ownStore.getStatusCode());

		ResponseEntity<String> foreignStore = delete(
				"/api/v1/private/product/image/" + imageId + "?store=" + OTHER_STORE, storeAdmin);
		assertEquals(HttpStatus.UNAUTHORIZED, foreignStore.getStatusCode());

		assertTrue("image must still exist", imageIds(productId, OTHER_STORE).contains(imageId));
	}

	@Test
	public void customerCannotDeleteImageThroughAuthPath() {
		fixtures();
		Long productId = createProduct(Constants.DEFAULT_STORE);
		Long imageId = uploadImage(productId, Constants.DEFAULT_STORE);

		ResponseEntity<String> delete = delete("/api/v1/auth/product/images/" + imageId, customerHeader());

		assertFalse("customer delete must not succeed, got " + delete.getStatusCode(),
				delete.getStatusCode().is2xxSuccessful());
		assertTrue("image must still exist", imageIds(productId, Constants.DEFAULT_STORE).contains(imageId));
	}

	@Test
	public void customerTokenRejectedOnPrivateDelete() {
		fixtures();
		Long productId = createProduct(Constants.DEFAULT_STORE);
		Long imageId = uploadImage(productId, Constants.DEFAULT_STORE);

		ResponseEntity<String> delete = delete("/api/v1/private/product/image/" + imageId, customerHeader());

		assertTrue("expected 401/403, got " + delete.getStatusCode(),
				delete.getStatusCode() == HttpStatus.UNAUTHORIZED || delete.getStatusCode() == HttpStatus.FORBIDDEN);
		assertTrue("image must still exist", imageIds(productId, Constants.DEFAULT_STORE).contains(imageId));
	}

	@Test
	public void unauthenticatedDeleteIsRejected() {
		Long productId = createProduct(Constants.DEFAULT_STORE);
		Long imageId = uploadImage(productId, Constants.DEFAULT_STORE);

		ResponseEntity<String> delete = delete("/api/v1/private/product/image/" + imageId, jsonHeaders());

		assertEquals(HttpStatus.UNAUTHORIZED, delete.getStatusCode());
		assertTrue("image must still exist", imageIds(productId, Constants.DEFAULT_STORE).contains(imageId));
	}

	@Test
	public void unknownImageIdIsNotFound() {
		ResponseEntity<String> delete = delete("/api/v1/private/product/image/987654321?store=DEFAULT",
				getHeader());
		assertEquals(HttpStatus.NOT_FOUND, delete.getStatusCode());
	}

	private synchronized void fixtures() {
		if (fixturesCreated) {
			return;
		}
		PersistableAddress address = new PersistableAddress();
		address.setAddress("1 image street");
		address.setPostalCode("12345");
		address.setCountry("US");
		address.setCity("Miami");
		address.setStateProvince("FL");
		PersistableMerchantStore store = new PersistableMerchantStore();
		store.setCode(OTHER_STORE);
		store.setCurrency("USD");
		store.setDefaultLanguage("en");
		store.setEmail("imgother@test.com");
		store.setName(OTHER_STORE);
		store.setPhone("444-555-6666");
		store.setSupportedLanguages(Arrays.asList("en"));
		store.setAddress(address);
		ResponseEntity<String> storeResponse = testRestTemplate.exchange("/api/v1/private/store/", HttpMethod.POST,
				new HttpEntity<>(store, getHeader()), String.class);
		assertEquals(storeResponse.toString(), HttpStatus.OK, storeResponse.getStatusCode());

		ResponseEntity<String> manufacturerResponse = testRestTemplate.exchange(
				"/api/v1/private/manufacturer?store=" + OTHER_STORE, HttpMethod.POST,
				new HttpEntity<>(manufacturer(
						com.salesmanager.core.model.catalog.product.manufacturer.Manufacturer.DEFAULT_MANUFACTURER),
						getHeader()),
				String.class);
		assertTrue(manufacturerResponse.toString(), manufacturerResponse.getStatusCode().is2xxSuccessful());

		PersistableUser user = new PersistableUser();
		user.setDefaultLanguage("en");
		user.setEmailAddress(STORE_ADMIN);
		user.setFirstName("Image");
		user.setLastName("Admin");
		user.setUserName(STORE_ADMIN);
		user.setPassword(STORE_ADMIN_PASSWORD);
		user.setRepeatPassword(STORE_ADMIN_PASSWORD);
		user.setActive(true);
		PersistableGroup g = new PersistableGroup();
		g.setName("ADMIN");
		user.getGroups().add(g);
		ResponseEntity<String> userResponse = testRestTemplate.exchange("/api/v1/private/user/?store=DEFAULT",
				HttpMethod.POST, new HttpEntity<>(user, getHeader()), String.class);
		assertEquals(userResponse.toString(), HttpStatus.OK, userResponse.getStatusCode());

		PersistableCustomer customer = new PersistableCustomer();
		customer.setEmailAddress(CUSTOMER);
		customer.setPassword(CUSTOMER_PASSWORD);
		customer.setGender(CustomerGender.M.name());
		customer.setLanguage("en");
		Address billing = new Address();
		billing.setFirstName("Image");
		billing.setLastName("Customer");
		billing.setCountry("CA");
		customer.setBilling(billing);
		customer.setStoreCode(Constants.DEFAULT_STORE);
		ResponseEntity<String> register = testRestTemplate.postForEntity("/api/v1/customer/register",
				new HttpEntity<>(customer, jsonHeaders()), String.class);
		assertEquals(register.toString(), HttpStatus.OK, register.getStatusCode());
		fixturesCreated = true;
	}

	private HttpHeaders customerHeader() {
		ResponseEntity<AuthenticationResponse> login = testRestTemplate.postForEntity("/api/v1/customer/login",
				new HttpEntity<>(new AuthenticationRequest(CUSTOMER, CUSTOMER_PASSWORD)),
				AuthenticationResponse.class);
		assertEquals(HttpStatus.OK, login.getStatusCode());
		HttpHeaders headers = jsonHeaders();
		headers.add("Authorization", "Bearer " + login.getBody().getToken());
		return headers;
	}

	private static HttpHeaders jsonHeaders() {
		HttpHeaders headers = new HttpHeaders();
		headers.setContentType(new MediaType("application", "json", StandardCharsets.UTF_8));
		return headers;
	}

	private ResponseEntity<String> delete(String url, HttpHeaders headers) {
		return testRestTemplate.exchange(url, HttpMethod.DELETE, new HttpEntity<>(headers), String.class);
	}

	private Long createProduct(String store) {
		String sku = "img" + UUID.randomUUID().toString().replace("-", "").substring(0, 10);
		PersistableProduct product = product(sku);
		ProductSpecification specifications = new ProductSpecification();
		specifications.setManufacturer(
				com.salesmanager.core.model.catalog.product.manufacturer.Manufacturer.DEFAULT_MANUFACTURER);
		product.setProductSpecifications(specifications);
		product.setPrice(BigDecimal.TEN);
		product.setSku(sku);
		ResponseEntity<Entity> response = testRestTemplate.postForEntity("/api/v1/private/product?store=" + store,
				new HttpEntity<>(product, getHeader()), Entity.class);
		assertEquals(response.toString(), HttpStatus.CREATED, response.getStatusCode());
		assertNotNull(response.getBody().getId());
		return response.getBody().getId();
	}

	private Long uploadImage(Long productId, String store) {
		LinkedMultiValueMap<String, Object> parameters = new LinkedMultiValueMap<>();
		parameters.add("file", new ClassPathResource("image.jpg"));
		HttpHeaders headers = getHeader();
		headers.setContentType(MediaType.MULTIPART_FORM_DATA);
		ResponseEntity<String> upload = testRestTemplate.exchange(
				"/api/v1/private/product/" + productId + "/image?store=" + store, HttpMethod.POST,
				new HttpEntity<>(parameters, headers), String.class);
		assertEquals(upload.toString(), HttpStatus.CREATED, upload.getStatusCode());
		java.util.List<Long> ids = imageIds(productId, store);
		assertFalse("uploaded image not listed", ids.isEmpty());
		return ids.get(ids.size() - 1);
	}

	private java.util.List<Long> imageIds(Long productId, String store) {
		ResponseEntity<ReadableImage[]> images = testRestTemplate.exchange(
				"/api/v1/product/" + productId + "/images?store=" + store, HttpMethod.GET,
				new HttpEntity<>(jsonHeaders()), ReadableImage[].class);
		assertEquals(HttpStatus.OK, images.getStatusCode());
		java.util.List<Long> ids = new java.util.ArrayList<>();
		for (ReadableImage i : images.getBody()) {
			ids.add(i.getId());
		}
		return ids;
	}
}
