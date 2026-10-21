package com.salesmanager.test.modules;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.CompletableFuture;

import org.junit.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.test.util.ReflectionTestUtils;

import com.paypal.sdk.PaypalServerSdkClient;
import com.paypal.sdk.controllers.OrdersController;
import com.paypal.sdk.controllers.PaymentsController;
import com.paypal.sdk.http.Headers;
import com.paypal.sdk.http.response.ApiResponse;
import com.paypal.sdk.models.AuthorizationStatus;
import com.paypal.sdk.models.AuthorizationWithAdditionalData;
import com.paypal.sdk.models.CaptureAuthorizedPaymentInput;
import com.paypal.sdk.models.CaptureRequest;
import com.paypal.sdk.models.CaptureStatus;
import com.paypal.sdk.models.CapturedPayment;
import com.paypal.sdk.models.OrderAuthorizeResponse;
import com.paypal.sdk.models.OrderStatus;
import com.paypal.sdk.models.OrdersCapture;
import com.paypal.sdk.models.Payer;
import com.paypal.sdk.models.PaymentCollection;
import com.paypal.sdk.models.PurchaseUnit;
import com.paypal.sdk.models.Refund;
import com.paypal.sdk.models.RefundCapturedPaymentInput;
import com.paypal.sdk.models.RefundRequest;
import com.paypal.sdk.models.RefundStatus;
import com.salesmanager.core.business.modules.integration.payment.impl.PayPalExpressCheckoutPayment;
import com.salesmanager.core.business.exception.ServiceException;
import com.salesmanager.core.business.services.catalog.pricing.PricingService;
import com.salesmanager.core.model.merchant.MerchantStore;
import com.salesmanager.core.model.order.Order;
import com.salesmanager.core.model.payments.PaypalPayment;
import com.salesmanager.core.model.payments.PaymentType;
import com.salesmanager.core.model.payments.Transaction;
import com.salesmanager.core.model.payments.TransactionType;
import com.salesmanager.core.model.reference.currency.Currency;
import com.salesmanager.core.model.system.IntegrationConfiguration;
import com.salesmanager.core.modules.integration.IntegrationException;

public class PayPalOrdersV2TransactionTest {

  @Test
  public void authorizesApprovedOrderAndReturnsAuthorizationId() throws Exception {
    OrdersController orders = mock(OrdersController.class);
    PaypalServerSdkClient client = mock(PaypalServerSdkClient.class);
    when(client.getOrdersController()).thenReturn(orders);
    com.paypal.sdk.models.Order approvedOrder = approvedOrder();
    when(orders.getOrderAsync(any())).thenReturn(future(response(approvedOrder)));

    AuthorizationWithAdditionalData authorization = new AuthorizationWithAdditionalData();
    authorization.setId("auth-123");
    authorization.setStatus(AuthorizationStatus.CREATED);
    PurchaseUnit unit = new PurchaseUnit();
    unit.setPayments(new PaymentCollection(Collections.singletonList(authorization),
        Collections.<OrdersCapture>emptyList(), Collections.<Refund>emptyList()));
    OrderAuthorizeResponse authorizeResult = new OrderAuthorizeResponse();
    authorizeResult.setPurchaseUnits(Collections.singletonList(unit));
    when(orders.authorizeOrderAsync(any())).thenReturn(future(response(authorizeResult)));

    PayPalExpressCheckoutPayment module = module(client);
    PaypalPayment payment = new PaypalPayment();
    payment.setPaymentToken("order-123");
    payment.setTransactionType(TransactionType.AUTHORIZE);
    Transaction result = module.authorize(null, null, Collections.emptyList(),
        new BigDecimal("12.00"), payment, configuration(), null);

    assertEquals("auth-123", result.getTransactionDetails().get("TRANSACTIONID"));
    assertEquals("payer-123", result.getTransactionDetails().get("PAYERID"));
    assertEquals("order-123", result.getTransactionDetails().get("TOKEN"));
    assertEquals(PaymentType.PAYPAL, result.getPaymentType());
  }

