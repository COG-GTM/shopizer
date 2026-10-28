package com.salesmanager.test.shop.integration.product;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.Collections;
import java.util.Optional;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.SpringBootTest.WebEnvironment;
import org.springframework.core.io.ClassPathResource;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.test.context.junit.jupiter.SpringExtension;
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
import com.salesmanager.shop.store.security.AuthenticationRequest;
import com.salesmanager.shop.store.security.AuthenticationResponse;
import com.salesmanager.test.shop.common.ServicesTestSupport;

/**
 * Image write routes (upload, PATCH order, both deletes) through the real security chain and H2 (PR #186).
 */
@SpringBootTest(classes = ShopApplication.class, webEnvironment = WebEnvironment.RANDOM_PORT)
@ExtendWith(SpringExtension.class)
public class ProductImageRouteAuthorizationIntegrationTest extends ServicesTestSupport {

	private static final String PASSWORD = "Passw0rd";

	@Test
	public void customerCannotUploadOrReorderImagesThroughRemovedAuthAliases() {
		ReadableProduct product = sampleProduct("imgroute1");
		HttpHeaders customer = customerHeader("imgroute1-customer@test.com");

		ResponseEntity<String> authUpload = upload("/api/v1/auth/product/" + product.getId() + "/image", customer);
		assertFalse(authUpload.getStatusCode().is2xxSuccessful(), authUpload.toString());
		assertEquals(0, images(product.getId()).length);

		ResponseEntity<String> privateUpload = upload(
				"/api/v1/private/product/" + product.getId() + "/image?store=" + Constants.DEFAULT_STORE, customer);
		assertFalse(privateUpload.getStatusCode().is2xxSuccessful(), privateUpload.toString());
		assertEquals(0, images(product.getId()).length);

		Long imageId = ownerUpload(product.getId());

		ResponseEntity<String> authPatch = exchange(
				"/api/v1/auth/product/" + product.getId() + "/image/" + imageId + "?order=7", HttpMethod.PATCH, customer);
		assertFalse(authPatch.getStatusCode().is2xxSuccessful(), authPatch.toString());
		ResponseEntity<String> authDelete = exchange("/api/v1/auth/product/images/" + imageId, HttpMethod.DELETE,
				customer);
		assertFalse(authDelete.getStatusCode().is2xxSuccessful(), authDelete.toString());

		ReadableImage image = image(product.getId(), imageId).orElseThrow(AssertionError::new);
		assertEquals(0, image.getOrder());
	}

	@Test
	public void anonymousImageWritesAreRejectedWith401() {
		ReadableProduct product = sampleProduct("imgroute2");
		Long imageId = ownerUpload(product.getId());
		HttpHeaders anonymous = new HttpHeaders();

		assertEquals(HttpStatus.UNAUTHORIZED,
				exchange("/api/v1/private/product/image/" + imageId, HttpMethod.DELETE, anonymous).getStatusCode());
		assertEquals(HttpStatus.UNAUTHORIZED, exchange("/api/v1/private/product/" + product.getId() + "/image/"
				+ imageId, HttpMethod.DELETE, anonymous).getStatusCode());
		assertEquals(HttpStatus.UNAUTHORIZED, exchange("/api/v1/private/product/" + product.getId() + "/image/"
				+ imageId + "?order=3", HttpMethod.PATCH, anonymous).getStatusCode());
		assertEquals(HttpStatus.UNAUTHORIZED,
				upload("/api/v1/private/product/" + product.getId() + "/image", anonymous).getStatusCode());

		assertEquals(1, images(product.getId()).length);
	}

	@Test
	public void otherStoreAdminCannotUploadReorderOrDeleteByProduct() {
		ReadableProduct product = sampleProduct("imgroute3");
		Long imageId = ownerUpload(product.getId());
		String otherStore = "imgrtc";
		HttpHeaders otherAdmin = otherStoreAdmin(otherStore, "admin@imgrtc.com");
		Long productId = product.getId();

		for (String store : Arrays.asList(otherStore, Constants.DEFAULT_STORE)) {
			ResponseEntity<String> crossUpload = upload("/api/v1/private/product/" + productId + "/image?store=" + store,
					otherAdmin);
			assertFalse(crossUpload.getStatusCode().is2xxSuccessful(), store + " " + crossUpload);

			ResponseEntity<String> crossPatch = exchange(
					"/api/v1/private/product/" + productId + "/image/" + imageId + "?order=9&store=" + store,
					HttpMethod.PATCH, otherAdmin);
			assertFalse(crossPatch.getStatusCode().is2xxSuccessful(), store + " " + crossPatch);

			ResponseEntity<String> crossDelete = exchange(
					"/api/v1/private/product/" + productId + "/image/" + imageId + "?store=" + store, HttpMethod.DELETE,
					otherAdmin);
			assertFalse(crossDelete.getStatusCode().is2xxSuccessful(), store + " " + crossDelete);
		}
		assertEquals(1, images(productId).length);
		assertEquals(0, image(productId, imageId).orElseThrow(AssertionError::new).getOrder());

		ResponseEntity<String> ownerPatch = exchange(
				"/api/v1/private/product/" + productId + "/image/" + imageId + "?order=4&store=" + Constants.DEFAULT_STORE,
				HttpMethod.PATCH, getHeader());
		assertEquals(HttpStatus.OK, ownerPatch.getStatusCode());
		assertEquals(4, image(productId, imageId).orElseThrow(AssertionError::new).getOrder());

		ResponseEntity<String> ownerDelete = exchange(
				"/api/v1/private/product/" + productId + "/image/" + imageId + "?store=" + Constants.DEFAULT_STORE,
				HttpMethod.DELETE, getHeader());
		assertEquals(HttpStatus.OK, ownerDelete.getStatusCode());
		assertEquals(0, images(productId).length);
	}

