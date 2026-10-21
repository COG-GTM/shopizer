package com.salesmanager.test.modules;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;

import org.junit.Test;
import org.springframework.test.util.ReflectionTestUtils;

import com.paypal.sdk.Environment;
import com.paypal.sdk.PaypalServerSdkClient;
import com.paypal.sdk.controllers.OrdersController;
import com.paypal.sdk.controllers.PaymentsController;
import com.paypal.sdk.http.Headers;
import com.paypal.sdk.http.response.ApiResponse;
import com.paypal.sdk.models.AmountBreakdown;
import com.paypal.sdk.models.AuthorizationStatus;
import com.paypal.sdk.models.AuthorizationWithAdditionalData;
import com.paypal.sdk.models.CaptureRequest;
import com.paypal.sdk.models.CaptureStatus;
import com.paypal.sdk.models.CapturedPayment;
import com.paypal.sdk.models.CheckoutPaymentIntent;
import com.paypal.sdk.models.OrderAuthorizeResponse;
import com.paypal.sdk.models.OrderRequest;
import com.paypal.sdk.models.OrderStatus;
import com.paypal.sdk.models.OrdersCapture;
import com.paypal.sdk.models.Payer;
import com.paypal.sdk.models.PaymentCollection;
import com.paypal.sdk.models.PurchaseUnit;
import com.paypal.sdk.models.PurchaseUnitRequest;
import com.paypal.sdk.models.Refund;
import com.paypal.sdk.models.RefundStatus;
import com.salesmanager.core.business.constants.Constants;
import com.salesmanager.core.business.exception.ServiceException;
import com.salesmanager.core.business.modules.integration.payment.impl.PayPalExpressCheckoutPayment;
import com.salesmanager.core.business.services.catalog.pricing.PricingService;
import com.salesmanager.core.model.catalog.product.Product;
import com.salesmanager.core.model.catalog.product.description.ProductDescription;
import com.salesmanager.core.model.catalog.product.price.FinalPrice;
import com.salesmanager.core.model.merchant.MerchantStore;
import com.salesmanager.core.model.order.Order;
import com.salesmanager.core.model.order.OrderTotal;
import com.salesmanager.core.model.order.OrderTotalSummary;
import com.salesmanager.core.model.payments.PaypalPayment;
import com.salesmanager.core.model.payments.Payment;
import com.salesmanager.core.model.payments.PaymentType;
import com.salesmanager.core.model.payments.Transaction;
import com.salesmanager.core.model.payments.TransactionType;
import com.salesmanager.core.model.reference.currency.Currency;
import com.salesmanager.core.model.shoppingcart.ShoppingCartItem;
import com.salesmanager.core.model.system.IntegrationConfiguration;
import com.salesmanager.core.modules.integration.IntegrationException;

public class PayPalOrdersV2EdgeCaseTest {

  @Test
  public void validatesOnlyRestCredentialNames() throws Exception {
    IntegrationConfiguration missingKeys = new IntegrationConfiguration();
    missingKeys.setIntegrationKeys(null);
    assertValidationFields(missingKeys, Arrays.asList("clientId", "clientSecret"));

    IntegrationConfiguration legacy = new IntegrationConfiguration();
    Map<String, String> legacyKeys = new HashMap<String, String>();
    legacyKeys.put("username", "old-user");
    legacyKeys.put("api", "old-api");
    legacyKeys.put("signature", "old-signature");
    legacy.setIntegrationKeys(legacyKeys);
    assertValidationFields(legacy, Arrays.asList("clientId", "clientSecret"));

    IntegrationConfiguration partial = configuration();
    partial.getIntegrationKeys().put("clientSecret", " ");
    assertValidationFields(partial, Collections.singletonList("clientSecret"));
  }

