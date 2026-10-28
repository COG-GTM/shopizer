package com.salesmanager.test.shop.integration.product;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.is;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import java.nio.charset.Charset;
import java.util.Arrays;
import java.util.Collections;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.SpringBootTest.WebEnvironment;
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

@SpringBootTest(classes = ShopApplication.class, webEnvironment = WebEnvironment.RANDOM_PORT)
@RunWith(SpringRunner.class)
public class ProductImageAuthorizationIntegrationTest extends ServicesTestSupport {

	private static final String OTHER_STORE = "imgauthb";
	private static final String OTHER_ADMIN = "admin@imgauthb.com";
	private static final String OTHER_ADMIN_PASSWORD = "Passw0rd";
	private static final String CUSTOMER = "imgauth-customer@test.com";
	private static final String CUSTOMER_PASSWORD = "clear123";

	@Test
	public void imageCannotBeDeletedByCustomerOrOtherStoreAdmin() throws Exception {
		ReadableProduct product = sampleProduct("imgauth");
		Long imageId = uploadImage(product.getId());

		HttpHeaders customer = customerHeader();
		ResponseEntity<String> customerDelete = testRestTemplate.exchange("/api/v1/auth/product/images/" + imageId,
				HttpMethod.DELETE, new HttpEntity<>(customer), String.class);
		assertFalse(customerDelete.getStatusCode().is2xxSuccessful());
		assertTrue(imageExists(product.getId(), imageId));

		ResponseEntity<String> customerPrivateDelete = testRestTemplate.exchange(
				"/api/v1/private/product/image/" + imageId, HttpMethod.DELETE, new HttpEntity<>(customer),
				String.class);
		assertFalse(customerPrivateDelete.getStatusCode().is2xxSuccessful());
		assertTrue(imageExists(product.getId(), imageId));

		createOtherStoreAdmin();
		HttpHeaders otherAdmin = getHeader(OTHER_ADMIN, OTHER_ADMIN_PASSWORD);

		ResponseEntity<String> ownStoreParam = testRestTemplate.exchange(
				"/api/v1/private/product/image/" + imageId + "?store=" + OTHER_STORE, HttpMethod.DELETE,
				new HttpEntity<>(otherAdmin), String.class);
		assertEquals(HttpStatus.NOT_FOUND, ownStoreParam.getStatusCode());
		assertTrue(imageExists(product.getId(), imageId));

		ResponseEntity<String> victimStoreParam = testRestTemplate.exchange(
				"/api/v1/private/product/image/" + imageId + "?store=" + Constants.DEFAULT_STORE, HttpMethod.DELETE,
				new HttpEntity<>(otherAdmin), String.class);
		assertFalse(victimStoreParam.getStatusCode().is2xxSuccessful());
		assertTrue(imageExists(product.getId(), imageId));

		ResponseEntity<String> ownerDelete = testRestTemplate.exchange(
				"/api/v1/private/product/image/" + imageId + "?store=" + Constants.DEFAULT_STORE, HttpMethod.DELETE,
				new HttpEntity<>(getHeader()), String.class);
		assertThat(ownerDelete.getStatusCode(), is(HttpStatus.OK));
		assertFalse(imageExists(product.getId(), imageId));
	}

	private Long uploadImage(Long productId) {
		LinkedMultiValueMap<String, Object> parameters = new LinkedMultiValueMap<>();
		parameters.add("file", new org.springframework.core.io.ClassPathResource("image.jpg"));
		HttpHeaders headers = getHeader();
		headers.setContentType(MediaType.MULTIPART_FORM_DATA);
		ResponseEntity<Void> upload = testRestTemplate.exchange(
				"/api/v1/private/product/" + productId + "/image?store=" + Constants.DEFAULT_STORE, HttpMethod.POST,
				new HttpEntity<>(parameters, headers), Void.class);
		assertThat(upload.getStatusCode(), is(HttpStatus.CREATED));

		ReadableImage[] images = images(productId);
		assertEquals(1, images.length);
		assertNotNull(images[0].getId());
		return images[0].getId();
	}

