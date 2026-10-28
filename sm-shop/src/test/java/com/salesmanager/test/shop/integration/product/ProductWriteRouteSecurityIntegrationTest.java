package com.salesmanager.test.shop.integration.product;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Map;
import java.util.Set;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.springframework.beans.factory.annotation.Autowired;
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
import org.springframework.web.method.HandlerMethod;
import org.springframework.web.servlet.mvc.method.RequestMappingInfo;
import org.springframework.web.servlet.mvc.method.annotation.RequestMappingHandlerMapping;

import com.salesmanager.core.business.constants.Constants;
import com.salesmanager.core.model.merchant.MerchantStore;
import com.salesmanager.shop.application.ShopApplication;
import com.salesmanager.shop.model.catalog.category.Category;
import com.salesmanager.shop.model.catalog.product.PersistableProductReview;
import com.salesmanager.shop.model.catalog.product.ReadableProduct;
import com.salesmanager.shop.model.catalog.product.product.PersistableProduct;
import com.salesmanager.shop.model.customer.PersistableCustomer;
import com.salesmanager.shop.model.customer.ReadableCustomer;
import com.salesmanager.shop.model.customer.address.Address;
import com.salesmanager.shop.model.references.PersistableAddress;
import com.salesmanager.shop.model.store.PersistableMerchantStore;
import com.salesmanager.shop.store.api.v1.product.ProductApi;
import com.salesmanager.shop.store.api.v1.product.ProductImageApi;
import com.salesmanager.shop.store.api.v1.product.ProductReviewApi;
import com.salesmanager.shop.store.security.AuthenticationRequest;
import com.salesmanager.shop.store.security.AuthenticationResponse;
import com.salesmanager.test.shop.common.ServicesTestSupport;

@SpringBootTest(classes = ShopApplication.class, webEnvironment = WebEnvironment.RANDOM_PORT)
@RunWith(SpringRunner.class)
public class ProductWriteRouteSecurityIntegrationTest extends ServicesTestSupport {

	@Autowired
	private RequestMappingHandlerMapping requestMappingHandlerMapping;

	private String suffix;
	private ReadableProduct product;
	private Shopper shopper;

	@Before
	public void setUp() {
		suffix = Long.toString(System.nanoTime());
		product = sampleProduct("write-security-" + suffix);
		shopper = registerShopper(suffix);
	}

	@Test
	public void shopperCannotCreateUpdateSortOrDeleteProductsThroughAuthAliases() {
		String newSku = "blocked-create-" + suffix;
		ResponseEntity<String> createResponse = testRestTemplate.postForEntity(
				"/api/v1/auth/products?store=" + Constants.DEFAULT_STORE,
				new HttpEntity<>(productPayload(newSku, null, "Blocked Create"), shopper.headers), String.class);
		recordStatus("2a-shopper-create", createResponse);
		assertFalse(createResponse.getStatusCode().is2xxSuccessful());
		assertSkuAbsent(newSku);

		String originalName = product.getDescription().getName();
		PersistableProduct update = productPayload(product.getSku(), product.getId(), "Blocked Rename");
		ResponseEntity<String> updateResponse = testRestTemplate.exchange(
				"/api/v1/auth/product/" + product.getId() + "?store=" + Constants.DEFAULT_STORE,
				HttpMethod.PUT, new HttpEntity<>(update, shopper.headers), String.class);
		recordStatus("2b-shopper-update", updateResponse);
		assertFalse(updateResponse.getStatusCode().is2xxSuccessful());
		assertEquals(originalName, readProduct(product.getSku()).getDescription().getName());

		int originalSortOrder = readProduct(product.getSku()).getSortOrder();
		ResponseEntity<String> sortResponse = testRestTemplate.exchange(
				"/api/v1/auth/product/" + product.getId() + "?order=99&store=" + Constants.DEFAULT_STORE,
				HttpMethod.PATCH, new HttpEntity<>(shopper.headers), String.class);
		recordStatus("2c-shopper-sort", sortResponse);
		assertFalse(sortResponse.getStatusCode().is2xxSuccessful());
		assertEquals(originalSortOrder, readProduct(product.getSku()).getSortOrder());

		ResponseEntity<String> deleteResponse = testRestTemplate.exchange(
				"/api/v1/auth/product/" + product.getId() + "?store=" + Constants.DEFAULT_STORE,
				HttpMethod.DELETE, new HttpEntity<>(shopper.headers), String.class);
		recordStatus("2d-shopper-delete", deleteResponse);
		assertFalse(deleteResponse.getStatusCode().is2xxSuccessful());
		assertEquals(HttpStatus.OK, productResponse(product.getSku()).getStatusCode());
	}

