package com.salesmanager.test.shop.order;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.salesmanager.core.business.exception.ServiceException;
import com.salesmanager.core.business.services.customer.CustomerService;
import com.salesmanager.core.business.services.order.OrderReturnService;
import com.salesmanager.core.business.services.order.OrderService;
import com.salesmanager.core.model.customer.Customer;
import com.salesmanager.core.model.merchant.MerchantStore;
import com.salesmanager.core.model.order.Order;
import com.salesmanager.core.model.order.OrderReturnResult;
import com.salesmanager.core.model.order.orderproduct.OrderProduct;
import com.salesmanager.core.model.order.orderstatus.OrderStatus;
import com.salesmanager.core.model.reference.language.Language;
import com.salesmanager.shop.model.order.PersistableOrderReturn;
import com.salesmanager.shop.model.order.PersistableOrderReturnItem;
import com.salesmanager.shop.model.order.ReadableOrderReturn;
import com.salesmanager.shop.store.api.exception.ResourceNotFoundException;
import com.salesmanager.shop.store.api.exception.RestApiException;
import com.salesmanager.shop.store.api.exception.ServiceRuntimeException;
import com.salesmanager.shop.store.controller.order.facade.OrderFacadeImpl;

/**
 * Mock based tests of the order return facade. The existing tests of this source set are
 * integration tests requiring a running database, those tests only exercise the facade logic.
 */
@ExtendWith(MockitoExtension.class)
public class OrderReturnFacadeTest {

	private static final Long ORDER_ID = 100L;
	private static final Long ORDER_PRODUCT_ID = 10L;
	private static final Long CUSTOMER_ID = 5L;

	@Mock
	private OrderService orderService;

	@Mock
	private CustomerService customerService;

	@Mock
	private OrderReturnService orderReturnService;

	@InjectMocks
	private OrderFacadeImpl orderFacade;

	private MerchantStore store;
	private Language language;

	@BeforeEach
	public void setUp() {
		store = new MerchantStore();
		store.setCode(MerchantStore.DEFAULT_STORE);
		language = new Language("en");
	}

	@Test
	public void returnOrderRefundsTheReturnedQuantities() throws Exception {

		Order order = order();

		when(orderService.getOrder(ORDER_ID, store)).thenReturn(order);
		when(customerService.getById(CUSTOMER_ID)).thenReturn(new Customer());

		OrderReturnResult result = new OrderReturnResult();
		result.setRefundedAmount(new BigDecimal("20.00"));
		result.setOrderStatus(OrderStatus.PARTIALLY_RETURNED);
		Map<Long, Integer> returnedQuantities = new HashMap<Long, Integer>();
		returnedQuantities.put(ORDER_PRODUCT_ID, 2);
		result.setReturnedQuantities(returnedQuantities);

		when(orderReturnService.returnOrderProducts(eq(order), any(Customer.class), eq(store), any(Map.class),
				anyString())).thenReturn(result);

		ReadableOrderReturn readable = orderFacade.returnOrder(ORDER_ID, returnRequest(ORDER_PRODUCT_ID, 2), store,
				language);

		assertEquals(new BigDecimal("20.00"), readable.getRefundedAmount());
		assertEquals(OrderStatus.PARTIALLY_RETURNED.name(), readable.getOrderStatus());
		assertEquals(1, readable.getItems().size());
		assertEquals(ORDER_PRODUCT_ID, readable.getItems().get(0).getOrderProductId());
		assertEquals(2, readable.getItems().get(0).getQuantity());
		assertEquals(new BigDecimal("20.00"), readable.getItems().get(0).getAmount());

		@SuppressWarnings("unchecked")
		ArgumentCaptor<Map<Long, Integer>> captor = ArgumentCaptor.forClass(Map.class);
		org.mockito.Mockito.verify(orderReturnService).returnOrderProducts(eq(order), any(Customer.class), eq(store),
				captor.capture(), anyString());
		assertEquals(Integer.valueOf(2), captor.getValue().get(ORDER_PRODUCT_ID));

	}

	@Test
	public void returnOrderThrowsWhenOrderDoesNotExist() {

		when(orderService.getOrder(ORDER_ID, store)).thenReturn(null);

		assertThrows(ResourceNotFoundException.class,
				() -> orderFacade.returnOrder(ORDER_ID, returnRequest(ORDER_PRODUCT_ID, 1), store, language));

	}

	@Test
	public void returnOrderThrowsWhenNoItemIsRequested() {

		when(orderService.getOrder(ORDER_ID, store)).thenReturn(order());

		PersistableOrderReturn request = new PersistableOrderReturn();
		request.setReason("damaged");

		assertThrows(RestApiException.class, () -> orderFacade.returnOrder(ORDER_ID, request, store, language));

	}

	@Test
	public void returnOrderThrowsWhenQuantityIsNotPositive() {

		when(orderService.getOrder(ORDER_ID, store)).thenReturn(order());

		assertThrows(RestApiException.class,
				() -> orderFacade.returnOrder(ORDER_ID, returnRequest(ORDER_PRODUCT_ID, 0), store, language));

	}

	@Test
	public void returnOrderWrapsCoreServiceErrors() throws Exception {

		Order order = order();

		when(orderService.getOrder(ORDER_ID, store)).thenReturn(order);
		when(customerService.getById(CUSTOMER_ID)).thenReturn(new Customer());
		when(orderReturnService.returnOrderProducts(eq(order), any(Customer.class), eq(store), any(Map.class),
				anyString())).thenThrow(new ServiceException("Returned quantity is greater than the ordered quantity"));

		assertThrows(ServiceRuntimeException.class,
				() -> orderFacade.returnOrder(ORDER_ID, returnRequest(ORDER_PRODUCT_ID, 2), store, language));

	}

	private PersistableOrderReturn returnRequest(Long orderProductId, int quantity) {
		PersistableOrderReturnItem item = new PersistableOrderReturnItem();
		item.setOrderProductId(orderProductId);
		item.setQuantity(quantity);

		PersistableOrderReturn request = new PersistableOrderReturn();
		request.setItems(Arrays.asList(item));
		request.setReason("damaged");
		return request;
	}

	private Order order() {
		OrderProduct orderProduct = new OrderProduct();
		orderProduct.setId(ORDER_PRODUCT_ID);
		orderProduct.setProductQuantity(3);
		orderProduct.setOneTimeCharge(new BigDecimal("10.00"));

		Set<OrderProduct> orderProducts = new HashSet<OrderProduct>();
		orderProducts.add(orderProduct);

		Order order = new Order();
		order.setId(ORDER_ID);
		order.setCustomerId(CUSTOMER_ID);
		order.setOrderProducts(orderProducts);
		return order;
	}

}