	private ReadableImage[] images(Long productId) {
		ResponseEntity<ReadableImage[]> response = testRestTemplate.exchange(
				"/api/v1/product/" + productId + "/images?store=" + Constants.DEFAULT_STORE, HttpMethod.GET,
				new HttpEntity<>(new HttpHeaders()), ReadableImage[].class);
		assertThat(response.getStatusCode(), is(HttpStatus.OK));
		return response.getBody();
	}

	private boolean imageExists(Long productId, Long imageId) {
		return Arrays.stream(images(productId)).anyMatch(i -> imageId.equals(i.getId()));
	}

	private HttpHeaders customerHeader() {
		PersistableCustomer customer = new PersistableCustomer();
		customer.setEmailAddress(CUSTOMER);
		customer.setPassword(CUSTOMER_PASSWORD);
		customer.setGender(CustomerGender.M.name());
		customer.setLanguage("en");
		Address billing = new Address();
		billing.setFirstName("img");
		billing.setLastName("auth");
		billing.setCountry("BE");
		customer.setBilling(billing);
		customer.setStoreCode(Constants.DEFAULT_STORE);
		ResponseEntity<PersistableCustomer> register = testRestTemplate.postForEntity("/api/v1/customer/register",
				new HttpEntity<>(customer, getHeader()), PersistableCustomer.class);
		assertThat(register.getStatusCode(), is(HttpStatus.OK));

		ResponseEntity<AuthenticationResponse> login = testRestTemplate.postForEntity("/api/v1/customer/login",
				new HttpEntity<>(new AuthenticationRequest(CUSTOMER, CUSTOMER_PASSWORD)),
				AuthenticationResponse.class);
		assertThat(login.getStatusCode(), is(HttpStatus.OK));
		HttpHeaders headers = new HttpHeaders();
		headers.setContentType(new MediaType("application", "json", Charset.forName("UTF-8")));
		headers.add("Authorization", "Bearer " + login.getBody().getToken());
		return headers;
	}

	private void createOtherStoreAdmin() {
		PersistableAddress address = new PersistableAddress();
		address.setAddress("1 other street");
		address.setPostalCode("12345");
		address.setCountry("US");
		address.setCity("FT LD");
		address.setStateProvince("FL");

		PersistableMerchantStore store = new PersistableMerchantStore();
		store.setCode(OTHER_STORE);
		store.setCurrency("USD");
		store.setDefaultLanguage("en");
		store.setEmail("store@imgauthb.com");
		store.setName(OTHER_STORE);
		store.setPhone("444-555-6666");
		store.setSupportedLanguages(Collections.singletonList("en"));
		store.setAddress(address);
		ResponseEntity<Void> storeResponse = testRestTemplate.exchange("/api/v1/private/store/", HttpMethod.POST,
				new HttpEntity<>(store, getHeader()), Void.class);
		assertThat(storeResponse.getStatusCode(), is(HttpStatus.OK));

		PersistableGroup admin = new PersistableGroup();
		admin.setName("ADMIN");
		PersistableUser user = new PersistableUser();
		user.setUserName(OTHER_ADMIN);
		user.setEmailAddress(OTHER_ADMIN);
		user.setFirstName("Other");
		user.setLastName("Admin");
		user.setDefaultLanguage("en");
		user.setPassword(OTHER_ADMIN_PASSWORD);
		user.setRepeatPassword(OTHER_ADMIN_PASSWORD);
		user.setStore(OTHER_STORE);
		user.setActive(true);
		user.getGroups().add(admin);
		ResponseEntity<String> userResponse = testRestTemplate.exchange("/api/v1/private/user/?store=" + OTHER_STORE,
				HttpMethod.POST, new HttpEntity<>(user, getHeader()), String.class);
		assertThat(userResponse.getBody(), userResponse.getStatusCode(), is(HttpStatus.OK));
	}
}
