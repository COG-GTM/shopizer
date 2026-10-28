package com.salesmanager.test.shop.integration.product;

import static org.hamcrest.core.Is.is;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertThat;

import java.nio.charset.Charset;

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

import com.salesmanager.core.business.constants.Constants;
import com.salesmanager.core.model.customer.CustomerGender;
import com.salesmanager.shop.application.ShopApplication;
import com.salesmanager.shop.model.catalog.product.PersistableProductReview;
import com.salesmanager.shop.model.catalog.product.ReadableProduct;
import com.salesmanager.shop.model.catalog.product.ReadableProductReview;
import com.salesmanager.shop.model.customer.PersistableCustomer;
import com.salesmanager.shop.model.customer.address.Address;
import com.salesmanager.shop.store.security.AuthenticationResponse;
import com.salesmanager.test.shop.common.ServicesTestSupport;

@SpringBootTest(classes = ShopApplication.class, webEnvironment = WebEnvironment.RANDOM_PORT)
@RunWith(SpringRunner.class)
public class ProductReviewApiIntegrationTest extends ServicesTestSupport {

	private static final String STORE = "?store=" + Constants.DEFAULT_STORE;

	@Test
	public void customerCanOnlyChangeOwnReview() {

		ReadableProduct product = sampleProduct("reviewOwnership");
		Long productId = product.getId();

		AuthenticationResponse alice = register("review-alice@test.com");
		AuthenticationResponse mallory = register("review-mallory@test.com");

		// author is taken from the token, not from the body
		PersistableProductReview aliceReview = review(mallory.getId(), "alice original");
		ResponseEntity<PersistableProductReview> created = testRestTemplate.postForEntity(
				"/api/v1/auth/products/" + productId + "/reviews" + STORE,
				new HttpEntity<>(aliceReview, customerHeader(alice)), PersistableProductReview.class);
		assertThat(created.getStatusCode(), is(HttpStatus.CREATED));
		Long reviewId = created.getBody().getId();
		assertNotNull(reviewId);
		assertEquals(alice.getId(), created.getBody().getCustomerId());

		String reviewUrl = "/api/v1/auth/products/" + productId + "/reviews/" + reviewId + STORE;

		// another customer cannot rewrite it, even when claiming the author's id
		ResponseEntity<String> forgedUpdate = testRestTemplate.exchange(reviewUrl, HttpMethod.PUT,
				new HttpEntity<>(review(alice.getId(), "mallory was here"), customerHeader(mallory)), String.class);
		assertThat(forgedUpdate.getStatusCode(), is(HttpStatus.NOT_FOUND));

		// another customer cannot delete it
		ResponseEntity<String> forgedDelete = testRestTemplate.exchange(reviewUrl, HttpMethod.DELETE,
				new HttpEntity<>(customerHeader(mallory)), String.class);
		assertThat(forgedDelete.getStatusCode(), is(HttpStatus.NOT_FOUND));

		ReadableProductReview[] reviews = reviews(productId);
		assertEquals(1, reviews.length);
		assertEquals("alice original", reviews[0].getDescription());

		// review must match the product in the path
		ResponseEntity<String> wrongProduct = testRestTemplate.exchange(
				"/api/v1/auth/products/" + (productId + 1000) + "/reviews/" + reviewId + STORE, HttpMethod.DELETE,
				new HttpEntity<>(customerHeader(alice)), String.class);
		assertThat(wrongProduct.getStatusCode(), is(HttpStatus.NOT_FOUND));

		// the author can update the review in place
		ResponseEntity<PersistableProductReview> updated = testRestTemplate.exchange(reviewUrl, HttpMethod.PUT,
				new HttpEntity<>(review(mallory.getId(), "alice edited"), customerHeader(alice)),
				PersistableProductReview.class);
		assertThat(updated.getStatusCode(), is(HttpStatus.OK));
		assertEquals(reviewId, updated.getBody().getId());
		assertEquals(alice.getId(), updated.getBody().getCustomerId());

		reviews = reviews(productId);
		assertEquals(1, reviews.length);
		assertEquals("alice edited", reviews[0].getDescription());

		// the author can delete the review
		ResponseEntity<String> deleted = testRestTemplate.exchange(reviewUrl, HttpMethod.DELETE,
				new HttpEntity<>(customerHeader(alice)), String.class);
		assertThat(deleted.getStatusCode(), is(HttpStatus.OK));
		assertEquals(0, reviews(productId).length);
	}

