package com.salesmanager.test.shop.integration.product;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;
import static org.springframework.http.HttpMethod.DELETE;
import static org.springframework.http.HttpMethod.PATCH;
import static org.springframework.http.HttpMethod.POST;
import static org.springframework.http.HttpStatus.CREATED;
import static org.springframework.http.HttpStatus.NOT_FOUND;
import static org.springframework.http.HttpStatus.OK;
import static org.springframework.http.HttpStatus.UNAUTHORIZED;

import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.SpringBootTest.WebEnvironment;
import org.springframework.core.io.ClassPathResource;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.test.context.junit4.SpringRunner;
import org.springframework.util.LinkedMultiValueMap;

import com.salesmanager.core.business.constants.Constants;
import com.salesmanager.core.model.customer.CustomerGender;
import com.salesmanager.shop.application.ShopApplication;
import com.salesmanager.shop.model.catalog.product.ReadableImage;
import com.salesmanager.shop.model.catalog.product.ReadableProduct;
import com.salesmanager.shop.model.customer.PersistableCustomer;
import com.salesmanager.shop.model.customer.address.Address;
import com.salesmanager.shop.model.references.PersistableAddress;
import com.salesmanager.shop.model.security.PersistableGroup;
import com.salesmanager.shop.model.store.PersistableMerchantStore;
import com.salesmanager.shop.model.user.PersistableUser;
import com.salesmanager.shop.model.user.ReadableUser;
import com.salesmanager.shop.store.security.AuthenticationRequest;
import com.salesmanager.shop.store.security.AuthenticationResponse;
import com.salesmanager.test.shop.common.ServicesTestSupport;

@SpringBootTest(classes = ShopApplication.class, webEnvironment = WebEnvironment.RANDOM_PORT)
@RunWith(SpringRunner.class)
public class ProductImageAuthorizationIntegrationTest extends ServicesTestSupport {

	private static final String PASSWORD = "Password1";

	@Test
	public void anonymousAndInvalidBearerRequestsCannotChangeImages() {
		Long productId = createProduct("anonymous-image-");
		ReadableImage initialImage = uploadAndGetImage(productId, getHeader());

		int deleteNumber = 0;
		for (HttpMethod method : Arrays.asList(POST, DELETE, PATCH, DELETE)) {
			boolean legacyDelete = DELETE.equals(method) && deleteNumber++ == 0;
			String path = privateWritePath(method, productId, initialImage.getId(), legacyDelete);
			ResponseEntity<Void> anonymous = write(method, path, new HttpHeaders());
			assertEquals(path, UNAUTHORIZED, anonymous.getStatusCode());
			assertImageCount(productId, 1);

			HttpHeaders badToken = new HttpHeaders();
			badToken.setBearerAuth("invalid-token");
			ResponseEntity<Void> invalid = write(method, path, badToken);
			assertFalse(path + " returned " + invalid.getStatusCode(),
					invalid.getStatusCode().is2xxSuccessful());
			assertImageCount(productId, 1);
		}
	}

	@Test
	public void customerCannotUsePrivateWritesOrLegacyPatchAlias() {
		Long productId = createProduct("customer-image-");
		ReadableImage initialImage = uploadAndGetImage(productId, getHeader());
		HttpHeaders customerHeaders = customerHeaders();

		int deleteNumber = 0;
		for (HttpMethod method : Arrays.asList(POST, DELETE, PATCH, DELETE)) {
			boolean legacyDelete = DELETE.equals(method) && deleteNumber++ == 0;
			String path = privateWritePath(method, productId, initialImage.getId(), legacyDelete);
			ResponseEntity<Void> response = write(method, path, customerHeaders);
			assertFalse(path + " returned " + response.getStatusCode(),
					response.getStatusCode().is2xxSuccessful());
			assertImageCount(productId, 1);
			assertEquals(0, getImages(productId)[0].getOrder());
		}

		ResponseEntity<Void> oldPatch = testRestTemplate.exchange(
				"/api/v1/auth/product/" + productId + "/image/" + initialImage.getId()
						+ "?order=9&store=" + Constants.DEFAULT_STORE,
				PATCH, new HttpEntity<>(customerHeaders), Void.class);
		assertFalse(oldPatch.getStatusCode().is2xxSuccessful());
		assertEquals(0, getImages(productId)[0].getOrder());
	}