  @Test
  public void capturesApprovedOrderForAuthorizeAndCapture() throws Exception {
    OrdersController orders = mock(OrdersController.class);
    PaypalServerSdkClient client = mock(PaypalServerSdkClient.class);
    when(client.getOrdersController()).thenReturn(orders);
    when(orders.getOrderAsync(any())).thenReturn(future(response(approvedOrder())));
    OrdersCapture capture = new OrdersCapture();
    capture.setId("capture-123");
    capture.setStatus(CaptureStatus.COMPLETED);
    PurchaseUnit unit = new PurchaseUnit();
    unit.setPayments(new PaymentCollection(Collections.<AuthorizationWithAdditionalData>emptyList(),
        Collections.singletonList(capture), Collections.<Refund>emptyList()));
    com.paypal.sdk.models.Order capturedOrder = new com.paypal.sdk.models.Order();
    capturedOrder.setPurchaseUnits(Collections.singletonList(unit));
    when(orders.captureOrderAsync(any())).thenReturn(future(response(capturedOrder)));

    PaypalPayment payment = new PaypalPayment();
    payment.setPaymentToken("order-123");
    payment.setTransactionType(TransactionType.AUTHORIZECAPTURE);
    Transaction result = module(client).authorizeAndCapture(null, null, Collections.emptyList(),
        new BigDecimal("12.00"), payment, configuration(), null);

    assertEquals("capture-123", result.getTransactionDetails().get("TRANSACTIONID"));
    assertEquals("payer-123", result.getTransactionDetails().get("PAYERID"));
  }

  @Test
  public void capturesAuthorizationWithOrderTotalAndNonFinalFlag() throws Exception {
    PaymentsController payments = mock(PaymentsController.class);
    PaypalServerSdkClient client = mock(PaypalServerSdkClient.class);
    when(client.getPaymentsController()).thenReturn(payments);
    CapturedPayment capture = new CapturedPayment();
    capture.setId("capture-456");
    capture.setStatus(CaptureStatus.COMPLETED);
    when(payments.captureAuthorizedPaymentAsync(any())).thenReturn(future(response(capture)));

    PayPalExpressCheckoutPayment module = module(client);
    injectPricing(module);
    Transaction original = new Transaction();
    original.getTransactionDetails().put("TRANSACTIONID", "auth-123");
    Order order = order();
    Transaction result = module.capture(new MerchantStore(), null, order, original,
        configuration(), null);

    ArgumentCaptor<CaptureAuthorizedPaymentInput> request =
        ArgumentCaptor.forClass(CaptureAuthorizedPaymentInput.class);
    verify(payments).captureAuthorizedPaymentAsync(request.capture());
    CaptureRequest body = request.getValue().getBody();
    assertEquals("auth-123", request.getValue().getAuthorizationId());
    assertEquals("12.00", body.getAmount().getValue());
    assertEquals(Boolean.FALSE, body.getFinalCapture());
    assertEquals("auth-123", result.getTransactionDetails().get("AUTHORIZATIONID"));
    assertEquals("capture-456", result.getTransactionDetails().get("TRANSACTIONID"));
    assertEquals(TransactionType.CAPTURE, result.getTransactionType());
  }