	@Test
	public void storeAdministratorCanModerateStoreReviews() {

		ReadableProduct product = sampleProduct("reviewModeration");
		Long productId = product.getId();

		AuthenticationResponse author = register("review-author@test.com");
		ResponseEntity<PersistableProductReview> created = testRestTemplate.postForEntity(
				"/api/v1/auth/products/" + productId + "/reviews" + STORE,
				new HttpEntity<>(review(author.getId(), "needs moderation"), customerHeader(author)),
				PersistableProductReview.class);
		assertThat(created.getStatusCode(), is(HttpStatus.CREATED));
		Long reviewId = created.getBody().getId();

		String reviewUrl = "/api/v1/private/products/" + productId + "/reviews/" + reviewId + STORE;

		ResponseEntity<PersistableProductReview> updated = testRestTemplate.exchange(reviewUrl, HttpMethod.PUT,
				new HttpEntity<>(review(null, "moderated"), getHeader()), PersistableProductReview.class);
		assertThat(updated.getStatusCode(), is(HttpStatus.OK));
		assertEquals(author.getId(), updated.getBody().getCustomerId());
		assertEquals("moderated", reviews(productId)[0].getDescription());

		ResponseEntity<String> deleted = testRestTemplate.exchange(reviewUrl, HttpMethod.DELETE,
				new HttpEntity<>(getHeader()), String.class);
		assertThat(deleted.getStatusCode(), is(HttpStatus.OK));
		assertEquals(0, reviews(productId).length);
	}

	private ReadableProductReview[] reviews(Long productId) {
		ResponseEntity<ReadableProductReview[]> response = testRestTemplate.getForEntity(
				"/api/v1/product/" + productId + "/reviews" + STORE, ReadableProductReview[].class);
		assertThat(response.getStatusCode(), is(HttpStatus.OK));
		return response.getBody();
	}

	private PersistableProductReview review(Long customerId, String description) {
		PersistableProductReview review = new PersistableProductReview();
		review.setCustomerId(customerId);
		review.setLanguage("en");
		review.setRating(4D);
		review.setDescription(description);
		return review;
	}

	private AuthenticationResponse register(String email) {
		PersistableCustomer customer = new PersistableCustomer();
		customer.setEmailAddress(email);
		customer.setPassword("clear123");
		customer.setGender(CustomerGender.M.name());
		customer.setLanguage("en");
		Address billing = new Address();
		billing.setFirstName("review");
		billing.setLastName("customer");
		billing.setCountry("BE");
		customer.setBilling(billing);
		customer.setStoreCode(Constants.DEFAULT_STORE);

		ResponseEntity<AuthenticationResponse> response = testRestTemplate.postForEntity(
				"/api/v1/customer/register" + STORE, new HttpEntity<>(customer), AuthenticationResponse.class);
		assertThat(response.getStatusCode(), is(HttpStatus.OK));
		assertNotNull(response.getBody().getId());
		assertNotNull(response.getBody().getToken());
		return response.getBody();
	}

	private HttpHeaders customerHeader(AuthenticationResponse auth) {
		HttpHeaders headers = new HttpHeaders();
		headers.setContentType(new MediaType("application", "json", Charset.forName("UTF-8")));
		headers.add("Authorization", "Bearer " + auth.getToken());
		return headers;
	}
}
