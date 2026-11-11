package com.salesmanager.core.business.modules.integration.payment.impl;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import java.math.BigDecimal;
import java.util.Collections;
import java.util.HashMap;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import com.braintreegateway.BraintreeGateway;
import com.braintreegateway.ClientTokenGateway;
import com.braintreegateway.Result;
import com.braintreegateway.TransactionGateway;
import com.braintreegateway.TransactionRequest;
import com.braintreegateway.ValidationErrors;
import com.salesmanager.core.model.merchant.MerchantStore;
import com.salesmanager.core.model.order.Order;
import com.salesmanager.core.model.payments.Payment;
import com.salesmanager.core.model.payments.PaymentType;
import com.salesmanager.core.model.payments.Transaction;
import com.salesmanager.core.model.payments.TransactionType;
import com.salesmanager.core.model.system.IntegrationConfiguration;
import com.salesmanager.core.modules.integration.IntegrationException;

class BraintreePaymentTest {
    private final BraintreePayment module = new BraintreePayment();
    private final BraintreeGateway client = mock(BraintreeGateway.class);
    private final TransactionGateway transactions = mock(TransactionGateway.class);
    private final MerchantStore store = new MerchantStore();
    private final Payment payment = new Payment();
    private final IntegrationConfiguration config = new IntegrationConfiguration();
    private final Order order = new Order();
    private final BigDecimal amount = new BigDecimal("12.34");
    private final Transaction original = new Transaction();

    @BeforeEach
    void setUp() {
        module.setGateway(client);
        when(client.transaction()).thenReturn(transactions);
        config.setEnvironment("TEST");
        config.setIntegrationKeys(new HashMap<>());
        config.getIntegrationKeys().put("merchant_id", "merchant");
        config.getIntegrationKeys().put("public_key", "public");
        config.getIntegrationKeys().put("private_key", "private");
        payment.setPaymentMetaData(new HashMap<>());
        payment.getPaymentMetaData().put("paymentToken", "nonce");
        payment.setPaymentType(PaymentType.CREDITCARD);
        payment.setTransactionType(TransactionType.INIT);
        order.setTotal(amount);
        original.getTransactionDetails().put("TRANSACTIONID", "original-id");
        com.braintreegateway.Transaction result = mock(com.braintreegateway.Transaction.class);
        when(result.getId()).thenReturn("sdk-id");
        when(transactions.sale(any(TransactionRequest.class))).thenReturn(new Result<>(result));
        when(transactions.submitForSettlement("original-id", amount)).thenReturn(new Result<>(result));
        when(transactions.refund("original-id", amount)).thenReturn(new Result<>(result));
    }

    @Test
    void initReturnsClientToken() throws Exception {
        ClientTokenGateway tokens = mock(ClientTokenGateway.class);
        when(client.clientToken()).thenReturn(tokens);
        when(tokens.generate()).thenReturn("client-token");
        Transaction result = module.initTransaction(store, null, amount, payment, config, null);
        assertEquals("client-token", result.getDetails());
        assertEquals(TransactionType.INIT, result.getTransactionType());
        assertEquals(PaymentType.CREDITCARD, result.getPaymentType());
    }

    @Test
    void authorizePreservesContract() throws Exception {
        assertTransaction(module.authorize(store, null, Collections.emptyList(), amount, payment, config, null), TransactionType.AUTHORIZE);
        verify(transactions).sale(any(TransactionRequest.class));
    }

    @Test
    void authorizeAndCapturePreservesContract() throws Exception {
        assertTransaction(module.authorizeAndCapture(store, null, Collections.emptyList(), amount, payment, config, null), TransactionType.AUTHORIZECAPTURE);
    }

    @Test
    void capturePreservesContract() throws Exception {
        assertTransaction(module.capture(store, null, order, original, config, null), TransactionType.CAPTURE);
        verify(transactions).submitForSettlement("original-id", amount);
    }

    @Test
    void refundPreservesContract() throws Exception {
        assertTransaction(module.refund(true, store, original, order, amount, config, null), TransactionType.REFUND);
        verify(transactions).refund("original-id", amount);
    }

    @Test
    void emptyNonceIsRejected() {
        payment.getPaymentMetaData().clear();
        assertThrows(IntegrationException.class,
            () -> module.authorize(store, null, Collections.emptyList(), amount, payment, config, null));
        verify(transactions, never()).sale(any(TransactionRequest.class));
    }

    @Test
    void sdkDeclineIsRejected() {
        when(transactions.sale(any(TransactionRequest.class))).thenReturn(new Result<>(new ValidationErrors()));
        assertThrows(IntegrationException.class,
            () -> module.authorize(store, null, Collections.emptyList(), amount, payment, config, null));
    }

    private void assertTransaction(Transaction result, TransactionType type) {
        assertEquals(type, result.getTransactionType());
        assertEquals(PaymentType.CREDITCARD, result.getPaymentType());
        assertEquals(amount, result.getAmount());
        assertEquals("sdk-id", result.getTransactionDetails().get("TRANSACTIONID"));
        assertEquals("sdk-id", result.getTransactionDetails().get("TRNORDERNUMBER"));
        assertNull(result.getTransactionDetails().get("TRNAPPROVED"));
        assertTrue(result.getTransactionDetails().containsKey("MESSAGETEXT"));
    }
}
