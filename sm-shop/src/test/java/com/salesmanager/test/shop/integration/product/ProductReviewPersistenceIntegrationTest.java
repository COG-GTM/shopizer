package com.salesmanager.test.shop.integration.product;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;

import java.util.Date;
import java.util.List;
import java.util.UUID;

import javax.persistence.EntityManager;
import javax.persistence.PersistenceContext;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.SpringBootTest.WebEnvironment;
import org.springframework.test.context.junit4.SpringRunner;
import org.springframework.transaction.annotation.Transactional;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.salesmanager.core.business.services.catalog.product.review.ProductReviewService;
import com.salesmanager.core.business.services.merchant.MerchantStoreService;
import com.salesmanager.core.business.services.reference.language.LanguageService;
import com.salesmanager.core.business.services.reference.country.CountryService;
import com.salesmanager.core.model.catalog.product.Product;
import com.salesmanager.core.model.catalog.product.review.ProductReview;
import com.salesmanager.core.model.catalog.product.review.ProductReviewDescription;
import com.salesmanager.core.model.common.Billing;
import com.salesmanager.core.model.common.Delivery;
import com.salesmanager.core.model.customer.Customer;
import com.salesmanager.core.model.merchant.MerchantStore;
import com.salesmanager.core.model.reference.language.Language;
import com.salesmanager.shop.application.ShopApplication;
import com.salesmanager.shop.model.catalog.product.ReadableProductReview;
import com.salesmanager.shop.store.controller.product.facade.ProductCommonFacade;

@RunWith(SpringRunner.class)
@SpringBootTest(classes = ShopApplication.class, webEnvironment = WebEnvironment.RANDOM_PORT)
@Transactional
public class ProductReviewPersistenceIntegrationTest {

    @PersistenceContext
    private EntityManager entityManager;
    @Autowired
    private MerchantStoreService stores;
    @Autowired
    private LanguageService languages;
    @Autowired
    private CountryService countries;
    @Autowired
    private ProductReviewService reviews;
    @Autowired
    private ProductCommonFacade facade;

    @Test
    public void persistedPrivateCustomerDataIsNotIncludedInPublicReview() throws Exception {
        MerchantStore store = stores.getByCode(MerchantStore.DEFAULT_STORE);
        Language language = languages.getByCode("en");
        Product product = new Product();
        product.setSku("review-privacy-" + UUID.randomUUID());
        product.setMerchantStore(store);
        entityManager.persist(product);

        Billing billing = new Billing();
        billing.setFirstName("Alice");
        billing.setLastName("SensitiveSurname");
        billing.setCompany("Private Company");
        billing.setAddress("13 Private Road");
        billing.setPostalCode("PRIVATE123");
        billing.setTelephone("555-987-6543");
        billing.setCountry(countries.getByCode("BE"));
        Delivery delivery = new Delivery();
        delivery.setFirstName("Alice");
        delivery.setLastName("SensitiveSurname");
        delivery.setAddress("99 Delivery Road");
        delivery.setCountry(countries.getByCode("BE"));
        Customer customer = new Customer();
        customer.setEmailAddress("review-" + UUID.randomUUID() + "@example.test");
        customer.setNick("private-" + UUID.randomUUID());
        customer.setDefaultLanguage(language);
        customer.setMerchantStore(store);
        customer.setBilling(billing);
        customer.setDelivery(delivery);
        entityManager.persist(customer);

        ProductReview review = new ProductReview();
        review.setCustomer(customer);
        review.setProduct(product);
        review.setReviewDate(new Date());
        review.setReviewRating(4.0);
        ProductReviewDescription description = new ProductReviewDescription(language, "Review");
        description.setDescription("Useful product");
        description.setProductReview(review);
        review.getDescriptions().add(description);
        entityManager.persist(review);
        entityManager.flush();
        Long reviewId = review.getId();
        Long customerId = customer.getId();
        entityManager.clear();

        List<ProductReview> reloaded = reviews.getByProduct(product);
        assertEquals(1, reloaded.size());
        assertEquals("SensitiveSurname", reloaded.get(0).getCustomer().getBilling().getLastName());
        assertEquals("13 Private Road", entityManager.find(Customer.class, customerId).getBilling().getAddress());

        List<ReadableProductReview> readable = facade.getProductReviews(product, store, language);
        assertEquals(1, readable.size());
        JsonNode json = new ObjectMapper().valueToTree(readable.get(0));
        assertEquals(reviewId.longValue(), json.get("id").asLong());
        assertEquals(product.getId().longValue(), json.get("productId").asLong());
        assertEquals("Useful product", json.get("description").asText());
        assertEquals(4.0, json.get("rating").asDouble(), 0.0);
        assertEquals("en", json.get("language").asText());
        JsonNode author = json.get("customer");
        assertNotNull(author);
        assertEquals(2, author.size());
        assertEquals("Alice", author.get("firstName").asText());
        assertEquals("Alice S.", author.get("displayName").asText());
        for (String privateValue : new String[] {customer.getEmailAddress(), customer.getNick(),
                "SensitiveSurname", "Private Company", "13 Private Road", "PRIVATE123",
                "555-987-6543", "99 Delivery Road"}) {
            assertFalse("Public review contains " + privateValue, json.toString().contains(privateValue));
        }
    }

    @Test
    public void persistedProductWithoutReviewsReturnsEmptyList() throws Exception {
        MerchantStore store = stores.getByCode(MerchantStore.DEFAULT_STORE);
        Product product = new Product();
        product.setSku("no-reviews-" + UUID.randomUUID());
        product.setMerchantStore(store);
        entityManager.persist(product);
        entityManager.flush();
        entityManager.clear();

        assertEquals(0, facade.getProductReviews(product, store, languages.getByCode("en")).size());
    }
}
