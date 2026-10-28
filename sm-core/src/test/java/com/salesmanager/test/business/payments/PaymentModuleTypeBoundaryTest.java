package com.salesmanager.test.business.payments;

import static org.junit.Assert.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import java.math.BigDecimal;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

import org.junit.Before;
import org.junit.Test;
import org.springframework.test.util.ReflectionTestUtils;

import com.salesmanager.core.business.exception.ServiceException;
import com.salesmanager.core.business.modules.integration.payment.impl.MoneyOrderPayment;
import com.salesmanager.core.business.services.payments.PaymentServiceImpl;
import com.salesmanager.core.business.services.payments.TransactionService;
import com.salesmanager.core.business.utils.CoreConfiguration;
import com.salesmanager.core.model.customer.Customer;
import com.salesmanager.core.model.merchant.MerchantStore;
import com.salesmanager.core.model.order.Order;
import com.salesmanager.core.model.order.orderstatus.OrderStatus;
import com.salesmanager.core.model.payments.*;
import com.salesmanager.core.model.system.IntegrationConfiguration;
import com.salesmanager.core.model.system.IntegrationModule;
import com.salesmanager.core.modules.integration.payment.model.PaymentModule;

public class PaymentModuleTypeBoundaryTest {
    private PaymentServiceImpl service;
    private PaymentModule provider;
    private TransactionService transactions;
    private MerchantStore store;
    private IntegrationConfiguration configuration;
    private IntegrationModule metadata;
    private Payment payment;
    private Order order;

    @Before
    public void setUp() throws Exception {
        service = spy(new PaymentServiceImpl());
        provider = mock(PaymentModule.class);
        transactions = mock(TransactionService.class);
        Map<String, PaymentModule> providers = new HashMap<>();
        providers.put("provider", provider);
        ReflectionTestUtils.setField(service, "paymentModules", providers);
        ReflectionTestUtils.setField(service, "transactionService", transactions);
        ReflectionTestUtils.setField(service, "coreConfiguration", mock(CoreConfiguration.class));
        store = new MerchantStore();
        configuration = new IntegrationConfiguration();
        configuration.setModuleCode("provider");
        configuration.setActive(true);
        doReturn(Collections.singletonMap("provider", configuration)).when(service).getPaymentModulesConfigured(store);
        metadata = new IntegrationModule();
        metadata.setCode("provider");
        metadata.setType("creditcard");
        doReturn(metadata).when(service).getPaymentMethodByCode(store, "provider");
        payment = new Payment();
        payment.setModuleName("provider");
        order = new Order();
        order.setTotal(new BigDecimal("42.50"));
    }

    @Test
    public void everyOfflineModuleKeepsOrderUnprocessedEvenIfProviderClaimsCardCapture() throws Exception {
        for (PaymentType type : offlineTypes()) {
            metadata.setType(type.name().toLowerCase(java.util.Locale.ROOT));
            payment.setPaymentType(type);
            stubCapture(PaymentType.CREDITCARD);
            process();
            assertEquals(type.name(), OrderStatus.ORDERED, order.getStatus());
            assertEquals(type, order.getPaymentType());
            assertEquals("provider", order.getPaymentModuleCode());
        }
    }

    @Test
    public void everyOfflineProviderTransactionPreventsCardOrderBeingProcessed() throws Exception {
        for (PaymentType type : offlineTypes()) {
            payment.setPaymentType(PaymentType.CREDITCARD);
            stubCapture(type);
            process();
            assertEquals(type.name(), OrderStatus.ORDERED, order.getStatus());
        }
    }

    @Test
    public void unsupportedAndMissingModuleTypesRejectBeforeProviderOrPersistence() throws Exception {
        for (String type : new String[] {null, "", "not-a-payment-type"}) {
            metadata.setType(type);
            assertRejected();
        }
        verifyNoInteractions(provider, transactions);
    }

    @Test
    public void mismatchAlsoRejectsAuthorizationOnlyWithoutCallingProvider() throws Exception {
        configuration.getIntegrationKeys().put("transaction", "AUTHORIZE");
        payment.setPaymentType(PaymentType.PAYPAL);
        assertRejected();
        verifyNoInteractions(provider, transactions);
    }

    @Test
    public void omittedTypeUsesMixedCaseModuleMetadataAndOverwritesStaleOrderFields() throws Exception {
        metadata.setType("PaYpAl");
        order.setPaymentType(PaymentType.CREDITCARD);
        order.setPaymentModuleCode("untrusted-module");
        stubCapture(PaymentType.PAYPAL);
        Transaction result = process();
        assertEquals(PaymentType.PAYPAL, payment.getPaymentType());
        assertEquals(PaymentType.PAYPAL, order.getPaymentType());
        assertEquals("provider", order.getPaymentModuleCode());
        assertEquals(OrderStatus.PROCESSED, order.getStatus());
        verify(transactions).create(result);
    }

    @Test
    public void authorizationDoesNotPromoteOrderToProcessed() throws Exception {
        configuration.getIntegrationKeys().put("transaction", "AUTHORIZE");
        order.setStatus(OrderStatus.ORDERED);
        Transaction authorization = new Transaction();
        authorization.setPaymentType(PaymentType.CREDITCARD);
        authorization.setTransactionType(TransactionType.AUTHORIZE);
        when(provider.authorize(any(), any(), anyList(), any(), any(), any(), any())).thenReturn(authorization);
        assertSame(authorization, process());
        assertEquals(PaymentType.CREDITCARD, payment.getPaymentType());
        assertEquals(OrderStatus.ORDERED, order.getStatus());
        verify(transactions).create(authorization);
        verify(provider, never()).authorizeAndCapture(any(), any(), anyList(), any(), any(), any(), any());
    }

    @Test
    public void moneyOrderAdapterPinsTypeWithoutChangingAmountOrTransactionKind() throws Exception {
        for (PaymentType supplied : new PaymentType[] {null, PaymentType.CREDITCARD, PaymentType.MONEYORDER}) {
            payment.setPaymentType(supplied);
            Transaction result = new MoneyOrderPayment().authorizeAndCapture(store, new Customer(),
                    Collections.emptyList(), order.getTotal(), payment, configuration, metadata);
            assertEquals(PaymentType.MONEYORDER, payment.getPaymentType());
            assertEquals(PaymentType.MONEYORDER, result.getPaymentType());
            assertEquals(TransactionType.AUTHORIZECAPTURE, result.getTransactionType());
            assertEquals(order.getTotal(), result.getAmount());
            assertNotNull(result.getTransactionDate());
        }
    }

    private PaymentType[] offlineTypes() {
        return new PaymentType[] {PaymentType.MONEYORDER, PaymentType.COD, PaymentType.INVOICE, PaymentType.DIRECTBANK};
    }

    private void stubCapture(PaymentType type) throws Exception {
        Transaction transaction = new Transaction();
        transaction.setPaymentType(type);
        transaction.setTransactionType(TransactionType.AUTHORIZECAPTURE);
        when(provider.authorizeAndCapture(any(), any(), anyList(), any(), any(), any(), any())).thenReturn(transaction);
    }

    private Transaction process() throws Exception {
        return service.processPayment(new Customer(), store, payment, Collections.emptyList(), order);
    }

    private void assertRejected() throws Exception {
        try {
            process();
            fail("Invalid module/type combination must be rejected");
        } catch (ServiceException exception) {
            assertEquals(ServiceException.EXCEPTION_VALIDATION, exception.getExceptionType());
        }
        assertNull(order.getStatus());
        assertNull(order.getPaymentType());
        assertNull(order.getPaymentModuleCode());
    }
}
