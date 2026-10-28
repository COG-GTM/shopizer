package com.salesmanager.test.shop.uat;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;
import static org.springframework.http.HttpMethod.DELETE;
import static org.springframework.http.HttpMethod.GET;
import static org.springframework.http.HttpMethod.POST;
import static org.springframework.http.HttpMethod.PUT;
import static org.springframework.http.HttpStatus.OK;

import java.util.concurrent.atomic.AtomicLong;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.SpringBootTest.WebEnvironment;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.test.context.junit4.SpringRunner;

import com.fasterxml.jackson.databind.JsonNode;
import com.salesmanager.core.business.constants.Constants;
import com.salesmanager.core.model.customer.CustomerGender;
import com.salesmanager.shop.model.catalog.product.PersistableProductReview;
import com.salesmanager.shop.model.catalog.product.ReadableProduct;
import com.salesmanager.shop.model.catalog.product.ReadableProductReview;
import com.salesmanager.shop.model.customer.PersistableCustomer;
import com.salesmanager.shop.model.customer.address.Address;
import com.salesmanager.shop.model.shoppingcart.PersistableShoppingCartItem;
import com.salesmanager.shop.model.shoppingcart.ReadableShoppingCart;
import com.salesmanager.shop.model.store.ReadableMerchantStore;
import com.salesmanager.shop.store.security.AuthenticationRequest;
import com.salesmanager.shop.store.security.AuthenticationResponse;
import com.salesmanager.shop.application.ShopApplication;
import com.salesmanager.test.shop.common.ServicesTestSupport;

@RunWith(SpringRunner.class)
@SpringBootTest(classes = ShopApplication.class, webEnvironment = WebEnvironment.RANDOM_PORT)
public class StorefrontJourneysUatTest extends ServicesTestSupport {

	private static final AtomicLong CUSTOMER_SEQUENCE = new AtomicLong();

	@Test
	public void adminAndCustomerAuthenticationJourneys() throws Exception {
		ResponseEntity<AuthenticationResponse> adminLogin = testRestTemplate.postForEntity(
				"/api/v1/private/login",
				new HttpEntity<>(new AuthenticationRequest("admin@shopizer.com", "password")),
				AuthenticationResponse.class);
		assertEquals(OK, adminLogin.getStatusCode());
		assertNotNull(adminLogin.getBody().getToken());

		HttpHeaders adminHeaders = bearer(adminLogin.getBody().getToken());
		ResponseEntity<String> privateCustomers = testRestTemplate.exchange(
				"/api/v1/private/customers", GET, new HttpEntity<>(adminHeaders), String.class);
		assertEquals(OK, privateCustomers.getStatusCode());

		ResponseEntity<String> anonymousCustomers = testRestTemplate.exchange(
				"/api/v1/private/customers", GET, HttpEntity.EMPTY, String.class);
		assertDenied(anonymousCustomers.getStatusCode());

		RegisteredCustomer customer = registerCustomer("uat-auth");
		ResponseEntity<AuthenticationResponse> customerLogin = testRestTemplate.postForEntity(
				"/api/v1/customer/login",
				new HttpEntity<>(new AuthenticationRequest(customer.email, "clear123")),
				AuthenticationResponse.class);
		assertEquals(OK, customerLogin.getStatusCode());
		assertNotNull(customerLogin.getBody().getToken());

		ResponseEntity<JsonNode> profile = testRestTemplate.exchange(
				"/api/v1/auth/customer/profile", GET,
				new HttpEntity<>(bearer(customerLogin.getBody().getToken())), JsonNode.class);
		assertEquals(OK, profile.getStatusCode());
		assertEquals(customer.email, profile.getBody().path("emailAddress").asText());
	}

	@Test
	public void storefrontCatalogAndProductBrowsing() {
		ResponseEntity<ReadableMerchantStore> store = testRestTemplate.getForEntity(
				"/api/v1/store/" + Constants.DEFAULT_STORE, ReadableMerchantStore.class);
		assertEquals(OK, store.getStatusCode());
		assertEquals(Constants.DEFAULT_STORE, store.getBody().getCode());

		ResponseEntity<JsonNode> categories = testRestTemplate.getForEntity(
				"/api/v1/category?store=" + Constants.DEFAULT_STORE, JsonNode.class);
		assertEquals(OK, categories.getStatusCode());
		assertNotNull(categories.getBody());

		ResponseEntity<JsonNode> products = testRestTemplate.getForEntity(
				"/api/v1/products?store=" + Constants.DEFAULT_STORE, JsonNode.class);
		assertEquals(OK, products.getStatusCode());
		assertNotNull(products.getBody());

		ReadableProduct created = sampleProduct("uatBrowse" + CUSTOMER_SEQUENCE.incrementAndGet());
		ResponseEntity<ReadableProduct> detail = testRestTemplate.getForEntity(
				"/api/v2/product/" + created.getSku() + "?store=" + Constants.DEFAULT_STORE,
				ReadableProduct.class);
		assertEquals(OK, detail.getStatusCode());
		assertEquals(created.getSku(), detail.getBody().getSku());
	}

