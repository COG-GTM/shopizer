package com.salesmanager.test.shop.api.product;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnitRunner;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

import com.salesmanager.core.business.services.catalog.product.review.ProductReviewService;
import com.salesmanager.core.model.catalog.product.review.ProductReview;
import com.salesmanager.core.model.customer.Customer;
import com.salesmanager.core.model.merchant.MerchantStore;
import com.salesmanager.core.model.reference.language.Language;
import com.salesmanager.shop.model.catalog.product.PersistableProductReview;
import com.salesmanager.shop.store.api.v1.product.ProductReviewApi;
import com.salesmanager.shop.store.controller.customer.facade.CustomerFacade;
import com.salesmanager.shop.store.controller.product.facade.ProductCommonFacade;

@RunWith(MockitoJUnitRunner.class)
public class ProductReviewApiTest {

  private static final Long PATH_PRODUCT_ID = 55L;
  private static final Long CALLER_ID = 7L;
  private static final Long VICTIM_ID = 1L;

  @Mock private ProductCommonFacade productCommonFacade;
  @Mock private ProductReviewService productReviewService;
  @Mock private CustomerFacade customerFacade;

  @InjectMocks private ProductReviewApi api;

  private MerchantStore store;
  private Language language;
  private MockHttpServletRequest request;
  private MockHttpServletResponse response;

  @Before
  public void setUp() {
    store = new MerchantStore();
    store.setId(1);
    store.setCode("ACME");
    language = new Language("en");
    request = new MockHttpServletRequest();
    response = new MockHttpServletResponse();
  }

  private PersistableProductReview impersonatingReview() {
    PersistableProductReview review = new PersistableProductReview();
    review.setCustomerId(VICTIM_ID);
    review.setProductId(999L);
    review.setRating(1D);
    review.setDescription("fake");
    return review;
  }

  private void authenticateAs(String userName, Long customerId) {
    request.setUserPrincipal(() -> userName);
    Customer caller = new Customer();
    caller.setId(customerId);
    caller.setMerchantStore(store);
    when(customerFacade.getCustomerByUserName(userName, store)).thenReturn(caller);
  }

  @Test
  public void authRouteSavesReviewUnderAuthenticatedCustomer() throws Exception {
    authenticateAs("mallory@test.com", CALLER_ID);

    PersistableProductReview saved =
        api.createAuthenticatedCustomerReview(
            PATH_PRODUCT_ID, impersonatingReview(), store, language, request, response);

    ArgumentCaptor<PersistableProductReview> captor =
        ArgumentCaptor.forClass(PersistableProductReview.class);
    verify(productCommonFacade).saveOrUpdateReview(captor.capture(), eq(store), eq(language));
    assertEquals(CALLER_ID, captor.getValue().getCustomerId());
    assertEquals(PATH_PRODUCT_ID, captor.getValue().getProductId());
    assertNotNull(saved);
    assertEquals(CALLER_ID, saved.getCustomerId());
    verify(productReviewService).getByProductAndCustomer(PATH_PRODUCT_ID, CALLER_ID);
  }

  @Test
  public void authRouteDuplicateCheckUsesPathProductRegardlessOfBody() throws Exception {
    authenticateAs("mallory@test.com", CALLER_ID);
    when(productReviewService.getByProductAndCustomer(PATH_PRODUCT_ID, CALLER_ID))
        .thenReturn(new ProductReview());

    PersistableProductReview saved =
        api.createAuthenticatedCustomerReview(
            PATH_PRODUCT_ID, impersonatingReview(), store, language, request, response);

    assertNull(saved);
    assertEquals(500, response.getStatus());
    verify(productCommonFacade, never()).saveOrUpdateReview(any(), any(), any());
  }

  @Test
  public void authRouteRejectsCustomerNotInRequestedStore() throws Exception {
    request.setUserPrincipal(() -> "mallory@other-store.com");
    when(customerFacade.getCustomerByUserName("mallory@other-store.com", store)).thenReturn(null);

    PersistableProductReview saved =
        api.createAuthenticatedCustomerReview(
            PATH_PRODUCT_ID, impersonatingReview(), store, language, request, response);

    assertNull(saved);
    assertEquals(403, response.getStatus());
    verify(productReviewService, never()).getByProductAndCustomer(anyLong(), anyLong());
    verify(productCommonFacade, never()).saveOrUpdateReview(any(), any(), any());
  }

  @Test
  public void authRouteRejectsMissingPrincipal() throws Exception {
    PersistableProductReview saved =
        api.createAuthenticatedCustomerReview(
            PATH_PRODUCT_ID, impersonatingReview(), store, language, request, response);

    assertNull(saved);
    assertEquals(403, response.getStatus());
    verify(productCommonFacade, never()).saveOrUpdateReview(any(), any(), any());
  }

  @Test
  public void authRouteIgnoresBodyReviewId() throws Exception {
    authenticateAs("mallory@test.com", CALLER_ID);
    PersistableProductReview review = impersonatingReview();
    review.setId(123L);

    api.createAuthenticatedCustomerReview(PATH_PRODUCT_ID, review, store, language, request, response);

    ArgumentCaptor<PersistableProductReview> captor =
        ArgumentCaptor.forClass(PersistableProductReview.class);
    verify(productCommonFacade).saveOrUpdateReview(captor.capture(), eq(store), eq(language));
    assertNull(captor.getValue().getId());
  }

  @Test
  public void privateRouteKeepsBodyCustomerAndUsesPathProduct() throws Exception {
    PersistableProductReview saved =
        api.create(PATH_PRODUCT_ID, impersonatingReview(), store, language, request, response);

    assertNotNull(saved);
    assertEquals(VICTIM_ID, saved.getCustomerId());
    assertEquals(PATH_PRODUCT_ID, saved.getProductId());
    verify(productReviewService).getByProductAndCustomer(PATH_PRODUCT_ID, VICTIM_ID);
    verify(customerFacade, never()).getCustomerByUserName(any(), any());
  }
}
