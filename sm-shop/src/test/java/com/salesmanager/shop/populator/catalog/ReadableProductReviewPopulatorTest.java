package com.salesmanager.shop.populator.catalog;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import java.util.Date;

import org.junit.Test;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.salesmanager.core.model.catalog.product.Product;
import com.salesmanager.core.model.catalog.product.review.ProductReview;
import com.salesmanager.core.model.common.Billing;
import com.salesmanager.core.model.common.Delivery;
import com.salesmanager.core.model.customer.Customer;
import com.salesmanager.shop.model.catalog.product.ReadableProductReview;

public class ReadableProductReviewPopulatorTest {

	private final ReadableProductReviewPopulator populator = new ReadableProductReviewPopulator();

	@Test
	public void reviewerExposesOnlyFirstNameAndLastInitial() throws Exception {
		ReadableProductReview review = populator.populate(review(customer("Alice", "smith")),
				new ReadableProductReview(), null, null);

		assertEquals("Alice", review.getCustomer().getFirstName());
		assertEquals("Alice S.", review.getCustomer().getDisplayName());

		JsonNode json = new ObjectMapper().valueToTree(review);
		JsonNode customer = json.get("customer");
		assertNotNull(customer);
		assertEquals(2, customer.size());
		assertTrue(customer.has("firstName"));
		assertTrue(customer.has("displayName"));

		String body = json.toString();
		for (String leaked : new String[] { "alice@example.com", "alice-nick", "555-0100", "Acme Corp",
				"1 Main St", "H0H 0H0", "2 Delivery Rd", "smith", "emailAddress", "billing", "delivery",
				"userName", "gender", "phone" }) {
			assertFalse("review JSON leaks " + leaked + ": " + body, body.contains(leaked));
		}
	}

	@Test
	public void reviewerWithoutLastName() throws Exception {
		ReadableProductReview review = populator.populate(review(customer("Alice", " ")),
				new ReadableProductReview(), null, null);

		assertEquals("Alice", review.getCustomer().getDisplayName());
	}

	@Test
	public void reviewerWithoutBillingHasNoName() throws Exception {
		Customer customer = customer("Alice", "Smith");
		customer.setBilling(null);

		ReadableProductReview review = populator.populate(review(customer), new ReadableProductReview(), null, null);

		assertNotNull(review.getCustomer());
		assertNull(review.getCustomer().getFirstName());
		assertNull(review.getCustomer().getDisplayName());
	}

	private static ProductReview review(Customer customer) {
		Product product = new Product();
		product.setId(123L);
		ProductReview review = new ProductReview();
		review.setId(7L);
		review.setReviewDate(new Date());
		review.setReviewRating(4.0);
		review.setProduct(product);
		review.setCustomer(customer);
		return review;
	}

	private static Customer customer(String firstName, String lastName) {
		Billing billing = new Billing();
		billing.setFirstName(firstName);
		billing.setLastName(lastName);
		billing.setCompany("Acme Corp");
		billing.setAddress("1 Main St");
		billing.setPostalCode("H0H 0H0");
		billing.setTelephone("555-0100");

		Delivery delivery = new Delivery();
		delivery.setFirstName(firstName);
		delivery.setLastName(lastName);
		delivery.setAddress("2 Delivery Rd");
		delivery.setTelephone("555-0100");

		Customer customer = new Customer();
		customer.setId(42L);
		customer.setEmailAddress("alice@example.com");
		customer.setNick("alice-nick");
		customer.setBilling(billing);
		customer.setDelivery(delivery);
		return customer;
	}
}
