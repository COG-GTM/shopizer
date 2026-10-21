package com.salesmanager.test.shop.integration.product;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import javax.inject.Inject;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.SpringBootTest.WebEnvironment;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.core.io.ClassPathResource;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.test.context.junit4.SpringRunner;
import org.springframework.util.LinkedMultiValueMap;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.salesmanager.core.model.customer.CustomerGender;
import com.salesmanager.shop.application.ShopApplication;
import com.salesmanager.shop.model.catalog.category.Category;
import com.salesmanager.shop.model.catalog.category.CategoryDescription;
import com.salesmanager.shop.model.catalog.category.PersistableCategory;
import com.salesmanager.shop.model.catalog.manufacturer.PersistableManufacturer;
import com.salesmanager.shop.model.catalog.product.ReadableImage;
import com.salesmanager.shop.model.catalog.product.product.PersistableProduct;
import com.salesmanager.shop.model.catalog.product.product.ProductSpecification;
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
public class ProductImageSecurityIntegrationTest extends ServicesTestSupport {

	@Inject
	private TestRestTemplate testRestTemplate;

	@Test
	public void customerCannotUseRemovedAuthImageAliases() {
		Long productId = createDefaultProduct();
		uploadImage(productId, "DEFAULT", getHeader());
		ReadableImage[] before = fetchImages(productId, "DEFAULT");
		assertEquals(1, before.length);
		String customerToken = registerCustomer();
		HttpHeaders customerHeaders = bearerHeaders(customerToken);
		HttpHeaders multipartHeaders = new HttpHeaders();
		multipartHeaders.putAll(customerHeaders);
		multipartHeaders.setContentType(MediaType.MULTIPART_FORM_DATA);

		LinkedMultiValueMap<String, Object> parameters = multipartFile();
		ResponseEntity<Void> uploadResponse = testRestTemplate.exchange(
				"/api/v1/auth/product/" + productId + "/image", HttpMethod.POST,
				new HttpEntity<>(parameters, multipartHeaders), Void.class);
		ResponseEntity<Void> deleteResponse = testRestTemplate.exchange(
				"/api/v1/auth/product/images/" + before[0].getId(), HttpMethod.DELETE,
				new HttpEntity<>(customerHeaders), Void.class);
		ResponseEntity<Void> patchResponse = testRestTemplate.exchange(
				"/api/v1/auth/product/" + productId + "/image/" + before[0].getId() + "?order=5",
				HttpMethod.PATCH, new HttpEntity<>(customerHeaders), Void.class);

		List<Integer> actualStatuses = Arrays.asList(uploadResponse.getStatusCodeValue(),
				deleteResponse.getStatusCodeValue(), patchResponse.getStatusCodeValue());
		System.out.println("Product image auth alias statuses POST/DELETE/PATCH=" + actualStatuses);
		assertEquals("Expected removed aliases to return 404; observed " + actualStatuses,
				Arrays.asList(404, 404, 404), actualStatuses);
		ReadableImage[] after = fetchImages(productId, "DEFAULT");
		assertEquals(imageState(before), imageState(after));
	}

	@Test
	public void privateImageDeleteRequiresAdminAuthentication() {
		Long productId = createDefaultProduct();
		uploadImage(productId, "DEFAULT", getHeader());
		ReadableImage[] before = fetchImages(productId, "DEFAULT");
		Long imageId = before[0].getId();
		String customerToken = registerCustomer();

		ResponseEntity<String> noToken = testRestTemplate.exchange("/api/v1/private/product/image/" + imageId,
				HttpMethod.DELETE, new HttpEntity<>(new HttpHeaders()), String.class);
		HttpHeaders invalidHeaders = new HttpHeaders();
		invalidHeaders.setBearerAuth("garbage");
		ResponseEntity<String> invalidToken = testRestTemplate.exchange(
				"/api/v1/private/product/image/" + imageId, HttpMethod.DELETE,
				new HttpEntity<>(invalidHeaders), String.class);
		ResponseEntity<String> customerTokenResponse = testRestTemplate.exchange(
				"/api/v1/private/product/image/" + imageId, HttpMethod.DELETE,
				new HttpEntity<>(bearerHeaders(customerToken)), String.class);

		System.out.println("Product image private DELETE statuses no-token/invalid/customer="
				+ noToken.getStatusCodeValue() + "/" + invalidToken.getStatusCodeValue() + "/"
				+ customerTokenResponse.getStatusCodeValue() + " bodies="
				+ noToken.getBody() + "/" + invalidToken.getBody() + "/" + customerTokenResponse.getBody());
		assertEquals("No-token DELETE response: " + noToken.getBody(),
				HttpStatus.UNAUTHORIZED, noToken.getStatusCode());
		assertFalse("A garbage bearer token must not authenticate",
				invalidToken.getStatusCode().is2xxSuccessful());
		assertTrue(customerTokenResponse.getStatusCode() == HttpStatus.UNAUTHORIZED
				|| customerTokenResponse.getStatusCode() == HttpStatus.FORBIDDEN);
		assertEquals(imageState(before), imageState(fetchImages(productId, "DEFAULT")));
	}