	@Test
	public void onlyCatalogueAdminsCanManageImagesAndCatalogueAdminCanUseBothDeletes() {
		Long productId = createProduct("group-image-");
		ReadableImage initialImage = uploadAndGetImage(productId, getHeader());

		HttpHeaders orderAdmin = createUserHeaders("ADMIN_ORDER", Constants.DEFAULT_STORE);
		int deleteNumber = 0;
		for (HttpMethod method : Arrays.asList(POST, DELETE, PATCH, DELETE)) {
			boolean legacyDelete = DELETE.equals(method) && deleteNumber++ == 0;
			String path = privateWritePath(method, productId, initialImage.getId(), legacyDelete);
			ResponseEntity<Void> response = write(method, path, orderAdmin);
			assertFalse(path + " returned " + response.getStatusCode(),
					response.getStatusCode().is2xxSuccessful());
			assertImageCount(productId, 1);
			assertEquals(0, getImages(productId)[0].getOrder());
		}

		HttpHeaders catalogueAdmin = createUserHeaders("ADMIN_CATALOGUE", Constants.DEFAULT_STORE);
		ResponseEntity<Void> upload = write(POST, privateWritePath(POST, productId,
				initialImage.getId(), false),
				catalogueAdmin);
		assertEquals(CREATED, upload.getStatusCode());
		ReadableImage secondImage = getImages(productId)[1];

		ResponseEntity<Void> patch = testRestTemplate.exchange(
				"/api/v1/private/product/" + productId + "/image/" + secondImage.getId()
						+ "?order=6&store=" + Constants.DEFAULT_STORE,
				PATCH, new HttpEntity<>(catalogueAdmin), Void.class);
		assertEquals(OK, patch.getStatusCode());
		assertEquals(6, imageById(productId, secondImage.getId()).getOrder());

		ResponseEntity<Void> productDelete = testRestTemplate.exchange(
				"/api/v1/private/product/" + productId + "/image/" + secondImage.getId()
						+ "?store=" + Constants.DEFAULT_STORE,
				DELETE, new HttpEntity<>(catalogueAdmin), Void.class);
		assertTrue(productDelete.getStatusCode().is2xxSuccessful());

		ReadableImage legacyDeleteImage = uploadAndGetImage(productId, catalogueAdmin);
		ResponseEntity<Void> legacyDelete = testRestTemplate.exchange(
				"/api/v1/private/product/image/" + legacyDeleteImage.getId()
						+ "?store=" + Constants.DEFAULT_STORE,
				DELETE, new HttpEntity<>(catalogueAdmin), Void.class);
		assertTrue(legacyDelete.getStatusCode().is2xxSuccessful());
		assertImageCount(productId, 1);
	}

	@Test
	public void legacyDeleteEnforcesStoreOwnershipAndAllowsDefaultSuperadmin() {
		Long productId = createProduct("tenant-image-");
		ReadableImage image = uploadAndGetImage(productId, getHeader());
		String otherStore = createStoreCode();
		createStore(otherStore);
		HttpHeaders otherStoreAdmin = createUserHeaders("ADMIN", otherStore);

		ResponseEntity<Void> mismatchedImageStore = testRestTemplate.exchange(
				"/api/v1/private/product/image/" + image.getId() + "?store=" + otherStore,
				DELETE, new HttpEntity<>(otherStoreAdmin), Void.class);
		assertEquals(NOT_FOUND, mismatchedImageStore.getStatusCode());
		assertImageCount(productId, 1);

		ResponseEntity<Void> unauthorizedStore = testRestTemplate.exchange(
				"/api/v1/private/product/image/" + image.getId()
						+ "?store=" + Constants.DEFAULT_STORE,
				DELETE, new HttpEntity<>(otherStoreAdmin), Void.class);
		assertEquals(UNAUTHORIZED, unauthorizedStore.getStatusCode());
		assertImageCount(productId, 1);

		ResponseEntity<Void> superadminDelete = testRestTemplate.exchange(
				"/api/v1/private/product/image/" + image.getId()
						+ "?store=" + Constants.DEFAULT_STORE,
				DELETE, new HttpEntity<>(getHeader()), Void.class);
		assertTrue(superadminDelete.getStatusCode().is2xxSuccessful());
		assertImageCount(productId, 0);
	}

