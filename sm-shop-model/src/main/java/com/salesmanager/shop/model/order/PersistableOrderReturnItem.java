package com.salesmanager.shop.model.order;

import java.io.Serializable;

/**
 * A single line of a return request, referring to an order product
 * and the quantity to be returned.
 */
public class PersistableOrderReturnItem implements Serializable {

	private static final long serialVersionUID = 1L;

	private Long orderProductId;
	private int quantity;

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

}