  @Test
  public void mapsPaypalIntentsAndOrderTotals() throws Exception {
    IntegrationConfiguration authorize = configuration();
    authorize.getIntegrationKeys().put("transaction", "AUTHORIZE");
    OrderTotalSummary withTotals = summary("20.00", "26.00");
    withTotals.setTotals(Arrays.asList(total(Constants.OT_SHIPPING_MODULE_CODE, "2.00"),
        total(Constants.OT_HANDLING_MODULE_CODE, "1.00"), total(Constants.OT_TAX_MODULE_CODE, "1.50"),
        total(Constants.OT_TAX_MODULE_CODE, "1.50")));

    OrderInitialization initialized = initializeOrder(Collections.singletonList(cartItem("20.00")),
        withTotals, authorize, "order-authorize", null);
    PurchaseUnitRequest purchaseUnit = initialized.orderRequest.getPurchaseUnits().get(0);
    AmountBreakdown breakdown = purchaseUnit.getAmount().getBreakdown();

    assertEquals(CheckoutPaymentIntent.AUTHORIZE, initialized.orderRequest.getIntent());
    assertEquals("2.00", breakdown.getShipping().getValue());
    assertEquals("1.00", breakdown.getHandling().getValue());
    assertEquals("3.00", breakdown.getTaxTotal().getValue());

    IntegrationConfiguration capture = configuration();
    capture.getIntegrationKeys().put("transaction", "AUTHORIZECAPTURE");
    OrderInitialization captured = initializeOrder(Collections.singletonList(cartItem("20.00")),
        summary("20.00", "20.00"), capture, "order-capture", "debug-123");
    assertEquals(CheckoutPaymentIntent.CAPTURE, captured.orderRequest.getIntent());
    assertNull(captured.orderRequest.getPurchaseUnits().get(0).getAmount().getBreakdown().getDiscount());
    assertEquals(TransactionType.INIT, captured.transaction.getTransactionType());
    assertEquals(PaymentType.PAYPAL, captured.transaction.getPaymentType());
    assertEquals(new BigDecimal("20.00"), captured.transaction.getAmount());
    assertEquals("order-capture", captured.transaction.getTransactionDetails().get("TOKEN"));
    assertEquals("debug-123", captured.transaction.getTransactionDetails().get("CORRELATION"));
  }

  @Test
  public void usesOrderIdForCorrelationWhenDebugHeaderIsAbsent() throws Exception {
    OrderInitialization initialized = initializeOrder(Collections.<ShoppingCartItem>emptyList(),
        summary("0.00", "0.00"), configuration(), "order-fallback", null);
    assertEquals("order-fallback",
        initialized.transaction.getTransactionDetails().get("CORRELATION"));
  }

  @Test
  public void rejectsMissingPaypalOrderIds() throws Exception {
    expectIntegrationException(() -> initializeOrder(Collections.<ShoppingCartItem>emptyList(),
        summary("0.00", "0.00"), configuration(), null, null));
    expectIntegrationException(() -> initializeOrder(Collections.<ShoppingCartItem>emptyList(),
        summary("0.00", "0.00"), configuration(), " ", null));
  }

  @Test
  public void rejectsOrdersWithoutPayerAndDeniedAuthorizations() throws Exception {
    OrdersController orders = mock(OrdersController.class);
    PaypalServerSdkClient client = mock(PaypalServerSdkClient.class);
    when(client.getOrdersController()).thenReturn(orders);
    com.paypal.sdk.models.Order noPayer = new com.paypal.sdk.models.Order();
    noPayer.setId("order-no-payer");
    when(orders.getOrderAsync(any())).thenReturn(future(response(noPayer)));
    expectIntegrationException(() -> payment(client).authorize(null, null, Collections.emptyList(),
        BigDecimal.ONE, paypalPayment("order-no-payer", TransactionType.AUTHORIZE),
        configuration(), null));

    when(orders.getOrderAsync(any())).thenReturn(future(response(approvedOrder())));
    AuthorizationWithAdditionalData denied = new AuthorizationWithAdditionalData();
    denied.setId("authorization-denied");
    denied.setStatus(AuthorizationStatus.DENIED);
    OrderAuthorizeResponse authorizationResponse = new OrderAuthorizeResponse();
    authorizationResponse.setPurchaseUnits(Collections.singletonList(
        purchaseUnit(new PaymentCollection(Collections.singletonList(denied),
            Collections.<OrdersCapture>emptyList(), Collections.<Refund>emptyList()))));
    when(orders.authorizeOrderAsync(any())).thenReturn(future(response(authorizationResponse)));
    expectIntegrationException(() -> payment(client).authorize(null, null, Collections.emptyList(),
        BigDecimal.ONE, paypalPayment("order-123", TransactionType.AUTHORIZE),
        configuration(), null));
  }