	@Test
	public void adminCanPatchOrderAndDeleteThroughProductImageEndpoint() {
		Long productId = createProduct("happy-image-");
		HttpHeaders admin = getHeader();
		ReadableImage image = uploadAndGetImage(productId, admin);

		ResponseEntity<Void> patch = testRestTemplate.exchange(
				"/api/v1/private/product/" + productId + "/image/" + image.getId()
						+ "?order=3&store=" + Constants.DEFAULT_STORE,
				PATCH, new HttpEntity<>(admin), Void.class);
		assertEquals(OK, patch.getStatusCode());
		assertEquals(3, getImages(productId)[0].getOrder());

		ResponseEntity<Void> delete = testRestTemplate.exchange(
				"/api/v1/private/product/" + productId + "/image/" + image.getId()
						+ "?store=" + Constants.DEFAULT_STORE,
				DELETE, new HttpEntity<>(admin), Void.class);
		assertTrue(delete.getStatusCode().is2xxSuccessful());
		assertImageCount(productId, 0);
	}

	private Long createProduct(String prefix) {
		ReadableProduct product = sampleProduct(unique(prefix));
		assertNotNull(product);
		return product.getId();
	}

	private ReadableImage uploadAndGetImage(Long productId, HttpHeaders headers) {
		Set<Long> existingIds = new HashSet<>();
		for (ReadableImage image : getImages(productId)) {
			existingIds.add(image.getId());
		}
		ResponseEntity<Void> response = write(POST,
				"/api/v1/private/product/" + productId + "/image?store=" + Constants.DEFAULT_STORE,
				headers);
		assertEquals(CREATED, response.getStatusCode());
		ReadableImage[] images = getImages(productId);
		return Arrays.stream(images).filter(image -> !existingIds.contains(image.getId()))
				.findFirst().orElseThrow(AssertionError::new);
	}

	private ResponseEntity<Void> write(HttpMethod method, String path, HttpHeaders headers) {
		if (POST.equals(method)) {
			LinkedMultiValueMap<String, Object> form = new LinkedMultiValueMap<>();
			form.add("file", new ClassPathResource("image.jpg"));
			HttpHeaders multipartHeaders = new HttpHeaders();
			multipartHeaders.putAll(headers);
			multipartHeaders.setContentType(MediaType.MULTIPART_FORM_DATA);
			return testRestTemplate.exchange(path, method,
					new HttpEntity<>(form, multipartHeaders), Void.class);
		}
		return testRestTemplate.exchange(path, method, new HttpEntity<>(headers), Void.class);
	}

	private String privateWritePath(HttpMethod method, Long productId, Long imageId,
			boolean legacyDelete) {
		String store = "?store=" + Constants.DEFAULT_STORE;
		if (POST.equals(method)) {
			return "/api/v1/private/product/" + productId + "/image" + store;
		}
		if (PATCH.equals(method)) {
			return "/api/v1/private/product/" + productId + "/image/" + imageId
					+ "?order=5&store=" + Constants.DEFAULT_STORE;
		}
		if (DELETE.equals(method) && legacyDelete) {
			return "/api/v1/private/product/image/" + imageId + store;
		}
		return "/api/v1/private/product/" + productId + "/image/" + imageId + store;
	}

	private ReadableImage[] getImages(Long productId) {
		ResponseEntity<ReadableImage[]> response = testRestTemplate.getForEntity(
				"/api/v1/product/" + productId + "/images?store=" + Constants.DEFAULT_STORE,
				ReadableImage[].class);
		assertEquals(OK, response.getStatusCode());
		return response.getBody();
	}

