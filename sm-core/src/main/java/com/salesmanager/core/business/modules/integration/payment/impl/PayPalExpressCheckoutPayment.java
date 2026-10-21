package com.salesmanager.core.business.modules.integration.payment.impl;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Map;

import javax.inject.Inject;

import org.apache.commons.lang3.StringUtils;
import org.apache.commons.lang3.Validate;

import com.paypal.sdk.Environment;
import com.paypal.sdk.PaypalServerSdkClient;
import com.paypal.sdk.authentication.ClientCredentialsAuthModel;
import com.paypal.sdk.http.response.ApiResponse;
import com.paypal.sdk.models.AmountBreakdown;
import com.paypal.sdk.models.AmountWithBreakdown;
import com.paypal.sdk.models.AuthorizeOrderInput;
import com.paypal.sdk.models.AuthorizationWithAdditionalData;
import com.paypal.sdk.models.CaptureAuthorizedPaymentInput;
import com.paypal.sdk.models.CaptureOrderInput;
import com.paypal.sdk.models.CaptureRequest;
import com.paypal.sdk.models.CapturedPayment;
import com.paypal.sdk.models.CheckoutPaymentIntent;
import com.paypal.sdk.models.CreateOrderInput;
import com.paypal.sdk.models.GetOrderInput;
import com.paypal.sdk.models.ItemRequest;
import com.paypal.sdk.models.Money;
import com.paypal.sdk.models.OrderAuthorizeResponse;
import com.paypal.sdk.models.OrderRequest;
import com.paypal.sdk.models.OrdersCapture;
import com.paypal.sdk.models.PaymentCollection;
import com.paypal.sdk.models.PurchaseUnit;
import com.paypal.sdk.models.PurchaseUnitRequest;
import com.paypal.sdk.models.Refund;
import com.paypal.sdk.models.RefundCapturedPaymentInput;
import com.paypal.sdk.models.RefundRequest;
import com.salesmanager.core.business.constants.Constants;
import com.salesmanager.core.business.exception.ServiceException;
import com.salesmanager.core.business.services.catalog.pricing.PricingService;
import com.salesmanager.core.model.customer.Customer;
import com.salesmanager.core.model.merchant.MerchantStore;
import com.salesmanager.core.model.order.OrderTotal;
import com.salesmanager.core.model.order.OrderTotalSummary;
import com.salesmanager.core.model.order.Order;
import com.salesmanager.core.model.payments.Payment;
import com.salesmanager.core.model.payments.PaymentType;
import com.salesmanager.core.model.payments.Transaction;
import com.salesmanager.core.model.payments.TransactionType;
import com.salesmanager.core.model.shoppingcart.ShoppingCartItem;
import com.salesmanager.core.model.system.IntegrationConfiguration;
import com.salesmanager.core.model.system.IntegrationModule;
import com.salesmanager.core.modules.integration.IntegrationException;
import com.salesmanager.core.modules.integration.payment.model.PaymentModule;

public class PayPalExpressCheckoutPayment implements PaymentModule {

  @Inject
  private PricingService pricingService;

  @Override
  public void validateModuleConfiguration(IntegrationConfiguration integrationConfiguration,
      MerchantStore store) throws IntegrationException {
    Map<String, String> keys = integrationConfiguration.getIntegrationKeys();
    List<String> errorFields = null;
    if (keys == null || StringUtils.isBlank(keys.get("clientId"))) {
      errorFields = new ArrayList<String>();
      errorFields.add("clientId");
    }
    if (keys == null || StringUtils.isBlank(keys.get("clientSecret"))) {
      if (errorFields == null) {
        errorFields = new ArrayList<String>();
      }
      errorFields.add("clientSecret");
    }
    if (errorFields != null) {
      IntegrationException ex = new IntegrationException(IntegrationException.ERROR_VALIDATION_SAVE);
      ex.setErrorFields(errorFields);
      throw ex;
    }
  }

  @Override
  public Transaction initTransaction(MerchantStore store, Customer customer, BigDecimal amount,
      Payment payment, IntegrationConfiguration configuration, IntegrationModule module)
      throws IntegrationException {
    throw new IntegrationException("Not imlemented");
  }

  @Override
  public Transaction authorize(MerchantStore store, Customer customer, List<ShoppingCartItem> items,
      BigDecimal amount, Payment payment, IntegrationConfiguration configuration,
      IntegrationModule module) throws IntegrationException {
    com.salesmanager.core.model.payments.PaypalPayment paypalPayment =
        (com.salesmanager.core.model.payments.PaypalPayment) payment;
    Validate.notNull(paypalPayment.getPaymentToken(),
        "A paypal payment token is required to process this transaction");
    return processTransaction(store, customer, items, amount, paypalPayment, configuration, module);
  }

