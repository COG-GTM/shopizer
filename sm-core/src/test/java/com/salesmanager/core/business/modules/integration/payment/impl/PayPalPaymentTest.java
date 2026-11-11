package com.salesmanager.core.business.modules.integration.payment.impl;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import java.math.BigDecimal;
import java.util.Collections;
import java.util.HashMap;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.ArgumentCaptor;
import org.springframework.test.util.ReflectionTestUtils;
import com.paypal.sdk.models.CheckoutPaymentIntent;
import com.paypal.sdk.models.AmountWithBreakdown;
import com.paypal.sdk.models.PurchaseUnit;
import com.paypal.sdk.models.Money;
import com.paypal.sdk.models.OrderRequest;
import com.salesmanager.core.business.services.catalog.pricing.PricingService;
import com.salesmanager.core.business.utils.CoreConfiguration;
import com.salesmanager.core.model.merchant.MerchantStore;
import com.salesmanager.core.model.order.Order;
import com.salesmanager.core.model.payments.PaypalPayment;
import com.salesmanager.core.model.payments.PaymentType;
import com.salesmanager.core.model.payments.Transaction;
import com.salesmanager.core.model.payments.TransactionType;
import com.salesmanager.core.model.reference.currency.Currency;
import com.salesmanager.core.model.system.IntegrationConfiguration;
import com.salesmanager.core.modules.integration.IntegrationException;

class PayPalPaymentTest {
    private final BigDecimal amount = new BigDecimal("12.34");
    private final MerchantStore store = new MerchantStore();
    private final IntegrationConfiguration config = new IntegrationConfiguration();
    private final PaypalPayment payment = new PaypalPayment();
    private final Order order = new Order();
    private final PayPalGateway gateway = mock(PayPalGateway.class);

    private PayPalExpressCheckoutPayment module(boolean rest) throws Exception {
        Currency currency = new Currency();
        currency.setCurrency(java.util.Currency.getInstance("USD"));
        store.setCurrency(currency);
        store.setDomainName("shop.example.com");
        order.setCurrency(currency);
        order.setTotal(amount);
        config.setIntegrationKeys(new HashMap<>());
        config.getIntegrationKeys().put("clientId", "test-client");
        config.getIntegrationKeys().put("clientSecret", "test-secret");
        config.getIntegrationKeys().put("transaction", "AUTHORIZE");
        payment.setPaymentToken("order-id");
        payment.setTransactionType(TransactionType.AUTHORIZE);
        PayPalExpressCheckoutPayment module = rest ? new PayPalRestPayment() : new PayPalExpressCheckoutPayment();
        module.setGateway(gateway);
        when(gateway.getOrder("order-id")).thenReturn(approvedOrder("USD", "12.34"));
        PricingService pricing = mock(PricingService.class);
        when(pricing.getStringAmount(any(BigDecimal.class), eq(store)))
            .thenAnswer(invocation -> ((BigDecimal) invocation.getArgument(0)).toPlainString());
        ReflectionTestUtils.setField(module, "pricingService", pricing);
        CoreConfiguration core = mock(CoreConfiguration.class);
        when(core.getProperty("SHOP_SCHEME")).thenReturn("https");
        when(core.getProperty("SHOP_SCHEME", "http")).thenReturn("https");
        when(core.getProperty("CONTEXT_PATH", "sm-shop")).thenReturn("sm-shop");
        when(core.getProperty("URL_EXTENSION", ".html")).thenReturn(".html");
        ReflectionTestUtils.setField(module, "coreConfiguration", core);
        return module;
    }

