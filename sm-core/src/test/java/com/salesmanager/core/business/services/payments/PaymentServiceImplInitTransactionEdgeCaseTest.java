package com.salesmanager.core.business.services.payments;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;

import org.junit.Before;
import org.junit.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.test.util.ReflectionTestUtils;

import com.salesmanager.core.business.exception.ServiceException;
import com.salesmanager.core.business.services.order.OrderService;
import com.salesmanager.core.model.merchant.MerchantStore;
import com.salesmanager.core.model.order.OrderSummary;
import com.salesmanager.core.model.order.OrderTotalSummary;
import com.salesmanager.core.model.payments.Payment;
import com.salesmanager.core.model.payments.Transaction;
import com.salesmanager.core.model.payments.TransactionType;
import com.salesmanager.core.model.reference.currency.Currency;
import com.salesmanager.core.model.reference.language.Language;
import com.salesmanager.core.model.shipping.ShippingSummary;
import com.salesmanager.core.model.shoppingcart.ShoppingCart;
import com.salesmanager.core.model.shoppingcart.ShoppingCartItem;
import com.salesmanager.core.model.system.IntegrationConfiguration;
import com.salesmanager.core.model.system.IntegrationModule;
import com.salesmanager.core.modules.integration.IntegrationException;
import com.salesmanager.core.modules.integration.payment.model.PaymentModule;

public class PaymentServiceImplInitTransactionEdgeCaseTest {

	private static final String MODULE = "stripe3";
	private static final BigDecimal CART_TOTAL = new BigDecimal("120.00");

	private PaymentServiceImpl paymentService;
	private OrderService orderService;
	private TransactionService transactionService;
	private PaymentModule paymentModule;
	private MerchantStore store;
	private Language language;
	private ShoppingCart cart;

	@Before
	public void setUp() throws Exception {
		paymentService = spy(new PaymentServiceImpl());
		orderService = mock(OrderService.class);
		transactionService = mock(TransactionService.class);
		paymentModule = mock(PaymentModule.class);
		store = mock(MerchantStore.class);
		language = mock(Language.class);

		Map<String, PaymentModule> modules = new HashMap<>();
		modules.put(MODULE, paymentModule);
		ReflectionTestUtils.setField(paymentService, "orderService", orderService);
		ReflectionTestUtils.setField(paymentService, "transactionService", transactionService);
		ReflectionTestUtils.setField(paymentService, "paymentModules", modules);

		IntegrationConfiguration configuration = new IntegrationConfiguration();
		configuration.setActive(true);
		doReturn(Collections.singletonMap(MODULE, configuration)).when(paymentService).getPaymentModulesConfigured(store);
		doReturn(new IntegrationModule()).when(paymentService).getPaymentMethodByCode(store, MODULE);

		cart = new ShoppingCart();
		cart.setId(42L);
		cart.setShoppingCartCode("cart-code");
		ShoppingCartItem item = new ShoppingCartItem();
		item.setShoppingCart(cart);
		cart.setLineItems(new HashSet<>(Collections.singleton(item)));

		when(orderService.caculateOrderTotal(any(OrderSummary.class), eq(store), eq(language)))
				.thenReturn(summary(CART_TOTAL));
		when(paymentModule.initTransaction(eq(store), isNull(), any(BigDecimal.class), any(Payment.class), any(), any()))
				.thenAnswer(invocation -> {
					Transaction transaction = new Transaction();
					transaction.setAmount(invocation.getArgument(2));
					transaction.setTransactionType(TransactionType.INIT);
					return transaction;
				});
	}

	@Test
	public void rejectsZeroPayableCartTotalWithoutCallingGateway() throws Exception {
		when(orderService.caculateOrderTotal(any(OrderSummary.class), eq(store), eq(language)))
				.thenReturn(summary(BigDecimal.ZERO));

		try {
			paymentService.initTransaction(null, cart, null, payment(null), store, language);
			fail("Expected validation ServiceException");
		} catch (ServiceException e) {
			assertEquals(ServiceException.EXCEPTION_VALIDATION, e.getExceptionType());
		}

		verify(paymentModule, never()).initTransaction(any(), any(), any(), any(), any(), any());
		verify(transactionService, never()).create(any(Transaction.class));
	}