  public Transaction initPaypalTransaction(MerchantStore store, List<ShoppingCartItem> items,
      OrderTotalSummary summary, Payment payment, IntegrationConfiguration configuration,
      IntegrationModule module) throws IntegrationException {
    Validate.notNull(configuration, "Configuration must not be null");
    Validate.notNull(payment, "Payment must not be null");
    Validate.notNull(summary, "OrderTotalSummary must not be null");

    try {
      String currency = store.getCurrency().getCode();
      CheckoutPaymentIntent intent = TransactionType.AUTHORIZECAPTURE.name()
          .equalsIgnoreCase(configuration.getIntegrationKeys().get("transaction"))
              ? CheckoutPaymentIntent.CAPTURE : CheckoutPaymentIntent.AUTHORIZE;

      List<ItemRequest> orderItems = new ArrayList<ItemRequest>();
      for (ShoppingCartItem cartItem : items) {
        orderItems.add(new ItemRequest.Builder(
            cartItem.getProduct().getProductDescription().getName(),
            money(currency, cartItem.getFinalPrice().getFinalPrice(), store),
            String.valueOf(cartItem.getQuantity())).build());
      }

      AmountBreakdown.Builder breakdown = new AmountBreakdown.Builder()
          .itemTotal(money(currency, summary.getSubTotal(), store));
      BigDecimal tax = null;
      if (summary.getTotals() != null) {
        for (OrderTotal total : summary.getTotals()) {
          if (Constants.OT_SHIPPING_MODULE_CODE.equals(total.getModule())) {
            breakdown.shipping(money(currency, total.getValue(), store));
          } else if (Constants.OT_HANDLING_MODULE_CODE.equals(total.getModule())) {
            breakdown.handling(money(currency, total.getValue(), store));
          } else if (Constants.OT_TAX_MODULE_CODE.equals(total.getModule())) {
            tax = tax == null ? total.getValue() : tax.add(total.getValue());
          }
        }
      }
      if (tax != null) {
        breakdown.taxTotal(money(currency, tax, store));
      }

      AmountWithBreakdown orderAmount = new AmountWithBreakdown.Builder(currency,
          pricingService.getStringAmount(summary.getTotal(), store))
              .breakdown(breakdown.build()).build();
      PurchaseUnitRequest purchaseUnit = new PurchaseUnitRequest.Builder(orderAmount)
          .items(orderItems).build();
      OrderRequest orderRequest = new OrderRequest.Builder(intent,
          java.util.Collections.singletonList(purchaseUnit)).build();
      CreateOrderInput input = new CreateOrderInput.Builder("application/json", orderRequest).build();

      ApiResponse<com.paypal.sdk.models.Order> response = client(configuration).getOrdersController()
          .createOrderAsync(input).get();
      com.paypal.sdk.models.Order createdOrder = response.getResult();
      if (createdOrder == null || StringUtils.isBlank(createdOrder.getId())) {
        throw new IntegrationException("PayPal did not return an order ID");
      }

      Transaction transaction = new Transaction();
      transaction.setAmount(summary.getTotal());
      transaction.setTransactionDate(new Date());
      transaction.setTransactionType(TransactionType.INIT);
      transaction.setPaymentType(PaymentType.PAYPAL);
      transaction.getTransactionDetails().put("TOKEN", createdOrder.getId());
      transaction.getTransactionDetails().put("CORRELATION",
          correlation(response, createdOrder.getId()));
      return transaction;
    } catch (Exception e) {
      if (e instanceof IntegrationException) {
        throw (IntegrationException) e;
      }
      throw new IntegrationException(e);
    }
  }

  @Override
  public Transaction authorizeAndCapture(MerchantStore store, Customer customer,
      List<ShoppingCartItem> items, BigDecimal amount, Payment payment,
      IntegrationConfiguration configuration, IntegrationModule module) throws IntegrationException {
    com.salesmanager.core.model.payments.PaypalPayment paypalPayment =
        (com.salesmanager.core.model.payments.PaypalPayment) payment;
    Validate.notNull(paypalPayment.getPaymentToken(),
        "A paypal payment token is required to process this transaction");
    return processTransaction(store, customer, items, amount, paypalPayment, configuration, module);
  }

