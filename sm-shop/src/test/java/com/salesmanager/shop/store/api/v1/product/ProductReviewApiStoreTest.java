package com.salesmanager.shop.store.api.v1.product;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertSame;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.util.Collections;
import java.util.List;

import org.junit.Before;
import org.junit.Test;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.test.util.ReflectionTestUtils;

import com.salesmanager.core.business.services.catalog.product.ProductService;
import com.salesmanager.core.model.catalog.product.Product;
import com.salesmanager.core.model.merchant.MerchantStore;
import com.salesmanager.core.model.reference.language.Language;
import com.salesmanager.shop.model.catalog.product.ReadableProductReview;
import com.salesmanager.shop.store.controller.product.facade.ProductCommonFacade;

public class ProductReviewApiStoreTest {

    private final ProductService products = mock(ProductService.class);
    private final ProductCommonFacade facade = mock(ProductCommonFacade.class);
    private final ProductReviewApi api = new ProductReviewApi();
    private final MockHttpServletResponse response = new MockHttpServletResponse();
    private final MerchantStore requestedStore = store(1000);
    private final Language language = new Language();

    @Before
    public void setUp() {
        ReflectionTestUtils.setField(api, "productService", products);
        ReflectionTestUtils.setField(api, "productCommonFacade", facade);
    }

    @Test
    public void sameStoreIdAllowsReviewsEvenWithDifferentStoreInstances() throws Exception {
        Product product = product(store(1000));
        when(products.getById(23L)).thenReturn(product);
        List<ReadableProductReview> reviews = Collections.singletonList(new ReadableProductReview());
        when(facade.getProductReviews(product, requestedStore, language)).thenReturn(reviews);

        assertSame(reviews, api.getAll(23L, requestedStore, language, response));
        assertEquals(200, response.getStatus());
        verify(facade).getProductReviews(product, requestedStore, language);
    }

    @Test
    public void differentStoreReturns404WithoutFetchingReviewers() throws Exception {
        when(products.getById(23L)).thenReturn(product(store(1001)));

        assertNull(api.getAll(23L, requestedStore, language, response));
        assertEquals(404, response.getStatus());
        verifyNoInteractions(facade);
    }

    @Test
    public void productWithoutStoreReturns404WithoutFetchingReviewers() throws Exception {
        when(products.getById(23L)).thenReturn(product(null));

        assertNull(api.getAll(23L, requestedStore, language, response));
        assertEquals(404, response.getStatus());
        verifyNoInteractions(facade);
    }

    @Test
    public void missingProductReturns404WithoutFetchingReviewers() throws Exception {
        assertNull(api.getAll(23L, requestedStore, language, response));
        assertEquals(404, response.getStatus());
        verifyNoInteractions(facade);
    }

    @Test
    public void sameStoreWithoutReviewsStillReturnsEmptyList() throws Exception {
        Product product = product(requestedStore);
        when(products.getById(23L)).thenReturn(product);
        when(facade.getProductReviews(product, requestedStore, language)).thenReturn(Collections.emptyList());

        assertEquals(Collections.emptyList(), api.getAll(23L, requestedStore, language, response));
        assertEquals(200, response.getStatus());
    }

    private static Product product(MerchantStore store) {
        Product product = new Product();
        product.setId(23L);
        product.setMerchantStore(store);
        return product;
    }

    private static MerchantStore store(int id) {
        MerchantStore store = new MerchantStore();
        store.setId(id);
        return store;
    }
}
