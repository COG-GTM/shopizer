package com.salesmanager.test.order;

import java.math.BigDecimal;
import java.util.Collections;
import java.util.Date;
import java.util.HashMap;
import java.util.Map;

import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.ArgumentMatchers;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.junit.MockitoJUnitRunner;
import org.springframework.test.util.ReflectionTestUtils;

import com.salesmanager.core.business.constants.Constants;
import com.salesmanager.core.business.exception.ServiceException;
import com.salesmanager.core.business.services.order.OrderReturnServiceImpl;
import com.salesmanager.core.business.services.order.OrderService;
import com.salesmanager.core.business.services.payments.PaymentServiceImpl;
import com.salesmanager.core.business.services.payments.TransactionService;
import com.salesmanager.core.model.customer.Customer;
import com.salesmanager.core.model.merchant.MerchantStore;
import com.salesmanager.core.model.order.Order;
import com.salesmanager.core.model.order.OrderReturnResult;
import com.salesmanager.core.model.order.OrderTotal;
import com.salesmanager.core.model.order.OrderTotalType;
import com.salesmanager.core.model.order.orderproduct.OrderProduct;
import com.salesmanager.core.model.order.orderstatus.OrderStatus;
import com.salesmanager.core.model.payments.PaymentType;
import com.salesmanager.core.model.payments.Transaction;
import com.salesmanager.core.model.system.IntegrationConfiguration;
import com.salesmanager.core.model.system.IntegrationModule;
import com.salesmanager.core.modules.integration.payment.model.PaymentModule;

/**
 * Unit tests of the partial / complete order return logic.
 *
 * The existing sm-core order tests are integration style (they require a
 * populated database and a configured payment gateway), so the collaborators
 * that reach out to the database and to the payment gateway are mocked here.
 * The refund bookkeeping itself (REFUND order total, total recalculation and
 * over refund guard) is executed against the real PaymentServiceImpl.
 */
@RunWith(MockitoJUnitRunner.Silent.class)
public class OrderReturnTest {

	private static final String PAYMENT_MODULE_CODE = "moneyorder";
	private static final BigDecimal UNIT_PRICE = new BigDecimal("29.99");

	@Mock
	private OrderService orderService;

	@Mock
	private TransactionService transactionService;

	@Mock
	private PaymentModule paymentModule;

	private PaymentServiceImpl paymentService;
	private OrderReturnServiceImpl orderReturnService;

	private MerchantStore store;
	private Customer customer;

	@Before
	public void setUp() throws Exception {

		store = new MerchantStore();
		store.setCode(MerchantStore.DEFAULT_STORE);

		customer = new Customer();
		customer.setEmailAddress("email@email.com");

		IntegrationConfiguration configuration = new IntegrationConfiguration();
		configuration.setModuleCode(PAYMENT_MODULE_CODE);
		configuration.setActive(true);

		IntegrationModule integrationModule = new IntegrationModule();
		integrationModule.setCode(PAYMENT_MODULE_CODE);
		integrationModule.setModule(PAYMENT_MODULE_CODE);

		paymentService = Mockito.spy(new PaymentServiceImpl());
		ReflectionTestUtils.setField(paymentService, "orderService", orderService);
		ReflectionTestUtils.setField(paymentService, "transactionService", transactionService);
		ReflectionTestUtils.setField(paymentService, "paymentModules",
				Collections.singletonMap(PAYMENT_MODULE_CODE, paymentModule));

		Mockito.doReturn(Collections.singletonMap(PAYMENT_MODULE_CODE, configuration)).when(paymentService)
				.getPaymentModulesConfigured(store);
		Mockito.doReturn(integrationModule).when(paymentService).getPaymentMethodByCode(store, PAYMENT_MODULE_CODE);

		Mockito.when(transactionService.getRefundableTransaction(ArgumentMatchers.any(Order.class)))
				.thenReturn(new Transaction());
		Mockito.when(paymentModule.refund(ArgumentMatchers.anyBoolean(), ArgumentMatchers.any(MerchantStore.class),
				ArgumentMatchers.any(Transaction.class), ArgumentMatchers.any(Order.class),
				ArgumentMatchers.any(BigDecimal.class), ArgumentMatchers.any(IntegrationConfiguration.class),
				ArgumentMatchers.any(IntegrationModule.class))).thenReturn(new Transaction());

		orderReturnService = new OrderReturnServiceImpl();
		ReflectionTestUtils.setField(orderReturnService, "orderService", orderService);
		ReflectionTestUtils.setField(orderReturnService, "paymentService", paymentService);

	}

