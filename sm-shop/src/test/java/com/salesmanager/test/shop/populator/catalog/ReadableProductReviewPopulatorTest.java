package com.salesmanager.test.shop.populator.catalog;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;

import java.util.Date;
import java.util.HashSet;
import java.util.Set;

import org.junit.Test;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.salesmanager.core.model.catalog.product.Product;
import com.salesmanager.core.model.catalog.product.review.ProductReview;
import com.salesmanager.core.model.catalog.product.review.ProductReviewDescription;
import com.salesmanager.core.model.common.Billing;
import com.salesmanager.core.model.common.Delivery;
import com.salesmanager.core.model.customer.Customer;
import com.salesmanager.core.model.customer.CustomerGender;
import com.salesmanager.core.model.reference.language.Language;
import com.salesmanager.shop.model.catalog.product.ReadableProductReview;
import com.salesmanager.shop.populator.catalog.ReadableProductReviewPopulator;
import com.salesmanager.shop.utils.DateUtil;

public class ReadableProductReviewPopulatorTest {

	@Test
	public void exposesOnlyReviewerDisplayNameForFullCustomer() throws Exception {
		Customer customer = new Customer();
		customer.setId(7L);
		customer.setEmailAddress("alice.doe@example.test");
		customer.setNick("alice.doe");
		customer.setGender(CustomerGender.F);
		customer.setCompany("Example Company");
		Billing billing = new Billing();
		billing.setFirstName("Alice");
		billing.setLastName("Doe");
		billing.setCompany("Example Company");
		billing.setAddress("Billing Street 1");
		billing.setCity("Billing City");
		billing.setPostalCode("12345");
		billing.setTelephone("111-222-3333");
		customer.setBilling(billing);
		Delivery delivery = new Delivery();
		delivery.setFirstName("Alice");
		delivery.setLastName("Doe");
		delivery.setCompany("Delivery Company");
		delivery.setAddress("Delivery Street 2");
		delivery.setCity("Delivery City");
		delivery.setPostalCode("54321");
		delivery.setTelephone("444-555-6666");
		customer.setDelivery(delivery);

		ReadableProductReview result = populate(reviewWithCustomer(customer));

		assertEquals(Long.valueOf(7L), result.getCustomer().getId());
		assertEquals("Alice", result.getCustomer().getFirstName());
		assertEquals("D.", result.getCustomer().getLastName());
		assertNull(result.getCustomer().getEmailAddress());
		assertNull(result.getCustomer().getUserName());
		assertNull(result.getCustomer().getGender());
		assertNull(result.getCustomer().getBilling());
		assertNull(result.getCustomer().getDelivery());

		String json = new ObjectMapper().writeValueAsString(result);
		assertFalse(json.contains("alice.doe@example.test"));
		assertFalse(json.contains("111-222-3333"));
		assertFalse(json.contains("444-555-6666"));
		assertFalse(json.contains("Billing Street 1"));
		assertFalse(json.contains("Delivery Street 2"));
		assertFalse(json.contains("Example Company"));
		assertFalse(json.contains("Delivery Company"));
		assertFalse(json.contains("Doe"));
	}

	@Test
	public void trimsLastNameBeforeTakingInitialAndHandlesMissingNames() throws Exception {
		Customer customer = new Customer();
		customer.setId(8L);
		Billing billing = new Billing();
		billing.setFirstName("Alice");
		billing.setLastName("  doe ");
		customer.setBilling(billing);

		ReadableProductReview result = populate(reviewWithCustomer(customer));

		assertEquals("d.", result.getCustomer().getLastName());

		billing.setLastName("   ");
		result = populate(reviewWithCustomer(customer));

		assertEquals("Alice", result.getCustomer().getFirstName());
		assertNull(result.getCustomer().getLastName());

		customer.setBilling(null);
		result = populate(reviewWithCustomer(customer));

		assertEquals(Long.valueOf(8L), result.getCustomer().getId());
		assertNull(result.getCustomer().getFirstName());
		assertNull(result.getCustomer().getLastName());
	}

	@Test
	public void handlesNullCustomerAndPopulatesReviewFields() throws Exception {
		Date reviewDate = new Date(1634567890000L);
		ProductReview source = reviewWithCustomer(null);
		source.setReviewDate(reviewDate);

		ReadableProductReview result = populate(source);

		assertNull(result.getCustomer());
		assertEquals(Long.valueOf(17L), result.getId());
		assertEquals(Double.valueOf(4.0), result.getRating());
		assertEquals(Long.valueOf(29L), result.getProductId());
		assertEquals("A useful review", result.getDescription());
		assertEquals("en", result.getLanguage());
		assertEquals(DateUtil.formatDate(reviewDate), result.getDate());
	}

	private ReadableProductReview populate(ProductReview source) throws Exception {
		return new ReadableProductReviewPopulator().populate(
				source, new ReadableProductReview(), null, null);
	}

	private ProductReview reviewWithCustomer(Customer customer) {
		Product product = new Product();
		product.setId(29L);

		Language language = new Language();
		language.setCode("en");
		ProductReviewDescription description = new ProductReviewDescription();
		description.setDescription("A useful review");
		description.setLanguage(language);
		Set<ProductReviewDescription> descriptions = new HashSet<>();
		descriptions.add(description);

		ProductReview review = new ProductReview();
		review.setId(17L);
		review.setReviewRating(4.0);
		review.setReviewDate(new Date(1634567890000L));
		review.setProduct(product);
		review.setCustomer(customer);
		review.setDescriptions(descriptions);
		return review;
	}
}
