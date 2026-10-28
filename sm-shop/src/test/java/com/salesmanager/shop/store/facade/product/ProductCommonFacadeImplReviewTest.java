package com.salesmanager.shop.store.facade.product;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.Date;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnitRunner;

import com.salesmanager.core.business.services.catalog.product.ProductService;
import com.salesmanager.core.business.services.catalog.product.review.ProductReviewService;
import com.salesmanager.core.business.services.customer.CustomerService;
import com.salesmanager.core.business.services.reference.language.LanguageService;
import com.salesmanager.core.model.catalog.product.Product;
import com.salesmanager.core.model.catalog.product.review.ProductReview;
import com.salesmanager.core.model.catalog.product.review.ProductReviewDescription;
import com.salesmanager.core.model.customer.Customer;
import com.salesmanager.core.model.merchant.MerchantStore;
import com.salesmanager.core.model.reference.language.Language;
import com.salesmanager.shop.model.catalog.product.PersistableProductReview;
import com.salesmanager.shop.store.api.exception.ServiceRuntimeException;

@RunWith(MockitoJUnitRunner.class)
public class ProductCommonFacadeImplReviewTest {

    @InjectMocks
    private ProductCommonFacadeImpl facade;

    @Mock
    private ProductReviewService productReviewService;

    @Mock
    private CustomerService customerService;

    @Mock
    private ProductService productService;

    @Mock
    private LanguageService languageService;

    private MerchantStore store;
    private MerchantStore otherStore;
    private Product product;
    private Product otherProduct;
    private Customer customer;
    private Customer otherCustomer;
    private Language english;
    private Language french;

    @Before
    public void setUp() throws Exception {
        store = store(1);
        otherStore = store(2);
        product = product(10L, store);
        otherProduct = product(11L, store);
        customer = customer(100L, store);
        otherCustomer = customer(200L, store);
        english = language(1, "en");
        french = language(2, "fr");

        when(customerService.getById(100L)).thenReturn(customer);
        when(productService.getById(10L)).thenReturn(product);
        when(languageService.getByCode("en")).thenReturn(english);
    }

    @Test
    public void deleteReviewInCurrentStore() throws Exception {
        ProductReview review = storedReview(1L, 100L, product);

        facade.deleteReview(review, store, english);

        verify(productReviewService).delete(review);
    }

    @Test
    public void deleteReviewFromOtherStoreIsRejected() throws Exception {
        ProductReview review = storedReview(1L, 100L, product(10L, otherStore));

        assertThrows(ServiceRuntimeException.class, () -> facade.deleteReview(review, store, english));

        verify(productReviewService, never()).delete(any());
    }

    @Test
    public void deleteReviewWithNullProductIsRejected() throws Exception {
        ProductReview review = storedReview(1L, 100L, null);

        assertThrows(ServiceRuntimeException.class, () -> facade.deleteReview(review, store, english));

        verify(productReviewService, never()).delete(any());
    }

    @Test
    public void createsReviewWhenIdIsNull() throws Exception {
        PersistableProductReview review = requestReview(null, 100L, 10L, 4D, "new");

        facade.saveOrUpdateReview(review, store, english);

        verify(productReviewService).create(any(ProductReview.class));
        verify(productReviewService, never()).update(any());
    }

    @Test
    public void updateWithMissingReviewIsRejected() throws Exception {
        PersistableProductReview review = requestReview(5L, 100L, 10L, 4D, "new");
        when(productReviewService.getById(5L)).thenReturn(null);

        assertThrows(ServiceRuntimeException.class, () -> facade.saveOrUpdateReview(review, store, english));

        verify(productReviewService, never()).update(any());
    }

    @Test
    public void updateByAnotherCustomerIsRejected() throws Exception {
        PersistableProductReview review = requestReview(5L, 100L, 10L, 4D, "new");
        when(productReviewService.getById(5L)).thenReturn(storedReview(5L, 200L, product));

        assertThrows(ServiceRuntimeException.class, () -> facade.saveOrUpdateReview(review, store, english));

        verify(productReviewService, never()).update(any());
    }

