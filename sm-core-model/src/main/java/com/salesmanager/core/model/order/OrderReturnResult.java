package com.salesmanager.core.model.order;

import java.io.Serializable;
import java.math.BigDecimal;
import java.util.HashMap;
import java.util.Map;

import com.salesmanager.core.model.order.orderstatus.OrderStatus;

/**
 * Result of a partial or complete return of order products.
 */
public class OrderReturnResult implements Serializable {

	private static final long serialVersionUID = 1L;

	private BigDecimal refundedAmount;
	private OrderStatus orderStatus;
	private Map<Long, Integer> returnedQuantities = new HashMap<Long, Integer>();

	public BigDecimal getRefundedAmount() {
		return refundedAmount;
	}

	public void setRefundedAmount(BigDecimal refundedAmount) {
		this.refundedAmount = refundedAmount;
	}

	public OrderStatus getOrderStatus() {
		return orderStatus;
	}

	public void setOrderStatus(OrderStatus orderStatus) {
		this.orderStatus = orderStatus;
	}

	public Map<Long, Integer> getReturnedQuantities() {
		return returnedQuantities;
	}

	public void setReturnedQuantities(Map<Long, Integer> returnedQuantities) {
		this.returnedQuantities = returnedQuantities;
	}

}
