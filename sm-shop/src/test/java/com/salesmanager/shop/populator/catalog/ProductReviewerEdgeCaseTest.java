package com.salesmanager.shop.populator.catalog;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import java.util.Date;

import org.junit.Test;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.salesmanager.core.model.catalog.product.Product;
import com.salesmanager.core.model.catalog.product.review.ProductReview;
import com.salesmanager.core.model.catalog.product.review.ProductReviewDescription;
import com.salesmanager.core.model.common.Billing;
import com.salesmanager.core.model.customer.Customer;
import com.salesmanager.core.model.reference.language.Language;
import com.salesmanager.shop.model.catalog.product.ReadableProductReview;
import com.salesmanager.shop.utils.DateUtil;

public class ProductReviewerEdgeCaseTest {

    private final ObjectMapper mapper = new ObjectMapper();

    @Test
    public void trimsBillingNameBeforeSelectingInitial() throws Exception {
        JsonNode customer = json(customer("  Alice  ", "  smith  ")).get("customer");
        assertEquals("Alice", customer.get("firstName").asText());
        assertEquals("Alice S.", customer.get("displayName").asText());
        assertEquals(2, customer.size());
    }

    @Test
    public void supplementaryUnicodeInitialIsNotSplitIntoSurrogates() throws Exception {
        JsonNode customer = json(customer("Alice", "\uD801\uDC00surname")).get("customer");
        assertEquals("Alice \uD801\uDC00.", customer.get("displayName").asText());
    }

    @Test
    public void absentFirstNameDoesNotFallBackToLastNameOrUsername() throws Exception {
        JsonNode customer = json(customer("   ", "SecretSurname")).get("customer");
        assertTrue(customer.get("firstName").isNull());
        assertTrue(customer.get("displayName").isNull());
        assertEquals(2, customer.size());
    }

    @Test
    public void absentCustomerProducesAnonymousReviewer() throws Exception {
        JsonNode customer = json(null).get("customer");
        assertNotNull(customer);
        assertEquals(2, customer.size());
        assertTrue(customer.get("firstName").isNull());
        assertTrue(customer.get("displayName").isNull());
    }

    @Test
    public void reviewMetadataAndTextRemainUnchanged() throws Exception {
        JsonNode review = json(customer("Alice", "Smith"));
        assertEquals(7L, review.get("id").asLong());
        assertEquals(123L, review.get("productId").asLong());
        assertEquals(4.0, review.get("rating").asDouble(), 0.0);
        assertEquals("Useful product", review.get("description").asText());
        assertEquals("en", review.get("language").asText());
        assertEquals(DateUtil.formatDate(new Date(1609459200000L)), review.get("date").asText());
    }

    private JsonNode json(Customer customer) throws Exception {
        Product product = new Product();
        product.setId(123L);
        ProductReview review = new ProductReview();
        review.setId(7L);
        review.setReviewDate(new Date(1609459200000L));
        review.setReviewRating(4.0);
        review.setProduct(product);
        review.setCustomer(customer);
        Language language = new Language();
        language.setCode("en");
        ProductReviewDescription description = new ProductReviewDescription();
        description.setLanguage(language);
        description.setDescription("Useful product");
        review.getDescriptions().add(description);
        return mapper.valueToTree(new ReadableProductReviewPopulator().populate(
                review, new ReadableProductReview(), null, language));
    }

    private static Customer customer(String firstName, String lastName) {
        Billing billing = new Billing();
        billing.setFirstName(firstName);
        billing.setLastName(lastName);
        Customer customer = new Customer();
        customer.setBilling(billing);
        customer.setNick("private-username");
        customer.setEmailAddress("private@example.test");
        return customer;
    }
}
