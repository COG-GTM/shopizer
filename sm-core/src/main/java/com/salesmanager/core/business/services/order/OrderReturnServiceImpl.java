package com.salesmanager.core.business.services.order;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.Date;
import java.util.HashMap;
import java.util.Map;

import javax.inject.Inject;

import org.apache.commons.lang3.Validate;
import org.springframework.stereotype.Service;

import com.salesmanager.core.business.exception.ServiceException;
import com.salesmanager.core.business.services.payments.PaymentService;
import com.salesmanager.core.model.customer.Customer;
import com.salesmanager.core.model.merchant.MerchantStore;
import com.salesmanager.core.model.order.Order;
import com.salesmanager.core.model.order.OrderReturnResult;
import com.salesmanager.core.model.order.orderproduct.OrderProduct;
import com.salesmanager.core.model.order.orderstatus.OrderStatus;
import com.salesmanager.core.model.order.orderstatus.OrderStatusHistory;

@Service("orderReturnService")
public class OrderReturnServiceImpl implements OrderReturnService {

	@Inject
	private OrderService orderService;

	@Inject
	private PaymentService paymentService;

	@Override
	public OrderReturnResult returnOrderProducts(Order order, Customer customer, MerchantStore store,
			Map<Long, Integer> returnedQuantitiesByOrderProductId, String reason) throws ServiceException {

		Validate.notNull(order, "Order must not be null");
		Validate.notNull(customer, "Customer must not be null");
		Validate.notNull(store, "MerchantStore must not be null");

		if (returnedQuantitiesByOrderProductId == null || returnedQuantitiesByOrderProductId.isEmpty()) {
			throw new ServiceException("No order product to return");
		}

		Map<Long, OrderProduct> orderProducts = new HashMap<Long, OrderProduct>();
		for (OrderProduct orderProduct : order.getOrderProducts()) {
			orderProducts.put(orderProduct.getId(), orderProduct);
		}

		BigDecimal refundAmount = BigDecimal.ZERO;
		Map<Long, Integer> returnedQuantities = new HashMap<Long, Integer>();

		for (Map.Entry<Long, Integer> entry : returnedQuantitiesByOrderProductId.entrySet()) {

			Long orderProductId = entry.getKey();
			Integer quantity = entry.getValue();

			if (quantity == null || quantity.intValue() <= 0) {
				throw new ServiceException("Invalid returned quantity for order product id " + orderProductId);
			}

			OrderProduct orderProduct = orderProducts.get(orderProductId);
			if (orderProduct == null) {
				throw new ServiceException(
						"Order product id " + orderProductId + " does not belong to order id " + order.getId());
			}

			int returnedQuantity = orderProduct.getReturnedQuantity() + quantity.intValue();
			if (returnedQuantity > orderProduct.getProductQuantity()) {
				throw new ServiceException("Returned quantity is greater than the ordered quantity for order product id "
						+ orderProductId);
			}

			refundAmount = refundAmount
					.add(unitPrice(orderProduct).multiply(new BigDecimal(quantity.intValue())));

			orderProduct.setReturnedQuantity(returnedQuantity);
			returnedQuantities.put(orderProductId, quantity);

		}

		refundAmount = refundAmount.setScale(2, RoundingMode.HALF_UP);

		paymentService.processRefund(order, customer, store, refundAmount);

		OrderStatus status = isFullyReturned(order) ? OrderStatus.RETURNED : OrderStatus.PARTIALLY_RETURNED;

		OrderStatusHistory orderHistory = new OrderStatusHistory();
		orderHistory.setOrder(order);
		orderHistory.setStatus(status);
		orderHistory.setDateAdded(new Date());
		orderHistory.setComments(reason);
		order.getOrderHistory().add(orderHistory);

		order.setStatus(status);

		orderService.saveOrUpdate(order);

		OrderReturnResult result = new OrderReturnResult();
		result.setRefundedAmount(refundAmount);
		result.setOrderStatus(status);
		result.setReturnedQuantities(returnedQuantities);

		return result;

	}

	/**
	 * ONETIME_CHARGE is the price of a single item, the line total is obtained
	 * by multiplying it with the ordered quantity.
	 */
	private BigDecimal unitPrice(OrderProduct orderProduct) throws ServiceException {
		BigDecimal oneTimeCharge = orderProduct.getOneTimeCharge();
		if (oneTimeCharge == null) {
			throw new ServiceException("No price for order product id " + orderProduct.getId());
		}
		return oneTimeCharge;
	}

	private boolean isFullyReturned(Order order) {
		for (OrderProduct orderProduct : order.getOrderProducts()) {
			if (orderProduct.getReturnedQuantity() < orderProduct.getProductQuantity()) {
				return false;
			}
		}
		return true;
	}

}