	@Test
	public void adminCanPatchAndDeleteImagesAndProductScopedDeleteRemainsAvailable() {
		Long productId = createDefaultProduct();
		HttpHeaders adminHeaders = getHeader();
		uploadImage(productId, "DEFAULT", adminHeaders);
		uploadImage(productId, "DEFAULT", adminHeaders);
		ReadableImage[] images = fetchImages(productId, "DEFAULT");
		assertEquals(2, images.length);

		ResponseEntity<Void> patchResponse = testRestTemplate.exchange(
				"/api/v1/private/product/" + productId + "/image/" + images[0].getId()
						+ "?order=3&store=DEFAULT",
				HttpMethod.PATCH, new HttpEntity<>(adminHeaders), Void.class);
		assertEquals(HttpStatus.OK, patchResponse.getStatusCode());
		ReadableImage[] afterPatch = fetchImages(productId, "DEFAULT");
		assertTrue(Arrays.stream(afterPatch).anyMatch(image -> image.getId().equals(images[0].getId())
				&& image.getOrder() == 3));

		ResponseEntity<Void> scopedDelete = testRestTemplate.exchange(
				"/api/v1/private/product/" + productId + "/image/" + images[1].getId() + "?store=DEFAULT",
				HttpMethod.DELETE, new HttpEntity<>(adminHeaders), Void.class);
		assertEquals(HttpStatus.OK, scopedDelete.getStatusCode());

		ResponseEntity<Void> delete = testRestTemplate.exchange(
				"/api/v1/private/product/image/" + images[0].getId(), HttpMethod.DELETE,
				new HttpEntity<>(adminHeaders), Void.class);
		ResponseEntity<Void> deleteAgain = testRestTemplate.exchange(
				"/api/v1/private/product/image/" + images[0].getId(), HttpMethod.DELETE,
				new HttpEntity<>(adminHeaders), Void.class);
		ResponseEntity<Void> deleteMissing = testRestTemplate.exchange(
				"/api/v1/private/product/image/999999", HttpMethod.DELETE,
				new HttpEntity<>(adminHeaders), Void.class);
		assertEquals(HttpStatus.OK, delete.getStatusCode());
		assertEquals(HttpStatus.NOT_FOUND, deleteAgain.getStatusCode());
		assertEquals(HttpStatus.NOT_FOUND, deleteMissing.getStatusCode());
		assertEquals(0, fetchImages(productId, "DEFAULT").length);
	}