	@Test
	public void fullReturnOfSingleLineOrder() throws Exception {

		Order order = order(new BigDecimal("29.99"));
		OrderProduct orderProduct = orderProduct(order, 1L, 1);

		OrderReturnResult result = orderReturnService.returnOrderProducts(order, customer, store,
				Collections.singletonMap(1L, 1), "Wrong size");

		Assert.assertEquals(OrderStatus.RETURNED, result.getOrderStatus());
		Assert.assertEquals(OrderStatus.RETURNED, order.getStatus());
		Assert.assertEquals(new BigDecimal("29.99"), result.getRefundedAmount());
		Assert.assertEquals(Integer.valueOf(1), result.getReturnedQuantities().get(1L));
		Assert.assertEquals(orderProduct.getProductQuantity(), orderProduct.getReturnedQuantity());

		Assert.assertEquals(0, order.getTotal().compareTo(BigDecimal.ZERO));
		Assert.assertEquals(0, refundTotal(order).compareTo(new BigDecimal("29.99")));
		Assert.assertEquals(0, orderTotal(order, Constants.OT_TOTAL_MODULE_CODE).compareTo(BigDecimal.ZERO));

		Mockito.verify(orderService, Mockito.atLeastOnce()).saveOrUpdate(order);

	}

	@Test
	public void partialReturnOfSingleLineOrder() throws Exception {

		Order order = order(new BigDecimal("89.97"));
		OrderProduct orderProduct = orderProduct(order, 1L, 3);

		OrderReturnResult result = orderReturnService.returnOrderProducts(order, customer, store,
				Collections.singletonMap(1L, 1), "One item damaged");

		Assert.assertEquals(OrderStatus.PARTIALLY_RETURNED, result.getOrderStatus());
		Assert.assertEquals(OrderStatus.PARTIALLY_RETURNED, order.getStatus());
		Assert.assertEquals(new BigDecimal("29.99"), result.getRefundedAmount());
		Assert.assertEquals(1, orderProduct.getReturnedQuantity());

		Assert.assertEquals(0, order.getTotal().compareTo(new BigDecimal("59.98")));
		Assert.assertEquals(0, refundTotal(order).compareTo(new BigDecimal("29.99")));
		Assert.assertEquals(0, orderTotal(order, Constants.OT_TOTAL_MODULE_CODE).compareTo(new BigDecimal("59.98")));

	}

	@Test
	public void partialReturnOfMultipleLines() throws Exception {

		Order order = order(new BigDecimal("149.95"));
		OrderProduct first = orderProduct(order, 1L, 3);
		OrderProduct second = orderProduct(order, 2L, 2);

		Map<Long, Integer> quantities = new HashMap<Long, Integer>();
		quantities.put(1L, 2);
		quantities.put(2L, 1);

		OrderReturnResult result = orderReturnService.returnOrderProducts(order, customer, store, quantities,
				"Returned a few items");

		// 3 units * 29.99
		Assert.assertEquals(new BigDecimal("89.97"), result.getRefundedAmount());
		Assert.assertEquals(OrderStatus.PARTIALLY_RETURNED, result.getOrderStatus());
		Assert.assertEquals(2, first.getReturnedQuantity());
		Assert.assertEquals(1, second.getReturnedQuantity());
		Assert.assertEquals(0, order.getTotal().compareTo(new BigDecimal("59.98")));

	}

	@Test
	public void sequentialPartialReturnsAccumulateReturnedQuantity() throws Exception {

		Order order = order(new BigDecimal("89.97"));
		OrderProduct orderProduct = orderProduct(order, 1L, 3);

		orderReturnService.returnOrderProducts(order, customer, store, Collections.singletonMap(1L, 1), "First return");
		Assert.assertEquals(1, orderProduct.getReturnedQuantity());
		Assert.assertEquals(OrderStatus.PARTIALLY_RETURNED, order.getStatus());

		OrderReturnResult second = orderReturnService.returnOrderProducts(order, customer, store,
				Collections.singletonMap(1L, 2), "Second return");

		Assert.assertEquals(3, orderProduct.getReturnedQuantity());
		Assert.assertEquals(OrderStatus.RETURNED, second.getOrderStatus());
		Assert.assertEquals(new BigDecimal("59.98"), second.getRefundedAmount());
		Assert.assertEquals(0, order.getTotal().compareTo(BigDecimal.ZERO));

		// two REFUND totals, one per return
		int refunds = 0;
		for (OrderTotal total : order.getOrderTotal()) {
			if (OrderTotalType.REFUND.equals(total.getOrderTotalType())) {
				refunds++;
			}
		}
		Assert.assertEquals(2, refunds);

	}

	@Test
	public void returnMoreThanOrderedQuantityIsRejected() throws Exception {

		Order order = order(new BigDecimal("89.97"));
		OrderProduct orderProduct = orderProduct(order, 1L, 3);

		orderReturnService.returnOrderProducts(order, customer, store, Collections.singletonMap(1L, 2), "First return");
		Assert.assertEquals(2, orderProduct.getReturnedQuantity());

		try {
			orderReturnService.returnOrderProducts(order, customer, store, Collections.singletonMap(1L, 2),
					"Too many items");
			Assert.fail("Returning more than the ordered quantity must be rejected");
		} catch (ServiceException e) {
			Assert.assertTrue(e.getMessageCode().contains("greater than the ordered quantity"));
		}

	}

