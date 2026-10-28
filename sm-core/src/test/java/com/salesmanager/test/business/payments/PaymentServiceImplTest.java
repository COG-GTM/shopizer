package com.salesmanager.test.business.payments;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.fail;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.lang.reflect.Field;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;

import org.junit.Before;
import org.junit.Test;

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
import com.salesmanager.core.model.reference.currency.Currency;
import com.salesmanager.core.model.system.IntegrationConfiguration;
import com.salesmanager.core.model.system.IntegrationModule;
import com.salesmanager.core.modules.integration.payment.model.PaymentModule;

public class PaymentServiceImplTest {

	private PaymentServiceImpl paymentService;
	private TransactionService transactionService;
	private PaymentModule cardModule;
	private MerchantStore store;
	private Map<String, IntegrationConfiguration> configured;

	@Before
	public void setUp() throws Exception {
		PaymentServiceImpl target = new PaymentServiceImpl();
		transactionService = mock(TransactionService.class);
		cardModule = mock(PaymentModule.class);

		Map<String, PaymentModule> modules = new HashMap<String, PaymentModule>();
		modules.put("moneyorder", new MoneyOrderPayment());
		modules.put("stripe", cardModule);

		inject(target, "transactionService", transactionService);
		inject(target, "coreConfiguration", mock(CoreConfiguration.class));
		inject(target, "paymentModules", modules);

		paymentService = spy(target);

		store = mock(MerchantStore.class);
		when(store.getCurrency()).thenReturn(new Currency());

		configured = new HashMap<String, IntegrationConfiguration>();
		configured.put("moneyorder", activeConfiguration("moneyorder"));
		configured.put("stripe", activeConfiguration("stripe"));
		doReturn(configured).when(paymentService).getPaymentModulesConfigured(store);
		doReturn(integrationModule("moneyorder", "moneyorder")).when(paymentService).getPaymentMethodByCode(store, "moneyorder");
		doReturn(integrationModule("stripe", "creditcard")).when(paymentService).getPaymentMethodByCode(store, "stripe");
	}

	@Test
	public void moneyOrderModuleWithCreditCardTypeIsRejected() throws Exception {
		Order order = order(PaymentType.CREDITCARD);
		try {
			paymentService.processPayment(new Customer(), store, payment("moneyorder", PaymentType.CREDITCARD), new ArrayList<>(), order);
			fail("moneyorder module paired with CREDITCARD must be rejected");
		} catch (ServiceException e) {
			assertEquals(ServiceException.EXCEPTION_VALIDATION, e.getExceptionType());
		}
		verify(transactionService, never()).create(any(Transaction.class));
		assertEquals(PaymentType.CREDITCARD, order.getPaymentType());
		assertEquals(null, order.getStatus());
	}

	@Test
	public void moneyOrderStaysOrdered() throws Exception {
		Order order = order(null);
		Payment payment = payment("moneyorder", PaymentType.MONEYORDER);

		Transaction transaction = paymentService.processPayment(new Customer(), store, payment, new ArrayList<>(), order);

		assertEquals(PaymentType.MONEYORDER, transaction.getPaymentType());
		assertEquals(OrderStatus.ORDERED, order.getStatus());
		assertEquals(PaymentType.MONEYORDER, order.getPaymentType());
		assertEquals("moneyorder", order.getPaymentModuleCode());
	}

	@Test
	public void missingPaymentTypeIsTakenFromModule() throws Exception {
		Order order = order(null);
		Payment payment = payment("moneyorder", null);

		paymentService.processPayment(new Customer(), store, payment, new ArrayList<>(), order);

		assertEquals(PaymentType.MONEYORDER, payment.getPaymentType());
		assertEquals(PaymentType.MONEYORDER, order.getPaymentType());
		assertEquals(OrderStatus.ORDERED, order.getStatus());
	}

	@Test
	public void capturedCardPaymentIsProcessed() throws Exception {
		Transaction captured = new Transaction();
		captured.setTransactionType(TransactionType.AUTHORIZECAPTURE);
		captured.setPaymentType(PaymentType.CREDITCARD);
		when(cardModule.authorizeAndCapture(eq(store), any(), anyList(), any(), any(), any(), any())).thenReturn(captured);
		Order order = order(null);

		paymentService.processPayment(new Customer(), store, payment("stripe", PaymentType.CREDITCARD), new ArrayList<>(), order);

		assertEquals(OrderStatus.PROCESSED, order.getStatus());
		assertEquals(PaymentType.CREDITCARD, order.getPaymentType());
	}

	@Test
	public void cardModuleWithMoneyOrderTypeIsRejected() throws Exception {
		try {
			paymentService.processPayment(new Customer(), store, payment("stripe", PaymentType.MONEYORDER), new ArrayList<>(), order(null));
			fail("stripe module paired with MONEYORDER must be rejected");
		} catch (ServiceException e) {
			assertEquals(ServiceException.EXCEPTION_VALIDATION, e.getExceptionType());
		}
		verify(cardModule, never()).authorizeAndCapture(any(), any(), anyList(), any(), any(), any(), any());
	}

	@Test
	public void offlineTransactionFromModuleIsNotProcessed() throws Exception {
		Transaction offline = new Transaction();
		offline.setTransactionType(TransactionType.AUTHORIZECAPTURE);
		offline.setPaymentType(PaymentType.MONEYORDER);
		when(cardModule.authorizeAndCapture(eq(store), any(), anyList(), any(), any(), any(), any())).thenReturn(offline);
		Order order = order(null);

		paymentService.processPayment(new Customer(), store, payment("stripe", PaymentType.CREDITCARD), new ArrayList<>(), order);

		assertEquals(OrderStatus.ORDERED, order.getStatus());
	}

	@Test
	public void moduleNotAvailableForStoreIsRejected() throws Exception {
		doReturn(null).when(paymentService).getPaymentMethodByCode(store, "stripe");
		try {
			paymentService.processPayment(new Customer(), store, payment("stripe", PaymentType.CREDITCARD), new ArrayList<>(), order(null));
			fail("payment module without an IntegrationModule must be rejected");
		} catch (ServiceException e) {
			assertEquals(ServiceException.EXCEPTION_VALIDATION, e.getExceptionType());
		}
		verify(transactionService, never()).create(any(Transaction.class));
	}

	private static IntegrationConfiguration activeConfiguration(String code) {
		IntegrationConfiguration configuration = new IntegrationConfiguration();
		configuration.setModuleCode(code);
		configuration.setActive(true);
		return configuration;
	}

	private static IntegrationModule integrationModule(String code, String type) {
		IntegrationModule module = new IntegrationModule();
		module.setCode(code);
		module.setModule("PAYMENT");
		module.setType(type);
		return module;
	}

	private static Payment payment(String moduleName, PaymentType type) {
		Payment payment = new Payment();
		payment.setModuleName(moduleName);
		payment.setPaymentType(type);
		return payment;
	}

	private static Order order(PaymentType clientPaymentType) {
		Order order = new Order();
		order.setTotal(new BigDecimal("42.00"));
		order.setPaymentType(clientPaymentType);
		return order;
	}

	private static void inject(Object target, String field, Object value) throws Exception {
		Field f = PaymentServiceImpl.class.getDeclaredField(field);
		f.setAccessible(true);
		f.set(target, value);
	}
}
