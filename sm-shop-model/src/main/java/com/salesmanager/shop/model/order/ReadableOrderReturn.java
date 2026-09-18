package com.salesmanager.shop.model.order;

import java.io.Serializable;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

/**
 * Outcome of a return request : returned lines, refunded amount and resulting order status.
 */
public class ReadableOrderReturn implements Serializable {

	private static final long serialVersionUID = 1L;

	private List<ReadableOrderReturnItem> items = new ArrayList<ReadableOrderReturnItem>();
	private BigDecimal refundedAmount;
	private String orderStatus;

	public List<ReadableOrderReturnItem> getItems() {
		return items;
	}

	public void setItems(List<ReadableOrderReturnItem> items) {
		this.items = items;
	}

	public BigDecimal getRefundedAmount() {
		return refundedAmount;
	}

	public void setRefundedAmount(BigDecimal refundedAmount) {
		this.refundedAmount = refundedAmount;
	}

	public String getOrderStatus() {
		return orderStatus;
	}

	public void setOrderStatus(String orderStatus) {
		this.orderStatus = orderStatus;
	}

}
