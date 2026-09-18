package com.salesmanager.shop.model.order;

import java.io.Serializable;
import java.math.BigDecimal;

/**
 * A single returned line with the quantity returned and the amount refunded for that line.
 */
public class ReadableOrderReturnItem implements Serializable {

	private static final long serialVersionUID = 1L;

	private Long orderProductId;
	private int quantity;
	private BigDecimal amount;

	public Long getOrderProductId() {
		return orderProductId;
	}

	public void setOrderProductId(Long orderProductId) {
		this.orderProductId = orderProductId;
	}

	public int getQuantity() {
		return quantity;
	}

	public void setQuantity(int quantity) {
		this.quantity = quantity;
	}

	public BigDecimal getAmount() {
		return amount;
	}

	public void setAmount(BigDecimal amount) {
		this.amount = amount;
	}

}
