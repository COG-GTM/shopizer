package com.salesmanager.test.payments;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;
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

public class PaymentServiceImplPaymentTypeTest {

	private PaymentServiceImpl paymentService;
	private TransactionService transactionService;
	private PaymentModule moneyOrderModule;
	private PaymentModule stripeModule;
	private PaymentModule paypalModule;
	private MerchantStore store;
	private Map<String, IntegrationConfiguration> configured;

	@Before
	public void setUp() throws Exception {
		paymentService = spy(new PaymentServiceImpl());
		transactionService = mock(TransactionService.class);
		moneyOrderModule = spy(new MoneyOrderPayment());
		stripeModule = mock(PaymentModule.class);
		paypalModule = mock(PaymentModule.class);
		store = mock(MerchantStore.class);

		Map<String, PaymentModule> paymentModules = new HashMap<>();
		paymentModules.put("moneyorder", moneyOrderModule);
		paymentModules.put("stripe", stripeModule);
		paymentModules.put("paypal-express-checkout", paypalModule);

		ReflectionTestUtils.setField(paymentService, "transactionService", transactionService);
		ReflectionTestUtils.setField(paymentService, "paymentModules", paymentModules);
		ReflectionTestUtils.setField(paymentService, "coreConfiguration", mock(CoreConfiguration.class));

		configured = new HashMap<>();
		configured.put("moneyorder", activeConfiguration("moneyorder"));
		configured.put("stripe", activeConfiguration("stripe"));
		configured.put("paypal-express-checkout", activeConfiguration("paypal-express-checkout"));
		doReturn(configured).when(paymentService).getPaymentModulesConfigured(store);
		doReturn(integrationModule("moneyorder", "moneyorder")).when(paymentService).getPaymentMethodByCode(store, "moneyorder");
		doReturn(integrationModule("stripe", "creditcard")).when(paymentService).getPaymentMethodByCode(store, "stripe");
		doReturn(integrationModule("paypal-express-checkout", "paypal"))
				.when(paymentService).getPaymentMethodByCode(store, "paypal-express-checkout");
	}

	@Test
	public void creditCardModuleRejectsMoneyOrderTypeBeforeCallingModule() throws Exception {
		Order order = order();

		assertRejected(payment("stripe", PaymentType.MONEYORDER), order);

		verify(stripeModule, never()).authorizeAndCapture(any(), any(), any(), any(), any(), any(), any());
		verify(transactionService, never()).create(any());
		assertNull(order.getStatus());
	}

	@Test
	public void paypalModuleRejectsCreditCardAndAcceptsPaypal() throws Exception {
		Order rejectedOrder = order();
		assertRejected(payment("paypal-express-checkout", PaymentType.CREDITCARD), rejectedOrder);
		verify(paypalModule, never()).authorizeAndCapture(any(), any(), any(), any(), any(), any(), any());
		verify(transactionService, never()).create(any());
		assertNull(rejectedOrder.getStatus());

		Transaction transaction = transaction(TransactionType.AUTHORIZECAPTURE, PaymentType.PAYPAL);
		when(paypalModule.authorizeAndCapture(any(), any(), any(), any(), any(), any(), any())).thenReturn(transaction);
		Payment paypalPayment = payment("paypal-express-checkout", PaymentType.PAYPAL);
		Order acceptedOrder = order();
		paymentService.processPayment(new Customer(), store, paypalPayment, new ArrayList<>(), acceptedOrder);

		assertEquals(OrderStatus.PROCESSED, acceptedOrder.getStatus());
		assertEquals(PaymentType.PAYPAL, acceptedOrder.getPaymentType());
		verify(transactionService).create(transaction);
	}

	@Test
	public void configuredModuleWithoutIntegrationModuleIsRejected() throws Exception {
		doReturn(null).when(paymentService).getPaymentMethodByCode(store, "stripe");
		Order order = order();

		assertRejected(payment("stripe", PaymentType.CREDITCARD), order);

		verify(stripeModule, never()).authorizeAndCapture(any(), any(), any(), any(), any(), any(), any());
		verify(transactionService, never()).create(any());
		assertNull(order.getStatus());
	}

	@Test
	public void invalidIntegrationModuleTypeIsRejected() throws Exception {
		doReturn(integrationModule("stripe", "bogus")).when(paymentService).getPaymentMethodByCode(store, "stripe");
		Order order = order();

		assertRejected(payment("stripe", PaymentType.CREDITCARD), order);

		verify(stripeModule, never()).authorizeAndCapture(any(), any(), any(), any(), any(), any(), any());
		verify(transactionService, never()).create(any());
		assertNull(order.getStatus());
	}

