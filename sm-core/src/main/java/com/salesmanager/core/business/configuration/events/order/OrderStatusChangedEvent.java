package com.salesmanager.core.business.configuration.events.order;

import com.salesmanager.core.model.merchant.MerchantStore;
import com.salesmanager.core.model.order.Order;
import com.salesmanager.core.model.order.orderstatus.OrderStatus;

public class OrderStatusChangedEvent extends OrderEvent {

	private static final long serialVersionUID = 1L;

	private final OrderStatus oldStatus;
	private final OrderStatus newStatus;

	public OrderStatusChangedEvent(Object source, Order order, OrderStatus oldStatus, OrderStatus newStatus,
			MerchantStore store) {
		super(source, order, store);
		this.oldStatus = oldStatus;
		this.newStatus = newStatus;
	}

	public OrderStatus getOldStatus() {
		return oldStatus;
	}

	public OrderStatus getNewStatus() {
		return newStatus;
	}

}
