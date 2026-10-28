package com.salesmanager.core.business.services.payments;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.fail;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.doReturn;
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
import com.salesmanager.core.model.reference.language.Language;
import com.salesmanager.core.model.shoppingcart.ShoppingCart;
import com.salesmanager.core.model.shoppingcart.ShoppingCartItem;
import com.salesmanager.core.model.system.IntegrationConfiguration;
import com.salesmanager.core.model.system.IntegrationModule;
import com.salesmanager.core.modules.integration.payment.model.PaymentModule;

public class PaymentServiceImplInitTransactionTest {

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

		OrderTotalSummary summary = new OrderTotalSummary();
		summary.setTotal(CART_TOTAL);
		when(orderService.caculateOrderTotal(any(OrderSummary.class), eq(store), eq(language))).thenReturn(summary);

		when(paymentModule.initTransaction(eq(store), isNull(), any(BigDecimal.class), any(Payment.class), any(), any()))
				.thenAnswer(invocation -> {
					Transaction transaction = new Transaction();
					transaction.setAmount(invocation.getArgument(2));
					transaction.setTransactionType(TransactionType.INIT);
					return transaction;
				});
	}

	@Test
	public void usesServerCalculatedCartTotalWhenNoAmountSubmitted() throws Exception {
		Payment payment = payment(null);

		Transaction transaction = paymentService.initTransaction(null, cart, null, payment, store, language);

		ArgumentCaptor<BigDecimal> amount = ArgumentCaptor.forClass(BigDecimal.class);
		verify(paymentModule).initTransaction(eq(store), isNull(), amount.capture(), eq(payment), any(), any());
		assertEquals(0, CART_TOTAL.compareTo(amount.getValue()));
		assertEquals(0, CART_TOTAL.compareTo(transaction.getAmount()));
		assertEquals("42", transaction.getTransactionDetails().get(PaymentServiceImpl.INIT_SHOPPING_CART_ID));
		verify(transactionService).create(transaction);
	}

	@Test
	public void rejectsAmountLowerThanCartTotal() throws Exception {
		assertRejected(payment(new BigDecimal("0.50")));
	}

	@Test
	public void rejectsAmountHigherThanCartTotal() throws Exception {
		assertRejected(payment(new BigDecimal("500.00")));
	}

	@Test
	public void acceptsMatchingAmount() throws Exception {
		Transaction transaction = paymentService.initTransaction(null, cart, null, payment(new BigDecimal("120")), store, language);
		assertEquals(0, CART_TOTAL.compareTo(transaction.getAmount()));
	}

	@Test
	public void ignoresClientTransactionType() throws Exception {
		Payment payment = payment(null);
		payment.setTransactionType(TransactionType.AUTHORIZECAPTURE);

		paymentService.initTransaction(null, cart, null, payment, store, language);

		assertEquals(TransactionType.INIT, payment.getTransactionType());
	}

	@Test
	public void rejectsEmptyCart() throws Exception {
		cart.setLineItems(new HashSet<>());
		assertRejected(payment(null));
	}

	private void assertRejected(Payment payment) throws Exception {
		try {
			paymentService.initTransaction(null, cart, null, payment, store, language);
			fail("Expected ServiceException");
		} catch (ServiceException e) {
			assertEquals(ServiceException.EXCEPTION_VALIDATION, e.getExceptionType());
		}
		verify(paymentModule, never()).initTransaction(any(), any(), any(), any(), any(), any());
		verify(transactionService, never()).create(any(Transaction.class));
	}

	private Payment payment(BigDecimal amount) {
		Payment payment = new Payment();
		payment.setModuleName(MODULE);
		payment.setAmount(amount);
		return payment;
	}
}