	@Test
	public void imageDeleteEnforcesStoreOwnershipAndNonSuperAdminStoreAuthorization() throws Exception {
		String otherStore = "imgstore" + System.nanoTime();
		createStore(otherStore);
		Long productId = createProductForStore(otherStore, "imgproduct" + System.nanoTime());
		HttpHeaders adminHeaders = getHeader();
		uploadImage(productId, otherStore, adminHeaders);
		ReadableImage[] images = fetchImages(productId, otherStore);
		assertEquals(1, images.length);
		Long imageId = images[0].getId();

		ResponseEntity<Void> wrongStore = testRestTemplate.exchange(
				"/api/v1/private/product/image/" + imageId + "?store=DEFAULT", HttpMethod.DELETE,
				new HttpEntity<>(adminHeaders), Void.class);
		assertEquals(HttpStatus.NOT_FOUND, wrongStore.getStatusCode());
		assertEquals(1, fetchImages(productId, otherStore).length);

		HttpHeaders regularAdminHeaders = bearerHeaders(createAdminUserToken());
		ResponseEntity<Void> unauthorizedStore = testRestTemplate.exchange(
				"/api/v1/private/product/image/" + imageId + "?store=" + otherStore, HttpMethod.DELETE,
				new HttpEntity<>(regularAdminHeaders), Void.class);
		ResponseEntity<Void> wrongImageStore = testRestTemplate.exchange(
				"/api/v1/private/product/image/" + imageId + "?store=DEFAULT", HttpMethod.DELETE,
				new HttpEntity<>(regularAdminHeaders), Void.class);
		System.out.println("Product image store DELETE statuses superadmin-wrong-store/non-superadmin-other/"
				+ "non-superadmin-default=" + wrongStore.getStatusCodeValue() + "/"
				+ unauthorizedStore.getStatusCodeValue() + "/" + wrongImageStore.getStatusCodeValue());
		assertTrue(unauthorizedStore.getStatusCode().is4xxClientError());
		assertEquals(HttpStatus.NOT_FOUND, wrongImageStore.getStatusCode());
		assertEquals(1, fetchImages(productId, otherStore).length);

		ResponseEntity<Void> correctStore = testRestTemplate.exchange(
				"/api/v1/private/product/image/" + imageId + "?store=" + otherStore, HttpMethod.DELETE,
				new HttpEntity<>(adminHeaders), Void.class);
		assertEquals(HttpStatus.OK, correctStore.getStatusCode());
		assertEquals(0, fetchImages(productId, otherStore).length);
	}

	private Long createDefaultProduct() {
		String code = "imgproduct" + System.nanoTime();
		return sampleProduct(code).getId();
	}

	private Long createProductForStore(String storeCode, String code) throws Exception {
		PersistableCategory category = new PersistableCategory();
		category.setCode(code);
		category.setSortOrder(1);
		category.setVisible(true);
		category.setDepth(4);
		category.setParent(new Category());
		CategoryDescription categoryDescription = new CategoryDescription();
		categoryDescription.setLanguage("en");
		categoryDescription.setName(code);
		categoryDescription.setFriendlyUrl(code);
		categoryDescription.setTitle(code);
		category.setDescriptions(Collections.singletonList(categoryDescription));
		ResponseEntity<String> categoryResponse = testRestTemplate.postForEntity(
				"/api/v1/private/category?store=" + storeCode,
				new HttpEntity<>(category, getHeader()), String.class);
		assertEquals("Category creation response: " + categoryResponse.getBody(),
				HttpStatus.CREATED, categoryResponse.getStatusCode());
		PersistableCategory persistedCategory = new ObjectMapper().readValue(
				categoryResponse.getBody(), PersistableCategory.class);

		PersistableManufacturer manufacturer = manufacturer("brand-" + code);
		ResponseEntity<PersistableManufacturer> manufacturerResponse = testRestTemplate.postForEntity(
				"/api/v1/private/manufacturer?store=" + storeCode,
				new HttpEntity<>(manufacturer, getHeader()), PersistableManufacturer.class);
		assertEquals(HttpStatus.CREATED, manufacturerResponse.getStatusCode());
		assertNotNull(manufacturerResponse.getBody());

		PersistableProduct product = product(code);
		product.setCategories(new ArrayList<>(Collections.singletonList(persistedCategory)));
		ProductSpecification specifications = new ProductSpecification();
		specifications.setManufacturer(manufacturerResponse.getBody().getCode());
		product.setProductSpecifications(specifications);
		product.setAvailable(true);
		product.setPrice(BigDecimal.TEN);
		product.setSku(code);
		product.setQuantity(100);
		ResponseEntity<String> productResponse = testRestTemplate.postForEntity(
				"/api/v1/private/product?store=" + storeCode,
				new HttpEntity<>(product, getHeader()), String.class);
		assertEquals("Product creation response: " + productResponse.getBody(),
				HttpStatus.CREATED, productResponse.getStatusCode());
		Long productId = new ObjectMapper().readTree(productResponse.getBody()).path("id").asLong();
		assertTrue(productId > 0);
		return productId;
	}

	private void createStore(String storeCode) {
		PersistableAddress address = new PersistableAddress();
		address.setAddress("121212 image security test");
		address.setPostalCode("12345");
		address.setCountry("US");
		address.setCity("Test City");
		address.setStateProvince("CA");

		PersistableMerchantStore store = new PersistableMerchantStore();
		store.setCode(storeCode);
		store.setCurrency("CAD");
		store.setDefaultLanguage("en");
		store.setEmail(storeCode + "@example.test");
		store.setName(storeCode);
		store.setPhone("444-555-6666");
		store.setSupportedLanguages(Collections.singletonList("en"));
		store.setAddress(address);
		ResponseEntity<Void> response = testRestTemplate.exchange("/api/v1/private/store/",
				HttpMethod.POST, new HttpEntity<>(store, getHeader()), Void.class);
		assertEquals(HttpStatus.OK, response.getStatusCode());
	}