	@Test
	public void shopperCannotManageProductImagesThroughAuthAliases() {
		HttpHeaders headers = new HttpHeaders();
		headers.putAll(shopper.headers);
		headers.setContentType(MediaType.MULTIPART_FORM_DATA);
		LinkedMultiValueMap<String, Object> multipart = new LinkedMultiValueMap<>();
		multipart.add("file", new ClassPathResource("image.jpg"));
		ResponseEntity<String> uploadResponse = testRestTemplate.exchange(
				"/api/v1/auth/product/" + product.getId() + "/image?store=" + Constants.DEFAULT_STORE,
				HttpMethod.POST, new HttpEntity<>(multipart, headers), String.class);
		recordStatus("2e-shopper-image-upload", uploadResponse);
		assertFalse(uploadResponse.getStatusCode().is2xxSuccessful());
		assertTrue(readProduct(product.getSku()).getImages().isEmpty());

		ResponseEntity<String> deleteResponse = testRestTemplate.exchange(
				"/api/v1/auth/product/images/1?store=" + Constants.DEFAULT_STORE,
				HttpMethod.DELETE, new HttpEntity<>(shopper.headers), String.class);
		recordStatus("2e-shopper-image-delete", deleteResponse);
		assertFalse(deleteResponse.getStatusCode().is2xxSuccessful());
	}

	@Test
	public void privateProductWritesRequireAdminAuthentication() {
		String shopperSku = "private-shopper-" + suffix;
		ResponseEntity<String> shopperResponse = testRestTemplate.postForEntity(
				"/api/v1/private/product?store=" + Constants.DEFAULT_STORE,
				new HttpEntity<>(productPayload(shopperSku, null, "Shopper Product"), shopper.headers), String.class);
		recordStatus("2f-shopper-private-create", shopperResponse);
		assertFalse(shopperResponse.getStatusCode().is2xxSuccessful());
		assertSkuAbsent(shopperSku);
		if (shopperResponse.getStatusCode() != HttpStatus.UNAUTHORIZED
				&& shopperResponse.getStatusCode() != HttpStatus.FORBIDDEN) {
			System.out.println("2f-shopper-private-create-unexpected-status="
					+ shopperResponse.getStatusCodeValue());
		}

		String anonymousSku = "anonymous-private-" + suffix;
		ResponseEntity<String> anonymousResponse = testRestTemplate.postForEntity(
				"/api/v1/private/product?store=" + Constants.DEFAULT_STORE,
				productPayload(anonymousSku, null, "Anonymous Product"), String.class);
		recordStatus("2f-anonymous-private-create", anonymousResponse);
		assertEquals(HttpStatus.UNAUTHORIZED, anonymousResponse.getStatusCode());
		assertSkuAbsent(anonymousSku);
	}

