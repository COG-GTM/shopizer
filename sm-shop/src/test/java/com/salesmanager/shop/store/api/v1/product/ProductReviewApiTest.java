package com.salesmanager.shop.store.api.v1.product;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import javax.servlet.http.HttpServletResponse;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnitRunner;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

import com.salesmanager.core.business.services.catalog.product.ProductService;
import com.salesmanager.core.business.services.catalog.product.review.ProductReviewService;
import com.salesmanager.core.business.services.customer.CustomerService;
import com.salesmanager.core.model.catalog.product.Product;
import com.salesmanager.core.model.catalog.product.review.ProductReview;
import com.salesmanager.core.model.customer.Customer;
import com.salesmanager.core.model.merchant.MerchantStore;
import com.salesmanager.core.model.reference.language.Language;
import com.salesmanager.shop.model.catalog.product.PersistableProductReview;
import com.salesmanager.shop.store.controller.product.facade.ProductCommonFacade;

@RunWith(MockitoJUnitRunner.class)
public class ProductReviewApiTest {

    @InjectMocks
    private ProductReviewApi api;

    @Mock
    private ProductCommonFacade productCommonFacade;

    @Mock
    private ProductService productService;

    @Mock
    private ProductReviewService productReviewService;

    @Mock
    private CustomerService customerService;

    private MerchantStore store;
    private MerchantStore otherStore;
    private Language language;
    private Customer owner;
    private Customer otherCustomer;
    private Product product;

    @Before
    public void setUp() {
        store = store(1);
        otherStore = store(2);
        language = new Language();
        owner = customer(100L);
        otherCustomer = customer(200L);
        product = product(10L, store);
        when(customerService.getByNick("alice@test.com", store.getId())).thenReturn(owner);
        when(customerService.getByNick("mallory@test.com", store.getId())).thenReturn(otherCustomer);
    }

    @Test
    public void customerUpdateWithoutPrincipalReturnsUnauthorized() throws Exception {
        MockHttpServletResponse response = new MockHttpServletResponse();

        api.updateAsCustomer(10L, 5L, requestReview(200L, 4D), store, language,
                new MockHttpServletRequest(), response);

        assertEquals(HttpServletResponse.SC_UNAUTHORIZED, response.getStatus());
        verify(productCommonFacade, never()).saveOrUpdateReview(any(), any(), any());
    }

    @Test
    public void customerDeleteWithoutPrincipalReturnsUnauthorized() throws Exception {
        MockHttpServletResponse response = new MockHttpServletResponse();

        api.deleteAsCustomer(10L, 5L, store, language, new MockHttpServletRequest(), response);

        assertEquals(HttpServletResponse.SC_UNAUTHORIZED, response.getStatus());
        verify(productCommonFacade, never()).deleteReview(any(), any(), any());
    }

    @Test
    public void customerCannotUpdateAnotherCustomersReview() throws Exception {
        when(productReviewService.getById(5L)).thenReturn(review(5L, 100L, product));
        MockHttpServletResponse response = new MockHttpServletResponse();

        api.updateAsCustomer(10L, 5L, requestReview(100L, 4D), store, language,
                request("mallory@test.com"), response);

        assertEquals(HttpServletResponse.SC_NOT_FOUND, response.getStatus());
        verify(productCommonFacade, never()).saveOrUpdateReview(any(), any(), any());
    }

    @Test
    public void customerCannotDeleteAnotherCustomersReview() throws Exception {
        when(productReviewService.getById(5L)).thenReturn(review(5L, 100L, product));
        MockHttpServletResponse response = new MockHttpServletResponse();

        api.deleteAsCustomer(10L, 5L, store, language, request("mallory@test.com"), response);

        assertEquals(HttpServletResponse.SC_NOT_FOUND, response.getStatus());
        verify(productCommonFacade, never()).deleteReview(any(), any(), any());
    }

    @Test
    public void customerUpdateForWrongProductReturnsNotFound() throws Exception {
        when(productReviewService.getById(5L)).thenReturn(review(5L, 100L, product));
        MockHttpServletResponse response = new MockHttpServletResponse();

        api.updateAsCustomer(11L, 5L, requestReview(100L, 4D), store, language,
                request("alice@test.com"), response);

        assertEquals(HttpServletResponse.SC_NOT_FOUND, response.getStatus());
        verify(productCommonFacade, never()).saveOrUpdateReview(any(), any(), any());
    }

    @Test
    public void customerDeleteForWrongProductReturnsNotFound() throws Exception {
        when(productReviewService.getById(5L)).thenReturn(review(5L, 100L, product));
        MockHttpServletResponse response = new MockHttpServletResponse();

        api.deleteAsCustomer(11L, 5L, store, language, request("alice@test.com"), response);

        assertEquals(HttpServletResponse.SC_NOT_FOUND, response.getStatus());
        verify(productCommonFacade, never()).deleteReview(any(), any(), any());
    }

    @Test
    public void privateDeleteForWrongProductReturnsNotFound() throws Exception {
        when(productReviewService.getById(5L)).thenReturn(review(5L, 100L, product));
        MockHttpServletResponse response = new MockHttpServletResponse();

        api.delete(11L, 5L, store, language, response);

        assertEquals(HttpServletResponse.SC_NOT_FOUND, response.getStatus());
        verify(productCommonFacade, never()).deleteReview(any(), any(), any());
    }