    @ParameterizedTest
    @ValueSource(booleans = {false, true})
    void initCreatesOrderAndReturnsLegacyTokenKey(boolean rest) throws Exception {
        PayPalExpressCheckoutPayment module = module(rest);
        PayPalGateway.Result result = new PayPalGateway.Result("order-id", "debug");
        result.approvalUrl = "https://www.sandbox.paypal.com/checkoutnow?token=order-id";
        when(gateway.create(any(OrderRequest.class))).thenReturn(result);
        Transaction transaction = module.initTransaction(store, null, amount, payment, config, null);
        assertTransaction(transaction, TransactionType.INIT, null);
        assertEquals("order-id", transaction.getTransactionDetails().get("TOKEN"));
        assertEquals("debug", transaction.getTransactionDetails().get("CORRELATION"));
        assertEquals(result.approvalUrl, transaction.getTransactionDetails().get("APPROVALURL"));
        config.getIntegrationKeys().put("transaction", "AUTHORIZECAPTURE");
        module.initTransaction(store, null, amount, payment, config, null);
        ArgumentCaptor<OrderRequest> request = ArgumentCaptor.forClass(OrderRequest.class);
        verify(gateway, times(2)).create(request.capture());
        assertEquals(CheckoutPaymentIntent.AUTHORIZE, request.getAllValues().get(0).getIntent());
        assertEquals(CheckoutPaymentIntent.CAPTURE, request.getValue().getIntent());
        assertEquals("12.34", request.getValue().getPurchaseUnits().get(0).getAmount().getValue());
    }

    @ParameterizedTest
    @ValueSource(booleans = {false, true})
    void authorizePreservesDetails(boolean rest) throws Exception {
        PayPalExpressCheckoutPayment module = module(rest);
        PayPalGateway.Result result = new PayPalGateway.Result("auth-id", "debug");
        result.payerId = "payer";
        when(gateway.authorize("order-id")).thenReturn(result);
        Transaction transaction = module.authorize(store, null, Collections.emptyList(), amount, payment, config, null);
        assertTransaction(transaction, TransactionType.AUTHORIZE, "auth-id");
        assertEquals("order-id", transaction.getTransactionDetails().get("TOKEN"));
        assertEquals("payer", transaction.getTransactionDetails().get("PAYERID"));
        assertEquals("debug", transaction.getTransactionDetails().get("CORRELATION"));
    }

    @ParameterizedTest
    @ValueSource(booleans = {false, true})
    void authorizeAndCaptureUsesOrderCapture(boolean rest) throws Exception {
        PayPalExpressCheckoutPayment module = module(rest);
        payment.setTransactionType(TransactionType.AUTHORIZECAPTURE);
        when(gateway.captureOrder("order-id")).thenReturn(new PayPalGateway.Result("capture-id", "debug"));
        assertTransaction(module.authorizeAndCapture(store, null, Collections.emptyList(), amount, payment, config, null),
            TransactionType.AUTHORIZECAPTURE, "capture-id");
    }

    @ParameterizedTest
    @ValueSource(booleans = {false, true})
    void capturePreservesAuthorizationAndRecordsCaptureForRefund(boolean rest) throws Exception {
        PayPalExpressCheckoutPayment module = module(rest);
        Transaction original = new Transaction();
        original.getTransactionDetails().put("TRANSACTIONID", "auth-id");
        when(gateway.capture(eq("auth-id"), any(Money.class))).thenReturn(new PayPalGateway.Result("capture-id", "debug"));
        Transaction captured = module.capture(store, null, order, original, config, null);
        assertTransaction(captured, TransactionType.CAPTURE, "auth-id");
        assertEquals("auth-id", captured.getTransactionDetails().get("AUTHORIZATIONID"));
        assertEquals("capture-id", captured.getTransactionDetails().get("CAPTUREID"));
        ArgumentCaptor<Money> value = ArgumentCaptor.forClass(Money.class);
        verify(gateway).capture(eq("auth-id"), value.capture());
        assertEquals("12.34", value.getValue().getValue());
        assertEquals("USD", value.getValue().getCurrencyCode());
    }