	@Test
	public void adminCanCreateUpdateAndDeleteProductsAndMismatchedIdsAreRejected() {
		String sku = "admin-write-" + suffix;
		com.salesmanager.shop.model.catalog.category.PersistableCategory category = category(sku + "-category");
		category.setParent(new Category());
		ResponseEntity<com.salesmanager.shop.model.catalog.category.PersistableCategory> categoryResponse =
				testRestTemplate.postForEntity("/api/v1/private/category?store=" + Constants.DEFAULT_STORE,
						new HttpEntity<>(category, getHeader()),
						com.salesmanager.shop.model.catalog.category.PersistableCategory.class);
		assertEquals(HttpStatus.CREATED, categoryResponse.getStatusCode());

		PersistableProduct createBody = productPayload(sku, null, "Admin Product");
		ArrayList<Category> categories = new ArrayList<>();
		categories.add(categoryResponse.getBody());
		createBody.setCategories(categories);
		createBody.setAvailable(true);
		createBody.setPrice(java.math.BigDecimal.TEN);
		createBody.setQuantity(100);
		createBody.setProductSpecifications(new com.salesmanager.shop.model.catalog.product.product.ProductSpecification());
		createBody.getProductSpecifications().setManufacturer(
				com.salesmanager.core.model.catalog.product.manufacturer.Manufacturer.DEFAULT_MANUFACTURER);
		ResponseEntity<com.salesmanager.shop.model.entity.Entity> createResponse = testRestTemplate.postForEntity(
				"/api/v1/private/product?store=" + Constants.DEFAULT_STORE,
				new HttpEntity<>(createBody, getHeader()), com.salesmanager.shop.model.entity.Entity.class);
		assertEquals(HttpStatus.CREATED, createResponse.getStatusCode());
		ReadableProduct created = testRestTemplate.getForEntity("/api/v2/product/" + sku, ReadableProduct.class)
				.getBody();
		assertNotNull(created);

		String renamed = "Admin Renamed " + suffix;
		PersistableProduct update = productPayload(sku, created.getId(), renamed);
		ResponseEntity<String> updateResponse = testRestTemplate.exchange(
				"/api/v1/private/product/" + created.getId() + "?store=" + Constants.DEFAULT_STORE,
				HttpMethod.PUT, new HttpEntity<>(update, getHeader()), String.class);
		assertEquals(HttpStatus.OK, updateResponse.getStatusCode());
		assertEquals(renamed, readProduct(created.getSku()).getDescription().getName());

		ReadableProduct mismatchTarget = sampleProduct("mismatch-target-" + suffix);
		ReadableProduct mismatchPath = sampleProduct("mismatch-path-" + suffix);
		String targetName = mismatchTarget.getDescription().getName();
		PersistableProduct mismatchedBody = productPayload(mismatchTarget.getSku(), mismatchTarget.getId(),
				"Must Not Be Saved");
		ResponseEntity<String> mismatchResponse = testRestTemplate.exchange(
				"/api/v1/private/product/" + mismatchPath.getId() + "?store=" + Constants.DEFAULT_STORE,
				HttpMethod.PUT, new HttpEntity<>(mismatchedBody, getHeader()), String.class);
		assertEquals(HttpStatus.BAD_REQUEST, mismatchResponse.getStatusCode());
		assertEquals(targetName, readProduct(mismatchTarget.getSku()).getDescription().getName());

		ResponseEntity<String> deleteResponse = testRestTemplate.exchange(
				"/api/v1/private/product/" + created.getId() + "?store=" + Constants.DEFAULT_STORE,
				HttpMethod.DELETE, new HttpEntity<>(getHeader()), String.class);
		assertEquals(HttpStatus.OK, deleteResponse.getStatusCode());
		assertFalse(productResponse(created.getSku()).getStatusCode().is2xxSuccessful());
	}

	@Test
	public void productWritesAreScopedToRequestedStore() {
		String childCode = "CHILD" + suffix;
		ResponseEntity<Void> childStoreResponse = createStore(childCode, MerchantStore.DEFAULT_STORE);
		if (childStoreResponse.getStatusCode().is2xxSuccessful()) {
			assertCrossStorePut(childCode, "2i-child");
			ResponseEntity<String> deleteResponse = testRestTemplate.exchange(
					"/api/v1/private/product/" + product.getId() + "?store=" + childCode,
					HttpMethod.DELETE, new HttpEntity<>(getHeader()), String.class);
			recordStatus("2i-child-delete", deleteResponse);
			assertEquals(HttpStatus.NOT_FOUND, deleteResponse.getStatusCode());
			assertEquals(product.getDescription().getName(), readProduct(product.getSku()).getDescription().getName());
		} else {
			System.out.println("2i-child-store-creation-unavailable status="
					+ childStoreResponse.getStatusCodeValue());
		}

		String standaloneCode = "STANDALONE" + suffix;
		ResponseEntity<Void> standaloneResponse = createStore(standaloneCode, null);
		assertTrue("Standalone store creation returned " + standaloneResponse.getStatusCode(),
				standaloneResponse.getStatusCode().is2xxSuccessful());
		assertCrossStorePut(standaloneCode, "2i-standalone");
	}

