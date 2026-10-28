package com.salesmanager.core.business.services.order;

import static org.junit.Assert.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

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
import com.salesmanager.core.model.payments.Payment;
import com.salesmanager.core.model.shoppingcart.ShoppingCartItem;

public class OrderServiceQuantityTest {

	@Test
	public void processOrderRejectsNegativeQuantityBeforePayment() throws Exception {
		PaymentService paymentService = mock(PaymentService.class);
		OrderServiceImpl orderService = new OrderServiceImpl(mock(OrderRepository.class));
		ReflectionTestUtils.setField(orderService, "paymentService", paymentService);

		ShoppingCartItem item = new ShoppingCartItem();
		item.setQuantity(-1);
		List<ShoppingCartItem> items = Collections.singletonList(item);

		assertThrows(ServiceException.class, () -> orderService.processOrder(new Order(), new Customer(), items,
				new OrderTotalSummary(), new Payment(), new MerchantStore()));

		verify(paymentService, never()).processPayment(any(), any(), any(), any(), any());
	}
}
