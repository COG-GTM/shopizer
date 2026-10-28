package com.salesmanager.core.business.services.order;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertThrows;
import static org.junit.Assert.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import org.junit.Test;
import org.springframework.test.util.ReflectionTestUtils;

import com.salesmanager.core.business.exception.ServiceException;
import com.salesmanager.core.business.repositories.order.OrderRepository;
import com.salesmanager.core.business.services.payments.PaymentService;
import com.salesmanager.core.model.customer.Customer;
import com.salesmanager.core.model.merchant.MerchantStore;
import com.salesmanager.core.model.order.Order;
import com.salesmanager.core.model.order.OrderTotalSummary;
import com.salesmanager.core.model.order.orderproduct.OrderProduct;
import com.salesmanager.core.model.payments.Payment;
import com.salesmanager.core.model.shoppingcart.ShoppingCartItem;

public class OrderServiceValidateQuantitiesTest {

	@Test
	public void acceptsPositiveCartAndOrderProductQuantities() throws Exception {
		Order order = new Order();
		order.getOrderProducts().add(orderProduct(2));

		OrderServiceImpl.validateQuantities(order, Arrays.asList(item(1), item(5)));
	}

	@Test
	public void rejectsNullAndNonPositiveCartItemQuantities() {
		assertInvalidCartItemQuantity(null);
		assertInvalidCartItemQuantity(0);
		assertInvalidCartItemQuantity(-3);
	}

	@Test
	public void rejectsNonPositiveOrderProductQuantities() {
		assertInvalidOrderProductQuantity(0);
		assertInvalidOrderProductQuantity(-2);
	}

	@Test
	public void processOrderRejectsNonPositiveOrderProductBeforePayment() throws Exception {
		PaymentService paymentService = mock(PaymentService.class);
		OrderServiceImpl orderService = new OrderServiceImpl(mock(OrderRepository.class));
		ReflectionTestUtils.setField(orderService, "paymentService", paymentService);
		Order order = new Order();
		order.getOrderProducts().add(orderProduct(0));

		ServiceException exception = assertThrows(ServiceException.class,
				() -> orderService.processOrder(order, new Customer(), Collections.singletonList(item(1)),
						new OrderTotalSummary(), new Payment(), new MerchantStore()));

		assertEquals(ServiceException.EXCEPTION_VALIDATION, exception.getExceptionType());
		assertTrue(exception.getMessage().contains("Invalid quantity"));
		verify(paymentService, never()).processPayment(any(), any(), any(), any(), any());
	}

	private static void assertInvalidCartItemQuantity(Integer quantity) {
		ServiceException exception = assertThrows(ServiceException.class,
				() -> OrderServiceImpl.validateQuantities(new Order(), Collections.singletonList(item(quantity))));

		assertEquals(ServiceException.EXCEPTION_VALIDATION, exception.getExceptionType());
		assertTrue(exception.getMessage().contains("Invalid quantity"));
	}

	private static void assertInvalidOrderProductQuantity(int quantity) {
		Order order = new Order();
		order.getOrderProducts().add(orderProduct(quantity));

		ServiceException exception = assertThrows(ServiceException.class,
				() -> OrderServiceImpl.validateQuantities(order, Collections.singletonList(item(1))));

		assertEquals(ServiceException.EXCEPTION_VALIDATION, exception.getExceptionType());
		assertTrue(exception.getMessage().contains("Invalid quantity"));
	}

	private static ShoppingCartItem item(Integer quantity) {
		ShoppingCartItem item = new ShoppingCartItem();
		item.setQuantity(quantity);
		return item;
	}

	private static OrderProduct orderProduct(int quantity) {
		OrderProduct product = new OrderProduct();
		product.setProductQuantity(quantity);
		return product;
	}
}