  @Override
  public Transaction capture(MerchantStore store, Customer customer, Order order,
      Transaction capturableTransaction, IntegrationConfiguration configuration,
      IntegrationModule module) throws IntegrationException {
    try {
      Validate.notNull(capturableTransaction, "Transaction cannot be null");
      Validate.notNull(capturableTransaction.getTransactionDetails().get("TRANSACTIONID"),
          "Transaction details must contain a TRANSACTIONID");
      Validate.notNull(order, "Order must not be null");
      Validate.notNull(order.getCurrency(), "Order nust contain Currency object");

      String authorizationId = capturableTransaction.getTransactionDetails().get("TRANSACTIONID");
      ApiResponse<CapturedPayment> response = client(configuration).getPaymentsController()
          .captureAuthorizedPaymentAsync(new CaptureAuthorizedPaymentInput.Builder(
              authorizationId, "application/json")
                  .body(new CaptureRequest.Builder()
                      .amount(money(order.getCurrency().getCode(), order.getTotal(), store))
                      .finalCapture(false).build())
                  .build()).get();
      CapturedPayment capture = response.getResult();
      String status = status(capture == null ? null : capture.getStatus());
      if (!"COMPLETED".equals(status) && !"PENDING".equals(status)) {
        throw new IntegrationException("PayPal capture returned status " + status);
      }

      Transaction newTransaction = new Transaction();
      newTransaction.setAmount(order.getTotal());
      newTransaction.setTransactionDate(new Date());
      newTransaction.setTransactionType(TransactionType.CAPTURE);
      newTransaction.setPaymentType(PaymentType.PAYPAL);
      newTransaction.getTransactionDetails().put("AUTHORIZATIONID", authorizationId);
      newTransaction.getTransactionDetails().put("TRANSACTIONID", capture.getId());
      return newTransaction;
    } catch (Exception e) {
      throw new IntegrationException(e);
    }
  }

  @Override
  public Transaction refund(boolean partial, MerchantStore store, Transaction transaction, Order order,
      BigDecimal amount, IntegrationConfiguration configuration, IntegrationModule module)
      throws IntegrationException {
    try {
      Validate.notNull(transaction, "Transaction cannot be null");
      Validate.notNull(transaction.getTransactionDetails().get("TRANSACTIONID"),
          "Transaction details must contain a TRANSACTIONID");
      Validate.notNull(order, "Order must not be null");
      Validate.notNull(order.getCurrency(), "Order nust contain Currency object");

      RefundCapturedPaymentInput.Builder request = new RefundCapturedPaymentInput.Builder(
          transaction.getTransactionDetails().get("TRANSACTIONID"), "application/json");
      if (partial) {
        request.body(new RefundRequest.Builder()
            .amount(money(order.getCurrency().getCode(), amount, store)).build());
      }
      ApiResponse<Refund> response = client(configuration).getPaymentsController()
          .refundCapturedPaymentAsync(request.build()).get();
      Refund refund = response.getResult();
      String status = status(refund == null ? null : refund.getStatus());
      if ("FAILED".equals(status) || "CANCELLED".equals(status)) {
        throw new IntegrationException(ServiceException.EXCEPTION_TRANSACTION_DECLINED,
            "Paypal refund returned status " + status);
      }

      Transaction newTransaction = new Transaction();
      newTransaction.setAmount(amount);
      newTransaction.setTransactionDate(new Date());
      newTransaction.setTransactionType(TransactionType.REFUND);
      newTransaction.setPaymentType(PaymentType.PAYPAL);
      newTransaction.getTransactionDetails().put("TRANSACTIONID", refund.getId());
      transaction.getTransactionDetails().put("CORRELATION",
          correlation(response, transaction.getTransactionDetails().get("TRANSACTIONID")));
      return newTransaction;
    } catch (Exception e) {
      if (e instanceof IntegrationException) {
        throw (IntegrationException) e;
      }
      throw new IntegrationException(e);
    }
  }

