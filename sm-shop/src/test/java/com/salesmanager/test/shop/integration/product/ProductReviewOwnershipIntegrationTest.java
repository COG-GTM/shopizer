package com.salesmanager.test.shop.integration.product;

import static org.hamcrest.core.Is.is;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.assertThat;

import java.nio.charset.Charset;
import java.util.concurrent.atomic.AtomicLong;

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
public class ProductReviewOwnershipIntegrationTest extends ServicesTestSupport {

    private static final String STORE = "?store=" + Constants.DEFAULT_STORE;
    private static final AtomicLong IDS = new AtomicLong(System.currentTimeMillis());

    @Test
    public void unauthenticatedCustomerReviewUpdatesAndDeletesAreRejected() {
        ReadableProduct product = sampleProduct("reviewUnauth" + nextId());
        AuthenticationResponse owner = register("review-owner-" + nextId() + "@test.com");
        Long reviewId = createReview(product.getId(), owner, null, "original").getBody().getId();
        String path = "/api/v1/auth/products/" + product.getId() + "/reviews/" + reviewId + STORE;

        ResponseEntity<String> update = testRestTemplate.exchange(path, HttpMethod.PUT,
                new HttpEntity<>(review(null, 1D, "changed")), String.class);
        ResponseEntity<String> delete = testRestTemplate.exchange(path, HttpMethod.DELETE, null, String.class);

        assertEquals(HttpStatus.UNAUTHORIZED, update.getStatusCode());
        assertEquals(HttpStatus.UNAUTHORIZED, delete.getStatusCode());
        assertReview(product.getId(), reviewId, 4D, "original", owner.getId());
    }

    @Test
    public void customerTokenCannotUsePrivateReviewRoutes() {
        ReadableProduct product = sampleProduct("reviewPrivateAuth" + nextId());
        AuthenticationResponse owner = register("review-private-" + nextId() + "@test.com");
        Long reviewId = createReview(product.getId(), owner, null, "original").getBody().getId();
        String path = "/api/v1/private/products/" + product.getId() + "/reviews/" + reviewId + STORE;

        ResponseEntity<String> update = testRestTemplate.exchange(path, HttpMethod.PUT,
                new HttpEntity<>(review(null, 1D, "changed"), customerHeader(owner)), String.class);
        ResponseEntity<String> delete = testRestTemplate.exchange(path, HttpMethod.DELETE,
                new HttpEntity<>(customerHeader(owner)), String.class);

        System.out.println("Customer-token private review status codes: PUT="
                + update.getStatusCodeValue() + ", DELETE=" + delete.getStatusCodeValue());
        assertTrue(update.getStatusCode() == HttpStatus.UNAUTHORIZED
                || update.getStatusCode() == HttpStatus.FORBIDDEN);
        assertTrue(delete.getStatusCode() == HttpStatus.UNAUTHORIZED
                || delete.getStatusCode() == HttpStatus.FORBIDDEN);
        assertReview(product.getId(), reviewId, 4D, "original", owner.getId());
    }

    @Test
    public void administratorCannotChangeReviewThroughWrongProductPath() {
        ReadableProduct product = sampleProduct("reviewWrongPath" + nextId());
        AuthenticationResponse owner = register("review-wrong-path-" + nextId() + "@test.com");
        Long reviewId = createReview(product.getId(), owner, null, "original").getBody().getId();
        String path = "/api/v1/private/products/" + (product.getId() + 10000L)
                + "/reviews/" + reviewId + STORE;
        HttpHeaders adminHeaders = getHeader();

        ResponseEntity<String> update = testRestTemplate.exchange(path, HttpMethod.PUT,
                new HttpEntity<>(review(null, 1D, "changed"), adminHeaders), String.class);
        ResponseEntity<String> delete = testRestTemplate.exchange(path, HttpMethod.DELETE,
                new HttpEntity<>(adminHeaders), String.class);

        assertEquals(HttpStatus.NOT_FOUND, update.getStatusCode());
        assertEquals(HttpStatus.NOT_FOUND, delete.getStatusCode());
        assertReview(product.getId(), reviewId, 4D, "original", owner.getId());
    }

    @Test
    public void customerPostUsesAuthenticatedCustomerAndDuplicatePostKeepsExistingStatus() {
        ReadableProduct product = sampleProduct("reviewPostOwner" + nextId());
        AuthenticationResponse owner = register("review-post-owner-" + nextId() + "@test.com");
        AuthenticationResponse forgedCustomer = register("review-forged-" + nextId() + "@test.com");

        ResponseEntity<PersistableProductReview> first = createReview(
                product.getId(), owner, forgedCustomer.getId(), "first review");

        assertEquals(HttpStatus.CREATED, first.getStatusCode());
        assertEquals(owner.getId(), first.getBody().getCustomerId());
        ReadableProductReview[] saved = reviews(product.getId());
        assertEquals(1, saved.length);
        assertEquals(owner.getId(), saved[0].getCustomer().getId());

        ResponseEntity<String> duplicate = testRestTemplate.postForEntity(
                "/api/v1/auth/products/" + product.getId() + "/reviews" + STORE,
                new HttpEntity<>(review(forgedCustomer.getId(), 3D, "duplicate"),
                        customerHeader(owner)),
                String.class);

        assertEquals(HttpStatus.INTERNAL_SERVER_ERROR, duplicate.getStatusCode());
        assertEquals(1, reviews(product.getId()).length);
    }

