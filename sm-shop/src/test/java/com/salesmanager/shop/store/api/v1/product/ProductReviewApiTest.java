package com.salesmanager.shop.store.api.v1.product;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertSame;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.concurrent.atomic.AtomicReference;

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
import com.salesmanager.core.model.catalog.product.review.ProductReview;
import com.salesmanager.core.model.customer.Customer;
import com.salesmanager.core.model.merchant.MerchantStore;
import com.salesmanager.core.model.reference.language.Language;
import com.salesmanager.shop.model.catalog.product.PersistableProductReview;
import com.salesmanager.shop.store.controller.customer.facade.CustomerFacade;
import com.salesmanager.shop.store.controller.product.facade.ProductCommonFacade;

@RunWith(MockitoJUnitRunner.class)
public class ProductReviewApiTest {

  private static final Long PATH_PRODUCT_ID = 10L;
  private static final Long BODY_PRODUCT_ID = 5L;
  private static final Long AUTHENTICATED_CUSTOMER_ID = 42L;
  private static final Long SPOOFED_CUSTOMER_ID = 99L;
  private static final Long EXISTING_REVIEW_ID = 77L;

  @Mock private ProductCommonFacade productCommonFacade;
  @Mock private ProductService productService;
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
    store.setCode("DEFAULT");
    language = new Language();
    language.setCode("en");
    request = new MockHttpServletRequest();
    response = new MockHttpServletResponse();
  }

  @Test
  public void customerCreateWithoutPrincipalReturns401AndDoesNotSave() throws Exception {
    PersistableProductReview result =
        api.createAsCustomer(PATH_PRODUCT_ID, spoofedReview(), store, language, request, response);

    assertNull(result);
    assertEquals(401, response.getStatus());
    verify(customerFacade, never()).getCustomerByUserName(anyString(), any());
    verify(productCommonFacade, never()).saveOrUpdateReview(any(), any(), any());
  }

  @Test
  public void customerCreateForCustomerNotInStoreReturns401AndDoesNotSave() throws Exception {
    request.setUserPrincipal(() -> "alice@test.com");
    when(customerFacade.getCustomerByUserName("alice@test.com", store)).thenReturn(null);

    PersistableProductReview result =
        api.createAsCustomer(PATH_PRODUCT_ID, spoofedReview(), store, language, request, response);

    assertNull(result);
    assertEquals(401, response.getStatus());
    verify(productReviewService, never()).getByProductAndCustomer(anyLong(), anyLong());
    verify(productCommonFacade, never()).saveOrUpdateReview(any(), any(), any());
  }

  @Test
  public void customerCreateWhenCustomerLookupFailsReturns503AndDoesNotSave() throws Exception {
    request.setUserPrincipal(() -> "alice@test.com");
    when(customerFacade.getCustomerByUserName("alice@test.com", store))
        .thenThrow(new RuntimeException("db down"));

    PersistableProductReview result =
        api.createAsCustomer(PATH_PRODUCT_ID, spoofedReview(), store, language, request, response);

    assertNull(result);
    assertEquals(503, response.getStatus());
    verify(productCommonFacade, never()).saveOrUpdateReview(any(), any(), any());
  }

  @Test
  public void customerCreateBindsReviewToAuthenticatedCustomerAndPathProduct() throws Exception {
    authenticatedAs("alice@test.com", AUTHENTICATED_CUSTOMER_ID);
    AtomicReference<PersistableProductReview> saved = captureSavedReview();

    PersistableProductReview review = spoofedReview();
    PersistableProductReview result =
        api.createAsCustomer(PATH_PRODUCT_ID, review, store, language, request, response);

    assertSame(review, result);
    assertEquals(200, response.getStatus());
    verify(productReviewService).getByProductAndCustomer(PATH_PRODUCT_ID, AUTHENTICATED_CUSTOMER_ID);
    assertEquals(AUTHENTICATED_CUSTOMER_ID, saved.get().getCustomerId());
    assertEquals(PATH_PRODUCT_ID, saved.get().getProductId());
    assertNull("body review id must not reach saveOrUpdateReview", saved.get().getId());
  }

  @Test
  public void customerCreateWithoutBodyCustomerIdUsesAuthenticatedCustomer() throws Exception {
    authenticatedAs("alice@test.com", AUTHENTICATED_CUSTOMER_ID);
    AtomicReference<PersistableProductReview> saved = captureSavedReview();

    PersistableProductReview review = review(null, null);
    api.createAsCustomer(PATH_PRODUCT_ID, review, store, language, request, response);

    assertEquals(AUTHENTICATED_CUSTOMER_ID, saved.get().getCustomerId());
    assertEquals(PATH_PRODUCT_ID, saved.get().getProductId());
  }

  @Test
  public void customerCreateRejectsSecondReviewForPathProduct() throws Exception {
    authenticatedAs("alice@test.com", AUTHENTICATED_CUSTOMER_ID);
    when(productReviewService.getByProductAndCustomer(PATH_PRODUCT_ID, AUTHENTICATED_CUSTOMER_ID))
        .thenReturn(new ProductReview());

    PersistableProductReview result =
        api.createAsCustomer(PATH_PRODUCT_ID, spoofedReview(), store, language, request, response);

    assertNull(result);
    assertEquals(500, response.getStatus());
    verify(productCommonFacade, never()).saveOrUpdateReview(any(), any(), any());
  }

  @Test
  public void customerCreateRejectsRatingAboveMaximum() throws Exception {
    authenticatedAs("alice@test.com", AUTHENTICATED_CUSTOMER_ID);
    PersistableProductReview review = spoofedReview();
    review.setRating(6D);

    PersistableProductReview result =
        api.createAsCustomer(PATH_PRODUCT_ID, review, store, language, request, response);

    assertNull(result);
    assertEquals(503, response.getStatus());
    verify(productCommonFacade, never()).saveOrUpdateReview(any(), any(), any());
  }

  @Test
  public void adminCreateKeepsBodyCustomerButUsesPathProductAndClearsId() throws Exception {
    AtomicReference<PersistableProductReview> saved = captureSavedReview();

    PersistableProductReview result =
        api.create(PATH_PRODUCT_ID, spoofedReview(), store, language, request, response);

    assertEquals(200, response.getStatus());
    assertEquals(SPOOFED_CUSTOMER_ID, result.getCustomerId());
    assertEquals(PATH_PRODUCT_ID, result.getProductId());
    verify(customerFacade, never()).getCustomerByUserName(anyString(), any());
    verify(productReviewService).getByProductAndCustomer(PATH_PRODUCT_ID, SPOOFED_CUSTOMER_ID);
    assertEquals(SPOOFED_CUSTOMER_ID, saved.get().getCustomerId());
    assertEquals(PATH_PRODUCT_ID, saved.get().getProductId());
    assertNull(saved.get().getId());
  }

  @Test
  public void adminCreateDuplicateCheckUsesPathProductNotBodyProduct() throws Exception {
    when(productReviewService.getByProductAndCustomer(PATH_PRODUCT_ID, SPOOFED_CUSTOMER_ID))
        .thenReturn(new ProductReview());

    PersistableProductReview result =
        api.create(PATH_PRODUCT_ID, spoofedReview(), store, language, request, response);

    assertNull(result);
    assertEquals(500, response.getStatus());
    verify(productReviewService, never()).getByProductAndCustomer(BODY_PRODUCT_ID, SPOOFED_CUSTOMER_ID);
    verify(productCommonFacade, never()).saveOrUpdateReview(any(), any(), any());
  }

  @Test
  public void saveFailureReturns503() throws Exception {
    authenticatedAs("alice@test.com", AUTHENTICATED_CUSTOMER_ID);
    doAnswer(invocation -> {
          throw new Exception("boom");
        })
        .when(productCommonFacade)
        .saveOrUpdateReview(any(), any(), any());

    PersistableProductReview result =
        api.createAsCustomer(PATH_PRODUCT_ID, spoofedReview(), store, language, request, response);

    assertNull(result);
    assertEquals(503, response.getStatus());
  }

  private void authenticatedAs(String userName, Long customerId) {
    request.setUserPrincipal(() -> userName);
    Customer customer = new Customer();
    customer.setId(customerId);
    customer.setNick(userName);
    customer.setMerchantStore(store);
    when(customerFacade.getCustomerByUserName(userName, store)).thenReturn(customer);
  }

  private AtomicReference<PersistableProductReview> captureSavedReview() throws Exception {
    AtomicReference<PersistableProductReview> saved = new AtomicReference<>();
    doAnswer(invocation -> {
          PersistableProductReview r = invocation.getArgument(0);
          PersistableProductReview copy = review(r.getCustomerId(), r.getProductId());
          copy.setId(r.getId());
          saved.set(copy);
          return null;
        })
        .when(productCommonFacade)
        .saveOrUpdateReview(any(PersistableProductReview.class), any(MerchantStore.class), any(Language.class));
    return saved;
  }

  private PersistableProductReview spoofedReview() {
    PersistableProductReview review = review(SPOOFED_CUSTOMER_ID, BODY_PRODUCT_ID);
    review.setId(EXISTING_REVIEW_ID);
    return review;
  }

  private static PersistableProductReview review(Long customerId, Long productId) {
    PersistableProductReview review = new PersistableProductReview();
    review.setCustomerId(customerId);
    review.setProductId(productId);
    review.setLanguage("en");
    review.setRating(4D);
    review.setDescription("unit review");
    return review;
  }
}