	@Test
	public void cartCanBeCreatedUpdatedAndRead() {
		ReadableProduct product = sampleProduct("uatCart" + CUSTOMER_SEQUENCE.incrementAndGet());
		PersistableShoppingCartItem item = new PersistableShoppingCartItem();
		item.setProduct(product.getSku());
		item.setQuantity(1);

		ResponseEntity<ReadableShoppingCart> created = testRestTemplate.postForEntity(
				"/api/v1/cart?store=" + Constants.DEFAULT_STORE,
				new HttpEntity<>(item), ReadableShoppingCart.class);
		assertEquals(HttpStatus.CREATED, created.getStatusCode());
		assertNotNull(created.getBody());

		item.setQuantity(3);
		ResponseEntity<ReadableShoppingCart> updated = testRestTemplate.exchange(
				"/api/v1/cart/" + created.getBody().getCode() + "?store=" + Constants.DEFAULT_STORE,
				PUT, new HttpEntity<>(item), ReadableShoppingCart.class);
		assertEquals(HttpStatus.CREATED, updated.getStatusCode());
		assertEquals(3, updated.getBody().getQuantity());

		ResponseEntity<ReadableShoppingCart> readBack = testRestTemplate.getForEntity(
				"/api/v1/cart/" + created.getBody().getCode() + "?store=" + Constants.DEFAULT_STORE,
				ReadableShoppingCart.class);
		assertEquals(OK, readBack.getStatusCode());
		assertEquals(3, readBack.getBody().getQuantity());
	}

	@Test
	public void privateServicesActuatorAndReviewApiContractAreAvailable() throws Exception {
		HttpHeaders basicHeaders = new HttpHeaders();
		basicHeaders.setBasicAuth("admin@shopizer.com", "password");
		basicHeaders.setContentType(MediaType.TEXT_PLAIN);
		ResponseEntity<String> validBasicAuth = testRestTemplate.exchange(
				"/services/private/system/optin", POST,
				new HttpEntity<>("uat-optin", basicHeaders), String.class);
		assertEquals(OK, validBasicAuth.getStatusCode());

		HttpHeaders invalidBasicHeaders = new HttpHeaders();
		invalidBasicHeaders.setBasicAuth("admin@shopizer.com", "wrong-password");
		invalidBasicHeaders.setContentType(MediaType.TEXT_PLAIN);
		ResponseEntity<String> invalidBasicAuth = testRestTemplate.exchange(
				"/services/private/system/optin", POST,
				new HttpEntity<>("uat-optin", invalidBasicHeaders), String.class);
		assertDenied(invalidBasicAuth.getStatusCode());

		ResponseEntity<JsonNode> health = testRestTemplate.getForEntity("/actuator/health", JsonNode.class);
		assertEquals(OK, health.getStatusCode());
		assertEquals("UP", health.getBody().path("status").asText());

		ResponseEntity<String> apiDocs = testRestTemplate.getForEntity("/v2/api-docs", String.class);
		assertEquals(OK, apiDocs.getStatusCode());
		assertTrue(apiDocs.getBody().contains("/api/v1/auth/products/{id}/reviews/{reviewId}"));
		assertTrue(apiDocs.getBody().contains("/api/v1/private/products/{id}/reviews/{reviewId}"));
	}