	@Test
	public void uppercaseIntegrationModuleTypeIsAccepted() throws Exception {
		doReturn(integrationModule("stripe", "CREDITCARD")).when(paymentService).getPaymentMethodByCode(store, "stripe");
		Transaction transaction = transaction(TransactionType.AUTHORIZECAPTURE, PaymentType.CREDITCARD);
		when(stripeModule.authorizeAndCapture(any(), any(), any(), any(), any(), any(), any())).thenReturn(transaction);
		Order order = order();

		paymentService.processPayment(new Customer(), store, payment("stripe", PaymentType.CREDITCARD),
				new ArrayList<>(), order);

		assertEquals(OrderStatus.PROCESSED, order.getStatus());
		assertEquals(PaymentType.CREDITCARD, order.getPaymentType());
		verify(transactionService).create(transaction);
	}

	@Test
	public void transactionMoneyOrderTypeKeepsCreditCardModuleOrderOrdered() throws Exception {
		Transaction transaction = transaction(TransactionType.AUTHORIZECAPTURE, PaymentType.MONEYORDER);
		when(stripeModule.authorizeAndCapture(any(), any(), any(), any(), any(), any(), any())).thenReturn(transaction);
		Order order = order();

		paymentService.processPayment(new Customer(), store, payment("stripe", PaymentType.CREDITCARD),
				new ArrayList<>(), order);

		assertEquals(OrderStatus.ORDERED, order.getStatus());
		assertEquals(PaymentType.CREDITCARD, order.getPaymentType());
		verify(transactionService).create(transaction);
	}

	@Test
	public void authorizeConfigurationCallsAuthorizeWithoutChangingOrderStatus() throws Exception {
		configured.get("moneyorder").getIntegrationKeys().put("transaction", "AUTHORIZE");
		Transaction transaction = transaction(TransactionType.AUTHORIZE, PaymentType.MONEYORDER);
		when(moneyOrderModule.authorize(any(), any(), any(), any(), any(), any(), any())).thenReturn(transaction);
		Payment payment = payment("moneyorder", null);
		Order order = order();

		paymentService.processPayment(new Customer(), store, payment, new ArrayList<>(), order);

		verify(moneyOrderModule).authorize(any(), any(), any(), any(), any(), any(), any());
		verify(moneyOrderModule, never()).authorizeAndCapture(any(), any(), any(), any(), any(), any(), any());
		verify(transactionService).create(transaction);
		assertNull(order.getStatus());
		assertEquals(PaymentType.MONEYORDER, order.getPaymentType());
		assertEquals(PaymentType.MONEYORDER, payment.getPaymentType());
		assertEquals(TransactionType.AUTHORIZE, payment.getTransactionType());
	}

	@Test
	public void moduleTypeOverwritesExistingOrderPaymentType() throws Exception {
		Order order = order();
		order.setPaymentType(PaymentType.PAYPAL);

		paymentService.processPayment(new Customer(), store, payment("moneyorder", PaymentType.MONEYORDER),
				new ArrayList<>(), order);

		assertEquals(OrderStatus.ORDERED, order.getStatus());
		assertEquals(PaymentType.MONEYORDER, order.getPaymentType());
	}

	@Test
	public void unconfiguredModuleIsRejectedBeforeCallingModule() throws Exception {
		configured.remove("stripe");
		Order order = order();

		assertRejected(payment("stripe", PaymentType.CREDITCARD), order);

		verify(stripeModule, never()).authorizeAndCapture(any(), any(), any(), any(), any(), any(), any());
		verify(transactionService, never()).create(any());
		assertNull(order.getStatus());
	}

	@Test
	public void inactiveModuleIsRejectedBeforeCallingModule() throws Exception {
		configured.get("stripe").setActive(false);
		Order order = order();

		assertRejected(payment("stripe", PaymentType.CREDITCARD), order);

		verify(stripeModule, never()).authorizeAndCapture(any(), any(), any(), any(), any(), any(), any());
		verify(transactionService, never()).create(any());
		assertNull(order.getStatus());
	}

	private void assertRejected(Payment payment, Order order) throws Exception {
		try {
			paymentService.processPayment(new Customer(), store, payment, new ArrayList<>(), order);
			fail("Expected ServiceException");
		} catch (ServiceException expected) {
		}
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

	private static Transaction transaction(TransactionType type, PaymentType paymentType) {
		Transaction transaction = new Transaction();
		transaction.setTransactionType(type);
		transaction.setPaymentType(paymentType);
		return transaction;
	}

	private static Order order() {
		Order order = new Order();
		order.setTotal(new BigDecimal("10.00"));
		return order;
	}
}