  private Transaction processTransaction(MerchantStore store, Customer customer,
      List<ShoppingCartItem> items, BigDecimal amount, Payment payment,
      IntegrationConfiguration configuration, IntegrationModule module) throws IntegrationException {
    com.salesmanager.core.model.payments.PaypalPayment paypalPayment =
        (com.salesmanager.core.model.payments.PaypalPayment) payment;
    try {
      String orderId = paypalPayment.getPaymentToken();
      ApiResponse<com.paypal.sdk.models.Order> orderResponse = client(configuration).getOrdersController()
          .getOrderAsync(new GetOrderInput.Builder(orderId).build()).get();
      com.paypal.sdk.models.Order order = orderResponse.getResult();
      if (order == null || order.getPayer() == null) {
        throw new IntegrationException("PayPal order did not include payer details");
      }

      String transactionId;
      ApiResponse<?> transactionResponse;
      String transactionStatus;
      if (payment.getTransactionType() == TransactionType.AUTHORIZE) {
        ApiResponse<OrderAuthorizeResponse> response = client(configuration).getOrdersController()
            .authorizeOrderAsync(new AuthorizeOrderInput.Builder(orderId, "application/json").build())
            .get();
        OrderAuthorizeResponse authorizedOrder = response.getResult();
        AuthorizationWithAdditionalData authorization = firstAuthorization(
            authorizedOrder == null ? null : authorizedOrder.getPurchaseUnits());
        transactionStatus = status(authorization == null ? null : authorization.getStatus());
        if (!"CREATED".equals(transactionStatus)) {
          throw new IntegrationException("PayPal authorization returned status " + transactionStatus);
        }
        transactionId = authorization.getId();
        transactionResponse = response;
      } else {
        ApiResponse<com.paypal.sdk.models.Order> response = client(configuration).getOrdersController()
            .captureOrderAsync(new CaptureOrderInput.Builder(orderId, "application/json").build()).get();
        com.paypal.sdk.models.Order capturedOrder = response.getResult();
        OrdersCapture capture = firstCapture(
            capturedOrder == null ? null : capturedOrder.getPurchaseUnits());
        transactionStatus = status(capture == null ? null : capture.getStatus());
        if (!"COMPLETED".equals(transactionStatus) && !"PENDING".equals(transactionStatus)) {
          throw new IntegrationException("PayPal capture returned status " + transactionStatus);
        }
        transactionId = capture.getId();
        transactionResponse = response;
      }

      Transaction transaction = new Transaction();
      transaction.setAmount(amount);
      transaction.setTransactionDate(new Date());
      transaction.setTransactionType(payment.getTransactionType());
      transaction.setPaymentType(PaymentType.PAYPAL);
      transaction.getTransactionDetails().put("TOKEN", orderId);
      transaction.getTransactionDetails().put("PAYERID", order.getPayer().getPayerId());
      transaction.getTransactionDetails().put("TRANSACTIONID", transactionId);
      transaction.getTransactionDetails().put("CORRELATION", correlation(transactionResponse, orderId));
      return transaction;
    } catch (Exception e) {
      if (e instanceof IntegrationException) {
        throw (IntegrationException) e;
      }
      throw new IntegrationException(e);
    }
  }

  protected PaypalServerSdkClient client(IntegrationConfiguration configuration) {
    Map<String, String> keys = configuration.getIntegrationKeys();
    Environment environment = Constants.PRODUCTION_ENVIRONMENT.equals(configuration.getEnvironment())
        ? Environment.PRODUCTION : Environment.SANDBOX;
    return new PaypalServerSdkClient.Builder()
        .clientCredentialsAuth(new ClientCredentialsAuthModel.Builder(
            keys.get("clientId"), keys.get("clientSecret")).build())
        .environment(environment)
        .build();
  }

  private Money money(String currency, BigDecimal amount, MerchantStore store)
      throws ServiceException {
    return new Money.Builder(currency, pricingService.getStringAmount(amount, store)).build();
  }

  private String correlation(ApiResponse<?> response, String fallback) {
    String paypalDebugId = response.getHeaders().value("paypal-debug-id");
    return StringUtils.isBlank(paypalDebugId) ? fallback : paypalDebugId;
  }

  private String status(Object status) {
    return status == null ? null : status.toString();
  }

  private AuthorizationWithAdditionalData firstAuthorization(List<PurchaseUnit> purchaseUnits) {
    if (purchaseUnits == null) {
      return null;
    }
    for (PurchaseUnit purchaseUnit : purchaseUnits) {
      PaymentCollection payments = purchaseUnit.getPayments();
      if (payments != null && payments.getAuthorizations() != null
          && !payments.getAuthorizations().isEmpty()) {
        return payments.getAuthorizations().get(0);
      }
    }
    return null;
  }

  private OrdersCapture firstCapture(List<PurchaseUnit> purchaseUnits) {
    if (purchaseUnits == null) {
      return null;
    }
    for (PurchaseUnit purchaseUnit : purchaseUnits) {
      PaymentCollection payments = purchaseUnit.getPayments();
      if (payments != null && payments.getCaptures() != null && !payments.getCaptures().isEmpty()) {
        return payments.getCaptures().get(0);
      }
    }
    return null;
  }
}
