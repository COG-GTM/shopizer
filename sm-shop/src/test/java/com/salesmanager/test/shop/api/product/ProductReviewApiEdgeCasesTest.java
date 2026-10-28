package com.salesmanager.test.shop.api.product;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
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
import com.salesmanager.shop.store.api.exception.ServiceRuntimeException;
import com.salesmanager.shop.store.api.v1.product.ProductReviewApi;
import com.salesmanager.shop.store.controller.customer.facade.CustomerFacade;
import com.salesmanager.shop.store.controller.product.facade.ProductCommonFacade;

@RunWith(MockitoJUnitRunner.class)
public class ProductReviewApiEdgeCasesTest {

  private static final Long PATH_PRODUCT_ID = 55L;
  private static final Long CALLER_ID = 7L;
  private static final Long VICTIM_ID = 1L;
  private static final String PRINCIPAL_NAME = "caller@test.com";

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

  private PersistableProductReview review(Long customerId, Long productId, Double rating) {
    PersistableProductReview review = new PersistableProductReview();
    review.setCustomerId(customerId);
    review.setProductId(productId);
    review.setRating(rating);
    review.setDescription("review");
    return review;
  }

  private void authenticateAsCaller() {
    request.setUserPrincipal(() -> PRINCIPAL_NAME);
    Customer caller = new Customer();
    caller.setId(CALLER_ID);
    caller.setMerchantStore(store);
    when(customerFacade.getCustomerByUserName(PRINCIPAL_NAME, store)).thenReturn(caller);
  }

  @Test
  public void authRouteRatingAboveMaxIsRejectedWithoutSave() throws Exception {
    authenticateAsCaller();

    api.createAuthenticatedCustomerReview(
        PATH_PRODUCT_ID, review(VICTIM_ID, 999L, 6D), store, language, request, response);

    assertEquals(503, response.getStatus());
    verify(productCommonFacade, never()).saveOrUpdateReview(any(), any(), any());
  }

  @Test
  public void authRouteCustomerLookupFailureIsForbidden() throws Exception {
    request.setUserPrincipal(() -> PRINCIPAL_NAME);
    when(customerFacade.getCustomerByUserName(PRINCIPAL_NAME, store))
        .thenThrow(new ServiceRuntimeException("lookup failed"));

    api.createAuthenticatedCustomerReview(
        PATH_PRODUCT_ID, review(VICTIM_ID, 999L, 4D), store, language, request, response);

    assertEquals(403, response.getStatus());
    verify(productReviewService, never()).getByProductAndCustomer(anyLong(), anyLong());
    verify(productCommonFacade, never()).saveOrUpdateReview(any(), any(), any());
  }

  @Test
  public void authRouteLooksUpPrincipalInRequestStore() throws Exception {
    authenticateAsCaller();

    api.createAuthenticatedCustomerReview(
        PATH_PRODUCT_ID, review(VICTIM_ID, 999L, 4D), store, language, request, response);

    verify(customerFacade, times(1)).getCustomerByUserName(eq(PRINCIPAL_NAME), eq(store));
  }

  @Test
  public void privateRouteDuplicateCheckUsesPathProductNotBody() throws Exception {
    when(productReviewService.getByProductAndCustomer(PATH_PRODUCT_ID, VICTIM_ID))
        .thenReturn(new ProductReview());

    api.create(
        PATH_PRODUCT_ID, review(VICTIM_ID, 999L, 4D), store, language, request, response);

    assertEquals(500, response.getStatus());
    verify(productReviewService, never()).getByProductAndCustomer(eq(999L), anyLong());
    verify(productCommonFacade, never()).saveOrUpdateReview(any(), any(), any());
  }

  @Test
  public void authRouteSaveFailureReturns503() throws Exception {
    authenticateAsCaller();
    doThrow(new Exception("save failed"))
        .when(productCommonFacade)
        .saveOrUpdateReview(any(), eq(store), eq(language));

    api.createAuthenticatedCustomerReview(
        PATH_PRODUCT_ID, review(VICTIM_ID, 999L, 4D), store, language, request, response);

    assertEquals(503, response.getStatus());
  }

  @Test
  public void authRouteOverridesBodyCustomerEvenWhenItMatchesNothing() throws Exception {
    authenticateAsCaller();
    PersistableProductReview bodyReview = review(424242L, 999L, 4D);

    api.createAuthenticatedCustomerReview(
        PATH_PRODUCT_ID, bodyReview, store, language, request, response);

    ArgumentCaptor<PersistableProductReview> captor =
        ArgumentCaptor.forClass(PersistableProductReview.class);
    verify(productCommonFacade).saveOrUpdateReview(captor.capture(), eq(store), eq(language));
    assertEquals(CALLER_ID, captor.getValue().getCustomerId());
    assertEquals(PATH_PRODUCT_ID, captor.getValue().getProductId());
  }
}