  @Test
  public void fullAndPartialRefundsMapCaptureAndDebugId() throws Exception {
    PaymentsController payments = mock(PaymentsController.class);
    PaypalServerSdkClient client = mock(PaypalServerSdkClient.class);
    when(client.getPaymentsController()).thenReturn(payments);
    Refund refund = new Refund();
    refund.setId("refund-123");
    refund.setStatus(RefundStatus.COMPLETED);
    Headers headers = new Headers();
    headers.add("paypal-debug-id", "debug-refund");
    when(payments.refundCapturedPaymentAsync(any())).thenReturn(
        future(new ApiResponse<Refund>(200, headers, refund)));
    PayPalExpressCheckoutPayment module = module(client);
    injectPricing(module);
    MerchantStore store = new MerchantStore();

    Transaction original = new Transaction();
    original.getTransactionDetails().put("TRANSACTIONID", "capture-123");
    Transaction fullRefund = module.refund(false, store, original, order(),
        new BigDecimal("12.00"), configuration(), null);
    ArgumentCaptor<RefundCapturedPaymentInput> fullRequest =
        ArgumentCaptor.forClass(RefundCapturedPaymentInput.class);
    verify(payments).refundCapturedPaymentAsync(fullRequest.capture());
    assertNull(fullRequest.getValue().getBody());
    assertEquals("refund-123", fullRefund.getTransactionDetails().get("TRANSACTIONID"));
    assertEquals("debug-refund", original.getTransactionDetails().get("CORRELATION"));

    when(payments.refundCapturedPaymentAsync(any())).thenReturn(future(response(refund)));
    Transaction partialOriginal = new Transaction();
    partialOriginal.getTransactionDetails().put("TRANSACTIONID", "capture-456");
    module.refund(true, store, partialOriginal, order(), new BigDecimal("5.00"),
        configuration(), null);
    ArgumentCaptor<RefundCapturedPaymentInput> partialRequest =
        ArgumentCaptor.forClass(RefundCapturedPaymentInput.class);
    verify(payments, org.mockito.Mockito.times(2)).refundCapturedPaymentAsync(
        partialRequest.capture());
    RefundRequest body = partialRequest.getAllValues().get(1).getBody();
    assertEquals("5.00", body.getAmount().getValue());
    assertEquals("USD", body.getAmount().getCurrencyCode());
  }

  @Test
  public void declinesFailedRefunds() throws Exception {
    PaymentsController payments = mock(PaymentsController.class);
    PaypalServerSdkClient client = mock(PaypalServerSdkClient.class);
    when(client.getPaymentsController()).thenReturn(payments);
    Refund failed = new Refund();
    failed.setId("refund-failed");
    failed.setStatus(RefundStatus.FAILED);
    when(payments.refundCapturedPaymentAsync(any())).thenReturn(future(response(failed)));
    Transaction original = new Transaction();
    original.getTransactionDetails().put("TRANSACTIONID", "capture-123");

    try {
      module(client).refund(false, null, original, order(), new BigDecimal("12.00"),
          configuration(), null);
    } catch (IntegrationException e) {
      assertEquals(ServiceException.EXCEPTION_TRANSACTION_DECLINED, e.getErrorCode());
      return;
    }
    throw new AssertionError("Expected PayPal refund to be declined");
  }

  private PayPalExpressCheckoutPayment module(PaypalServerSdkClient client) {
    return new PayPalExpressCheckoutPayment() {
      @Override
      protected PaypalServerSdkClient client(IntegrationConfiguration configuration) {
        return client;
      }
    };
  }

  private void injectPricing(PayPalExpressCheckoutPayment module) throws Exception {
    PricingService pricing = mock(PricingService.class);
    when(pricing.getStringAmount(any(BigDecimal.class), any(MerchantStore.class)))
        .thenAnswer(invocation -> ((BigDecimal) invocation.getArgument(0)).toPlainString());
    ReflectionTestUtils.setField(module, "pricingService", pricing);
  }

  private com.paypal.sdk.models.Order approvedOrder() {
    Payer payer = new Payer();
    payer.setPayerId("payer-123");
    com.paypal.sdk.models.Order order = new com.paypal.sdk.models.Order();
    order.setId("order-123");
    order.setPayer(payer);
    order.setStatus(OrderStatus.APPROVED);
    return order;
  }

  private Order order() {
    Currency currency = new Currency();
    currency.setCurrency(java.util.Currency.getInstance("USD"));
    Order order = new Order();
    order.setCurrency(currency);
    order.setTotal(new BigDecimal("12.00"));
    return order;
  }

  private IntegrationConfiguration configuration() {
    IntegrationConfiguration configuration = new IntegrationConfiguration();
    Map<String, String> keys = new HashMap<String, String>();
    keys.put("clientId", "client");
    keys.put("clientSecret", "secret");
    configuration.setIntegrationKeys(keys);
    return configuration;
  }

  private <T> ApiResponse<T> response(T result) {
    return new ApiResponse<T>(200, new Headers(), result);
  }

  private <T> CompletableFuture<ApiResponse<T>> future(ApiResponse<T> response) {
    return CompletableFuture.completedFuture(response);
  }
}