	@Test
	public void foreignImageDeleteIsIndistinguishableFromMissingImage() {
		ReadableProduct product = sampleProduct("imgroute4");
		Long imageId = ownerUpload(product.getId());
		HttpHeaders otherAdmin = otherStoreAdmin("imgrtd", "admin@imgrtd.com");

		ResponseEntity<String> foreign = exchange("/api/v1/private/product/image/" + imageId + "?store=imgrtd",
				HttpMethod.DELETE, otherAdmin);
		ResponseEntity<String> missing = exchange("/api/v1/private/product/image/987654321?store=imgrtd",
				HttpMethod.DELETE, otherAdmin);

		assertEquals(HttpStatus.NOT_FOUND, foreign.getStatusCode());
		assertEquals(missing.getStatusCode(), foreign.getStatusCode());
		assertTrue(image(product.getId(), imageId).isPresent());
	}

	private ResponseEntity<String> upload(String url, HttpHeaders auth) {
		LinkedMultiValueMap<String, Object> parameters = new LinkedMultiValueMap<>();
		parameters.add("file", new ClassPathResource("image.jpg"));
		HttpHeaders headers = new HttpHeaders();
		headers.putAll(auth);
		headers.setContentType(MediaType.MULTIPART_FORM_DATA);
		return testRestTemplate.exchange(url, HttpMethod.POST, new HttpEntity<>(parameters, headers), String.class);
	}

	private ResponseEntity<String> exchange(String url, HttpMethod method, HttpHeaders headers) {
		return testRestTemplate.exchange(url, method, new HttpEntity<>(headers), String.class);
	}

	private Long ownerUpload(Long productId) {
		ResponseEntity<String> upload = upload(
				"/api/v1/private/product/" + productId + "/image?store=" + Constants.DEFAULT_STORE, getHeader());
		assertEquals(HttpStatus.CREATED, upload.getStatusCode());
		ReadableImage[] images = images(productId);
		assertEquals(1, images.length);
		assertNotNull(images[0].getId());
		return images[0].getId();
	}

	private ReadableImage[] images(Long productId) {
		ResponseEntity<ReadableImage[]> response = testRestTemplate.getForEntity(
				"/api/v1/product/" + productId + "/images?store=" + Constants.DEFAULT_STORE, ReadableImage[].class);
		assertEquals(HttpStatus.OK, response.getStatusCode());
		return response.getBody();
	}

	private Optional<ReadableImage> image(Long productId, Long imageId) {
		return Arrays.stream(images(productId)).filter(i -> imageId.equals(i.getId())).findFirst();
	}

	private HttpHeaders customerHeader(String email) {
		PersistableCustomer customer = new PersistableCustomer();
		customer.setEmailAddress(email);
		customer.setPassword(PASSWORD);
		customer.setGender(CustomerGender.F.name());
		customer.setLanguage("en");
		Address billing = new Address();
		billing.setFirstName("img");
		billing.setLastName("route");
		billing.setCountry("CA");
		customer.setBilling(billing);
		customer.setStoreCode(Constants.DEFAULT_STORE);
		assertEquals(HttpStatus.OK, testRestTemplate.postForEntity("/api/v1/customer/register",
				new HttpEntity<>(customer, getHeader()), String.class).getStatusCode());
		ResponseEntity<AuthenticationResponse> login = testRestTemplate.postForEntity("/api/v1/customer/login",
				new HttpEntity<>(new AuthenticationRequest(email, PASSWORD)), AuthenticationResponse.class);
		assertEquals(HttpStatus.OK, login.getStatusCode());
		return bearer(login.getBody().getToken());
	}

	private HttpHeaders otherStoreAdmin(String storeCode, String adminEmail) {
		PersistableAddress address = new PersistableAddress();
		address.setAddress("2 other street");
		address.setPostalCode("12345");
		address.setCountry("US");
		address.setCity("FT LD");
		address.setStateProvince("FL");
		PersistableMerchantStore store = new PersistableMerchantStore();
		store.setCode(storeCode);
		store.setCurrency("USD");
		store.setDefaultLanguage("en");
		store.setEmail("store@" + storeCode + ".com");
		store.setName(storeCode);
		store.setPhone("444-555-6666");
		store.setSupportedLanguages(Collections.singletonList("en"));
		store.setAddress(address);
		assertEquals(HttpStatus.OK, testRestTemplate.exchange("/api/v1/private/store/", HttpMethod.POST,
				new HttpEntity<>(store, getHeader()), String.class).getStatusCode());

		PersistableGroup admin = new PersistableGroup();
		admin.setName("ADMIN");
		PersistableUser user = new PersistableUser();
		user.setUserName(adminEmail);
		user.setEmailAddress(adminEmail);
		user.setFirstName("Other");
		user.setLastName("Admin");
		user.setDefaultLanguage("en");
		user.setPassword(PASSWORD);
		user.setRepeatPassword(PASSWORD);
		user.setStore(storeCode);
		user.setActive(true);
		user.getGroups().add(admin);
		ResponseEntity<String> created = testRestTemplate.exchange("/api/v1/private/user/?store=" + storeCode,
				HttpMethod.POST, new HttpEntity<>(user, getHeader()), String.class);
		assertEquals(HttpStatus.OK, created.getStatusCode(), created.getBody());
		return getHeader(adminEmail, PASSWORD);
	}

	private static HttpHeaders bearer(String token) {
		HttpHeaders headers = new HttpHeaders();
		headers.setContentType(new MediaType("application", "json", StandardCharsets.UTF_8));
		headers.add("Authorization", "Bearer " + token);
		return headers;
	}
}
