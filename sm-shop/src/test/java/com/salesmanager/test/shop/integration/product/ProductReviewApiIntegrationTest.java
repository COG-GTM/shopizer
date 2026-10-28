package com.salesmanager.test.shop.integration.product;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;
import static org.springframework.http.HttpStatus.CREATED;
import static org.springframework.http.HttpStatus.OK;

import java.util.UUID;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.SpringBootTest.WebEnvironment;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpMethod;
import org.springframework.http.ResponseEntity;
import org.springframework.test.context.junit4.SpringRunner;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.salesmanager.core.business.constants.Constants;
import com.salesmanager.shop.application.ShopApplication;
import com.salesmanager.shop.model.catalog.product.PersistableProductReview;
import com.salesmanager.shop.model.catalog.product.ReadableProduct;
import com.salesmanager.shop.model.customer.PersistableCustomer;
import com.salesmanager.shop.model.customer.address.Address;
import com.salesmanager.shop.populator.customer.ReadableCustomerList;
import com.salesmanager.test.shop.common.ServicesTestSupport;

@SpringBootTest(classes = ShopApplication.class, webEnvironment = WebEnvironment.RANDOM_PORT)
@RunWith(SpringRunner.class)
public class ProductReviewApiIntegrationTest extends ServicesTestSupport {

	@Test
	public void publiclyReadableReviewContainsOnlyReviewerDisplayName() throws Exception {
		String suffix = UUID.randomUUID().toString().replace("-", "");
		String email = "review-" + suffix + "@example.test";
		String lastName = "Reviewer" + suffix;
		String phone = "555-" + suffix.substring(0, 7);
		String street = "Private Street " + suffix;
		String company = "Private Company " + suffix;

		PersistableCustomer customer = new PersistableCustomer();
		customer.setEmailAddress(email);
		customer.setPassword("clear123");
		customer.setLanguage("en");
		customer.setStoreCode(Constants.DEFAULT_STORE);
		Address billing = new Address();
		billing.setFirstName("Alice");
		billing.setLastName(lastName);
		billing.setPhone(phone);
		billing.setCompany(company);
		billing.setAddress(street);
		billing.setCity("Private City");
		billing.setPostalCode("12345");
		billing.setCountry("BE");
		billing.setCountryCode("BE");
		customer.setBilling(billing);

		ResponseEntity<PersistableCustomer> registration = testRestTemplate.postForEntity(
				"/api/v1/customer/register?store=" + Constants.DEFAULT_STORE,
				new HttpEntity<>(customer, getHeader()), PersistableCustomer.class);
		assertEquals(OK, registration.getStatusCode());

		ReadableCustomerList customers = fetchCustomers();
		Long customerId = customers.getCustomers().stream()
				.filter(found -> email.equals(found.getEmailAddress()))
				.map(found -> found.getId())
				.findFirst()
				.orElse(null);
		assertNotNull(customerId);

		ReadableProduct product = sampleProduct("review-" + suffix);
		assertNotNull(product.getId());

		PersistableProductReview review = new PersistableProductReview();
		review.setCustomerId(customerId);
		review.setProductId(product.getId());
		review.setRating(4.0);
		review.setDescription("A public review");
		review.setLanguage("en");
		ResponseEntity<PersistableProductReview> created = testRestTemplate.postForEntity(
				"/api/v1/private/products/" + product.getId() + "/reviews?store=" + Constants.DEFAULT_STORE,
				new HttpEntity<>(review, getHeader()), PersistableProductReview.class);
		assertEquals(CREATED, created.getStatusCode());

		ResponseEntity<String> publicReviews = testRestTemplate.exchange(
				"/api/v1/product/" + product.getId() + "/reviews?store=" + Constants.DEFAULT_STORE,
				HttpMethod.GET, HttpEntity.EMPTY, String.class);
		assertEquals(OK, publicReviews.getStatusCode());
		assertNotNull(publicReviews.getBody());

		JsonNode reviews = new ObjectMapper().readTree(publicReviews.getBody());
		assertEquals(1, reviews.size());
		JsonNode reviewer = reviews.get(0).get("customer");
		assertNotNull(reviewer);
		assertEquals(customerId.longValue(), reviewer.get("id").asLong());
		assertEquals("Alice", reviewer.get("firstName").asText());
		assertEquals(lastName.substring(0, 1) + ".", reviewer.get("lastName").asText());
		assertNullOrMissing(reviewer, "emailAddress");
		assertNullOrMissing(reviewer, "userName");
		assertNullOrMissing(reviewer, "nick");
		assertNullOrMissing(reviewer, "gender");
		assertNullOrMissing(reviewer, "billing");
		assertNullOrMissing(reviewer, "delivery");

		String rawBody = publicReviews.getBody();
		assertFalse(rawBody.contains(email));
		assertFalse(rawBody.contains(lastName));
		assertFalse(rawBody.contains(phone));
		assertFalse(rawBody.contains(street));
		assertFalse(rawBody.contains(company));

	}

	@Test
	public void returnsEmptyReviewsForProductId999999() throws Exception {
		ResponseEntity<String> reviews = testRestTemplate.exchange(
				"/api/v1/product/999999/reviews?store=" + Constants.DEFAULT_STORE,
				HttpMethod.GET, HttpEntity.EMPTY, String.class);

		assertEquals(OK, reviews.getStatusCode());
		assertEquals(0, new ObjectMapper().readTree(reviews.getBody()).size());
	}

	private void assertNullOrMissing(JsonNode object, String field) {
		assertTrue(!object.has(field) || object.get(field).isNull());
	}
}