	@Test
	public void productWriteHandlersHaveNoAuthAliasesAndReviewsRemainAvailable() {
		boolean reviewPostExists = false;
		for (Map.Entry<RequestMappingInfo, HandlerMethod> entry
				: requestMappingHandlerMapping.getHandlerMethods().entrySet()) {
			Class<?> controller = entry.getValue().getBeanType();
			Set<String> paths = entry.getKey().getPatternsCondition() == null
					? java.util.Collections.emptySet() : entry.getKey().getPatternsCondition().getPatterns();
			boolean productWriteController = ProductApi.class.isAssignableFrom(controller)
					|| ProductImageApi.class.isAssignableFrom(controller);
			if (productWriteController) {
				for (String path : paths) {
					assertFalse(controller.getSimpleName() + " maps " + path, path.startsWith("/api/v1/auth/"));
				}
			}
			if (ProductReviewApi.class.isAssignableFrom(controller)
					&& paths.contains("/api/v1/auth/products/{id}/reviews")
					&& entry.getKey().getMethodsCondition().getMethods()
							.contains(org.springframework.web.bind.annotation.RequestMethod.POST)) {
				reviewPostExists = true;
			}
		}
		assertTrue("Product review POST should remain mapped for shoppers", reviewPostExists);

		PersistableProductReview review = new PersistableProductReview();
		review.setProductId(product.getId());
		review.setCustomerId(shopper.customer.getId());
		review.setLanguage("en");
		review.setRating(5D);
		review.setDescription("Review route remains available " + suffix);
		review.setDate("2024-01-01");
		ResponseEntity<String> response = testRestTemplate.postForEntity(
				"/api/v1/auth/products/" + product.getId() + "/reviews?store=" + Constants.DEFAULT_STORE,
				new HttpEntity<>(review, shopper.headers), String.class);
		recordStatus("2k-shopper-review", response);
		assertTrue(response.getStatusCode().is2xxSuccessful());
	}

	private void assertCrossStorePut(String storeCode, String label) {
		String originalName = readProduct(product.getSku()).getDescription().getName();
		PersistableProduct update = productPayload(product.getSku(), product.getId(), "Cross-store rename");
		ResponseEntity<String> response = testRestTemplate.exchange(
				"/api/v1/private/product/" + product.getId() + "?store=" + storeCode,
				HttpMethod.PUT, new HttpEntity<>(update, getHeader()), String.class);
		recordStatus(label + "-put", response);
		assertEquals(HttpStatus.SERVICE_UNAVAILABLE, response.getStatusCode());
		assertEquals(originalName, readProduct(product.getSku()).getDescription().getName());
	}

	private ResponseEntity<Void> createStore(String code, String parentCode) {
		PersistableMerchantStore store = new PersistableMerchantStore();
		store.setCode(code);
		store.setCurrency("CAD");
		store.setDefaultLanguage("en");
		store.setEmail(code.toLowerCase() + "@example.com");
		store.setName(code);
		store.setPhone("444-555-6666");
		store.setSupportedLanguages(Arrays.asList("en"));
		store.setRetailerStore(parentCode);
		PersistableAddress address = new PersistableAddress();
		address.setAddress("121212 simple address");
		address.setPostalCode("12345");
		address.setCountry("US");
		address.setCity("FT LD");
		address.setStateProvince("FL");
		store.setAddress(address);
		return testRestTemplate.exchange("/api/v1/private/store/", HttpMethod.POST,
				new HttpEntity<>(store, getHeader()), Void.class);
	}