    @Test
    public void customersCanOnlyDeleteTheirOwnReviewWhenTwoReviewsShareAProduct() {
        ReadableProduct product = sampleProduct("reviewSeparateOwners" + nextId());
        AuthenticationResponse owner = register("review-owner-b-" + nextId() + "@test.com");
        AuthenticationResponse other = register("review-owner-a-" + nextId() + "@test.com");
        Long ownerReviewId = createReview(product.getId(), owner, null, "owner review").getBody().getId();
        Long otherReviewId = createReview(product.getId(), other, null, "other review").getBody().getId();
        String ownerPath = "/api/v1/auth/products/" + product.getId()
                + "/reviews/" + ownerReviewId + STORE;
        String otherPath = "/api/v1/auth/products/" + product.getId()
                + "/reviews/" + otherReviewId + STORE;

        ResponseEntity<String> forbiddenDelete = testRestTemplate.exchange(ownerPath,
                HttpMethod.DELETE, new HttpEntity<>(customerHeader(other)), String.class);
        assertEquals(HttpStatus.NOT_FOUND, forbiddenDelete.getStatusCode());
        assertEquals(2, reviews(product.getId()).length);

        ResponseEntity<String> ownDelete = testRestTemplate.exchange(otherPath,
                HttpMethod.DELETE, new HttpEntity<>(customerHeader(other)), String.class);

        assertEquals(HttpStatus.OK, ownDelete.getStatusCode());
        ReadableProductReview[] remaining = reviews(product.getId());
        assertEquals(1, remaining.length);
        assertEquals(owner.getId(), remaining[0].getCustomer().getId());
    }

    @Test
    public void administratorUpdateChangesRatingWithoutChangingAuthor() {
        ReadableProduct product = sampleProduct("reviewAdminUpdate" + nextId());
        AuthenticationResponse owner = register("review-admin-owner-" + nextId() + "@test.com");
        Long reviewId = createReview(product.getId(), owner, null, "original").getBody().getId();
        String path = "/api/v1/private/products/" + product.getId()
                + "/reviews/" + reviewId + STORE;

        ResponseEntity<PersistableProductReview> updated = testRestTemplate.exchange(path,
                HttpMethod.PUT,
                new HttpEntity<>(review(999L, 2D, "updated"), getHeader()),
                PersistableProductReview.class);

        assertEquals(HttpStatus.OK, updated.getStatusCode());
        ReadableProductReview[] saved = reviews(product.getId());
        assertEquals(1, saved.length);
        assertEquals(Double.valueOf(2D), saved[0].getRating());
        assertEquals(owner.getId(), saved[0].getCustomer().getId());
    }

    private ResponseEntity<PersistableProductReview> createReview(
            Long productId, AuthenticationResponse poster, Long forgedCustomerId, String description) {
        return testRestTemplate.postForEntity(
                "/api/v1/auth/products/" + productId + "/reviews" + STORE,
                new HttpEntity<>(review(forgedCustomerId, 4D, description), customerHeader(poster)),
                PersistableProductReview.class);
    }

    private ReadableProductReview[] reviews(Long productId) {
        ResponseEntity<ReadableProductReview[]> response = testRestTemplate.getForEntity(
                "/api/v1/product/" + productId + "/reviews" + STORE, ReadableProductReview[].class);
        assertThat(response.getStatusCode(), is(HttpStatus.OK));
        return response.getBody();
    }

    private void assertReview(
            Long productId, Long reviewId, Double rating, String description, Long customerId) {
        ReadableProductReview[] saved = reviews(productId);
        assertEquals(1, saved.length);
        assertEquals(reviewId, saved[0].getId());
        assertEquals(rating, saved[0].getRating());
        assertEquals(description, saved[0].getDescription());
        assertEquals(customerId, saved[0].getCustomer().getId());
    }

    private PersistableProductReview review(Long customerId, Double rating, String description) {
        PersistableProductReview result = new PersistableProductReview();
        result.setCustomerId(customerId);
        result.setLanguage("en");
        result.setRating(rating);
        result.setDescription(description);
        return result;
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
                "/api/v1/customer/register" + STORE,
                new HttpEntity<>(customer), AuthenticationResponse.class);
        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertNotNull(response.getBody().getToken());
        return response.getBody();
    }

    private HttpHeaders customerHeader(AuthenticationResponse auth) {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(new MediaType("application", "json", Charset.forName("UTF-8")));
        headers.add("Authorization", "Bearer " + auth.getToken());
        return headers;
    }

    private long nextId() {
        return IDS.incrementAndGet();
    }
}
