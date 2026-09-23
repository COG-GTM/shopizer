package com.salesmanager.core.business.configuration.events.order;

import com.salesmanager.core.model.merchant.MerchantStore;
import com.salesmanager.core.model.order.Order;

public class SaveOrderEvent extends OrderEvent {

	private static final long serialVersionUID = 1L;

	public SaveOrderEvent(Object source, Order order, MerchantStore store) {
		super(source, order, store);
	}

}
