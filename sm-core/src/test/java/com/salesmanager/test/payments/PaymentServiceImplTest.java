package com.salesmanager.test.payments;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.fail;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.util.ArrayList;
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
import com.salesmanager.core.model.payments.Payment;
import com.salesmanager.core.model.payments.PaymentType;
import com.salesmanager.core.model.payments.Transaction;
import com.salesmanager.core.model.payments.TransactionType;
import com.salesmanager.core.model.system.IntegrationConfiguration;
import com.salesmanager.core.model.system.IntegrationModule;
import com.salesmanager.core.modules.integration.payment.model.PaymentModule;

public class PaymentServiceImplTest {

	private PaymentServiceImpl paymentService;
	private TransactionService transactionService;
	private PaymentModule moneyOrderModule;
	private PaymentModule creditCardModule;
	private MerchantStore store;

	@Before
	public void setUp() throws Exception {
		paymentService = spy(new PaymentServiceImpl());
		transactionService = mock(TransactionService.class);
		moneyOrderModule = spy(new MoneyOrderPayment());
		creditCardModule = mock(PaymentModule.class);
		store = mock(MerchantStore.class);

		Map<String, PaymentModule> paymentModules = new HashMap<>();
		paymentModules.put("moneyorder", moneyOrderModule);
		paymentModules.put("stripe", creditCardModule);

		ReflectionTestUtils.setField(paymentService, "transactionService", transactionService);
		ReflectionTestUtils.setField(paymentService, "paymentModules", paymentModules);
		ReflectionTestUtils.setField(paymentService, "coreConfiguration", mock(CoreConfiguration.class));

		Map<String, IntegrationConfiguration> configured = new HashMap<>();
		configured.put("moneyorder", activeConfiguration("moneyorder"));
		configured.put("stripe", activeConfiguration("stripe"));
		doReturn(configured).when(paymentService).getPaymentModulesConfigured(store);
		doReturn(integrationModule("moneyorder", "moneyorder")).when(paymentService).getPaymentMethodByCode(store, "moneyorder");
		doReturn(integrationModule("stripe", "creditcard")).when(paymentService).getPaymentMethodByCode(store, "stripe");
	}

	@Test
	public void moneyOrderModuleWithCreditCardTypeIsRejected() throws Exception {
		Order order = order();
		try {
			paymentService.processPayment(new Customer(), store, payment("moneyorder", PaymentType.CREDITCARD), new ArrayList<>(), order);
			fail("Expected ServiceException for mismatched payment type");
		} catch (ServiceException expected) {
			// expected
		}
		verify(moneyOrderModule, never()).authorizeAndCapture(any(), any(), any(), any(), any(), any(), any());
		verify(transactionService, never()).create(any());
		assertEquals(null, order.getStatus());
	}

	@Test
	public void moneyOrderStaysOrdered() throws Exception {
		Order order = order();
		paymentService.processPayment(new Customer(), store, payment("moneyorder", PaymentType.MONEYORDER), new ArrayList<>(), order);
		assertEquals(OrderStatus.ORDERED, order.getStatus());
		assertEquals(PaymentType.MONEYORDER, order.getPaymentType());
	}

	@Test
	public void missingPaymentTypeIsDerivedFromModule() throws Exception {
		Order order = order();
		order.setPaymentType(PaymentType.CREDITCARD);
		Payment payment = payment("moneyorder", null);
		paymentService.processPayment(new Customer(), store, payment, new ArrayList<>(), order);
		assertEquals(OrderStatus.ORDERED, order.getStatus());
		assertEquals(PaymentType.MONEYORDER, order.getPaymentType());
		assertEquals(PaymentType.MONEYORDER, payment.getPaymentType());
	}

	@Test
	public void creditCardModuleIsProcessed() throws Exception {
		Transaction transaction = new Transaction();
		transaction.setTransactionType(TransactionType.AUTHORIZECAPTURE);
		transaction.setPaymentType(PaymentType.CREDITCARD);
		when(creditCardModule.authorizeAndCapture(any(), any(), any(), any(), any(), any(), any())).thenReturn(transaction);

		Order order = order();
		paymentService.processPayment(new Customer(), store, payment("stripe", PaymentType.CREDITCARD), new ArrayList<>(), order);
		assertEquals(OrderStatus.PROCESSED, order.getStatus());
		assertEquals(PaymentType.CREDITCARD, order.getPaymentType());
	}

	private static IntegrationConfiguration activeConfiguration(String code) {
		IntegrationConfiguration configuration = new IntegrationConfiguration();
		configuration.setModuleCode(code);
		configuration.setActive(true);
		configuration.setIntegrationKeys(new HashMap<>());
		return configuration;
	}

	private static IntegrationModule integrationModule(String code, String type) {
		IntegrationModule module = new IntegrationModule();
		module.setCode(code);
		module.setType(type);
		return module;
	}

	private static Payment payment(String moduleName, PaymentType type) {
		Payment payment = new Payment();
		payment.setModuleName(moduleName);
		payment.setPaymentType(type);
		return payment;
	}

	private static Order order() {
		Order order = new Order();
		order.setTotal(new BigDecimal("10.00"));
		return order;
	}
}