    @ParameterizedTest
    @ValueSource(booleans = {false, true})
    void refundsFullAndPartialCaptures(boolean rest) throws Exception {
        PayPalExpressCheckoutPayment module = module(rest);
        Transaction original = new Transaction();
        original.getTransactionDetails().put("TRANSACTIONID", "auth-id");
        original.getTransactionDetails().put("CAPTUREID", "capture-id");
        when(gateway.refund(eq("capture-id"), any())).thenReturn(new PayPalGateway.Result("refund-id", "debug"));
        assertTransaction(module.refund(true, store, original, order, amount, config, null), TransactionType.REFUND, "refund-id");
        assertEquals("debug", original.getTransactionDetails().get("CORRELATION"));
        module.refund(false, store, original, order, amount, config, null);
        verify(gateway).refund("capture-id", null);
        ArgumentCaptor<Money> value = ArgumentCaptor.forClass(Money.class);
        verify(gateway, times(2)).refund(eq("capture-id"), value.capture());
        assertEquals("12.34", value.getAllValues().get(0).getValue());
    }

    @ParameterizedTest
    @ValueSource(booleans = {false, true})
    void validatesNewCredentialFields(boolean rest) throws Exception {
        PayPalExpressCheckoutPayment module = module(rest);
        module.validateModuleConfiguration(config, store);
        config.setIntegrationKeys(null);
        IntegrationException error = assertThrows(IntegrationException.class,
            () -> module.validateModuleConfiguration(config, store));
        assertEquals(java.util.Arrays.asList("clientId", "clientSecret"), error.getErrorFields());
    }

    @ParameterizedTest
    @ValueSource(booleans = {false, true})
    void missingTokenAndSdkFailureAreRejected(boolean rest) throws Exception {
        PayPalExpressCheckoutPayment module = module(rest);
        payment.setPaymentToken(null);
        assertThrows(IntegrationException.class,
            () -> module.authorize(store, null, Collections.emptyList(), amount, payment, config, null));
        payment.setPaymentToken("order-id");
        when(gateway.authorize("order-id")).thenThrow(new IllegalStateException("offline"));
        assertThrows(IntegrationException.class,
            () -> module.authorize(store, null, Collections.emptyList(), amount, payment, config, null));
    }

    private void assertTransaction(Transaction transaction, TransactionType type, String id) {
        assertEquals(type, transaction.getTransactionType());
        assertEquals(PaymentType.PAYPAL, transaction.getPaymentType());
        assertEquals(amount, transaction.getAmount());
        assertEquals(id, transaction.getTransactionDetails().get("TRANSACTIONID"));
        assertNotNull(transaction.getTransactionDate());
    }

    private com.paypal.sdk.models.Order approvedOrder(String currency, String value) {
        PurchaseUnit unit = new PurchaseUnit();
        unit.setAmount(new AmountWithBreakdown.Builder(currency, value).build());
        com.paypal.sdk.models.Order approved = new com.paypal.sdk.models.Order();
        approved.setPurchaseUnits(Collections.singletonList(unit));
        return approved;
    }

    @ParameterizedTest
    @ValueSource(booleans = {false, true})
    void mismatchedOrMissingOrderAmountRejectsAuthorizeAndCapture(boolean rest) throws Exception {
        PayPalExpressCheckoutPayment module = module(rest);
        com.paypal.sdk.models.Order[] invalidOrders = {
            approvedOrder("USD", "1.00"), approvedOrder("EUR", "12.34"),
            new com.paypal.sdk.models.Order(), null
        };
        for (com.paypal.sdk.models.Order approved : invalidOrders) {
            when(gateway.getOrder("order-id")).thenReturn(approved);
            assertThrows(IntegrationException.class,
                () -> module.authorize(store, null, Collections.emptyList(), amount, payment, config, null));
            assertThrows(IntegrationException.class,
                () -> module.authorizeAndCapture(store, null, Collections.emptyList(), amount, payment, config, null));
        }
        verify(gateway, never()).authorize(anyString());
        verify(gateway, never()).captureOrder(anyString());
    }
}
