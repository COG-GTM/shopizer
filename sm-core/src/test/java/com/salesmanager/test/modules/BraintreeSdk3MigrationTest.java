package com.salesmanager.test.modules;

import static org.junit.Assert.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

import org.junit.Test;

import com.braintreegateway.BraintreeGateway;
import com.braintreegateway.ClientTokenGateway;
import com.braintreegateway.Result;
import com.braintreegateway.TransactionGateway;
import com.braintreegateway.TransactionRequest;
import com.salesmanager.core.business.modules.integration.payment.impl.BraintreePayment;
import com.salesmanager.core.model.merchant.MerchantStore;
import com.salesmanager.core.model.order.Order;
import com.salesmanager.core.model.payments.Payment;
import com.salesmanager.core.model.payments.PaymentType;
import com.salesmanager.core.model.payments.Transaction;
import com.salesmanager.core.model.payments.TransactionType;
import com.salesmanager.core.model.system.IntegrationConfiguration;

public class BraintreeSdk3MigrationTest {

  @Test
  public void initializesTransactionUsingInjectedGateway() throws Exception {
    BraintreeGateway gateway = mock(BraintreeGateway.class);
    ClientTokenGateway clientTokens = mock(ClientTokenGateway.class);
    when(gateway.clientToken()).thenReturn(clientTokens);
    when(clientTokens.generate()).thenReturn("client-token");
    BraintreePayment paymentModule = new BraintreePayment() {
      @Override
      protected BraintreeGateway gateway(IntegrationConfiguration configuration) {
        return gateway;
      }
    };
    IntegrationConfiguration configuration = new IntegrationConfiguration();
    Map<String, String> keys = new HashMap<String, String>();
    keys.put("merchant_id", "merchant");
    keys.put("public_key", "public");
    keys.put("private_key", "private");
    configuration.setIntegrationKeys(keys);
    Payment payment = new Payment();
    payment.setTransactionType(TransactionType.AUTHORIZE);

    Transaction transaction = paymentModule.initTransaction(null, null, new BigDecimal("10.00"),
        payment, configuration, null);

    assertEquals(TransactionType.AUTHORIZE, transaction.getTransactionType());
    assertEquals("client-token", transaction.getDetails());
  }

  @Test
  @SuppressWarnings("unchecked")
  public void authorizesCapturesAndRefundsThroughInjectedGateway() throws Exception {
    BraintreeGateway gateway = mock(BraintreeGateway.class);
    TransactionGateway transactions = mock(TransactionGateway.class);
    when(gateway.transaction()).thenReturn(transactions);
    Result<com.braintreegateway.Transaction> result = mock(Result.class);
    when(result.isSuccess()).thenReturn(true);
    com.braintreegateway.Transaction sdkTransaction = mock(com.braintreegateway.Transaction.class);
    when(sdkTransaction.getId()).thenReturn("bt-123");
    when(result.getTarget()).thenReturn(sdkTransaction);
    when(transactions.sale(any(TransactionRequest.class))).thenReturn(result);
    when(transactions.submitForSettlement(anyString(), any(BigDecimal.class))).thenReturn(result);
    when(transactions.refund(anyString(), any(BigDecimal.class))).thenReturn(result);
    BraintreePayment paymentModule = new BraintreePayment() {
      @Override
      protected BraintreeGateway gateway(IntegrationConfiguration configuration) {
        return gateway;
      }
    };
    IntegrationConfiguration configuration = configuration();
    MerchantStore store = new MerchantStore();
    BigDecimal amount = new BigDecimal("10.00");
    Payment payment = new Payment();
    payment.setPaymentType(PaymentType.CREDITCARD);
    payment.setPaymentMetaData(Collections.singletonMap("paymentToken", "nonce-123"));

    Transaction authorized = paymentModule.authorize(store, null, Collections.emptyList(), amount,
        payment, configuration, null);
    assertEquals(TransactionType.AUTHORIZE, authorized.getTransactionType());
    assertEquals("bt-123", authorized.getTransactionDetails().get("TRANSACTIONID"));

    Transaction capturable = new Transaction();
    capturable.getTransactionDetails().put("TRANSACTIONID", "bt-123");
    Order order = new Order();
    order.setTotal(amount);
    Transaction captured = paymentModule.capture(store, null, order, capturable,
        configuration, null);
    assertEquals(TransactionType.CAPTURE, captured.getTransactionType());
    assertEquals("bt-123", captured.getTransactionDetails().get("TRANSACTIONID"));

    Transaction refunded = paymentModule.refund(true, store, capturable, order, amount,
        configuration, null);
    assertEquals(TransactionType.REFUND, refunded.getTransactionType());
    assertEquals("bt-123", refunded.getTransactionDetails().get("TRANSACTIONID"));
  }

  private IntegrationConfiguration configuration() {
    IntegrationConfiguration configuration = new IntegrationConfiguration();
    Map<String, String> keys = new HashMap<String, String>();
    keys.put("merchant_id", "merchant");
    keys.put("public_key", "public");
    keys.put("private_key", "private");
    configuration.setIntegrationKeys(keys);
    return configuration;
  }
}