	private Shopper registerShopper(String id) {
		String email = "write-security-" + id + "@example.com";
		PersistableCustomer customer = new PersistableCustomer();
		customer.setEmailAddress(email);
		customer.setPassword("clear123");
		customer.setGender("M");
		customer.setLanguage("en");
		customer.setStoreCode(Constants.DEFAULT_STORE);
		Address billing = new Address();
		billing.setFirstName("Test");
		billing.setLastName("Shopper");
		billing.setCountry("BE");
		customer.setBilling(billing);
		ResponseEntity<PersistableCustomer> registration = testRestTemplate.postForEntity(
				"/api/v1/customer/register", new HttpEntity<>(customer, getHeader()), PersistableCustomer.class);
		assertEquals(HttpStatus.OK, registration.getStatusCode());
		ResponseEntity<AuthenticationResponse> login = testRestTemplate.postForEntity("/api/v1/customer/login",
				new HttpEntity<>(new AuthenticationRequest(email, "clear123")), AuthenticationResponse.class);
		assertEquals(HttpStatus.OK, login.getStatusCode());
		assertNotNull(login.getBody());
		HttpHeaders headers = new HttpHeaders();
		headers.setContentType(MediaType.APPLICATION_JSON);
		headers.setBearerAuth(login.getBody().getToken());
		ResponseEntity<ReadableCustomer> profile = testRestTemplate.exchange(
				"/api/v1/auth/customer/profile", HttpMethod.GET, new HttpEntity<>(headers), ReadableCustomer.class);
		assertEquals(HttpStatus.OK, profile.getStatusCode());
		return new Shopper(headers, profile.getBody());
	}

	private PersistableProduct productPayload(String sku, Long id, String name) {
		PersistableProduct payload = product(sku);
		payload.setId(id);
		payload.setSku(sku);
		payload.setAvailable(true);
		payload.setPrice(java.math.BigDecimal.TEN);
		payload.setQuantity(100);
		com.salesmanager.shop.model.catalog.product.product.ProductSpecification specifications =
				new com.salesmanager.shop.model.catalog.product.product.ProductSpecification();
		specifications.setManufacturer(
				com.salesmanager.core.model.catalog.product.manufacturer.Manufacturer.DEFAULT_MANUFACTURER);
		payload.setProductSpecifications(specifications);
		if (!product.getCategories().isEmpty()) {
			Category category = new Category();
			category.setId(product.getCategories().get(0).getId());
			payload.setCategories(Arrays.asList(category));
		}
		if (!payload.getDescriptions().isEmpty()) {
			payload.getDescriptions().get(0).setName(name);
		}
		return payload;
	}

	private ResponseEntity<ReadableProduct> productResponse(String sku) {
		return testRestTemplate.getForEntity("/api/v2/product/" + sku + "?store=" + Constants.DEFAULT_STORE,
				ReadableProduct.class);
	}

	private ReadableProduct readProduct(String sku) {
		ResponseEntity<ReadableProduct> response = productResponse(sku);
		assertEquals(HttpStatus.OK, response.getStatusCode());
		assertNotNull(response.getBody());
		return response.getBody();
	}

	private void assertSkuAbsent(String sku) {
		ResponseEntity<String> response = testRestTemplate.getForEntity("/api/v2/product/" + sku, String.class);
		assertFalse("SKU " + sku + " unexpectedly returned " + response.getStatusCodeValue(),
				response.getStatusCode().is2xxSuccessful());
	}

	private void recordStatus(String label, ResponseEntity<?> response) {
		System.out.println(label + " status=" + response.getStatusCodeValue());
	}

	private static class Shopper {
		private final HttpHeaders headers;
		private final ReadableCustomer customer;

		private Shopper(HttpHeaders headers, ReadableCustomer customer) {
			this.headers = headers;
			this.customer = customer;
		}
	}
}
