package com.salesmanager.test.shop.integration.payment;

import static org.junit.Assert.*;

import java.math.BigDecimal;
import java.util.Collections;

import javax.persistence.EntityManager;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.junit4.SpringRunner;
import org.springframework.transaction.annotation.Transactional;

import com.salesmanager.core.business.exception.ServiceException;
import com.salesmanager.core.business.repositories.payments.TransactionRepository;
import com.salesmanager.core.business.services.merchant.MerchantStoreService;
import com.salesmanager.core.business.services.payments.PaymentService;
import com.salesmanager.core.model.customer.Customer;
import com.salesmanager.core.model.merchant.MerchantStore;
import com.salesmanager.core.model.order.Order;
import com.salesmanager.core.model.order.orderstatus.OrderStatus;
import com.salesmanager.core.model.payments.Payment;
import com.salesmanager.core.model.payments.PaymentType;
import com.salesmanager.core.model.payments.Transaction;
import com.salesmanager.core.model.payments.TransactionType;
import com.salesmanager.core.model.system.IntegrationConfiguration;
import com.salesmanager.shop.application.ShopApplication;

@RunWith(SpringRunner.class)
@SpringBootTest(classes = ShopApplication.class, webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@Transactional
public class MoneyOrderPersistenceIntegrationTest {
    @Autowired private PaymentService payments;
    @Autowired private MerchantStoreService stores;
    @Autowired private TransactionRepository transactions;
    @Autowired private EntityManager entityManager;
    private MerchantStore store;

    @Before
    public void configureMoneyOrder() throws Exception {
        store = stores.getByCode(MerchantStore.DEFAULT_STORE);
        assertNotNull("Default store must be seeded", store);
        IntegrationConfiguration configuration = new IntegrationConfiguration();
        configuration.setModuleCode("moneyorder");
        configuration.setActive(true);
        configuration.getIntegrationKeys().put("address", "Test remittance address");
        payments.savePaymentModuleConfiguration(configuration, store);
        assertEquals("moneyorder", payments.getPaymentMethodByCode(store, "moneyorder").getType());
    }

    @Test
    public void matchingMoneyOrderPersistsAnOfflineTransaction() throws Exception {
        assertOfflineTransaction(PaymentType.MONEYORDER);
    }

    @Test
    public void missingTypeIsResolvedFromRealModuleCatalog() throws Exception {
        assertOfflineTransaction(null);
    }

    @Test
    public void spoofedCardTypeCreatesNoTransactionInH2() throws Exception {
        long before = transactions.count();
        Order order = order();
        try {
            payments.processPayment(new Customer(), store, payment(PaymentType.CREDITCARD), Collections.emptyList(), order);
            fail("moneyorder/CREDITCARD must be rejected");
        } catch (ServiceException exception) {
            assertEquals(ServiceException.EXCEPTION_VALIDATION, exception.getExceptionType());
        }
        entityManager.flush();
        assertEquals(before, transactions.count());
        assertNull(order.getStatus());
    }

    private void assertOfflineTransaction(PaymentType supplied) throws Exception {
        Order order = order();
        Payment payment = payment(supplied);
        long before = transactions.count();
        Transaction result = payments.processPayment(new Customer(), store, payment, Collections.emptyList(), order);
        entityManager.flush();
        entityManager.clear();
        Transaction stored = transactions.findById(result.getId()).get();
        assertEquals(before + 1, transactions.count());
        assertEquals(PaymentType.MONEYORDER, stored.getPaymentType());
        assertEquals(TransactionType.AUTHORIZECAPTURE, stored.getTransactionType());
        assertEquals(0, order.getTotal().compareTo(stored.getAmount()));
        assertEquals(PaymentType.MONEYORDER, payment.getPaymentType());
        assertEquals(PaymentType.MONEYORDER, order.getPaymentType());
        assertEquals("moneyorder", order.getPaymentModuleCode());
        assertEquals(OrderStatus.ORDERED, order.getStatus());
    }

    private Payment payment(PaymentType type) {
        Payment payment = new Payment();
        payment.setModuleName("moneyorder");
        payment.setPaymentType(type);
        return payment;
    }

    private Order order() {
        Order order = new Order();
        order.setTotal(new BigDecimal("42.50"));
        order.setPaymentType(PaymentType.CREDITCARD);
        return order;
    }
}