	@Test
	public void forwardsShippingSummaryToAnonymousOrderTotalOverload() throws Exception {
		ShippingSummary shippingSummary = new ShippingSummary();

		paymentService.initTransaction(null, cart, shippingSummary, payment(null), store, language);

		ArgumentCaptor<OrderSummary> orderSummary = ArgumentCaptor.forClass(OrderSummary.class);
		verify(orderService).caculateOrderTotal(orderSummary.capture(), eq(store), eq(language));
		assertSame(shippingSummary, orderSummary.getValue().getShippingSummary());
	}

	@Test
	public void forwardsShippingSummaryAndCustomerToCustomerOrderTotalOverload() throws Exception {
		com.salesmanager.core.model.customer.Customer customer =
				mock(com.salesmanager.core.model.customer.Customer.class);
		ShippingSummary shippingSummary = new ShippingSummary();
		when(orderService.caculateOrderTotal(any(OrderSummary.class), eq(customer), eq(store), eq(language)))
				.thenReturn(summary(CART_TOTAL));
		when(paymentModule.initTransaction(eq(store), eq(customer), any(BigDecimal.class), any(Payment.class), any(), any()))
				.thenAnswer(invocation -> new Transaction());

		paymentService.initTransaction(customer, cart, shippingSummary, payment(null), store, language);

		ArgumentCaptor<OrderSummary> orderSummary = ArgumentCaptor.forClass(OrderSummary.class);
		verify(orderService).caculateOrderTotal(orderSummary.capture(), eq(customer), eq(store), eq(language));
		assertSame(shippingSummary, orderSummary.getValue().getShippingSummary());
		verify(paymentModule).initTransaction(eq(store), eq(customer), any(BigDecimal.class), any(Payment.class), any(), any());
	}

	@Test
	public void rejectsNullGatewayTransactionWithoutPersisting() throws Exception {
		when(paymentModule.initTransaction(eq(store), isNull(), any(BigDecimal.class), any(Payment.class), any(), any()))
				.thenReturn(null);

		try {
			paymentService.initTransaction(null, cart, null, payment(null), store, language);
			fail("Expected ServiceException");
		} catch (ServiceException e) {
			assertTrue(e.getExceptionType() != ServiceException.EXCEPTION_VALIDATION);
		}

		verify(transactionService, never()).create(any(Transaction.class));
		verify(transactionService, never()).save(any(Transaction.class));
	}

	@Test
	public void createsTransactionWithServerTotalAndCartId() throws Exception {
		Payment payment = payment(null);
		Transaction transaction = paymentService.initTransaction(null, cart, null, payment, store, language);

		assertNotNull(transaction);
		assertEquals(0, CART_TOTAL.compareTo(transaction.getAmount()));
		assertEquals("42", transaction.getTransactionDetails().get(PaymentServiceImpl.INIT_SHOPPING_CART_ID));
		ArgumentCaptor<Transaction> created = ArgumentCaptor.forClass(Transaction.class);
		verify(transactionService).create(created.capture());
		assertSame(transaction, created.getValue());
		verify(transactionService, never()).save(any(Transaction.class));
	}

	@Test
	public void setsStoreCurrencyAndInitDespiteCaptureRequest() throws Exception {
		Currency currency = mock(Currency.class);
		when(store.getCurrency()).thenReturn(currency);
		Payment payment = payment(null);
		payment.setTransactionType(TransactionType.CAPTURE);

		paymentService.initTransaction(null, cart, null, payment, store, language);

		assertEquals(CART_TOTAL, payment.getAmount());
		assertSame(currency, payment.getCurrency());
		assertEquals(TransactionType.INIT, payment.getTransactionType());
	}

	@Test
	public void propagatesGatewayIntegrationExceptionWithoutPersisting() throws Exception {
		IntegrationException gatewayException = new IntegrationException("gateway failed");
		doThrow(gatewayException).when(paymentModule)
				.initTransaction(eq(store), isNull(), any(BigDecimal.class), any(Payment.class), any(), any());

		try {
			paymentService.initTransaction(null, cart, null, payment(null), store, language);
			fail("Expected gateway ServiceException");
		} catch (ServiceException e) {
			assertSame(gatewayException, e);
		}

		verify(transactionService, never()).create(any(Transaction.class));
	}

	private OrderTotalSummary summary(BigDecimal total) {
		OrderTotalSummary summary = new OrderTotalSummary();
		summary.setTotal(total);
		return summary;
	}

	private Payment payment(BigDecimal amount) {
		Payment payment = new Payment();
		payment.setModuleName(MODULE);
		payment.setAmount(amount);
		return payment;
	}
}