  @Test
  public void rejectsDeclinedCapturesAndAcceptsPendingCapture() throws Exception {
    OrdersController orders = mock(OrdersController.class);
    PaypalServerSdkClient orderClient = mock(PaypalServerSdkClient.class);
    when(orderClient.getOrdersController()).thenReturn(orders);
    when(orders.getOrderAsync(any())).thenReturn(future(response(approvedOrder())));

    OrdersCapture declined = new OrdersCapture();
    declined.setId("capture-declined");
    declined.setStatus(CaptureStatus.DECLINED);
    when(orders.captureOrderAsync(any())).thenReturn(future(response(orderWithCapture(declined))));
    expectIntegrationException(() -> payment(orderClient).authorizeAndCapture(null, null,
        Collections.emptyList(), BigDecimal.ONE,
        paypalPayment("order-123", TransactionType.AUTHORIZECAPTURE), configuration(), null));

    OrdersCapture pending = new OrdersCapture();
    pending.setId("capture-pending");
    pending.setStatus(CaptureStatus.PENDING);
    when(orders.captureOrderAsync(any())).thenReturn(future(response(orderWithCapture(pending))));
    Transaction captured = payment(orderClient).authorizeAndCapture(null, null,
        Collections.emptyList(), BigDecimal.ONE,
        paypalPayment("order-123", TransactionType.AUTHORIZECAPTURE), configuration(), null);
    assertEquals("capture-pending", captured.getTransactionDetails().get("TRANSACTIONID"));
  }

  @Test
  public void rejectsNullPaymentTokens() throws Exception {
    try {
      payment(mock(PaypalServerSdkClient.class)).authorizeAndCapture(null, null,
          Collections.emptyList(), BigDecimal.ONE,
          paypalPayment(null, TransactionType.AUTHORIZECAPTURE), configuration(), null);
      fail("Expected null token to be rejected");
    } catch (NullPointerException expected) {
      assertTrue(expected.getMessage().contains("token"));
    }
  }

  @Test
  public void rejectsDeclinedCapturesAndMissingAuthorizationIds() throws Exception {
    PaymentsController payments = mock(PaymentsController.class);
    PaypalServerSdkClient client = mock(PaypalServerSdkClient.class);
    when(client.getPaymentsController()).thenReturn(payments);
    CapturedPayment declined = new CapturedPayment();
    declined.setId("capture-declined");
    declined.setStatus(CaptureStatus.DECLINED);
    when(payments.captureAuthorizedPaymentAsync(any())).thenReturn(future(response(declined)));
    PayPalExpressCheckoutPayment module = payment(client);
    injectPricing(module);
    expectIntegrationException(() -> module.capture(new MerchantStore(), null, order(),
        transaction("authorization-123"), configuration(), null));
    expectIntegrationException(() -> module.capture(new MerchantStore(), null, order(),
        new Transaction(), configuration(), null));
  }