	private ReadableImage imageById(Long productId, Long imageId) {
		return Arrays.stream(getImages(productId)).filter(image -> imageId.equals(image.getId()))
				.findFirst().orElseThrow(AssertionError::new);
	}

	private void assertImageCount(Long productId, int expected) {
		assertEquals(expected, getImages(productId).length);
	}

	private HttpHeaders customerHeaders() {
		String email = unique("image-customer-") + "@test.com";
		PersistableCustomer customer = new PersistableCustomer();
		customer.setEmailAddress(email);
		customer.setPassword("clear123");
		customer.setGender(CustomerGender.M.name());
		customer.setLanguage("en");
		customer.setStoreCode(Constants.DEFAULT_STORE);
		Address billing = new Address();
		billing.setFirstName("image");
		billing.setLastName("customer");
		billing.setCountry("BE");
		customer.setBilling(billing);
		ResponseEntity<PersistableCustomer> registration = testRestTemplate.postForEntity(
				"/api/v1/customer/register", new HttpEntity<>(customer, getHeader()),
				PersistableCustomer.class);
		assertEquals(OK, registration.getStatusCode());

		ResponseEntity<AuthenticationResponse> login = testRestTemplate.postForEntity(
				"/api/v1/customer/login",
				new HttpEntity<>(new AuthenticationRequest(email, "clear123")),
				AuthenticationResponse.class);
		assertEquals(OK, login.getStatusCode());
		assertNotNull(login.getBody());
		HttpHeaders headers = new HttpHeaders();
		headers.setBearerAuth(login.getBody().getToken());
		return headers;
	}

	private HttpHeaders createUserHeaders(String group, String store) {
		String email = unique("image-" + group.toLowerCase() + "-") + "@test.com";
		PersistableUser user = new PersistableUser();
		user.setDefaultLanguage("en");
		user.setEmailAddress(email);
		user.setFirstName("Image");
		user.setLastName("Admin");
		user.setUserName(email);
		user.setPassword(PASSWORD);
		user.setRepeatPassword(PASSWORD);
		user.setActive(true);
		user.setStore(store);
		PersistableGroup persistableGroup = new PersistableGroup();
		persistableGroup.setName(group);
		user.getGroups().add(persistableGroup);
		ResponseEntity<ReadableUser> created = testRestTemplate.exchange(
				"/api/v1/private/user/", POST, new HttpEntity<>(user, getHeader()),
				ReadableUser.class);
		assertEquals(OK, created.getStatusCode());
		assertNotNull(created.getBody());

		ResponseEntity<AuthenticationResponse> login = testRestTemplate.postForEntity(
				"/api/v1/private/login",
				new HttpEntity<>(new AuthenticationRequest(email, PASSWORD)),
				AuthenticationResponse.class);
		assertEquals(OK, login.getStatusCode());
		assertNotNull(login.getBody());
		HttpHeaders headers = new HttpHeaders();
		headers.setBearerAuth(login.getBody().getToken());
		return headers;
	}

	private String createStoreCode() {
		return unique("tenant-");
	}

	private void createStore(String code) {
		PersistableAddress address = new PersistableAddress();
		address.setAddress("2 test lane");
		address.setPostalCode("12345");
		address.setCountry("US");
		address.setCity("Test City");
		address.setStateProvince("CA");
		PersistableMerchantStore store = new PersistableMerchantStore();
		store.setCode(code);
		store.setCurrency("USD");
		store.setDefaultLanguage("en");
		store.setEmail(code + "@test.com");
		store.setName(code);
		store.setPhone("444-555-6666");
		store.setSupportedLanguages(Arrays.asList("en"));
		store.setAddress(address);
		ResponseEntity<Void> response = testRestTemplate.exchange(
				"/api/v1/private/store", POST, new HttpEntity<>(store, getHeader()), Void.class);
		assertEquals(OK, response.getStatusCode());
	}

	private String unique(String prefix) {
		return prefix + UUID.randomUUID().toString().replace("-", "");
	}
}