	private void uploadImage(Long productId, String storeCode, HttpHeaders headers) {
		HttpHeaders multipartHeaders = new HttpHeaders();
		multipartHeaders.putAll(headers);
		multipartHeaders.setContentType(MediaType.MULTIPART_FORM_DATA);
		ResponseEntity<Void> response = testRestTemplate.exchange(
				"/api/v1/private/product/" + productId + "/image?store=" + storeCode,
				HttpMethod.POST, new HttpEntity<>(multipartFile(), multipartHeaders), Void.class);
		assertEquals(HttpStatus.CREATED, response.getStatusCode());
	}

	private LinkedMultiValueMap<String, Object> multipartFile() {
		LinkedMultiValueMap<String, Object> parameters = new LinkedMultiValueMap<>();
		parameters.add("file", new ClassPathResource("image.jpg"));
		return parameters;
	}

	private ReadableImage[] fetchImages(Long productId, String storeCode) {
		return testRestTemplate.getForEntity(
				"/api/v1/product/" + productId + "/images?store=" + storeCode,
				ReadableImage[].class).getBody();
	}

	private String registerCustomer() {
		String suffix = Long.toString(System.nanoTime());
		PersistableCustomer customer = new PersistableCustomer();
		customer.setEmailAddress("customer-" + suffix + "@example.test");
		customer.setPassword("clear123");
		customer.setGender(CustomerGender.M.name());
		customer.setLanguage("en");
		customer.setStoreCode("DEFAULT");
		Address billing = new Address();
		billing.setFirstName("Image");
		billing.setLastName("Customer");
		billing.setCountry("US");
		customer.setBilling(billing);
		ResponseEntity<PersistableCustomer> registration = testRestTemplate.postForEntity(
				"/api/v1/customer/register", new HttpEntity<>(customer), PersistableCustomer.class);
		assertEquals(HttpStatus.OK, registration.getStatusCode());

		ResponseEntity<AuthenticationResponse> login = testRestTemplate.postForEntity(
				"/api/v1/customer/login",
				new HttpEntity<>(new AuthenticationRequest(customer.getEmailAddress(), "clear123")),
				AuthenticationResponse.class);
		assertEquals(HttpStatus.OK, login.getStatusCode());
		assertNotNull(login.getBody());
		return login.getBody().getToken();
	}

	private String createAdminUserToken() {
		String email = "image-admin-" + System.nanoTime() + "@example.test";
		PersistableUser user = new PersistableUser();
		user.setDefaultLanguage("en");
		user.setEmailAddress(email);
		user.setFirstName("Image");
		user.setLastName("Admin");
		user.setUserName(email);
		user.setPassword("Password1");
		user.setRepeatPassword("Password1");
		PersistableGroup group = new PersistableGroup();
		group.setName("ADMIN");
		user.getGroups().add(group);
		ResponseEntity<?> created = testRestTemplate.exchange("/api/v1/private/user/",
				HttpMethod.POST, new HttpEntity<>(user, getHeader()), Object.class);
		assertEquals(HttpStatus.OK, created.getStatusCode());
		ResponseEntity<AuthenticationResponse> login = testRestTemplate.postForEntity(
				"/api/v1/private/login",
				new HttpEntity<>(new AuthenticationRequest(email, "Password1")),
				AuthenticationResponse.class);
		assertEquals(HttpStatus.OK, login.getStatusCode());
		assertNotNull(login.getBody());
		return login.getBody().getToken();
	}

	private HttpHeaders bearerHeaders(String token) {
		HttpHeaders headers = new HttpHeaders();
		headers.setBearerAuth(token);
		headers.setContentType(MediaType.APPLICATION_JSON);
		return headers;
	}

	private List<String> imageState(ReadableImage[] images) {
		List<String> state = new ArrayList<>();
		for (ReadableImage image : images) {
			state.add(image.getId() + ":" + image.getOrder());
		}
		Collections.sort(state);
		return state;
	}
}