  @Test
  public void treatsPendingRefundAsBookedForNvpParityAndRejectsCancelledRefund() throws Exception {
    PaymentsController payments = mock(PaymentsController.class);
    PaypalServerSdkClient client = mock(PaypalServerSdkClient.class);
    when(client.getPaymentsController()).thenReturn(payments);
    PayPalExpressCheckoutPayment module = payment(client);
    Refund pending = new Refund();
    pending.setId("refund-pending");
    pending.setStatus(RefundStatus.PENDING);
    when(payments.refundCapturedPaymentAsync(any())).thenReturn(future(response(pending)));

    Transaction refund = module.refund(false, new MerchantStore(), transaction("capture-123"),
        order(), BigDecimal.ONE, configuration(), null);
    assertEquals(TransactionType.REFUND, refund.getTransactionType());
    assertEquals("refund-pending", refund.getTransactionDetails().get("TRANSACTIONID"));

    Refund cancelled = new Refund();
    cancelled.setId("refund-cancelled");
    cancelled.setStatus(RefundStatus.CANCELLED);
    when(payments.refundCapturedPaymentAsync(any())).thenReturn(future(response(cancelled)));
    try {
      module.refund(false, new MerchantStore(), transaction("capture-123"),
          order(), BigDecimal.ONE, configuration(), null);
      fail("Expected cancelled refund to be declined");
    } catch (IntegrationException expected) {
      assertEquals(ServiceException.EXCEPTION_TRANSACTION_DECLINED, expected.getErrorCode());
    }
  }

  @Test
  public void createsClientsForProductionAndTestEnvironments() {
    ExposedPayment module = new ExposedPayment();
    IntegrationConfiguration configuration = configuration();
    configuration.setEnvironment(Constants.PRODUCTION_ENVIRONMENT);
    assertEquals(Environment.PRODUCTION, module.createClient(configuration).getEnvironment());

    configuration.setEnvironment(Constants.TEST_ENVIRONMENT);
    assertEquals(Environment.SANDBOX, module.createClient(configuration).getEnvironment());
  }

  private void assertValidationFields(IntegrationConfiguration configuration, List<String> fields)
      throws Exception {
    try {
      new PayPalExpressCheckoutPayment().validateModuleConfiguration(configuration, null);
      fail("Expected PayPal configuration validation to fail");
    } catch (IntegrationException expected) {
      assertEquals(IntegrationException.ERROR_VALIDATION_SAVE, expected.getErrorCode());
      assertEquals(fields, expected.getErrorFields());
    }
  }

  private void expectIntegrationException(IntegrationAction action) throws Exception {
    try {
      action.run();
      fail("Expected IntegrationException");
    } catch (IntegrationException expected) {
      assertEquals(IntegrationException.class, expected.getClass());
    }
  }

  private OrderInitialization initializeOrder(List<ShoppingCartItem> items, OrderTotalSummary summary,
      IntegrationConfiguration configuration, String orderId, String debugId) throws Exception {
    OrdersController orders = mock(OrdersController.class);
    PaypalServerSdkClient client = mock(PaypalServerSdkClient.class);
    when(client.getOrdersController()).thenReturn(orders);
    com.paypal.sdk.models.Order createdOrder = new com.paypal.sdk.models.Order();
    createdOrder.setId(orderId);
    Headers headers = new Headers();
    if (debugId != null) {
      headers.add("paypal-debug-id", debugId);
    }
    when(orders.createOrderAsync(any())).thenReturn(future(
        new ApiResponse<com.paypal.sdk.models.Order>(201, headers, createdOrder)));
    PayPalExpressCheckoutPayment module = payment(client);
    injectPricing(module);
    Transaction transaction = module.initPaypalTransaction(store(), items, summary, new Payment(),
        configuration, null);
    org.mockito.ArgumentCaptor<com.paypal.sdk.models.CreateOrderInput> input =
        org.mockito.ArgumentCaptor.forClass(com.paypal.sdk.models.CreateOrderInput.class);
    org.mockito.Mockito.verify(orders).createOrderAsync(input.capture());
    return new OrderInitialization(input.getValue().getBody(), transaction);
  }

  private void injectPricing(PayPalExpressCheckoutPayment module) throws Exception {
    PricingService pricing = mock(PricingService.class);
    when(pricing.getStringAmount(any(BigDecimal.class), any(MerchantStore.class)))
        .thenAnswer(invocation -> ((BigDecimal) invocation.getArgument(0)).toPlainString());
    ReflectionTestUtils.setField(module, "pricingService", pricing);
  }