	@Test
	public void zeroQuantityIsRejected() throws Exception {

		Order order = order(new BigDecimal("89.97"));
		orderProduct(order, 1L, 3);

		try {
			orderReturnService.returnOrderProducts(order, customer, store, Collections.singletonMap(1L, 0), "Nothing");
			Assert.fail("A zero returned quantity must be rejected");
		} catch (ServiceException e) {
			Assert.assertTrue(e.getMessageCode().contains("Invalid returned quantity"));
		}

		Mockito.verify(paymentService, Mockito.never()).processRefund(ArgumentMatchers.any(Order.class),
				ArgumentMatchers.any(Customer.class), ArgumentMatchers.any(MerchantStore.class),
				ArgumentMatchers.any(BigDecimal.class));

	}

	@Test
	public void negativeQuantityIsRejected() throws Exception {

		Order order = order(new BigDecimal("89.97"));
		orderProduct(order, 1L, 3);

		try {
			orderReturnService.returnOrderProducts(order, customer, store, Collections.singletonMap(1L, -1),
					"Negative");
			Assert.fail("A negative returned quantity must be rejected");
		} catch (ServiceException e) {
			Assert.assertTrue(e.getMessageCode().contains("Invalid returned quantity"));
		}

	}

	@Test
	public void unknownOrderProductIsRejected() throws Exception {

		Order order = order(new BigDecimal("89.97"));
		orderProduct(order, 1L, 3);

		try {
			orderReturnService.returnOrderProducts(order, customer, store, Collections.singletonMap(99L, 1),
					"Unknown line");
			Assert.fail("An order product not belonging to the order must be rejected");
		} catch (ServiceException e) {
			Assert.assertTrue(e.getMessageCode().contains("does not belong to order"));
		}

	}

	@Test
	public void emptyRequestIsRejected() throws Exception {

		Order order = order(new BigDecimal("89.97"));
		orderProduct(order, 1L, 3);

		try {
			orderReturnService.returnOrderProducts(order, customer, store, new HashMap<Long, Integer>(), "Nothing");
			Assert.fail("An empty return request must be rejected");
		} catch (ServiceException e) {
			Assert.assertTrue(e.getMessageCode().contains("No order product to return"));
		}

	}

	/**
	 * The order was discounted, the sum of the returned line prices is greater
	 * than what remains payable on the order, the refund guard must reject it.
	 */
	@Test
	public void refundGreaterThanOrderTotalIsRejected() throws Exception {

		Order order = order(new BigDecimal("40.00"));
		orderProduct(order, 1L, 2);

		try {
			orderReturnService.returnOrderProducts(order, customer, store, Collections.singletonMap(1L, 2),
					"Refund too large");
			Assert.fail("A refund greater than the order total must be rejected");
		} catch (ServiceException e) {
			Assert.assertTrue(e.getMessageCode().contains("refunded amount is greater than the total allowed"));
		}

		Assert.assertEquals(0, order.getTotal().compareTo(new BigDecimal("40.00")));
		Assert.assertNull(refundTotal(order));

	}

	private Order order(BigDecimal total) {

		Order order = new Order();
		order.setId(1L);
		order.setMerchant(store);
		order.setDatePurchased(new Date());
		order.setPaymentType(PaymentType.MONEYORDER);
		order.setPaymentModuleCode(PAYMENT_MODULE_CODE);
		order.setStatus(OrderStatus.ORDERED);
		order.setCustomerEmailAddress(customer.getEmailAddress());
		order.setTotal(total);

		OrderTotal subTotal = new OrderTotal();
		subTotal.setOrder(order);
		subTotal.setModule(Constants.OT_SUBTOTAL_MODULE_CODE);
		subTotal.setOrderTotalCode(Constants.OT_SUBTOTAL_MODULE_CODE);
		subTotal.setTitle("Sub Total");
		subTotal.setSortOrder(0);
		subTotal.setValue(total);
		order.getOrderTotal().add(subTotal);

		OrderTotal orderTotal = new OrderTotal();
		orderTotal.setOrder(order);
		orderTotal.setModule(Constants.OT_TOTAL_MODULE_CODE);
		orderTotal.setOrderTotalCode(Constants.OT_TOTAL_MODULE_CODE);
		orderTotal.setTitle("Total");
		orderTotal.setSortOrder(1);
		orderTotal.setValue(total);
		order.getOrderTotal().add(orderTotal);

		return order;

	}

	private OrderProduct orderProduct(Order order, Long id, int quantity) {

		OrderProduct orderProduct = new OrderProduct();
		orderProduct.setId(id);
		orderProduct.setOrder(order);
		orderProduct.setProductName("Short sleeves shirt");
		orderProduct.setSku("TB12345");
		orderProduct.setProductQuantity(quantity);
		orderProduct.setOneTimeCharge(UNIT_PRICE);
		order.getOrderProducts().add(orderProduct);

		return orderProduct;

	}

	private BigDecimal refundTotal(Order order) {
		return orderTotal(order, Constants.OT_REFUND_MODULE_CODE);
	}

	private BigDecimal orderTotal(Order order, String moduleCode) {
		for (OrderTotal total : order.getOrderTotal()) {
			if (moduleCode.equals(total.getModule())) {
				return total.getValue();
			}
		}
		return null;
	}

}
