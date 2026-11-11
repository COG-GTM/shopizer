package com.salesmanager.core.business.modules.integration.payment.impl;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import java.math.BigDecimal;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.ArgumentCaptor;
import org.springframework.test.util.ReflectionTestUtils;
import com.salesmanager.core.business.utils.ProductPriceUtils;
import com.salesmanager.core.model.merchant.MerchantStore;
import com.salesmanager.core.model.order.Order;
import com.salesmanager.core.model.payments.Payment;
import com.salesmanager.core.model.payments.PaymentType;
import com.salesmanager.core.model.payments.Transaction;
import com.salesmanager.core.model.payments.TransactionType;
import com.salesmanager.core.model.reference.currency.Currency;
import com.salesmanager.core.model.system.IntegrationConfiguration;
import com.salesmanager.core.modules.integration.IntegrationException;
import com.salesmanager.core.modules.integration.payment.model.PaymentModule;
import com.stripe.model.Charge;
import com.stripe.model.PaymentIntent;
import com.stripe.model.Refund;
import com.stripe.param.PaymentIntentCaptureParams;
import com.stripe.param.PaymentIntentCreateParams;

class StripeSdkPaymentTest {
    private final BigDecimal amount = new BigDecimal("12.34");
    private final MerchantStore store = new MerchantStore();
    private final IntegrationConfiguration config = new IntegrationConfiguration();
    private final Payment payment = new Payment();
    private final Order order = new Order();
    private final StripeGateway gateway = mock(StripeGateway.class);

    private PaymentModule module(boolean intent) throws Exception {
        Currency currency = new Currency();
        currency.setCurrency(java.util.Currency.getInstance("USD"));
        store.setCurrency(currency);
        store.setStorename("Test Store");
        order.setTotal(amount);
        order.setCurrency(currency);
        config.setIntegrationKeys(new HashMap<>());
        config.getIntegrationKeys().put("secretKey", "sk_test_mock");
        config.getIntegrationKeys().put("publishableKey", "pk_test_mock");
        payment.setPaymentType(PaymentType.CREDITCARD);
        payment.setTransactionType(TransactionType.AUTHORIZE);
        Map<String, String> metadata = new HashMap<>();
        metadata.put("stripe_token", "token");
        metadata.put("paymentToken", "token");
        payment.setPaymentMetaData(metadata);
        PaymentModule module;
        if (intent) {
            Stripe3Payment stripe = new Stripe3Payment();
            stripe.setGateway(gateway);
            module = stripe;
        } else {
            StripePayment stripe = new StripePayment();
            stripe.setGateway(gateway);
            module = stripe;
        }
        ProductPriceUtils pricing = mock(ProductPriceUtils.class);
        when(pricing.getAdminFormatedAmount(eq(store), any(BigDecimal.class))).thenReturn("12.34");
        ReflectionTestUtils.setField(module, "productPriceUtils", pricing);
        Charge charge = new Charge();
        charge.setId("sdk-id");
        charge.setStatus("succeeded");
        PaymentIntent pi = new PaymentIntent();
        pi.setId("sdk-id");
        pi.setStatus("succeeded");
        pi.setClientSecret("intent-secret");
        Refund refund = new Refund();
        refund.setId("refund-id");
        refund.setReason("requested_by_customer");
        when(gateway.createCharge(anyMap())).thenReturn(charge);
        when(gateway.retrieveCharge(anyString())).thenReturn(charge);
        when(gateway.createIntent(any(PaymentIntentCreateParams.class))).thenReturn(pi);
        when(gateway.retrieveIntent(anyString())).thenReturn(pi);
        when(gateway.captureIntent(any(PaymentIntent.class), any(PaymentIntentCaptureParams.class))).thenReturn(pi);
        when(gateway.refund(anyMap())).thenReturn(refund);
        return module;
    }

    @ParameterizedTest
    @ValueSource(booleans = {false, true})
    void initPreservesContract(boolean intent) throws Exception {
        PaymentModule module = module(intent);
        Transaction result = module.initTransaction(store, null, amount, payment, config, null);
        assertEquals(TransactionType.AUTHORIZE, result.getTransactionType());
        assertEquals(PaymentType.CREDITCARD, result.getPaymentType());
        if (intent) {
            assertEquals("sdk-id", result.getTransactionDetails().get("TRANSACTIONID"));
            assertEquals("intent-secret", result.getTransactionDetails().get("INTENTSECRET"));
            ArgumentCaptor<PaymentIntentCreateParams> params = ArgumentCaptor.forClass(PaymentIntentCreateParams.class);
            verify(gateway).createIntent(params.capture());
            assertEquals(1234L, params.getValue().getAmount());
            assertEquals(PaymentIntentCreateParams.CaptureMethod.MANUAL, params.getValue().getCaptureMethod());
        } else {
            assertEquals("pk_test_mock", result.getDetails());
            verifyNoInteractions(gateway);
        }
    }

