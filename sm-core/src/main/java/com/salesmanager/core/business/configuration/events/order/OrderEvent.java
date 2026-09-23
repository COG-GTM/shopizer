package com.salesmanager.core.business.configuration.events.order;

import org.springframework.context.ApplicationEvent;

import com.salesmanager.core.model.merchant.MerchantStore;
import com.salesmanager.core.model.order.Order;

public abstract class OrderEvent extends ApplicationEvent {

	private static final long serialVersionUID = 1L;

	private final transient Order order;
	private final transient MerchantStore store;

	public OrderEvent(Object source, Order order, MerchantStore store) {
		super(source);
		this.order = order;
		this.store = store;
	}

	public Order getOrder() {
		return order;
	}

	public MerchantStore getStore() {
		return store;
	}

}