  private PayPalExpressCheckoutPayment payment(PaypalServerSdkClient client) {
    return new PayPalExpressCheckoutPayment() {
      @Override
      protected PaypalServerSdkClient client(IntegrationConfiguration configuration) {
        return client;
      }
    };
  }

  private IntegrationConfiguration configuration() {
    IntegrationConfiguration configuration = new IntegrationConfiguration();
    Map<String, String> keys = new HashMap<String, String>();
    keys.put("clientId", "client");
    keys.put("clientSecret", "secret");
    configuration.setIntegrationKeys(keys);
    return configuration;
  }

  private MerchantStore store() {
    Currency currency = new Currency();
    currency.setCurrency(java.util.Currency.getInstance("USD"));
    MerchantStore store = new MerchantStore();
    store.setCurrency(currency);
    return store;
  }

  private OrderTotalSummary summary(String subtotal, String total) {
    OrderTotalSummary summary = new OrderTotalSummary();
    summary.setSubTotal(new BigDecimal(subtotal));
    summary.setTotal(new BigDecimal(total));
    return summary;
  }

  private OrderTotal total(String module, String value) {
    OrderTotal total = new OrderTotal();
    total.setModule(module);
    total.setValue(new BigDecimal(value));
    return total;
  }

  private ShoppingCartItem cartItem(String amount) {
    ShoppingCartItem item = mock(ShoppingCartItem.class);
    FinalPrice price = new FinalPrice();
    price.setFinalPrice(new BigDecimal(amount));
    when(item.getFinalPrice()).thenReturn(price);
    when(item.getQuantity()).thenReturn(1);
    Product product = mock(Product.class);
    ProductDescription description = mock(ProductDescription.class);
    when(description.getName()).thenReturn("sample");
    when(product.getProductDescription()).thenReturn(description);
    when(item.getProduct()).thenReturn(product);
    return item;
  }

  private PaypalPayment paypalPayment(String token, TransactionType type) {
    PaypalPayment payment = new PaypalPayment();
    payment.setPaymentToken(token);
    payment.setTransactionType(type);
    return payment;
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

  private com.paypal.sdk.models.Order orderWithCapture(OrdersCapture capture) {
    com.paypal.sdk.models.Order order = new com.paypal.sdk.models.Order();
    order.setId("order-123");
    order.setPayer(approvedOrder().getPayer());
    order.setPurchaseUnits(Collections.singletonList(purchaseUnit(new PaymentCollection(
        Collections.<AuthorizationWithAdditionalData>emptyList(), Collections.singletonList(capture),
        Collections.<Refund>emptyList()))));
    return order;
  }

  private PurchaseUnit purchaseUnit(PaymentCollection payments) {
    PurchaseUnit unit = new PurchaseUnit();
    unit.setPayments(payments);
    return unit;
  }

  private Transaction transaction(String id) {
    Transaction transaction = new Transaction();
    transaction.getTransactionDetails().put("TRANSACTIONID", id);
    return transaction;
  }

  private Order order() {
    Order order = new Order();
    order.setCurrency(store().getCurrency());
    order.setTotal(new BigDecimal("12.00"));
    return order;
  }

  private <T> ApiResponse<T> response(T result) {
    return new ApiResponse<T>(200, new Headers(), result);
  }

  private <T> CompletableFuture<ApiResponse<T>> future(ApiResponse<T> response) {
    return CompletableFuture.completedFuture(response);
  }

  private static class ExposedPayment extends PayPalExpressCheckoutPayment {
    PaypalServerSdkClient createClient(IntegrationConfiguration configuration) {
      return client(configuration);
    }
  }

  private static class OrderInitialization {
    private final OrderRequest orderRequest;
    private final Transaction transaction;

    private OrderInitialization(OrderRequest orderRequest, Transaction transaction) {
      this.orderRequest = orderRequest;
      this.transaction = transaction;
    }
  }

  private interface IntegrationAction {
    void run() throws Exception;
  }
}