    @ParameterizedTest
    @ValueSource(booleans = {false, true})
    void authorizePreservesTypeAndDetails(boolean intent) throws Exception {
        PaymentModule module = module(intent);
        assertTransaction(module.authorize(store, null, Collections.emptyList(), amount, payment, config, null),
            TransactionType.AUTHORIZE, "token", "sdk-id");
        if (!intent) {
            ArgumentCaptor<Map<String, Object>> params = ArgumentCaptor.forClass(Map.class);
            verify(gateway).createCharge(params.capture());
            assertEquals(false, params.getValue().get("capture"));
            assertEquals("1234", params.getValue().get("amount"));
        }
    }

    @ParameterizedTest
    @ValueSource(booleans = {false, true})
    void authorizeAndCapturePreservesTypeAndDetails(boolean intent) throws Exception {
        PaymentModule module = module(intent);
        assertTransaction(module.authorizeAndCapture(store, null, Collections.emptyList(), amount, payment, config, null),
            TransactionType.AUTHORIZECAPTURE, "token", "sdk-id");
        if (intent) {
            verify(gateway).captureIntent(any(PaymentIntent.class), any(PaymentIntentCaptureParams.class));
        } else {
            ArgumentCaptor<Map<String, Object>> params = ArgumentCaptor.forClass(Map.class);
            verify(gateway).createCharge(params.capture());
            assertEquals(true, params.getValue().get("capture"));
        }
    }

    @ParameterizedTest
    @ValueSource(booleans = {false, true})
    void capturePreservesTypeAndDetails(boolean intent) throws Exception {
        PaymentModule module = module(intent);
        Transaction original = new Transaction();
        original.getTransactionDetails().put("TRANSACTIONID", "token");
        original.getTransactionDetails().put("TRNORDERNUMBER", "sdk-id");
        assertTransaction(module.capture(store, null, order, original, config, null),
            TransactionType.CAPTURE, "token", "sdk-id");
        if (intent) {
            ArgumentCaptor<PaymentIntentCaptureParams> params = ArgumentCaptor.forClass(PaymentIntentCaptureParams.class);
            verify(gateway).captureIntent(any(PaymentIntent.class), params.capture());
            assertEquals(1234L, params.getValue().getAmountToCapture());
        } else {
            verify(gateway).captureCharge(any(Charge.class));
        }
    }

    @ParameterizedTest
    @ValueSource(booleans = {false, true})
    void refundRetainsExistingCaptureTypeAndNullTransactionId(boolean intent) throws Exception {
        PaymentModule module = module(intent);
        Transaction original = new Transaction();
        original.getTransactionDetails().put("TRNORDERNUMBER", "sdk-id");
        Transaction result = module.refund(true, store, original, order, amount, config, null);
        assertEquals(TransactionType.CAPTURE, result.getTransactionType());
        assertNull(result.getTransactionDetails().get("TRANSACTIONID"));
        assertEquals("refund-id", result.getTransactionDetails().get("TRNORDERNUMBER"));
        assertEquals("requested_by_customer", result.getTransactionDetails().get("TRNAPPROVED"));
        ArgumentCaptor<Map<String, Object>> params = ArgumentCaptor.forClass(Map.class);
        verify(gateway).refund(params.capture());
        assertEquals("1234", params.getValue().get("amount"));
        assertEquals("sdk-id", params.getValue().get(intent ? "payment_intent" : "charge"));
    }

    @ParameterizedTest
    @ValueSource(booleans = {false, true})
    void emptyTokenAndSdkErrorsAreRejected(boolean intent) throws Exception {
        PaymentModule module = module(intent);
        payment.getPaymentMetaData().clear();
        assertThrows(IntegrationException.class,
            () -> module.authorize(store, null, Collections.emptyList(), amount, payment, config, null));
        payment.getPaymentMetaData().put("stripe_token", "token");
        when(gateway.createCharge(anyMap())).thenThrow(new IllegalStateException("offline"));
        when(gateway.retrieveIntent(anyString())).thenThrow(new IllegalStateException("offline"));
        assertThrows(IntegrationException.class,
            () -> module.authorize(store, null, Collections.emptyList(), amount, payment, config, null));
    }

    private void assertTransaction(Transaction result, TransactionType type, String token, String id) {
        assertEquals(type, result.getTransactionType());
        assertEquals(PaymentType.CREDITCARD, result.getPaymentType());
        assertEquals(amount, result.getAmount());
        assertEquals(token, result.getTransactionDetails().get("TRANSACTIONID"));
        assertEquals(id, result.getTransactionDetails().get("TRNORDERNUMBER"));
        assertEquals("succeeded", result.getTransactionDetails().get("TRNAPPROVED"));
        assertTrue(result.getTransactionDetails().containsKey("MESSAGETEXT"));
    }
}