    @Test
    public void updateForAnotherProductIsRejected() throws Exception {
        PersistableProductReview review = requestReview(5L, 100L, 10L, 4D, "new");
        when(productReviewService.getById(5L)).thenReturn(storedReview(5L, 100L, otherProduct));

        assertThrows(ServiceRuntimeException.class, () -> facade.saveOrUpdateReview(review, store, english));

        verify(productReviewService, never()).update(any());
    }

    @Test
    public void updateForReviewInAnotherStoreIsRejected() throws Exception {
        PersistableProductReview review = requestReview(5L, 100L, 10L, 4D, "new");
        when(productReviewService.getById(5L)).thenReturn(storedReview(5L, 100L, product(10L, otherStore)));

        assertThrows(ServiceRuntimeException.class, () -> facade.saveOrUpdateReview(review, store, english));

        verify(productReviewService, never()).update(any());
    }

    @Test
    public void updatesExistingReviewInPlace() throws Exception {
        ProductReview existing = storedReview(5L, 100L, product);
        existing.setReviewRating(2D);
        ProductReviewDescription oldDescription = description(existing, english, "old");
        existing.getDescriptions().add(oldDescription);
        when(productReviewService.getById(5L)).thenReturn(existing);
        PersistableProductReview review = requestReview(5L, 100L, 10L, 4D, "new");

        facade.saveOrUpdateReview(review, store, english);

        verify(productReviewService).update(org.mockito.ArgumentMatchers.same(existing));
        assertEquals(Double.valueOf(4D), existing.getReviewRating());
        assertEquals(1, existing.getDescriptions().size());
        assertEquals("new", existing.getDescriptions().iterator().next().getDescription());
        assertEquals(Long.valueOf(100L), existing.getCustomer().getId());
        assertEquals(Long.valueOf(5L), review.getId());
    }

    @Test
    public void updateAddsDescriptionForLanguageNotAlreadyPresent() throws Exception {
        ProductReview existing = storedReview(5L, 100L, product);
        existing.getDescriptions().add(description(existing, french, "ancien"));
        when(productReviewService.getById(5L)).thenReturn(existing);
        PersistableProductReview review = requestReview(5L, 100L, 10L, 4D, "new");

        facade.saveOrUpdateReview(review, store, english);

        verify(productReviewService).update(org.mockito.ArgumentMatchers.same(existing));
        assertEquals(2, existing.getDescriptions().size());
        ProductReviewDescription added = existing.getDescriptions().stream()
                .filter(item -> item.getLanguage().getId().equals(english.getId()))
                .findFirst()
                .orElseThrow(AssertionError::new);
        assertEquals("new", added.getDescription());
        assertSame(existing, added.getProductReview());
    }

    private MerchantStore store(int id) {
        MerchantStore result = new MerchantStore();
        result.setId(id);
        return result;
    }

    private Product product(Long id, MerchantStore merchantStore) {
        Product result = new Product();
        result.setId(id);
        result.setMerchantStore(merchantStore);
        return result;
    }

    private Customer customer(Long id, MerchantStore merchantStore) {
        Customer result = new Customer();
        result.setId(id);
        result.setMerchantStore(merchantStore);
        return result;
    }

    private Language language(int id, String code) {
        Language result = new Language();
        result.setId(id);
        result.setCode(code);
        return result;
    }

    private ProductReview storedReview(Long id, Long customerId, Product reviewProduct) {
        ProductReview result = new ProductReview();
        result.setId(id);
        result.setCustomer(customer(customerId, store));
        result.setProduct(reviewProduct);
        result.setReviewDate(new Date());
        return result;
    }

    private ProductReviewDescription description(
            ProductReview review, Language language, String text) {
        ProductReviewDescription result = new ProductReviewDescription();
        result.setLanguage(language);
        result.setDescription(text);
        result.setProductReview(review);
        return result;
    }

    private PersistableProductReview requestReview(
            Long id, Long customerId, Long productId, Double rating, String description) {
        PersistableProductReview result = new PersistableProductReview();
        result.setId(id);
        result.setCustomerId(customerId);
        result.setProductId(productId);
        result.setRating(rating);
        result.setDescription(description);
        result.setLanguage("en");
        return result;
    }
}