    @Test
    public void privateUpdateForAnotherStoreReturnsNotFound() throws Exception {
        when(productReviewService.getById(5L)).thenReturn(review(5L, 100L, product(10L, otherStore)));
        MockHttpServletResponse response = new MockHttpServletResponse();

        api.update(10L, 5L, requestReview(999L, 4D), store, language,
                new MockHttpServletRequest(), response);

        assertEquals(HttpServletResponse.SC_NOT_FOUND, response.getStatus());
        verify(productCommonFacade, never()).saveOrUpdateReview(any(), any(), any());
    }

    @Test
    public void privateDeleteForAnotherStoreReturnsNotFound() throws Exception {
        when(productReviewService.getById(5L)).thenReturn(review(5L, 100L, product(10L, otherStore)));
        MockHttpServletResponse response = new MockHttpServletResponse();

        api.delete(10L, 5L, store, language, response);

        assertEquals(HttpServletResponse.SC_NOT_FOUND, response.getStatus());
        verify(productCommonFacade, never()).deleteReview(any(), any(), any());
    }

    @Test
    public void missingReviewReturnsNotFound() throws Exception {
        when(productReviewService.getById(5L)).thenReturn(null);
        MockHttpServletResponse response = new MockHttpServletResponse();

        api.update(10L, 5L, requestReview(100L, 4D), store, language,
                new MockHttpServletRequest(), response);

        assertEquals(HttpServletResponse.SC_NOT_FOUND, response.getStatus());
        verify(productCommonFacade, never()).saveOrUpdateReview(any(), any(), any());
    }

    @Test
    public void customerUpdateUsesAuthenticatedCustomerAndPathIdentifiers() throws Exception {
        when(productReviewService.getById(5L)).thenReturn(review(5L, 100L, product));
        PersistableProductReview body = requestReview(200L, 4D);
        MockHttpServletResponse response = new MockHttpServletResponse();

        PersistableProductReview result = api.updateAsCustomer(10L, 5L, body, store, language,
                request("alice@test.com"), response);

        assertNotNull(result);
        assertEquals(Long.valueOf(100L), result.getCustomerId());
        assertEquals(Long.valueOf(5L), result.getId());
        assertEquals(Long.valueOf(10L), result.getProductId());
        verify(productCommonFacade).saveOrUpdateReview(body, store, language);
    }

    @Test
    public void privateUpdateKeepsOriginalAuthor() throws Exception {
        when(productReviewService.getById(5L)).thenReturn(review(5L, 100L, product));
        PersistableProductReview body = requestReview(999L, 4D);
        MockHttpServletResponse response = new MockHttpServletResponse();

        PersistableProductReview result = api.update(10L, 5L, body, store, language,
                new MockHttpServletRequest(), response);

        assertNotNull(result);
        assertEquals(Long.valueOf(100L), result.getCustomerId());
        assertEquals(Long.valueOf(5L), result.getId());
        assertEquals(Long.valueOf(10L), result.getProductId());
        verify(productCommonFacade).saveOrUpdateReview(body, store, language);
    }

    @Test
    public void customerCreateUsesAuthenticatedCustomerInsteadOfBodyCustomer() throws Exception {
        when(productReviewService.getByProductAndCustomer(10L, 100L)).thenReturn(null);
        PersistableProductReview body = requestReview(200L, 4D);
        MockHttpServletResponse response = new MockHttpServletResponse();

        PersistableProductReview result = api.createAsCustomer(10L, body, store, language,
                request("alice@test.com"), response);

        assertNotNull(result);
        assertTrue(response.getStatus() < HttpServletResponse.SC_BAD_REQUEST);
        assertEquals(Long.valueOf(100L), result.getCustomerId());
        assertEquals(null, result.getId());
        assertEquals(Long.valueOf(10L), result.getProductId());
        verify(productCommonFacade).saveOrUpdateReview(body, store, language);
    }

    @Test
    public void customerUpdateWithRatingAboveFiveReturnsServiceUnavailable() throws Exception {
        when(productReviewService.getById(5L)).thenReturn(review(5L, 100L, product));
        MockHttpServletResponse response = new MockHttpServletResponse();

        api.updateAsCustomer(10L, 5L, requestReview(100L, 6D), store, language,
                request("alice@test.com"), response);

        assertEquals(HttpServletResponse.SC_SERVICE_UNAVAILABLE, response.getStatus());
        verify(productCommonFacade, never()).saveOrUpdateReview(any(), any(), any());
    }

    private MockHttpServletRequest request(String principalName) {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setUserPrincipal(() -> principalName);
        return request;
    }

    private MerchantStore store(int id) {
        MerchantStore result = new MerchantStore();
        result.setId(id);
        return result;
    }

    private Customer customer(Long id) {
        Customer result = new Customer();
        result.setId(id);
        result.setMerchantStore(store);
        return result;
    }

    private Product product(Long id, MerchantStore merchantStore) {
        Product result = new Product();
        result.setId(id);
        result.setMerchantStore(merchantStore);
        return result;
    }

    private ProductReview review(Long id, Long customerId, Product reviewProduct) {
        ProductReview result = new ProductReview();
        result.setId(id);
        result.setCustomer(customer(customerId));
        result.setProduct(reviewProduct);
        return result;
    }

    private PersistableProductReview requestReview(Long customerId, Double rating) {
        PersistableProductReview result = new PersistableProductReview();
        result.setCustomerId(customerId);
        result.setRating(rating);
        result.setDescription("review");
        result.setLanguage("en");
        return result;
    }
}