	@Test
	public void customersCanOnlyEditAndDeleteTheirOwnReviews() throws Exception {
		RegisteredCustomer author = registerCustomer("uat-review-author");
		RegisteredCustomer other = registerCustomer("uat-review-other");
		String authorToken = login(author.email).getBody().getToken();
		String otherToken = login(other.email).getBody().getToken();
		ReadableProduct product = sampleProduct("uatReview" + CUSTOMER_SEQUENCE.incrementAndGet());

		ResponseEntity<ReadableProduct> browsedProduct = testRestTemplate.getForEntity(
				"/api/v2/product/" + product.getSku() + "?store=" + Constants.DEFAULT_STORE,
				ReadableProduct.class);
		assertEquals(OK, browsedProduct.getStatusCode());
		Long productId = browsedProduct.getBody().getId();

		PersistableProductReview review = newReview(author.id, 2D, "first");
		ResponseEntity<PersistableProductReview> created = testRestTemplate.exchange(
				"/api/v1/auth/products/" + productId + "/reviews?store=" + Constants.DEFAULT_STORE,
				POST, new HttpEntity<>(review, bearer(authorToken)), PersistableProductReview.class);
		assertEquals(HttpStatus.CREATED, created.getStatusCode());
		Long reviewId = created.getBody().getId();
		assertNotNull(reviewId);

		PersistableProductReview forgedUpdate = newReview(other.id, 4D, "other customer");
		ResponseEntity<String> otherUpdate = testRestTemplate.exchange(
				"/api/v1/auth/products/" + productId + "/reviews/" + reviewId + "?store="
						+ Constants.DEFAULT_STORE,
				PUT, new HttpEntity<>(forgedUpdate, bearer(otherToken)), String.class);
		assertEquals(HttpStatus.NOT_FOUND, otherUpdate.getStatusCode());

		ResponseEntity<String> otherDelete = testRestTemplate.exchange(
				"/api/v1/auth/products/" + productId + "/reviews/" + reviewId + "?store="
						+ Constants.DEFAULT_STORE,
				DELETE, new HttpEntity<>(bearer(otherToken)), String.class);
		assertEquals(HttpStatus.NOT_FOUND, otherDelete.getStatusCode());
		assertEquals(2D, findReview(productId, reviewId).getRating(), 0D);

		PersistableProductReview ownerUpdate = newReview(other.id, 5D, "updated by owner");
		ResponseEntity<PersistableProductReview> updated = testRestTemplate.exchange(
				"/api/v1/auth/products/" + productId + "/reviews/" + reviewId + "?store="
						+ Constants.DEFAULT_STORE,
				PUT, new HttpEntity<>(ownerUpdate, bearer(authorToken)), PersistableProductReview.class);
		assertEquals(OK, updated.getStatusCode());
		assertEquals(5D, findReview(productId, reviewId).getRating(), 0D);

		ResponseEntity<String> deleted = testRestTemplate.exchange(
				"/api/v1/auth/products/" + productId + "/reviews/" + reviewId + "?store="
						+ Constants.DEFAULT_STORE,
				DELETE, new HttpEntity<>(bearer(authorToken)), String.class);
		assertEquals(OK, deleted.getStatusCode());
		assertTrue(reviewsFor(productId).isEmpty());
	}

	private RegisteredCustomer registerCustomer(String name) {
		long suffix = CUSTOMER_SEQUENCE.incrementAndGet();
		String email = name + suffix + "@example.com";
		PersistableCustomer customer = new PersistableCustomer();
		customer.setEmailAddress(email);
		customer.setPassword("clear123");
		customer.setGender(CustomerGender.M.name());
		customer.setLanguage("en");
		customer.setStoreCode(Constants.DEFAULT_STORE);

		Address billing = new Address();
		billing.setFirstName("UAT");
		billing.setLastName("Customer");
		billing.setCountry("US");
		customer.setBilling(billing);

		ResponseEntity<PersistableCustomer> response = testRestTemplate.postForEntity(
				"/api/v1/customer/register", new HttpEntity<>(customer, getHeader()),
				PersistableCustomer.class);
		assertTrue(response.getStatusCode().is2xxSuccessful());
		return new RegisteredCustomer(email, response.getBody().getId());
	}

	private ResponseEntity<AuthenticationResponse> login(String email) {
		return testRestTemplate.postForEntity("/api/v1/customer/login",
				new HttpEntity<>(new AuthenticationRequest(email, "clear123")),
				AuthenticationResponse.class);
	}

	private PersistableProductReview newReview(Long customerId, double rating, String text) {
		PersistableProductReview review = new PersistableProductReview();
		review.setCustomerId(customerId);
		review.setRating(rating);
		review.setDescription(text);
		review.setLanguage("en");
		return review;
	}

	private ReadableProductReview findReview(Long productId, Long reviewId) throws Exception {
		for (ReadableProductReview review : reviewsFor(productId)) {
			if (review.getId().equals(reviewId)) {
				return review;
			}
		}
		fail("Review " + reviewId + " not found for product " + productId);
		return null;
	}

	private java.util.List<ReadableProductReview> reviewsFor(Long productId) {
		ResponseEntity<ReadableProductReview[]> response = testRestTemplate.getForEntity(
				"/api/v1/product/" + productId + "/reviews?store=" + Constants.DEFAULT_STORE,
				ReadableProductReview[].class);
		assertEquals(OK, response.getStatusCode());
		return java.util.Arrays.asList(response.getBody());
	}

	private HttpHeaders bearer(String token) {
		HttpHeaders headers = new HttpHeaders();
		headers.setBearerAuth(token);
		return headers;
	}

	private void assertDenied(HttpStatus status) {
		assertTrue("Expected HTTP 401 or 403, received " + status,
				status == HttpStatus.UNAUTHORIZED || status == HttpStatus.FORBIDDEN);
	}

	private static final class RegisteredCustomer {
		private final String email;
		private final Long id;

		private RegisteredCustomer(String email, Long id) {
			this.email = email;
			this.id = id;
		}
	}
}
